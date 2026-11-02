import { UserSession } from '../settings.models';

export function aUserSessionMock(overrides: Partial<UserSession> = {}): UserSession {
  return {
    id: 'session-1',
    kind: 'WEB',
    offline: false,
    startedAt: '2026-11-02T08:01:00Z',
    lastAccessedAt: '2026-11-02T08:40:00Z',
    ...overrides,
  };
}
