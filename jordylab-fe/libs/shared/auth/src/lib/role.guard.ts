import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AppRole, AuthService } from './auth.service';

/** Where each role lands when it is denied a route it does not hold. */
const ROLE_HOME: Record<AppRole, string> = {
  admin: '/fna',
  guest: '/games/grid',
};

/**
 * Role-aware route guard mirroring the backend access matrix:
 * unauthenticated visitors go to `/login`; an authenticated account holding no application
 * role (a pending sign-up) goes to `/awaiting-approval`; and a user whose role is not in
 * {@code allowedRoles} is sent to their own landing route rather than an admin page.
 */
export function roleGuard(...allowedRoles: AppRole[]): CanActivateFn {
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
    if (roles.includes('guest')) {
      return allowedRoles.includes('guest')
        ? true
        : router.parseUrl(ROLE_HOME.guest);
    }

    return router.parseUrl('/awaiting-approval');
  };
}
