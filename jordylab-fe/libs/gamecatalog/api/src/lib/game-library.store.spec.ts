import { DOCUMENT } from '@angular/common';
import {
  createServiceFactory,
  SpectatorService,
} from '@ngneat/spectator/vitest';
import { of, Subject, throwError } from 'rxjs';
import { GameCatalogApiService, GamesQuery } from './gamecatalog-api.service';
import { GameLibraryStore, QueryReader } from './game-library.store';
import { GamesPage } from './gamecatalog.models';
import { aGameSummaryMock } from './mocks/game-summary.model.mock';
import { aGamesPageMock } from './mocks/games-page.model.mock';
import { aPlaceOptionMock } from './mocks/place-option.model.mock';
import { aPlatformChipMock } from './mocks/platform-chip.model.mock';

const DEFAULT_QUERY: GamesQuery = {
  search: undefined,
  platform: [],
  where: [],
  installStatus: 'INSTALLED',
  source: [],
  minLocalPlayers: undefined,
  romStatus: [],
  mark: [],
  markScope: 'ALL',
  sort: 'TITLE',
  page: 0,
  size: 60,
};

function queryReader(params: Record<string, string[]>): QueryReader {
  return {
    get: (name) => params[name]?.[0] ?? null,
    getAll: (name) => params[name] ?? [],
  };
}

describe('GameLibraryStore', () => {
  let spectator: SpectatorService<GameLibraryStore>;
  const getGames = vi.fn<GameCatalogApiService['getGames']>();
  const getPlatforms = vi.fn<GameCatalogApiService['getPlatforms']>();
  const getPlaces = vi.fn<GameCatalogApiService['getPlaces']>();
  const livingRoom = aPlaceOptionMock();
  const platformChips = [
    aPlatformChipMock(),
    aPlatformChipMock({ name: 'Steam', family: 'STEAM' }),
  ];

  const createService = createServiceFactory({
    service: GameLibraryStore,
    providers: [
      {
        provide: GameCatalogApiService,
        useValue: { getGames, getPlatforms, getPlaces },
      },
    ],
  });

  const lastQuery = () => getGames.mock.lastCall?.[0];

  beforeEach(() => {
    getGames.mockReset();
    getGames.mockReturnValue(of(aGamesPageMock()));
    getPlatforms.mockReset();
    getPlatforms.mockReturnValue(of(platformChips));
    getPlaces.mockReset();
    getPlaces.mockReturnValue(of([livingRoom]));
  });

  it('loads the first page, the platforms and the places on construction', () => {
    spectator = createService();

    expect(getGames).toHaveBeenCalledWith(DEFAULT_QUERY);
    expect(spectator.service.games()).toEqual([aGameSummaryMock()]);
    expect(spectator.service.platforms()).toEqual(platformChips);
    expect(spectator.service.places()).toEqual([livingRoom]);
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

  it('swallows a platforms or places failure into an empty list', () => {
    getPlatforms.mockReturnValue(throwError(() => new Error('network error')));
    getPlaces.mockReturnValue(throwError(() => new Error('network error')));
    spectator = createService();

    expect(spectator.service.platforms()).toEqual([]);
    expect(spectator.service.places()).toEqual([]);
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
    getGames
      .mockReturnValueOnce(first.asObservable())
      .mockReturnValueOnce(second.asObservable());
    spectator = createService();

    spectator.service.goToPage(1);
    second.next(
      aGamesPageMock({ content: [aGameSummaryMock({ title: 'Metroid' })] }),
    );
    first.next(
      aGamesPageMock({ content: [aGameSummaryMock({ title: 'Stale' })] }),
    );

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

      expect(getGames).toHaveBeenCalledWith({
        ...DEFAULT_QUERY,
        search: 'mario',
      });
      expect(spectator.service.searchTerm()).toBe('mario');
    });

    it('only searches for the last term typed within the debounce window', () => {
      spectator.service.search('mar');
      vi.advanceTimersByTime(100);
      spectator.service.search('mario');
      vi.advanceTimersByTime(300);

      expect(getGames).toHaveBeenCalledTimes(1);
      expect(getGames).toHaveBeenCalledWith(
        expect.objectContaining({ search: 'mario' }),
      );
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
      expect(lastQuery()).toEqual({ ...DEFAULT_QUERY, search: 'mario' });
    });
  });

  describe('filters', () => {
    beforeEach(() => {
      spectator = createService();
    });

    it('adds and removes platforms one by one and resets the page', () => {
      spectator.service.goToPage(2);

      spectator.service.togglePlatform('SNES');
      spectator.service.togglePlatform('Steam');

      expect(spectator.service.selectedPlatforms()).toEqual(['SNES', 'Steam']);
      expect(spectator.service.page()).toBe(0);
      expect(lastQuery()?.platform).toEqual(['SNES', 'Steam']);

      spectator.service.togglePlatform('SNES');

      expect(lastQuery()?.platform).toEqual(['Steam']);
    });

    it('filters by place id', () => {
      spectator.service.togglePlace(livingRoom.id);

      expect(lastQuery()?.where).toEqual([livingRoom.id]);
    });

    it('changes the install status', () => {
      spectator.service.selectInstallStatus('NOT_INSTALLED');

      expect(spectator.service.selectedInstallStatus()).toBe('NOT_INSTALLED');
      expect(lastQuery()?.installStatus).toBe('NOT_INSTALLED');
    });

    it('filters by source, rom status and marks', () => {
      spectator.service.toggleSource('EMULATED');
      spectator.service.toggleRomStatus('BROKEN');
      spectator.service.toggleMark('WANT_TO_PLAY');
      spectator.service.selectMarkScope('MINE');

      expect(lastQuery()).toEqual({
        ...DEFAULT_QUERY,
        source: ['EMULATED'],
        romStatus: ['BROKEN'],
        mark: ['WANT_TO_PLAY'],
        markScope: 'MINE',
      });
    });

    it('asks for a minimum number of local players between two and eight', () => {
      spectator.service.setMinLocalPlayers(6);
      expect(lastQuery()?.minLocalPlayers).toBe(6);

      spectator.service.setMinLocalPlayers(12);
      expect(spectator.service.minLocalPlayers()).toBe(8);

      spectator.service.setMinLocalPlayers(1);
      expect(spectator.service.minLocalPlayers()).toBeNull();
      expect(lastQuery()?.minLocalPlayers).toBeUndefined();
    });

    it('sorts', () => {
      spectator.service.selectSort('MOST_LIKED');

      expect(lastQuery()?.sort).toBe('MOST_LIKED');
    });

    it('navigates to the requested page', () => {
      getGames.mockReturnValue(
        of(aGamesPageMock({ totalPages: 3, totalElements: 150 })),
      );

      spectator.service.goToPage(1);

      expect(spectator.service.page()).toBe(1);
      expect(spectator.service.totalPages()).toBe(3);
      expect(spectator.service.totalElements()).toBe(150);
      expect(lastQuery()?.page).toBe(1);
    });

    it('reports the games left out because their player count is unknown', () => {
      getGames.mockReturnValue(of(aGamesPageMock({ unknownPlayerCount: 31 })));

      spectator.service.setMinLocalPlayers(4);

      expect(spectator.service.unknownPlayerCount()).toBe(31);
    });
  });

  describe('active filters', () => {
    beforeEach(() => {
      spectator = createService();
    });

    it('shows the default installed status as a real chip', () => {
      expect(
        spectator.service.activeFilters().map((chip) => [chip.key, chip.value]),
      ).toEqual([['Status', 'Installed']]);
    });

    it('lists every filter in the order of the panel with readable values', () => {
      spectator.service.togglePlatform('SNES');
      spectator.service.togglePlace(livingRoom.id);
      spectator.service.toggleSource('STEAM_FAMILY');
      spectator.service.setMinLocalPlayers(4);
      spectator.service.toggleRomStatus('VALIDATED');
      spectator.service.toggleMark('PLAYED_LIKED');
      spectator.service.selectMarkScope('MINE');
      spectator.service.selectSort('MOST_WANTED');

      expect(
        spectator.service.activeFilters().map((chip) => [chip.key, chip.value]),
      ).toEqual([
        ['Platform', 'SNES'],
        ['Where', 'Living room PC'],
        ['Status', 'Installed'],
        ['Source', 'Steam (Family)'],
        ['Players', '4+'],
        ['ROM', 'Validated'],
        ['My marks', 'Played & liked'],
        ['Sort', 'Most wanted'],
      ]);
    });

    it('removes just the filter of the chip', () => {
      spectator.service.togglePlatform('SNES');
      spectator.service.togglePlatform('Steam');

      spectator.service
        .activeFilters()
        .find((chip) => chip.value === 'SNES')
        ?.remove();

      expect(spectator.service.selectedPlatforms()).toEqual(['Steam']);
    });

    it('removing the status chip shows every game', () => {
      spectator.service
        .activeFilters()
        .find((chip) => chip.key === 'Status')
        ?.remove();

      expect(spectator.service.selectedInstallStatus()).toBe('ALL');
      expect(spectator.service.activeFilters()).toEqual([]);
    });

    it('clears everything including the default status but keeps the search', () => {
      vi.useFakeTimers();
      spectator.service.search('mario');
      vi.advanceTimersByTime(300);
      vi.useRealTimers();
      spectator.service.togglePlatform('SNES');
      spectator.service.setMinLocalPlayers(4);

      spectator.service.clearAll();

      expect(spectator.service.activeFilters()).toEqual([]);
      expect(lastQuery()).toEqual({
        ...DEFAULT_QUERY,
        search: 'mario',
        installStatus: 'ALL',
      });
    });
  });

  describe('address bar', () => {
    beforeEach(() => {
      spectator = createService();
    });

    it('writes nothing for the default filters', () => {
      expect(
        Object.values(spectator.service.queryParams()).every(
          (value) => value === null,
        ),
      ).toBe(true);
    });

    it('writes every filter that is in use', () => {
      spectator.service.togglePlatform('SNES');
      spectator.service.selectInstallStatus('ALL');
      spectator.service.setMinLocalPlayers(4);
      spectator.service.toggleMark('WANT_TO_PLAY');
      spectator.service.selectMarkScope('MINE');
      spectator.service.selectSort('MOST_LIKED');
      spectator.service.goToPage(2);

      expect(spectator.service.queryParams()).toEqual({
        q: null,
        platform: ['SNES'],
        where: null,
        status: 'ALL',
        source: null,
        players: '4',
        rom: null,
        mark: ['WANT_TO_PLAY'],
        scope: 'MINE',
        sort: 'MOST_LIKED',
        page: '2',
      });
    });

    it('restores the filters from the address bar and loads once', () => {
      getGames.mockClear();

      spectator.service.applyQuery(
        queryReader({
          q: ['mario'],
          platform: ['SNES', 'Steam'],
          status: ['ALL'],
          source: ['EMULATED', 'bogus'],
          players: ['6'],
          mark: ['PLAYED_LIKED'],
          scope: ['MINE'],
          sort: ['MOST_WANTED'],
          page: ['3'],
        }),
      );

      expect(getGames).toHaveBeenCalledTimes(1);
      expect(lastQuery()).toEqual({
        ...DEFAULT_QUERY,
        search: 'mario',
        platform: ['SNES', 'Steam'],
        installStatus: 'ALL',
        source: ['EMULATED'],
        minLocalPlayers: 6,
        mark: ['PLAYED_LIKED'],
        markScope: 'MINE',
        sort: 'MOST_WANTED',
        page: 3,
      });
    });

    it('does not reload when the address bar already matches the filters', () => {
      getGames.mockClear();

      spectator.service.applyQuery(queryReader({}));

      expect(getGames).not.toHaveBeenCalled();
    });

    it('falls back to the defaults for values it does not know', () => {
      spectator.service.applyQuery(
        queryReader({
          status: ['SOMETHING'],
          players: ['99'],
          sort: ['RANDOM'],
          page: ['-4'],
        }),
      );

      expect(spectator.service.selectedInstallStatus()).toBe('INSTALLED');
      expect(spectator.service.minLocalPlayers()).toBeNull();
      expect(spectator.service.sort()).toBe('TITLE');
      expect(spectator.service.page()).toBe(0);
    });
  });

  describe('coming back to the page', () => {
    const tab = Object.assign(new EventTarget(), { visibilityState: 'visible' });

    it('looks at the votes again when the tab becomes visible', () => {
      spectator = createService({ providers: [{ provide: DOCUMENT, useValue: tab }] });
      getGames.mockClear();

      tab.dispatchEvent(new Event('visibilitychange'));

      expect(getGames).toHaveBeenCalledTimes(1);
    });

    it('does nothing while the tab goes into the background', () => {
      spectator = createService({ providers: [{ provide: DOCUMENT, useValue: tab }] });
      getGames.mockClear();
      tab.visibilityState = 'hidden';

      tab.dispatchEvent(new Event('visibilitychange'));
      tab.visibilityState = 'visible';

      expect(getGames).not.toHaveBeenCalled();
    });
  });
});
