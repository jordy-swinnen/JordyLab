import { Component, inject, ChangeDetectionStrategy } from '@angular/core';
import { UsersStore } from '@jordylab-fe/settings/api';
import { HlmBadgeDirective } from '@spartan-ng/ui-badge-helm';

/**
 * Only instantiated while its parent's `@if` is true (e.g. an admin-only nav group), so
 * `UsersStore` — and the `/api/settings/users/pending-count` call it fires on construction —
 * never gets created for a signed-in guest.
 */
@Component({
  selector: 'lib-pending-count-badge',
  standalone: true,
  imports: [HlmBadgeDirective],
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `
    @if (pendingCount() > 0) {
      <span hlmBadge variant="secondary" data-testid="pending-count-badge">{{ pendingCount() }}</span>
    }
  `,
})
export class PendingCountBadgeComponent {
  readonly #store = inject(UsersStore);

  readonly pendingCount = this.#store.pendingCount;
}
