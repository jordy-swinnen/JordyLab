import { GameSummary } from '../gamecatalog.models';

export function aGameSummaryMock(overrides: Partial<GameSummary> = {}): GameSummary {
  return {
    id: '1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f',
    title: 'Super Mario World',
    platform: 'SNES',
    artworkStatus: 'EXTERNAL_URL',
    artworkUrl: 'https://example.com/smw.png',
    artworkEndpoint: null,
    ...overrides,
  };
}
