import { Component, inject } from '@angular/core';
import { UsersStore } from '@jordylab-fe/settings/api';
import { UsersPageViewComponent } from './users-page-view.component';

@Component({
  selector: 'lib-users-page',
  standalone: true,
  imports: [UsersPageViewComponent],
  template: `
    <lib-users-page-view
      [pendingUsers]="pendingUsers()"
      [approvedUsers]="approvedUsers()"
      [rejectedUsers]="rejectedUsers()"
      [loading]="loading()"
      [error]="error()"
      [actioningId]="actioningId()"
      [temporaryPassword]="temporaryPassword()"
      (approve)="onApprove($event)"
      (reject)="onReject($event)"
      (revoke)="onRevoke($event)"
      (resetPassword)="onResetPassword($event)"
      (dismissTemporaryPassword)="onDismissTemporaryPassword()"
    />
  `,
})
export class UsersPageComponent {
  readonly #store = inject(UsersStore);

  readonly pendingUsers = this.#store.pendingUsers;
  readonly approvedUsers = this.#store.approvedUsers;
  readonly rejectedUsers = this.#store.rejectedUsers;
  readonly loading = this.#store.loading;
  readonly error = this.#store.error;
  readonly actioningId = this.#store.actioningId;
  readonly temporaryPassword = this.#store.temporaryPassword;

  onApprove(id: string): void {
    this.#store.approve(id);
  }

  onReject(id: string): void {
    this.#store.reject(id);
  }

  onRevoke(id: string): void {
    this.#store.revoke(id);
  }

  onResetPassword(id: string): void {
    this.#store.resetPassword(id);
  }

  onDismissTemporaryPassword(): void {
    this.#store.clearTemporaryPassword();
  }
}
