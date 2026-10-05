import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/**
 * Redirects unauthenticated users to {@code /login}, remembering where they were going in {@code returnUrl} so the
 * login page can take them there once they are signed in (the native login finishes outside the router, and a
 * share that arrived while signed out must still end on the share screen). Every app that mounts this guard —
 * `jordylab` and the `fna`/`gamecatalog` standalone dev harnesses — runs its own Keycloak
 * check independently; there is no shared host session to inherit a token from.
 */
export const authGuard: CanActivateFn = async (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (auth.isAuthenticated()) {
    return true;
  }
  const authenticated = await auth.init();
  if (authenticated) {
    return true;
  }

  const wanted = state.url;
  const returnUrl = wanted && wanted !== '/' && !wanted.startsWith('/login') ? wanted : undefined;

  return router.createUrlTree(['/login'], returnUrl ? { queryParams: { returnUrl } } : {});
};
