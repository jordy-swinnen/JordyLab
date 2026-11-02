import { ChangeDetectionStrategy, Component, input, output, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import {
  DESCRIPTION_MAX_LENGTH,
  FEEDBACK_STATE_LABELS,
  FEEDBACK_TYPES,
  FeedbackApp,
  FeedbackAppId,
  FeedbackState,
  FeedbackTicket,
  FeedbackTypeId,
  TicketSubmission,
  TITLE_MAX_LENGTH,
} from '@jordylab-fe/feedback/api';
import { HlmBadgeDirective } from '@spartan-ng/ui-badge-helm';
import { HlmButtonDirective } from '@spartan-ng/ui-button-helm';
import {
  ErrorStateComponent,
  PageHeaderComponent,
  SkeletonRowsComponent,
} from '@jordylab-fe/shared/layout';

const INPUT_CLASS =
  'mt-1 h-11 w-full rounded-xl border border-input bg-background px-3 text-foreground';

@Component({
  selector: 'lib-feedback-page-view',
  standalone: true,
  imports: [
    DatePipe,
    HlmBadgeDirective,
    HlmButtonDirective,
    ErrorStateComponent,
    PageHeaderComponent,
    SkeletonRowsComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './feedback-page-view.component.html',
})
export class FeedbackPageViewComponent {
  apps = input<FeedbackApp[]>([]);
  tickets = input<FeedbackTicket[]>([]);
  loading = input(false);
  submitting = input(false);
  error = input<string | null>(null);

  submitTicket = output<TicketSubmission>();
  reload = output<void>();
  dismissError = output<void>();

  protected readonly inputClass = INPUT_CLASS;
  protected readonly titleMaxLength = TITLE_MAX_LENGTH;
  protected readonly descriptionMaxLength = DESCRIPTION_MAX_LENGTH;
  protected readonly types = FEEDBACK_TYPES;
  protected readonly stateLabels: Record<FeedbackState, string> = FEEDBACK_STATE_LABELS;

  protected readonly appId = signal<FeedbackAppId | null>(null);
  protected readonly typeId = signal<FeedbackTypeId>('BUG_REPORT');
  protected readonly title = signal('');
  protected readonly description = signal('');

  protected get canSubmit(): boolean {
    return (
      this.appId() !== null &&
      this.title().trim().length > 0 &&
      this.description().trim().length > 0 &&
      !this.submitting()
    );
  }

  protected onAppChange(value: string): void {
    this.appId.set(value as FeedbackAppId);
  }

  protected onTypeChange(value: string): void {
    this.typeId.set(value as FeedbackTypeId);
  }

  protected onTitleInput(value: string): void {
    this.title.set(value);
  }

  protected onDescriptionInput(value: string): void {
    this.description.set(value);
  }

  protected onSubmit(): void {
    const app = this.appId();
    if (!this.canSubmit || app === null) {
      return;
    }
    this.submitTicket.emit({
      app,
      type: this.typeId(),
      title: this.title().trim(),
      description: this.description().trim(),
    });
    this.title.set('');
    this.description.set('');
  }
}
