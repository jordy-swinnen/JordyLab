import { HttpErrorResponse } from '@angular/common/http';
import { createServiceFactory, SpectatorService } from '@ngneat/spectator/vitest';
import { of, throwError } from 'rxjs';
import { aGuestChatLimitMock } from './mocks/guest-chat-limit.model.mock';
import { GuestChatLimitStore } from './guest-chat-limit.store';
import { SettingsGuestChatLimitApiService } from './settings-guest-chat-limit-api.service';

describe('GuestChatLimitStore', () => {
  let spectator: SpectatorService<GuestChatLimitStore>;
  const getDefault = vi.fn<SettingsGuestChatLimitApiService['getDefault']>();
  const saveDefault = vi.fn<SettingsGuestChatLimitApiService['saveDefault']>();

  const createService = createServiceFactory({
    service: GuestChatLimitStore,
    providers: [
      {
        provide: SettingsGuestChatLimitApiService,
        useValue: { getDefault, saveDefault },
      },
    ],
  });

  beforeEach(() => {
    getDefault.mockReset();
    getDefault.mockReturnValue(of(aGuestChatLimitMock()));
    saveDefault.mockReset();
    saveDefault.mockReturnValue(of(aGuestChatLimitMock({ dailyLimit: 5, source: 'DEFAULT_SAVED' })));
  });

  it('loads the current default on construction', () => {
    spectator = createService();

    expect(spectator.service.limit()).toEqual(aGuestChatLimitMock());
    expect(spectator.service.loading()).toBe(false);
  });

  it('saves a new default and shows it as saved', () => {
    spectator = createService();

    spectator.service.saveDefault(5);

    expect(saveDefault).toHaveBeenCalledWith(5);
    expect(spectator.service.limit()?.dailyLimit).toBe(5);
    expect(spectator.service.limit()?.source).toBe('DEFAULT_SAVED');
    expect(spectator.service.saving()).toBe(false);
  });

  it('keeps a saved zero instead of treating it as empty', () => {
    saveDefault.mockReturnValue(of(aGuestChatLimitMock({ dailyLimit: 0, source: 'DEFAULT_SAVED' })));
    spectator = createService();

    spectator.service.saveDefault(0);

    expect(spectator.service.limit()?.dailyLimit).toBe(0);
  });

  it('explains an invalid limit', () => {
    saveDefault.mockReturnValue(
      throwError(
        () => new HttpErrorResponse({ status: 400, error: { reason: 'INVALID_LIMIT' } }),
      ),
    );
    spectator = createService();

    spectator.service.saveDefault(-1);

    expect(spectator.service.error()).toBe('The limit must be a whole number of 0 or more.');
  });
});
