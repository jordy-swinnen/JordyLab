import { DatePipe } from '@angular/common';
import { Component, input, output, ChangeDetectionStrategy } from '@angular/core';
import { AppUser, GuestChatLimit, UserSession } from '@jordylab-fe/settings/api';
import { HlmBadgeDirective } from '@spartan-ng/ui-badge-helm';
import { HlmButtonDirective } from '@spartan-ng/ui-button-helm';
import { ErrorStateComponent, PageHeaderComponent, SkeletonRowsComponent } from '@jordylab-fe/shared/layout';
import { DeletePersonDialogComponent } from './delete-person-dialog.component';
import { GuestChatLimitCardComponent } from './guest-chat-limit-card.component';
import { PersonAccessComponent } from './person-access.component';
import { PersonChatLimitComponent } from './person-chat-limit.component';
import { PersonSessionsComponent } from './person-sessions.component';

interface TemporaryPassword {
  userId: string;
  password: string;
}

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
    DeletePersonDialogComponent,
    GuestChatLimitCardComponent,
    PersonAccessComponent,
    PersonChatLimitComponent,
    PersonSessionsComponent,
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
  temporaryPassword = input.required<TemporaryPassword | null>();

  approve = output<string>();
  reject = output<string>();
  revoke = output<string>();
  resetPassword = output<string>();
  dismissTemporaryPassword = output<void>();
  reload = output<void>();
  grantApp = output<{ userId: string; appKey: string }>();
  removeApp = output<{ userId: string; appKey: string }>();
  setPersonalLimit = output<{ userId: string; dailyLimit: number }>();
  clearPersonalLimit = output<string>();
  deletePerson = output<string>();
  toggleSessions = output<string>();
  endSession = output<UserSession>();
  endAllSessions = output<string>();
  saveGuestLimit = output<number>();

  guestLimit = input.required<GuestChatLimit | null>();
  guestLimitLoading = input.required<boolean>();
  guestLimitSaving = input.required<boolean>();
  guestLimitError = input.required<string | null>();
  openSessionsUserId = input.required<string | null>();
  sessions = input.required<UserSession[]>();
  sessionsLoading = input.required<boolean>();
  sessionsActioning = input.required<boolean>();
  sessionsError = input.required<string | null>();

  displayName(user: AppUser): string {
    const name = `${user.firstName} ${user.lastName}`.trim();

    return name.length > 0 ? name : user.email;
  }
}
