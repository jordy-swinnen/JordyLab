import { HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { catchError, of } from 'rxjs';
import { GameCatalogApiService } from './gamecatalog-api.service';
import { GameDetail, RomStatus } from './gamecatalog.models';
import { MarkStore } from './mark.store';

@Injectable({ providedIn: 'root' })
export class GameDetailStore {
  readonly #api = inject(GameCatalogApiService);
  readonly #marks = inject(MarkStore);

  readonly #game = signal<GameDetail | null>(null);
  readonly #loading = signal(true);
  readonly #notFound = signal(false);
  readonly #error = signal<string | null>(null);
  readonly #refreshingMetadata = signal(false);
  readonly #refreshingEnrichment = signal(false);
  readonly #savingRomStatus = signal<ReadonlySet<string>>(new Set());
  readonly #romStatusError = signal<string | null>(null);

  readonly game = this.#game.asReadonly();
  readonly loading = this.#loading.asReadonly();
  readonly notFound = this.#notFound.asReadonly();
  readonly error = this.#error.asReadonly();
  readonly refreshingMetadata = this.#refreshingMetadata.asReadonly();
  readonly refreshingEnrichment = this.#refreshingEnrichment.asReadonly();
  /** Copies (by installation id) whose ROM status is being saved. */
  readonly savingRomStatus = this.#savingRomStatus.asReadonly();
  readonly romStatusError = this.#romStatusError.asReadonly();

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
        this.#marks.forget();
        this.#game.set(game);
        this.#loading.set(false);
      });
  }

  /** Says whether the ROM of one machine's copy launches; the page shows the saved answer, not a guess. */
  setRomStatus(installationId: string, status: RomStatus): void {
    const gameId = this.#game()?.id;
    if (!gameId || this.#savingRomStatus().has(installationId)) {
      return;
    }
    this.#romStatusError.set(null);
    this.#savingRomStatus.update((saving) => new Set(saving).add(installationId));
    this.#api.setRomStatus(gameId, installationId, status).subscribe({
      next: (place) => {
        this.#game.update((game) =>
          game
            ? { ...game, places: game.places.map((existing) => (existing.installationId === installationId ? place : existing)) }
            : game,
        );
        this.#doneSaving(installationId);
      },
      error: () => {
        this.#romStatusError.set('Could not save the ROM status. Try again.');
        this.#doneSaving(installationId);
      },
    });
  }

  dismissRomStatusError(): void {
    this.#romStatusError.set(null);
  }

  #doneSaving(installationId: string): void {
    this.#savingRomStatus.update((saving) => {
      const remaining = new Set(saving);
      remaining.delete(installationId);

      return remaining;
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
