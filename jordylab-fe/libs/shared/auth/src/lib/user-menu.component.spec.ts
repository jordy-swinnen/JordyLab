import { signal } from '@angular/core';
import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { AuthService } from './auth.service';
import { UserMenuComponent } from './user-menu.component';

describe('UserMenuComponent', () => {
  const username = signal<string | null>('jordy');
  const requestAction = vi.fn<AuthService['requestAction']>().mockResolvedValue(undefined);

  let spectator: Spectator<UserMenuComponent>;
  const createComponent = createComponentFactory({
    component: UserMenuComponent,
    providers: [{ provide: AuthService, useValue: { username: username.asReadonly(), requestAction } }],
  });

  beforeEach(() => {
    requestAction.mockClear();
    username.set('jordy');
    spectator = createComponent();
  });

  function open(): void {
    spectator.click('[data-testid="user-menu-trigger"]');
  }

  function item(testId: string): HTMLElement {
    const element = document.querySelector<HTMLElement>(`[data-testid="${testId}"]`);
    if (!element) {
      throw new Error(`Menu item ${testId} is not rendered`);
    }

    return element;
  }

  it('shows the user initial and name on the trigger', () => {
    expect(spectator.query('[data-testid="user-menu-trigger"]')).toHaveText('J');
    expect(spectator.query('[data-testid="user-menu-trigger"]')).toHaveText('jordy');
  });

  it('shows only the initial in the compact variant, with the name for screen readers only', () => {
    spectator.setInput('compact', true);

    const trigger = spectator.query('[data-testid="user-menu-trigger"]');
    expect(trigger?.querySelector('.truncate')).toBeNull();
    expect(trigger?.querySelector('.sr-only')).toHaveText('Account menu for jordy');
  });

  it('names the trigger from what it shows, never from an aria-label that differs (WCAG label in name)', () => {
    expect(spectator.query('[data-testid="user-menu-trigger"]')).not.toHaveAttribute('aria-label');
  });

  it('starts the Keycloak password change', () => {
    open();
    item('user-menu-password').click();

    expect(requestAction).toHaveBeenCalledWith('UPDATE_PASSWORD');
  });

  it('starts the Keycloak profile edit', () => {
    open();
    item('user-menu-profile').click();

    expect(requestAction).toHaveBeenCalledWith('UPDATE_PROFILE');
  });

  it('starts the Keycloak email change', () => {
    open();
    item('user-menu-email').click();

    expect(requestAction).toHaveBeenCalledWith('UPDATE_EMAIL');
  });

  it('asks the shell to sign out', () => {
    let signedOut = false;
    spectator.output('signOut').subscribe(() => (signedOut = true));

    open();
    item('user-menu-sign-out').click();

    expect(signedOut).toBe(true);
    expect(requestAction).not.toHaveBeenCalled();
  });
});
