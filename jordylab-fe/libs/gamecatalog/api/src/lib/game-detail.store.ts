import { HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { catchError, of } from 'rxjs';
import { GameCatalogApiService } from './gamecatalog-api.service';
import { GameDetail } from './gamecatalog.models';

@Injectable({ providedIn: 'root' })
export class GameDetailStore {
  readonly #api = inject(GameCatalogApiService);

  readonly #game = signal<GameDetail | null>(null);
  readonly #loading = signal(true);
  readonly #notFound = signal(false);
  readonly #error = signal<string | null>(null);
  readonly #refreshingMetadata = signal(false);
  readonly #refreshingEnrichment = signal(false);

  readonly game = this.#game.asReadonly();
  readonly loading = this.#loading.asReadonly();
  readonly notFound = this.#notFound.asReadonly();
  readonly error = this.#error.asReadonly();
  readonly refreshingMetadata = this.#refreshingMetadata.asReadonly();
  readonly refreshingEnrichment = this.#refreshingEnrichment.asReadonly();

  load(id: string): void {
    this.#game.set(null);
    this.#loading.set(true);
    this.#notFound.set(false);
    this.#error.set(null);

    this.#api
      .getGame(id)
      .pipe(
        catchError((httpError: HttpErrorResponse) => {
          if (httpError.status === 404) {
            this.#notFound.set(true);
          } else {
            this.#error.set('Failed to load the game.');
          }

          return of(null);
        })
      )
      .subscribe((game) => {
        this.#game.set(game);
        this.#loading.set(false);
      });
  }

  refreshMetadata(): void {
    const id = this.#game()?.id;
    if (!id || this.#refreshingMetadata()) {
      return;
    }

    this.#refreshingMetadata.set(true);
    this.#error.set(null);
    this.#api
      .refreshGameMetadata(id)
      .pipe(
        catchError(() => {
          this.#error.set('Failed to refresh the deterministic facts.');

          return of(null);
        })
      )
      .subscribe((game) => {
        this.#refreshingMetadata.set(false);
        if (game) {
          this.#game.set(game);
        }
      });
  }

  refreshEnrichment(): void {
    const id = this.#game()?.id;
    if (!id || this.#refreshingEnrichment()) {
      return;
    }

    this.#refreshingEnrichment.set(true);
    this.#error.set(null);
    this.#api
      .refreshGameEnrichment(id)
      .pipe(
        catchError(() => {
          this.#error.set('Failed to regenerate the description.');

          return of(null);
        })
      )
      .subscribe((game) => {
        this.#refreshingEnrichment.set(false);
        if (game) {
          this.#game.set(game);
        }
      });
  }
}
