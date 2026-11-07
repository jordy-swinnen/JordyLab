import { HttpClient } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { Subscription } from 'rxjs';
import { PriceHistory, PriceHistoryPeriod } from './fna.models';

/** Recorded price history of one holding (spec 023). Reads what the backend has recorded; it never calls the price source. */
@Injectable({ providedIn: 'root' })
export class PriceHistoryStore {
  readonly #http = inject(HttpClient);
  readonly #history = signal<PriceHistory | null>(null);
  readonly #isLoading = signal(false);
  readonly #error = signal<string | null>(null);
  #pendingRequest: Subscription | null = null;

  readonly history = this.#history.asReadonly();
  readonly isLoading = this.#isLoading.asReadonly();
  readonly error = this.#error.asReadonly();

  /** Loads one holding's history for a period. A newer request cancels an older one still in flight. */
  load(positionId: string, period: PriceHistoryPeriod): void {
    this.#pendingRequest?.unsubscribe();
    this.#isLoading.set(true);
    this.#error.set(null);
    this.#pendingRequest = this.#http
      .get<PriceHistory>(`/api/fna/portfolio/${positionId}/price-history`, {
        params: { period },
      })
      .subscribe({
        next: (history) => {
          this.#history.set(history);
          this.#isLoading.set(false);
        },
        error: () => {
          this.#history.set(null);
          this.#error.set(
            "Couldn't load the price history. Try again in a moment.",
          );
          this.#isLoading.set(false);
        },
      });
  }

  /** Forgets the history on screen and cancels a request still in flight. */
  clear(): void {
    this.#pendingRequest?.unsubscribe();
    this.#pendingRequest = null;
    this.#history.set(null);
    this.#error.set(null);
    this.#isLoading.set(false);
  }
}
