import { inject, Injectable, signal } from '@angular/core';
import { catchError, of } from 'rxjs';
import { GameCatalogApiService } from './gamecatalog-api.service';
import { AttachedGame, ChatMessage } from './gamecatalog.models';

@Injectable({ providedIn: 'root' })
export class GameChatStore {
  readonly #api = inject(GameCatalogApiService);

  readonly #messages = signal<ChatMessage[]>([]);
  readonly #asking = signal(false);
  readonly #attachedGame = signal<AttachedGame | null>(null);

  readonly messages = this.#messages.asReadonly();
  readonly asking = this.#asking.asReadonly();
  readonly attachedGame = this.#attachedGame.asReadonly();

  attachGame(gameId: string): void {
    if (!gameId || this.#attachedGame()?.id === gameId) {
      return;
    }

    this.#api
      .getGame(gameId)
      .pipe(catchError(() => of(null)))
      .subscribe((game) => {
        if (game) {
          this.#attachedGame.set({ id: game.id, title: game.title });
        }
      });
  }

  clearAttachment(): void {
    this.#attachedGame.set(null);
  }

  ask(question: string): void {
    const trimmed = question.trim();
    if (!trimmed || this.#asking()) {
      return;
    }

    const attached = this.#attachedGame();
    this.#messages.update((list) => [...list, { role: 'user', text: trimmed }]);
    this.#asking.set(true);

    this.#api
      .chat(trimmed, attached ? [attached.id] : [])
      .pipe(catchError(() => of({ kind: 'unavailable' } as const)))
      .subscribe((response) => {
        this.#asking.set(false);
        if (response.kind === 'answered') {
          this.#messages.update((list) => [
            ...list,
            { role: 'assistant', text: response.answer.answer, games: response.answer.games },
          ]);
        } else {
          this.#messages.update((list) => [
            ...list,
            {
              role: 'assistant',
              text: 'Chat is currently unavailable. Please try again later.',
              unavailable: true,
            },
          ]);
        }
      });
  }
}
