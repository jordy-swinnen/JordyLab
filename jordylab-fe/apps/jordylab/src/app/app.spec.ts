import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { RouterModule } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { AuthService } from '@jordylab-fe/shared/auth';
import { App } from './app';

describe('App', () => {
  const logout = vi.fn(() => Promise.resolve());
  const createComponent = createComponentFactory({
    component: App,
    imports: [RouterModule.forRoot([])],
    providers: [
      provideHttpClient(),
      { provide: AuthService, useValue: { username: () => 'jordy', logout } },
    ],
  });

  let spectator: Spectator<App>;

  beforeEach(() => {
    spectator = createComponent();
  });

  it('lists every section grouped by module in the sidebar', () => {
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
    ]);
  });

  it('links each section to its domain route', () => {
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
    ]);
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
});
