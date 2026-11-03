import { HttpErrorResponse } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { catchError, map, Observable, of } from 'rxjs';
import { SettingsUsersApiService } from './settings-users-api.service';
import { AppUser } from './settings.models';

@Injectable({ providedIn: 'root' })
export class UsersStore {
  #api = inject(SettingsUsersApiService);

  readonly #users = signal<AppUser[]>([]);
  readonly #loading = signal(true);
  readonly #error = signal<string | null>(null);
  readonly #pendingCount = signal(0);
  readonly #actioningId = signal<string | null>(null);

  readonly users = this.#users.asReadonly();
  readonly loading = this.#loading.asReadonly();
  readonly error = this.#error.asReadonly();
  readonly pendingCount = this.#pendingCount.asReadonly();
  readonly actioningId = this.#actioningId.asReadonly();

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
      default:
        return fallback;
    }
  }
}
