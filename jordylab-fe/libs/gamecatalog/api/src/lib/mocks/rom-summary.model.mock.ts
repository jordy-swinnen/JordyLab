import { RomSummary } from '../gamecatalog.models';

export function aRomSummaryMock(overrides: Partial<RomSummary> = {}): RomSummary {
  return {
    state: 'VALIDATED',
    validated: 1,
    broken: 0,
    unknown: 0,
    total: 1,
    ...overrides,
  };
}
