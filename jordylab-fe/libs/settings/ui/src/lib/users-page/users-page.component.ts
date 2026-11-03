import { Component, inject, ChangeDetectionStrategy } from '@angular/core';
import { GuestChatLimitStore, UserSession, UsersStore } from '@jordylab-fe/settings/api';
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
      (grantApp)="onGrantApp($event)"
      (removeApp)="onRemoveApp($event)"
      (toggleSessions)="onToggleSessions($event)"
      (endSession)="onEndSession($event)"
      (endAllSessions)="onEndAllSessions()"
      (deletePerson)="onDeletePerson($event)"
      (resetPassword)="onResetPassword($event)"
      (approveResetRequest)="onApproveResetRequest($event)"
      (rejectResetRequest)="onRejectResetRequest($event)"
      (dismissResetResult)="onDismissResetResult()"
      [sessionsUserId]="sessionsUserId()"
      [sessions]="sessions()"
      [sessionsLoading]="sessionsLoading()"
      [resetResult]="resetResult()"
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
  readonly sessionsUserId = this.#store.sessionsUserId;
  readonly sessions = this.#store.sessions;
  readonly sessionsLoading = this.#store.sessionsLoading;
  readonly resetResult = this.#store.resetResult;
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

  onGrantApp(id: string): void {
    this.#store.grantApp(id, 'gamecatalog');
  }

  onRemoveApp(id: string): void {
    this.#store.removeApp(id, 'gamecatalog');
  }

  onToggleSessions(id: string): void {
    this.#store.toggleSessions(id);
  }

  onEndSession(session: UserSession): void {
    const userId = this.#store.sessionsUserId();
    if (userId) {
      this.#store.endSession(userId, session.sessionId);
    }
  }

  onEndAllSessions(): void {
    const userId = this.#store.sessionsUserId();
    if (userId) {
      this.#store.endAllSessions(userId);
    }
  }

  onDeletePerson(id: string): void {
    this.#store.deletePerson(id);
  }

  onResetPassword(id: string): void {
    this.#store.resetPassword(id);
  }

  onApproveResetRequest(request: { userId: string; requestId: string }): void {
    this.#store.approveResetRequest(request.userId, request.requestId);
  }

  onRejectResetRequest(request: { userId: string; requestId: string }): void {
    this.#store.rejectResetRequest(request.userId, request.requestId);
  }

  onDismissResetResult(): void {
    this.#store.dismissResetResult();
  }

  onSaveGuestLimit(dailyLimit: number): void {
    this.#guestChatLimit.saveDefault(dailyLimit);
  }
}
