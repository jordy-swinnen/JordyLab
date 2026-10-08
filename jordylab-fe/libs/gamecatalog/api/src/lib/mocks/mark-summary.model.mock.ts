import { MarkResult } from '../gamecatalog.models';

export function aMarkResultMock(overrides: Partial<MarkResult> = {}): MarkResult {
  return {
    votes: { wantToPlay: 1, playedLiked: 0, playedDisliked: 0 },
    myMark: 'WANT_TO_PLAY',
    ...overrides,
  };
}
