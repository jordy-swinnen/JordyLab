import { Router } from '@angular/router';
import {
  createServiceFactory,
  SpectatorService,
} from '@ngneat/spectator/vitest';
import { AUTH_CONFIG, AuthConfig } from './auth-config';
import { AuthService } from './auth.service';

// vi.mock factories are hoisted above imports, so the spies they close over must be created
// through vi.hoisted() — a plain top-level const here would still be in its temporal dead zone
// when the (also hoisted) factory below first evaluates.
const {
  keycloakInit,
  keycloakLogin,
  keycloakLogout,
  keycloakUpdateToken,
  keycloakCreateLogoutUrl,
  keycloakClearToken,
  isNativePlatform,
  browserOpen,
  browserClose,
} = vi.hoisted(() => ({
  keycloakInit: vi.fn(),
  keycloakLogin: vi.fn(),
  keycloakLogout: vi.fn(),
  keycloakUpdateToken: vi.fn(),
  keycloakCreateLogoutUrl: vi
    .fn()
    .mockReturnValue('https://keycloak.example/logout'),
  // Real keycloak-js's `clearToken()` deletes `token`/`tokenParsed` and sets `authenticated =
  // false` on the same instance (verified against its source) — replicated here so the logout
  // test below can assert on the resulting signal state, not just that the mock was called.
  keycloakClearToken: vi.fn(function (this: Record<string, unknown>) {
    delete this['token'];
    delete this['tokenParsed'];
    this['authenticated'] = false;
  }),
  isNativePlatform: vi.fn().mockReturnValue(false),
  browserOpen: vi.fn().mockResolvedValue(undefined),
  browserClose: vi.fn().mockResolvedValue(undefined),
}));

vi.mock('keycloak-js', () => ({
  // A regular `function` here, not an arrow function: the mock is invoked with `new` (AuthService
  // does `new Keycloak(...)`), and arrow functions can never be constructors — using one throws
  // "is not a constructor" the moment mockImplementation's function is invoked via `new`.
  default: vi.fn().mockImplementation(function keycloakConstructor() {
    return {
      init: keycloakInit,
      login: keycloakLogin,
      logout: keycloakLogout,
      updateToken: keycloakUpdateToken,
      createLogoutUrl: keycloakCreateLogoutUrl,
      clearToken: keycloakClearToken,
      token: 'the-access-token',
      tokenParsed: {
        preferred_username: 'jordy',
        realm_access: { roles: ['admin', 'offline_access'] },
      },
    };
  }),
}));

vi.mock('@capacitor/core', () => ({
  Capacitor: { isNativePlatform },
}));

vi.mock('@capacitor/browser', () => ({
  Browser: { open: browserOpen, close: browserClose },
}));

const aTestAuthConfig: AuthConfig = {
  keycloakUrl: 'http://localhost:8180',
  keycloakRealm: 'jordylab',
  keycloakClientId: 'jordylab-host',
  mobileCallbackUri: 'https://app.jordylab.test/mobile/callback',
};

function base64UrlEncode(value: string): string {
  return btoa(value).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function makeJwt(payload: Record<string, unknown>): string {
  const header = base64UrlEncode(JSON.stringify({ alg: 'RS256', typ: 'JWT' }));
  const body = base64UrlEncode(JSON.stringify(payload));

  return `${header}.${body}.signature`;
}

describe('AuthService', () => {
  const navigateByUrl = vi.fn().mockResolvedValue(true);
  let spectator: SpectatorService<AuthService>;

  const createService = createServiceFactory({
    service: AuthService,
    providers: [
      { provide: AUTH_CONFIG, useValue: aTestAuthConfig },
      { provide: Router, useValue: { navigateByUrl } },
    ],
  });

  beforeEach(() => {
    vi.clearAllMocks();
    spectator = createService();
  });

  it('starts unauthenticated before init', () => {
    expect(spectator.service.isAuthenticated()).toBe(false);
    expect(spectator.service.username()).toBeNull();
    expect(spectator.service.token()).toBeNull();
    expect(spectator.service.roles()).toEqual([]);
    expect(spectator.service.hasAppRole()).toBe(false);
  });

  it('sets authenticated state and reads the token/username on a successful init', async () => {
    keycloakInit.mockResolvedValueOnce(true);

    const authenticated = await spectator.service.init();

    expect(authenticated).toBe(true);
    expect(spectator.service.isAuthenticated()).toBe(true);
    expect(spectator.service.token()).toBe('the-access-token');
    expect(spectator.service.username()).toBe('jordy');
  });

  it('reads the realm roles and derives the role flags on a successful init', async () => {
    keycloakInit.mockResolvedValueOnce(true);

    await spectator.service.init();

    expect(spectator.service.roles()).toEqual(['admin', 'offline_access']);
    expect(spectator.service.isAdmin()).toBe(true);
    expect(spectator.service.isGuest()).toBe(false);
    expect(spectator.service.hasAppRole()).toBe(true);
  });

  it('keeps the realm roles in sync when getToken refreshes the token', async () => {
    keycloakInit.mockResolvedValueOnce(true);
    keycloakUpdateToken.mockResolvedValueOnce(true);
    await spectator.service.init();

    await spectator.service.getToken();

    expect(spectator.service.roles()).toEqual(['admin', 'offline_access']);
  });

  it('stays unauthenticated when Keycloak reports no session', async () => {
    keycloakInit.mockResolvedValueOnce(false);

    const authenticated = await spectator.service.init();

    expect(authenticated).toBe(false);
    expect(spectator.service.isAuthenticated()).toBe(false);
  });

  it('returns false and does not throw when Keycloak init rejects', async () => {
    keycloakInit.mockRejectedValueOnce(new Error('network error'));

    const authenticated = await spectator.service.init();

    expect(authenticated).toBe(false);
  });

  it('carries on signed out when Keycloak never answers, instead of leaving the app on a blank page', async () => {
    vi.useFakeTimers();
    const consoleError = vi
      .spyOn(console, 'error')
      .mockImplementation(() => undefined);
    keycloakInit.mockReturnValueOnce(new Promise<boolean>(() => undefined));

    const initialization = spectator.service.init();
    await vi.advanceTimersByTimeAsync(8000);

    await expect(initialization).resolves.toBe(false);
    expect(spectator.service.isAuthenticated()).toBe(false);
    expect(consoleError).toHaveBeenCalled();
    consoleError.mockRestore();
    vi.useRealTimers();
  });

  it('only initializes Keycloak once across repeated init() calls', async () => {
    keycloakInit.mockResolvedValue(true);

    await spectator.service.init();
    await spectator.service.init();

    expect(keycloakInit).toHaveBeenCalledTimes(1);
  });

  it('initializes before delegating to Keycloak login when not yet initialized', async () => {
    keycloakInit.mockResolvedValueOnce(true);

    await spectator.service.login();

    expect(keycloakInit).toHaveBeenCalledTimes(1);
    expect(keycloakLogin).toHaveBeenCalledWith({
      redirectUri: window.location.origin,
    });
  });

  it('sends the user to Keycloak to change their password and back to the current page', async () => {
    keycloakInit.mockResolvedValueOnce(true);
    await spectator.service.init();

    await spectator.service.requestAction('UPDATE_PASSWORD');

    expect(keycloakLogin).toHaveBeenCalledWith({
      redirectUri: window.location.href,
      action: 'UPDATE_PASSWORD',
    });
  });

  it('delegates logout to the Keycloak SDK', async () => {
    keycloakInit.mockResolvedValueOnce(true);
    await spectator.service.init();

    await spectator.service.logout();

    expect(keycloakLogout).toHaveBeenCalledWith({
      redirectUri: window.location.origin,
    });
  });

  it('returns null from getToken before init', async () => {
    const token = await spectator.service.getToken();

    expect(token).toBeNull();
    expect(keycloakUpdateToken).not.toHaveBeenCalled();
  });

  it('refreshes and returns the token from getToken after init', async () => {
    keycloakInit.mockResolvedValueOnce(true);
    keycloakUpdateToken.mockResolvedValueOnce(true);
    await spectator.service.init();

    const token = await spectator.service.getToken();

    expect(keycloakUpdateToken).toHaveBeenCalledWith(30);
    expect(token).toBe('the-access-token');
  });

  it('triggers login and returns null when the token refresh fails', async () => {
    keycloakInit.mockResolvedValueOnce(true);
    keycloakUpdateToken.mockRejectedValueOnce(new Error('token expired'));
    await spectator.service.init();

    const token = await spectator.service.getToken();

    expect(token).toBeNull();
    expect(keycloakLogin).toHaveBeenCalledWith({
      redirectUri: window.location.origin,
    });
  });

  describe('native login (Capacitor, research D2)', () => {
    beforeEach(() => {
      isNativePlatform.mockReturnValue(true);
      vi.stubGlobal('fetch', vi.fn());
    });

    afterEach(() => {
      isNativePlatform.mockReturnValue(false);
      vi.unstubAllGlobals();
    });

    it('opens the system browser with a PKCE-protected authorize URL instead of calling keycloak.login', async () => {
      keycloakInit.mockResolvedValueOnce(false);

      await spectator.service.login();

      expect(keycloakLogin).not.toHaveBeenCalled();
      expect(browserOpen).toHaveBeenCalledTimes(1);

      const openedUrl = new URL(
        (browserOpen.mock.calls[0][0] as { url: string }).url,
      );
      expect(openedUrl.origin + openedUrl.pathname).toBe(
        'http://localhost:8180/realms/jordylab/protocol/openid-connect/auth',
      );
      expect(openedUrl.searchParams.get('client_id')).toBe('jordylab-host');
      expect(openedUrl.searchParams.get('redirect_uri')).toBe(
        aTestAuthConfig.mobileCallbackUri,
      );
      expect(openedUrl.searchParams.get('response_type')).toBe('code');
      expect(openedUrl.searchParams.get('code_challenge_method')).toBe('S256');
      expect(openedUrl.searchParams.get('state')).toBeTruthy();
    });

    it('asks Keycloak for the profile update through the same system-browser flow', async () => {
      keycloakInit.mockResolvedValueOnce(false);

      await spectator.service.requestAction('UPDATE_PROFILE');

      expect(keycloakLogin).not.toHaveBeenCalled();
      const openedUrl = new URL(
        (browserOpen.mock.calls[0][0] as { url: string }).url,
      );
      expect(openedUrl.searchParams.get('kc_action')).toBe('UPDATE_PROFILE');
      expect(openedUrl.searchParams.get('code_challenge_method')).toBe('S256');
    });

    it('does not open a credentials page when the session cannot be refreshed: it shows the login page and says why', async () => {
      keycloakInit.mockResolvedValueOnce(true);
      keycloakUpdateToken.mockRejectedValueOnce(new Error('invalid_grant'));
      await spectator.service.init();

      const token = await spectator.service.getToken();

      expect(token).toBeNull();
      expect(browserOpen).not.toHaveBeenCalled();
      expect(keycloakLogin).not.toHaveBeenCalled();
      expect(navigateByUrl).toHaveBeenCalledWith('/login');
      expect(spectator.service.isAuthenticated()).toBe(false);
      expect(spectator.service.nativeFailure()).toContain(
        'could not be refreshed',
      );
    });

    it('records the server status when the stored fingerprint session is rejected', async () => {
      keycloakInit.mockResolvedValueOnce(false);
      vi.mocked(fetch).mockResolvedValueOnce({
        ok: false,
        status: 400,
      } as Response);

      const unlocked = await spectator.service.unlockWithRefreshToken(
        'stale-refresh-token',
      );

      expect(unlocked).toBe(false);
      expect(spectator.service.nativeFailure()).toContain('HTTP 400');
    });

    it('tells keycloak-js the clock offset after a native sign-in, so it stops refreshing on every request', async () => {
      keycloakInit.mockResolvedValueOnce(false);
      const nowSeconds = Math.floor(Date.now() / 1000);
      const accessToken = makeJwt({
        sub: 'user-1',
        iat: nowSeconds - 5,
        exp: nowSeconds + 1795,
        realm_access: { roles: ['guest'] },
      });
      vi.mocked(fetch).mockResolvedValueOnce({
        ok: true,
        json: () =>
          Promise.resolve({
            access_token: accessToken,
            refresh_token: makeJwt({ sub: 'user-1' }),
          }),
      } as Response);

      await spectator.service.unlockWithRefreshToken('good-refresh-token');

      const keycloakInstance = vi.mocked((await import('keycloak-js')).default)
        .mock.results[0].value as {
        timeSkew: number;
      };
      expect(keycloakInstance.timeSkew).toBeGreaterThanOrEqual(5);
      expect(keycloakInstance.timeSkew).toBeLessThan(10);
      expect(spectator.service.nativeFailure()).toBeNull();
    });

    it('exchanges the authorization code for tokens and updates the signal surface on a valid callback', async () => {
      keycloakInit.mockResolvedValueOnce(false);
      await spectator.service.login();
      const state = new URL(
        (browserOpen.mock.calls[0][0] as { url: string }).url,
      ).searchParams.get('state');
      const accessToken = makeJwt({
        sub: 'user-1',
        preferred_username: 'jordy-native',
        realm_access: { roles: ['guest'] },
      });
      const refreshToken = makeJwt({ sub: 'user-1' });
      vi.mocked(fetch).mockResolvedValueOnce({
        ok: true,
        json: () =>
          Promise.resolve({
            access_token: accessToken,
            refresh_token: refreshToken,
          }),
      } as Response);

      await spectator.service.completeNativeLogin(
        `https://app.jordylab.test/mobile/callback?code=abc123&state=${state}`,
      );

      expect(browserClose).toHaveBeenCalledTimes(1);
      expect(fetch).toHaveBeenCalledWith(
        'http://localhost:8180/realms/jordylab/protocol/openid-connect/token',
        expect.objectContaining({ method: 'POST' }),
      );
      expect(spectator.service.isAuthenticated()).toBe(true);
      expect(spectator.service.username()).toBe('jordy-native');
      expect(spectator.service.roles()).toEqual(['guest']);
    });

    it('rejects a callback whose state does not match the pending login, without calling the token endpoint', async () => {
      keycloakInit.mockResolvedValueOnce(false);
      await spectator.service.login();

      await spectator.service.completeNativeLogin(
        'https://app.jordylab.test/mobile/callback?code=abc123&state=not-the-real-state',
      );

      expect(fetch).not.toHaveBeenCalled();
      expect(spectator.service.isAuthenticated()).toBe(false);
    });

    it('ignores a callback when no login is pending (e.g. a duplicate App Link delivery)', async () => {
      await spectator.service.completeNativeLogin(
        'https://app.jordylab.test/mobile/callback?code=abc123&state=anything',
      );

      expect(fetch).not.toHaveBeenCalled();
      expect(browserClose).not.toHaveBeenCalled();
    });

    it('clears local state immediately on logout, without waiting for the system browser round trip', async () => {
      keycloakInit.mockResolvedValueOnce(true);
      await spectator.service.init();

      await spectator.service.logout();

      expect(keycloakClearToken).toHaveBeenCalledTimes(1);
      expect(browserOpen).toHaveBeenCalledWith({
        url: 'https://keycloak.example/logout',
      });
      expect(spectator.service.isAuthenticated()).toBe(false);
    });
  });
});
