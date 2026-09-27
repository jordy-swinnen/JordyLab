import { LibraryStatus } from '../gamecatalog.models';

export function aLibraryStatusMock(overrides: Partial<LibraryStatus> = {}): LibraryStatus {
  return {
    owned: {
      lastSuccessAt: '2026-09-27T10:00:04Z',
      lastOutcome: 'APPLIED',
      entriesActive: 412,
      metadataCalls: 25,
      aiCalls: 6,
      familyTokenPresent: false,
      stale: false,
    },
    family: {
      lastSuccessAt: null,
      lastOutcome: null,
      entriesActive: 0,
      metadataCalls: 0,
      aiCalls: 0,
      familyTokenPresent: false,
      stale: true,
    },
    ownedConfigured: true,
    ...overrides,
  };
}
