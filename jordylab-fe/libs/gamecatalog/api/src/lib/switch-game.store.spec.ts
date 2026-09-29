import { createServiceFactory, SpectatorService } from '@ngneat/spectator/vitest';
import { of, Subject, throwError } from 'rxjs';
import { GameCatalogApiService } from './gamecatalog-api.service';
import { aSwitchSearchResultMock } from './mocks/switch-search-result.model.mock';
import { SwitchGameStore } from './switch-game.store';

vi.useFakeTimers();

describe('SwitchGameStore', () => {
  let spectator: SpectatorService<SwitchGameStore>;
  const searchSwitchGames = vi.fn<GameCatalogApiService['searchSwitchGames']>();
  const addSwitchGame = vi.fn<GameCatalogApiService['addSwitchGame']>();

  const createService = createServiceFactory({
    service: SwitchGameStore,
    providers: [{ provide: GameCatalogApiService, useValue: { searchSwitchGames, addSwitchGame } }],
  });

  beforeEach(() => {
    searchSwitchGames.mockReset();
    addSwitchGame.mockReset();
  });

  it('starts empty and not loading', () => {
    spectator = createService();

    expect(spectator.service.results()).toEqual([]);
    expect(spectator.service.loading()).toBe(false);
    expect(spectator.service.added()).toBe(false);
  });

  it('searches after debounce and stores results', () => {
    searchSwitchGames.mockReturnValue(of([aSwitchSearchResultMock()]));
    spectator = createService();

    spectator.service.search('mario');
    vi.advanceTimersByTime(400);

    expect(searchSwitchGames).toHaveBeenCalledWith('mario');
    expect(spectator.service.results()).toHaveLength(1);
    expect(spectator.service.loading()).toBe(false);
  });

  it('sets an error when search fails', () => {
    searchSwitchGames.mockReturnValue(throwError(() => new Error('network error')));
    spectator = createService();

    spectator.service.search('mario');
    vi.advanceTimersByTime(400);

    expect(spectator.service.error()).toBe('Search failed. Please try again.');
    expect(spectator.service.results()).toEqual([]);
  });

  it('prevents add until a result is selected', () => {
    spectator = createService();

    expect(spectator.service.canAdd()).toBe(false);

    spectator.service.selectResult(aSwitchSearchResultMock());

    expect(spectator.service.canAdd()).toBe(true);
  });

  it('switches to manual mode and allows add with a title', () => {
    spectator = createService();

    spectator.service.setMode('manual');
    spectator.service.setManualTitle('My Game');

    expect(spectator.service.canAdd()).toBe(true);
  });

  it('adds a searched game and marks added', () => {
    addSwitchGame.mockReturnValue(of({ gameId: 'id-1', title: 'Mario Kart 8 Deluxe', platform: 'Nintendo Switch', format: 'PHYSICAL' }));
    spectator = createService();
    spectator.service.selectResult(aSwitchSearchResultMock());

    spectator.service.addGame();

    expect(addSwitchGame).toHaveBeenCalledWith(111, null, 'PHYSICAL');
    expect(spectator.service.added()).toBe(true);
    expect(spectator.service.adding()).toBe(false);
  });

  it('adds a manual game and stores the error on failure', () => {
    addSwitchGame.mockReturnValue(throwError(() => ({ error: { message: 'already present' } })));
    spectator = createService();
    spectator.service.setMode('manual');
    spectator.service.setManualTitle('My Game');

    spectator.service.addGame();

    expect(addSwitchGame).toHaveBeenCalledWith(null, 'My Game', 'PHYSICAL');
    expect(spectator.service.addError()).toBe('already present');
    expect(spectator.service.added()).toBe(false);
  });

  it('resets to the initial state', () => {
    spectator = createService();
    spectator.service.selectResult(aSwitchSearchResultMock());
    spectator.service.setFormat('DIGITAL');

    spectator.service.reset();

    expect(spectator.service.selectedResult()).toBeNull();
    expect(spectator.service.format()).toBe('PHYSICAL');
  });
});
