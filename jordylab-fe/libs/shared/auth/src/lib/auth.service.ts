import { computed, inject, Injectable, signal, Signal } from '@angular/core';
import Keycloak, { KeycloakInstance, KeycloakTokenParsed } from 'keycloak-js';
import { AUTH_CONFIG } from './auth-config';

/** The two application roles the realm grants. Self-registered users hold neither until approved. */
export type AppRole = 'admin' | 'guest';

const APP_ROLES: readonly AppRole[] = ['admin', 'guest'];

function realmRolesFrom(tokenParsed: KeycloakTokenParsed | undefined): string[] {
  const realmAccess = tokenParsed?.['realm_access'] as { roles?: unknown } | undefined;
  const roles = realmAccess?.roles;
  if (!Array.isArray(roles)) {
    return [];
  }

  return roles.filter((role): role is string => typeof role === 'string');
}

/**
 * Wraps the official `keycloak-js` SDK behind Angular signals. Every deployable app
 * (`jordylab`, and the `fna`/`gamecatalog` standalone dev harnesses) provides its own
 * {@link AUTH_CONFIG} and uses this same service, so each authenticates against the real
 * Keycloak realm independently rather than relying on a host shell to hold the only session.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  #config = inject(AUTH_CONFIG);
  #keycloak: KeycloakInstance | null = null;
  #init: Promise<boolean> | null = null;
  #authenticated = signal(false);
  #username = signal<string | null>(null);
  #token = signal<string | null>(null);
  #roles = signal<string[]>([]);

  readonly isAuthenticated = this.#authenticated.asReadonly();
  readonly username: Signal<string | null> = this.#username.asReadonly();
  readonly token: Signal<string | null> = this.#token.asReadonly();
  readonly roles: Signal<string[]> = this.#roles.asReadonly();
  readonly isAdmin = computed(() => this.#roles().includes('admin'));
  readonly isGuest = computed(() => this.#roles().includes('guest'));
  readonly hasAppRole = computed(() =>
    APP_ROLES.some((role) => this.#roles().includes(role)),
  );

  /**
   * Idempotent and concurrency-safe: route guards run in parallel and may call this
   * simultaneously (e.g. `authGuard` and `roleGuard` on the same route while a sign-in
   * callback is still being exchanged), so every caller shares one in-flight Keycloak
   * initialization instead of one caller reading half-initialized state.
   */
  async init(): Promise<boolean> {
    this.#init ??= this.#initializeKeycloak();

    return this.#init;
  }

  async login(): Promise<void> {
    if (!this.#keycloak) {
      await this.init();
    }
    await this.#keycloak?.login({ redirectUri: window.location.origin });
  }

  async logout(): Promise<void> {
    await this.#keycloak?.logout({ redirectUri: window.location.origin });
  }

  async getToken(): Promise<string | null> {
    if (!this.#keycloak) {
      return null;
    }
    try {
      await this.#keycloak.updateToken(30);
    } catch (error) {
      console.error('Token refresh failed', error);
      await this.login();

      return null;
    }
    this.#applyToken();

    return this.#token();
  }

  #applyToken(): void {
    const tokenParsed = this.#keycloak?.tokenParsed;
    this.#token.set(this.#keycloak?.token ?? null);
    this.#username.set(tokenParsed?.['preferred_username'] ?? null);
    this.#roles.set(realmRolesFrom(tokenParsed));
  }

  async #initializeKeycloak(): Promise<boolean> {
    const keycloak = new Keycloak({
      url: this.#config.keycloakUrl,
      realm: this.#config.keycloakRealm,
      clientId: this.#config.keycloakClientId,
    });
    this.#keycloak = keycloak;
    try {
      const authenticated = await keycloak.init({
        onLoad: 'check-sso',
        silentCheckSsoRedirectUri: `${window.location.origin}/silent-check-sso.html`,
        silentCheckSsoFallback: false,
        pkceMethod: 'S256',
        checkLoginIframe: false,
      });
      this.#authenticated.set(authenticated);
      if (authenticated) {
        this.#applyToken();
      }

      return authenticated;
    } catch (error) {
      console.error('Keycloak init failed', error);

      return false;
    }
  }
}
