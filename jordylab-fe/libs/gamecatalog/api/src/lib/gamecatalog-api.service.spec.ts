import { createHttpFactory, HttpMethod, SpectatorHttp } from '@ngneat/spectator/vitest';
import { artworkUrl, GameCatalogApiService } from './gamecatalog-api.service';
import { GameSummary, GamesPage } from './gamecatalog.models';

describe('GameCatalogApiService', () => {
  let spectator: SpectatorHttp<GameCatalogApiService>;
  const createService = createHttpFactory(GameCatalogApiService);

  beforeEach(() => {
    spectator = createService();
  });

  const aGameSummary = (overrides: Partial<GameSummary> = {}): GameSummary => ({
    id: '1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f',
    title: 'Super Mario World',
    platform: 'SNES',
    artworkStatus: 'EXTERNAL_URL',
    artworkUrl: 'https://example.com/smw.png',
    artworkEndpoint: null,
    ...overrides,
  });

  it('requests the games page without optional params', () => {
    const expectedPage: GamesPage = {
      content: [aGameSummary()],
      page: 0,
      size: 60,
      totalElements: 1,
      totalPages: 1,
    };

    spectator.service.getGames().subscribe((page) => {
      expect(page).toEqual(expectedPage);
    });

    spectator.expectOne('/api/gamecatalog/games', HttpMethod.GET).flush(expectedPage);
  });

  it('passes search, platform, page and size as query params', () => {
    spectator.service
      .getGames({ search: 'mario', platform: 'SNES', page: 2, size: 30 })
      .subscribe();

    spectator
      .expectOne('/api/gamecatalog/games?search=mario&platform=SNES&page=2&size=30', HttpMethod.GET)
      .flush({ content: [], page: 2, size: 30, totalElements: 0, totalPages: 0 });
  });

  it('omits empty search and platform params', () => {
    spectator.service.getGames({ search: '', page: 0 }).subscribe();

    spectator
      .expectOne('/api/gamecatalog/games?page=0', HttpMethod.GET)
      .flush({ content: [], page: 0, size: 60, totalElements: 0, totalPages: 0 });
  });

  it('returns an HTTP error when the games endpoint fails', () => {
    spectator.service.getGames().subscribe({
      next: () => fail('expected an error'),
      error: (error) => expect(error.status).toBe(500),
    });

    spectator
      .expectOne('/api/gamecatalog/games', HttpMethod.GET)
      .flush('Server error', { status: 500, statusText: 'Internal Server Error' });
  });

  it('maps the platforms response to a plain string list', () => {
    spectator.service.getPlatforms().subscribe((platforms) => {
      expect(platforms).toEqual(['SNES', 'PlayStation 2', 'Steam']);
    });

    spectator
      .expectOne('/api/gamecatalog/platforms', HttpMethod.GET)
      .flush({ platforms: ['SNES', 'PlayStation 2', 'Steam'] });
  });

  it('requests a game detail by id', () => {
    const expectedDetail = {
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
      description: 'A classic.',
      firstSeenAt: '2026-08-02T10:15:00Z',
    };

    spectator.service.getGame('1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f').subscribe((detail) => {
      expect(detail).toEqual(expectedDetail);
    });

    spectator
      .expectOne('/api/gamecatalog/games/1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f', HttpMethod.GET)
      .flush(expectedDetail);
  });

  it('propagates a 404 when the game is not visible', () => {
    spectator.service.getGame('missing-id').subscribe({
      next: () => fail('expected an error'),
      error: (error) => expect(error.status).toBe(404),
    });

    spectator
      .expectOne('/api/gamecatalog/games/missing-id', HttpMethod.GET)
      .flush('Not Found', { status: 404, statusText: 'Not Found' });
  });

  it('posts a chat question and maps the answered response', () => {
    const expectedAnswer = {
      answer: 'One game supports local co-op.',
      games: [{ id: '1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f', title: 'Super Mario World', platform: 'SNES' }],
      noMatch: false,
    };

    spectator.service.chat('which games support local co-op?').subscribe((response) => {
      expect(response).toEqual({ kind: 'answered', answer: expectedAnswer });
    });

    const request = spectator.expectOne('/api/gamecatalog/chat', HttpMethod.POST);
    expect(request.request.body).toEqual({ question: 'which games support local co-op?' });
    request.flush(expectedAnswer);
  });

  it('maps a 503 chat response to the unavailable state', () => {
    spectator.service.chat('anything').subscribe((response) => {
      expect(response).toEqual({ kind: 'unavailable' });
    });

    spectator
      .expectOne('/api/gamecatalog/chat', HttpMethod.POST)
      .flush({ reason: 'CHAT_UNAVAILABLE' }, { status: 503, statusText: 'Service Unavailable' });
  });

  it('propagates non-503 chat errors', () => {
    spectator.service.chat('anything').subscribe({
      next: () => fail('expected an error'),
      error: (error) => expect(error.status).toBe(500),
    });

    spectator
      .expectOne('/api/gamecatalog/chat', HttpMethod.POST)
      .flush('Server error', { status: 500, statusText: 'Internal Server Error' });
  });

  it('maps the sources response to a plain list', () => {
    const expectedSources = [
      {
        id: '2c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f',
        sourceKey: 'snes',
        path: 'D:\\EmuDeck\\roms\\snes',
        sourceType: 'ROM_FOLDER',
        platform: 'SNES',
        enabled: true,
        lastAttemptAt: '2026-08-02T10:20:00Z',
        lastSuccessAt: '2026-08-02T10:20:00Z',
        lastOutcome: 'APPLIED',
        installedGameCount: 412,
      },
    ];

    spectator.service.getSources().subscribe((sources) => {
      expect(sources).toEqual(expectedSources);
    });

    spectator.expectOne('/api/gamecatalog/sources', HttpMethod.GET).flush({ sources: expectedSources });
  });

  it('puts the enabled toggle for a source', () => {
    spectator.service.setSourceEnabled('2c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f', false).subscribe((response) => {
      expect(response).toEqual({ id: '2c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f', enabled: false });
    });

    const request = spectator.expectOne(
      '/api/gamecatalog/sources/2c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f/enabled',
      HttpMethod.PUT
    );
    expect(request.request.body).toEqual({ enabled: false });
    request.flush({ id: '2c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f', enabled: false });
  });

  it('resolves the external artwork URL when present', () => {
    const game = aGameSummary();

    expect(artworkUrl(game)).toBe('https://example.com/smw.png');
  });

  it('resolves the local artwork endpoint for uploaded art', () => {
    const game = aGameSummary({
      artworkStatus: 'LOCAL_UPLOAD',
      artworkUrl: null,
      artworkEndpoint: '/api/gamecatalog/games/1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f/artwork',
    });

    expect(artworkUrl(game)).toBe(
      '/api/gamecatalog/games/1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f/artwork'
    );
  });

  it('resolves null when a game has no artwork', () => {
    const game = aGameSummary({ artworkStatus: 'PLACEHOLDER', artworkUrl: null, artworkEndpoint: null });

    expect(artworkUrl(game)).toBeNull();
  });
});
