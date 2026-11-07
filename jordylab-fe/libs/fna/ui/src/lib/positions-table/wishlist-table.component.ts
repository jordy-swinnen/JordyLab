import { DatePipe, DecimalPipe } from '@angular/common';
import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  inject,
  Injector,
  input,
  output,
  signal,
} from '@angular/core';
import {
  PriceHistory,
  PriceHistoryPeriod,
  WishlistItem,
} from '@jordylab-fe/fna/api';
import { HlmButtonDirective } from '@spartan-ng/ui-button-helm';
import { PriceHistoryPanelComponent } from '../price-history';
import { ROW_MOTION_STYLES } from './row-motion';

/** The wishlist: stocks not owned yet, with the price history opening in place of the row (spec 023). */
@Component({
  selector: 'lib-wishlist-table',
  standalone: true,
  imports: [
    DatePipe,
    DecimalPipe,
    HlmButtonDirective,
    PriceHistoryPanelComponent,
  ],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './wishlist-table.component.html',
  styles: [ROW_MOTION_STYLES],
})
export class WishlistTableComponent {
  wishlist = input.required<WishlistItem[]>();
  openedPositionId = input<string | null>(null);
  history = input<PriceHistory | null>(null);
  historyLoading = input(false);
  historyError = input<string | null>(null);
  historyPeriod = input<PriceHistoryPeriod>('ONE_YEAR');

  openHistory = output<string>();
  closeHistory = output<void>();
  historyPeriodSelected = output<PriceHistoryPeriod>();
  historyRetry = output<void>();
  deleteWishlistItem = output<string>();
  /** A wishlist item promoted to a holding: the ticker and the shares it is bought with. */
  addPosition = output<{ ticker: string; shares: number }>();

  readonly #host = inject<ElementRef<HTMLElement>>(ElementRef);
  readonly #injector = inject(Injector);

  promotingId = signal<string | null>(null);
  promoteShares = signal<number | null>(null);

  startPromoting(item: WishlistItem): void {
    this.promotingId.set(item.id);
    this.promoteShares.set(null);
  }

  cancelPromoting(): void {
    this.promotingId.set(null);
    this.promoteShares.set(null);
  }

  /** Moves a wishlist ticker into held positions, keeping the row (and its cached price) in place. */
  confirmPromoting(item: WishlistItem): void {
    const shares = this.promoteShares();
    if (shares === null || shares <= 0) return;

    this.addPosition.emit({ ticker: item.ticker, shares });
    this.cancelPromoting();
  }

  /** Closing returns focus to the History button of the same row once that row has been rendered again. */
  protected closeHistoryPanel(): void {
    const positionId = this.openedPositionId();
    this.closeHistory.emit();
    if (positionId) {
      afterNextRender(
        () =>
          this.#host.nativeElement
            .querySelector<HTMLButtonElement>(
              `[data-history-button="${positionId}"]`,
            )
            ?.focus(),
        { injector: this.#injector },
      );
    }
  }
}
