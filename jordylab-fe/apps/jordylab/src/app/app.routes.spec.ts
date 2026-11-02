import { appRoutes } from './app.routes';

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
