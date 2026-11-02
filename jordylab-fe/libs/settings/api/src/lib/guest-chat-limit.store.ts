import { HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { catchError, of } from 'rxjs';
import { SettingsGuestChatLimitApiService } from './settings-guest-chat-limit-api.service';
import { GuestChatLimit } from './settings.models';

/**
 * The saved default guest chat limit (spec 014 FR-009). Until the admin saves one, the server reports the environment
 * value as its source. A saved 0 is a real limit that blocks chat, so it is never treated as "empty".
 */
@Injectable({ providedIn: 'root' })
export class GuestChatLimitStore {
  #api = inject(SettingsGuestChatLimitApiService);

  readonly #limit = signal<GuestChatLimit | null>(null);
  readonly #loading = signal(true);
  readonly #saving = signal(false);
  readonly #error = signal<string | null>(null);

  readonly limit = this.#limit.asReadonly();
  readonly loading = this.#loading.asReadonly();
  readonly saving = this.#saving.asReadonly();
  readonly error = this.#error.asReadonly();

  constructor() {
    this.load();
  }

  load(): void {
    this.#loading.set(true);
    this.#error.set(null);

    this.#api
      .getDefault()
      .pipe(
        catchError(() => {
          this.#error.set('Failed to load the guest limit.');

          return of<GuestChatLimit | null>(null);
        })
      )
      .subscribe((limit) => {
        this.#limit.set(limit);
        this.#loading.set(false);
      });
  }

  saveDefault(dailyLimit: number): void {
    if (this.#saving()) {
      return;
    }
    this.#saving.set(true);
    this.#error.set(null);

    this.#api
      .saveDefault(dailyLimit)
      .pipe(
        catchError((error: unknown) => {
          this.#error.set(this.#reasonMessage(error));
          this.#saving.set(false);

          return of<GuestChatLimit | null>(null);
        })
      )
      .subscribe((limit) => {
        this.#saving.set(false);
        if (limit !== null) {
          this.#limit.set(limit);
        }
      });
  }

  #reasonMessage(error: unknown): string {
    const reason = error instanceof HttpErrorResponse
      ? (error.error as { reason?: string } | null)?.reason
      : undefined;

    return reason === 'INVALID_LIMIT'
      ? 'The limit must be a whole number of 0 or more.'
      : 'Failed to save the guest limit.';
  }
}
