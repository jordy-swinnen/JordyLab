import { ChangeDetectionStrategy, Component, inject, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '@jordylab-fe/shared/auth';

/**
 * The role-aware landing of `/settings` (spec 017): the admin lands on Users, a guest on their
 * Feedback tab. A component rather than a pathless guard route — Angular rejects an empty route
 * with no component/redirect/children (NG04014).
 */
@Component({
  selector: 'app-settings-home',
  standalone: true,
  template: '',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SettingsHomeComponent implements OnInit {
  readonly #auth = inject(AuthService);
  readonly #router = inject(Router);

  ngOnInit(): void {
    void this.#router.navigateByUrl(
      this.#auth.isAdmin() ? '/settings/users' : '/settings/feedback',
    );
  }
}
