import { SwitchSearchResult } from '../gamecatalog.models';

export function aSwitchSearchResultMock(overrides: Partial<SwitchSearchResult> = {}): SwitchSearchResult {
  return {
    igdbGameId: 111,
    title: 'Mario Kart 8 Deluxe',
    releaseYear: 2017,
    genres: ['Racing'],
    developer: 'Nintendo EPD',
    coverUrl: 'https://cover.test/cover.jpg',
    bannerUrl: 'https://banner.test/banner.jpg',
    ...overrides,
  };
}
