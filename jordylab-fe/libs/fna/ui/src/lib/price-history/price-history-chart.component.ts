import { DatePipe, DecimalPipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  signal,
} from '@angular/core';
import { PriceHistoryPoint } from '@jordylab-fe/fna/api';

/** Drawing area in SVG units. The end dot sits inside the margins, so it is never clipped. */
const VIEW_WIDTH = 320;
const VIEW_HEIGHT = 160;
const HORIZONTAL_MARGIN = 6;
const VERTICAL_MARGIN = 10;
const END_DOT_RADIUS = 4;
/** Within this share of the width from either edge, the readout is pinned to that edge so it stays on the chart. */
const EDGE_PIN_SHARE = 0.18;

interface ChartCoordinate {
  x: number;
  y: number;
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

/** One stock's euro price over a period as a single line, the end point marked. The figures sit beside it as text. */
@Component({
  selector: 'lib-price-history-chart',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [DatePipe, DecimalPipe],
  templateUrl: './price-history-chart.component.html',
})
export class PriceHistoryChartComponent {
  points = input.required<PriceHistoryPoint[]>();
  label = input.required<string>();

  protected readonly viewBox = `0 0 ${VIEW_WIDTH} ${VIEW_HEIGHT}`;
  protected readonly viewWidth = VIEW_WIDTH;
  protected readonly horizontalMargin = HORIZONTAL_MARGIN;
  protected readonly verticalMargin = VERTICAL_MARGIN;
  protected readonly baselineY = VIEW_HEIGHT - VERTICAL_MARGIN;
  protected readonly endDotRadius = END_DOT_RADIUS;

  /** Index of the recorded point under the pointer, or null when the pointer is off the chart. */
  protected readonly hoveredIndex = signal<number | null>(null);

  /** Scaled to the period's own lowest and highest price: the scale does not start at zero. */
  protected readonly coordinates = computed<ChartCoordinate[]>(() => {
    const points = this.points();
    if (points.length < 2) {
      return [];
    }
    const prices = points.map((point) => point.priceInEuro);
    const lowest = Math.min(...prices);
    const priceRange = Math.max(...prices) - lowest || 1;
    const stepX = (VIEW_WIDTH - 2 * HORIZONTAL_MARGIN) / (points.length - 1);
    const drawableHeight = VIEW_HEIGHT - 2 * VERTICAL_MARGIN;

    return points.map((point, index) => ({
      x: HORIZONTAL_MARGIN + index * stepX,
      y:
        VERTICAL_MARGIN +
        (1 - (point.priceInEuro - lowest) / priceRange) * drawableHeight,
    }));
  });

  protected readonly polyline = computed(() =>
    this.coordinates()
      .map(({ x, y }) => `${x.toFixed(1)},${y.toFixed(1)}`)
      .join(' '),
  );

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
    const share = coordinate.x / VIEW_WIDTH;

    return {
      x: coordinate.x,
      y: coordinate.y,
      leftPercent: share * 100,
      transform: readoutTransform(share),
      date: point.date,
      priceInEuro: point.priceInEuro,
    };
  });

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
      ((event.clientX - bounds.left) / bounds.width) * VIEW_WIDTH;
    const stepX = (VIEW_WIDTH - 2 * HORIZONTAL_MARGIN) / (pointCount - 1);
    const nearestIndex = Math.round((pointerX - HORIZONTAL_MARGIN) / stepX);
    this.hoveredIndex.set(Math.min(Math.max(nearestIndex, 0), pointCount - 1));
  }

  protected pointerLeft(): void {
    this.hoveredIndex.set(null);
  }
}
