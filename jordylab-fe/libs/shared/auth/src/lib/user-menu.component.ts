import { Component, computed, inject, input, output } from '@angular/core';
import { CdkMenu, CdkMenuItem, CdkMenuTrigger } from '@angular/cdk/menu';
import { ConnectedPosition } from '@angular/cdk/overlay';
import { AuthService } from './auth.service';

/**
 * The signed-in user's own menu (006 US5, FR-008): change password, edit name and change email on Keycloak's
 * pages, and sign out. Open to every authenticated user — admins and guests alike. `compact` is the small
 * mobile-header variant; the full variant shows the avatar and name (desktop sidebar).
 */
@Component({
  selector: 'lib-user-menu',
  standalone: true,
  // CDK menu directly: spartan brain's menu trigger reads private CDK overlay fields that CDK 21 no longer has.
  imports: [CdkMenuTrigger, CdkMenu, CdkMenuItem],
  template: `
    <button
      type="button"
      data-testid="user-menu-trigger"
      [cdkMenuTriggerFor]="menu"
      [cdkMenuPosition]="positions()"
      [attr.aria-label]="'Account menu for ' + (username() ?? 'you')"
      [class]="
        compact()
          ? 'flex items-center gap-2 rounded-full text-sm text-muted-foreground hover:text-foreground'
          : 'flex w-full items-center gap-3 rounded-xl border border-border p-3 text-left transition-colors hover:border-input'
      "
    >
      <span
        class="flex h-[34px] w-[34px] items-center justify-center rounded-full bg-iris font-display text-[15px] font-bold text-primary-foreground"
        aria-hidden="true"
        >{{ initial() }}</span
      >
      @if (!compact()) {
        <span class="flex min-w-0 flex-col gap-0.5">
          <span class="truncate text-sm font-semibold">{{ username() }}</span>
          <span class="text-xs text-muted-foreground">Account</span>
        </span>
      }
    </button>

    <ng-template #menu>
      <div
        cdkMenu
        class="min-w-48 rounded-xl border border-border bg-card p-1 text-sm text-foreground shadow-lg"
      >
        <button type="button" cdkMenuItem data-testid="user-menu-password" [class]="itemClass" (cdkMenuItemTriggered)="onChangePassword()">
          Change password
        </button>
        <button type="button" cdkMenuItem data-testid="user-menu-profile" [class]="itemClass" (cdkMenuItemTriggered)="onEditProfile()">
          Edit name
        </button>
        <!-- Email is the username here, so Keycloak changes it through its own re-authenticated UPDATE_EMAIL action. -->
        <button type="button" cdkMenuItem data-testid="user-menu-email" [class]="itemClass" (cdkMenuItemTriggered)="onChangeEmail()">
          Change email
        </button>
        <div class="my-1 h-px bg-border" role="separator"></div>
        <button type="button" cdkMenuItem data-testid="user-menu-sign-out" [class]="itemClass" (cdkMenuItemTriggered)="signOut.emit()">
          Sign out
        </button>
      </div>
    </ng-template>
  `,
})
export class UserMenuComponent {
  readonly #auth = inject(AuthService);

  readonly compact = input(false);
  /** The shell owns sign-out (it also wipes biometric unlock first). */
  readonly signOut = output<void>();

  protected readonly username = this.#auth.username;
  protected readonly initial = computed(() => (this.username() ?? '?').charAt(0).toUpperCase());
  /** Desktop sidebar: the trigger sits at the bottom, so open upwards; mobile header: open below, right-aligned. */
  protected readonly positions = computed<ConnectedPosition[]>(() =>
    this.compact()
      ? [{ originX: 'end', originY: 'bottom', overlayX: 'end', overlayY: 'top', offsetY: 8 }]
      : [{ originX: 'start', originY: 'top', overlayX: 'start', overlayY: 'bottom', offsetY: -8 }],
  );
  protected readonly itemClass =
    'flex w-full items-center rounded-lg px-3 py-2 text-left hover:bg-secondary focus:bg-secondary focus:outline-none';

  onChangePassword(): void {
    void this.#auth.requestAction('UPDATE_PASSWORD');
  }

  onEditProfile(): void {
    void this.#auth.requestAction('UPDATE_PROFILE');
  }

  onChangeEmail(): void {
    void this.#auth.requestAction('UPDATE_EMAIL');
  }
}
