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
  /**
   * The Android App Link the native build's login flow returns to (research D2) —
   * `https://{PRODUCTION_DOMAIN}/mobile/callback`. Only `environment.mobile.ts` sets this; web
   * builds never read it (their login flow is a same-origin redirect, not an App Link).
   */
  readonly mobileCallbackUri?: string;
}

export const AUTH_CONFIG = new InjectionToken<AuthConfig>('AUTH_CONFIG');
