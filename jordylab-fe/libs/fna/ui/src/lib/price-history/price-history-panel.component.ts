import { DatePipe, DecimalPipe } from '@angular/common';
import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  input,
  output,
  viewChild,
} from '@angular/core';
import {
  PriceHistory,
  PriceHistoryPeriod,
  PriceHistorySummary,
} from '@jordylab-fe/fna/api';
import { ErrorStateComponent } from '@jordylab-fe/shared/layout';
import { HlmButtonDirective } from '@spartan-ng/ui-button-helm';
import { PriceHistoryChartComponent } from './price-history-chart.component';

/** The period choices in the order the investor reads them. */
const PERIOD_CHOICES: { period: PriceHistoryPeriod; label: string }[] = [
  { period: 'ONE_MONTH', label: '1M' },
  { period: 'THREE_MONTHS', label: '3M' },
  { period: 'ONE_YEAR', label: '1Y' },
  { period: 'MAX', label: 'Max' },
];

/**
 * The history of one holding: period choice, chart, the figures as text, and the loading, empty, short and failed
 * states (spec 023). It shows recorded prices only; it never says buy, sell or hold (FR-009).
 */
@Component({
  selector: 'lib-price-history-panel',
  standalone: true,
  imports: [
    DatePipe,
    DecimalPipe,
    ErrorStateComponent,
    HlmButtonDirective,
    PriceHistoryChartComponent,
  ],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './price-history-panel.component.html',
})
export class PriceHistoryPanelComponent {
  ticker = input.required<string>();
  history = input<PriceHistory | null>(null);
  isLoading = input(false);
  error = input<string | null>(null);
  selectedPeriod = input<PriceHistoryPeriod>('ONE_YEAR');

  periodSelected = output<PriceHistoryPeriod>();
  closed = output<void>();
  retried = output<void>();

  protected readonly periodChoices = PERIOD_CHOICES;
  protected readonly heading =
    viewChild.required<ElementRef<HTMLHeadingElement>>('heading');

  /** The window begins before the first recorded day: the history starts later than the period (FR-006). */
  protected readonly startsAfterWindow = computed(() => {
    const history = this.history();

    return (
      history?.windowStartDate != null &&
      history.dataStartDate != null &&
      history.dataStartDate > history.windowStartDate
    );
  });

  constructor() {
    afterNextRender(() => this.heading().nativeElement.focus());
  }

  protected absolute(value: number): number {
    return Math.abs(value);
  }

  /** Names the start, the end and the change, so the drawing says what the figures say. */
  protected chartLabel(summary: PriceHistorySummary): string {
    const range =
      `Euro price from ${summary.startDate}, ${summary.startPriceInEuro.toFixed(2)} euro, ` +
      `to ${summary.endDate}, ${summary.endPriceInEuro.toFixed(2)} euro`;
    if (summary.changeInEuro === null || summary.changePercent === null) {
      return `${range}. Not enough history yet to show a change`;
    }
    const direction = summary.changeInEuro >= 0 ? 'up' : 'down';

    return `${range}, ${direction} ${Math.abs(summary.changeInEuro).toFixed(2)} euro or ${Math.abs(summary.changePercent).toFixed(2)} percent`;
  }
}
