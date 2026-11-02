import { Component, inject, ChangeDetectionStrategy } from '@angular/core';
import { AuthService } from '@jordylab-fe/shared/auth';
import { HlmButtonDirective } from '@spartan-ng/ui-button-helm';

/**
 * Shown to an approved person who has no app grants (spec 014 US1). Approval alone no longer opens
 * an app: the admin grants each app per person. Like the awaiting-approval page it renders outside
 * the nav shell (the shell hides itself when there is no app to show) and every API call it could
 * make is refused by the backend.
 */
@Component({
  selector: 'app-no-apps',
  standalone: true,
  imports: [HlmButtonDirective],
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `
    <div class="flex min-h-[80vh] items-center justify-center">
      <div class="panel w-full max-w-md p-9">
        <p class="eyebrow">No apps yet</p>
        <h1 class="page-title mt-3">Nothing shared with you</h1>
        <p class="mt-4 text-[15px] leading-relaxed text-secondary-foreground">
          Your account is approved, but no app has been shared with you yet. Ask the admin to
          grant you one, then check back.
        </p>
        <p class="mono-label mt-4 text-[12px]">Signed in as {{ username() ?? 'unknown' }}</p>
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
export class NoAppsComponent {
  #auth = inject(AuthService);

  username = this.#auth.username;

  async onLogout(): Promise<void> {
    await this.#auth.logout();
  }
}
