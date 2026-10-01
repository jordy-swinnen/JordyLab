import { SwitchBulkLine } from '../gamecatalog.models';
import { aSwitchSearchResultMock } from './switch-search-result.model.mock';

export function aSwitchBulkLineMock(overrides: Partial<SwitchBulkLine> = {}): SwitchBulkLine {
  return {
    line: 'Mario Kart 8 Deluxe',
    status: 'MATCH',
    candidates: [aSwitchSearchResultMock()],
    existingGameId: null,
    include: true,
    ...overrides,
  };
}
