import {
  Component,
  computed,
  ElementRef,
  inject,
  input,
  output,
  signal,
  ChangeDetectionStrategy,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe, DecimalPipe } from '@angular/common';
import {
  PortfolioPositionRow,
  PriceHistory,
  PriceHistoryPeriod,
  WishlistItem,
} from '@jordylab-fe/fna/api';
import {
  EmptyStateComponent,
  PageHeaderComponent,
} from '@jordylab-fe/shared/layout';
import { HlmButtonDirective } from '@spartan-ng/ui-button-helm';
import { PriceHistoryPanelComponent } from '../price-history';

/** Swatch colours, assigned by row position so the allocation bar and the table agree. */
const SWATCHES = [
  'bg-series-1',
  'bg-series-2',
  'bg-series-3',
  'bg-series-4',
  'bg-muted-foreground',
];

interface AllocationSegment {
  ticker: string;
  swatch: string;
  percent: number;
}

@Component({
  selector: 'lib-portfolio-manager-view',
  standalone: true,
  imports: [
    FormsModule,
    DatePipe,
    DecimalPipe,
    HlmButtonDirective,
    EmptyStateComponent,
    PageHeaderComponent,
    PriceHistoryPanelComponent,
  ],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './portfolio-manager-view.component.html',
})
export class PortfolioManagerViewComponent {
  positions = input.required<PortfolioPositionRow[]>();
  wishlist = input.required<WishlistItem[]>();
  wishlistError = input<string | null>(null);
  totalWorth = input.required<number>();
  hasAnyPrices = input.required<boolean>();
  openedPositionId = input<string | null>(null);
  history = input<PriceHistory | null>(null);
  historyLoading = input(false);
  historyError = input<string | null>(null);
  historyPeriod = input<PriceHistoryPeriod>('ONE_YEAR');

  addPosition = output<{ ticker: string; shares: number }>();
  deletePosition = output<string>();
  addWishlistItem = output<string>();
  deleteWishlistItem = output<string>();
  openHistory = output<string>();
  closeHistory = output<void>();
  historyPeriodSelected = output<PriceHistoryPeriod>();
  historyRetry = output<void>();

  readonly #host = inject<ElementRef<HTMLElement>>(ElementRef);

  protected readonly swatch = (index: number): string =>
    SWATCHES[index % SWATCHES.length];

  /** Share of total value per priced position; positions without a price have no slice. */
  protected readonly allocation = computed<AllocationSegment[]>(() => {
    const priced = this.positions()
      .map((position, index) => ({ position, index }))
      .filter(({ position }) => position.value !== null && position.value > 0);
    const total = priced.reduce(
      (sum, { position }) => sum + (position.value ?? 0),
      0,
    );

    return priced.map(({ position, index }) => ({
      ticker: position.ticker,
      swatch: this.swatch(index),
      percent: total === 0 ? 0 : ((position.value ?? 0) / total) * 100,
    }));
  });

  protected readonly openedTicker = computed(
    () =>
      this.positions().find(
        (position) => position.id === this.openedPositionId(),
      )?.ticker ?? '',
  );

  newTicker = signal('');
  newShares = signal<number | null>(null);
  newWishlistTicker = signal('');
  promotingId = signal<string | null>(null);
  promoteShares = signal<number | null>(null);

  onAddPosition(): void {
    const ticker = this.newTicker();
    const shares = this.newShares();
    if (!ticker || shares === null) return;

    this.addPosition.emit({ ticker, shares });
    this.newTicker.set('');
    this.newShares.set(null);
  }

  onAddWishlistItem(): void {
    const ticker = this.newWishlistTicker().trim();
    if (!ticker) return;

    this.addWishlistItem.emit(ticker);
    this.newWishlistTicker.set('');
  }

  startPromoting(item: WishlistItem): void {
    this.promotingId.set(item.id);
    this.promoteShares.set(null);
  }

  cancelPromoting(): void {
    this.promotingId.set(null);
    this.promoteShares.set(null);
  }

  /** Closing returns focus to the History button of the same holding, so keyboard users keep their place. */
  closeHistoryPanel(): void {
    const positionId = this.openedPositionId();
    this.closeHistory.emit();
    if (positionId) {
      this.#host.nativeElement
        .querySelector<HTMLButtonElement>(
          `[data-history-button="${positionId}"]`,
        )
        ?.focus();
    }
  }

  /** Moves a wishlist ticker into held positions, keeping the row (and its cached price) in place. */
  confirmPromoting(item: WishlistItem): void {
    const shares = this.promoteShares();
    if (shares === null || shares <= 0) return;

    this.addPosition.emit({ ticker: item.ticker, shares });
    this.cancelPromoting();
  }
}
