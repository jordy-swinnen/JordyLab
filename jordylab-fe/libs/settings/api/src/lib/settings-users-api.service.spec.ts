import { createHttpFactory, HttpMethod, SpectatorHttp } from '@ngneat/spectator/vitest';
import { SettingsUsersApiService } from './settings-users-api.service';
import { anAppUserMock } from './mocks/app-user.model.mock';

describe('SettingsUsersApiService', () => {
  let spectator: SpectatorHttp<SettingsUsersApiService>;
  const createService = createHttpFactory(SettingsUsersApiService);

  beforeEach(() => {
    spectator = createService();
  });

  it('requests users from the identity API without a status filter', () => {
    spectator.service.getUsers().subscribe((users) => {
      expect(users).toEqual([anAppUserMock()]);
    });

    spectator.expectOne('/api/identity/users', HttpMethod.GET).flush({ users: [anAppUserMock()] });
  });

  it('passes the status query param', () => {
    spectator.service.getUsers('PENDING').subscribe();

    spectator.expectOne('/api/identity/users?status=PENDING', HttpMethod.GET).flush({ users: [] });
  });

  it('unwraps the pending count', () => {
    spectator.service.getPendingCount().subscribe((count) => {
      expect(count).toBe(3);
    });

    spectator.expectOne('/api/identity/users/pending-count', HttpMethod.GET).flush({ pendingCount: 3 });
  });

  it('posts to approve with the default role', () => {
    spectator.service.approve('user-1').subscribe();

    const request = spectator.expectOne('/api/identity/users/user-1/approve', HttpMethod.POST);
    expect(request.request.body).toEqual({ role: null });
    request.flush(null);
  });

  it('posts to approve with an explicit role', () => {
    spectator.service.approve('user-1', 'admin').subscribe();

    const request = spectator.expectOne('/api/identity/users/user-1/approve', HttpMethod.POST);
    expect(request.request.body).toEqual({ role: 'admin' });
    request.flush(null);
  });

  it('posts to reject', () => {
    spectator.service.reject('user-1').subscribe();

    spectator.expectOne('/api/identity/users/user-1/reject', HttpMethod.POST).flush(null);
  });

  it('posts to revoke', () => {
    spectator.service.revoke('user-1').subscribe();

    spectator.expectOne('/api/identity/users/user-1/revoke', HttpMethod.POST).flush(null);
  });
});
