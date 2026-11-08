import { DatePipe, DecimalPipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  inject,
  Injector,
  input,
  output,
} from '@angular/core';
import {
  PortfolioPositionRow,
  PriceHistory,
  PriceHistoryPeriod,
} from '@jordylab-fe/fna/api';
import { HlmButtonDirective } from '@spartan-ng/ui-button-helm';
import { PriceHistoryPanelComponent } from '../price-history';
import { returnFocusToHistoryButton } from './history-focus';

/** Swatch colours, assigned by row position so the allocation bar and the table agree. */
const SWATCHES = [
  'bg-series-1',
  'bg-series-2',
  'bg-series-3',
  'bg-series-4',
  'bg-muted-foreground',
];

/** The swatch class of the holding at this position in the list. */
export function swatchFor(index: number): string {
  return SWATCHES[index % SWATCHES.length];
}

/** The holdings: one row each, with the price history opening in place of the row (spec 023). */
@Component({
  selector: 'lib-holdings-table',
  standalone: true,
  imports: [
    DatePipe,
    DecimalPipe,
    HlmButtonDirective,
    PriceHistoryPanelComponent,
  ],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './holdings-table.component.html',
  styleUrl: './row-motion.css',
})
export class HoldingsTableComponent {
  positions = input.required<PortfolioPositionRow[]>();
  openedPositionId = input<string | null>(null);
  history = input<PriceHistory | null>(null);
  historyLoading = input(false);
  historyError = input<string | null>(null);
  historyPeriod = input<PriceHistoryPeriod>('ONE_YEAR');

  openHistory = output<string>();
  closeHistory = output<void>();
  historyPeriodSelected = output<PriceHistoryPeriod>();
  historyRetry = output<void>();
  deletePosition = output<string>();

  readonly #host = inject<ElementRef<HTMLElement>>(ElementRef);
  readonly #injector = inject(Injector);

  protected readonly swatch = swatchFor;

  /** Closing returns focus to the History button of the same row once that row has been rendered again. */
  protected closeHistoryPanel(): void {
    const positionId = this.openedPositionId();
    this.closeHistory.emit();
    returnFocusToHistoryButton(
      this.#host.nativeElement,
      this.#injector,
      positionId,
    );
  }
}
