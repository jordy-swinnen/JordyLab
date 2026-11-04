import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, ChangeDetectionStrategy, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HlmButtonDirective } from '@spartan-ng/ui-button-helm';
import { AuthService, authErrorMessage } from './auth.service';

/**
 * A locked-out user's reset request (spec 019 US6): username plus the new password they want. Nothing is emailed; the
 * admin approves the request in the Users page. The confirmation is the same whether or not the account exists.
 */
@Component({
  selector: 'lib-forgot-password',
  standalone: true,
  imports: [FormsModule, RouterLink, HlmButtonDirective],
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `
    <div class="flex min-h-[80vh] items-center justify-center">
      <div class="panel w-full max-w-[400px] p-8">
        <h1 class="text-[26px] leading-tight tracking-[-0.03em]">Forgot your password?</h1>
        <p class="mt-3 text-sm leading-relaxed text-secondary-foreground">
          Pick a new password and send the request. An admin approves it from the Users page — there is no email and
          no link.
        </p>

        @if (submitted()) {
          <div class="mt-6 flex flex-col gap-4" data-testid="reset-submitted">
            <p class="text-sm text-foreground">
              Your request has been sent. Once an admin approves it, sign in with the password you chose.
            </p>
            <a
              routerLink="/login"
              class="text-sm font-semibold text-accent-text underline underline-offset-4"
              data-testid="reset-back-to-login"
              >Back to sign in</a
            >
          </div>
        } @else {
          <form class="mt-6 flex flex-col gap-4" (ngSubmit)="onSubmit()">
            <label class="flex flex-col gap-1">
              <span class="label">Username</span>
              <input
                class="h-11 w-full rounded-lg border border-border bg-background px-3 py-2 text-sm"
                type="text"
                name="username"
                data-testid="reset-username"
                autocomplete="username"
                required
                [(ngModel)]="username"
              />
            </label>
            <label class="flex flex-col gap-1">
              <span class="label">New password</span>
              <input
                class="h-11 w-full rounded-lg border border-border bg-background px-3 py-2 text-sm"
                type="password"
                name="newPassword"
                data-testid="reset-password"
                autocomplete="new-password"
                required
                [(ngModel)]="newPassword"
              />
            </label>
            <label class="flex flex-col gap-1">
              <span class="label">Confirm new password</span>
              <input
                class="h-11 w-full rounded-lg border border-border bg-background px-3 py-2 text-sm"
                type="password"
                name="confirmPassword"
                data-testid="reset-confirm"
                autocomplete="new-password"
                required
                [(ngModel)]="confirmPassword"
              />
            </label>
            @if (error(); as message) {
              <p class="text-sm text-destructive" role="alert" data-testid="reset-error">{{ message }}</p>
            }
            <button
              hlmBtn
              variant="default"
              type="submit"
              [disabled]="submitting()"
              class="h-11 w-full text-[15px] font-semibold"
              data-testid="reset-submit"
            >
              {{ submitting() ? 'Sending…' : 'Send reset request' }}
            </button>
          </form>

          <p class="mt-5 text-sm text-muted-foreground">
            Remembered it?
            <a routerLink="/login" class="font-semibold text-accent-text underline underline-offset-4">Sign in</a>
          </p>
        }
      </div>
    </div>
  `,
})
export class ForgotPasswordComponent {
  readonly #auth = inject(AuthService);

  protected username = '';
  protected newPassword = '';
  protected confirmPassword = '';
  protected readonly submitting = signal(false);
  protected readonly submitted = signal(false);
  protected readonly error = signal<string | null>(null);

  async onSubmit(): Promise<void> {
    this.error.set(null);
    if (this.newPassword !== this.confirmPassword) {
      this.error.set('The two passwords do not match.');

      return;
    }
    this.submitting.set(true);
    try {
      await this.#auth.requestPasswordReset(this.username.trim(), this.newPassword);
      this.newPassword = '';
      this.confirmPassword = '';
      this.submitted.set(true);
    } catch (failure) {
      this.error.set(this.#message(failure));
    } finally {
      this.submitting.set(false);
    }
  }

  #message(failure: unknown): string {
    if (failure instanceof HttpErrorResponse) {
      const body = failure.error as { reason?: string; message?: string } | null;
      if (body?.reason === 'WEAK_PASSWORD') {
        return body.message ?? 'Choose a password of at least 12 characters.';
      }
    }

    return authErrorMessage(failure);
  }
}
