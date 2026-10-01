import { HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { catchError, map, of } from 'rxjs';
import { GameCatalogApiService } from './gamecatalog-api.service';
import { GameDetail, SwitchGameFormat, SwitchGameUpdate, SwitchSearchResult } from './gamecatalog.models';

@Injectable({ providedIn: 'root' })
export class GameDetailStore {
  readonly #api = inject(GameCatalogApiService);

  readonly #game = signal<GameDetail | null>(null);
  readonly #loading = signal(true);
  readonly #notFound = signal(false);
  readonly #error = signal<string | null>(null);
  readonly #refreshingMetadata = signal(false);
  readonly #refreshingEnrichment = signal(false);
  readonly #savingSwitch = signal(false);
  readonly #removed = signal(false);
  readonly #relinkCandidates = signal<SwitchSearchResult[]>([]);

  readonly game = this.#game.asReadonly();
  readonly loading = this.#loading.asReadonly();
  readonly notFound = this.#notFound.asReadonly();
  readonly error = this.#error.asReadonly();
  readonly refreshingMetadata = this.#refreshingMetadata.asReadonly();
  readonly refreshingEnrichment = this.#refreshingEnrichment.asReadonly();
  readonly savingSwitch = this.#savingSwitch.asReadonly();
  /** True once the Switch game was removed — the page navigates back to the library. */
  readonly removed = this.#removed.asReadonly();
  readonly relinkCandidates = this.#relinkCandidates.asReadonly();

  load(id: string): void {
    this.#game.set(null);
    this.#loading.set(true);
    this.#notFound.set(false);
    this.#error.set(null);
    this.#removed.set(false);
    this.#relinkCandidates.set([]);

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

  changeSwitchFormat(format: SwitchGameFormat): void {
    this.#saveSwitch({ format });
  }

  relinkSwitchGame(igdbGameId: number): void {
    this.#saveSwitch({ igdbGameId });
  }

  searchRelinkCandidates(query: string): void {
    if (query.trim().length < 3) {
      this.#relinkCandidates.set([]);

      return;
    }

    this.#api
      .searchSwitchGames(query.trim())
      .pipe(catchError(() => of([])))
      .subscribe((results) => this.#relinkCandidates.set(results));
  }

  removeSwitchGame(): void {
    const id = this.#game()?.id;
    if (!id || this.#savingSwitch()) {
      return;
    }

    this.#savingSwitch.set(true);
    this.#error.set(null);
    // HttpClient emits null for 204 No Content, so success is an explicit flag rather than the body.
    this.#api
      .deleteSwitchGame(id)
      .pipe(
        map(() => true),
        catchError(() => {
          this.#error.set('Failed to remove the Switch game.');

          return of(false);
        })
      )
      .subscribe((deleted) => {
        this.#savingSwitch.set(false);
        this.#removed.set(deleted);
      });
  }

  #saveSwitch(update: SwitchGameUpdate): void {
    const id = this.#game()?.id;
    if (!id || this.#savingSwitch()) {
      return;
    }

    this.#savingSwitch.set(true);
    this.#error.set(null);
    this.#api
      .updateSwitchGame(id, update)
      .pipe(
        catchError(() => {
          this.#error.set('Failed to save the Switch game.');

          return of(null);
        })
      )
      .subscribe((response) => {
        this.#savingSwitch.set(false);
        if (response) {
          this.#relinkCandidates.set([]);
          this.#reload(id);
        }
      });
  }

  #reload(id: string): void {
    this.#api
      .getGame(id)
      .pipe(catchError(() => of(null)))
      .subscribe((game) => {
        if (game) {
          this.#game.set(game);
        }
      });
  }
}
