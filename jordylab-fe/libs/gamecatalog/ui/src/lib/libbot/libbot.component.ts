import { ChangeDetectionStrategy, Component, computed, effect, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { LibBotStore } from '@jordylab-fe/gamecatalog/api';
import { LibBotViewComponent } from './libbot-view.component';

@Component({
  selector: 'lib-libbot',
  standalone: true,
  imports: [LibBotViewComponent],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './libbot.component.html',
})
export class LibBotComponent {
  readonly #route = inject(ActivatedRoute);
  readonly #store = inject(LibBotStore);
  // Read reactively: Angular reuses this component on query-param-only navigation
  // (`/libbot?attach=A` → `?attach=B`), so a one-shot snapshot read would miss the change.
  readonly #queryParams = toSignal(this.#route.queryParamMap);

  readonly messages = this.#store.messages;
  readonly busy = this.#store.busy;
  readonly stage = this.#store.stage;
  readonly failure = this.#store.failure;
  readonly failedQuestion = this.#store.failedQuestion;
  readonly quota = this.#store.quota;
  readonly attachedGame = this.#store.attachedGame;
  /** `?prefill=` (the share landing's "Ask LibBot" destination). */
  readonly prefillQuestion = computed(() => this.#queryParams()?.get('prefill') ?? '');

  constructor() {
    this.#store.loadQuota();
    effect(() => {
      const attach = this.#queryParams()?.get('attach');
      if (attach) {
        this.#store.attachGame(attach);
      }
    });
  }

  onAsk(question: string): void {
    this.#store.ask(question);
  }

  onRetry(): void {
    this.#store.retry();
  }

  onNewConversation(): void {
    this.#store.newConversation();
  }

  onRemoveAttachment(): void {
    this.#store.clearAttachment();
  }
}
