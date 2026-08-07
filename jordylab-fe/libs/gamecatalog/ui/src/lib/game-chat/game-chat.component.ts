import { Component, inject, signal } from '@angular/core';
import { catchError, of } from 'rxjs';
import { ChatGameRef, GameCatalogApiService } from '@jordylab-fe/gamecatalog/api';
import { GameChatViewComponent } from './game-chat-view.component';

export interface ChatMessage {
  role: 'user' | 'assistant';
  text: string;
  games?: ChatGameRef[];
  unavailable?: boolean;
}

@Component({
  selector: 'lib-game-chat',
  standalone: true,
  imports: [GameChatViewComponent],
  templateUrl: './game-chat.component.html',
})
export class GameChatComponent {
  #api = inject(GameCatalogApiService);

  messages = signal<ChatMessage[]>([]);
  asking = signal(false);

  onAsk(question: string) {
    const trimmed = question.trim();
    if (!trimmed || this.asking()) {
      return;
    }

    this.messages.update((list) => [...list, { role: 'user', text: trimmed }]);
    this.asking.set(true);

    this.#api
      .chat(trimmed)
      .pipe(
        catchError(() => {
          return of({ kind: 'unavailable' } as const);
        })
      )
      .subscribe((response) => {
        this.asking.set(false);
        if (response.kind === 'answered') {
          this.messages.update((list) => [
            ...list,
            { role: 'assistant', text: response.answer.answer, games: response.answer.games },
          ]);
        } else {
          this.messages.update((list) => [
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
