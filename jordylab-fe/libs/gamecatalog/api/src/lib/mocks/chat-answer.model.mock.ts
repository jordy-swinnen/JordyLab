import { ChatAnswer } from '../gamecatalog.models';

export function aChatAnswerMock(overrides: Partial<ChatAnswer> = {}): ChatAnswer {
  return {
    answer: 'Super Mario World supports 2-player co-op.',
    games: [
      {
        id: '1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f',
        title: 'Super Mario World',
        platform: 'SNES',
        coverUrl: 'https://example.com/smw.png',
        coverEndpoint: null,
      },
    ],
    noMatch: false,
    ...overrides,
  };
}
