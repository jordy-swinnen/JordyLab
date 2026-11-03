import { Component, inject, ChangeDetectionStrategy } from '@angular/core';
import { GuestChatLimitStore, UsersStore } from '@jordylab-fe/settings/api';
import { UsersPageViewComponent } from './users-page-view.component';

@Component({
  selector: 'lib-users-page',
  standalone: true,
  imports: [UsersPageViewComponent],
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `
    <lib-users-page-view
      [pendingUsers]="pendingUsers()"
      [approvedUsers]="approvedUsers()"
      [rejectedUsers]="rejectedUsers()"
      [loading]="loading()"
      [error]="error()"
      [actioningId]="actioningId()"
      (reload)="onReload()"
      (approve)="onApprove($event)"
      (reject)="onReject($event)"
      (revoke)="onRevoke($event)"
      [guestLimit]="guestLimit()"
      [guestLimitLoading]="guestLimitLoading()"
      [guestLimitSaving]="guestLimitSaving()"
      [guestLimitError]="guestLimitError()"
      (saveGuestLimit)="onSaveGuestLimit($event)"
    />
  `,
})
export class UsersPageComponent {
  readonly #store = inject(UsersStore);
  readonly #guestChatLimit = inject(GuestChatLimitStore);

  readonly pendingUsers = this.#store.pendingUsers;
  readonly approvedUsers = this.#store.approvedUsers;
  readonly rejectedUsers = this.#store.rejectedUsers;
  readonly loading = this.#store.loading;
  readonly error = this.#store.error;
  readonly actioningId = this.#store.actioningId;
  readonly guestLimit = this.#guestChatLimit.limit;
  readonly guestLimitLoading = this.#guestChatLimit.loading;
  readonly guestLimitSaving = this.#guestChatLimit.saving;
  readonly guestLimitError = this.#guestChatLimit.error;

  onReload(): void {
    this.#store.load();
  }

  onApprove(id: string): void {
    this.#store.approve(id);
  }

  onReject(id: string): void {
    this.#store.reject(id);
  }

  onRevoke(id: string): void {
    this.#store.revoke(id);
  }

  onSaveGuestLimit(dailyLimit: number): void {
    this.#guestChatLimit.saveDefault(dailyLimit);
  }
}
