import { signal } from '@angular/core';
import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import {
  aGuestChatLimitMock,
  AppUser,
  anAppUserMock,
  GuestChatLimit,
  GuestChatLimitStore,
  UserSession,
  UsersStore,
} from '@jordylab-fe/settings/api';
import { UsersPageComponent } from './users-page.component';

describe('UsersPageComponent', () => {
  const pendingUsers = signal<AppUser[]>([]);
  const approvedUsers = signal<AppUser[]>([]);
  const rejectedUsers = signal<AppUser[]>([]);
  const loading = signal(true);
  const error = signal<string | null>(null);
  const actioningId = signal<string | null>(null);
  const sessionsUserId = signal<string | null>(null);
  const sessions = signal<UserSession[]>([]);
  const sessionsLoading = signal(false);

  const approve = vi.fn<UsersStore['approve']>();
  const reject = vi.fn<UsersStore['reject']>();
  const revoke = vi.fn<UsersStore['revoke']>();

  const storeMock = {
    pendingUsers: pendingUsers.asReadonly(),
    approvedUsers: approvedUsers.asReadonly(),
    rejectedUsers: rejectedUsers.asReadonly(),
    loading: loading.asReadonly(),
    error: error.asReadonly(),
    actioningId: actioningId.asReadonly(),
    sessionsUserId: sessionsUserId.asReadonly(),
    sessions: sessions.asReadonly(),
    sessionsLoading: sessionsLoading.asReadonly(),
    approve,
    reject,
    revoke,
    grantApp: vi.fn(),
    removeApp: vi.fn(),
    toggleSessions: vi.fn(),
    endSession: vi.fn(),
    endAllSessions: vi.fn(),
    deletePerson: vi.fn(),
  };

  const guestLimit = signal<GuestChatLimit | null>(aGuestChatLimitMock());
  const guestLimitLoading = signal(false);
  const guestLimitSaving = signal(false);
  const guestLimitError = signal<string | null>(null);
  const saveDefault = vi.fn<GuestChatLimitStore['saveDefault']>();

  const guestLimitMock = {
    limit: guestLimit.asReadonly(),
    loading: guestLimitLoading.asReadonly(),
    saving: guestLimitSaving.asReadonly(),
    error: guestLimitError.asReadonly(),
    saveDefault,
  };

  let spectator: Spectator<UsersPageComponent>;
  const createComponent = createComponentFactory({
    component: UsersPageComponent,
    providers: [
      { provide: UsersStore, useValue: storeMock },
      { provide: GuestChatLimitStore, useValue: guestLimitMock },
    ],
  });

  beforeEach(() => {
    vi.clearAllMocks();
    loading.set(false);
    error.set(null);
    actioningId.set(null);
    pendingUsers.set([]);
    approvedUsers.set([]);
    rejectedUsers.set([]);
    spectator = createComponent();
  });

  it('shows a pending account with approve and reject actions', () => {
    pendingUsers.set([anAppUserMock({ id: 'p1', username: 'ada', displayName: 'Ada Palmer' })]);

    spectator.detectChanges();

    expect(spectator.query('[data-testid="approve-user"]')).toHaveText('Approve');
    expect(spectator.query('[data-testid="reject-user"]')).toHaveText('Reject');
    expect(spectator.element).toHaveText('Ada Palmer');
  });

  it('approves and rejects through the store', () => {
    pendingUsers.set([anAppUserMock({ id: 'p1' })]);
    spectator.detectChanges();

    spectator.click('[data-testid="approve-user"]');
    spectator.click('[data-testid="reject-user"]');

    expect(approve).toHaveBeenCalledWith('p1');
    expect(reject).toHaveBeenCalledWith('p1');
  });

  it('offers revoke for a non-admin approved account only', () => {
    approvedUsers.set([
      anAppUserMock({ id: 'g1', status: 'APPROVED', role: 'guest' }),
      anAppUserMock({ id: 'a1', status: 'APPROVED', role: 'admin', username: 'boss' }),
    ]);
    spectator.detectChanges();

    expect(spectator.queryAll('[data-testid="revoke-user"]')).toHaveLength(1);

    spectator.click('[data-testid="revoke-user"]');
    expect(revoke).toHaveBeenCalledWith('g1');
  });

  it('shows rejected and revoked accounts together', () => {
    rejectedUsers.set([
      anAppUserMock({ id: 'r1', status: 'REJECTED', username: 'rejected-one' }),
      anAppUserMock({ id: 'v1', status: 'REVOKED', username: 'revoked-one' }),
    ]);
    spectator.detectChanges();

    expect(spectator.element).toHaveText('rejected-one');
    expect(spectator.element).toHaveText('revoked-one');
  });

  it('shows the error state with a retry', () => {
    error.set('Failed to load users.');
    spectator.detectChanges();

    expect(spectator.element).toHaveText('Failed to load users.');
  });
});
