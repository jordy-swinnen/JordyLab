import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AccessRole, AppRole, AuthService } from './auth.service';

/** Where each role lands when it is denied a route it does not hold. */
const ROLE_HOME: Record<AppRole, string> = {
  admin: '/fna',
  guest: '/games/grid',
};

/** Shown when an approved person has no app grants at all (spec 014). */
const NO_APPS = '/no-apps';

/**
 * Role-aware route guard mirroring the backend access matrix:
 * unauthenticated visitors go to `/login`; an authenticated account holding no application
 * role (a pending sign-up) goes to `/awaiting-approval`; and a user whose role is not in
 * {@code allowedRoles} is sent to their own landing route rather than an admin page.
 *
 * Since spec 014 the Game Catalog needs the per-app grant `app-gamecatalog` as well as approval,
 * so an approved guest holding no app grant is sent to the "No apps yet" page rather than to a
 * route the server would refuse.
 */
export function roleGuard(...allowedRoles: (AppRole | AccessRole)[]): CanActivateFn {
  return async () => {
    const auth = inject(AuthService);
    const router = inject(Router);

    if (!auth.isAuthenticated()) {
      const authenticated = await auth.init();
      if (!authenticated) {
        return router.parseUrl('/login');
      }
    }

    const roles = auth.roles();
    if (roles.includes('admin')) {
      return allowedRoles.includes('admin')
        ? true
        : router.parseUrl(ROLE_HOME.admin);
    }

    const hasGameCatalog = roles.includes('app-gamecatalog');
    if (roles.includes('guest') || hasGameCatalog) {
      const allowed =
        allowedRoles.includes('guest') ||
        (allowedRoles.includes('app-gamecatalog') && hasGameCatalog);
      if (allowed) {
        return true;
      }

      return router.parseUrl(hasGameCatalog ? ROLE_HOME.guest : NO_APPS);
    }

    return router.parseUrl('/awaiting-approval');
  };
}
