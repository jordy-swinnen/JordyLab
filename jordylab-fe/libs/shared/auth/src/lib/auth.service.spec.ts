import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { createServiceFactory, SpectatorService } from '@ngneat/spectator/vitest';
import { AuthService, CurrentUser } from './auth.service';

const USER: CurrentUser = {
  id: '11111111-1111-4111-8111-111111111111',
  username: 'jordy',
  displayName: 'Jordy Swinnen',
  role: 'guest',
  status: 'APPROVED',
  appGrants: ['gamecatalog'],
};

/** Lets the pending promise continuations run so the next HTTP call is issued. */
function nextTask(): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, 0));
}

describe('AuthService', () => {
  let spectator: SpectatorService<AuthService>;
  let http: HttpTestingController;
  const createService = createServiceFactory({
    service: AuthService,
    providers: [provideHttpClient(), provideHttpClientTesting()],
  });

  beforeEach(() => {
    spectator = createService();
    http = spectator.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  async function flushCsrf(): Promise<void> {
    http.expectOne('/api/identity/auth/csrf').flush(null, { status: 204, statusText: 'No Content' });
    await nextTask();
  }

  it('boots signed out after an unauthenticated /me', async () => {
    const ready = spectator.service.init();
    await flushCsrf();
    http.expectOne('/api/identity/me').flush(null, { status: 401, statusText: 'Unauthorized' });

    await expect(ready).resolves.toBe(false);
    expect(spectator.service.isAuthenticated()).toBe(false);
  });

  it('boots with the profile from /me', async () => {
    const ready = spectator.service.init();
    await flushCsrf();
    http.expectOne('/api/identity/me').flush(USER);

    await expect(ready).resolves.toBe(true);
    expect(spectator.service.username()).toBe('jordy');
    expect(spectator.service.isGuest()).toBe(true);
    expect(spectator.service.hasGameCatalogAccess()).toBe(true);
  });

  it('signs in with a username and password', async () => {
    const login = spectator.service.login('jordy', 'correct horse battery');
    await flushCsrf();
    const request = http.expectOne('/api/identity/auth/login');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ username: 'jordy', password: 'correct horse battery' });
    request.flush(USER);

    await expect(login).resolves.toEqual(USER);
    expect(spectator.service.isAuthenticated()).toBe(true);
  });

  it('clears the session on logout', async () => {
    const login = spectator.service.login('jordy', 'pw');
    await flushCsrf();
    http.expectOne('/api/identity/auth/login').flush(USER);
    await login;

    const logout = spectator.service.logout();
    await nextTask();
    http.expectOne('/api/identity/auth/logout').flush(null, { status: 204, statusText: 'No Content' });
    await logout;

    expect(spectator.service.isAuthenticated()).toBe(false);
  });

  it('updates the display name locally after a successful change', async () => {
    const login = spectator.service.login('jordy', 'pw');
    await flushCsrf();
    http.expectOne('/api/identity/auth/login').flush(USER);
    await login;

    const update = spectator.service.updateDisplayName('Jordy S.');
    await nextTask();
    http.expectOne('/api/identity/me').flush(null, { status: 204, statusText: 'No Content' });
    await update;

    expect(spectator.service.displayName()).toBe('Jordy S.');
  });

  it('signs in with a passkey assertion', async () => {
    const assertion = {
      id: 'cred-1',
      rawId: new Uint8Array([1]).buffer,
      type: 'public-key',
      getClientExtensionResults: () => ({}),
      response: {
        authenticatorData: new Uint8Array([2]).buffer,
        clientDataJSON: new Uint8Array([3]).buffer,
        signature: new Uint8Array([4]).buffer,
        userHandle: null,
      },
    } as unknown as PublicKeyCredential;
    Object.defineProperty(navigator, 'credentials', {
      value: { get: vi.fn().mockResolvedValue(assertion), create: vi.fn() },
      configurable: true,
    });

    const signIn = spectator.service.signInWithPasskey();
    await flushCsrf();
    http.expectOne('/webauthn/authenticate/options').flush({ challenge: 'AAAA' });
    await nextTask();
    await nextTask();
    const login = http.expectOne('/login/webauthn');
    expect(login.request.method).toBe('POST');
    expect((login.request.body as { id: string }).id).toBe('cred-1');
    login.flush({ authenticated: true, redirectUrl: '/' });
    await nextTask();
    http.expectOne('/api/identity/me').flush(USER);

    await expect(signIn).resolves.toEqual(USER);
    expect(spectator.service.isAuthenticated()).toBe(true);
  });

  it('treats a cancelled passkey prompt as a cancellation, not an error', async () => {
    Object.defineProperty(navigator, 'credentials', {
      value: { get: vi.fn().mockResolvedValue(null), create: vi.fn() },
      configurable: true,
    });

    const signIn = spectator.service.signInWithPasskey();
    const rejection = expect(signIn).rejects.toThrow('PASSKEY_CANCELLED');
    await flushCsrf();
    http.expectOne('/webauthn/authenticate/options').flush({ challenge: 'AAAA' });
    await nextTask();
    await nextTask();

    await rejection;
    expect(spectator.service.isAuthenticated()).toBe(false);
  });

  it('lists and removes the account passkeys', async () => {
    const list = spectator.service.passkeys();
    http.expectOne('/api/identity/passkeys').flush({
      passkeys: [{ credentialId: 'cred-1', label: 'MacBook', createdAt: '2026-11-03T12:00:00Z', lastUsedAt: null }],
    });

    await expect(list).resolves.toHaveLength(1);

    const remove = spectator.service.removePasskey('cred-1');
    await flushCsrf();
    const request = http.expectOne('/api/identity/passkeys/cred-1');
    expect(request.request.method).toBe('DELETE');
    request.flush(null, { status: 204, statusText: 'No Content' });
    await remove;
  });

  it('changes the password through the identity endpoint', async () => {
    const change = spectator.service.changePassword('old password 12', 'new password 12');
    await flushCsrf();
    const request = http.expectOne('/api/identity/auth/change-password');
    expect(request.request.body).toEqual({ currentPassword: 'old password 12', newPassword: 'new password 12' });
    request.flush(null, { status: 204, statusText: 'No Content' });

    await expect(change).resolves.toBeUndefined();
  });
});
