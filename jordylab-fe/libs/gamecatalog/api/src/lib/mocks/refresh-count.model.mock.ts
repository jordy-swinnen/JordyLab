import { RefreshCount } from '../gamecatalog.models';

export function aRefreshCountMock(overrides: Partial<RefreshCount> = {}): RefreshCount {
  return {
    processed: 3,
    remaining: 0,
    ...overrides,
  };
}
