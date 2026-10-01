import { HttpErrorResponse } from '@angular/common/http';
import { createServiceFactory, SpectatorService } from '@ngneat/spectator/vitest';
import { of, Subject, throwError } from 'rxjs';
import { GameCatalogApiService } from './gamecatalog-api.service';
import { GameDetail } from './gamecatalog.models';
import { GameDetailStore } from './game-detail.store';
import { aGameDetailMock } from './mocks/game-detail.model.mock';
import { aSwitchSearchResultMock } from './mocks/switch-search-result.model.mock';

describe('GameDetailStore', () => {
  let spectator: SpectatorService<GameDetailStore>;
  const getGame = vi.fn<GameCatalogApiService['getGame']>();
  const refreshGameMetadata = vi.fn<GameCatalogApiService['refreshGameMetadata']>();
  const refreshGameEnrichment = vi.fn<GameCatalogApiService['refreshGameEnrichment']>();
  const updateSwitchGame = vi.fn<GameCatalogApiService['updateSwitchGame']>();
  const deleteSwitchGame = vi.fn<GameCatalogApiService['deleteSwitchGame']>();
  const searchSwitchGames = vi.fn<GameCatalogApiService['searchSwitchGames']>();

  const createService = createServiceFactory({
    service: GameDetailStore,
    providers: [{ provide: GameCatalogApiService, useValue: { getGame, refreshGameMetadata, refreshGameEnrichment, updateSwitchGame, deleteSwitchGame, searchSwitchGames } }],
  });

  beforeEach(() => {
    getGame.mockReset();
    refreshGameMetadata.mockReset();
    refreshGameEnrichment.mockReset();
    updateSwitchGame.mockReset();
    deleteSwitchGame.mockReset();
    searchSwitchGames.mockReset();
    spectator = createService();
  });

  it('starts in the loading state', () => {
    expect(spectator.service.loading()).toBe(true);
    expect(spectator.service.game()).toBeNull();
  });

  it('loads the game for the id', () => {
    getGame.mockReturnValue(of(aGameDetailMock()));

    spectator.service.load('abc');

    expect(getGame).toHaveBeenCalledWith('abc');
    expect(spectator.service.game()).toEqual(aGameDetailMock());
    expect(spectator.service.loading()).toBe(false);
    expect(spectator.service.notFound()).toBe(false);
    expect(spectator.service.error()).toBeNull();
  });

  it('is loading until the game arrives', () => {
    const game = new Subject<GameDetail>();
    getGame.mockReturnValue(game.asObservable());

    spectator.service.load('abc');

    expect(spectator.service.loading()).toBe(true);

    game.next(aGameDetailMock());

    expect(spectator.service.loading()).toBe(false);
  });

  it('flags a 404 as not found without an error message', () => {
    getGame.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404 })));

    spectator.service.load('abc');

    expect(spectator.service.notFound()).toBe(true);
    expect(spectator.service.error()).toBeNull();
    expect(spectator.service.loading()).toBe(false);
  });

  it('sets an error message for other failures', () => {
    getGame.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));

    spectator.service.load('abc');

    expect(spectator.service.error()).toBe('Failed to load the game.');
    expect(spectator.service.notFound()).toBe(false);
    expect(spectator.service.loading()).toBe(false);
  });

  it('discards the previous game and state when loading another one', () => {
    getGame.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 404 })));
    spectator.service.load('missing');
    getGame.mockReturnValue(of(aGameDetailMock()));

    spectator.service.load('abc');

    expect(spectator.service.notFound()).toBe(false);
    expect(spectator.service.game()).toEqual(aGameDetailMock());
  });

  it('clears the previous game while the next one loads', () => {
    getGame.mockReturnValueOnce(of(aGameDetailMock()));
    spectator.service.load('abc');
    getGame.mockReturnValue(new Subject<GameDetail>().asObservable());

    spectator.service.load('def');

    expect(spectator.service.game()).toBeNull();
    expect(spectator.service.loading()).toBe(true);
  });

  it('refreshes deterministic metadata and replaces the loaded game', () => {
    getGame.mockReturnValue(of(aGameDetailMock()));
    spectator.service.load('abc');
    const refreshed = aGameDetailMock({ developer: 'Valve', releaseYear: 2011 });
    refreshGameMetadata.mockReturnValue(of(refreshed));

    spectator.service.refreshMetadata();

    expect(refreshGameMetadata).toHaveBeenCalledWith(aGameDetailMock().id);
    expect(spectator.service.game()).toEqual(refreshed);
    expect(spectator.service.refreshingMetadata()).toBe(false);
  });

  it('is refreshing until the metadata refresh arrives', () => {
    getGame.mockReturnValue(of(aGameDetailMock()));
    spectator.service.load('abc');
    refreshGameMetadata.mockReturnValue(new Subject<GameDetail>().asObservable());

    spectator.service.refreshMetadata();

    expect(spectator.service.refreshingMetadata()).toBe(true);
  });

  it('sets an error when the metadata refresh fails', () => {
    getGame.mockReturnValue(of(aGameDetailMock()));
    spectator.service.load('abc');
    refreshGameMetadata.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));

    spectator.service.refreshMetadata();

    expect(spectator.service.error()).toBe('Failed to refresh the deterministic facts.');
    expect(spectator.service.refreshingMetadata()).toBe(false);
  });

  it('refreshes the AI description and replaces the loaded game', () => {
    getGame.mockReturnValue(of(aGameDetailMock()));
    spectator.service.load('abc');
    const regenerated = aGameDetailMock({ description: 'A regenerated description.' });
    refreshGameEnrichment.mockReturnValue(of(regenerated));

    spectator.service.refreshEnrichment();

    expect(refreshGameEnrichment).toHaveBeenCalledWith(aGameDetailMock().id);
    expect(spectator.service.game()).toEqual(regenerated);
    expect(spectator.service.refreshingEnrichment()).toBe(false);
  });

  it('sets an error when the enrichment refresh fails', () => {
    getGame.mockReturnValue(of(aGameDetailMock()));
    spectator.service.load('abc');
    refreshGameEnrichment.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));

    spectator.service.refreshEnrichment();

    expect(spectator.service.error()).toBe('Failed to regenerate the description.');
  });

  it('does not refresh when no game is loaded', () => {
    spectator.service.refreshMetadata();
    spectator.service.refreshEnrichment();

    expect(refreshGameMetadata).not.toHaveBeenCalled();
    expect(refreshGameEnrichment).not.toHaveBeenCalled();
  });

  describe('Switch management', () => {
    const switchGame = aGameDetailMock({ platform: 'Nintendo Switch', hostFormats: { 'Nintendo Switch': 'PHYSICAL' } });

    beforeEach(() => {
      getGame.mockReturnValue(of(switchGame));
      spectator.service.load(switchGame.id);
    });

    it('changes the format and reloads the game', () => {
      const changed = aGameDetailMock({ ...switchGame, hostFormats: { 'Nintendo Switch': 'DIGITAL' } });
      updateSwitchGame.mockReturnValue(of({ gameId: switchGame.id, title: switchGame.title, platform: 'Nintendo Switch', format: 'DIGITAL' }));
      getGame.mockReturnValue(of(changed));

      spectator.service.changeSwitchFormat('DIGITAL');

      expect(updateSwitchGame).toHaveBeenCalledWith(switchGame.id, { format: 'DIGITAL' });
      expect(spectator.service.game()).toEqual(changed);
      expect(spectator.service.savingSwitch()).toBe(false);
    });

    it('relinks to another IGDB game and reloads the game', () => {
      updateSwitchGame.mockReturnValue(of({ gameId: switchGame.id, title: 'Relinked', platform: 'Nintendo Switch', format: 'PHYSICAL' }));

      spectator.service.relinkSwitchGame(4321);

      expect(updateSwitchGame).toHaveBeenCalledWith(switchGame.id, { igdbGameId: 4321 });
      expect(getGame).toHaveBeenLastCalledWith(switchGame.id);
    });

    it('shows an error when saving a Switch change fails', () => {
      updateSwitchGame.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));

      spectator.service.changeSwitchFormat('DIGITAL');

      expect(spectator.service.error()).toBe('Failed to save the Switch game.');
      expect(spectator.service.savingSwitch()).toBe(false);
    });

    it('marks the game removed after deleting it', () => {
      // HttpClient emits null for the 204 No Content the endpoint returns.
      deleteSwitchGame.mockReturnValue(of(null as unknown as void));

      spectator.service.removeSwitchGame();

      expect(deleteSwitchGame).toHaveBeenCalledWith(switchGame.id);
      expect(spectator.service.removed()).toBe(true);
    });

    it('keeps the game and shows an error when removing fails', () => {
      deleteSwitchGame.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));

      spectator.service.removeSwitchGame();

      expect(spectator.service.removed()).toBe(false);
      expect(spectator.service.error()).toBe('Failed to remove the Switch game.');
    });

    it('searches relink candidates from three characters', () => {
      searchSwitchGames.mockReturnValue(of([aSwitchSearchResultMock()]));

      spectator.service.searchRelinkCandidates('Ma');
      expect(searchSwitchGames).not.toHaveBeenCalled();

      spectator.service.searchRelinkCandidates('Mario');
      expect(searchSwitchGames).toHaveBeenCalledWith('Mario');
      expect(spectator.service.relinkCandidates()).toEqual([aSwitchSearchResultMock()]);
    });
  });
});
