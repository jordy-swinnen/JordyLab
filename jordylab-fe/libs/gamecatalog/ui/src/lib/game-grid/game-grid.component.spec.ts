import { signal } from '@angular/core';
import { RouterModule } from '@angular/router';
import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { aGameSummaryMock, GameLibraryStore, GameSummary } from '@jordylab-fe/gamecatalog/api';
import { GameGridComponent } from './game-grid.component';

describe('GameGridComponent', () => {
  const games = signal<GameSummary[]>([]);
  const platforms = signal<string[]>([]);
  const loading = signal(true);
  const error = signal<string | null>(null);
  const selectedPlatform = signal<string | null>(null);
  const page = signal(0);
  const totalPages = signal(0);
  const totalElements = signal(0);

  const search = vi.fn<GameLibraryStore['search']>();
  const selectPlatform = vi.fn<GameLibraryStore['selectPlatform']>();
  const goToPage = vi.fn<GameLibraryStore['goToPage']>();

  const storeMock = {
    games: games.asReadonly(),
    platforms: platforms.asReadonly(),
    loading: loading.asReadonly(),
    error: error.asReadonly(),
    selectedPlatform: selectedPlatform.asReadonly(),
    page: page.asReadonly(),
    totalPages: totalPages.asReadonly(),
    totalElements: totalElements.asReadonly(),
    search,
    selectPlatform,
    goToPage,
  };

  let spectator: Spectator<GameGridComponent>;

  const createComponent = createComponentFactory({
    component: GameGridComponent,
    imports: [RouterModule.forRoot([])],
    providers: [{ provide: GameLibraryStore, useValue: storeMock }],
  });

  beforeEach(() => {
    games.set([]);
    platforms.set(['SNES', 'Steam']);
    loading.set(true);
    error.set(null);
    selectedPlatform.set(null);
    page.set(0);
    totalPages.set(0);
    totalElements.set(0);
    search.mockReset();
    selectPlatform.mockReset();
    goToPage.mockReset();
    spectator = createComponent();
  });

  const populate = (content: GameSummary[], overrides: { totalPages?: number; totalElements?: number } = {}) => {
    loading.set(false);
    games.set(content);
    totalPages.set(overrides.totalPages ?? 1);
    totalElements.set(overrides.totalElements ?? content.length);
    spectator.detectChanges();
  };

  it('shows skeleton cards while loading', () => {
    expect(spectator.queryAll('hlm-skeleton').length).toBeGreaterThan(0);
  });

  it('renders game cards with title, badge and artwork when populated', () => {
    populate([aGameSummaryMock()]);

    expect(spectator.query('h3')).toHaveText('Super Mario World');
    const image = spectator.query('img');
    expect(image).toBeTruthy();
    expect(image?.getAttribute('src')).toBe('https://example.com/smw.png');
    expect(spectator.query('[hlmbadge]')).toBeTruthy();
  });

  it('renders a placeholder instead of an image when a game has no artwork', () => {
    populate([aGameSummaryMock({ artworkStatus: 'PLACEHOLDER', artworkUrl: null, artworkEndpoint: null })]);

    expect(spectator.query('img')).toBeNull();
    expect(spectator.query('[aria-label="No artwork for Super Mario World"]')).toBeTruthy();
  });

  it('shows an explicit empty state when the catalog is empty', () => {
    populate([]);

    expect(spectator.query('p')).toHaveText('No games discovered yet.');
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

  it('selects the platform in the store when a chip is clicked', () => {
    populate([aGameSummaryMock()]);

    const chips = spectator.queryAll('button[hlmbadge]');
    const snesChip = chips.find((chip) => chip.textContent?.trim() === 'SNES');
    spectator.click(snesChip as Element);

    expect(selectPlatform).toHaveBeenCalledWith('SNES');
  });

  it('asks the store for the next page', () => {
    populate([aGameSummaryMock()], { totalPages: 3, totalElements: 150 });

    const nextButton = spectator.queryAll('button[hlmbadge]').find((b) => b.textContent?.trim() === 'Next');
    spectator.click(nextButton as Element);

    expect(goToPage).toHaveBeenCalledWith(1);
  });

  it('renders the store platforms as filter chips', () => {
    populate([aGameSummaryMock()]);

    const chips = spectator.queryAll('button[hlmbadge]').map((chip) => chip.textContent?.trim());
    expect(chips).toContain('All');
    expect(chips).toContain('SNES');
    expect(chips).toContain('Steam');
  });
});
