import { signal } from '@angular/core';
import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { RouterModule } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { AuthService } from '@jordylab-fe/shared/auth';
import { UsersStore } from '@jordylab-fe/settings/api';
import { App } from './app';

describe('App', () => {
  const logout = vi.fn(() => Promise.resolve());
  // Real signals back the mock's reactive state so tests can drive shell visibility.
  const hasAppRole = signal(true);
  const isAdmin = signal(false);
  const pendingCount = signal(0);
  const createComponent = createComponentFactory({
    component: App,
    imports: [RouterModule.forRoot([])],
    providers: [
      provideHttpClient(),
      {
        provide: AuthService,
        useValue: {
          username: () => 'jordy',
          logout,
          hasAppRole: hasAppRole.asReadonly(),
          isAdmin: isAdmin.asReadonly(),
        },
      },
      {
        provide: UsersStore,
        useValue: { pendingCount: pendingCount.asReadonly() },
      },
    ],
  });

  let spectator: Spectator<App>;

  beforeEach(() => {
    hasAppRole.set(true);
    isAdmin.set(false);
    pendingCount.set(0);
    spectator = createComponent();
  });

  it('lists every section grouped by module in the sidebar for an admin', () => {
    isAdmin.set(true);
    spectator.detectChanges();

    const links = spectator
      .queryAll('nav a')
      .map((link) => link.textContent?.trim());

    expect(links).toEqual([
      'Library',
      'Chat',
      'Sources',
      'Articles',
      'Portfolio',
      'Briefing',
      'Users',
    ]);
  });

  it('links each section to its domain route for an admin', () => {
    isAdmin.set(true);
    spectator.detectChanges();

    const hrefs = spectator
      .queryAll('nav a')
      .map((link) => link.getAttribute('href'));

    expect(hrefs).toEqual([
      '/games/grid',
      '/games/chat',
      '/games/sources',
      '/fna/articles',
      '/fna/portfolio',
      '/fna/briefing',
      '/settings/users',
    ]);
  });

  it('shows a guest only the Game Catalog library and chat, with Sources, FNA and Settings hidden', () => {
    const links = spectator
      .queryAll('nav a')
      .map((link) => link.textContent?.trim());

    expect(links).toEqual(['Library', 'Chat']);
  });

  it('renders a router outlet for the domain routes', () => {
    expect(spectator.query('router-outlet')).toBeTruthy();
  });

  it('shows the signed-in user and signs out on click', () => {
    const button = spectator.query(
      'button[aria-label="Sign out"]',
    ) as HTMLElement;

    expect(button).toHaveText('jordy');
    spectator.click(button);
    expect(logout).toHaveBeenCalled();
  });

  it('hides the shell for an authenticated account with no application role', () => {
    hasAppRole.set(false);

    spectator.detectChanges();

    expect(spectator.query('aside')).toBeNull();
    expect(spectator.query('nav')).toBeNull();
    expect(spectator.query('router-outlet')).toBeTruthy();
  });

  describe('Settings nav group', () => {
    it('is hidden for a non-admin user', () => {
      const links = spectator
        .queryAll('nav a')
        .map((link) => link.textContent?.trim());

      expect(links).not.toContain('Users');
    });

    it('is shown to an admin, linked to /settings/users', () => {
      isAdmin.set(true);
      spectator.detectChanges();

      const links = spectator.queryAll('nav a');
      const settingsLink = links.find(
        (link) => link.textContent?.trim() === 'Users',
      );

      expect(settingsLink?.getAttribute('href')).toBe('/settings/users');
    });

    it('shows the pending-count badge only once there are pending sign-ups', () => {
      isAdmin.set(true);
      spectator.detectChanges();

      expect(
        spectator.query('[data-testid="pending-count-badge"]'),
      ).toBeNull();

      pendingCount.set(2);
      spectator.detectChanges();

      expect(
        spectator.query('[data-testid="pending-count-badge"]'),
      ).toHaveText('2');
    });
  });
});
