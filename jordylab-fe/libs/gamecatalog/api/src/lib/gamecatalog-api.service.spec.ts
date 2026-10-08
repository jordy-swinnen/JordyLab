import { createHttpFactory, HttpMethod, SpectatorHttp } from '@ngneat/spectator/vitest';
import { bannerUrl, coverUrl, GameCatalogApiService } from './gamecatalog-api.service';
import { GamesPage } from './gamecatalog.models';
import { aGameDetailMock } from './mocks/game-detail.model.mock';
import { aGameSummaryMock } from './mocks/game-summary.model.mock';
import { aConsoleMock } from './mocks/console.model.mock';
import { aKnownConsoleMock } from './mocks/known-console.model.mock';
import { aLibBotQuotaMock } from './mocks/libbot-quota.model.mock';
import { aPlaceOptionMock } from './mocks/place-option.model.mock';
import { aPlatformChipMock } from './mocks/platform-chip.model.mock';
import { aSourcesOverviewMock } from './mocks/sources-overview.model.mock';
import { aRefreshRunMock } from './mocks/refresh-run.model.mock';

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

  it('passes search, platform, page and size as query params', () => {
    spectator.service.getGames({ search: 'mario', platform: ['SNES'], page: 2, size: 30 }).subscribe();

    spectator
      .expectOne('/api/gamecatalog/games?search=mario&platform=SNES&page=2&size=30', HttpMethod.GET)
      .flush({ content: [], page: 2, size: 30, totalElements: 0, totalPages: 0 });
  });

  it('repeats a param once per value for the multi-select filters', () => {
    spectator.service
      .getGames({
        platform: ['SNES', 'Steam'],
        where: ['host-1', 'console-1'],
        source: ['STEAM_OWNED', 'EMULATED'],
        romStatus: ['BROKEN'],
        mark: ['WANT_TO_PLAY', 'PLAYED_LIKED'],
        markScope: 'MINE',
        minLocalPlayers: 6,
        sort: 'MOST_WANTED',
      })
      .subscribe();

    spectator
      .expectOne(
        '/api/gamecatalog/games?platform=SNES&platform=Steam&where=host-1&where=console-1&source=STEAM_OWNED&source=EMULATED&minLocalPlayers=6&romStatus=BROKEN&mark=WANT_TO_PLAY&mark=PLAYED_LIKED&markScope=MINE&sort=MOST_WANTED',
        HttpMethod.GET,
      )
      .flush({ content: [], page: 0, size: 60, totalElements: 0, totalPages: 0 });
  });

  it('leaves out the mark scope when no mark is asked for and the default sort', () => {
    spectator.service.getGames({ markScope: 'MINE', sort: 'TITLE' }).subscribe();

    spectator
      .expectOne('/api/gamecatalog/games', HttpMethod.GET)
      .flush({ content: [], page: 0, size: 60, totalElements: 0, totalPages: 0 });
  });

  it('omits an empty search', () => {
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

  it('maps the platforms response to a list of coloured platform chips', () => {
    const chips = [aPlatformChipMock({ name: 'SNES' }), aPlatformChipMock({ name: 'Steam', family: 'STEAM' })];
    spectator.service.getPlatforms().subscribe((platforms) => {
      expect(platforms).toEqual(chips);
    });

    spectator
      .expectOne('/api/gamecatalog/platforms', HttpMethod.GET)
      .flush({ platforms: chips });
  });

  it('maps the places response to a list of hosts and consoles', () => {
    const places = [aPlaceOptionMock(), aPlaceOptionMock({ id: 'c-1', kind: 'CONSOLE', label: 'Nintendo Switch' })];
    spectator.service.getPlaces().subscribe((result) => {
      expect(result).toEqual(places);
    });

    spectator.expectOne('/api/gamecatalog/places', HttpMethod.GET).flush({ places });
  });

  it('puts the display name of a host, or null to clear it', () => {
    spectator.service.setHostDisplayName('host-1', 'Living room PC').subscribe();
    const request = spectator.expectOne('/api/gamecatalog/hosts/host-1/display-name', HttpMethod.PUT);
    expect(request.request.body).toEqual({ displayName: 'Living room PC' });
    request.flush({});

    spectator.service.setHostDisplayName('host-1', null).subscribe();
    const clearing = spectator.expectOne('/api/gamecatalog/hosts/host-1/display-name', HttpMethod.PUT);
    expect(clearing.request.body).toEqual({ displayName: null });
    clearing.flush({});
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

  it('reads the LibBot allowance', () => {
    spectator.service.getLibBotQuota().subscribe((quota) => {
      expect(quota).toEqual(aLibBotQuotaMock());
    });

    spectator.expectOne('/api/gamecatalog/libbot/quota', HttpMethod.GET).flush(aLibBotQuotaMock());
  });

  it('asks the server to forget a LibBot conversation', () => {
    spectator.service.forgetLibBotConversation('c-1').subscribe();

    spectator.expectOne('/api/gamecatalog/libbot/conversations/c-1', HttpMethod.DELETE).flush(null);
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

    spectator.service.getSources().subscribe((overview) => {
      expect(overview).toEqual(aSourcesOverviewMock({ sources: expectedSources }));
    });

    spectator
      .expectOne('/api/gamecatalog/sources', HttpMethod.GET)
      .flush(aSourcesOverviewMock({ sources: expectedSources }));
  });

  it('asks for the games behind one health count', () => {
    spectator.service.getHealthExceptions('COVER').subscribe();

    spectator
      .expectOne('/api/gamecatalog/sources/health/exceptions?kind=COVER', HttpMethod.GET)
      .flush({ kind: 'COVER', total: 0, games: [] });
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

  describe('consoles', () => {
    it('asks for the well-known consoles matching a query', () => {
      spectator.service.getKnownConsoles('nin').subscribe();

      spectator.expectOne('/api/gamecatalog/consoles/known?q=nin', HttpMethod.GET).flush([aKnownConsoleMock()]);
    });

    it('lists, adds and renames consoles', () => {
      spectator.service.getConsoles().subscribe();
      spectator.expectOne('/api/gamecatalog/consoles', HttpMethod.GET).flush([aConsoleMock()]);

      spectator.service.addConsole('Nintendo Switch', 'Switch dock').subscribe();
      const add = spectator.expectOne('/api/gamecatalog/consoles', HttpMethod.POST);
      expect(add.request.body).toEqual({ platform: 'Nintendo Switch', name: 'Switch dock' });
      add.flush(aConsoleMock());

      spectator.service.addConsole('Nintendo Switch', null).subscribe();
      const addDefault = spectator.expectOne('/api/gamecatalog/consoles', HttpMethod.POST);
      expect(addDefault.request.body).toEqual({ platform: 'Nintendo Switch' });
      addDefault.flush(aConsoleMock());

      spectator.service.renameConsole('c1', 'Bedroom').subscribe();
      const rename = spectator.expectOne('/api/gamecatalog/consoles/c1', HttpMethod.PATCH);
      expect(rename.request.body).toEqual({ name: 'Bedroom' });
      rename.flush(aConsoleMock());
    });

    it('reads the impact of a removal and removes the console', () => {
      spectator.service.getConsoleImpact('c1').subscribe();
      spectator.expectOne('/api/gamecatalog/consoles/c1/impact', HttpMethod.GET)
        .flush({ games: 1, alsoElsewhere: 0, wouldBeRemoved: 1 });

      spectator.service.removeConsole('c1').subscribe();
      spectator.expectOne('/api/gamecatalog/consoles/c1', HttpMethod.DELETE).flush(null);
    });

    it('lists, searches, adds, relinks and removes games on a console', () => {
      spectator.service.getConsoleGames('c1').subscribe();
      spectator.expectOne('/api/gamecatalog/consoles/c1/games', HttpMethod.GET).flush([]);

      spectator.service.searchConsoleGames('c1', 'mario').subscribe();
      spectator.expectOne('/api/gamecatalog/consoles/c1/search?q=mario', HttpMethod.GET).flush([]);

      spectator.service.addConsoleGame('c1', { igdbGameId: 13427 }).subscribe();
      const add = spectator.expectOne('/api/gamecatalog/consoles/c1/games', HttpMethod.POST);
      expect(add.request.body).toEqual({ igdbGameId: 13427 });
      add.flush({ gameId: 'g1', title: 'Mario Kart 8 Deluxe', linkedExisting: false });

      spectator.service.relinkConsoleGame('c1', 'g1', 13427).subscribe();
      const relink = spectator.expectOne('/api/gamecatalog/consoles/c1/games/g1', HttpMethod.PATCH);
      expect(relink.request.body).toEqual({ igdbGameId: 13427 });
      relink.flush({ gameId: 'g1', title: 'x', linkedExisting: false });

      spectator.service.removeConsoleGame('c1', 'g1').subscribe();
      spectator.expectOne('/api/gamecatalog/consoles/c1/games/g1', HttpMethod.DELETE).flush(null);
    });

    it('previews and confirms a pasted list', () => {
      spectator.service.previewConsoleBulk('c1', ['Celeste']).subscribe((lines) => {
        expect(lines).toEqual([{ line: 'Celeste', status: 'NO_MATCH', match: null }]);
      });
      const preview = spectator.expectOne('/api/gamecatalog/consoles/c1/games/bulk/preview', HttpMethod.POST);
      expect(preview.request.body).toEqual({ lines: ['Celeste'] });
      preview.flush({ lines: [{ line: 'Celeste', status: 'NO_MATCH', match: null }] });

      spectator.service.confirmConsoleBulk('c1', [{ line: 'Celeste', igdbGameId: null, title: 'Celeste' }]).subscribe();
      const confirm = spectator.expectOne('/api/gamecatalog/consoles/c1/games/bulk/confirm', HttpMethod.POST);
      expect(confirm.request.body).toEqual({ items: [{ line: 'Celeste', igdbGameId: null, title: 'Celeste' }] });
      confirm.flush({ added: [], skipped: [], failed: [] });
    });
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

  it('puts the ROM status of one copy of a game', () => {
    spectator.service.setRomStatus('game-1', 'copy-1', 'BROKEN').subscribe();

    const request = spectator.expectOne('/api/gamecatalog/games/game-1/installations/copy-1/rom-status', HttpMethod.PUT);
    expect(request.request.body).toEqual({ status: 'BROKEN' });
    request.flush({});
  });

  it('asks what turning a source off would hide', () => {
    spectator.service.getHideImpact('source-1').subscribe((impact) => {
      expect(impact).toEqual({ hiddenGames: 87, stillVisibleElsewhere: 5 });
    });

    spectator
      .expectOne('/api/gamecatalog/sources/source-1/hide-impact', HttpMethod.GET)
      .flush({ hiddenGames: 87, stillVisibleElsewhere: 5 });
  });

  it('starts a refresh run of a kind, with the cost confirmation', () => {
    const run = aRefreshRunMock({ kind: 'AI' });

    spectator.service.startRefreshRun('AI', true).subscribe((result) => expect(result).toEqual(run));

    const request = spectator.expectOne('/api/gamecatalog/refresh-runs', HttpMethod.POST);
    expect(request.request.body).toEqual({ kind: 'AI', confirmCost: true });
    request.flush(run);
  });

  it('asks for the latest run of a kind', () => {
    const run = aRefreshRunMock();

    spectator.service.getCurrentRefreshRun('DATA').subscribe((result) => expect(result).toEqual(run));

    spectator.expectOne('/api/gamecatalog/refresh-runs/current?kind=DATA', HttpMethod.GET).flush(run);
  });

  it('posts a stop request for a run', () => {
    const run = aRefreshRunMock({ stopRequested: true });

    spectator.service.stopRefreshRun(run.id).subscribe((result) => expect(result).toEqual(run));

    spectator.expectOne(`/api/gamecatalog/refresh-runs/${run.id}/stop`, HttpMethod.POST).flush(run);
  });
});
