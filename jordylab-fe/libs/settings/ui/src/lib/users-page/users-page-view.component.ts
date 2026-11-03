import { DatePipe } from '@angular/common';
import { Component, input, output, ChangeDetectionStrategy } from '@angular/core';
import { AppUser, GuestChatLimit } from '@jordylab-fe/settings/api';
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
  reload = output<void>();
  saveGuestLimit = output<number>();

  guestLimit = input.required<GuestChatLimit | null>();
  guestLimitLoading = input.required<boolean>();
  guestLimitSaving = input.required<boolean>();
  guestLimitError = input.required<string | null>();

  displayName(user: AppUser): string {
    return user.displayName?.trim() || user.username;
  }
}
