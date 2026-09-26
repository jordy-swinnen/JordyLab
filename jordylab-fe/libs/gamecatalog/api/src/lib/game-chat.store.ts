import { inject, Injectable, signal } from '@angular/core';
import { catchError, of } from 'rxjs';
import { GameCatalogApiService } from './gamecatalog-api.service';
import { ChatMessage } from './gamecatalog.models';

@Injectable({ providedIn: 'root' })
export class GameChatStore {
  readonly #api = inject(GameCatalogApiService);

  readonly #messages = signal<ChatMessage[]>([]);
  readonly #asking = signal(false);

  readonly messages = this.#messages.asReadonly();
  readonly asking = this.#asking.asReadonly();

  ask(question: string): void {
    const trimmed = question.trim();
    if (!trimmed || this.#asking()) {
      return;
    }

    this.#messages.update((list) => [...list, { role: 'user', text: trimmed }]);
    this.#asking.set(true);

    this.#api
      .chat(trimmed)
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
