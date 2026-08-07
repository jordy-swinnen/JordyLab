import { RouterModule } from '@angular/router';
import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { of, Subject } from 'rxjs';
import { GameCatalogApiService, GamesPage, GameSummary } from '@jordylab-fe/gamecatalog/api';
import { GameGridComponent } from './game-grid.component';

class GameCatalogApiServiceMock {
  private gamesSubject = new Subject<GamesPage>();
  getGames = vi.fn(() => this.gamesSubject.asObservable());
  getPlatforms = vi.fn(() => of(['SNES', 'Steam']));

  setGames(page: GamesPage) {
    this.gamesSubject.next(page);
  }

  failGames() {
    this.gamesSubject.error(new Error('network error'));
  }
}

const aGame = (overrides: Partial<GameSummary> = {}): GameSummary => ({
  id: '1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f',
  title: 'Super Mario World',
  platform: 'SNES',
  artworkStatus: 'EXTERNAL_URL',
  artworkUrl: 'https://example.com/smw.png',
  artworkEndpoint: null,
  ...overrides,
});

const aPage = (content: GameSummary[], overrides: Partial<GamesPage> = {}): GamesPage => ({
  content,
  page: 0,
  size: 60,
  totalElements: content.length,
  totalPages: 1,
  ...overrides,
});

describe('GameGridComponent', () => {
  const createComponent = createComponentFactory({
    component: GameGridComponent,
    imports: [RouterModule.forRoot([])],
    providers: [{ provide: GameCatalogApiService, useClass: GameCatalogApiServiceMock }],
  });

  const apiMock = (spectator: Spectator<GameGridComponent>) =>
    spectator.inject(GameCatalogApiService) as unknown as GameCatalogApiServiceMock;

  it('shows skeleton cards while loading', () => {
    const spectator: Spectator<GameGridComponent> = createComponent();

    expect(spectator.queryAll('hlm-skeleton').length).toBeGreaterThan(0);
  });

  it('renders game cards with title, badge and artwork when populated', () => {
    const spectator: Spectator<GameGridComponent> = createComponent();
    apiMock(spectator).setGames(aPage([aGame()]));
    spectator.detectChanges();

    expect(spectator.query('h3')).toHaveText('Super Mario World');
    const image = spectator.query('img');
    expect(image).toBeTruthy();
    expect(image?.getAttribute('src')).toBe('https://example.com/smw.png');
    expect(spectator.query('[hlmbadge]')).toBeTruthy();
  });

  it('renders a placeholder instead of an image when a game has no artwork', () => {
    const spectator: Spectator<GameGridComponent> = createComponent();
    apiMock(spectator).setGames(
      aPage([aGame({ artworkStatus: 'PLACEHOLDER', artworkUrl: null, artworkEndpoint: null })])
    );
    spectator.detectChanges();

    expect(spectator.query('img')).toBeNull();
    expect(spectator.query('[aria-label="No artwork for Super Mario World"]')).toBeTruthy();
  });

  it('shows an explicit empty state when the catalog is empty', () => {
    const spectator: Spectator<GameGridComponent> = createComponent();
    apiMock(spectator).setGames(aPage([]));
    spectator.detectChanges();

    expect(spectator.query('p')).toHaveText('No games discovered yet.');
  });

  it('shows an error state when loading fails', () => {
    const spectator: Spectator<GameGridComponent> = createComponent();
    apiMock(spectator).failGames();
    spectator.detectChanges();

    expect(spectator.query('.text-destructive')).toHaveText('Failed to load games.');
  });

  it('debounces search input before reloading', () => {
    vi.useFakeTimers();
    try {
      const spectator: Spectator<GameGridComponent> = createComponent();
      apiMock(spectator).setGames(aPage([aGame()]));
      spectator.detectChanges();
      const mock = apiMock(spectator);
      mock.getGames.mockClear();

      spectator.typeInElement('mario', 'input[type="search"]');
      vi.advanceTimersByTime(299);
      expect(mock.getGames).not.toHaveBeenCalled();

      vi.advanceTimersByTime(1);
      expect(mock.getGames).toHaveBeenCalledWith({ search: 'mario', platform: undefined, page: 0, size: 60 });
    } finally {
      vi.useRealTimers();
    }
  });

  it('reloads with the platform filter when a chip is clicked', () => {
    const spectator: Spectator<GameGridComponent> = createComponent();
    apiMock(spectator).setGames(aPage([aGame()]));
    spectator.detectChanges();
    const mock = apiMock(spectator);
    mock.getGames.mockClear();

    const chips = spectator.queryAll('button[hlmbadge]');
    const snesChip = chips.find((chip) => chip.textContent?.trim() === 'SNES');
    spectator.click(snesChip as Element);

    expect(mock.getGames).toHaveBeenCalledWith({ search: undefined, platform: 'SNES', page: 0, size: 60 });
  });

  it('navigates to the next page', () => {
    const spectator: Spectator<GameGridComponent> = createComponent();
    apiMock(spectator).setGames(aPage([aGame()], { totalPages: 3, totalElements: 150 }));
    spectator.detectChanges();
    const mock = apiMock(spectator);
    mock.getGames.mockClear();

    const nextButton = spectator.queryAll('button[hlmbadge]').find((b) => b.textContent?.trim() === 'Next');
    spectator.click(nextButton as Element);

    expect(mock.getGames).toHaveBeenCalledWith({ search: undefined, platform: undefined, page: 1, size: 60 });
  });

  it('loads platforms for the filter chips', () => {
    const spectator: Spectator<GameGridComponent> = createComponent();
    apiMock(spectator).setGames(aPage([aGame()]));
    spectator.detectChanges();

    const chips = spectator.queryAll('button[hlmbadge]').map((chip) => chip.textContent?.trim());
    expect(chips).toContain('All');
    expect(chips).toContain('SNES');
    expect(chips).toContain('Steam');
  });
});
