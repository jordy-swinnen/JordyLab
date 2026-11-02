import { computed, inject, Injectable, signal, Signal } from '@angular/core';
import Keycloak, { KeycloakInstance, KeycloakTokenParsed } from 'keycloak-js';
import { AUTH_CONFIG } from './auth-config';

/**
 * How long start-up waits for Keycloak's silent check before carrying on signed out. Without a limit an unreachable (or
 * frame-blocked) Keycloak left the whole app on a blank page: `keycloak.init` never settles.
 */
const KEYCLOAK_INIT_TIMEOUT_MS = 8000;

/** The two application roles the realm grants. Self-registered users hold neither until approved. */
export type AppRole = 'admin' | 'guest';

/**
 * Per-app grant roles (spec 014). Holding one is not approval: the person also needs `guest`.
 * The Game Catalog is the first app that can be granted per person.
 */
export type AccessRole = 'app-gamecatalog';

const APP_ROLES: readonly AppRole[] = ['admin', 'guest'];

function realmRolesFrom(tokenParsed: KeycloakTokenParsed | undefined): string[] {
  const realmAccess = tokenParsed?.['realm_access'] as { roles?: unknown } | undefined;
  const roles = realmAccess?.roles;
  if (!Array.isArray(roles)) {
    return [];
  }

  return roles.filter((role): role is string => typeof role === 'string');
}

/** Keycloak application-initiated actions a user may start on their own account (006 US5). */
export type AccountAction = 'UPDATE_PASSWORD' | 'UPDATE_PROFILE' | 'UPDATE_EMAIL';

/**
 * Wraps the official `keycloak-js` SDK behind Angular signals. Every deployable app (`jordylab`, and the
 * `fna`/`gamecatalog` standalone dev harnesses) provides its own {@link AUTH_CONFIG} and uses this same service, so each
 * authenticates against the real Keycloak realm independently rather than relying on a host shell to hold the only
 * session.
 *
 * <p>Everything runs in the page itself — the standard browser redirect flow. The native app's system-browser login and
 * manual token exchange were removed with the native wrapper (spec 017 US4).
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
  /** True once a session really existed (sign-in or silent SSO); only then can one "die". */
  #hadSession = false;

  readonly isAuthenticated = this.#authenticated.asReadonly();
  readonly username: Signal<string | null> = this.#username.asReadonly();
  readonly token: Signal<string | null> = this.#token.asReadonly();
  readonly roles: Signal<string[]> = this.#roles.asReadonly();
  readonly isAdmin = computed(() => this.#roles().includes('admin'));
  readonly isGuest = computed(() => this.#roles().includes('guest'));
  readonly hasAppRole = computed(() =>
    APP_ROLES.some((role) => this.#roles().includes(role)),
  );
  /** Admin, or an approved person the admin granted the Game Catalog to (spec 014). */
  readonly hasGameCatalogAccess = computed(
    () => this.isAdmin() || this.#roles().includes('app-gamecatalog'),
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

  async login(redirectPath?: string): Promise<void> {
    if (!this.#keycloak) {
      await this.init();
    }
    // A share that arrived while signed out must return to the share screen (spec 017 FR-008): the login page forwards
    // its sanitised returnUrl here, so Keycloak's redirect lands back on that in-app path instead of the bare origin.
    const redirectUri = redirectPath
      ? new URL(redirectPath, window.location.origin).toString()
      : window.location.origin;
    await this.#keycloak?.login({ redirectUri });
  }

  /**
   * Lets the signed-in user change their own password or profile (name, email) on Keycloak's own pages, then come
   * back here — Keycloak application-initiated actions, no admin needed (006 US5, FR-008).
   */
  async requestAction(action: AccountAction): Promise<void> {
    if (!this.#keycloak) {
      await this.init();
    }
    await this.#keycloak?.login({ redirectUri: window.location.href, action });
  }

  async logout(): Promise<void> {
    await this.#keycloak?.logout({ redirectUri: window.location.origin });
  }

  async getToken(): Promise<string | null> {
    if (!this.#keycloak) {
      return null;
    }
    if (!this.#hadSession) {
      // Nobody has signed in yet: there is no session to refresh, so there is nothing to fail.
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
      const initialization = keycloak.init({
        onLoad: 'check-sso',
        silentCheckSsoRedirectUri: `${window.location.origin}/silent-check-sso.html`,
        silentCheckSsoFallback: false,
        pkceMethod: 'S256',
        checkLoginIframe: false,
      });
      const authenticated = await this.#settleWithin(initialization, KEYCLOAK_INIT_TIMEOUT_MS);
      this.#authenticated.set(authenticated);
      if (authenticated) {
        this.#hadSession = true;
        this.#applyToken();
      }

      return authenticated;
    } catch (error) {
      console.error('Keycloak init failed', error);

      return false;
    }
  }

  /** The result of `work`, or `false` (signed out) when it has not settled after `milliseconds`. */
  async #settleWithin(work: Promise<boolean>, milliseconds: number): Promise<boolean> {
    let timer: ReturnType<typeof setTimeout> | undefined;
    const timeout = new Promise<boolean>((resolve) => {
      timer = setTimeout(() => {
        console.error(`Keycloak did not answer within ${milliseconds / 1000} s; continuing signed out`);
        resolve(false);
      }, milliseconds);
    });
    try {
      return await Promise.race([work, timeout]);
    } finally {
      clearTimeout(timer);
    }
  }
}
