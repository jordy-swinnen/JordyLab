import { ErrorStateComponent } from '@jordylab-fe/shared/layout';
import {
  Component,
  inject,
  ChangeDetectionStrategy,
  signal,
} from '@angular/core';
import {
  PortfolioStore,
  PriceHistoryPeriod,
  PriceHistoryStore,
} from '@jordylab-fe/fna/api';
import { PortfolioManagerViewComponent } from './portfolio-manager-view.component';

@Component({
  selector: 'lib-portfolio-manager',
  standalone: true,
  imports: [PortfolioManagerViewComponent, ErrorStateComponent],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './portfolio-manager.component.html',
})
export class PortfolioManagerComponent {
  #store = inject(PortfolioStore);
  #priceHistory = inject(PriceHistoryStore);

  positions = this.#store.positionRows;
  wishlist = this.#store.wishlist;
  error = this.#store.error;
  wishlistError = this.#store.wishlistError;
  totalWorth = this.#store.totalWorth;
  hasAnyPrices = this.#store.hasAnyPrices;

  openedPositionId = signal<string | null>(null);
  historyPeriod = signal<PriceHistoryPeriod>('ONE_YEAR');
  history = this.#priceHistory.history;
  historyLoading = this.#priceHistory.isLoading;
  historyError = this.#priceHistory.error;

  addPosition(event: { ticker: string; shares: number }): void {
    this.#store.upsertPosition(event.ticker, event.shares);
  }

  deletePosition(id: string): void {
    this.#store.removePosition(id);
  }

  addWishlistItem(ticker: string): void {
    this.#store.addWishlistItem(ticker);
  }

  deleteWishlistItem(id: string): void {
    this.#store.removeWishlistItem(id);
  }

  openHistory(positionId: string): void {
    this.openedPositionId.set(positionId);
    this.historyPeriod.set('ONE_YEAR');
    this.#priceHistory.load(positionId, 'ONE_YEAR');
  }

  selectHistoryPeriod(period: PriceHistoryPeriod): void {
    const positionId = this.openedPositionId();
    if (positionId === null) return;

    this.historyPeriod.set(period);
    this.#priceHistory.load(positionId, period);
  }

  closeHistory(): void {
    this.openedPositionId.set(null);
    this.#priceHistory.clear();
  }

  retryHistory(): void {
    const positionId = this.openedPositionId();
    if (positionId === null) return;

    this.#priceHistory.load(positionId, this.historyPeriod());
  }

  reload(): void {
    this.#store.load();
  }
}
