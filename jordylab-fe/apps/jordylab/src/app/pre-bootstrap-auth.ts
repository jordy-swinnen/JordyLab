import { Injector } from '@angular/core';
import { AUTH_CONFIG, AuthConfig, AuthService } from '@jordylab-fe/shared/auth';

/**
 * A throwaway injector that only knows `AUTH_CONFIG`, used for the early Keycloak check that runs before
 * `bootstrapApplication`. Whatever `AuthService` injects in its constructor must therefore be available here (no router,
 * no HTTP client) — a missing provider is a blank page on every start. This instance is separate from the one
 * components later inject via `providedIn: 'root'`.
 */
export function createPreBootstrapAuth(config: AuthConfig): AuthService {
  const injector = Injector.create({
    providers: [{ provide: AUTH_CONFIG, useValue: config }, AuthService],
  });

  return injector.get(AuthService);
}
