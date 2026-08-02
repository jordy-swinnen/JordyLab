import { Briefing } from '../fna.models';

export function aBriefingMock(overrides: Partial<Briefing> = {}): Briefing {
  return {
    id: 'b1',
    generatedAt: '2026-08-01T06:30:00Z',
    content: '## Portfolio Impact',
    modelUsed: 'claude-sonnet-5',
    ...overrides,
  };
}
