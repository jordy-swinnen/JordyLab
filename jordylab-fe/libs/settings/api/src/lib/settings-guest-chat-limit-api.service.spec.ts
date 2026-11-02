import { createHttpFactory, HttpMethod, SpectatorHttp } from '@ngneat/spectator/vitest';
import { aGuestChatLimitMock } from './mocks/guest-chat-limit.model.mock';
import { SettingsGuestChatLimitApiService } from './settings-guest-chat-limit-api.service';

describe('SettingsGuestChatLimitApiService', () => {
  let spectator: SpectatorHttp<SettingsGuestChatLimitApiService>;
  const createService = createHttpFactory(SettingsGuestChatLimitApiService);

  beforeEach(() => {
    spectator = createService();
  });

  it('loads the saved default', () => {
    spectator.service.getDefault().subscribe((limit) => {
      expect(limit).toEqual(aGuestChatLimitMock());
    });

    spectator
      .expectOne('/api/settings/guest-chat-limit', HttpMethod.GET)
      .flush(aGuestChatLimitMock());
  });

  it('saves the default', () => {
    spectator.service.saveDefault(5).subscribe();

    const request = spectator.expectOne('/api/settings/guest-chat-limit', HttpMethod.PUT);
    expect(request.request.body).toEqual({ dailyLimit: 5 });
    request.flush(aGuestChatLimitMock({ dailyLimit: 5, source: 'DEFAULT_SAVED' }));
  });
});
