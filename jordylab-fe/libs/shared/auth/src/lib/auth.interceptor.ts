import { HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from './auth.service';
import { from, switchMap } from 'rxjs';

/**
 * Adds the Keycloak bearer token to every outgoing request whose URL is
 * either relative (e.g. {@code /api/...}) or points at the configured backend
 * origin. Tokens are fetched lazily from the {@link AuthService} so the
 * interceptor never holds a stale copy.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);

  return from(auth.getToken()).pipe(
    switchMap((token) => {
      if (!token) {
        return next(req);
      }
      const authed: HttpRequest<unknown> = req.clone({
        setHeaders: { Authorization: `Bearer ${token}` },
      });

      return next(authed);
    }),
  );
};
