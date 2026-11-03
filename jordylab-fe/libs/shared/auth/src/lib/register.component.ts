import { Component, inject, ChangeDetectionStrategy, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HlmButtonDirective } from '@spartan-ng/ui-button-helm';
import { AuthService, authErrorMessage } from './auth.service';

/**
 * Self-registration (spec 019 US1, FR-003): username and password only — no email is asked for. The account starts
 * pending; after the admin approves it, the person can sign in and use the apps they were granted.
 */
@Component({
  selector: 'lib-register',
  standalone: true,
  imports: [FormsModule, RouterLink, HlmButtonDirective],
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `
    <div class="flex min-h-[80vh] items-center justify-center">
      <div class="panel w-full max-w-[400px] p-8">
        <h1 class="text-[24px] leading-none tracking-[-0.02em] font-semibold">Create an account</h1>
        <p class="mt-3 text-[15px] leading-relaxed text-secondary-foreground">
          Pick a username and a password. No email address is needed.
        </p>

        <form class="mt-6 flex flex-col gap-4" (ngSubmit)="onRegister()">
          <label class="flex flex-col gap-1">
            <span class="label">Username</span>
            <input
              class="h-11 w-full rounded-lg border border-border bg-background px-3 py-2 text-sm"
              type="text"
              name="username"
              data-testid="register-username"
              autocomplete="username"
              required
              minlength="3"
              maxlength="40"
              [(ngModel)]="username"
            />
          </label>
          <label class="flex flex-col gap-1">
            <span class="label">Display name <span class="font-normal text-muted-foreground">(optional)</span></span>
            <input
              class="h-11 w-full rounded-lg border border-border bg-background px-3 py-2 text-sm"
              type="text"
              name="displayName"
              data-testid="register-display-name"
              autocomplete="name"
              [(ngModel)]="displayName"
            />
          </label>
          <label class="flex flex-col gap-1">
            <span class="label">Password</span>
            <input
              class="h-11 w-full rounded-lg border border-border bg-background px-3 py-2 text-sm"
              type="password"
              name="password"
              data-testid="register-password"
              autocomplete="new-password"
              required
              minlength="12"
              [(ngModel)]="password"
            />
            <span class="text-xs text-muted-foreground">At least 12 characters.</span>
          </label>
          @if (error(); as message) {
            <p class="text-sm text-destructive" role="alert" data-testid="register-error">{{ message }}</p>
          }
          <button
            hlmBtn
            variant="default"
            type="submit"
            [disabled]="submitting()"
            class="h-11 w-full text-[15px] font-semibold"
            data-testid="register-submit"
          >
            Register
          </button>
        </form>

        <p class="mt-5 text-sm text-muted-foreground">
          Already have an account?
          <a class="text-accent-text underline-offset-2 hover:underline" routerLink="/login">Sign in</a>
        </p>
      </div>
    </div>
  `,
})
export class RegisterComponent {
  #auth = inject(AuthService);
  #router = inject(Router);

  protected username = '';
  protected password = '';
  protected displayName = '';
  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);

  async onRegister(): Promise<void> {
    if (this.submitting()) {
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    try {
      await this.#auth.register(
        this.username.trim(),
        this.password,
        this.displayName.trim() || undefined,
      );
      // Sign in straight away so the person lands on the awaiting-approval screen with a real session.
      await this.#auth.login(this.username.trim(), this.password);
      await this.#router.navigateByUrl('/awaiting-approval');
    } catch (failure) {
      this.error.set(authErrorMessage(failure));
    } finally {
      this.submitting.set(false);
    }
  }
}
