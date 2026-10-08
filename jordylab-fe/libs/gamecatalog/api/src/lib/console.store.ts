import { HttpErrorResponse } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, debounceTime, distinctUntilChanged, Observable, of, Subject, switchMap, tap } from 'rxjs';
import { GameCatalogApiService } from './gamecatalog-api.service';
import {
  ConsoleBulkItem,
  ConsoleBulkLine,
  ConsoleBulkSummary,
  ConsoleGameListItem,
  ConsoleImpact,
  ConsoleSearchResult,
  GameConsole,
  KnownConsole,
} from './gamecatalog.models';

const SUGGEST_DEBOUNCE_MS = 200;
const SEARCH_DEBOUNCE_MS = 300;
const MIN_SEARCH_LENGTH = 2;

const ERROR_MESSAGES: Record<string, string> = {
  NAME_TAKEN: 'That name is already used by another console or host.',
  NAME_TOO_LONG: 'Names can be at most 40 characters.',
  ALREADY_ON_CONSOLE: 'That game is already on this console.',
  GAME_NOT_FOUND: 'IGDB does not know that game.',
  NOT_FOUND: 'That console or game no longer exists.',
};

function messageFor(error: unknown, fallback: string): string {
  const reason = (error as HttpErrorResponse | undefined)?.error?.reason as string | undefined;

  return (reason && ERROR_MESSAGES[reason]) || fallback;
}

/**
 * State of the Consoles page: the consoles an admin registered, the autocomplete of well-known ones, the games on the
 * selected console with the IGDB search that adds them, and the paste-a-list flow. Every failure shows a plain message.
 */
@Injectable({ providedIn: 'root' })
export class ConsoleStore {
  readonly #api = inject(GameCatalogApiService);
  readonly #suggestInput = new Subject<string>();
  readonly #searchInput = new Subject<string>();

  readonly #consoles = signal<GameConsole[]>([]);
  readonly #loading = signal(true);
  readonly #error = signal<string | null>(null);
  readonly #suggestions = signal<KnownConsole[]>([]);
  readonly #selectedId = signal<string | null>(null);
  readonly #games = signal<ConsoleGameListItem[]>([]);
  readonly #loadingGames = signal(false);
  readonly #results = signal<ConsoleSearchResult[]>([]);
  readonly #searching = signal(false);
  readonly #searchedQuery = signal<string | null>(null);
  readonly #busy = signal(false);
  readonly #removal = signal<{ console: GameConsole; impact: ConsoleImpact } | null>(null);
  readonly #bulkLines = signal<ConsoleBulkLine[]>([]);
  readonly #bulkSummary = signal<ConsoleBulkSummary | null>(null);
  readonly #lastAdded = signal<string | null>(null);

  readonly consoles = this.#consoles.asReadonly();
  readonly loading = this.#loading.asReadonly();
  readonly error = this.#error.asReadonly();
  readonly suggestions = this.#suggestions.asReadonly();
  readonly selectedId = this.#selectedId.asReadonly();
  readonly games = this.#games.asReadonly();
  readonly loadingGames = this.#loadingGames.asReadonly();
  readonly results = this.#results.asReadonly();
  readonly searching = this.#searching.asReadonly();
  readonly busy = this.#busy.asReadonly();
  readonly removal = this.#removal.asReadonly();
  readonly bulkLines = this.#bulkLines.asReadonly();
  readonly bulkSummary = this.#bulkSummary.asReadonly();
  /** The title just added, for a one-line confirmation. */
  readonly lastAdded = this.#lastAdded.asReadonly();

  readonly selected = computed(() => this.#consoles().find((console) => console.id === this.#selectedId()) ?? null);
  readonly hasConsoles = computed(() => this.#consoles().length > 0);

  /** The searched text when a finished IGDB search found nothing, to offer "add it by title". */
  readonly noMatchFor = computed(() => {
    const query = this.#searchedQuery();

    return query && !this.#searching() && this.#results().length === 0 ? query : null;
  });

  constructor() {
    this.#suggestInput
      .pipe(
        debounceTime(SUGGEST_DEBOUNCE_MS),
        distinctUntilChanged(),
        switchMap((query) => this.#api.getKnownConsoles(query).pipe(catchError(() => of([] as KnownConsole[])))),
        takeUntilDestroyed(),
      )
      .subscribe((known) => this.#suggestions.set(known));

    this.#searchInput
      .pipe(
        debounceTime(SEARCH_DEBOUNCE_MS),
        distinctUntilChanged(),
        switchMap((query) => this.#runSearch(query)),
        takeUntilDestroyed(),
      )
      .subscribe((results) => {
        this.#searching.set(false);
        if (results) {
          this.#results.set(results);
        }
      });
  }

  load(): void {
    this.#loading.set(true);
    this.#error.set(null);
    this.#api
      .getConsoles()
      .pipe(
        catchError(() => {
          this.#error.set('Failed to load the consoles.');

          return of([] as GameConsole[]);
        }),
      )
      .subscribe((consoles) => {
        this.#consoles.set(consoles);
        this.#loading.set(false);
        const selected = this.#selectedId();
        if (consoles.length > 0 && (!selected || !consoles.some((console) => console.id === selected))) {
          this.selectConsole(consoles[0].id);
        } else if (consoles.length === 0) {
          this.#selectedId.set(null);
          this.#games.set([]);
        }
      });
  }

  suggest(query: string): void {
    this.#suggestInput.next(query);
  }

  addConsole(platform: string, name: string): void {
    const trimmed = platform.trim();
    if (!trimmed || this.#busy()) {
      return;
    }
    this.#act(this.#api.addConsole(trimmed, name.trim() || null), 'Failed to add the console.', (added) => {
      this.#consoles.update((list) => [...list, added].sort((a, b) => a.name.localeCompare(b.name)));
      this.selectConsole(added.id);
    });
  }

  renameConsole(id: string, name: string): void {
    this.#act(this.#api.renameConsole(id, name.trim()), 'Failed to rename the console.', (renamed) =>
      this.#consoles.update((list) => list.map((console) => (console.id === id ? renamed : console))),
    );
  }

  requestRemoval(console: GameConsole): void {
    this.#error.set(null);
    this.#api
      .getConsoleImpact(console.id)
      .pipe(
        catchError((error: unknown) => {
          this.#error.set(messageFor(error, 'Could not check what removing this console would do.'));

          return of(null);
        }),
      )
      .subscribe((impact) => {
        if (impact) {
          this.#removal.set({ console, impact });
        }
      });
  }

  cancelRemoval(): void {
    this.#removal.set(null);
  }

  confirmRemoval(): void {
    const pending = this.#removal();
    if (!pending) {
      return;
    }
    this.#act(this.#api.removeConsole(pending.console.id), 'Failed to remove the console.', () => {
      this.#removal.set(null);
      this.#consoles.update((list) => list.filter((console) => console.id !== pending.console.id));
      if (this.#selectedId() === pending.console.id) {
        const next = this.#consoles()[0]?.id ?? null;
        this.#selectedId.set(null);
        this.#games.set([]);
        if (next) {
          this.selectConsole(next);
        }
      }
    });
  }

  selectConsole(id: string): void {
    this.#selectedId.set(id);
    this.#results.set([]);
    this.#searchedQuery.set(null);
    this.#lastAdded.set(null);
    this.#loadGames(id);
  }

  search(query: string): void {
    this.#searchInput.next(query);
  }

  addGame(game: { igdbGameId: number } | { title: string }): void {
    const id = this.#selectedId();
    if (!id || this.#busy()) {
      return;
    }
    this.#act(this.#api.addConsoleGame(id, game), 'Failed to add the game.', (added) => {
      this.#lastAdded.set(added.title);
      this.#results.set([]);
      this.#searchedQuery.set(null);
      this.#bumpCount(id, 1);
      this.#loadGames(id);
    });
  }

  relinkGame(gameId: string, igdbGameId: number): void {
    const id = this.#selectedId();
    if (!id) {
      return;
    }
    this.#act(this.#api.relinkConsoleGame(id, gameId, igdbGameId), 'Failed to relink the game.', () => {
      this.#loadGames(id);
      this.load();
    });
  }

  removeGame(gameId: string): void {
    const id = this.#selectedId();
    if (!id) {
      return;
    }
    this.#act(this.#api.removeConsoleGame(id, gameId), 'Failed to remove the game.', () => {
      this.#games.update((list) => list.filter((game) => game.gameId !== gameId));
      this.#bumpCount(id, -1);
    });
  }

  previewBulk(text: string): void {
    const id = this.#selectedId();
    const lines = text.split(/\r?\n/).map((line) => line.trim()).filter((line) => line.length > 0);
    if (!id || lines.length === 0 || this.#busy()) {
      return;
    }
    this.#bulkSummary.set(null);
    this.#act(this.#api.previewConsoleBulk(id, lines), 'Failed to review the list.', (reviewed) =>
      this.#bulkLines.set(reviewed),
    );
  }

  confirmBulk(items: ConsoleBulkItem[]): void {
    const id = this.#selectedId();
    if (!id || items.length === 0 || this.#busy()) {
      return;
    }
    this.#act(this.#api.confirmConsoleBulk(id, items), 'Failed to add the games.', (summary) => {
      this.#bulkSummary.set(summary);
      this.#bulkLines.set([]);
      this.load();
    });
  }

  resetBulk(): void {
    this.#bulkLines.set([]);
    this.#bulkSummary.set(null);
  }

  #runSearch(query: string): Observable<ConsoleSearchResult[] | null> {
    const id = this.#selectedId();
    const trimmed = query.trim();
    if (!id || trimmed.length < MIN_SEARCH_LENGTH) {
      this.#results.set([]);
      this.#searchedQuery.set(null);
      this.#searching.set(false);

      return of(null);
    }
    this.#searching.set(true);
    this.#error.set(null);

    return this.#api.searchConsoleGames(id, trimmed).pipe(
      tap(() => this.#searchedQuery.set(trimmed)),
      catchError(() => {
        this.#error.set('The search failed. Please try again.');

        return of(null);
      }),
    );
  }

  #loadGames(id: string): void {
    this.#loadingGames.set(true);
    this.#api
      .getConsoleGames(id)
      .pipe(catchError(() => of([] as ConsoleGameListItem[])))
      .subscribe((games) => {
        if (this.#selectedId() === id) {
          this.#games.set(games);
        }
        this.#loadingGames.set(false);
      });
  }

  #bumpCount(id: string, delta: number): void {
    this.#consoles.update((list) =>
      list.map((console) => (console.id === id ? { ...console, gameCount: Math.max(0, console.gameCount + delta) } : console)),
    );
  }

  /** Runs one request; a 204 answers with a null body, so success is tracked apart from the body. */
  #act<T>(request: Observable<T>, fallback: string, onSuccess: (response: T) => void): void {
    this.#busy.set(true);
    this.#error.set(null);
    let failed = false;
    request
      .pipe(
        catchError((error: unknown) => {
          failed = true;
          this.#error.set(messageFor(error, fallback));

          return of(null as T);
        }),
      )
      .subscribe((response) => {
        this.#busy.set(false);
        if (!failed) {
          onSuccess(response);
        }
      });
  }
}
