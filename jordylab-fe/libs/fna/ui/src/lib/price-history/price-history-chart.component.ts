import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
} from '@angular/core';
import { PriceHistoryPoint } from '@jordylab-fe/fna/api';

/** Drawing area in SVG units. The end dot sits inside the margins, so it is never clipped. */
const VIEW_WIDTH = 320;
const VIEW_HEIGHT = 160;
const HORIZONTAL_MARGIN = 6;
const VERTICAL_MARGIN = 10;
const END_DOT_RADIUS = 4;

interface ChartCoordinate {
  x: number;
  y: number;
}

/** One stock's euro price over a period as a single line, the end point marked. The figures sit beside it as text. */
@Component({
  selector: 'lib-price-history-chart',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './price-history-chart.component.html',
})
export class PriceHistoryChartComponent {
  points = input.required<PriceHistoryPoint[]>();
  label = input.required<string>();

  protected readonly viewBox = `0 0 ${VIEW_WIDTH} ${VIEW_HEIGHT}`;
  protected readonly viewWidth = VIEW_WIDTH;
  protected readonly horizontalMargin = HORIZONTAL_MARGIN;
  protected readonly baselineY = VIEW_HEIGHT - VERTICAL_MARGIN;
  protected readonly endDotRadius = END_DOT_RADIUS;

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
}
