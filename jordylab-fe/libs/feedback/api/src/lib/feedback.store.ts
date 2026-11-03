import { HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { catchError, of } from 'rxjs';
import { FeedbackApiService } from './feedback-api.service';
import { FeedbackApp, FeedbackTicket, TicketSubmission } from './feedback.models';

/**
 * The guest tab's state (Custom Signal Store): the apps this sender may pick, their own tickets,
 * and the submission lifecycle. A failed submission keeps the typed form intact and surfaces the
 * backend's stable reason codes as human messages.
 */
@Injectable({ providedIn: 'root' })
export class FeedbackStore {
  #api = inject(FeedbackApiService);

  readonly #apps = signal<FeedbackApp[]>([]);
  readonly #tickets = signal<FeedbackTicket[]>([]);
  readonly #loading = signal(true);
  readonly #submitting = signal(false);
  readonly #error = signal<string | null>(null);

  readonly apps = this.#apps.asReadonly();
  readonly tickets = this.#tickets.asReadonly();
  readonly loading = this.#loading.asReadonly();
  readonly submitting = this.#submitting.asReadonly();
  readonly error = this.#error.asReadonly();

  constructor() {
    this.load();
  }

  load(): void {
    this.#loading.set(true);
    this.#error.set(null);

    this.#api
      .getApps()
      .pipe(catchError(() => of<FeedbackApp[]>([])))
      .subscribe((apps) => {
        this.#apps.set(apps);
        this.#loadTickets();
      });
  }

  submit(submission: TicketSubmission): void {
    if (this.#submitting()) {
      return;
    }
    this.#submitting.set(true);
    this.#error.set(null);

    this.#api
      .submit(submission)
      .pipe(
        catchError((error: unknown) => {
          this.#error.set(this.#reasonMessage(error));
          this.#submitting.set(false);

          return of(null);
        })
      )
      .subscribe((ticket) => {
        if (ticket === null) {
          return;
        }
        this.#submitting.set(false);
        this.#loadTickets();
      });
  }

  dismissError(): void {
    this.#error.set(null);
  }

  #loadTickets(): void {
    this.#api
      .getOwnTickets()
      .pipe(
        catchError(() => {
          this.#error.set('Failed to load your feedback.');

          return of<FeedbackTicket[]>([]);
        })
      )
      .subscribe((tickets) => {
        this.#tickets.set(tickets);
        this.#loading.set(false);
      });
  }

  #reasonMessage(error: unknown): string {
    if (!(error instanceof HttpErrorResponse)) {
      return 'Failed to send your feedback.';
    }
    const reason = (error.error as { reason?: string } | null)?.reason;
    switch (reason) {
      case 'FEEDBACK_LIMIT_REACHED':
        return 'You have reached the daily limit of five feedback tickets. Try again tomorrow.';
      case 'APP_NOT_ALLOWED':
        return 'That app is not available to you.';
      case 'VALIDATION':
        return 'Check the app, type, title and description — everything is required.';
      default:
        return 'Failed to send your feedback.';
    }
  }
}
