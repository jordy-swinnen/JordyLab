import { signal } from '@angular/core';
import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { RouterModule } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { AuthService } from '@jordylab-fe/shared/auth';
import { UsersStore } from '@jordylab-fe/settings/api';
import { InstallPromptStore } from '@jordylab-fe/shared/platform/api';
import { App } from './app';

describe('App', () => {
  const logout = vi.fn(() => Promise.resolve());
  // Real signals back the mock's reactive state so tests can drive shell visibility.
  const hasAppRole = signal(true);
  const isAdmin = signal(false);
  const hasGameCatalogAccess = signal(false);
  const pendingCount = signal(0);
  const captureInstallPrompt = vi.fn();
  const authInit = vi.fn();
  const createComponent = createComponentFactory({
    component: App,
    imports: [RouterModule.forRoot([{ path: 'login', children: [] }])],
    providers: [
      provideHttpClient(),
      {
        provide: AuthService,
        useValue: {
          username: () => 'jordy',
          init: authInit,
          logout,
          hasAppRole: hasAppRole.asReadonly(),
          isAdmin: isAdmin.asReadonly(),
          hasGameCatalogAccess: hasGameCatalogAccess.asReadonly(),
        },
      },
      {
        provide: UsersStore,
        useValue: { pendingCount: pendingCount.asReadonly() },
      },
      {
        provide: InstallPromptStore,
        useValue: {
          captureInstallPrompt,
          promptKind: signal(null).asReadonly(),
          canPromptInstall: signal(false).asReadonly(),
          dismiss: vi.fn(),
          promptInstall: vi.fn(),
        },
      },
    ],
  });

  let spectator: Spectator<App>;

  beforeEach(() => {
    hasAppRole.set(true);
    isAdmin.set(false);
    hasGameCatalogAccess.set(false);
    pendingCount.set(0);
    authInit.mockReset().mockResolvedValue(false);
    spectator = createComponent();
  });

  it('lists every section grouped by module in the sidebar for an admin', () => {
    isAdmin.set(true);
    hasGameCatalogAccess.set(true);
    spectator.detectChanges();

    const links = spectator
      .queryAll('nav[aria-label="Primary"] a')
      .map((link) => link.textContent?.trim());

    expect(links).toEqual([
      'Library',
      'LibBot',
      'Sources',
      'Consoles',
      'Articles',
      'Portfolio',
      'Briefing',
      'Users',
      'AI Models',
      'App',
    ]);
  });

  it('links each section to its domain route for an admin', () => {
    isAdmin.set(true);
    hasGameCatalogAccess.set(true);
    spectator.detectChanges();

    const hrefs = spectator
      .queryAll('nav[aria-label="Primary"] a')
      .map((link) => link.getAttribute('href'));

    expect(hrefs).toEqual([
      '/games/grid',
      '/games/libbot',
      '/games/sources',
      '/games/consoles',
      '/fna/articles',
      '/fna/portfolio',
      '/fna/briefing',
      '/settings/users',
      '/settings/ai-models',
      '/settings/app',
    ]);
  });

  it('shows a guest with the app grant the Game Catalog library and LibBot and App settings, with Sources, Consoles, FNA and the admin settings hidden', () => {
    hasGameCatalogAccess.set(true);
    spectator.detectChanges();

    const links = spectator
      .queryAll('nav[aria-label="Primary"] a')
      .map((link) => link.textContent?.trim());

    expect(links).toEqual(['Library', 'LibBot', 'App']);
  });

  it('hides the Game Catalog from an approved guest without the app grant', () => {
    const links = spectator
      .queryAll('nav[aria-label="Primary"] a')
      .map((link) => link.textContent?.trim());

    expect(links).toEqual(['App']);
  });

  it('renders a router outlet for the domain routes', () => {
    expect(spectator.query('router-outlet')).toBeTruthy();
  });

  it('gives every signed-in user an account menu, admin or guest, that signs out', async () => {
    const triggers = spectator.queryAll('[data-testid="user-menu-trigger"]');
    // One trigger per layout: compact in the mobile header, full in the desktop sidebar (CSS hides the other).
    expect(triggers).toHaveLength(2);
    expect(triggers[1]).toHaveText('jordy');

    spectator.click(triggers[1]);
    const signOut = document.querySelector<HTMLElement>('[data-testid="user-menu-sign-out"]');
    expect(signOut).not.toBeNull();
    signOut?.click();
    await Promise.resolve();

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
        .queryAll('nav[aria-label="Primary"] a')
        .map((link) => link.textContent?.trim());

      expect(links).not.toContain('Users');
    });

    it('is shown to an admin, linked to /settings/users', () => {
      isAdmin.set(true);
      hasGameCatalogAccess.set(true);
      spectator.detectChanges();

      const links = spectator.queryAll('nav[aria-label="Primary"] a');
      const settingsLink = links.find(
        (link) => link.textContent?.trim() === 'Users',
      );

      expect(settingsLink?.getAttribute('href')).toBe('/settings/users');
    });

    it('shows the pending-count badge only once there are pending sign-ups', () => {
      isAdmin.set(true);
      hasGameCatalogAccess.set(true);
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

  describe('install prompt', () => {
    it('captures the browser install event once, from the shell (spec 018 FR-003)', () => {
      expect(captureInstallPrompt).toHaveBeenCalled();
    });
  });

  it('shows the App settings entry on the web to everyone, because the theme choice lives there', () => {
    const settings = spectator.component['visibleGroups']().find((group) => group.label === 'Settings');

    expect(settings?.items.map((item) => item.path)).toEqual(['/settings/app']);
  });

  describe('phone tab bar', () => {
    const tabLabels = () => spectator.queryAll('lib-tab-bar nav > *').map((tab) => tab.textContent?.trim());

    it('shows Games and Settings to a guest with the app grant', () => {
      hasGameCatalogAccess.set(true);
      spectator.detectChanges();

      expect(tabLabels()).toEqual(['Games', 'Settings']);
    });

    it('shows only Settings to an approved guest without the app grant', () => {
      expect(tabLabels()).toEqual(['Settings']);
    });

    it('adds Finance for an administrator', () => {
      isAdmin.set(true);
      hasGameCatalogAccess.set(true);
      spectator.detectChanges();

      expect(tabLabels()).toEqual(['Games', 'Finance', 'Settings']);
    });

    it('lifts the pages of a module out of its tab', () => {
      isAdmin.set(true);
      hasGameCatalogAccess.set(true);
      spectator.detectChanges();

      spectator.click(spectator.queryAll('lib-tab-bar nav > button')[0]);

      expect(spectator.queryAll('[data-testid="tab-bar-options"] a').map((link) => link.textContent?.trim())).toEqual([
        'Library',
        'LibBot',
        'Sources',
        'Consoles',
      ]);
    });

    it('goes straight to the only page of a module', () => {
      hasGameCatalogAccess.set(true);
      spectator.detectChanges();
      const settingsTab = spectator.queryAll('lib-tab-bar nav > *')[1];

      expect(settingsTab.tagName).toBe('A');
      expect(settingsTab.getAttribute('href')).toBe('/settings/app');
    });

    it('is not shown to an account with no application role', () => {
      hasAppRole.set(false);
      spectator.detectChanges();

      expect(spectator.query('lib-tab-bar')).toBeNull();
    });
  });
});
