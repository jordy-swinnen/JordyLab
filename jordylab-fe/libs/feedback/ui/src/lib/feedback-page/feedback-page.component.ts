import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FeedbackStore, TicketSubmission } from '@jordylab-fe/feedback/api';
import { FeedbackPageViewComponent } from './feedback-page-view.component';

@Component({
  selector: 'lib-feedback-page',
  standalone: true,
  imports: [FeedbackPageViewComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <lib-feedback-page-view
      [apps]="apps()"
      [tickets]="tickets()"
      [loading]="loading()"
      [submitting]="submitting()"
      [error]="error()"
      (submitTicket)="onSubmit($event)"
      (reload)="onReload()"
      (dismissError)="onDismissError()"
    />
  `,
})
export class FeedbackPageComponent {
  readonly #store = inject(FeedbackStore);

  readonly apps = this.#store.apps;
  readonly tickets = this.#store.tickets;
  readonly loading = this.#store.loading;
  readonly submitting = this.#store.submitting;
  readonly error = this.#store.error;

  onSubmit(submission: TicketSubmission): void {
    this.#store.submit(submission);
  }

  onReload(): void {
    this.#store.load();
  }

  onDismissError(): void {
    this.#store.dismissError();
  }
}
