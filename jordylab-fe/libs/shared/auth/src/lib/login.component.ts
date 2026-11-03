import { Component, effect, inject, ChangeDetectionStrategy, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HlmButtonDirective } from '@spartan-ng/ui-button-helm';
import { AuthService, authErrorMessage } from './auth.service';

/** The app's own sign-in form (spec 019 US1): username and password, with a link to register. */
@Component({
  selector: 'lib-login',
  standalone: true,
  imports: [FormsModule, RouterLink, HlmButtonDirective],
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `
    <div class="flex min-h-[80vh] items-center justify-center">
      <div class="panel w-full max-w-[400px] p-8">
        <div class="flex items-center gap-3">
          <svg width="40" height="40" viewBox="0 0 48 48" fill="none" aria-hidden="true">
            <rect width="48" height="48" rx="13" fill="hsl(var(--primary))" />
            <path
              d="M30 10V27C30 32.5 26 36 21 36C17.5 36 15 34.5 13.5 32"
              stroke="hsl(var(--primary-foreground))"
              stroke-width="6.5"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
            <circle cx="17" cy="21" r="3.6" fill="hsl(var(--primary-foreground))" />
            <circle cx="21.5" cy="12.5" r="2.4" fill="hsl(var(--primary-foreground))" />
          </svg>
          <h1 class="text-[28px] leading-none tracking-[-0.03em]">
            <span class="font-normal">Jordy</span><span class="font-extrabold text-accent-text">Lab</span>
          </h1>
        </div>
        <p class="mt-6 text-[15px] leading-relaxed text-secondary-foreground">
          Sign in to access your library and game catalog.
        </p>

        <form class="mt-6 flex flex-col gap-4" (ngSubmit)="onLogin()">
          <label class="flex flex-col gap-1">
            <span class="label">Username</span>
            <input
              class="h-11 w-full rounded-lg border border-border bg-background px-3 py-2 text-sm"
              type="text"
              name="username"
              data-testid="login-username"
              autocomplete="username"
              required
              [(ngModel)]="username"
            />
          </label>
          <label class="flex flex-col gap-1">
            <span class="label">Password</span>
            <input
              class="h-11 w-full rounded-lg border border-border bg-background px-3 py-2 text-sm"
              type="password"
              name="password"
              data-testid="login-password"
              autocomplete="current-password"
              required
              [(ngModel)]="password"
            />
          </label>
          @if (error(); as message) {
            <p class="text-sm text-destructive" role="alert" data-testid="login-error">{{ message }}</p>
          }
          <button
            hlmBtn
            variant="default"
            type="submit"
            [disabled]="submitting()"
            class="h-11 w-full text-[15px] font-semibold"
            data-testid="login-submit"
          >
            Sign in
          </button>
        </form>

        <p class="mt-5 text-sm text-muted-foreground">
          No account yet?
          <a class="text-accent-text underline-offset-2 hover:underline" routerLink="/register">Register</a>
        </p>
      </div>
    </div>
  `,
})
export class LoginComponent {
  #auth = inject(AuthService);
  #router = inject(Router);
  #returnUrl = safeReturnUrl(inject(ActivatedRoute).snapshot.queryParamMap.get('returnUrl'));

  protected username = '';
  protected password = '';
  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);

  constructor() {
    // A session restored in another tab (or still valid) authenticates without any form submit: leave immediately.
    effect(() => {
      if (this.#auth.isAuthenticated()) {
        void this.#router.navigateByUrl(this.#returnUrl);
      }
    });
  }

  async onLogin(): Promise<void> {
    if (this.submitting()) {
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    try {
      await this.#auth.login(this.username.trim(), this.password);
      await this.#router.navigateByUrl(this.#returnUrl);
    } catch (failure) {
      this.error.set(authErrorMessage(failure));
    } finally {
      this.submitting.set(false);
    }
  }
}

/** Only an in-app path may be returned to: never another site, never the login page itself. */
export function safeReturnUrl(candidate: string | null): string {
  if (!candidate || !candidate.startsWith('/') || candidate.startsWith('//') || candidate.startsWith('/login')) {
    return '/';
  }

  return candidate;
}
