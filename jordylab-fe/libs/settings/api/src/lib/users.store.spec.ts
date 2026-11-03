import { HttpErrorResponse } from '@angular/common/http';
import { createServiceFactory, SpectatorService } from '@ngneat/spectator/vitest';
import { of, Subject, throwError } from 'rxjs';
import { SettingsUsersApiService } from './settings-users-api.service';
import { AppUser } from './settings.models';
import { UsersStore } from './users.store';
import { anAppUserMock } from './mocks/app-user.model.mock';

describe('UsersStore', () => {
  let spectator: SpectatorService<UsersStore>;
  const getUsers = vi.fn<SettingsUsersApiService['getUsers']>();
  const getPendingCount = vi.fn<SettingsUsersApiService['getPendingCount']>();
  const approve = vi.fn<SettingsUsersApiService['approve']>();
  const reject = vi.fn<SettingsUsersApiService['reject']>();
  const revoke = vi.fn<SettingsUsersApiService['revoke']>();
  const resetPassword = vi.fn<SettingsUsersApiService['resetPassword']>();
  const grantApp = vi.fn<SettingsUsersApiService['grantApp']>();
  const removeApp = vi.fn<SettingsUsersApiService['removeApp']>();
  const deletePerson = vi.fn<SettingsUsersApiService['deletePerson']>();

  const createService = createServiceFactory({
    service: UsersStore,
    providers: [
      {
        provide: SettingsUsersApiService,
        useValue: { getUsers, getPendingCount, approve, reject, revoke, resetPassword, grantApp, removeApp, deletePerson },
      },
    ],
  });

  beforeEach(() => {
    getUsers.mockReset();
    getUsers.mockReturnValue(of([anAppUserMock()]));
    getPendingCount.mockReset();
    getPendingCount.mockReturnValue(of(1));
    approve.mockReset();
    reject.mockReset();
    revoke.mockReset();
    resetPassword.mockReset();
    grantApp.mockReset();
    grantApp.mockReturnValue(of(void 0));
    removeApp.mockReset();
    removeApp.mockReturnValue(of(void 0));
    deletePerson.mockReset();
    deletePerson.mockReturnValue(of(void 0));
  });

  describe('deletion', () => {
    it('deletes a person and reloads the list', () => {
      spectator = createService();
      getUsers.mockClear();

      spectator.service.deletePerson('user-1');

      expect(deletePerson).toHaveBeenCalledWith('user-1');
      expect(getUsers).toHaveBeenCalled();
    });

    it('says nothing changed when the very first deletion step failed', () => {
      spectator = createService();
      deletePerson.mockReturnValue(
        throwError(
          () =>
            new HttpErrorResponse({
              status: 503,
              error: { reason: 'KEYCLOAK_UNAVAILABLE', stage: 'DISABLE' },
            }),
        ),
      );

      spectator.service.deletePerson('user-1');

      expect(spectator.service.error()).toBe(
        'Deletion could not start; nothing changed. Try again shortly.',
      );
    });

    it('reports where a deletion stopped and that it can be retried', () => {
      spectator = createService();
      deletePerson.mockReturnValue(
        throwError(
          () =>
            new HttpErrorResponse({
              status: 503,
              error: { reason: 'KEYCLOAK_UNAVAILABLE', stage: 'DELETE_ACCOUNT' },
            }),
        ),
      );

      spectator.service.deletePerson('user-1');

      expect(spectator.service.error()).toBe(
        'Deletion stopped at DELETE_ACCOUNT. The person is blocked and you can retry.',
      );
    });
  });

  describe('app access', () => {
    it('grants an app and reloads the list', () => {
      spectator = createService();
      getUsers.mockClear();

      spectator.service.grantApp('user-1', 'gamecatalog');

      expect(grantApp).toHaveBeenCalledWith('user-1', 'gamecatalog');
      expect(getUsers).toHaveBeenCalled();
    });

    it('removes an app and reloads the list', () => {
      spectator = createService();
      getUsers.mockClear();

      spectator.service.removeApp('user-1', 'gamecatalog');

      expect(removeApp).toHaveBeenCalledWith('user-1', 'gamecatalog');
      expect(getUsers).toHaveBeenCalled();
    });

    it('explains a refused grant', () => {
      spectator = createService();
      grantApp.mockReturnValue(
        throwError(
          () =>
            new HttpErrorResponse({
              status: 400,
              error: { reason: 'APP_NOT_GRANTABLE' },
            }),
        ),
      );

      spectator.service.grantApp('user-1', 'fna');

      expect(spectator.service.error()).toBe('That app cannot be granted.');
    });

    it('explains a change to the admin own account', () => {
      spectator = createService();
      removeApp.mockReturnValue(
        throwError(
          () =>
            new HttpErrorResponse({
              status: 400,
              error: { reason: 'CANNOT_CHANGE_OWN_ACCOUNT' },
            }),
        ),
      );

      spectator.service.removeApp('admin-1', 'gamecatalog');

      expect(spectator.service.error()).toBe('You cannot change your own account from here.');
    });
  });

  describe('load', () => {
    it('loads users and the pending count on construction', () => {
      spectator = createService();

      expect(spectator.service.users()).toEqual([anAppUserMock()]);
      expect(spectator.service.pendingCount()).toBe(1);
      expect(spectator.service.loading()).toBe(false);
    });

    it('is loading until the users arrive', () => {
      const users = new Subject<AppUser[]>();
      getUsers.mockReturnValue(users.asObservable());
      spectator = createService();

      expect(spectator.service.loading()).toBe(true);

      users.next([]);

      expect(spectator.service.loading()).toBe(false);
    });

    it('sets an error message when loading fails', () => {
      getUsers.mockReturnValue(throwError(() => new Error('network error')));
      spectator = createService();

      expect(spectator.service.error()).toBe('Failed to load users.');
      expect(spectator.service.users()).toEqual([]);
    });
  });

  describe('derived lists', () => {
    it('groups users by their derived status', () => {
      getUsers.mockReturnValue(
        of([
          anAppUserMock({ id: 'p1', status: 'PENDING' }),
          anAppUserMock({ id: 'a1', status: 'APPROVED' }),
          anAppUserMock({ id: 'r1', status: 'REJECTED' }),
        ])
      );
      spectator = createService();

      expect(spectator.service.pendingUsers().map((user) => user.id)).toEqual(['p1']);
      expect(spectator.service.approvedUsers().map((user) => user.id)).toEqual(['a1']);
      expect(spectator.service.rejectedUsers().map((user) => user.id)).toEqual(['r1']);
    });
  });

  describe('approve', () => {
    it('approves a user and reloads the list', () => {
      approve.mockReturnValue(of(undefined));
      spectator = createService();
      getUsers.mockReturnValue(of([anAppUserMock({ status: 'APPROVED' })]));

      spectator.service.approve('user-1');

      expect(approve).toHaveBeenCalledWith('user-1');
      expect(spectator.service.users()[0].status).toBe('APPROVED');
      expect(spectator.service.actioningId()).toBeNull();
    });

    it('does not start a second action while one is in flight', () => {
      approve.mockReturnValue(new Subject<void>());
      spectator = createService();

      spectator.service.approve('user-1');
      spectator.service.approve('user-2');

      expect(approve).toHaveBeenCalledTimes(1);
    });
  });

  describe('reject', () => {
    it('surfaces the last-admin-protected reason as a friendly message', () => {
      reject.mockReturnValue(
        throwError(() => new HttpErrorResponse({ status: 400, error: { reason: 'LAST_ADMIN_PROTECTED' } }))
      );
      spectator = createService();

      spectator.service.reject('user-1');

      expect(spectator.service.error()).toBe('The last admin cannot be rejected or revoked.');
      expect(spectator.service.actioningId()).toBeNull();
    });
  });

  describe('revoke', () => {
    it('surfaces the user-not-approved reason as a friendly message', () => {
      revoke.mockReturnValue(
        throwError(() => new HttpErrorResponse({ status: 400, error: { reason: 'USER_NOT_APPROVED' } }))
      );
      spectator = createService();

      spectator.service.revoke('user-1');

      expect(spectator.service.error()).toBe('This user is not currently approved.');
    });
  });

  describe('resetPassword', () => {
    it('stores the one-time temporary password', () => {
      resetPassword.mockReturnValue(of('temp-pass-123'));
      spectator = createService();

      spectator.service.resetPassword('user-1');

      expect(spectator.service.temporaryPassword()).toEqual({ userId: 'user-1', password: 'temp-pass-123' });
    });

    it('clears the temporary password on demand', () => {
      resetPassword.mockReturnValue(of('temp-pass-123'));
      spectator = createService();
      spectator.service.resetPassword('user-1');

      spectator.service.clearTemporaryPassword();

      expect(spectator.service.temporaryPassword()).toBeNull();
    });
  });
});
