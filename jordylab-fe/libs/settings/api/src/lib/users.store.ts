import { HttpErrorResponse } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { catchError, map, Observable, of } from 'rxjs';
import { SettingsUsersApiService } from './settings-users-api.service';
import { AppUser } from './settings.models';

interface TemporaryPassword {
  userId: string;
  password: string;
}

@Injectable({ providedIn: 'root' })
export class UsersStore {
  #api = inject(SettingsUsersApiService);

  readonly #users = signal<AppUser[]>([]);
  readonly #loading = signal(true);
  readonly #error = signal<string | null>(null);
  readonly #pendingCount = signal(0);
  readonly #actioningId = signal<string | null>(null);
  readonly #temporaryPassword = signal<TemporaryPassword | null>(null);

  readonly users = this.#users.asReadonly();
  readonly loading = this.#loading.asReadonly();
  readonly error = this.#error.asReadonly();
  readonly pendingCount = this.#pendingCount.asReadonly();
  readonly actioningId = this.#actioningId.asReadonly();
  readonly temporaryPassword = this.#temporaryPassword.asReadonly();

  readonly pendingUsers = computed(() => this.#users().filter((user) => user.status === 'PENDING'));
  readonly approvedUsers = computed(() => this.#users().filter((user) => user.status === 'APPROVED'));
  readonly rejectedUsers = computed(() => this.#users().filter((user) => user.status === 'REJECTED'));

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
        })
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

  setPersonalLimit(id: string, dailyLimit: number): void {
    this.#runAction(id, () => this.#api.setPersonalLimit(id, dailyLimit), 'Failed to save the limit.');
  }

  clearPersonalLimit(id: string): void {
    this.#runAction(id, () => this.#api.clearPersonalLimit(id), 'Failed to clear the limit.');
  }

  deletePerson(id: string): void {
    this.#runAction(id, () => this.#api.deletePerson(id), 'Failed to delete the person.');
  }

  resetPassword(id: string): void {
    if (this.#actioningId()) {
      return;
    }
    this.#actioningId.set(id);
    this.#error.set(null);

    this.#api
      .resetPassword(id)
      .pipe(
        catchError((error: unknown) => {
          this.#error.set(this.#reasonMessage(error, 'Failed to reset password.'));

          return of(null);
        })
      )
      .subscribe((password) => {
        this.#actioningId.set(null);
        if (password !== null) {
          this.#temporaryPassword.set({ userId: id, password });
        }
      });
  }

  clearTemporaryPassword(): void {
    this.#temporaryPassword.set(null);
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
        })
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
      case 'CANNOT_CHANGE_OWN_ACCOUNT':
        return 'You cannot change your own account from here.';
      case 'ADMIN_PROTECTED':
        return 'The admin account is protected.';
      case 'APP_NOT_GRANTABLE':
        return 'That app cannot be granted.';
      case 'APP_NOT_FOUND':
        return 'That app no longer exists.';
      case 'INVALID_LIMIT':
        return 'The limit must be a whole number of 0 or more.';
      case 'USER_NOT_APPROVED':
        return 'This user is not currently approved.';
      case 'USER_NOT_FOUND':
        return 'This user no longer exists.';
      case 'KEYCLOAK_UNAVAILABLE': {
        const stage = (error.error as { stage?: string } | null)?.stage;
        if (stage === 'DISABLE') {
          // The very first step failed, so the person is not blocked: nothing changed.
          return 'Deletion could not start; nothing changed. Try again shortly.';
        }

        return stage
          ? `Deletion stopped at ${stage}. The person is blocked and you can retry.`
          : 'Keycloak is unavailable right now — try again shortly.';
      }
      default:
        return fallback;
    }
  }
}
