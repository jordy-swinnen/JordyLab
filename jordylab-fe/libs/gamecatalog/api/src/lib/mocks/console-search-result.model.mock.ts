import { ConsoleSearchResult } from '../gamecatalog.models';

export function aConsoleSearchResultMock(overrides: Partial<ConsoleSearchResult> = {}): ConsoleSearchResult {
  return {
    igdbGameId: 13427,
    title: 'Mario Kart 8 Deluxe',
    releaseYear: 2017,
    genres: ['Racing'],
    developer: 'Nintendo EPD',
    coverUrl: 'https://images.igdb.com/cover.jpg',
    bannerUrl: 'https://images.igdb.com/banner.jpg',
    alreadyOnConsole: false,
    ...overrides,
  };
}
