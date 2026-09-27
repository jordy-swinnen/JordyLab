import { Component, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { GameChatStore } from '@jordylab-fe/gamecatalog/api';
import { GameChatViewComponent } from './game-chat-view.component';

@Component({
  selector: 'lib-game-chat',
  standalone: true,
  imports: [GameChatViewComponent],
  templateUrl: './game-chat.component.html',
})
export class GameChatComponent {
  readonly #route = inject(ActivatedRoute);
  readonly #store = inject(GameChatStore);

  readonly messages = this.#store.messages;
  readonly asking = this.#store.asking;
  readonly attachedGame = this.#store.attachedGame;

  constructor() {
    const attach = this.#route.snapshot.queryParamMap.get('attach');
    if (attach) {
      this.#store.attachGame(attach);
    }
  }

  onAsk(question: string): void {
    this.#store.ask(question);
  }

  onRemoveAttachment(): void {
    this.#store.clearAttachment();
  }
}
