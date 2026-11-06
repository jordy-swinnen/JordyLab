import { GameExclusion } from '../gamecatalog.models';

export function aGameExclusionMock(overrides: Partial<GameExclusion> = {}): GameExclusion {
  return {
    id: 'game-1',
    title: 'Proton 9.0',
    platforms: ['Steam'],
    excludedAt: '2026-11-06T10:00:00Z',
    ...overrides,
  };
}
