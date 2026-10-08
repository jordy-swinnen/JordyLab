import { HttpErrorResponse } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { catchError, of } from 'rxjs';
import { GameCatalogApiService } from './gamecatalog-api.service';
import { RefreshRun, RefreshRunKind } from './gamecatalog.models';

const POLL_INTERVAL_MS = 2000;
const KINDS: readonly RefreshRunKind[] = ['DATA', 'AI'];

/**
 * The admin's two bulk refreshes. A run lives on the server, so leaving the page does not lose it: the store asks for the
 * latest run of each kind when it is created and then every two seconds for as long as one is running. The AI run costs money,
 * so starting it first asks the server what it would cost ({@code aiCostQuote}) and only starts once confirmed.
 */
@Injectable({ providedIn: 'root' })
export class RefreshRunStore {
  readonly #api = inject(GameCatalogApiService);

  readonly #runs = signal<Readonly<Record<RefreshRunKind, RefreshRun | null>>>({ DATA: null, AI: null });
  readonly #starting = signal<RefreshRunKind | null>(null);
  readonly #aiCostQuote = signal<number | null>(null);
  readonly #error = signal<string | null>(null);
  #pollTimer: ReturnType<typeof setTimeout> | null = null;

  readonly dataRun = computed(() => this.#runs().DATA);
  readonly aiRun = computed(() => this.#runs().AI);
  readonly starting = this.#starting.asReadonly();
  /** How many paid model calls the AI run would make, while the confirmation dialog is open; null otherwise. */
  readonly aiCostQuote = this.#aiCostQuote.asReadonly();
  readonly error = this.#error.asReadonly();

  constructor() {
    this.loadCurrent();
  }

  loadCurrent(): void {
    for (const kind of KINDS) {
      this.#api
        .getCurrentRefreshRun(kind)
        .pipe(catchError(() => of(null)))
        .subscribe((run) => this.#remember(kind, run));
    }
  }

  startData(): void {
    this.#start('DATA', false);
  }

  /** Asks what the AI run would cost; the dialog opens from {@code aiCostQuote}. */
  requestAi(): void {
    this.#start('AI', false);
  }

  confirmAi(): void {
    this.#aiCostQuote.set(null);
    this.#start('AI', true);
  }

  cancelAi(): void {
    this.#aiCostQuote.set(null);
  }

  stop(run: RefreshRun): void {
    this.#api
      .stopRefreshRun(run.id)
      .pipe(
        catchError(() => {
          this.#error.set('Could not stop the refresh.');

          return of(null);
        }),
      )
      .subscribe((stopped) => {
        if (stopped) {
          this.#remember(stopped.kind, stopped);
        }
      });
  }

  dismissError(): void {
    this.#error.set(null);
  }

  #start(kind: RefreshRunKind, confirmCost: boolean): void {
    if (this.#starting()) {
      return;
    }
    this.#starting.set(kind);
    this.#error.set(null);
    this.#api.startRefreshRun(kind, confirmCost).subscribe({
      next: (run) => {
        this.#starting.set(null);
        this.#remember(kind, run);
      },
      error: (failure: unknown) => {
        this.#starting.set(null);
        this.#explain(kind, failure);
      },
    });
  }

  #explain(kind: RefreshRunKind, failure: unknown): void {
    const body = failure instanceof HttpErrorResponse ? (failure.error as { reason?: string; games?: number } | null) : null;
    if (body?.reason === 'COST_CONFIRMATION_REQUIRED') {
      this.#aiCostQuote.set(body.games ?? 0);
    } else if (body?.reason === 'RUN_ALREADY_ACTIVE') {
      this.loadCurrent();
      this.#error.set('A refresh of this kind is already running.');
    } else {
      this.#error.set(kind === 'AI' ? 'Could not start the AI refresh.' : 'Could not start the game data refresh.');
    }
  }

  #remember(kind: RefreshRunKind, run: RefreshRun | null): void {
    this.#runs.update((runs) => ({ ...runs, [kind]: run }));
    this.#schedulePoll();
  }

  #schedulePoll(): void {
    const anyRunning = KINDS.some((kind) => this.#runs()[kind]?.status === 'RUNNING');
    if (!anyRunning || this.#pollTimer !== null) {
      return;
    }
    this.#pollTimer = setTimeout(() => {
      this.#pollTimer = null;
      for (const kind of KINDS) {
        if (this.#runs()[kind]?.status === 'RUNNING') {
          this.#api
            .getCurrentRefreshRun(kind)
            .pipe(catchError(() => of(this.#runs()[kind])))
            .subscribe((run) => this.#remember(kind, run));
        }
      }
    }, POLL_INTERVAL_MS);
  }
}
