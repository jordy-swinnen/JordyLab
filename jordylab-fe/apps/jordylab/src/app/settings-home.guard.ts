import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '@jordylab-fe/shared/auth';

/**
 * The role-aware landing of `/settings` (spec 017): the admin lands on Users, a guest lands on
 * their Feedback tab. The route itself admits both roles; each tab re-guards itself.
 */
export const settingsHomeGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  return auth.isAdmin()
    ? router.createUrlTree(['/settings/users'])
    : router.createUrlTree(['/settings/feedback']);
};
