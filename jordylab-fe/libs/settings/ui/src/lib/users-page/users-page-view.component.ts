import { DatePipe } from '@angular/common';
import { Component, input, output } from '@angular/core';
import { AppUser } from '@jordylab-fe/settings/api';
import { HlmBadgeDirective } from '@spartan-ng/ui-badge-helm';
import { HlmButtonDirective } from '@spartan-ng/ui-button-helm';
import { HlmSkeletonComponent } from '@spartan-ng/ui-skeleton-helm';

interface TemporaryPassword {
  userId: string;
  password: string;
}

@Component({
  selector: 'lib-users-page-view',
  standalone: true,
  imports: [DatePipe, HlmBadgeDirective, HlmButtonDirective, HlmSkeletonComponent],
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

  displayName(user: AppUser): string {
    const name = `${user.firstName} ${user.lastName}`.trim();

    return name.length > 0 ? name : user.email;
  }
}
