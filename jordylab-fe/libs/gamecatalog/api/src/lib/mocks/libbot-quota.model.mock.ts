import { LibBotQuota } from '../gamecatalog.models';

export function aLibBotQuotaMock(overrides: Partial<LibBotQuota> = {}): LibBotQuota {
  return {
    limit: 20,
    remaining: 14,
    resetsAt: '2026-10-08T00:00:00Z',
    exempt: false,
    ...overrides,
  };
}
