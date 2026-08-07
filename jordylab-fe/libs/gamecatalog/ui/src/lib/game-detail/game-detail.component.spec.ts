import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { of, Subject, throwError } from 'rxjs';
import { GameCatalogApiService, GameDetail } from '@jordylab-fe/gamecatalog/api';
import { GameDetailComponent } from './game-detail.component';

class GameCatalogApiServiceMock {
  private gameSubject = new Subject<GameDetail>();
  getGame = vi.fn(() => this.gameSubject.asObservable());

  setGame(game: GameDetail) {
    this.gameSubject.next(game);
  }

  failGame(status: number) {
    this.gameSubject.error({ status });
  }
}

const aGameDetail = (overrides: Partial<GameDetail> = {}): GameDetail => ({
  id: '1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f',
  title: 'Super Mario World',
  platform: 'SNES',
  sourceKey: 'snes',
  artworkStatus: 'EXTERNAL_URL',
  artworkUrl: 'https://example.com/smw.png',
  artworkEndpoint: null,
  enrichmentStatus: 'ENRICHED',
  genre: 'Platformer',
  maxLocalPlayers: 2,
  onlineMultiplayer: false,
  singlePlayer: true,
  description: 'A classic SNES platformer.',
  firstSeenAt: '2026-08-02T10:15:00Z',
  ...overrides,
});

describe('GameDetailComponent', () => {
  const createComponent = createComponentFactory({
    component: GameDetailComponent,
    providers: [
      { provide: GameCatalogApiService, useClass: GameCatalogApiServiceMock },
      {
        provide: ActivatedRoute,
        useValue: {
          snapshot: { paramMap: convertToParamMap({ id: '1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f' }) },
        },
      },
    ],
  });

  const apiMock = (spectator: Spectator<GameDetailComponent>) =>
    spectator.inject(GameCatalogApiService) as unknown as GameCatalogApiServiceMock;

  it('requests the game for the route id', () => {
    const spectator: Spectator<GameDetailComponent> = createComponent();

    expect(apiMock(spectator).getGame).toHaveBeenCalledWith('1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f');
  });

  it('renders artwork, facts and prose for an enriched game', () => {
    const spectator: Spectator<GameDetailComponent> = createComponent();
    apiMock(spectator).setGame(aGameDetail());
    spectator.detectChanges();

    const image = spectator.query('img');
    expect(image?.getAttribute('src')).toBe('https://example.com/smw.png');
    expect(spectator.query('h2')).toHaveText('Super Mario World');
    expect(spectator.query('dl')).toHaveText('up to 2 players');
    expect(spectator.query('dl')).toHaveText('Platformer');
    expect(spectator.query('p.max-w-prose')).toHaveText('A classic SNES platformer.');
    expect(spectator.element).not.toHaveText('Description unavailable.');
  });

  it('shows the explicit unavailable state while enrichment is pending', () => {
    const spectator: Spectator<GameDetailComponent> = createComponent();
    apiMock(spectator).setGame(
      aGameDetail({
        enrichmentStatus: 'PENDING',
        genre: null,
        maxLocalPlayers: null,
        onlineMultiplayer: null,
        singlePlayer: null,
        description: null,
      })
    );
    spectator.detectChanges();

    expect(spectator.element).toHaveText('Description unavailable.');
    expect(spectator.element).toHaveText('has not been generated yet');
    expect(spectator.query('dl')).toBeNull();
  });

  it('shows the explicit unavailable state when enrichment failed', () => {
    const spectator: Spectator<GameDetailComponent> = createComponent();
    apiMock(spectator).setGame(
      aGameDetail({
        enrichmentStatus: 'FAILED',
        genre: null,
        maxLocalPlayers: null,
        onlineMultiplayer: null,
        singlePlayer: null,
        description: null,
      })
    );
    spectator.detectChanges();

    expect(spectator.element).toHaveText('Description unavailable.');
    expect(spectator.element).toHaveText('could not be generated');
  });

  it('shows a not-found state for an invisible game', () => {
    const spectator: Spectator<GameDetailComponent> = createComponent();
    apiMock(spectator).failGame(404);
    spectator.detectChanges();

    expect(spectator.element).toHaveText('This game is not in your catalog.');
  });

  it('shows an error state for other failures', () => {
    const spectator: Spectator<GameDetailComponent> = createComponent();
    apiMock(spectator).failGame(500);
    spectator.detectChanges();

    expect(spectator.query('.text-destructive')).toHaveText('Failed to load the game.');
  });

  it('shows skeletons while loading', () => {
    const spectator: Spectator<GameDetailComponent> = createComponent();

    expect(spectator.queryAll('hlm-skeleton').length).toBeGreaterThan(0);
  });
});
