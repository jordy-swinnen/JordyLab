import { HttpErrorResponse } from '@angular/common/http';
import { createServiceFactory, SpectatorService } from '@ngneat/spectator/vitest';
import { of, throwError } from 'rxjs';
import { ConsoleStore } from './console.store';
import { GameCatalogApiService } from './gamecatalog-api.service';
import { aConsoleMock } from './mocks/console.model.mock';
import { aConsoleSearchResultMock } from './mocks/console-search-result.model.mock';
import { aKnownConsoleMock } from './mocks/known-console.model.mock';

describe('ConsoleStore', () => {
  let spectator: SpectatorService<ConsoleStore>;
  const getConsoles = vi.fn<GameCatalogApiService['getConsoles']>();
  const getKnownConsoles = vi.fn<GameCatalogApiService['getKnownConsoles']>();
  const addConsole = vi.fn<GameCatalogApiService['addConsole']>();
  const renameConsole = vi.fn<GameCatalogApiService['renameConsole']>();
  const getConsoleImpact = vi.fn<GameCatalogApiService['getConsoleImpact']>();
  const removeConsole = vi.fn<GameCatalogApiService['removeConsole']>();
  const getConsoleGames = vi.fn<GameCatalogApiService['getConsoleGames']>();
  const searchConsoleGames = vi.fn<GameCatalogApiService['searchConsoleGames']>();
  const addConsoleGame = vi.fn<GameCatalogApiService['addConsoleGame']>();
  const relinkConsoleGame = vi.fn<GameCatalogApiService['relinkConsoleGame']>();
  const removeConsoleGame = vi.fn<GameCatalogApiService['removeConsoleGame']>();
  const previewConsoleBulk = vi.fn<GameCatalogApiService['previewConsoleBulk']>();
  const confirmConsoleBulk = vi.fn<GameCatalogApiService['confirmConsoleBulk']>();

  const createService = createServiceFactory({
    service: ConsoleStore,
    providers: [
      {
        provide: GameCatalogApiService,
        useValue: {
          getConsoles,
          getKnownConsoles,
          addConsole,
          renameConsole,
          getConsoleImpact,
          removeConsole,
          getConsoleGames,
          searchConsoleGames,
          addConsoleGame,
          relinkConsoleGame,
          removeConsoleGame,
          previewConsoleBulk,
          confirmConsoleBulk,
        },
      },
    ],
  });

  const dock = aConsoleMock();
  const lite = aConsoleMock({ id: 'lite-id', name: 'Switch Lite', label: 'Switch Lite', gameCount: 0 });

  afterEach(() => vi.useRealTimers());

  beforeEach(() => {
    vi.useFakeTimers();
    [
      getConsoles, getKnownConsoles, addConsole, renameConsole, getConsoleImpact, removeConsole, getConsoleGames,
      searchConsoleGames, addConsoleGame, relinkConsoleGame, removeConsoleGame, previewConsoleBulk, confirmConsoleBulk,
    ].forEach((fn) => fn.mockReset());
    getConsoles.mockReturnValue(of([dock, lite]));
    getConsoleGames.mockReturnValue(of([]));
    spectator = createService();
  });

  function httpError(status: number, reason: string): HttpErrorResponse {
    return new HttpErrorResponse({ status, error: { reason } });
  }

  it('loads the consoles and opens the first one', () => {
    spectator.service.load();

    expect(spectator.service.consoles()).toEqual([dock, lite]);
    expect(spectator.service.selectedId()).toBe(dock.id);
    expect(getConsoleGames).toHaveBeenCalledWith(dock.id);
    expect(spectator.service.hasConsoles()).toBe(true);
    expect(spectator.service.loading()).toBe(false);
  });

  it('has no selection when there are no consoles', () => {
    getConsoles.mockReturnValue(of([]));

    spectator.service.load();

    expect(spectator.service.hasConsoles()).toBe(false);
    expect(spectator.service.selectedId()).toBeNull();
  });

  it('reports a failed load', () => {
    getConsoles.mockReturnValue(throwError(() => new Error('down')));

    spectator.service.load();

    expect(spectator.service.error()).toBe('Failed to load the consoles.');
    expect(spectator.service.loading()).toBe(false);
  });

  it('suggests well-known consoles after a short pause', () => {
    getKnownConsoles.mockReturnValue(of([aKnownConsoleMock()]));

    spectator.service.suggest('nin');
    vi.advanceTimersByTime(250);

    expect(getKnownConsoles).toHaveBeenCalledWith('nin');
    expect(spectator.service.suggestions()).toEqual([aKnownConsoleMock()]);
  });

  it('adds a console, keeps the list sorted and selects the new one', () => {
    spectator.service.load();
    const added = aConsoleMock({ id: 'new-id', name: 'Amiga', label: 'Amiga', platform: 'Amiga CD32', gameCount: 0 });
    addConsole.mockReturnValue(of(added));

    spectator.service.addConsole(' Amiga CD32 ', ' Amiga ');

    expect(addConsole).toHaveBeenCalledWith('Amiga CD32', 'Amiga');
    expect(spectator.service.consoles().map((console) => console.name)).toEqual(['Amiga', 'Switch dock', 'Switch Lite']);
    expect(spectator.service.selectedId()).toBe('new-id');
  });

  it('adds a console under the platform name when no name is given', () => {
    addConsole.mockReturnValue(of(dock));

    spectator.service.addConsole('Nintendo Switch', '  ');

    expect(addConsole).toHaveBeenCalledWith('Nintendo Switch', null);
  });

  it('explains a taken or too long name in plain words', () => {
    addConsole.mockReturnValueOnce(throwError(() => httpError(409, 'NAME_TAKEN')));
    spectator.service.addConsole('Nintendo Switch', 'Switch dock');
    expect(spectator.service.error()).toBe('That name is already used by another console or host.');

    addConsole.mockReturnValueOnce(throwError(() => httpError(400, 'NAME_TOO_LONG')));
    spectator.service.addConsole('Nintendo Switch', 'x'.repeat(41));
    expect(spectator.service.error()).toBe('Names can be at most 40 characters.');
    expect(spectator.service.busy()).toBe(false);
  });

  it('renames a console in place', () => {
    spectator.service.load();
    renameConsole.mockReturnValue(of({ ...dock, name: 'Bedroom', label: 'Bedroom' }));

    spectator.service.renameConsole(dock.id, ' Bedroom ');

    expect(renameConsole).toHaveBeenCalledWith(dock.id, 'Bedroom');
    expect(spectator.service.consoles()[0].name).toBe('Bedroom');
  });

  it('shows what a removal would do before removing, and removing selects another console', () => {
    spectator.service.load();
    getConsoleImpact.mockReturnValue(of({ games: 41, alsoElsewhere: 12, wouldBeRemoved: 29 }));
    removeConsole.mockReturnValue(of(null as unknown as void));

    spectator.service.requestRemoval(dock);
    expect(spectator.service.removal()?.impact.wouldBeRemoved).toBe(29);

    spectator.service.confirmRemoval();

    expect(removeConsole).toHaveBeenCalledWith(dock.id);
    expect(spectator.service.removal()).toBeNull();
    expect(spectator.service.consoles()).toEqual([lite]);
    expect(spectator.service.selectedId()).toBe(lite.id);
  });

  it('can cancel a removal without calling the server', () => {
    getConsoleImpact.mockReturnValue(of({ games: 1, alsoElsewhere: 0, wouldBeRemoved: 1 }));
    spectator.service.requestRemoval(dock);

    spectator.service.cancelRemoval();

    expect(spectator.service.removal()).toBeNull();
    expect(removeConsole).not.toHaveBeenCalled();
  });

  it('searches IGDB on the selected console after a pause and ignores very short queries', () => {
    spectator.service.load();
    searchConsoleGames.mockReturnValue(of([aConsoleSearchResultMock()]));

    spectator.service.search('m');
    vi.advanceTimersByTime(350);
    expect(searchConsoleGames).not.toHaveBeenCalled();

    spectator.service.search('mario');
    vi.advanceTimersByTime(350);

    expect(searchConsoleGames).toHaveBeenCalledWith(dock.id, 'mario');
    expect(spectator.service.results()).toEqual([aConsoleSearchResultMock()]);
    expect(spectator.service.noMatchFor()).toBeNull();
  });

  it('offers to add by title when a finished search found nothing', () => {
    spectator.service.load();
    searchConsoleGames.mockReturnValue(of([]));

    spectator.service.search('Homebrew Thing');
    vi.advanceTimersByTime(350);

    expect(spectator.service.noMatchFor()).toBe('Homebrew Thing');
  });

  it('adds a game by IGDB id, remembers its title, refreshes the list and bumps the count', () => {
    spectator.service.load();
    addConsoleGame.mockReturnValue(of({ gameId: 'g1', title: 'Mario Kart 8 Deluxe', linkedExisting: false }));

    spectator.service.addGame({ igdbGameId: 13427 });

    expect(addConsoleGame).toHaveBeenCalledWith(dock.id, { igdbGameId: 13427 });
    expect(spectator.service.lastAdded()).toBe('Mario Kart 8 Deluxe');
    expect(spectator.service.consoles()[0].gameCount).toBe(4);
    expect(getConsoleGames).toHaveBeenCalledTimes(2);
  });

  it('explains a game that is already on the console', () => {
    spectator.service.load();
    addConsoleGame.mockReturnValue(throwError(() => httpError(409, 'ALREADY_ON_CONSOLE')));

    spectator.service.addGame({ title: 'Celeste' });

    expect(spectator.service.error()).toBe('That game is already on this console.');
  });

  it('removes a game from the console and lowers the count', () => {
    spectator.service.load();
    getConsoleGames.mockReturnValue(of([{ gameId: 'g1', title: 'Celeste', releaseYear: 2018, coverUrl: null, coverEndpoint: null }]));
    spectator.service.selectConsole(dock.id);
    removeConsoleGame.mockReturnValue(of(null as unknown as void));

    spectator.service.removeGame('g1');

    expect(removeConsoleGame).toHaveBeenCalledWith(dock.id, 'g1');
    expect(spectator.service.games()).toEqual([]);
    expect(spectator.service.consoles()[0].gameCount).toBe(2);
  });

  it('relinks a game and reloads', () => {
    spectator.service.load();
    relinkConsoleGame.mockReturnValue(of({ gameId: 'g1', title: 'Mario Kart 8 Deluxe', linkedExisting: false }));

    spectator.service.relinkGame('g1', 13427);

    expect(relinkConsoleGame).toHaveBeenCalledWith(dock.id, 'g1', 13427);
  });

  it('reviews a pasted list one title per line, ignoring blank lines, then adds the ticked ones', () => {
    spectator.service.load();
    previewConsoleBulk.mockReturnValue(of([{ line: 'Celeste', status: 'NO_MATCH', match: null }]));
    confirmConsoleBulk.mockReturnValue(of({ added: [], skipped: ['Celeste'], failed: [] }));

    spectator.service.previewBulk('Celeste\n\n  Hades  \r\n');
    expect(previewConsoleBulk).toHaveBeenCalledWith(dock.id, ['Celeste', 'Hades']);
    expect(spectator.service.bulkLines()).toHaveLength(1);

    spectator.service.confirmBulk([{ line: 'Celeste', igdbGameId: null, title: 'Celeste' }]);

    expect(spectator.service.bulkSummary()?.skipped).toEqual(['Celeste']);
    expect(spectator.service.bulkLines()).toEqual([]);
  });

  it('does nothing for an empty pasted list', () => {
    spectator.service.load();

    spectator.service.previewBulk(' \n \n');

    expect(previewConsoleBulk).not.toHaveBeenCalled();
  });
});
