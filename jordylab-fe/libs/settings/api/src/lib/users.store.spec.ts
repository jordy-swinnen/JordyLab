import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { AppUser } from './settings.models';
import { SettingsUsersApiService } from './settings-users-api.service';
import { UsersStore } from './users.store';
import { anAppUserMock } from './mocks/app-user.model.mock';

describe('UsersStore', () => {
  const users = vi.fn<SettingsUsersApiService['getUsers']>();
  const pendingCount = vi.fn<SettingsUsersApiService['getPendingCount']>();
  const approve = vi.fn<SettingsUsersApiService['approve']>();
  const reject = vi.fn<SettingsUsersApiService['reject']>();
  const revoke = vi.fn<SettingsUsersApiService['revoke']>();
  const grantApp = vi.fn<SettingsUsersApiService['grantApp']>();
  const removeApp = vi.fn<SettingsUsersApiService['removeApp']>();
  const deletePerson = vi.fn<SettingsUsersApiService['deletePerson']>();
  const getSessions = vi.fn<SettingsUsersApiService['getSessions']>();
  const resetPassword = vi.fn<SettingsUsersApiService['resetPassword']>();
  const approveResetRequest = vi.fn<SettingsUsersApiService['approveResetRequest']>();
  const rejectResetRequest = vi.fn<SettingsUsersApiService['rejectResetRequest']>();

  let store: UsersStore;

  beforeEach(() => {
    vi.clearAllMocks();
    users.mockReturnValue(of<AppUser[]>([
      anAppUserMock({ id: 'p1', status: 'PENDING' }),
      anAppUserMock({ id: 'a1', status: 'APPROVED', role: 'guest', appGrants: ['gamecatalog'] }),
      anAppUserMock({ id: 'r1', status: 'REJECTED' }),
      anAppUserMock({ id: 'v1', status: 'REVOKED' }),
    ]));
    pendingCount.mockReturnValue(of(1));
    approve.mockReturnValue(of(undefined));
    reject.mockReturnValue(of(undefined));
    revoke.mockReturnValue(of(undefined));
    grantApp.mockReturnValue(of(undefined));
    removeApp.mockReturnValue(of(undefined));
    deletePerson.mockReturnValue(of(undefined));
    getSessions.mockReturnValue(of([]));
    resetPassword.mockReturnValue(of({ temporaryPassword: 'temporary-password-20ch' }));
    approveResetRequest.mockReturnValue(of(undefined));
    rejectResetRequest.mockReturnValue(of(undefined));

    TestBed.configureTestingModule({
      providers: [
        UsersStore,
        {
          provide: SettingsUsersApiService,
          useValue: {
            getUsers: users,
            getPendingCount: pendingCount,
            approve,
            reject,
            revoke,
            grantApp,
            removeApp,
            deletePerson,
            getSessions,
            resetPassword,
            approveResetRequest,
            rejectResetRequest,
          },
        },
      ],
    });
    store = TestBed.inject(UsersStore);
  });

  it('groups accounts by their stored status', () => {
    expect(store.pendingUsers()).toEqual([expect.objectContaining({ id: 'p1' })]);
    expect(store.approvedUsers()).toEqual([expect.objectContaining({ id: 'a1' })]);
    expect(store.rejectedUsers()).toEqual([
      expect.objectContaining({ id: 'r1' }),
      expect.objectContaining({ id: 'v1' }),
    ]);
    expect(store.pendingCount()).toBe(1);
  });

  it('approves and reloads', () => {
    store.approve('p1');

    expect(approve).toHaveBeenCalledWith('p1');
    expect(users).toHaveBeenCalledTimes(2);
  });

  it('grants and revokes an app through the API', () => {
    store.grantApp('a1', 'gamecatalog');
    store.removeApp('a1', 'gamecatalog');

    expect(grantApp).toHaveBeenCalledWith('a1', 'gamecatalog');
    expect(removeApp).toHaveBeenCalledWith('a1', 'gamecatalog');
  });

  it('deletes a person and reloads the list', () => {
    store.deletePerson('a1');

    expect(deletePerson).toHaveBeenCalledWith('a1');
    expect(users).toHaveBeenCalledTimes(2);
  });

  it('opens and closes the session panel for one user', () => {
    store.toggleSessions('a1');

    expect(getSessions).toHaveBeenCalledWith('a1');
    expect(store.sessionsUserId()).toBe('a1');

    store.toggleSessions('a1');

    expect(store.sessionsUserId()).toBeNull();
  });

  it('approves and rejects a reset request', () => {
    store.approveResetRequest('a1', 'r1');
    store.rejectResetRequest('a1', 'r1');

    expect(approveResetRequest).toHaveBeenCalledWith('a1', 'r1');
    expect(rejectResetRequest).toHaveBeenCalledWith('a1', 'r1');
  });

  it('reveals the temporary password once and clears it on dismiss', () => {
    store.resetPassword('a1');

    expect(resetPassword).toHaveBeenCalledWith('a1');
    expect(store.resetResult()).toEqual({
      userId: 'a1',
      temporaryPassword: 'temporary-password-20ch',
    });

    store.dismissResetResult();

    expect(store.resetResult()).toBeNull();
  });

  it('maps an already-decided reset request', () => {
    approveResetRequest.mockReturnValue(
      throwError(
        () => new HttpErrorResponse({ status: 409, error: { reason: 'REQUEST_NOT_PENDING' } }),
      ),
    );

    store.approveResetRequest('a1', 'r1');

    expect(store.error()).toBe('That reset request was already decided.');
  });

  it('maps the last-admin rejection to a message', () => {
    reject.mockReturnValue(
      throwError(() => new HttpErrorResponse({
        status: 409,
        error: { reason: 'LAST_ADMIN_PROTECTED', message: 'nope' },
      })),
    );

    store.reject('a1');

    expect(store.error()).toBe('The last admin cannot be rejected or revoked.');
  });

  it('maps an unknown failure to the generic message', () => {
    revoke.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 500, error: { reason: 'BOOM' } })),
    );

    store.revoke('a1');

    expect(store.error()).toBe('Failed to revoke user.');
  });
});
