import { DatePipe } from '@angular/common';
import { Component, input, output, ChangeDetectionStrategy, signal } from '@angular/core';
import { AppUser, GuestChatLimit, UserSession } from '@jordylab-fe/settings/api';
import { HlmBadgeDirective } from '@spartan-ng/ui-badge-helm';
import { HlmButtonDirective } from '@spartan-ng/ui-button-helm';
import { ErrorStateComponent, PageHeaderComponent, SkeletonRowsComponent } from '@jordylab-fe/shared/layout';
import { GuestChatLimitCardComponent } from './guest-chat-limit-card.component';

/**
 * The admin's Users page (spec 019 US1, parity with spec 014's panel). Approve, reject and revoke; the app-grant
 * toggles, per-person limits, session panel and deletion return with US4, reset decisions with US6.
 */
@Component({
  selector: 'lib-users-page-view',
  standalone: true,
  imports: [
    DatePipe,
    HlmBadgeDirective,
    HlmButtonDirective,
    ErrorStateComponent,
    PageHeaderComponent,
    SkeletonRowsComponent,
    GuestChatLimitCardComponent,
  ],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './users-page-view.component.html',
})
export class UsersPageViewComponent {
  pendingUsers = input.required<AppUser[]>();
  approvedUsers = input.required<AppUser[]>();
  rejectedUsers = input.required<AppUser[]>();
  loading = input.required<boolean>();
  error = input.required<string | null>();
  actioningId = input.required<string | null>();

  approve = output<string>();
  reject = output<string>();
  revoke = output<string>();
  grantApp = output<string>();
  removeApp = output<string>();
  toggleSessions = output<string>();
  endSession = output<UserSession>();
  endAllSessions = output<string>();
  deletePerson = output<string>();
  reload = output<void>();
  saveGuestLimit = output<number>();

  sessionsUserId = input.required<string | null>();
  sessions = input.required<UserSession[]>();
  sessionsLoading = input.required<boolean>();

  protected readonly confirmingDeleteId = signal<string | null>(null);

  onDelete(id: string): void {
    if (this.confirmingDeleteId() === id) {
      this.confirmingDeleteId.set(null);
      this.deletePerson.emit(id);

      return;
    }
    this.confirmingDeleteId.set(id);
  }


  guestLimit = input.required<GuestChatLimit | null>();
  guestLimitLoading = input.required<boolean>();
  guestLimitSaving = input.required<boolean>();
  guestLimitError = input.required<string | null>();

  displayName(user: AppUser): string {
    return user.displayName?.trim() || user.username;
  }
}
