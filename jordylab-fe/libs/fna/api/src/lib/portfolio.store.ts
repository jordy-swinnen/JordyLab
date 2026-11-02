import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { catchError, of, tap } from 'rxjs';
import { PortfolioPosition, PortfolioPositionRow, WishlistItem } from './fna.models';

@Injectable({ providedIn: 'root' })
export class PortfolioStore {
  readonly #http = inject(HttpClient);
  readonly #apiUrl = '/api/fna/portfolio';
  readonly #wishlistApiUrl = '/api/fna/wishlist';

  readonly #positions = signal<PortfolioPosition[]>([]);
  readonly #wishlist = signal<WishlistItem[]>([]);
  readonly #error = signal<string | null>(null);
  readonly #wishlistError = signal<string | null>(null);

  readonly positions = this.#positions.asReadonly();
  readonly wishlist = this.#wishlist.asReadonly();
  readonly error = this.#error.asReadonly();
  readonly wishlistError = this.#wishlistError.asReadonly();

  readonly positionRows = computed<PortfolioPositionRow[]>(() =>
    this.#positions().map((position) => ({
      ...position,
      value: position.lastPrice === null ? null : position.shareCount * position.lastPrice,
    }))
  );

  readonly totalWorth = computed(() =>
    this.#positions().reduce((sum, position) => {
      if (position.lastPrice !== null) {
        return sum + position.shareCount * position.lastPrice;
      }

      return sum;
    }, 0)
  );

  readonly hasAnyPrices = computed(() => this.#positions().some((position) => position.lastPrice !== null));

  constructor() {
    this.load();
  }

  load(): void {
    this.#error.set(null);
    this.#loadPositions();
    this.#loadWishlist();
  }

  upsertPosition(ticker: string, shares: number): void {
    this.#http
      .put<PortfolioPosition>(`${this.#apiUrl}/${ticker}`, null, { params: { shares } })
      .subscribe(() => this.load());
  }

  removePosition(id: string): void {
    this.#http.delete<void>(`${this.#apiUrl}/${id}`).subscribe(() => this.load());
  }

  addWishlistItem(ticker: string): void {
    this.#wishlistError.set(null);

    this.#http
      .put<WishlistItem>(`${this.#wishlistApiUrl}/${ticker}`, null)
      .pipe(
        catchError((response: HttpErrorResponse) => {
          this.#wishlistError.set(
            response.status === 409
              ? 'This ticker is already in your portfolio.'
              : 'Failed to add ticker to the wishlist.'
          );

          return of(null);
        })
      )
      .subscribe((item) => {
        if (item !== null) {
          this.load();
        }
      });
  }

  removeWishlistItem(id: string): void {
    this.#http.delete<void>(`${this.#wishlistApiUrl}/${id}`).subscribe(() => this.load());
  }

  #loadPositions(): void {
    this.#http
      .get<PortfolioPosition[]>(this.#apiUrl)
      .pipe(
        tap((positions) => this.#positions.set(positions)),
        catchError(() => {
          this.#error.set('Failed to load portfolio.');

          return of([]);
        })
      )
      .subscribe();
  }

  #loadWishlist(): void {
    this.#http
      .get<WishlistItem[]>(this.#wishlistApiUrl)
      .pipe(
        tap((wishlist) => this.#wishlist.set(wishlist)),
        catchError(() => {
          this.#error.set('Failed to load the wishlist.');

          return of([]);
        })
      )
      .subscribe();
  }
}
