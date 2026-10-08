import { LibBotAnswer } from '../gamecatalog.models';
import { aPlatformChipMock } from './platform-chip.model.mock';

export function aLibBotAnswerMock(overrides: Partial<LibBotAnswer> = {}): LibBotAnswer {
  return {
    outcome: 'ANSWERED',
    language: 'en',
    text: 'Super Mario World supports 2-player co-op.',
    applied: ['2+ local players', 'local multiplayer'],
    unknown: null,
    references: [
      {
        gameId: '1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f',
        title: 'Super Mario World',
        platforms: [aPlatformChipMock({ name: 'SNES' })],
        cover: { status: 'EXTERNAL_URL', externalUrl: 'https://example.com/smw.png', localUrl: null },
      },
    ],
    ...overrides,
  };
}
