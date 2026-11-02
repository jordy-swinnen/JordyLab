import { InjectionToken } from '@angular/core';

/**
 * Keycloak connection details a consuming app supplies from its own
 * `environments/environment.ts` (dev) / `environment.prod.ts` (prod). All three apps
 * (`jordylab`, `fna`, `gamecatalog`) provide the same realm/client — only the redirect origin
 * differs, and that's read from `window.location.origin` at runtime, not from here.
 */
export interface AuthConfig {
  readonly keycloakUrl: string;
  readonly keycloakRealm: string;
  readonly keycloakClientId: string;
}

export const AUTH_CONFIG = new InjectionToken<AuthConfig>('AUTH_CONFIG');
