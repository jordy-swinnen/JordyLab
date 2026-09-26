import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/**
 * Redirects unauthenticated users to {@code /login}. Every app that mounts this guard —
 * `jordylab` and the `fna`/`gamecatalog` standalone dev harnesses — runs its own Keycloak
 * check independently; there is no shared host session to inherit a token from.
 */
export const authGuard: CanActivateFn = async () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (auth.isAuthenticated()) {
    return true;
  }
  const authenticated = await auth.init();
  if (authenticated) {
    return true;
  }

  return router.parseUrl('/login');
};
