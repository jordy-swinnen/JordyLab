import { HttpClient } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { catchError, of, tap } from 'rxjs';
import { PortfolioPosition } from './fna.models';

@Injectable({ providedIn: 'root' })
export class PortfolioStore {
  readonly #http = inject(HttpClient);
  readonly #apiUrl = '/api/fna/portfolio';

  readonly #positions = signal<PortfolioPosition[]>([]);
  readonly #error = signal<string | null>(null);

  readonly positions = this.#positions.asReadonly();
  readonly error = this.#error.asReadonly();

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

  upsertPosition(ticker: string, shares: number): void {
    this.#http
      .put<PortfolioPosition>(`${this.#apiUrl}/${ticker}`, null, { params: { shares } })
      .subscribe(() => this.load());
  }

  removePosition(id: string): void {
    this.#http.delete<void>(`${this.#apiUrl}/${id}`).subscribe(() => this.load());
  }
}
