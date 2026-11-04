import { Machine } from '../machines.models';

export function aMachineMock(overrides: Partial<Machine> = {}): Machine {
  return {
    id: '2d1d3b7a-5d0e-4f43-9f6b-1c1f0a8a0b11',
    name: 'JordyBox',
    scope: 'INGEST',
    createdAt: '2026-11-03T10:00:00Z',
    lastUsedAt: null,
    revokedAt: null,
    ...overrides,
  };
}
