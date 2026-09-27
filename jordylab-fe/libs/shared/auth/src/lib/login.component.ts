import { Component, inject } from '@angular/core';
import { AuthService } from './auth.service';
import { HlmButtonDirective } from '@spartan-ng/ui-button-helm';

@Component({
  selector: 'lib-login',
  standalone: true,
  imports: [HlmButtonDirective],
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
      </div>
    </div>
  `,
})
export class LoginComponent {
  #auth = inject(AuthService);

  async onLogin(): Promise<void> {
    await this.#auth.login();
  }
}
