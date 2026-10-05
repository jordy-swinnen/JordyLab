import { Component, inject, ChangeDetectionStrategy } from '@angular/core';
import { AuthService } from '@jordylab-fe/shared/auth';
import { HlmButtonDirective } from '@spartan-ng/ui-button-helm';

/**
 * Shown to an authenticated account that holds no application role yet: self-registered users
 * wait here until an administrator approves them into `guest`. It renders outside the nav shell
 * (the shell hides itself for role-less accounts) and no API call this page could make is allowed.
 */
@Component({
  selector: 'app-awaiting-approval',
  standalone: true,
  imports: [HlmButtonDirective],
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `
    <div class="flex min-h-[80vh] items-center justify-center">
      <div class="panel w-full max-w-md p-9">
        <p class="eyebrow">Awaiting approval</p>
        <h1 class="page-title mt-3">You're almost in</h1>
        <p class="mt-4 text-[15px] leading-relaxed text-secondary-foreground">
          Your account has been created, but an administrator still has to
          approve it before you can open the Game Catalog. Check back soon.
        </p>
        <p class="mono-label mt-4 text-[12px]">
          Signed in as {{ username() ?? 'unknown' }}
        </p>
        <button
          hlmBtn
          variant="outline"
          (click)="onLogout()"
          class="mt-7 h-11 w-full text-[15px] font-semibold"
        >
          Sign out
        </button>
      </div>
    </div>
  `,
})
export class AwaitingApprovalComponent {
  #auth = inject(AuthService);

  username = this.#auth.username;

  async onLogout(): Promise<void> {
    await this.#auth.logout();
  }
}
