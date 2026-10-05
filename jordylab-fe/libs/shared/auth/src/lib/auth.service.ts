import {
  computed,
  inject,
  Injectable,
  Injector,
  signal,
  Signal,
} from '@angular/core';
import { Router } from '@angular/router';
import { Browser } from '@capacitor/browser';
import { Capacitor } from '@capacitor/core';
import Keycloak, { KeycloakInstance, KeycloakTokenParsed } from 'keycloak-js';
import { AUTH_CONFIG } from './auth-config';
import {
  codeChallengeFromVerifier,
  decodeJwtPayload,
  randomToken,
} from './pkce';

interface PendingNativeLogin {
  readonly state: string;
  readonly nonce: string;
  readonly codeVerifier: string;
  readonly redirectUri: string;
}

function stripTrailingSlash(url: string): string {
  return url.endsWith('/') ? url.slice(0, -1) : url;
}

/**
 * How long start-up waits for Keycloak's silent check before carrying on signed out. Without a limit an unreachable (or
 * frame-blocked) Keycloak left the whole app on a blank page: `keycloak.init` never settles.
 */
const KEYCLOAK_INIT_TIMEOUT_MS = 8000;

/** The two application roles the realm grants. Self-registered users hold neither until approved. */
export type AppRole = 'admin' | 'guest';

const APP_ROLES: readonly AppRole[] = ['admin', 'guest'];

function realmRolesFrom(
  tokenParsed: KeycloakTokenParsed | undefined,
): string[] {
  const realmAccess = tokenParsed?.['realm_access'] as
    | { roles?: unknown }
    | undefined;
  const roles = realmAccess?.roles;
  if (!Array.isArray(roles)) {
    return [];
  }

  return roles.filter((role): role is string => typeof role === 'string');
}

/** Keycloak application-initiated actions a user may start on their own account (006 US5). */
export type AccountAction =
  | 'UPDATE_PASSWORD'
  | 'UPDATE_PROFILE'
  | 'UPDATE_EMAIL';

/**
 * Wraps the official `keycloak-js` SDK behind Angular signals. Every deployable app
 * (`jordylab`, and the `fna`/`gamecatalog` standalone dev harnesses) provides its own
 * {@link AUTH_CONFIG} and uses this same service, so each authenticates against the real
 * Keycloak realm independently rather than relying on a host shell to hold the only session.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  #config = inject(AUTH_CONFIG);
  // Resolved lazily: the pre-bootstrap instance (apps/jordylab/src/main.ts) lives in an injector without a router.
  #injector = inject(Injector);
  #keycloak: KeycloakInstance | null = null;
  #init: Promise<boolean> | null = null;
  #authenticated = signal(false);
  #username = signal<string | null>(null);
  #token = signal<string | null>(null);
  #roles = signal<string[]>([]);
  #pendingNativeLogin: PendingNativeLogin | null = null;
  #nativeFailure = signal<string | null>(null);

  readonly isAuthenticated = this.#authenticated.asReadonly();
  readonly username: Signal<string | null> = this.#username.asReadonly();
  readonly token: Signal<string | null> = this.#token.asReadonly();
  readonly roles: Signal<string[]> = this.#roles.asReadonly();
  /** Why the last native token exchange (login callback, fingerprint unlock, refresh) failed — shown, never silent. */
  readonly nativeFailure: Signal<string | null> =
    this.#nativeFailure.asReadonly();
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
    if (Capacitor.isNativePlatform()) {
      await this.#loginNative();

      return;
    }
    await this.#keycloak?.login({ redirectUri: window.location.origin });
  }

  /**
   * Lets the signed-in user change their own password or profile (name, email) on Keycloak's own pages, then come
   * back here — Keycloak application-initiated actions, no admin needed (006 US5, FR-008).
   */
  async requestAction(action: AccountAction): Promise<void> {
    if (!this.#keycloak) {
      await this.init();
    }
    if (Capacitor.isNativePlatform()) {
      await this.#loginNative(action);

      return;
    }
    await this.#keycloak?.login({ redirectUri: window.location.href, action });
  }

  async logout(): Promise<void> {
    if (Capacitor.isNativePlatform()) {
      this.#logoutNative();

      return;
    }
    await this.#keycloak?.logout({ redirectUri: window.location.origin });
  }

  /**
   * Completes the native login flow started by {@link login} once the Android App Link callback
   * (`/mobile/callback`, research D2) arrives — the caller is `AppLinkService`'s `appUrlOpen`
   * listener (`libs/shared/platform/api`), which owns parsing that event and routing callback vs.
   * notification-tap URLs; this method only needs the full callback URL.
   *
   * **Implementation-time discovery**: `keycloak-js`'s `KeycloakAdapter` interface (`login`,
   * `logout`, `register`, `accountManagement`, `redirectUri`) cannot actually complete a login on
   * its own — the code-exchange/token-setting logic (`#processCallback`/`#setToken`) is private to
   * the `Keycloak` class and unreachable from an external adapter object, so passing a custom
   * `adapter` to `keycloak.init()` (D2's literal reading) cannot work for this keycloak-js version
   * (confirmed by reading `node_modules/keycloak-js/lib/keycloak.js`). What *is* public: `token`,
   * `tokenParsed`, `refreshToken`, `refreshTokenParsed`, `idToken`, `idTokenParsed`,
   * `authenticated`, `subject`, `realmAccess`, and `resourceAccess` are plain mutable instance
   * properties (verified against `keycloak.d.ts`), and `KeycloakInitOptions.token`/`refreshToken`
   * are a documented way to seed them — but `init()` may only run once per instance
   * (`didInitialize` guard). So this performs the OAuth code exchange manually (PKCE, `fetch` to
   * the token endpoint — the same approach `keycloak-js` itself uses internally) and then assigns
   * those same public fields directly, achieving D2's actual goal (system-browser login via an App
   * Link, reusing this one `AuthService`'s signal surface) without literally implementing
   * `KeycloakAdapter`.
   */
  async completeNativeLogin(callbackUrl: string): Promise<void> {
    const pending = this.#pendingNativeLogin;
    this.#pendingNativeLogin = null;
    if (!pending || !this.#keycloak) {
      return;
    }
    await Browser.close().catch(() => undefined);

    const url = new URL(callbackUrl);
    const code = url.searchParams.get('code');
    const state = url.searchParams.get('state');
    const error = url.searchParams.get('error');
    if (error || !code || state !== pending.state) {
      console.error('Native login callback rejected', {
        error,
        stateMatched: state === pending.state,
      });

      return;
    }

    const tokenUrl = `${stripTrailingSlash(this.#config.keycloakUrl)}/realms/${encodeURIComponent(this.#config.keycloakRealm)}/protocol/openid-connect/token`;
    const body = new URLSearchParams({
      grant_type: 'authorization_code',
      client_id: this.#config.keycloakClientId,
      code,
      redirect_uri: pending.redirectUri,
      code_verifier: pending.codeVerifier,
    });

    let response: Response;
    try {
      response = await fetch(tokenUrl, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: body.toString(),
      });
    } catch (fetchError) {
      console.error('Native login token exchange failed', fetchError);

      return;
    }
    if (!response.ok) {
      console.error('Native login token exchange rejected', response.status);

      return;
    }

    const tokens = (await response.json()) as {
      access_token: string;
      refresh_token?: string;
      id_token?: string;
    };
    this.#applyNativeTokens(
      tokens.access_token,
      tokens.refresh_token,
      tokens.id_token,
      pending.nonce,
    );
  }

  /**
   * The current `offline_access` refresh token, for {@link BiometricUnlockService} (spec US4) to
   * store behind a biometric-protected Keystore entry. `null` when not authenticated or the
   * session has no refresh token (e.g. the `offline_access` scope wasn't granted).
   */
  getRefreshToken(): string | null {
    return this.#keycloak?.refreshToken ?? null;
  }

  /**
   * Exchanges a refresh token recovered from biometric storage for a fresh access token (spec
   * US4, `BiometricUnlockService.unlock()`) — same manual-exchange approach as
   * {@link completeNativeLogin}, using the `refresh_token` grant instead of `authorization_code`.
   * Returns `false` on any failure (expired/revoked token — e.g. an admin's FR-013 revocation,
   * network error) so the caller falls back to a normal login, never a silent retry loop (FR-012).
   */
  async unlockWithRefreshToken(refreshToken: string): Promise<boolean> {
    if (!this.#keycloak) {
      await this.init();
    }
    if (!this.#keycloak) {
      return false;
    }

    const tokenUrl = `${stripTrailingSlash(this.#config.keycloakUrl)}/realms/${encodeURIComponent(this.#config.keycloakRealm)}/protocol/openid-connect/token`;
    const body = new URLSearchParams({
      grant_type: 'refresh_token',
      client_id: this.#config.keycloakClientId,
      refresh_token: refreshToken,
    });

    let response: Response;
    try {
      response = await fetch(tokenUrl, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: body.toString(),
      });
    } catch (fetchError) {
      console.error('Biometric unlock token refresh failed', fetchError);
      this.#nativeFailure.set(
        'Could not reach the server to restore your session. Check your connection.',
      );

      return false;
    }
    if (!response.ok) {
      console.error('Biometric unlock token refresh rejected', response.status);
      this.#nativeFailure.set(
        `The server no longer accepts the stored fingerprint session (HTTP ${response.status}). Sign in once and turn fingerprint unlock on again.`,
      );

      return false;
    }

    const tokens = (await response.json()) as {
      access_token: string;
      refresh_token?: string;
      id_token?: string;
    };
    this.#nativeFailure.set(null);
    this.#applyNativeTokens(
      tokens.access_token,
      tokens.refresh_token,
      tokens.id_token,
    );

    return true;
  }

  async getToken(): Promise<string | null> {
    if (!this.#keycloak) {
      return null;
    }
    try {
      await this.#keycloak.updateToken(30);
    } catch (error) {
      console.error('Token refresh failed', error);
      if (Capacitor.isNativePlatform()) {
        // Don't throw the user into a browser credentials page behind their back: drop the dead session and show
        // the login page, which says what happened and offers fingerprint unlock again.
        this.#nativeFailure.set(
          'Your session could not be refreshed. Sign in again.',
        );
        this.#keycloak.clearToken();
        this.#authenticated.set(false);
        this.#applyToken();
        await this.#injector.get(Router).navigateByUrl('/login');

        return null;
      }
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
      // Native has no cookie session shared with the system browser the login flow opens (the
      // WebView's cookie jar is isolated from it), so a silent-SSO iframe check can only ever
      // fail there — skip it rather than pay a pointless round trip on every app start.
      const initialization = Capacitor.isNativePlatform()
        ? keycloak.init({ checkLoginIframe: false })
        : keycloak.init({
            onLoad: 'check-sso',
            silentCheckSsoRedirectUri: `${window.location.origin}/silent-check-sso.html`,
            silentCheckSsoFallback: false,
            pkceMethod: 'S256',
            checkLoginIframe: false,
          });
      const authenticated = await this.#settleWithin(
        initialization,
        KEYCLOAK_INIT_TIMEOUT_MS,
      );
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

  /** The result of `work`, or `false` (signed out) when it has not settled after `milliseconds`. */
  async #settleWithin(
    work: Promise<boolean>,
    milliseconds: number,
  ): Promise<boolean> {
    let timer: ReturnType<typeof setTimeout> | undefined;
    const timeout = new Promise<boolean>((resolve) => {
      timer = setTimeout(() => {
        console.error(
          `Keycloak did not answer within ${milliseconds / 1000} s; continuing signed out`,
        );
        resolve(false);
      }, milliseconds);
    });
    try {
      return await Promise.race([work, timeout]);
    } finally {
      clearTimeout(timer);
    }
  }

  async #loginNative(action?: AccountAction): Promise<void> {
    if (!this.#keycloak || !this.#config.mobileCallbackUri) {
      console.error('Native login requires AUTH_CONFIG.mobileCallbackUri');

      return;
    }

    const codeVerifier = randomToken();
    const codeChallenge = await codeChallengeFromVerifier(codeVerifier);
    const state = randomToken();
    const nonce = randomToken();
    const redirectUri = this.#config.mobileCallbackUri;
    this.#pendingNativeLogin = { state, nonce, codeVerifier, redirectUri };

    const authorizeUrl = new URL(
      `${stripTrailingSlash(this.#config.keycloakUrl)}/realms/${encodeURIComponent(this.#config.keycloakRealm)}/protocol/openid-connect/auth`,
    );
    authorizeUrl.searchParams.set('client_id', this.#config.keycloakClientId);
    authorizeUrl.searchParams.set('redirect_uri', redirectUri);
    authorizeUrl.searchParams.set('response_type', 'code');
    authorizeUrl.searchParams.set('scope', 'openid offline_access');
    authorizeUrl.searchParams.set('state', state);
    authorizeUrl.searchParams.set('nonce', nonce);
    authorizeUrl.searchParams.set('code_challenge', codeChallenge);
    authorizeUrl.searchParams.set('code_challenge_method', 'S256');
    if (action) {
      authorizeUrl.searchParams.set('kc_action', action);
    }

    await Browser.open({ url: authorizeUrl.toString() });
  }

  #logoutNative(): void {
    if (!this.#keycloak) {
      return;
    }
    // Fire-and-forget: there is no App Link callback for logout (only login needs one, D2), so
    // this clears local state immediately rather than waiting on a round trip through the system
    // browser the user may not even see complete.
    void Browser.open({
      url: this.#keycloak.createLogoutUrl({
        redirectUri: this.#config.mobileCallbackUri,
      }),
    });
    this.#keycloak.clearToken();
    this.#authenticated.set(false);
    this.#applyToken();
  }

  #applyNativeTokens(
    accessToken: string,
    refreshToken: string | undefined,
    idToken: string | undefined,
    expectedNonce?: string,
  ): void {
    if (!this.#keycloak) {
      return;
    }
    if (
      expectedNonce &&
      idToken &&
      decodeJwtPayload(idToken)['nonce'] !== expectedNonce
    ) {
      console.error('Native login rejected: ID token nonce mismatch');

      return;
    }

    const tokenParsed = decodeJwtPayload(accessToken) as KeycloakTokenParsed;
    // keycloak-js treats a token as expired whenever timeSkew is unset, which forced a refresh on every request.
    this.#keycloak.timeSkew =
      Math.floor(Date.now() / 1000) -
      (tokenParsed.iat ?? Math.floor(Date.now() / 1000));
    this.#keycloak.token = accessToken;
    this.#keycloak.tokenParsed = tokenParsed;
    this.#keycloak.authenticated = true;
    this.#keycloak.subject = tokenParsed.sub;
    this.#keycloak.realmAccess = tokenParsed.realm_access;
    this.#keycloak.resourceAccess = tokenParsed.resource_access;
    if (refreshToken) {
      this.#keycloak.refreshToken = refreshToken;
      this.#keycloak.refreshTokenParsed = decodeJwtPayload(
        refreshToken,
      ) as KeycloakTokenParsed;
    }
    if (idToken) {
      this.#keycloak.idToken = idToken;
      this.#keycloak.idTokenParsed = decodeJwtPayload(
        idToken,
      ) as KeycloakTokenParsed;
    }

    this.#authenticated.set(true);
    this.#applyToken();
  }
}
