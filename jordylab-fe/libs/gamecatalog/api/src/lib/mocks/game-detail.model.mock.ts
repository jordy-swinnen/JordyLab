import { GameDetail } from '../gamecatalog.models';

export function aGameDetailMock(overrides: Partial<GameDetail> = {}): GameDetail {
  return {
    id: '1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f',
    title: 'Super Mario World',
    platform: 'SNES',
    sourceKey: 'snes',
    artworkStatus: 'EXTERNAL_URL',
    artworkUrl: 'https://example.com/smw.png',
    artworkEndpoint: null,
    enrichmentStatus: 'ENRICHED',
    genre: 'Platformer',
    maxLocalPlayers: 2,
    onlineMultiplayer: false,
    singlePlayer: true,
    description: 'A classic SNES platformer.',
    firstSeenAt: '2026-08-02T10:15:00Z',
    ...overrides,
  };
}
