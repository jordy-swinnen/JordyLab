import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { signal } from '@angular/core';
import { AuthService } from '@jordylab-fe/shared/auth';
import { NoAppsComponent } from './no-apps.component';

describe('NoAppsComponent', () => {
  const logout = vi.fn(() => Promise.resolve());
  const username = signal<string | null>('friend@example.org');
  const createComponent = createComponentFactory({
    component: NoAppsComponent,
    providers: [
      {
        provide: AuthService,
        useValue: { username, logout },
      },
    ],
  });

  let spectator: Spectator<NoAppsComponent>;

  beforeEach(() => {
    logout.mockClear();
    spectator = createComponent();
  });

  it('explains that no app has been shared yet', () => {
    expect(spectator.element.textContent).toContain('Nothing shared with you');
    expect(spectator.element.textContent).toContain('friend@example.org');
  });

  it('offers only signing out', () => {
    const buttons = spectator.queryAll('button');

    expect(buttons).toHaveLength(1);
    expect(buttons[0].textContent).toContain('Sign out');
  });

  it('signs out through the auth service', async () => {
    spectator.click('button');
    await Promise.resolve();

    expect(logout).toHaveBeenCalledOnce();
  });
});
