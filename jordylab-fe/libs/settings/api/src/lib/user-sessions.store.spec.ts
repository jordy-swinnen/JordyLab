import { createServiceFactory, SpectatorService } from '@ngneat/spectator/vitest';
import { of } from 'rxjs';
import { aUserSessionMock } from './mocks/user-session.model.mock';
import { SettingsUsersApiService } from './settings-users-api.service';
import { UserSessionsStore } from './user-sessions.store';

describe('UserSessionsStore', () => {
  let spectator: SpectatorService<UserSessionsStore>;
  const getSessions = vi.fn<SettingsUsersApiService['getSessions']>();
  const endSession = vi.fn<SettingsUsersApiService['endSession']>();
  const endAllSessions = vi.fn<SettingsUsersApiService['endAllSessions']>();

  const createService = createServiceFactory({
    service: UserSessionsStore,
    providers: [
      {
        provide: SettingsUsersApiService,
        useValue: { getSessions, endSession, endAllSessions },
      },
    ],
  });

  beforeEach(() => {
    getSessions.mockReset();
    getSessions.mockReturnValue(of([aUserSessionMock()]));
    endSession.mockReset();
    endSession.mockReturnValue(of(void 0));
    endAllSessions.mockReset();
    endAllSessions.mockReturnValue(of(void 0));
  });

  it('loads the sessions of the opened person only', () => {
    spectator = createService();

    spectator.service.loadFor('u1');

    expect(getSessions).toHaveBeenCalledWith('u1');
    expect(spectator.service.isOpen()).toBe(true);
    expect(spectator.service.sessions()).toHaveLength(1);
  });

  it('ends one session and reloads the list', () => {
    spectator = createService();
    spectator.service.loadFor('u1');
    getSessions.mockClear();

    spectator.service.endOne('session-1', 'WEB');

    expect(endSession).toHaveBeenCalledWith('u1', 'session-1', 'WEB');
    expect(getSessions).toHaveBeenCalledWith('u1');
  });

  it('ends every session and reloads the list', () => {
    spectator = createService();
    spectator.service.loadFor('u1');
    getSessions.mockClear();

    spectator.service.endAll();

    expect(endAllSessions).toHaveBeenCalledWith('u1');
    expect(getSessions).toHaveBeenCalledWith('u1');
  });

  it('closes and forgets the sessions', () => {
    spectator = createService();
    spectator.service.loadFor('u1');

    spectator.service.close();

    expect(spectator.service.isOpen()).toBe(false);
    expect(spectator.service.sessions()).toEqual([]);
  });
});
