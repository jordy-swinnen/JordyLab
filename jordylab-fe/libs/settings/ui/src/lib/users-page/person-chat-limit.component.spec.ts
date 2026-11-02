import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { anAppUserMock } from '@jordylab-fe/settings/api';
import { PersonChatLimitComponent } from './person-chat-limit.component';

describe('PersonChatLimitComponent', () => {
  // The user input is required, so it is set before the first change detection.
  const createComponent = createComponentFactory({
    component: PersonChatLimitComponent,
    detectChanges: false,
  });

  let spectator: Spectator<PersonChatLimitComponent>;

  const render = (limit = { limit: 35, source: 'PERSONAL' as const }, busy = false): Spectator<PersonChatLimitComponent> => {
    const created = createComponent({
      props: { user: anAppUserMock({ id: 'u1', chatLimit: limit }), busy },
    });
    created.detectChanges();

    return created;
  };

  it('shows the limit in force and its source', () => {
    spectator = render();

    expect(spectator.query('[data-testid="chat-limit-input-u1"]')).toHaveValue('35');
    expect(spectator.element).toHaveText('Personal limit');
  });

  it('saves a personal limit', () => {
    const setPersonal = vi.fn();
    spectator = render();
    spectator.component.setPersonal.subscribe(setPersonal);
    const input = spectator.query<HTMLInputElement>('[data-testid="chat-limit-input-u1"]');
    if (!input) {
      throw new Error('The chat limit input is missing.');
    }
    input.value = '12';
    input.dispatchEvent(new Event('input'));

    spectator.click('[data-testid="save-chat-limit-u1"]');

    expect(setPersonal).toHaveBeenCalledWith(12);
  });

  it('goes back to the default', () => {
    const clearPersonal = vi.fn();
    spectator = render();
    spectator.component.clearPersonal.subscribe(clearPersonal);

    spectator.click('[data-testid="clear-chat-limit-u1"]');

    expect(clearPersonal).toHaveBeenCalled();
  });

  it('offers nothing for the admin, who is never limited', () => {
    spectator = render(undefined, false);
    spectator.setInput('user', anAppUserMock({ id: 'admin', isAdmin: true, chatLimit: null }));
    spectator.detectChanges();

    expect(spectator.query('[data-testid="chat-limit-input-admin"]')).toBeNull();
  });

  it('disables every control while an action is running', () => {
    spectator = render(undefined, true);

    expect(spectator.query<HTMLButtonElement>('[data-testid="save-chat-limit-u1"]')?.disabled).toBe(true);
  });
});
