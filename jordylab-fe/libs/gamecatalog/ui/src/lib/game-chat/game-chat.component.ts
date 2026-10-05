import { Component, computed, effect, inject, ChangeDetectionStrategy } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { GameChatStore } from '@jordylab-fe/gamecatalog/api';
import { GameChatViewComponent } from './game-chat-view.component';

@Component({
  selector: 'lib-game-chat',
  standalone: true,
  imports: [GameChatViewComponent],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './game-chat.component.html',
})
export class GameChatComponent {
  readonly #route = inject(ActivatedRoute);
  readonly #store = inject(GameChatStore);
  // Read reactively: Angular reuses this component on query-param-only navigation
  // (`/games/chat?attach=A` → `?attach=B`), so a one-shot snapshot read would miss the change.
  readonly #attachParam = toSignal(this.#route.queryParamMap);

  readonly messages = this.#store.messages;
  readonly asking = this.#store.asking;
  readonly attachedGame = this.#store.attachedGame;
  /** `?prefill=` (spec US5 — the share landing's "Ask the catalog" destination). */
  readonly prefillQuestion = computed(() => this.#attachParam()?.get('prefill') ?? '');

  constructor() {
    effect(() => {
      const attach = this.#attachParam()?.get('attach');
      if (attach) {
        this.#store.attachGame(attach);
      }
    });
  }

  onAsk(question: string): void {
    this.#store.ask(question);
  }

  onRemoveAttachment(): void {
    this.#store.clearAttachment();
  }
}
