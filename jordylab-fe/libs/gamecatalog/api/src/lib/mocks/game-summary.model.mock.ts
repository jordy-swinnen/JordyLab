import { GameSummary } from '../gamecatalog.models';
import { aPlatformChipMock } from './platform-chip.model.mock';

export function aGameSummaryMock(overrides: Partial<GameSummary> = {}): GameSummary {
  return {
    id: '1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f',
    title: 'Super Mario World',
    platforms: [aPlatformChipMock()],
    sources: ['EMULATED'],
    coverStatus: 'EXTERNAL_URL',
    coverUrl: 'https://example.com/smw.png',
    coverEndpoint: null,
    installStatus: 'INSTALLED',
    localMultiplayer: false,
    votes: { wantToPlay: 0, playedLiked: 0, playedDisliked: 0 },
    myMark: null,
    romSummary: null,
    ...overrides,
  };
}
