import { DatePipe, DecimalPipe } from '@angular/common';
import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  effect,
  ElementRef,
  inject,
  input,
  signal,
  untracked,
} from '@angular/core';
import { PriceHistoryPoint } from '@jordylab-fe/fna/api';

/** The chart is drawn at its real pixel size: its width is the panel's width, its height is fixed. */
const CHART_HEIGHT = 180;
/** Used until the chart has been measured; the first measurement replaces it. */
const DEFAULT_WIDTH = 320;
/** Room to the right of the plot for the price values, so they never clip. */
const PRICE_GUTTER = 52;
const PLOT_LEFT = 8;
const PLOT_TOP = 12;
const PLOT_BOTTOM = CHART_HEIGHT - 26;
const PRICE_LABEL_OFFSET = 8;
const DATE_AXIS_Y = CHART_HEIGHT - 6;
const AXIS_TEXT_BASELINE_OFFSET = 4;
const END_DOT_RADIUS = 3.5;
const TARGET_GRIDLINES = 4;
const MAX_GRIDLINES = 5;
/** Within this share of the width from either edge, the readout is pinned to that edge so it stays on the chart. */
const EDGE_PIN_SHARE = 0.18;

const AXIS_DATE_FORMAT = new Intl.DateTimeFormat('en-GB', {
  day: 'numeric',
  month: 'short',
  year: '2-digit',
  timeZone: 'UTC',
});

/** A counter gives every chart its own gradient id, so several charts on one page never share a fill. */
let chartCount = 0;

interface ChartCoordinate {
  x: number;
  y: number;
}

interface PriceScale {
  low: number;
  high: number;
  step: number;
  ticks: number[];
}

interface PriceTick {
  value: number;
  y: number;
  label: string;
}

interface DateLabel {
  x: number;
  anchor: 'start' | 'middle' | 'end';
  text: string;
}

/** The recorded point under the pointer: where it is drawn and what it reads. */
interface HoveredPoint {
  x: number;
  y: number;
  leftPercent: number;
  transform: string;
  date: string;
  priceInEuro: number;
}

/** A round step (1, 2, 2.5 or 5 times a power of ten) that gives about the target number of gridlines. */
function roundStep(range: number, targetGridlines: number): number {
  const rawStep = range / targetGridlines;
  const magnitude = 10 ** Math.floor(Math.log10(rawStep));
  const normalized = rawStep / magnitude;
  if (normalized <= 1) {
    return magnitude;
  }
  if (normalized <= 2) {
    return 2 * magnitude;
  }
  if (normalized <= 2.5) {
    return 2.5 * magnitude;
  }
  if (normalized <= 5) {
    return 5 * magnitude;
  }

  return 10 * magnitude;
}

function tickValues(lowest: number, highest: number, step: number): number[] {
  const firstIndex = Math.floor(lowest / step);
  const lastIndex = Math.ceil(highest / step);

  return Array.from({ length: lastIndex - firstIndex + 1 }, (_, index) =>
    Number(((firstIndex + index) * step).toFixed(6)),
  );
}

/** Round gridline values around the period's prices. The scale never starts at zero. */
function priceScaleFor(lowest: number, highest: number): PriceScale {
  let step = roundStep(highest - lowest || 1, TARGET_GRIDLINES);
  let ticks = tickValues(lowest, highest, step);
  while (ticks.length > MAX_GRIDLINES) {
    step *= 2;
    ticks = tickValues(lowest, highest, step);
  }
  if (ticks.length < 2) {
    ticks = [ticks[0], ticks[0] + step];
  }

  return {
    low: ticks[0],
    high: ticks[ticks.length - 1],
    step,
    ticks,
  };
}

function formatAxisDate(date: string): string {
  return AXIS_DATE_FORMAT.format(new Date(`${date}T00:00:00Z`));
}

/** Centres the readout over the point, but pins it to the chart's left or right edge near either end. */
function readoutTransform(share: number): string {
  if (share < EDGE_PIN_SHARE) {
    return 'translateX(0)';
  }
  if (share > 1 - EDGE_PIN_SHARE) {
    return 'translateX(-100%)';
  }

  return 'translateX(-50%)';
}

/** One stock's euro price over a period as a line over a soft area, with price and date values and the end point marked. */
@Component({
  selector: 'lib-price-history-chart',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [DatePipe, DecimalPipe],
  host: { class: 'block w-full' },
  templateUrl: './price-history-chart.component.html',
  styles: [
    `
      /* The chart draws itself: the axis fades in, the line draws from left to right, then the area fades in and the
         end dot appears last. Reduced motion shows the finished chart at once. */
      .price-axis {
        opacity: 0;
        animation: price-fade-in 400ms ease-out 150ms forwards;
      }

      .price-line {
        stroke-dasharray: 1;
        stroke-dashoffset: 1;
        animation: price-line-draw 900ms ease-out 100ms forwards;
      }

      .price-area {
        opacity: 0;
        animation: price-fade-in 500ms ease-out 700ms forwards;
      }

      .price-end-dot {
        opacity: 0;
        animation: price-fade-in 250ms ease-out 950ms forwards;
      }

      @keyframes price-line-draw {
        to {
          stroke-dashoffset: 0;
        }
      }

      @keyframes price-fade-in {
        to {
          opacity: 1;
        }
      }

      @media (prefers-reduced-motion: reduce) {
        .price-axis,
        .price-area,
        .price-end-dot {
          opacity: 1;
          animation: none;
        }

        .price-line {
          stroke-dasharray: none;
          stroke-dashoffset: 0;
          animation: none;
        }
      }
    `,
  ],
})
export class PriceHistoryChartComponent {
  points = input.required<PriceHistoryPoint[]>();
  label = input.required<string>();

  readonly #host: ElementRef<HTMLElement> = inject(ElementRef);
  readonly #destroyRef = inject(DestroyRef);
  readonly #measuredWidth = signal(0);
  /** Changes whenever the points change, so the drawing is rebuilt and its entrance animation plays again. */
  readonly #drawKeyValue = signal(0);

  protected readonly drawKey = this.#drawKeyValue.asReadonly();
  protected readonly gradientId = `price-area-${++chartCount}`;
  protected readonly plotLeft = PLOT_LEFT;
  protected readonly plotTop = PLOT_TOP;
  protected readonly plotBottom = PLOT_BOTTOM;
  protected readonly dateAxisY = DATE_AXIS_Y;
  protected readonly axisTextBaselineOffset = AXIS_TEXT_BASELINE_OFFSET;
  protected readonly endDotRadius = END_DOT_RADIUS;

  protected readonly chartWidth = computed(
    () => this.#measuredWidth() || DEFAULT_WIDTH,
  );
  protected readonly viewBox = computed(
    () => `0 0 ${this.chartWidth()} ${CHART_HEIGHT}`,
  );
  protected readonly plotRight = computed(
    () => this.chartWidth() - PRICE_GUTTER,
  );
  protected readonly priceAxisX = computed(
    () => this.plotRight() + PRICE_LABEL_OFFSET,
  );

  /** Index of the recorded point under the pointer, or null when the pointer is off the chart. */
  protected readonly hoveredIndex = signal<number | null>(null);

  protected readonly scale = computed<PriceScale | null>(() => {
    const prices = this.points().map((point) => point.priceInEuro);
    if (prices.length < 2) {
      return null;
    }

    return priceScaleFor(Math.min(...prices), Math.max(...prices));
  });

  protected readonly coordinates = computed<ChartCoordinate[]>(() => {
    const scale = this.scale();
    const points = this.points();
    if (!scale) {
      return [];
    }
    const stepX = (this.plotRight() - PLOT_LEFT) / (points.length - 1);
    const span = scale.high - scale.low;

    return points.map((point, index) => ({
      x: PLOT_LEFT + index * stepX,
      y:
        PLOT_BOTTOM -
        ((point.priceInEuro - scale.low) / span) * (PLOT_BOTTOM - PLOT_TOP),
    }));
  });

  protected readonly polyline = computed(() =>
    this.coordinates()
      .map(({ x, y }) => `${x.toFixed(1)},${y.toFixed(1)}`)
      .join(' '),
  );

  /** The line closed down to the baseline, filled with the series colour fading to nothing. */
  protected readonly areaPath = computed(() => {
    const coordinates = this.coordinates();
    if (coordinates.length < 2) {
      return '';
    }
    const first = coordinates[0];
    const last = coordinates[coordinates.length - 1];
    const line = coordinates
      .map(({ x, y }) => `L${x.toFixed(1)},${y.toFixed(1)}`)
      .join(' ');

    return `M${first.x.toFixed(1)},${PLOT_BOTTOM} ${line} L${last.x.toFixed(1)},${PLOT_BOTTOM} Z`;
  });

  protected readonly priceTicks = computed<PriceTick[]>(() => {
    const scale = this.scale();
    if (!scale) {
      return [];
    }
    const span = scale.high - scale.low;
    const formatter = new Intl.NumberFormat('en-US', {
      maximumFractionDigits: scale.step < 1 ? 2 : 0,
    });

    return scale.ticks.map((value) => ({
      value,
      y: PLOT_BOTTOM - ((value - scale.low) / span) * (PLOT_BOTTOM - PLOT_TOP),
      label: formatter.format(value),
    }));
  });

  /** The start, the middle (when there are three or more points) and the end of the period. */
  protected readonly dateLabels = computed<DateLabel[]>(() => {
    const points = this.points();
    if (points.length < 2) {
      return [];
    }
    const plotRight = this.plotRight();
    const stepX = (plotRight - PLOT_LEFT) / (points.length - 1);
    const labels: DateLabel[] = [
      { x: PLOT_LEFT, anchor: 'start', text: formatAxisDate(points[0].date) },
    ];
    if (points.length >= 3) {
      const middleIndex = Math.floor((points.length - 1) / 2);
      labels.push({
        x: PLOT_LEFT + middleIndex * stepX,
        anchor: 'middle',
        text: formatAxisDate(points[middleIndex].date),
      });
    }
    labels.push({
      x: plotRight,
      anchor: 'end',
      text: formatAxisDate(points[points.length - 1].date),
    });

    return labels;
  });

  protected readonly endPoint = computed<ChartCoordinate | null>(
    () => this.coordinates().at(-1) ?? null,
  );

  protected readonly hovered = computed<HoveredPoint | null>(() => {
    const index = this.hoveredIndex();
    const coordinate = index === null ? undefined : this.coordinates()[index];
    const point = index === null ? undefined : this.points()[index];
    if (!coordinate || !point) {
      return null;
    }
    const share = coordinate.x / this.chartWidth();

    return {
      x: coordinate.x,
      y: coordinate.y,
      leftPercent: share * 100,
      transform: readoutTransform(share),
      date: point.date,
      priceInEuro: point.priceInEuro,
    };
  });

  constructor() {
    effect(() => {
      this.points();
      untracked(() => this.#drawKeyValue.update((key) => key + 1));
    });
    afterNextRender(() => {
      if (typeof ResizeObserver === 'undefined') {
        return;
      }
      const observer = new ResizeObserver(([entry]) => {
        this.#measuredWidth.set(entry.contentRect.width);
      });
      observer.observe(this.#host.nativeElement);
      this.#destroyRef.onDestroy(() => observer.disconnect());
    });
  }

  /** Follows the pointer to the nearest recorded point. Mouse and touch both arrive as pointer events. */
  protected pointerMoved(event: PointerEvent): void {
    const pointCount = this.points().length;
    if (pointCount < 2) {
      return;
    }
    const bounds = (
      event.currentTarget as SVGSVGElement
    ).getBoundingClientRect();
    if (bounds.width === 0) {
      return;
    }
    const pointerX =
      ((event.clientX - bounds.left) / bounds.width) * this.chartWidth();
    const stepX = (this.plotRight() - PLOT_LEFT) / (pointCount - 1);
    const nearestIndex = Math.round((pointerX - PLOT_LEFT) / stepX);
    this.hoveredIndex.set(Math.min(Math.max(nearestIndex, 0), pointCount - 1));
  }

  protected pointerLeft(): void {
    this.hoveredIndex.set(null);
  }
}
