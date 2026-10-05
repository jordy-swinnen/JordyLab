import { Component, computed, effect, inject, ChangeDetectionStrategy } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { BiometricUnlockService } from './biometric-unlock.service';
import { HlmButtonDirective } from '@spartan-ng/ui-button-helm';

@Component({
  selector: 'lib-login',
  standalone: true,
  imports: [HlmButtonDirective],
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `
    <div class="flex min-h-[80vh] items-center justify-center">
      <div class="panel w-full max-w-md p-9">
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
          <h1 class="font-display text-[28px] leading-none tracking-[-0.03em]" style="font-variation-settings: 'wdth' 88">
            <span class="font-normal">Jordy</span><span class="font-extrabold text-primary">Lab</span>
          </h1>
        </div>
        <p class="mt-6 text-[15px] leading-relaxed text-secondary-foreground">
          Sign in to access your library and game catalog.
        </p>
        <button hlmBtn variant="default" (click)="onLogin()" class="mt-7 h-11 w-full text-[15px] font-semibold">
          Sign in with Keycloak
        </button>
        @if (failure()) {
          <p class="mt-4 text-sm text-destructive" role="alert">{{ failure() }}</p>
        }
        @if (biometricEnabled()) {
          <button
            hlmBtn
            variant="outline"
            (click)="onUnlock()"
            class="mt-3 h-11 w-full text-[15px] font-semibold"
          >
            Unlock with fingerprint
          </button>
        }
      </div>
    </div>
  `,
})
export class LoginComponent {
  #auth = inject(AuthService);
  #biometric = inject(BiometricUnlockService);
  #router = inject(Router);
  #returnUrl = safeReturnUrl(inject(ActivatedRoute).snapshot.queryParamMap.get('returnUrl'));

  protected readonly biometricEnabled = this.#biometric.enabled;
  protected readonly failure = computed(() => this.#biometric.failure() ?? this.#auth.nativeFailure());

  constructor() {
    void this.#biometric.refresh();
    // The native login finishes outside the router (an App Link callback), so nothing navigates
    // away from this page once the tokens arrive: leave it as soon as the session is authenticated.
    effect(() => {
      if (this.#auth.isAuthenticated()) {
        void this.#router.navigateByUrl(this.#returnUrl);
      }
    });
  }

  protected async onUnlock(): Promise<void> {
    await this.#biometric.unlock();
  }

  async onLogin(): Promise<void> {
    await this.#auth.login();
  }
}

/** Only an in-app path may be returned to: never another site, never the login page itself. */
function safeReturnUrl(candidate: string | null): string {
  if (!candidate || !candidate.startsWith('/') || candidate.startsWith('//') || candidate.startsWith('/login')) {
    return '/';
  }

  return candidate;
}
