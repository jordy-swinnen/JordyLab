import { computed, inject, Injectable, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, debounceTime, distinctUntilChanged, of, Subject, switchMap } from 'rxjs';
import { GameCatalogApiService } from './gamecatalog-api.service';
import { SwitchGameFormat, SwitchSearchResult } from './gamecatalog.models';

const SEARCH_DEBOUNCE_MS = 300;

export type SwitchAddMode = 'search' | 'manual';

@Injectable({ providedIn: 'root' })
export class SwitchGameStore {
  readonly #api = inject(GameCatalogApiService);
  readonly #searchInput = new Subject<string>();

  readonly #results = signal<SwitchSearchResult[]>([]);
  readonly #selectedResult = signal<SwitchSearchResult | null>(null);
  readonly #manualTitle = signal('');
  readonly #format = signal<SwitchGameFormat>('PHYSICAL');
  readonly #mode = signal<SwitchAddMode>('search');
  readonly #loading = signal(false);
  readonly #error = signal<string | null>(null);
  readonly #adding = signal(false);
  readonly #addError = signal<string | null>(null);
  readonly #added = signal(false);
  /** The query whose search last finished (null until one does) — drives the "no match" prompt (009 US2). */
  readonly #searchedQuery = signal<string | null>(null);
  #pendingQuery = '';

  readonly results = this.#results.asReadonly();
  readonly selectedResult = this.#selectedResult.asReadonly();
  readonly manualTitle = this.#manualTitle.asReadonly();
  readonly format = this.#format.asReadonly();
  readonly mode = this.#mode.asReadonly();
  readonly loading = this.#loading.asReadonly();
  readonly error = this.#error.asReadonly();
  readonly adding = this.#adding.asReadonly();
  readonly addError = this.#addError.asReadonly();
  readonly added = this.#added.asReadonly();

  /** The query to offer as a manual title when a finished search found nothing on IGDB (009 US2, T039). */
  readonly noMatchFor = computed(() => {
    const query = this.#searchedQuery();
    if (this.#mode() !== 'search' || this.#loading() || this.#error() || !query || this.#results().length > 0) {
      return null;
    }

    return query;
  });

  readonly canAdd = computed(() => {
    if (this.#adding()) {
      return false;
    }
    if (this.#mode() === 'search') {
      return this.#selectedResult() != null;
    }

    return this.#manualTitle().trim().length > 0;
  });

  constructor() {
    this.#searchInput
      .pipe(
        debounceTime(SEARCH_DEBOUNCE_MS),
        distinctUntilChanged(),
        switchMap((query) => {
          const trimmed = query.trim();
          if (trimmed.length === 0) {
            this.#results.set([]);
            this.#loading.set(false);
            this.#error.set(null);

            return of(null);
          }
          this.#loading.set(true);
          this.#error.set(null);
          this.#pendingQuery = trimmed;

          return this.#api.searchSwitchGames(trimmed).pipe(
            catchError(() => {
              this.#error.set('Search failed. Please try again.');

              return of(null);
            })
          );
        }),
        takeUntilDestroyed()
      )
      .subscribe((results) => {
        this.#loading.set(false);
        if (results) {
          this.#results.set(results);
          this.#searchedQuery.set(this.#pendingQuery);
        }
      });
  }

  search(query: string): void {
    this.#searchInput.next(query);
  }

  selectResult(result: SwitchSearchResult | null): void {
    this.#selectedResult.set(result);
    if (result != null) {
      this.#manualTitle.set('');
    }
  }

  setManualTitle(title: string): void {
    this.#manualTitle.set(title);
    if (title.trim().length > 0) {
      this.#selectedResult.set(null);
    }
  }

  setFormat(format: SwitchGameFormat): void {
    this.#format.set(format);
  }

  addNoMatchManually(): void {
    const title = this.noMatchFor();
    if (!title) {
      return;
    }
    this.setMode('manual');
    this.#manualTitle.set(title);
  }

  setMode(mode: SwitchAddMode): void {
    this.#mode.set(mode);
    this.#selectedResult.set(null);
    this.#manualTitle.set('');
    this.#results.set([]);
    this.#searchedQuery.set(null);
    this.#error.set(null);
    this.#addError.set(null);
  }

  addGame(): void {
    if (!this.canAdd()) {
      return;
    }
    this.#adding.set(true);
    this.#addError.set(null);
    const mode = this.#mode();
    const result = this.#selectedResult();
    const title = this.#manualTitle().trim();
    const format = this.#format();

    this.#api
      .addSwitchGame(mode === 'search' ? result!.igdbGameId : null, title || null, format)
      .pipe(
        catchError((error: { error?: { message?: string } }) => {
          this.#addError.set(error.error?.message ?? 'Failed to add game.');

          return of(null);
        })
      )
      .subscribe((response) => {
        this.#adding.set(false);
        if (response) {
          this.#added.set(true);
        }
      });
  }

  reset(): void {
    this.#results.set([]);
    this.#searchedQuery.set(null);
    this.#selectedResult.set(null);
    this.#manualTitle.set('');
    this.#format.set('PHYSICAL');
    this.#mode.set('search');
    this.#loading.set(false);
    this.#error.set(null);
    this.#adding.set(false);
    this.#addError.set(null);
    this.#added.set(false);
  }
}
