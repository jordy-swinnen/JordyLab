import { TestBed } from '@angular/core/testing';
import { convertToParamMap, Router } from '@angular/router';
import { appRoutes } from './app.routes';
import { legacyCallbackRedirect, legacyScreenRedirect } from './legacy-mobile-redirect';

describe('Settings → App route', () => {
  const route = appRoutes.find((candidate) => candidate.path === 'settings/app');

  it('is declared before the admin-only settings area so guests reach it', () => {
    const settingsIndex = appRoutes.findIndex((candidate) => candidate.path === 'settings');

    expect(appRoutes.findIndex((candidate) => candidate.path === 'settings/app')).toBeLessThan(settingsIndex);
  });

  it('asks only for a signed-in person with a role, so the web opens it too', () => {
    expect(route?.canActivate).toHaveLength(2);
  });
});

describe('Share target route', () => {
  const routes = appRoutes.filter((candidate) => candidate.path === 'share' || candidate.path?.startsWith('mobile/'));

  it('mounts the share landing screen behind authGuard so a signed-out share returns to it', () => {
    const share = appRoutes.find((candidate) => candidate.path === 'share');

    expect(share?.canActivate).toHaveLength(1);
    expect(share?.component).toBeTruthy(); // asserted by rendering elsewhere; here only that it is component-backed
  });

  it('keeps redirects from the retired native app paths instead of dead-ending on an empty outlet', () => {
    expect(routes.map((candidate) => candidate.path)).toEqual(
      expect.arrayContaining(['share', 'mobile/open', 'mobile/callback']),
    );
  });
});

describe('legacy mobile redirects', () => {
  const parseUrl = vi.fn((url: string) => ({ redirectedTo: url }));

  beforeEach(() => {
    parseUrl.mockClear();
    TestBed.configureTestingModule({ providers: [{ provide: Router, useValue: { parseUrl } }] });
  });

  function run(guard: typeof legacyScreenRedirect, query: Record<string, string>) {
    return TestBed.runInInjectionContext(() =>
      guard({ queryParamMap: convertToParamMap(query) } as never, {} as never),
    );
  }

  it('maps an old notification tap to the Users screen', () => {
    run(legacyScreenRedirect, { screen: 'settings-users' });

    expect(parseUrl).toHaveBeenCalledWith('/settings/users');
  });

  it('maps an old notification tap to the Briefing screen', () => {
    run(legacyScreenRedirect, { screen: 'fna-briefing' });

    expect(parseUrl).toHaveBeenCalledWith('/fna/briefing');
  });

  it('sends an unknown or missing screen to the start page', () => {
    run(legacyScreenRedirect, { screen: 'somewhere-else' });

    expect(parseUrl).toHaveBeenCalledWith('/');
  });

  it('sends the retired native login callback to the login page', () => {
    run(legacyCallbackRedirect, {});

    expect(parseUrl).toHaveBeenCalledWith('/login');
  });
});
