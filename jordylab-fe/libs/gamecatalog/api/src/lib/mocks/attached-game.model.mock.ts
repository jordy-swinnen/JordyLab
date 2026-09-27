import { AttachedGame } from '../gamecatalog.models';

export function aAttachedGameMock(overrides: Partial<AttachedGame> = {}): AttachedGame {
  return {
    id: '1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f',
    title: 'Super Mario World',
    coverUrl: 'https://example.com/smw.png',
    coverEndpoint: null,
    ...overrides,
  };
}
