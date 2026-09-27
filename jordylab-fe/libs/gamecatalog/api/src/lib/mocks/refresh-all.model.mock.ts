import { RefreshAll } from '../gamecatalog.models';
import { aRefreshCountMock } from './refresh-count.model.mock';

export function aRefreshAllMock(overrides: Partial<RefreshAll> = {}): RefreshAll {
  return {
    metadata: aRefreshCountMock(),
    enrichment: aRefreshCountMock({ processed: 2, remaining: 5 }),
    multiplayer: aRefreshCountMock(),
    ...overrides,
  };
}
