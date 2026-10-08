import { signal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import {
  aConsoleMock,
  aConsoleSearchResultMock,
  aKnownConsoleMock,
  ConsoleGameListItem,
  ConsoleImpact,
  ConsoleSearchResult,
  ConsoleStore,
  GameConsole,
  KnownConsole,
} from '@jordylab-fe/gamecatalog/api';
import { ConsolesComponent } from './consoles.component';

describe('ConsolesComponent', () => {
  const consoles = signal<GameConsole[]>([]);
  const loading = signal(false);
  const error = signal<string | null>(null);
  const suggestions = signal<KnownConsole[]>([]);
  const selectedId = signal<string | null>(null);
  const games = signal<ConsoleGameListItem[]>([]);
  const loadingGames = signal(false);
  const results = signal<ConsoleSearchResult[]>([]);
  const searching = signal(false);
  const noMatchFor = signal<string | null>(null);
  const busy = signal(false);
  const removal = signal<{ console: GameConsole; impact: ConsoleImpact } | null>(null);
  const lastAdded = signal<string | null>(null);

  const load = vi.fn();
  const suggest = vi.fn();
  const addConsole = vi.fn();
  const renameConsole = vi.fn();
  const requestRemoval = vi.fn();
  const confirmRemoval = vi.fn();
  const cancelRemoval = vi.fn();
  const selectConsole = vi.fn();
  const search = vi.fn();
  const addGame = vi.fn();
  const relinkGame = vi.fn();
  const removeGame = vi.fn();

  const storeMock = {
    consoles: consoles.asReadonly(),
    loading: loading.asReadonly(),
    error: error.asReadonly(),
    suggestions: suggestions.asReadonly(),
    selectedId: selectedId.asReadonly(),
    games: games.asReadonly(),
    loadingGames: loadingGames.asReadonly(),
    results: results.asReadonly(),
    searching: searching.asReadonly(),
    noMatchFor: noMatchFor.asReadonly(),
    busy: busy.asReadonly(),
    removal: removal.asReadonly(),
    lastAdded: lastAdded.asReadonly(),
    load,
    suggest,
    addConsole,
    renameConsole,
    requestRemoval,
    confirmRemoval,
    cancelRemoval,
    selectConsole,
    search,
    addGame,
    relinkGame,
    removeGame,
  };

  let spectator: Spectator<ConsolesComponent>;

  const createComponent = createComponentFactory({
    component: ConsolesComponent,
    providers: [provideRouter([]), { provide: ConsoleStore, useValue: storeMock }],
  });

  const dock = aConsoleMock();

  beforeEach(() => {
    [consoles, games, results].forEach((state) => state.set([]));
    [loading, loadingGames, searching, busy].forEach((state) => state.set(false));
    [error, noMatchFor, lastAdded, selectedId].forEach((state) => state.set(null));
    suggestions.set([]);
    removal.set(null);
    [load, suggest, addConsole, renameConsole, requestRemoval, confirmRemoval, cancelRemoval, selectConsole, search,
      addGame, relinkGame, removeGame].forEach((fn) => fn.mockReset());
    spectator = createComponent();
  });

  const withADock = () => {
    consoles.set([dock]);
    selectedId.set(dock.id);
    spectator.detectChanges();
  };

  it('loads the consoles and the well-known list when the page opens', () => {
    expect(load).toHaveBeenCalled();
    expect(suggest).toHaveBeenCalledWith('');
  });

  it('explains the empty state and disables adding a game until there is a console', () => {
    spectator.detectChanges();

    expect(spectator.query('[data-testid="no-consoles"]')).toHaveText('No consoles yet');
    expect(spectator.query('[data-testid="add-game-disabled"]')).toHaveText('add a console first');
    expect(spectator.query('[data-testid="add-game-disabled"] a')?.getAttribute('href')).toBe('#add-console');
    expect(spectator.query('[data-testid="game-search-input"]')).toBeNull();
  });

  it('lists the consoles with their platform chip, name and game count', () => {
    withADock();

    expect(spectator.query('[data-testid="console-name"]')).toHaveText('Switch dock');
    expect(spectator.query('[data-testid="console-list"] lib-platform-chip')).toHaveText('Nintendo Switch');
    expect(spectator.query('[data-testid="console-list"]')).toHaveText('3 games');
  });

  it('suggests well-known consoles while typing and adds a console with an optional name', () => {
    suggestions.set([aKnownConsoleMock({ name: 'Nintendo 64' })]);
    spectator.detectChanges();
    expect(spectator.queryAll('#known-consoles option').map((option) => option.getAttribute('value'))).toEqual(['Nintendo 64']);

    spectator.typeInElement('Nin', '[data-testid="console-platform-input"]');
    expect(suggest).toHaveBeenLastCalledWith('Nin');

    spectator.typeInElement('Nintendo 64', '[data-testid="console-platform-input"]');
    spectator.typeInElement('Den N64', '[data-testid="console-name-input"]');
    spectator.click('[data-testid="add-console-button"]');

    expect(addConsole).toHaveBeenCalledWith('Nintendo 64', 'Den N64');
  });

  it('does not add a console without a platform', () => {
    spectator.click('[data-testid="add-console-button"]');

    expect(addConsole).not.toHaveBeenCalled();
  });

  it('shows the store error in plain words', () => {
    error.set('That name is already used by another console or host.');
    spectator.detectChanges();

    expect(spectator.query('[data-testid="consoles-error"]')).toHaveText('already used');
  });

  it('searches for games on the chosen console and adds a result', () => {
    withADock();
    results.set([aConsoleSearchResultMock(), aConsoleSearchResultMock({ igdbGameId: 2, title: 'Celeste', alreadyOnConsole: true })]);
    spectator.detectChanges();

    spectator.typeInElement('mario', '[data-testid="game-search-input"]');
    expect(search).toHaveBeenCalledWith('mario');
    spectator.click('[data-testid="add-result-13427"]');
    expect(addGame).toHaveBeenCalledWith({ igdbGameId: 13427 });

    const already = spectator.query('[data-testid="add-result-2"]') as HTMLButtonElement;
    expect(already.disabled).toBe(true);
    expect(already).toHaveText('Already here');
  });

  it('offers to add by title when IGDB has no match, and always allows adding by title', () => {
    withADock();
    noMatchFor.set('Homebrew Thing');
    spectator.detectChanges();
    expect(spectator.query('[data-testid="no-match"]')).toHaveText('Homebrew Thing');
    spectator.click('[data-testid="no-match"] button');
    expect(addGame).toHaveBeenCalledWith({ title: 'Homebrew Thing' });

    spectator.typeInElement('Celeste', '[data-testid="add-by-title-input"]');
    spectator.click('[data-testid="add-by-title-button"]');
    expect(addGame).toHaveBeenCalledWith({ title: 'Celeste' });
  });

  it('has no format field anywhere', () => {
    withADock();

    expect(spectator.element).not.toHaveText('Format');
    expect(spectator.element).not.toHaveText('Physical');
    expect(spectator.element).not.toHaveText('Digital');
  });

  it('switches the console games are added to', () => {
    consoles.set([dock, aConsoleMock({ id: 'lite', name: 'Switch Lite', label: 'Switch Lite' })]);
    selectedId.set(dock.id);
    spectator.detectChanges();

    spectator.selectOption('[data-testid="console-picker"]', 'lite');

    expect(selectConsole).toHaveBeenCalledWith('lite');
  });

  it('links to the paste-a-list page of the selected console', () => {
    withADock();

    expect(spectator.query('[data-testid="bulk-link"]')?.getAttribute('href'))
      .toBe(`/games/consoles/${dock.id}/games/bulk`);
  });

  it('lists the games on the console and removes one', () => {
    withADock();
    games.set([{ gameId: 'g1', title: 'Celeste', releaseYear: 2018, coverUrl: null, coverEndpoint: null }]);
    spectator.detectChanges();

    expect(spectator.query('[data-testid="console-games"]')).toHaveText('Celeste');
    spectator.click('button[aria-label="Remove Celeste from Switch dock"]');
    expect(removeGame).toHaveBeenCalledWith('g1');
  });

  it('fixes a wrong match by searching for the right game', () => {
    withADock();
    games.set([{ gameId: 'g1', title: 'Mario Cart 8', releaseYear: null, coverUrl: null, coverEndpoint: null }]);
    results.set([aConsoleSearchResultMock()]);
    spectator.detectChanges();

    const fix = spectator.queryAll('[data-testid="console-games"] button').find((button) => button.textContent?.includes('Fix match'));
    spectator.click(fix as Element);
    spectator.click('[data-testid="relink-result-13427"]');

    expect(relinkGame).toHaveBeenCalledWith('g1', 13427);
  });

  it('renames a console inline', () => {
    withADock();

    const rename = spectator.queryAll('[data-testid="console-list"] button').find((button) => button.textContent?.includes('Rename'));
    spectator.click(rename as Element);
    spectator.typeInElement('Bedroom', 'input[aria-label="New name for Switch dock"]');
    spectator.dispatchKeyboardEvent('input[aria-label="New name for Switch dock"]', 'keydown', 'Enter');

    expect(renameConsole).toHaveBeenCalledWith(dock.id, 'Bedroom');
  });

  it('asks before removing a console and says what it would do', () => {
    withADock();
    spectator.click('[data-testid="remove-console-Switch dock"]');
    expect(requestRemoval).toHaveBeenCalledWith(dock);

    removal.set({ console: dock, impact: { games: 41, alsoElsewhere: 12, wouldBeRemoved: 29 } });
    spectator.detectChanges();

    const dialog = spectator.query('[data-testid="removal-dialog"]') as HTMLElement;
    expect(dialog).toHaveText('41 games');
    expect(dialog).toHaveText('29 games are removed');
    spectator.click('[data-testid="confirm-removal"]');
    expect(confirmRemoval).toHaveBeenCalled();
  });
});
