import { createServiceFactory, SpectatorService } from '@ngneat/spectator/vitest';
import { of, Subject, throwError } from 'rxjs';
import { GameCatalogApiService } from './gamecatalog-api.service';
import { MarkResult } from './gamecatalog.models';
import { afterChange, MarkState, MarkStore } from './mark.store';
import { aMarkResultMock } from './mocks/mark-summary.model.mock';

describe('MarkStore', () => {
  let spectator: SpectatorService<MarkStore>;
  const setMark = vi.fn<GameCatalogApiService['setMark']>();
  const game: MarkState & { id: string } = {
    id: 'game-1',
    votes: { wantToPlay: 4, playedLiked: 2, playedDisliked: 0 },
    myMark: null,
  };

  const createService = createServiceFactory({
    service: MarkStore,
    providers: [{ provide: GameCatalogApiService, useValue: { setMark } }],
  });

  beforeEach(() => {
    setMark.mockReset();
    setMark.mockReturnValue(of(aMarkResultMock({ votes: { wantToPlay: 5, playedLiked: 2, playedDisliked: 0 } })));
    spectator = createService();
  });

  it('leaves a game alone that nobody changed', () => {
    expect(spectator.service.stateOf(game)).toBe(game);
  });

  it('shows the new mark and totals at once and keeps the server totals once saved', () => {
    const answer = new Subject<MarkResult>();
    setMark.mockReturnValue(answer.asObservable());

    spectator.service.toggle(game, 'WANT_TO_PLAY');

    expect(spectator.service.stateOf(game).myMark).toBe('WANT_TO_PLAY');
    expect(spectator.service.stateOf(game).votes.wantToPlay).toBe(5);
    expect(spectator.service.pending().has('game-1')).toBe(true);

    answer.next(aMarkResultMock({ votes: { wantToPlay: 6, playedLiked: 2, playedDisliked: 0 } }));

    expect(spectator.service.stateOf(game).votes.wantToPlay).toBe(6);
    expect(spectator.service.pending().has('game-1')).toBe(false);
  });

  it('choosing the current mark clears it', () => {
    const marked = { ...game, myMark: 'WANT_TO_PLAY' as const };
    setMark.mockReturnValue(of(aMarkResultMock({ myMark: null, votes: { wantToPlay: 3, playedLiked: 2, playedDisliked: 0 } })));

    spectator.service.toggle(marked, 'WANT_TO_PLAY');

    expect(setMark).toHaveBeenCalledWith('game-1', null);
    expect(spectator.service.stateOf(marked).myMark).toBeNull();
  });

  it('choosing another mark replaces the first and moves the vote between totals', () => {
    const marked = { ...game, myMark: 'WANT_TO_PLAY' as const };
    setMark.mockReturnValue(
      of(aMarkResultMock({ myMark: 'PLAYED_LIKED', votes: { wantToPlay: 3, playedLiked: 3, playedDisliked: 0 } })),
    );

    spectator.service.toggle(marked, 'PLAYED_LIKED');

    expect(setMark).toHaveBeenCalledWith('game-1', 'PLAYED_LIKED');
    expect(spectator.service.stateOf(marked).votes).toEqual({ wantToPlay: 3, playedLiked: 3, playedDisliked: 0 });
  });

  it('takes the change back and says so when the server refuses it', () => {
    setMark.mockReturnValue(throwError(() => new Error('offline')));

    spectator.service.toggle(game, 'PLAYED_DISLIKED');

    expect(spectator.service.stateOf(game)).toBe(game);
    expect(spectator.service.error()).toBe('Could not save your mark. Try again.');
    expect(spectator.service.pending().has('game-1')).toBe(false);
  });

  it('puts back the earlier change, not the original, when a later one is refused', () => {
    spectator.service.toggle(game, 'WANT_TO_PLAY');
    setMark.mockReturnValue(throwError(() => new Error('offline')));
    const afterFirst = spectator.service.stateOf(game);

    spectator.service.toggle(afterFirst, 'PLAYED_LIKED');

    expect(spectator.service.stateOf(game).myMark).toBe('WANT_TO_PLAY');
  });

  it('ignores a second tap while the first is still being saved', () => {
    setMark.mockReturnValue(new Subject<MarkResult>().asObservable());

    spectator.service.toggle(game, 'WANT_TO_PLAY');
    spectator.service.toggle(game, 'PLAYED_LIKED');

    expect(setMark).toHaveBeenCalledTimes(1);
  });

  it('forgets saved changes when a list loads fresh data but keeps requests in flight', () => {
    spectator.service.toggle(game, 'WANT_TO_PLAY');
    setMark.mockReturnValue(new Subject<MarkResult>().asObservable());
    const other = { ...game, id: 'game-2' };
    spectator.service.toggle(other, 'PLAYED_LIKED');

    spectator.service.forget();

    expect(spectator.service.stateOf(game)).toBe(game);
    expect(spectator.service.stateOf(other).myMark).toBe('PLAYED_LIKED');
  });

  it('clears the error', () => {
    setMark.mockReturnValue(throwError(() => new Error('offline')));
    spectator.service.toggle(game, 'WANT_TO_PLAY');

    spectator.service.dismissError();

    expect(spectator.service.error()).toBeNull();
  });

  describe('afterChange', () => {
    it('moves one vote from the old mark to the new one and never goes below zero', () => {
      expect(afterChange({ wantToPlay: 0, playedLiked: 1, playedDisliked: 0 }, 'WANT_TO_PLAY', 'PLAYED_LIKED')).toEqual({
        wantToPlay: 0,
        playedLiked: 2,
        playedDisliked: 0,
      });
    });
  });
});
