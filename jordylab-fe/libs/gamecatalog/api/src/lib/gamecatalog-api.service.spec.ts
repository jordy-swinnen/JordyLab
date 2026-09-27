import { createHttpFactory, HttpMethod, SpectatorHttp } from '@ngneat/spectator/vitest';
import { bannerUrl, coverUrl, GameCatalogApiService } from './gamecatalog-api.service';
import { GamesPage } from './gamecatalog.models';
import { aGameDetailMock } from './mocks/game-detail.model.mock';
import { aGameSummaryMock } from './mocks/game-summary.model.mock';
import { aRefreshAllMock } from './mocks/refresh-all.model.mock';

describe('GameCatalogApiService', () => {
  let spectator: SpectatorHttp<GameCatalogApiService>;
  const createService = createHttpFactory(GameCatalogApiService);

  beforeEach(() => {
    spectator = createService();
  });

  it('requests the games page without optional params', () => {
    const expectedPage: GamesPage = {
      content: [aGameSummaryMock()],
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

  it('passes search, platform, host, page and size as query params', () => {
    spectator.service
      .getGames({ search: 'mario', platform: 'SNES', host: 'jordybox', page: 2, size: 30 })
      .subscribe();

    spectator
      .expectOne('/api/gamecatalog/games?search=mario&platform=SNES&host=jordybox&page=2&size=30', HttpMethod.GET)
      .flush({ content: [], page: 2, size: 30, totalElements: 0, totalPages: 0 });
  });

  it('omits empty search, platform and host params', () => {
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

  it('maps the hosts response to a plain string list', () => {
    spectator.service.getHosts().subscribe((hosts) => {
      expect(hosts).toEqual(['jordybox', 'ryzen-desktop']);
    });

    spectator
      .expectOne('/api/gamecatalog/hosts', HttpMethod.GET)
      .flush({ hosts: ['jordybox', 'ryzen-desktop'] });
  });

  it('requests a game detail by id', () => {
    const expectedDetail = aGameDetailMock();

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

  it('includes attached game ids when present', () => {
    spectator.service.chat('is this good for 4 players?', ['game-1']).subscribe();

    const request = spectator.expectOne('/api/gamecatalog/chat', HttpMethod.POST);
    expect(request.request.body).toEqual({ question: 'is this good for 4 players?', gameIds: ['game-1'] });
    request.flush({ answer: 'Yes.', games: [], noMatch: false });
  });

  it('maps a 503 chat response to the unavailable state', () => {
    spectator.service.chat('anything').subscribe((response) => {
      expect(response).toEqual({ kind: 'unavailable' });
    });

    spectator
      .expectOne('/api/gamecatalog/chat', HttpMethod.POST)
      .flush({ reason: 'CHAT_UNAVAILABLE' }, { status: 503, statusText: 'Service Unavailable' });
  });

  it('maps a 400 invalid-attachment response to the unavailable state', () => {
    spectator.service.chat('anything', ['hidden']).subscribe((response) => {
      expect(response).toEqual({ kind: 'unavailable' });
    });

    spectator
      .expectOne('/api/gamecatalog/chat', HttpMethod.POST)
      .flush({ reason: 'GAME_IDS_INVALID' }, { status: 400, statusText: 'Bad Request' });
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
        sourceKey: 'jordybox:EMUDECK',
        hostname: 'jordybox',
        sourceType: 'EMUDECK',
        platform: 'SNES',
        enabled: true,
        lastAttemptAt: '2026-08-02T10:20:00Z',
        lastSuccessAt: '2026-08-02T10:20:00Z',
        lastCheckedAt: null,
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

  it('resolves the external cover URL when present', () => {
    expect(coverUrl(aGameSummaryMock())).toBe('https://example.com/smw.png');
  });

  it('resolves the local cover endpoint for uploaded art', () => {
    const game = aGameSummaryMock({
      coverStatus: 'LOCAL_UPLOAD',
      coverUrl: null,
      coverEndpoint: '/api/gamecatalog/games/1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f/artwork',
    });

    expect(coverUrl(game)).toBe('/api/gamecatalog/games/1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f/artwork');
  });

  it('resolves null when a game has no cover', () => {
    const game = aGameSummaryMock({ coverStatus: 'PLACEHOLDER', coverUrl: null, coverEndpoint: null });

    expect(coverUrl(game)).toBeNull();
  });

  it('resolves the banner URL when present', () => {
    expect(bannerUrl(aGameDetailMock())).toBe('https://example.com/smw-banner.png');
  });

  it('resolves null when a game has no banner', () => {
    const game = aGameDetailMock({ bannerStatus: 'PLACEHOLDER', bannerUrl: null, bannerEndpoint: null });

    expect(bannerUrl(game)).toBeNull();
  });

  it('posts a deterministic metadata refresh for a game', () => {
    const refreshed = aGameDetailMock({ developer: 'Valve' });

    spectator.service.refreshGameMetadata('1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f').subscribe((detail) => {
      expect(detail).toEqual(refreshed);
    });

    spectator
      .expectOne('/api/gamecatalog/games/1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f/metadata/refresh', HttpMethod.POST)
      .flush(refreshed);
  });

  it('posts an AI enrichment refresh for a game', () => {
    const regenerated = aGameDetailMock({ description: 'Regenerated.' });

    spectator.service.refreshGameEnrichment('1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f').subscribe((detail) => {
      expect(detail).toEqual(regenerated);
    });

    spectator
      .expectOne('/api/gamecatalog/games/1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f/enrichment/refresh', HttpMethod.POST)
      .flush(regenerated);
  });

  it('posts a bulk refresh and returns the processed and remaining counts', () => {
    const counts = aRefreshAllMock();

    spectator.service.refreshPending().subscribe((result) => {
      expect(result).toEqual(counts);
    });

    spectator.expectOne('/api/gamecatalog/games/refresh', HttpMethod.POST).flush(counts);
  });
});
