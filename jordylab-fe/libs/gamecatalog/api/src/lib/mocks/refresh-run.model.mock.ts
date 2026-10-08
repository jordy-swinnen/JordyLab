import { RefreshRun } from '../gamecatalog.models';

export function aRefreshRunMock(overrides: Partial<RefreshRun> = {}): RefreshRun {
  return {
    id: 'a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d',
    kind: 'DATA',
    status: 'RUNNING',
    total: 228,
    processed: 91,
    failed: 2,
    stopRequested: false,
    failureSummary: null,
    startedAt: '2026-10-07T12:00:00Z',
    finishedAt: null,
    ...overrides,
  };
}
