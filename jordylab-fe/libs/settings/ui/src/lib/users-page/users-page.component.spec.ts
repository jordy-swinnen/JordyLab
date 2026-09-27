import { signal } from '@angular/core';
import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { AppUser, anAppUserMock, UsersStore } from '@jordylab-fe/settings/api';
import { UsersPageComponent } from './users-page.component';

describe('UsersPageComponent', () => {
  const pendingUsers = signal<AppUser[]>([]);
  const approvedUsers = signal<AppUser[]>([]);
  const rejectedUsers = signal<AppUser[]>([]);
  const loading = signal(true);
  const error = signal<string | null>(null);
  const actioningId = signal<string | null>(null);
  const temporaryPassword = signal<{ userId: string; password: string } | null>(null);

  const approve = vi.fn<UsersStore['approve']>();
  const reject = vi.fn<UsersStore['reject']>();
  const revoke = vi.fn<UsersStore['revoke']>();
  const resetPassword = vi.fn<UsersStore['resetPassword']>();
  const clearTemporaryPassword = vi.fn<UsersStore['clearTemporaryPassword']>();

  const storeMock = {
    pendingUsers: pendingUsers.asReadonly(),
    approvedUsers: approvedUsers.asReadonly(),
    rejectedUsers: rejectedUsers.asReadonly(),
    loading: loading.asReadonly(),
    error: error.asReadonly(),
    actioningId: actioningId.asReadonly(),
    temporaryPassword: temporaryPassword.asReadonly(),
    approve,
    reject,
    revoke,
    resetPassword,
    clearTemporaryPassword,
  };

  let spectator: Spectator<UsersPageComponent>;

  const createComponent = createComponentFactory({
    component: UsersPageComponent,
    providers: [{ provide: UsersStore, useValue: storeMock }],
  });

  beforeEach(() => {
    pendingUsers.set([]);
    approvedUsers.set([]);
    rejectedUsers.set([]);
    loading.set(true);
    error.set(null);
    actioningId.set(null);
    temporaryPassword.set(null);
    approve.mockReset();
    reject.mockReset();
    revoke.mockReset();
    resetPassword.mockReset();
    clearTemporaryPassword.mockReset();
    spectator = createComponent();
  });

  const populate = () => {
    loading.set(false);
    spectator.detectChanges();
  };

  it('shows skeletons while loading', () => {
    expect(spectator.queryAll('hlm-skeleton').length).toBeGreaterThan(0);
  });

  it('renders a pending user with an empty-state message for the other sections', () => {
    pendingUsers.set([anAppUserMock({ id: 'p1', firstName: 'Ada', lastName: 'Palmer' })]);
    populate();

    expect(spectator.element).toHaveText('Ada Palmer');
    expect(spectator.element).toHaveText('friend@example.org');
    expect(spectator.element).toHaveText('No approved guests yet.');
    expect(spectator.element).toHaveText('No rejected accounts.');
  });

  it('approves a pending user', () => {
    pendingUsers.set([anAppUserMock({ id: 'p1' })]);
    populate();

    spectator.click('[data-testid="approve-user"]');

    expect(approve).toHaveBeenCalledWith('p1');
  });

  it('rejects a pending user', () => {
    pendingUsers.set([anAppUserMock({ id: 'p1' })]);
    populate();

    spectator.click('[data-testid="reject-user"]');

    expect(reject).toHaveBeenCalledWith('p1');
  });

  it('revokes and resets the password of an approved guest', () => {
    approvedUsers.set([anAppUserMock({ id: 'a1', status: 'APPROVED' })]);
    populate();

    spectator.click('[data-testid="revoke-user"]');
    expect(revoke).toHaveBeenCalledWith('a1');

    spectator.click('[data-testid="reset-password"]');
    expect(resetPassword).toHaveBeenCalledWith('a1');
  });

  it('re-approves a rejected user', () => {
    rejectedUsers.set([anAppUserMock({ id: 'r1', status: 'REJECTED', enabled: false })]);
    populate();

    spectator.click('[data-testid="approve-user"]');

    expect(approve).toHaveBeenCalledWith('r1');
  });

  it('shows an error message when an action fails', () => {
    error.set('The last admin cannot be rejected or revoked.');
    populate();

    expect(spectator.query('.text-destructive')).toHaveText('The last admin cannot be rejected or revoked.');
  });

  it('reveals a one-time temporary password and dismisses it', () => {
    temporaryPassword.set({ userId: 'a1', password: 'temp-pass-123' });
    populate();

    expect(spectator.query('[data-testid="temporary-password"]')).toHaveText('temp-pass-123');

    spectator.click('[data-testid="dismiss-temporary-password"]');

    expect(clearTemporaryPassword).toHaveBeenCalled();
  });
});
