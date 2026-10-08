import { inject, Injectable, signal } from '@angular/core';
import { catchError, of, Subscription } from 'rxjs';
import { GameCatalogApiService } from './gamecatalog-api.service';
import { AttachedGame, LibBotMessage, LibBotQuota, LibBotStage } from './gamecatalog.models';

export type LibBotFailure =
  | { kind: 'unavailable'; retryable: boolean }
  | { kind: 'limitReached'; resetsAt: string }
  | { kind: 'rejected' };

function newConversationId(): string {
  return globalThis.crypto?.randomUUID?.() ?? `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`;
}

/**
 * State of the LibBot page. The conversation lives only for the visit: a reload starts a new one, and "New
 * conversation" also tells the server to forget this one. A failed question keeps its text so the person can try
 * again without retyping.
 */
@Injectable({ providedIn: 'root' })
export class LibBotStore {
  readonly #api = inject(GameCatalogApiService);

  readonly #messages = signal<LibBotMessage[]>([]);
  readonly #busy = signal(false);
  readonly #stage = signal<LibBotStage | null>(null);
  readonly #failure = signal<LibBotFailure | null>(null);
  readonly #failedQuestion = signal<string | null>(null);
  readonly #quota = signal<LibBotQuota | null>(null);
  readonly #attachedGame = signal<AttachedGame | null>(null);

  #conversationId = newConversationId();
  #pendingQuestion: string | null = null;
  #request: Subscription | null = null;

  readonly messages = this.#messages.asReadonly();
  readonly busy = this.#busy.asReadonly();
  readonly stage = this.#stage.asReadonly();
  readonly failure = this.#failure.asReadonly();
  /** The text of the question that failed, so the composer can offer it back. */
  readonly failedQuestion = this.#failedQuestion.asReadonly();
  readonly quota = this.#quota.asReadonly();
  readonly attachedGame = this.#attachedGame.asReadonly();

  loadQuota(): void {
    this.#api
      .getLibBotQuota()
      .pipe(catchError(() => of(null)))
      .subscribe((quota) => {
        if (quota) {
          this.#quota.set(quota);
        }
      });
  }

  attachGame(gameId: string): void {
    if (!gameId || this.#attachedGame()?.id === gameId) {
      return;
    }

    this.#api
      .getGame(gameId)
      .pipe(catchError(() => of(null)))
      .subscribe((game) => {
        if (game) {
          this.#attachedGame.set({
            id: game.id,
            title: game.title,
            coverUrl: game.coverUrl,
            coverEndpoint: game.coverEndpoint,
          });
        }
      });
  }

  clearAttachment(): void {
    this.#attachedGame.set(null);
  }

  ask(question: string): void {
    const trimmed = question.trim();
    if (!trimmed || this.#busy()) {
      return;
    }
    this.#messages.update((list) => [...list, { role: 'user', text: trimmed }]);
    this.#send(trimmed);
  }

  /** Sends the question that failed again, without adding it to the thread a second time. */
  retry(): void {
    const question = this.#pendingQuestion;
    const failure = this.#failure();
    if (!question || this.#busy() || failure?.kind !== 'unavailable' || !failure.retryable) {
      return;
    }
    this.#send(question);
  }

  newConversation(): void {
    this.#request?.unsubscribe();
    const previous = this.#conversationId;
    this.#api
      .forgetLibBotConversation(previous)
      .pipe(catchError(() => of(undefined)))
      .subscribe();
    this.#conversationId = newConversationId();
    this.#pendingQuestion = null;
    this.#messages.set([]);
    this.#busy.set(false);
    this.#stage.set(null);
    this.#failure.set(null);
    this.#failedQuestion.set(null);
  }

  #send(question: string): void {
    this.#pendingQuestion = question;
    this.#busy.set(true);
    this.#stage.set('UNDERSTANDING');
    this.#failure.set(null);
    this.#failedQuestion.set(null);
    const attached = this.#attachedGame();

    this.#request = this.#api
      .askLibBot({
        conversationId: this.#conversationId,
        message: question,
        attachedGameIds: attached ? [attached.id] : [],
      })
      .subscribe((event) => {
        switch (event.kind) {
          case 'stage':
            this.#stage.set(event.stage);
            break;
          case 'answer':
            this.#messages.update((list) => [
              ...list,
              { role: 'assistant', text: event.answer.text, answer: event.answer },
            ]);
            this.#finish();
            this.loadQuota();
            break;
          case 'error':
            this.#fail(question, { kind: 'unavailable', retryable: event.retryable });
            break;
          case 'limitReached':
            this.#fail(question, { kind: 'limitReached', resetsAt: event.resetsAt });
            this.loadQuota();
            break;
          case 'rejected':
            this.#fail(question, { kind: 'rejected' });
            break;
        }
      });
  }

  #fail(question: string, failure: LibBotFailure): void {
    this.#failure.set(failure);
    this.#failedQuestion.set(question);
    this.#busy.set(false);
    this.#stage.set(null);
  }

  #finish(): void {
    this.#pendingQuestion = null;
    this.#busy.set(false);
    this.#stage.set(null);
  }
}
