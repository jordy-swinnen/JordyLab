import { FeedbackApp } from '../feedback.models';

export function aFeedbackAppMock(overrides: Partial<FeedbackApp> = {}): FeedbackApp {
  return {
    id: 'GAME_CATALOG',
    name: 'Game Catalog',
    ...overrides,
  };
}
