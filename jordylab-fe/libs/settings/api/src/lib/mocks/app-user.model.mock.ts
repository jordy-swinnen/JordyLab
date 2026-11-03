import { AppUser } from '../settings.models';

export function anAppUserMock(overrides: Partial<AppUser> = {}): AppUser {
  return {
    id: '8f14e45f-ea1f-4b0a-9c5d-3c1a2b3d4e5f',
    username: 'ada.palmer',
    displayName: 'Ada Palmer',
    role: null,
    status: 'PENDING',
    appGrants: [],
    createdAt: '2026-09-27T18:40:11Z',
    pendingResetRequest: null,
    ...overrides,
  };
}
