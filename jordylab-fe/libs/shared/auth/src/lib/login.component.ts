import { Component, inject } from '@angular/core';
import { AuthService } from './auth.service';
import { HlmButtonDirective } from '@spartan-ng/ui-button-helm';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [HlmButtonDirective],
  template: `
    <div class="noise-bg flex min-h-screen items-center justify-center">
      <div class="w-full max-w-md rounded-lg border border-border bg-card p-8 shadow-lg">
        <h1 class="mb-2 font-serif text-3xl italic tracking-tight text-primary" style="font-family: 'Instrument Serif', serif;">
          JordyLab
        </h1>
        <p class="mb-6 text-sm text-muted-foreground">Sign in to access your library and game catalog.</p>
        <button
          hlmBtn
          variant="default"
          (click)="onLogin()"
          class="w-full"
        >
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
