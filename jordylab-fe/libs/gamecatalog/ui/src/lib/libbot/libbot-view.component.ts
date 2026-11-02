import { ChangeDetectionStrategy, Component, computed, effect, input, output, viewChild, ElementRef } from '@angular/core';
import { RouterLink } from '@angular/router';
import {
  AttachedGame,
  coverUrl,
  LibBotFailure,
  LibBotMessage,
  LibBotQuota,
  LibBotReference,
  LibBotStage,
} from '@jordylab-fe/gamecatalog/api';
import { PageHeaderComponent } from '@jordylab-fe/shared/layout';
import { coverPlateClass } from '../cover';
import { SparkleIconComponent } from '../chips/sparkle-icon.component';

interface StageStep {
  stage: LibBotStage;
  label: string;
  state: 'done' | 'active' | 'todo';
}

const STAGES: { stage: LibBotStage; label: string }[] = [
  { stage: 'UNDERSTANDING', label: 'Understood' },
  { stage: 'SEARCHING', label: 'Searching your library' },
  { stage: 'WRITING', label: 'Writing the answer' },
];

export type LibBotState = 'firstRun' | 'answering' | 'limit' | 'error' | 'loaded';

const EXAMPLE_QUESTIONS = [
  'There are 6 people here tonight, what should we play?',
  'Which co-op games can I play on the Switch?',
  'What is a short game I have not played yet?',
];

@Component({
  selector: 'lib-libbot-view',
  standalone: true,
  imports: [RouterLink, PageHeaderComponent, SparkleIconComponent],
  host: { '[attr.data-state]': 'state()' },
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './libbot-view.component.html',
})
export class LibBotViewComponent {
  messages = input.required<LibBotMessage[]>();
  busy = input.required<boolean>();
  stage = input.required<LibBotStage | null>();
  failure = input.required<LibBotFailure | null>();
  failedQuestion = input.required<string | null>();
  quota = input.required<LibBotQuota | null>();
  attachedGame = input.required<AttachedGame | null>();
  /** Pre-fills the question box (a shared link arriving via "Ask LibBot"). */
  prefillQuestion = input<string>('');

  ask = output<string>();
  retry = output<void>();
  newConversation = output<void>();
  removeAttachment = output<void>();

  protected readonly questionInput = viewChild<ElementRef<HTMLInputElement>>('questionInput');
  protected readonly plateClass = coverPlateClass;
  protected readonly coverUrl = coverUrl;

  protected readonly steps = computed<StageStep[]>(() => {
    const active = STAGES.findIndex((candidate) => candidate.stage === this.stage());

    return STAGES.map((candidate, index) => ({
      ...candidate,
      state: index < active ? 'done' : index === active ? 'active' : 'todo',
    }));
  });

  protected readonly limitReached = computed(() => this.failure()?.kind === 'limitReached');
  protected readonly exampleQuestions = EXAMPLE_QUESTIONS;

  protected readonly state = computed<LibBotState>(() => {
    if (this.busy()) {
      return 'answering';
    }
    if (this.limitReached()) {
      return 'limit';
    }
    if (this.failure()) {
      return 'error';
    }

    return this.messages().length === 0 ? 'firstRun' : 'loaded';
  });

  protected readonly quotaLine = computed(() => {
    const quota = this.quota();
    if (!quota || quota.exempt || quota.remaining === null || quota.limit === null) {
      return null;
    }

    return `${quota.remaining} of ${quota.limit} messages left today`;
  });

  constructor() {
    // A failed question comes back into the box, so nothing has to be typed again.
    effect(() => {
      const failed = this.failedQuestion();
      const element = this.questionInput()?.nativeElement;
      if (failed && element && !element.value) {
        element.value = failed;
      }
    });
    effect(() => {
      const prefill = this.prefillQuestion();
      const element = this.questionInput()?.nativeElement;
      if (prefill && element && !element.value) {
        element.value = prefill;
      }
    });
  }

  protected resetsAtLabel(): string {
    const failure = this.failure();
    if (failure?.kind !== 'limitReached') {
      return 'midnight';
    }
    const parsed = new Date(failure.resetsAt);

    return Number.isNaN(parsed.getTime())
      ? 'midnight'
      : parsed.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' });
  }

  protected referenceCover(reference: LibBotReference): string | null {
    return reference.cover.externalUrl ?? reference.cover.localUrl;
  }

  onSubmit(input: HTMLInputElement) {
    const text = input.value.trim();
    if (!text || this.busy()) {
      return;
    }
    this.ask.emit(text);
    input.value = '';
  }
}
