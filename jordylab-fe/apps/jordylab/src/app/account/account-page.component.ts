import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, ChangeDetectionStrategy, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService, authErrorMessage, canUsePasskeys, Passkey } from '@jordylab-fe/shared/auth';
import { HlmButtonDirective } from '@spartan-ng/ui-button-helm';
import { PageHeaderComponent } from '@jordylab-fe/shared/layout';

/**
 * The signed-in user's account settings (spec 019 US1/US2): display name, password, and the passkeys that sign in with
 * this device's fingerprint or face. Emails do not exist on this platform — none is shown or asked for.
 */
@Component({
  selector: 'app-account-page',
  standalone: true,
  imports: [DatePipe, FormsModule, HlmButtonDirective, PageHeaderComponent],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './account-page.component.html',
})
export class AccountPageComponent {
  readonly #auth = inject(AuthService);

  protected displayName = this.#auth.displayName() ?? '';
  protected currentPassword = '';
  protected newPassword = '';
  protected newPasskeyLabel = 'This device';

  protected readonly displayNameSaving = signal(false);
  protected readonly displayNameMessage = signal<string | null>(null);
  protected readonly displayNameFailed = signal(false);
  protected readonly passwordSaving = signal(false);
  protected readonly passwordMessage = signal<string | null>(null);
  protected readonly passwordFailed = signal(false);

  protected readonly passkeysSupported = canUsePasskeys();
  protected readonly passkeys = signal<Passkey[]>([]);
  protected readonly passkeysLoading = signal(true);
  protected readonly passkeysError = signal<string | null>(null);
  protected readonly passkeyBusy = signal(false);

  constructor() {
    void this.loadPasskeys();
  }

  async loadPasskeys(): Promise<void> {
    this.passkeysLoading.set(true);
    this.passkeysError.set(null);
    try {
      this.passkeys.set(await this.#auth.passkeys());
    } catch (failure) {
      this.passkeysError.set(authErrorMessage(failure));
    } finally {
      this.passkeysLoading.set(false);
    }
  }

  async onAddPasskey(): Promise<void> {
    if (this.passkeyBusy()) {
      return;
    }
    this.passkeyBusy.set(true);
    this.passkeysError.set(null);
    try {
      await this.#auth.addPasskey(this.newPasskeyLabel.trim() || 'Passkey');
      await this.loadPasskeys();
    } catch (failure) {
      if (failure instanceof Error && failure.message === 'PASSKEY_CANCELLED') {
        this.passkeysError.set('Passkey creation was cancelled.');
      } else {
        this.passkeysError.set(authErrorMessage(failure));
      }
    } finally {
      this.passkeyBusy.set(false);
    }
  }

  async onRemovePasskey(credentialId: string): Promise<void> {
    if (this.passkeyBusy()) {
      return;
    }
    this.passkeyBusy.set(true);
    this.passkeysError.set(null);
    try {
      await this.#auth.removePasskey(credentialId);
      await this.loadPasskeys();
    } catch (failure) {
      this.passkeysError.set(
        failure instanceof HttpErrorResponse && failure.status === 404
          ? 'That passkey no longer exists.'
          : authErrorMessage(failure),
      );
    } finally {
      this.passkeyBusy.set(false);
    }
  }

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
