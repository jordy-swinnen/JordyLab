import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { aGuestChatLimitMock } from '@jordylab-fe/settings/api';
import { GuestChatLimitCardComponent } from './guest-chat-limit-card.component';

describe('GuestChatLimitCardComponent', () => {
  const createComponent = createComponentFactory({
    component: GuestChatLimitCardComponent,
    detectChanges: false,
  });

  let spectator: Spectator<GuestChatLimitCardComponent>;

  const render = (limit = aGuestChatLimitMock(), saving = false): Spectator<GuestChatLimitCardComponent> => {
    const created = createComponent({ props: { limit, loading: false, saving, error: null } });
    created.detectChanges();

    return created;
  };

  it('shows the saved default and where it comes from', () => {
    spectator = render(aGuestChatLimitMock({ dailyLimit: 25, source: 'DEFAULT_SAVED' }));

    expect(spectator.query('[data-testid="guest-limit-input"]')).toHaveValue('25');
    expect(spectator.query('[data-testid="guest-limit-source"]')).toHaveText('Saved default');
  });

  it('says when the environment value is still in force', () => {
    spectator = render();

    expect(spectator.query('[data-testid="guest-limit-source"]')).toHaveText('Environment default');
  });

  it('emits the entered limit', () => {
    const save = vi.fn();
    spectator = render();
    spectator.component.saveDefault.subscribe(save);
    const input = spectator.query<HTMLInputElement>('[data-testid="guest-limit-input"]');
    if (!input) {
      throw new Error('The guest limit input is missing.');
    }
    input.value = '7';
    input.dispatchEvent(new Event('input'));

    spectator.click('[data-testid="save-guest-limit"]');

    expect(save).toHaveBeenCalledWith(7);
  });

  it('disables saving while a save is running', () => {
    spectator = render(aGuestChatLimitMock(), true);

    expect(spectator.query<HTMLButtonElement>('[data-testid="save-guest-limit"]')?.disabled).toBe(true);
  });
});
