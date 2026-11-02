import { createHttpFactory, HttpMethod, SpectatorHttp } from '@ngneat/spectator/vitest';
import { SettingsUsersApiService } from './settings-users-api.service';
import { anAppUserMock } from './mocks/app-user.model.mock';

describe('SettingsUsersApiService', () => {
  let spectator: SpectatorHttp<SettingsUsersApiService>;
  const createService = createHttpFactory(SettingsUsersApiService);

  beforeEach(() => {
    spectator = createService();
  });

  it('requests users without a status filter', () => {
    spectator.service.getUsers().subscribe((users) => {
      expect(users).toEqual([anAppUserMock()]);
    });

    spectator.expectOne('/api/settings/users', HttpMethod.GET).flush({ users: [anAppUserMock()] });
  });

  it('passes the status query param', () => {
    spectator.service.getUsers('PENDING').subscribe();

    spectator.expectOne('/api/settings/users?status=PENDING', HttpMethod.GET).flush({ users: [] });
  });

  it('unwraps the pending count', () => {
    spectator.service.getPendingCount().subscribe((count) => {
      expect(count).toBe(3);
    });

    spectator.expectOne('/api/settings/users/pending-count', HttpMethod.GET).flush({ count: 3 });
  });

  it('posts to approve', () => {
    spectator.service.approve('user-1').subscribe();

    spectator.expectOne('/api/settings/users/user-1/approve', HttpMethod.POST).flush(null);
  });

  it('posts to reject', () => {
    spectator.service.reject('user-1').subscribe();

    spectator.expectOne('/api/settings/users/user-1/reject', HttpMethod.POST).flush(null);
  });

  it('posts to revoke', () => {
    spectator.service.revoke('user-1').subscribe();

    spectator.expectOne('/api/settings/users/user-1/revoke', HttpMethod.POST).flush(null);
  });

  it('puts a grant for one app', () => {
    spectator.service.grantApp('user-1', 'gamecatalog').subscribe();

    spectator.expectOne('/api/settings/users/user-1/apps/gamecatalog', HttpMethod.PUT).flush(null);
  });

  it('deletes the access to one app', () => {
    spectator.service.removeApp('user-1', 'gamecatalog').subscribe();

    spectator.expectOne('/api/settings/users/user-1/apps/gamecatalog', HttpMethod.DELETE).flush(null);
  });

  it('deletes a person', () => {
    spectator.service.deletePerson('user-1').subscribe();

    spectator.expectOne('/api/settings/users/user-1', HttpMethod.DELETE).flush(null);
  });

  it('unwraps the sessions', () => {
    spectator.service.getSessions('user-1').subscribe((sessions) => {
      expect(sessions).toHaveLength(1);
    });

    spectator
      .expectOne('/api/settings/users/user-1/sessions', HttpMethod.GET)
      .flush({ sessions: [{ id: 's1', kind: 'WEB', offline: false }] });
  });

  it('ends one session and passes the kind', () => {
    spectator.service.endSession('user-1', 's1', 'PHONE').subscribe();

    spectator
      .expectOne('/api/settings/users/user-1/sessions/s1?kind=PHONE', HttpMethod.DELETE)
      .flush(null);
  });

  it('ends every session', () => {
    spectator.service.endAllSessions('user-1').subscribe();

    spectator
      .expectOne('/api/settings/users/user-1/sessions/end-all', HttpMethod.POST)
      .flush(null);
  });

  it('unwraps the temporary password from reset-password', () => {
    spectator.service.resetPassword('user-1').subscribe((password) => {
      expect(password).toBe('temp-pass-123');
    });

    spectator
      .expectOne('/api/settings/users/user-1/reset-password', HttpMethod.POST)
      .flush({ temporaryPassword: 'temp-pass-123' });
  });
});
