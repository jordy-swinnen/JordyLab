import { Component, inject, ChangeDetectionStrategy, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService, authErrorMessage } from '@jordylab-fe/shared/auth';
import { HlmButtonDirective } from '@spartan-ng/ui-button-helm';
import { PageHeaderComponent } from '@jordylab-fe/shared/layout';

/**
 * The signed-in user's account settings (spec 019): display name and password now, the passkey card with US2. Emails do
 * not exist on this platform — no email is shown or asked for.
 */
@Component({
  selector: 'app-account-page',
  standalone: true,
  imports: [FormsModule, HlmButtonDirective, PageHeaderComponent],
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `
    <lib-page-header title="Account" subtitle="Your name, password and sign-in devices." />

    <div class="mt-8 grid gap-6 lg:grid-cols-2">
      <section class="panel flex flex-col gap-4 p-6">
        <div>
          <h3 class="section-title">Display name</h3>
          <p class="mt-1 text-sm text-muted-foreground">Shown in the app instead of your username where there is room.</p>
        </div>
        <form class="flex flex-col gap-3" (ngSubmit)="onSaveDisplayName()">
          <label class="flex flex-col gap-1">
            <span class="label">Display name</span>
            <input
              class="h-11 w-full rounded-lg border border-border bg-background px-3 py-2 text-sm"
              type="text"
              name="displayName"
              data-testid="account-display-name"
              [(ngModel)]="displayName"
            />
          </label>
          @if (displayNameMessage(); as message) {
            <p class="text-sm" [class.text-destructive]="displayNameFailed()" data-testid="account-display-name-message">{{ message }}</p>
          }
          <button hlmBtn variant="default" type="submit" class="h-11 self-start" [disabled]="displayNameSaving()" data-testid="account-display-name-save">
            Save name
          </button>
        </form>
      </section>

      <section class="panel flex flex-col gap-4 p-6">
        <div>
          <h3 class="section-title">Change password</h3>
          <p class="mt-1 text-sm text-muted-foreground">At least 12 characters. You stay signed in afterwards.</p>
        </div>
        <form class="flex flex-col gap-3" (ngSubmit)="onChangePassword()">
          <label class="flex flex-col gap-1">
            <span class="label">Current password</span>
            <input
              class="h-11 w-full rounded-lg border border-border bg-background px-3 py-2 text-sm"
              type="password"
              name="currentPassword"
              data-testid="account-current-password"
              autocomplete="current-password"
              [(ngModel)]="currentPassword"
            />
          </label>
          <label class="flex flex-col gap-1">
            <span class="label">New password</span>
            <input
              class="h-11 w-full rounded-lg border border-border bg-background px-3 py-2 text-sm"
              type="password"
              name="newPassword"
              data-testid="account-new-password"
              autocomplete="new-password"
              [(ngModel)]="newPassword"
            />
          </label>
          @if (passwordMessage(); as message) {
            <p class="text-sm" [class.text-destructive]="passwordFailed()" data-testid="account-password-message">{{ message }}</p>
          }
          <button hlmBtn variant="default" type="submit" class="h-11 self-start" [disabled]="passwordSaving()" data-testid="account-password-save">
            Change password
          </button>
        </form>
      </section>
    </div>
  `,
})
export class AccountPageComponent {
  readonly #auth = inject(AuthService);

  protected displayName = this.#auth.displayName() ?? '';
  protected currentPassword = '';
  protected newPassword = '';

  protected readonly displayNameSaving = signal(false);
  protected readonly displayNameMessage = signal<string | null>(null);
  protected readonly displayNameFailed = signal(false);
  protected readonly passwordSaving = signal(false);
  protected readonly passwordMessage = signal<string | null>(null);
  protected readonly passwordFailed = signal(false);

  async onSaveDisplayName(): Promise<void> {
    this.displayNameSaving.set(true);
    this.displayNameMessage.set(null);
    try {
      await this.#auth.updateDisplayName(this.displayName.trim());
      this.displayNameFailed.set(false);
      this.displayNameMessage.set('Saved.');
    } catch (failure) {
      this.displayNameFailed.set(true);
      this.displayNameMessage.set(authErrorMessage(failure));
    } finally {
      this.displayNameSaving.set(false);
    }
  }

  async onChangePassword(): Promise<void> {
    this.passwordSaving.set(true);
    this.passwordMessage.set(null);
    try {
      await this.#auth.changePassword(this.currentPassword, this.newPassword);
      this.currentPassword = '';
      this.newPassword = '';
      this.passwordFailed.set(false);
      this.passwordMessage.set('Password changed.');
    } catch (failure) {
      this.passwordFailed.set(true);
      this.passwordMessage.set(authErrorMessage(failure));
    } finally {
      this.passwordSaving.set(false);
    }
  }
}
