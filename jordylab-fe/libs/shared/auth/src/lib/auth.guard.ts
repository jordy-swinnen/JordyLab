import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/**
 * Redirects unauthenticated users to {@code /login}. The host shell gates
 * the whole micro-frontend topology behind this guard; remotes inherit the
 * token via the {@link authInterceptor} and don't run their own check.
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
