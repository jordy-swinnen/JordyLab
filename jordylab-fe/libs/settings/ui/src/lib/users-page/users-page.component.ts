import { Component, inject, ChangeDetectionStrategy } from '@angular/core';
import { GuestChatLimitStore, UserSession, UserSessionsStore, UsersStore } from '@jordylab-fe/settings/api';
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
      [temporaryPassword]="temporaryPassword()"
      (reload)="onReload()"
      (approve)="onApprove($event)"
      (reject)="onReject($event)"
      (revoke)="onRevoke($event)"
      (grantApp)="onGrantApp($event)"
      (removeApp)="onRemoveApp($event)"
      (setPersonalLimit)="onSetPersonalLimit($event)"
      (clearPersonalLimit)="onClearPersonalLimit($event)"
      (deletePerson)="onDeletePerson($event)"
      (toggleSessions)="onToggleSessions($event)"
      (endSession)="onEndSession($event)"
      (endAllSessions)="onEndAllSessions($event)"
      [openSessionsUserId]="sessionsUserId()"
      [sessions]="sessions()"
      [sessionsLoading]="sessionsLoading()"
      [sessionsActioning]="sessionsActioning()"
      [sessionsError]="sessionsError()"
      [guestLimit]="guestLimit()"
      [guestLimitLoading]="guestLimitLoading()"
      [guestLimitSaving]="guestLimitSaving()"
      [guestLimitError]="guestLimitError()"
      (saveGuestLimit)="onSaveGuestLimit($event)"
      (resetPassword)="onResetPassword($event)"
      (dismissTemporaryPassword)="onDismissTemporaryPassword()"
    />
  `,
})
export class UsersPageComponent {
  readonly #store = inject(UsersStore);
  readonly #guestChatLimit = inject(GuestChatLimitStore);
  readonly #sessions = inject(UserSessionsStore);

  readonly pendingUsers = this.#store.pendingUsers;
  readonly approvedUsers = this.#store.approvedUsers;
  readonly rejectedUsers = this.#store.rejectedUsers;
  readonly loading = this.#store.loading;
  readonly error = this.#store.error;
  readonly actioningId = this.#store.actioningId;
  readonly temporaryPassword = this.#store.temporaryPassword;
  readonly guestLimit = this.#guestChatLimit.limit;
  readonly guestLimitLoading = this.#guestChatLimit.loading;
  readonly guestLimitSaving = this.#guestChatLimit.saving;
  readonly guestLimitError = this.#guestChatLimit.error;
  readonly sessionsUserId = this.#sessions.userId;
  readonly sessions = this.#sessions.sessions;
  readonly sessionsLoading = this.#sessions.loading;
  readonly sessionsActioning = this.#sessions.actioning;
  readonly sessionsError = this.#sessions.error;

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

  onGrantApp(change: { userId: string; appKey: string }): void {
    this.#store.grantApp(change.userId, change.appKey);
  }

  onRemoveApp(change: { userId: string; appKey: string }): void {
    this.#store.removeApp(change.userId, change.appKey);
  }

  onSetPersonalLimit(change: { userId: string; dailyLimit: number }): void {
    this.#store.setPersonalLimit(change.userId, change.dailyLimit);
  }

  onClearPersonalLimit(id: string): void {
    this.#store.clearPersonalLimit(id);
  }

  onSaveGuestLimit(dailyLimit: number): void {
    this.#guestChatLimit.saveDefault(dailyLimit);
  }

  onDeletePerson(id: string): void {
    this.#store.deletePerson(id);
  }

  onToggleSessions(userId: string): void {
    if (this.#sessions.userId() === userId) {
      this.#sessions.close();

      return;
    }
    this.#sessions.loadFor(userId);
  }

  onEndSession(session: UserSession): void {
    this.#sessions.endOne(session.id, session.kind);
  }

  onEndAllSessions(userId: string): void {
    if (this.#sessions.userId() !== userId) {
      this.#sessions.loadFor(userId);
    }
    this.#sessions.endAll();
  }

  onResetPassword(id: string): void {
    this.#store.resetPassword(id);
  }

  onDismissTemporaryPassword(): void {
    this.#store.clearTemporaryPassword();
  }
}
