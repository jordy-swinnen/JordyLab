import { GamesPage } from '../gamecatalog.models';
import { aGameSummaryMock } from './game-summary.model.mock';

export function aGamesPageMock(overrides: Partial<GamesPage> = {}): GamesPage {
  const content = overrides.content ?? [aGameSummaryMock()];

  return {
    content,
    page: 0,
    size: 60,
    totalElements: content.length,
    totalPages: 1,
    ...overrides,
  };
}
