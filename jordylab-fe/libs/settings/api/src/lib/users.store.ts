import { HttpErrorResponse } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { catchError, map, Observable, of } from 'rxjs';
import { SettingsUsersApiService } from './settings-users-api.service';
import { AppUser, UserSession } from './settings.models';

@Injectable({ providedIn: 'root' })
export class UsersStore {
  #api = inject(SettingsUsersApiService);

  readonly #users = signal<AppUser[]>([]);
  readonly #loading = signal(true);
  readonly #error = signal<string | null>(null);
  readonly #pendingCount = signal(0);
  readonly #actioningId = signal<string | null>(null);
  readonly #sessionsUserId = signal<string | null>(null);
  readonly #sessions = signal<UserSession[]>([]);
  readonly #sessionsLoading = signal(false);
  readonly #resetResult = signal<{ userId: string; temporaryPassword: string } | null>(null);

  readonly users = this.#users.asReadonly();
  readonly loading = this.#loading.asReadonly();
  readonly error = this.#error.asReadonly();
  readonly pendingCount = this.#pendingCount.asReadonly();
  readonly actioningId = this.#actioningId.asReadonly();
  readonly sessionsUserId = this.#sessionsUserId.asReadonly();
  readonly sessions = this.#sessions.asReadonly();
  readonly sessionsLoading = this.#sessionsLoading.asReadonly();
  readonly resetResult = this.#resetResult.asReadonly();

  readonly pendingUsers = computed(() => this.#users().filter((user) => user.status === 'PENDING'));
  readonly approvedUsers = computed(() => this.#users().filter((user) => user.status === 'APPROVED'));
  readonly rejectedUsers = computed(() =>
    this.#users().filter((user) => user.status === 'REJECTED' || user.status === 'REVOKED'),
  );

  constructor() {
    this.load();
    this.loadPendingCount();
  }

  load(): void {
    this.#loading.set(true);
    this.#error.set(null);

    this.#api
      .getUsers('ALL')
      .pipe(
        catchError(() => {
          this.#error.set('Failed to load users.');

          return of<AppUser[]>([]);
        }),
      )
      .subscribe((users) => {
        this.#users.set(users);
        this.#loading.set(false);
      });
  }

  loadPendingCount(): void {
    this.#api
      .getPendingCount()
      .pipe(catchError(() => of(0)))
      .subscribe((count) => this.#pendingCount.set(count));
  }

  approve(id: string): void {
    this.#runAction(id, () => this.#api.approve(id), 'Failed to approve user.');
  }

  reject(id: string): void {
    this.#runAction(id, () => this.#api.reject(id), 'Failed to reject user.');
  }

  revoke(id: string): void {
    this.#runAction(id, () => this.#api.revoke(id), 'Failed to revoke user.');
  }

  grantApp(id: string, appKey: string): void {
    this.#runAction(id, () => this.#api.grantApp(id, appKey), 'Failed to grant the app.');
  }

  removeApp(id: string, appKey: string): void {
    this.#runAction(id, () => this.#api.removeApp(id, appKey), 'Failed to remove the app.');
  }

  deletePerson(id: string): void {
    this.#runAction(id, () => this.#api.deletePerson(id), 'Failed to delete the person.');
  }

  approveResetRequest(userId: string, requestId: string): void {
    this.#runAction(userId, () => this.#api.approveResetRequest(userId, requestId),
      'Failed to approve the reset request.');
  }

  rejectResetRequest(userId: string, requestId: string): void {
    this.#runAction(userId, () => this.#api.rejectResetRequest(userId, requestId),
      'Failed to reject the reset request.');
  }

  /** Issues a one-time temporary password; the result stays until dismissed (spec 019 US6). */
  resetPassword(id: string): void {
    if (this.#actioningId()) {
      return;
    }
    this.#actioningId.set(id);
    this.#error.set(null);
    this.#resetResult.set(null);
    this.#api
      .resetPassword(id)
      .pipe(
        catchError((error: unknown) => {
          this.#error.set(this.#reasonMessage(error, 'Failed to reset the password.'));

          return of<{ temporaryPassword: string } | null>(null);
        }),
      )
      .subscribe((result) => {
        this.#actioningId.set(null);
        if (result) {
          this.#resetResult.set({ userId: id, temporaryPassword: result.temporaryPassword });
          this.load();
          this.loadPendingCount();
        }
      });
  }

  dismissResetResult(): void {
    this.#resetResult.set(null);
  }

  toggleSessions(userId: string): void {
    if (this.#sessionsUserId() === userId) {
      this.#sessionsUserId.set(null);
      this.#sessions.set([]);

      return;
    }
    this.#sessionsUserId.set(userId);
    this.#sessionsLoading.set(true);
    this.#api
      .getSessions(userId)
      .pipe(catchError(() => of<UserSession[]>([])))
      .subscribe((sessions) => {
        this.#sessions.set(sessions);
        this.#sessionsLoading.set(false);
      });
  }

  endSession(userId: string, sessionId: string): void {
    this.#api.endSession(userId, sessionId).pipe(catchError(() => of(undefined))).subscribe(() => {
      this.refreshSessions(userId);
    });
  }

  endAllSessions(userId: string): void {
    this.#api.endAllSessions(userId).pipe(catchError(() => of(undefined))).subscribe(() => {
      this.refreshSessions(userId);
    });
  }

  private refreshSessions(userId: string): void {
    this.#api.getSessions(userId).pipe(catchError(() => of<UserSession[]>([]))).subscribe((sessions) => {
      this.#sessions.set(sessions);
    });
  }

  #runAction(id: string, request: () => Observable<void>, defaultErrorMessage: string): void {
    if (this.#actioningId()) {
      return;
    }
    this.#actioningId.set(id);
    this.#error.set(null);

    request()
      .pipe(
        map(() => true),
        catchError((error: unknown) => {
          this.#error.set(this.#reasonMessage(error, defaultErrorMessage));

          return of(false);
        }),
      )
      .subscribe((succeeded) => {
        this.#actioningId.set(null);
        if (succeeded) {
          this.load();
          this.loadPendingCount();
        }
      });
  }

  #reasonMessage(error: unknown, fallback: string): string {
    if (!(error instanceof HttpErrorResponse)) {
      return fallback;
    }
    const reason = (error.error as { reason?: string } | null)?.reason;
    switch (reason) {
      case 'LAST_ADMIN_PROTECTED':
        return 'The last admin cannot be rejected or revoked.';
      case 'USER_NOT_APPROVED':
        return 'This user is not currently approved.';
      case 'ALREADY_APPROVED':
        return 'This user is already approved.';
      case 'USER_NOT_FOUND':
        return 'This user no longer exists.';
      case 'REQUEST_NOT_FOUND':
        return 'That reset request no longer exists.';
      case 'REQUEST_NOT_PENDING':
        return 'That reset request was already decided.';
      default:
        return fallback;
    }
  }
}
