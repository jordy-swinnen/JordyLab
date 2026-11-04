import { HttpErrorResponse } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { catchError, map, Observable, of } from 'rxjs';
import { InboxApiService } from './inbox-api.service';
import { FeedbackState, InboxTicket } from './feedback.models';

/**
 * The admin idea box (Custom Signal Store): the ticket list with its state filter, the triage
 * actions and the two admin texts. Accept carries the optional private note and surfaces the
 * stable LINEAR_UNAVAILABLE code when the workspace is unreachable.
 */
@Injectable({ providedIn: 'root' })
export class InboxStore {
  #api = inject(InboxApiService);

  readonly #tickets = signal<InboxTicket[]>([]);
  readonly #loading = signal(true);
  readonly #error = signal<string | null>(null);
  readonly #actioningId = signal<string | null>(null);
  readonly #filter = signal<FeedbackState | null>(null);

  readonly tickets = this.#tickets.asReadonly();
  readonly loading = this.#loading.asReadonly();
  readonly error = this.#error.asReadonly();
  readonly actioningId = this.#actioningId.asReadonly();
  readonly filter = this.#filter.asReadonly();

  readonly newCount = computed(() => this.#tickets().filter((ticket) => ticket.state === 'NEW').length);

  constructor() {
    this.load();
  }

  setFilter(filter: FeedbackState | null): void {
    this.#filter.set(filter);
    this.load();
  }

  load(): void {
    this.#loading.set(true);
    this.#error.set(null);

    this.#api
      .getInbox(this.#filter() ?? undefined)
      .pipe(
        catchError(() => {
          this.#error.set('Failed to load the feedback inbox.');

          return of<InboxTicket[]>([]);
        })
      )
      .subscribe((tickets) => {
        this.#tickets.set(tickets);
        this.#loading.set(false);
      });
  }

  moveToBacklog(id: string): void {
    this.#runAction(id, () => this.#api.moveToBacklog(id), 'Failed to move the ticket to the backlog.');
  }

  reject(id: string): void {
    this.#runAction(id, () => this.#api.reject(id), 'Failed to reject the ticket.');
  }

  accept(id: string, privateNote: string | null): void {
    this.#runAction(id, () => this.#api.accept(id, privateNote), 'Failed to accept the ticket.');
  }

  updateTexts(id: string, reply: string, privateNote: string): void {
    this.#runAction(id, () => this.#api.updateTexts(id, reply, privateNote), 'Failed to save the texts.');
  }

  dismissError(): void {
    this.#error.set(null);
  }

  #runAction(id: string, request: () => Observable<InboxTicket>, fallback: string): void {
    if (this.#actioningId()) {
      return;
    }
    this.#actioningId.set(id);
    this.#error.set(null);

    request()
      .pipe(
        map(() => true),
        catchError((error: unknown) => {
          this.#error.set(this.#reasonMessage(error, fallback));
          this.#actioningId.set(null);

          return of(false);
        })
      )
      .subscribe((succeeded) => {
        if (!succeeded) {
          return;
        }
        this.#actioningId.set(null);
        this.load();
      });
  }

  #reasonMessage(error: unknown, fallback: string): string {
    if (!(error instanceof HttpErrorResponse)) {
      return fallback;
    }
    const reason = (error.error as { reason?: string } | null)?.reason;
    switch (reason) {
      case 'LINEAR_UNAVAILABLE':
        return 'Linear is unreachable right now — the ticket stays as it was, so you can retry.';
      case 'INVALID_STATE':
        return 'That ticket cannot be changed in its current state.';
      case 'NOT_FOUND':
        return 'That ticket no longer exists.';
      case 'VALIDATION':
        return 'Check the texts — both must stay within the length limits.';
      default:
        return fallback;
    }
  }
}
