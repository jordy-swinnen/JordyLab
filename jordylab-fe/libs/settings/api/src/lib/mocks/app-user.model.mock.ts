import { AppUser } from '../settings.models';

export function anAppUserMock(overrides: Partial<AppUser> = {}): AppUser {
  return {
    id: '8f14e45f-ea1f-4b0a-9c5d-3c1a2b3d4e5f',
    email: 'friend@example.org',
    firstName: 'Ada',
    lastName: 'Palmer',
    createdAt: '2026-09-27T18:40:11Z',
    enabled: true,
    realmRoles: [],
    status: 'PENDING',
    isAdmin: false,
    apps: [
      { key: 'gamecatalog', name: 'Game Catalog', granted: false, grantable: true, reason: null },
      { key: 'fna', name: 'FNA', granted: false, grantable: false, reason: "Holds the admin's private data" },
      { key: 'settings', name: 'Settings', granted: false, grantable: false, reason: 'Administration only' },
    ],
    chatLimit: { limit: 20, source: 'ENVIRONMENT' },
    sessionCount: 0,
    ...overrides,
  };
}
