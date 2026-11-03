import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { anAppUserMock } from '@jordylab-fe/settings/api';
import { PersonAccessComponent } from './person-access.component';

describe('PersonAccessComponent', () => {
  // The user input is required, so it is set before the first change detection.
  const createComponent = createComponentFactory({
    component: PersonAccessComponent,
    detectChanges: false,
  });

  let spectator: Spectator<PersonAccessComponent>;

  beforeEach(() => {
    spectator = createComponent({
      props: { user: anAppUserMock(), busy: false },
    });
    spectator.detectChanges();
  });

  it('shows FNA as not grantable with its reason and a disabled control', () => {
    const fna = spectator.query('[data-testid="disabled-app-fna"]');

    expect(fna?.textContent).toContain('Not grantable');
    expect(fna?.hasAttribute('disabled')).toBe(true);
    expect(spectator.element.textContent).toContain("Holds the admin's private data");
  });

  it('shows Settings as never grantable', () => {
    expect(spectator.query('[data-testid="disabled-app-settings"]')).toBeTruthy();
  });

  it('offers a grant for the Game Catalog when the person does not have it', () => {
    const toggle = spectator.query<HTMLButtonElement>('[data-testid="toggle-app-gamecatalog"]');

    expect(toggle?.textContent).toContain('Grant');
    expect(toggle?.disabled).toBe(false);
  });

  it('emits a grant for a person without the app', () => {
    const grant = vi.fn();
    spectator.component.grant.subscribe(grant);

    spectator.click('[data-testid="toggle-app-gamecatalog"]');

    expect(grant).toHaveBeenCalledWith('gamecatalog');
  });

  it('emits a removal for a person who has the app', () => {
    const remove = vi.fn();
    spectator.component.remove.subscribe(remove);
    spectator.setInput(
      'user',
      anAppUserMock({
        apps: [
          { key: 'gamecatalog', name: 'Game Catalog', granted: true, grantable: true, reason: null },
        ],
      }),
    );

    spectator.click('[data-testid="toggle-app-gamecatalog"]');

    expect(remove).toHaveBeenCalledWith('gamecatalog');
  });

  it('shows the admin as having every app instead of offering a change', () => {
    spectator.setInput('user', anAppUserMock({ isAdmin: true }));
    spectator.detectChanges();

    expect(spectator.query('[data-testid="toggle-app-gamecatalog"]')).toBeNull();
    expect(spectator.query('[data-testid="admin-app-gamecatalog"]')).toHaveText('Admin access');
  });

  it('disables every control while an action is running', () => {
    spectator.setInput('busy', true);

    expect(
      spectator.query<HTMLButtonElement>('[data-testid="toggle-app-gamecatalog"]')?.disabled,
    ).toBe(true);
  });
});
