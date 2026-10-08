import { LibraryHealth } from '../gamecatalog.models';

export function aLibraryHealthMock(overrides: Partial<LibraryHealth> = {}): LibraryHealth {
  return {
    totalGames: 230,
    gamesWithoutCover: 12,
    gamesWithoutDescription: 30,
    gamesPendingIndex: 5,
    ...overrides,
  };
}
