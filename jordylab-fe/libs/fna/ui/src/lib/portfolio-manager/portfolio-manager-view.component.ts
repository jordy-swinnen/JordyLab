import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  output,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DecimalPipe } from '@angular/common';
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
import {
  HoldingsTableComponent,
  swatchFor,
  WishlistTableComponent,
} from '../positions-table';

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
    DecimalPipe,
    HlmButtonDirective,
    EmptyStateComponent,
    PageHeaderComponent,
    HoldingsTableComponent,
    WishlistTableComponent,
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
      swatch: swatchFor(index),
      percent: total === 0 ? 0 : ((position.value ?? 0) / total) * 100,
    }));
  });

  newTicker = signal('');
  newShares = signal<number | null>(null);
  newWishlistTicker = signal('');

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
}
