import { Component, inject, ChangeDetectionStrategy } from '@angular/core';
import { AuthService } from '@jordylab-fe/shared/auth';
import { PageHeaderComponent } from '@jordylab-fe/shared/layout';
import { HlmButtonDirective } from '@spartan-ng/ui-button-helm';

/**
 * Shown to an authenticated account that holds no application role yet: self-registered users
 * wait here until an administrator approves them into `guest`. It renders outside the nav shell
 * (the shell hides itself for role-less accounts) and no API call this page could make is allowed.
 */
@Component({
  selector: 'app-awaiting-approval',
  standalone: true,
  imports: [HlmButtonDirective, PageHeaderComponent],
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `
    <div class="flex min-h-[80vh] items-center justify-center">
      <div class="panel w-full max-w-[400px] p-8">
        <lib-page-header title="You're almost in" subtitle="Awaiting approval" />
        <p class="mt-4 text-[15px] leading-relaxed text-secondary-foreground">
          Your account has been created, but an administrator still has to
          approve it before you can open the Game Catalog. Check back soon.
        </p>
        <p class="label mt-4">
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
