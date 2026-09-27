import { createServiceFactory, SpectatorService } from '@ngneat/spectator/vitest';
import { of, Subject, throwError } from 'rxjs';
import { GameCatalogApiService } from './gamecatalog-api.service';
import { GameLibraryStore } from './game-library.store';
import { GamesPage } from './gamecatalog.models';
import { aGameSummaryMock } from './mocks/game-summary.model.mock';
import { aGamesPageMock } from './mocks/games-page.model.mock';

describe('GameLibraryStore', () => {
  let spectator: SpectatorService<GameLibraryStore>;
  const getGames = vi.fn<GameCatalogApiService['getGames']>();
  const getPlatforms = vi.fn<GameCatalogApiService["getPlatforms"]>();
  const getHosts = vi.fn<GameCatalogApiService["getHosts"]>();

  const createService = createServiceFactory({
    service: GameLibraryStore,
    providers: [{ provide: GameCatalogApiService, useValue: { getGames, getPlatforms, getHosts } }],
  });

  beforeEach(() => {
    getGames.mockReset();
    getGames.mockReturnValue(of(aGamesPageMock()));
    getPlatforms.mockReset();
    getPlatforms.mockReturnValue(of(['SNES', 'Steam']));
    getHosts.mockReset();
    getHosts.mockReturnValue(of(['jordybox', 'ryzen-desktop']));
  });

  it('loads the first page and the platforms on construction', () => {
    spectator = createService();

    expect(getGames).toHaveBeenCalledWith({ search: undefined, platform: undefined, host: undefined, installStatus: 'INSTALLED', librarySource: undefined, page: 0, size: 60 });
    expect(spectator.service.games()).toEqual([aGameSummaryMock()]);
    expect(spectator.service.platforms()).toEqual(['SNES', 'Steam']);
    expect(spectator.service.totalPages()).toBe(1);
    expect(spectator.service.totalElements()).toBe(1);
    expect(spectator.service.loading()).toBe(false);
    expect(spectator.service.error()).toBeNull();
  });

  it('is loading until the first page arrives', () => {
    const pageSubject = new Subject<GamesPage>();
    getGames.mockReturnValue(pageSubject.asObservable());
    spectator = createService();

    expect(spectator.service.loading()).toBe(true);

    pageSubject.next(aGamesPageMock());

    expect(spectator.service.loading()).toBe(false);
  });

  it('sets an error message when loading games fails', () => {
    getGames.mockReturnValue(throwError(() => new Error('network error')));
    spectator = createService();

    expect(spectator.service.error()).toBe('Failed to load games.');
    expect(spectator.service.loading()).toBe(false);
    expect(spectator.service.games()).toEqual([]);
  });

  it('swallows a platforms failure into an empty list', () => {
    getPlatforms.mockReturnValue(throwError(() => new Error('network error')));
    spectator = createService();

    expect(spectator.service.platforms()).toEqual([]);
    expect(spectator.service.error()).toBeNull();
  });

  it('keeps the previous games when a reload fails', () => {
    spectator = createService();
    getGames.mockReturnValue(throwError(() => new Error('network error')));

    spectator.service.goToPage(1);

    expect(spectator.service.error()).toBe('Failed to load games.');
    expect(spectator.service.games()).toEqual([aGameSummaryMock()]);
  });

  it('cancels a stale request when a newer one starts', () => {
    const first = new Subject<GamesPage>();
    const second = new Subject<GamesPage>();
    getGames.mockReturnValueOnce(first.asObservable()).mockReturnValueOnce(second.asObservable());
    spectator = createService();

    spectator.service.goToPage(1);
    second.next(aGamesPageMock({ content: [aGameSummaryMock({ title: 'Metroid' })] }));
    first.next(aGamesPageMock({ content: [aGameSummaryMock({ title: 'Stale' })] }));

    expect(spectator.service.games()[0].title).toBe('Metroid');
  });

  describe('search', () => {
    beforeEach(() => {
      vi.useFakeTimers();
      spectator = createService();
      getGames.mockClear();
    });

    afterEach(() => {
      vi.useRealTimers();
    });

    it('debounces the term before reloading', () => {
      spectator.service.search('mario');
      vi.advanceTimersByTime(299);
      expect(getGames).not.toHaveBeenCalled();

      vi.advanceTimersByTime(1);

      expect(getGames).toHaveBeenCalledWith({ search: 'mario', platform: undefined, host: undefined, installStatus: 'INSTALLED', librarySource: undefined, page: 0, size: 60 });
      expect(spectator.service.searchTerm()).toBe('mario');
    });

    it('only searches for the last term typed within the debounce window', () => {
      spectator.service.search('mar');
      vi.advanceTimersByTime(100);
      spectator.service.search('mario');
      vi.advanceTimersByTime(300);

      expect(getGames).toHaveBeenCalledTimes(1);
      expect(getGames).toHaveBeenCalledWith(expect.objectContaining({ search: 'mario' }));
    });

    it('ignores a repeated term', () => {
      spectator.service.search('mario');
      vi.advanceTimersByTime(300);
      getGames.mockClear();

      spectator.service.search('mario');
      vi.advanceTimersByTime(300);

      expect(getGames).not.toHaveBeenCalled();
    });

    it('resets the page to the first one', () => {
      spectator.service.goToPage(2);
      spectator.service.search('mario');
      vi.advanceTimersByTime(300);

      expect(spectator.service.page()).toBe(0);
      expect(getGames).toHaveBeenLastCalledWith({ search: 'mario', platform: undefined, host: undefined, installStatus: 'INSTALLED', librarySource: undefined, page: 0, size: 60 });
    });
  });

  it('filters by platform and resets the page', () => {
    spectator = createService();
    spectator.service.goToPage(2);

    spectator.service.selectPlatform('SNES');

    expect(spectator.service.selectedPlatform()).toBe('SNES');
    expect(spectator.service.page()).toBe(0);
    expect(getGames).toHaveBeenLastCalledWith({ search: undefined, platform: 'SNES', host: undefined, installStatus: 'INSTALLED', librarySource: undefined, page: 0, size: 60 });
  });

  it('clears the platform filter when null is selected', () => {
    spectator = createService();
    spectator.service.selectPlatform('SNES');

    spectator.service.selectPlatform(null);

    expect(spectator.service.selectedPlatform()).toBeNull();
    expect(getGames).toHaveBeenLastCalledWith({ search: undefined, platform: undefined, host: undefined, installStatus: 'INSTALLED', librarySource: undefined, page: 0, size: 60 });
  });

  it('navigates to the requested page', () => {
    getGames.mockReturnValue(of(aGamesPageMock({ totalPages: 3, totalElements: 150 })));
    spectator = createService();

    spectator.service.goToPage(1);

    expect(spectator.service.page()).toBe(1);
    expect(spectator.service.totalPages()).toBe(3);
    expect(spectator.service.totalElements()).toBe(150);
    expect(getGames).toHaveBeenLastCalledWith({ search: undefined, platform: undefined, host: undefined, installStatus: 'INSTALLED', librarySource: undefined, page: 1, size: 60 });
  });

  it('exposes the hosts loaded on construction', () => {
    spectator = createService();

    expect(spectator.service.hosts()).toEqual(['jordybox', 'ryzen-desktop']);
  });

  it('filters by host and resets the page', () => {
    spectator = createService();
    spectator.service.goToPage(2);

    spectator.service.selectHost('jordybox');

    expect(spectator.service.selectedHost()).toBe('jordybox');
    expect(spectator.service.page()).toBe(0);
    expect(getGames).toHaveBeenLastCalledWith({ search: undefined, platform: undefined, host: 'jordybox', installStatus: 'INSTALLED', librarySource: undefined, page: 0, size: 60 });
  });

  it('clears the host filter when null is selected', () => {
    spectator = createService();
    spectator.service.selectHost('jordybox');

    spectator.service.selectHost(null);

    expect(spectator.service.selectedHost()).toBeNull();
    expect(getGames).toHaveBeenLastCalledWith({ search: undefined, platform: undefined, host: undefined, installStatus: 'INSTALLED', librarySource: undefined, page: 0, size: 60 });
  });

  it('toggles the local multiplayer filter and resets the page', () => {
    spectator = createService();
    spectator.service.goToPage(2);

    spectator.service.toggleLocalMultiplayerOnly();

    expect(spectator.service.localMultiplayerOnly()).toBe(true);
    expect(spectator.service.page()).toBe(0);
    expect(getGames).toHaveBeenLastCalledWith(
      expect.objectContaining({ localMultiplayer: true, page: 0 })
    );
  });
});
