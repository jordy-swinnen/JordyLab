import { LibrarySyncRun } from '../gamecatalog.models';

export function aLibrarySyncRunMock(overrides: Partial<LibrarySyncRun> = {}): LibrarySyncRun {
  return {
    librarySource: 'OWNED',
    outcome: 'APPLIED',
    startedAt: '2026-09-27T10:00:00Z',
    finishedAt: '2026-09-27T10:00:04Z',
    entriesSubmitted: 412,
    entriesAdded: 87,
    entriesRemoved: 0,
    metadataCalls: 25,
    aiCalls: 6,
    errorCode: null,
    ...overrides,
  };
}
