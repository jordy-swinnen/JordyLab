import { inject, Injectable, signal } from '@angular/core';
import { GameCatalogApiService } from './gamecatalog-api.service';
import { MarkResult, MarkType, VoteTotals } from './gamecatalog.models';

/** The part of a game the marks decide: the public totals and the signed-in person's own mark. */
export interface MarkState {
  votes: VoteTotals;
  myMark: MarkType | null;
}

const VOTE_FIELDS: Record<MarkType, keyof VoteTotals> = {
  WANT_TO_PLAY: 'wantToPlay',
  PLAYED_LIKED: 'playedLiked',
  PLAYED_DISLIKED: 'playedDisliked',
};

/**
 * One mark per person per game (want to play, played and liked, played and disliked). Choosing another replaces it, choosing
 * the current one clears it. The change shows at once and is taken back if the server refuses it. The lists keep their own
 * data; this store only holds what has changed since they last loaded, and they call {@link forget} when they reload.
 */
@Injectable({ providedIn: 'root' })
export class MarkStore {
  readonly #api = inject(GameCatalogApiService);

  readonly #changes = signal<ReadonlyMap<string, MarkState>>(new Map());
  readonly #pending = signal<ReadonlySet<string>>(new Set());
  readonly #error = signal<string | null>(null);

  readonly changes = this.#changes.asReadonly();
  readonly pending = this.#pending.asReadonly();
  readonly error = this.#error.asReadonly();

  /** {@code current} with this session's unsaved or just-saved change on top. */
  stateOf<T extends MarkState & { id: string }>(current: T): T {
    const change = this.#changes().get(current.id);

    return change ? { ...current, votes: change.votes, myMark: change.myMark } : current;
  }

  /** Sets {@code mark}, or clears it when it is already the person's mark. */
  toggle(game: MarkState & { id: string }, mark: MarkType): void {
    if (this.#pending().has(game.id)) {
      return;
    }
    const next = game.myMark === mark ? null : mark;
    const previous = this.#changes().get(game.id);
    this.#error.set(null);
    this.#remember(game.id, { votes: afterChange(game.votes, game.myMark, next), myMark: next });
    this.#markPending(game.id, true);

    this.#api.setMark(game.id, next).subscribe({
      next: (result: MarkResult) => {
        this.#remember(game.id, { votes: result.votes, myMark: result.myMark });
        this.#markPending(game.id, false);
      },
      error: () => {
        this.#restore(game.id, previous);
        this.#error.set('Could not save your mark. Try again.');
        this.#markPending(game.id, false);
      },
    });
  }

  /** Drops what was remembered once a list has loaded fresh data, except for requests still in flight. */
  forget(): void {
    this.#changes.update((changes) => new Map([...changes].filter(([gameId]) => this.#pending().has(gameId))));
  }

  dismissError(): void {
    this.#error.set(null);
  }

  #remember(gameId: string, state: MarkState): void {
    this.#changes.update((changes) => new Map(changes).set(gameId, state));
  }

  #restore(gameId: string, previous: MarkState | undefined): void {
    this.#changes.update((changes) => {
      const restored = new Map(changes);
      if (previous) {
        restored.set(gameId, previous);
      } else {
        restored.delete(gameId);
      }

      return restored;
    });
  }

  #markPending(gameId: string, isPending: boolean): void {
    this.#pending.update((pending) => {
      const updated = new Set(pending);
      if (isPending) {
        updated.add(gameId);
      } else {
        updated.delete(gameId);
      }

      return updated;
    });
  }
}

/** The totals after this person moves from {@code from} to {@code to}: their old vote leaves, their new one arrives. */
export function afterChange(votes: VoteTotals, from: MarkType | null, to: MarkType | null): VoteTotals {
  const updated = { ...votes };
  if (from) {
    updated[VOTE_FIELDS[from]] = Math.max(0, updated[VOTE_FIELDS[from]] - 1);
  }
  if (to) {
    updated[VOTE_FIELDS[to]] += 1;
  }

  return updated;
}
