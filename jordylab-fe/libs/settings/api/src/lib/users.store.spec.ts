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

    TestBed.configureTestingModule({
      providers: [
        UsersStore,
        {
          provide: SettingsUsersApiService,
          useValue: { getUsers: users, getPendingCount: pendingCount, approve, reject, revoke },
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
