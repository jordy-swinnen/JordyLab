import {
  createServiceFactory,
  SpectatorService,
} from '@ngneat/spectator/vitest';
import { AUTH_CONFIG, AuthConfig } from './auth-config';
import { AuthService } from './auth.service';

// vi.mock factories are hoisted above imports, so the spies they close over must be created
// through vi.hoisted() — a plain top-level const here would still be in its temporal dead zone
// when the (also hoisted) factory below first evaluates.
const { keycloakInit, keycloakLogin, keycloakLogout, keycloakUpdateToken } =
  vi.hoisted(() => ({
    keycloakInit: vi.fn(),
    keycloakLogin: vi.fn(),
    keycloakLogout: vi.fn(),
    keycloakUpdateToken: vi.fn(),
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
      token: 'the-access-token',
      tokenParsed: {
        preferred_username: 'jordy',
        realm_access: { roles: ['admin', 'offline_access'] },
      },
    };
  }),
}));

const aTestAuthConfig: AuthConfig = {
  keycloakUrl: 'http://localhost:8180',
  keycloakRealm: 'jordylab',
  keycloakClientId: 'jordylab-host',
};

describe('AuthService', () => {
  let spectator: SpectatorService<AuthService>;

  const createService = createServiceFactory({
    service: AuthService,
    providers: [{ provide: AUTH_CONFIG, useValue: aTestAuthConfig }],
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
});
