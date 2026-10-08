import { signal } from '@angular/core';
import { Router, RouterModule } from '@angular/router';
import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import {
  ActiveFilter,
  aGameSummaryMock,
  aPlaceOptionMock,
  aPlatformChipMock,
  GameLibraryStore,
  GameSort,
  GameSource,
  GameSummary,
  InstallStatus,
  MarkScope,
  MarkStore,
  MarkType,
  PlaceOption,
  PlatformChip,
  RomStatus,
} from '@jordylab-fe/gamecatalog/api';
import { GameGridComponent } from './game-grid.component';

describe('GameGridComponent', () => {
  const games = signal<GameSummary[]>([]);
  const platforms = signal<PlatformChip[]>([]);
  const places = signal<PlaceOption[]>([]);
  const loading = signal(true);
  const error = signal<string | null>(null);
  const searchTerm = signal('');
  const selectedPlatforms = signal<string[]>([]);
  const selectedPlaces = signal<string[]>([]);
  const selectedInstallStatus = signal<InstallStatus>('INSTALLED');
  const selectedSources = signal<GameSource[]>([]);
  const minLocalPlayers = signal<number | null>(null);
  const selectedRomStatuses = signal<RomStatus[]>([]);
  const selectedMarks = signal<MarkType[]>([]);
  const markScope = signal<MarkScope>('ALL');
  const sort = signal<GameSort>('TITLE');
  const activeFilters = signal<ActiveFilter[]>([]);
  const unknownPlayerCount = signal<number | null>(null);
  const queryParams = signal<Record<string, string | string[] | null>>({});
  const markPendingIds = signal<ReadonlySet<string>>(new Set());
  const markError = signal<string | null>(null);
  const page = signal(0);
  const totalPages = signal(0);
  const totalElements = signal(0);

  const search = vi.fn<GameLibraryStore['search']>();
  const togglePlatform = vi.fn<GameLibraryStore['togglePlatform']>();
  const togglePlace = vi.fn<GameLibraryStore['togglePlace']>();
  const selectInstallStatus = vi.fn<GameLibraryStore['selectInstallStatus']>();
  const toggleSource = vi.fn<GameLibraryStore['toggleSource']>();
  const setMinLocalPlayers = vi.fn<GameLibraryStore['setMinLocalPlayers']>();
  const toggleRomStatus = vi.fn<GameLibraryStore['toggleRomStatus']>();
  const toggleMark = vi.fn<GameLibraryStore['toggleMark']>();
  const selectMarkScope = vi.fn<GameLibraryStore['selectMarkScope']>();
  const selectSort = vi.fn<GameLibraryStore['selectSort']>();
  const clearAll = vi.fn<GameLibraryStore['clearAll']>();
  const applyQuery = vi.fn<GameLibraryStore['applyQuery']>();
  const goToPage = vi.fn<GameLibraryStore['goToPage']>();

  const storeMock = {
    games: games.asReadonly(),
    platforms: platforms.asReadonly(),
    places: places.asReadonly(),
    loading: loading.asReadonly(),
    error: error.asReadonly(),
    searchTerm: searchTerm.asReadonly(),
    selectedPlatforms: selectedPlatforms.asReadonly(),
    selectedPlaces: selectedPlaces.asReadonly(),
    selectedInstallStatus: selectedInstallStatus.asReadonly(),
    selectedSources: selectedSources.asReadonly(),
    minLocalPlayers: minLocalPlayers.asReadonly(),
    selectedRomStatuses: selectedRomStatuses.asReadonly(),
    selectedMarks: selectedMarks.asReadonly(),
    markScope: markScope.asReadonly(),
    sort: sort.asReadonly(),
    activeFilters: activeFilters.asReadonly(),
    unknownPlayerCount: unknownPlayerCount.asReadonly(),
    queryParams: queryParams.asReadonly(),
    page: page.asReadonly(),
    totalPages: totalPages.asReadonly(),
    totalElements: totalElements.asReadonly(),
    search,
    togglePlatform,
    togglePlace,
    selectInstallStatus,
    toggleSource,
    setMinLocalPlayers,
    toggleRomStatus,
    toggleMark,
    selectMarkScope,
    selectSort,
    clearAll,
    applyQuery,
    goToPage,
  };

  const toggleGameMark = vi.fn<MarkStore['toggle']>();
  const dismissMarkError = vi.fn<MarkStore['dismissError']>();
  const markStoreMock = {
    stateOf: <T>(game: T) => game,
    pending: markPendingIds.asReadonly(),
    error: markError.asReadonly(),
    toggle: toggleGameMark,
    dismissError: dismissMarkError,
  };

  let spectator: Spectator<GameGridComponent>;

  const createComponent = createComponentFactory({
    component: GameGridComponent,
    imports: [RouterModule.forRoot([])],
    providers: [
      { provide: GameLibraryStore, useValue: storeMock },
      { provide: MarkStore, useValue: markStoreMock },
    ],
  });

  const aFilter = (overrides: Partial<ActiveFilter> = {}): ActiveFilter => ({
    id: 'platform:SNES',
    key: 'Platform',
    value: 'SNES',
    remove: vi.fn(),
    ...overrides,
  });

  beforeEach(() => {
    games.set([]);
    platforms.set([aPlatformChipMock({ name: 'SNES' }), aPlatformChipMock({ name: 'Steam', family: 'STEAM' })]);
    places.set([aPlaceOptionMock({ id: 'host-1', label: 'Living room PC' })]);
    loading.set(true);
    error.set(null);
    searchTerm.set('');
    selectedPlatforms.set([]);
    selectedPlaces.set([]);
    selectedInstallStatus.set('INSTALLED');
    selectedSources.set([]);
    minLocalPlayers.set(null);
    selectedRomStatuses.set([]);
    selectedMarks.set([]);
    markScope.set('ALL');
    sort.set('TITLE');
    activeFilters.set([]);
    unknownPlayerCount.set(null);
    queryParams.set({});
    markPendingIds.set(new Set());
    markError.set(null);
    toggleGameMark.mockReset();
    dismissMarkError.mockReset();
    page.set(0);
    totalPages.set(0);
    totalElements.set(0);
    [
      search,
      togglePlatform,
      togglePlace,
      selectInstallStatus,
      toggleSource,
      setMinLocalPlayers,
      toggleRomStatus,
      toggleMark,
      selectMarkScope,
      selectSort,
      clearAll,
      applyQuery,
      goToPage,
    ].forEach((mock) => mock.mockReset());
    spectator = createComponent();
  });

  const populate = (content: GameSummary[], overrides: { totalPages?: number; totalElements?: number } = {}) => {
    loading.set(false);
    games.set(content);
    totalPages.set(overrides.totalPages ?? 1);
    totalElements.set(overrides.totalElements ?? content.length);
    spectator.detectChanges();
  };

  const openFilters = () => {
    spectator.click('[data-testid="filters-button"]');
    spectator.detectChanges();
  };

  const panelButton = (label: string): HTMLButtonElement | undefined =>
    spectator
      .queryAll<HTMLButtonElement>('[data-testid="filters-panel"] button')
      .find((button) => button.textContent?.trim() === label);

  it('shows skeleton cards while loading', () => {
    expect(spectator.queryAll('hlm-skeleton').length).toBeGreaterThan(0);
  });

  it('renders game cards with title, status chip and cover when populated', () => {
    populate([aGameSummaryMock()]);

    expect(spectator.query('h3')).toHaveText('Super Mario World');
    expect(spectator.query('img')?.getAttribute('src')).toBe('https://example.com/smw.png');
    expect(spectator.query('lib-status-chip')).toHaveText('Installed');
  });

  it('renders a placeholder instead of an image when a game has no cover', () => {
    populate([aGameSummaryMock({ coverStatus: 'PLACEHOLDER', coverUrl: null, coverEndpoint: null })]);

    expect(spectator.query('img')).toBeNull();
    expect(spectator.query('[aria-label="No artwork for Super Mario World"]')).toBeTruthy();
  });

  it('shows every platform of a game as a chip in the colours the server sent', () => {
    populate([
      aGameSummaryMock({
        platforms: [aPlatformChipMock({ name: 'SNES' }), aPlatformChipMock({ name: 'PlayStation 5', background: '#0070D1' })],
      }),
    ]);

    const chips = spectator.queryAll('lib-platform-chip');
    expect(chips.map((chip) => chip.textContent?.trim())).toEqual(['SNES', 'PlayStation 5']);
    expect((chips[1] as HTMLElement).style.background).toContain('rgb(0, 112, 209)');
  });

  it('shows the install status chip and the source labels on a card', () => {
    populate([aGameSummaryMock({ installStatus: 'NOT_INSTALLED', sources: ['STEAM_OWNED', 'EMULATED'] })]);

    expect(spectator.query('lib-status-chip')).toHaveText('Not installed');
    expect(spectator.queryAll('lib-source-label').map((label) => label.textContent?.trim())).toEqual([
      'Steam (Owned)',
      'Emulated',
    ]);
  });

  it('shows the ROM chip only for a game with an emulated copy and says which hosts when they disagree', () => {
    populate([
      aGameSummaryMock({ id: 'a', romSummary: { state: 'VALIDATED', validated: 1, broken: 0, unknown: 0, total: 1 } }),
      aGameSummaryMock({ id: 'b', romSummary: { state: 'MIXED', validated: 1, broken: 0, unknown: 1, total: 2 } }),
      aGameSummaryMock({ id: 'c', romSummary: null }),
    ]);

    expect(spectator.queryAll('lib-rom-chip').map((chip) => chip.textContent?.trim())).toEqual([
      'Validated',
      'Validated on 1 of 2',
    ]);
  });

  it('shows an explicit empty state when the catalog is empty', () => {
    populate([]);

    expect(spectator.query('[data-testid="empty-library"]')).toHaveText('No games discovered yet.');
  });

  it('names the filter to relax when nothing matches', () => {
    const status = aFilter({ id: 'status', key: 'Status', value: 'Installed' });
    activeFilters.set([aFilter(), status]);
    populate([]);

    const relax = spectator
      .queryAll<HTMLButtonElement>('[data-testid="empty-library"] button')
      .find((button) => button.textContent?.includes('Try removing'));
    expect(relax).toHaveText('Try removing Status: Installed');

    spectator.click(relax as Element);

    expect(status.remove).toHaveBeenCalled();
  });

  it('shows an error state when loading fails', () => {
    loading.set(false);
    error.set('Failed to load games.');
    spectator.detectChanges();

    expect(spectator.query('.text-destructive')).toHaveText('Failed to load games.');
  });

  it('forwards search input to the store', () => {
    populate([aGameSummaryMock()]);

    spectator.typeInElement('mario', 'input[type="search"]');

    expect(search).toHaveBeenCalledWith('mario');
  });

  it('asks the store for the next page', () => {
    populate([aGameSummaryMock()], { totalPages: 3, totalElements: 150 });

    const nextButton = spectator.queryAll('button[hlmbadge]').find((b) => b.textContent?.trim() === 'Next');
    spectator.click(nextButton as Element);

    expect(goToPage).toHaveBeenCalledWith(1);
  });

  describe('marks on a card', () => {
    it('shows the public totals on the cover and outlines my own mark', () => {
      populate([
        aGameSummaryMock({
          votes: { wantToPlay: 5, playedLiked: 2, playedDisliked: 0 },
          myMark: 'PLAYED_LIKED',
        }),
      ]);

      const rail = spectator.query('[data-testid="vote-rail"]');
      expect(rail?.getAttribute('aria-label')).toBe('5 people want to play this, 2 played and liked it');
      expect(spectator.queryAll('[data-testid="vote-rail"] lib-mark-chip')).toHaveLength(2);
      expect(spectator.query('[data-testid="vote-rail"] [data-mark="PLAYED_LIKED"]')?.getAttribute('data-mine')).toBe('true');
    });

    it('shows no rail while nobody has voted', () => {
      populate([aGameSummaryMock()]);

      expect(spectator.query('[data-testid="vote-rail"]')).toBeNull();
    });

    it('offers the three marks as toggle buttons and shows mine as pressed', () => {
      populate([aGameSummaryMock({ myMark: 'WANT_TO_PLAY' })]);

      const pressed = spectator
        .queryAll('[data-testid="mark-buttons"] button')
        .map((button) => [button.getAttribute('aria-label'), button.getAttribute('aria-pressed')]);
      expect(pressed).toEqual([
        ['Want to play', 'true'],
        ['Played & liked', 'false'],
        ['Played & disliked', 'false'],
      ]);
    });

    it('sends the tapped mark and the game it belongs to to the store', () => {
      const game = aGameSummaryMock();
      populate([game]);

      spectator.click('[data-testid="mark-buttons"] [data-mark="PLAYED_DISLIKED"]');

      expect(toggleGameMark).toHaveBeenCalledWith(game, 'PLAYED_DISLIKED');
    });

    it('disables the buttons of a game whose mark is being saved', () => {
      const game = aGameSummaryMock();
      markPendingIds.set(new Set([game.id]));
      populate([game]);

      const buttons = spectator.queryAll<HTMLButtonElement>('[data-testid="mark-buttons"] button');
      expect(buttons.every((button) => button.disabled)).toBe(true);
    });

    it('does not put the buttons inside the link to the game page', () => {
      populate([aGameSummaryMock()]);

      expect(spectator.query('a [data-testid="mark-buttons"]')).toBeNull();
    });

    it('says when a mark could not be saved and lets the person dismiss it', () => {
      markError.set('Could not save your mark. Try again.');
      populate([aGameSummaryMock()]);

      expect(spectator.query('[data-testid="mark-error"]')).toHaveText('Could not save your mark');

      spectator.click('[data-testid="mark-error"] button');

      expect(dismissMarkError).toHaveBeenCalled();
    });
  });

  describe('the calm bar', () => {
    it('shows only the search box, one Filters button and the sort while no filter is active', () => {
      populate([aGameSummaryMock()]);

      expect(spectator.query('input[type="search"]')).toBeTruthy();
      expect(spectator.query('[data-testid="filters-button"]')).toBeTruthy();
      expect(spectator.query('[data-testid="active-filters"]')).toBeNull();
      expect(spectator.query('[data-testid="filters-panel"]')).toBeNull();
      expect(spectator.query('[data-testid="filters-count"]')).toBeNull();
    });

    it('shows every active filter as a removable chip with the count and a Clear all', () => {
      const snes = aFilter();
      const status = aFilter({ id: 'status', key: 'Status', value: 'Installed' });
      activeFilters.set([snes, status]);
      populate([aGameSummaryMock()], { totalElements: 37 });

      expect(spectator.queryAll('[data-testid="active-filter"]').map((chip) => chip.textContent?.replace(/\s+/g, ' ').trim()))
        .toEqual(['PlatformSNES', 'StatusInstalled']);
      expect(spectator.query('[data-testid="filters-count"]')).toHaveText('2');
      expect(spectator.query('[data-testid="result-count"]')).toHaveText('37 games');

      spectator.click('[aria-label="Remove filter Platform: SNES"]');
      spectator.click('[data-testid="clear-all-filters"]');

      expect(snes.remove).toHaveBeenCalled();
      expect(clearAll).toHaveBeenCalled();
    });

    it('does not offer Clear all for a single chip', () => {
      activeFilters.set([aFilter({ id: 'status', key: 'Status', value: 'Installed' })]);
      populate([aGameSummaryMock()]);

      expect(spectator.query('[data-testid="clear-all-filters"]')).toBeNull();
    });

    it('says how many more games have an unknown player count', () => {
      activeFilters.set([aFilter({ id: 'players', key: 'Players', value: '4+' })]);
      unknownPlayerCount.set(31);
      populate([aGameSummaryMock()]);

      expect(spectator.query('[data-testid="result-count"]')).toHaveText('31 more with unknown player count');
    });
  });

  describe('the filters panel', () => {
    beforeEach(() => {
      populate([aGameSummaryMock()]);
      openFilters();
    });

    it('opens under the Filters button and marks the button as expanded', () => {
      expect(spectator.query('[data-testid="filters-panel"]')).toBeTruthy();
      expect(spectator.query('[data-testid="filters-button"]')?.getAttribute('aria-expanded')).toBe('true');
    });

    it('moves focus into the panel when it opens', () => {
      expect(spectator.element.contains(document.activeElement)).toBe(true);
      expect(document.activeElement?.closest('[data-testid="filters-panel"]')).toBeTruthy();
    });

    it('closes on Escape and gives focus back to the Filters button', () => {
      spectator.dispatchKeyboardEvent('[data-testid="filters-panel"]', 'keydown', 'Escape');
      spectator.detectChanges();

      expect(spectator.query('[data-testid="filters-panel"]')).toBeNull();
      expect(document.activeElement).toBe(spectator.query('[data-testid="filters-button"]'));
    });

    it('closes from the Show games button and from the scrim', () => {
      spectator.click('[data-testid="filters-done"]');
      expect(spectator.query('[data-testid="filters-panel"]')).toBeNull();

      openFilters();
      spectator.click('[aria-label="Close filters"]');
      expect(spectator.query('[data-testid="filters-panel"]')).toBeNull();
    });

    it('offers only the platforms and places that exist and the fixed groups', () => {
      const platformGroup = spectator.query('[data-testid="filter-group-platform"]');
      const whereGroup = spectator.query('[data-testid="filter-group-where"]');

      expect(platformGroup).toHaveText('SNES');
      expect(platformGroup).toHaveText('Steam');
      expect(whereGroup).toHaveText('Living room PC');
      ['status', 'source', 'players', 'rom', 'marks', 'sort'].forEach((group) =>
        expect(spectator.query(`[data-testid="filter-group-${group}"]`)).toBeTruthy(),
      );
    });

    it('reflects the selection with aria-pressed', () => {
      selectedPlatforms.set(['SNES']);
      selectedInstallStatus.set('ALL');
      spectator.detectChanges();

      expect(panelButton('SNES')?.getAttribute('aria-pressed')).toBe('true');
      expect(panelButton('Steam')?.getAttribute('aria-pressed')).toBe('false');
      expect(panelButton('All')?.getAttribute('aria-pressed')).toBe('true');
    });

    it('forwards every choice to the store', () => {
      panelButton('SNES')?.click();
      panelButton('Living room PC')?.click();
      panelButton('Not installed')?.click();
      panelButton('Steam (Family)')?.click();
      panelButton('Broken ROM')?.click();
      panelButton('Played & liked')?.click();
      panelButton('Only mine')?.click();
      panelButton('Most liked')?.click();

      expect(togglePlatform).toHaveBeenCalledWith('SNES');
      expect(togglePlace).toHaveBeenCalledWith('host-1');
      expect(selectInstallStatus).toHaveBeenCalledWith('NOT_INSTALLED');
      expect(toggleSource).toHaveBeenCalledWith('STEAM_FAMILY');
      expect(toggleRomStatus).toHaveBeenCalledWith('BROKEN');
      expect(toggleMark).toHaveBeenCalledWith('PLAYED_LIKED');
      expect(selectMarkScope).toHaveBeenCalledWith('MINE');
      expect(selectSort).toHaveBeenCalledWith('MOST_LIKED');
    });

    it('steps the number of local players from any to two up to eight', () => {
      spectator.click('[aria-label="More players"]');
      expect(setMinLocalPlayers).toHaveBeenLastCalledWith(2);

      minLocalPlayers.set(8);
      spectator.detectChanges();
      expect(spectator.query('[aria-label="More players"]')?.hasAttribute('disabled')).toBe(true);

      spectator.click('[aria-label="Fewer players"]');
      expect(setMinLocalPlayers).toHaveBeenLastCalledWith(7);

      minLocalPlayers.set(2);
      spectator.detectChanges();
      spectator.click('[aria-label="Fewer players"]');
      expect(setMinLocalPlayers).toHaveBeenLastCalledWith(null);
    });

    it('shows the live result count on the Show button', () => {
      totalElements.set(37);
      spectator.detectChanges();

      expect(spectator.query('[data-testid="filters-done"]')).toHaveText('Show 37 games');
    });
  });

  describe('address bar', () => {
    it('restores the filters from the address bar on entry', () => {
      expect(applyQuery).toHaveBeenCalledTimes(1);
    });

    it('mirrors the filters into the address bar', async () => {
      const navigate = vi.spyOn(spectator.inject(Router), 'navigate').mockResolvedValue(true);

      queryParams.set({ platform: ['SNES'], status: null });
      spectator.detectChanges();

      expect(navigate).toHaveBeenCalledWith([], expect.objectContaining({ queryParams: { platform: ['SNES'], status: null } }));
    });
  });
});
