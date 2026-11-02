import { HttpErrorResponse } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { catchError, of } from 'rxjs';
import { SettingsUsersApiService } from './settings-users-api.service';
import { UserSession } from './settings.models';

/**
 * One person's sessions (spec 014 US5), loaded only when their entry is opened. Ending one signs out only that device;
 * ending all also kills the phone app's offline session.
 */
@Injectable({ providedIn: 'root' })
export class UserSessionsStore {
  #api = inject(SettingsUsersApiService);

  readonly #userId = signal<string | null>(null);
  readonly #sessions = signal<UserSession[]>([]);
  readonly #loading = signal(false);
  readonly #actioning = signal(false);
  readonly #error = signal<string | null>(null);

  readonly userId = this.#userId.asReadonly();
  readonly sessions = this.#sessions.asReadonly();
  readonly loading = this.#loading.asReadonly();
  readonly actioning = this.#actioning.asReadonly();
  readonly error = this.#error.asReadonly();
  readonly isOpen = computed(() => this.#userId() !== null);

  loadFor(userId: string): void {
    this.#userId.set(userId);
    this.#loading.set(true);
    this.#error.set(null);

    this.#api
      .getSessions(userId)
      .pipe(
        catchError(() => {
          this.#error.set('Failed to load the sessions.');
          this.#loading.set(false);

          return of<UserSession[]>([]);
        })
      )
      .subscribe((sessions) => {
        this.#sessions.set(sessions);
        this.#loading.set(false);
      });
  }

  close(): void {
    this.#userId.set(null);
    this.#sessions.set([]);
    this.#error.set(null);
  }

  endOne(sessionId: string, kind: UserSession['kind']): void {
    const userId = this.#userId();
    if (userId === null || this.#actioning()) {
      return;
    }
    this.#actioning.set(true);
    this.#error.set(null);

    this.#api
      .endSession(userId, sessionId, kind)
      .pipe(
        catchError((error: unknown) => {
          this.#error.set(this.#reasonMessage(error, 'Failed to end the session.'));
          this.#actioning.set(false);

          return of(null);
        })
      )
      .subscribe(() => {
        this.#actioning.set(false);
        this.loadFor(userId);
      });
  }

  endAll(): void {
    const userId = this.#userId();
    if (userId === null || this.#actioning()) {
      return;
    }
    this.#actioning.set(true);
    this.#error.set(null);

    this.#api
      .endAllSessions(userId)
      .pipe(
        catchError((error: unknown) => {
          this.#error.set(this.#reasonMessage(error, 'Failed to end the sessions.'));
          this.#actioning.set(false);

          return of(null);
        })
      )
      .subscribe(() => {
        this.#actioning.set(false);
        this.loadFor(userId);
      });
  }

  #reasonMessage(error: unknown, fallback: string): string {
    const reason = error instanceof HttpErrorResponse
      ? (error.error as { reason?: string } | null)?.reason
      : undefined;

    switch (reason) {
      case 'SESSION_NOT_FOUND':
        return 'That session is already gone.';
      case 'CANNOT_CHANGE_OWN_ACCOUNT':
        return 'You cannot manage your own sessions from here.';
      default:
        return fallback;
    }
  }
}
