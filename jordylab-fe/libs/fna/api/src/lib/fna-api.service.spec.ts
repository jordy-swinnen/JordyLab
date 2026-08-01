import { createHttpFactory, HttpMethod, SpectatorHttp } from '@ngneat/spectator/vitest';
import { FnaApiService } from './fna-api.service';
import { ArticleSummary, Briefing, PortfolioPosition } from './fna.models';

describe('FnaApiService', () => {
  let spectator: SpectatorHttp<FnaApiService>;
  const createService = createHttpFactory(FnaApiService);

  beforeEach(() => {
    spectator = createService();
  });

  it('returns articles on success', () => {
    const expectedArticles: ArticleSummary[] = [
      {
        id: '1',
        title: 'ECB holds rates steady',
        url: 'https://example.com/1',
        publishedAt: '2026-08-01T06:00:00Z',
        feedName: 'ECB Press Releases',
      },
    ];

    spectator.service.getArticles().subscribe((articles) => {
      expect(articles).toEqual(expectedArticles);
    });

    spectator.expectOne('/api/fna/articles', HttpMethod.GET).flush(expectedArticles);
  });

  it('returns an HTTP error when articles endpoint fails', () => {
    spectator.service.getArticles().subscribe({
      next: () => fail('expected an error'),
      error: (error) => expect(error.status).toBe(500),
    });

    spectator
      .expectOne('/api/fna/articles', HttpMethod.GET)
      .flush('Server error', { status: 500, statusText: 'Internal Server Error' });
  });

  it('returns portfolio positions on success', () => {
    const expectedPositions: PortfolioPosition[] = [
      { id: 'p1', ticker: 'ABI', shareCount: 10, lastPrice: 70.5, lastPriceFetchedAt: '2026-08-01T06:00:00Z' },
    ];

    spectator.service.getPortfolio().subscribe((positions) => {
      expect(positions).toEqual(expectedPositions);
    });

    spectator.expectOne('/api/fna/portfolio', HttpMethod.GET).flush(expectedPositions);
  });

  it('returns null for latest briefing when server responds with 204', () => {
    spectator.service.getLatestBriefing().subscribe((briefing) => {
      expect(briefing).toBeNull();
    });

    spectator.expectOne('/api/fna/briefing', HttpMethod.GET).flush(null, { status: 204, statusText: 'No Content' });
  });

  it('returns the latest briefing when available', () => {
    const expectedBriefing: Briefing = {
      id: 'b1',
      generatedAt: '2026-08-01T06:30:00Z',
      content: '## Portfolio Impact',
      modelUsed: 'claude-sonnet-4-20250514',
    };

    spectator.service.getLatestBriefing().subscribe((briefing) => {
      expect(briefing).toEqual(expectedBriefing);
    });

    spectator.expectOne('/api/fna/briefing', HttpMethod.GET).flush(expectedBriefing);
  });

  it('returns an HTTP error when briefing endpoint fails', () => {
    spectator.service.getLatestBriefing().subscribe({
      next: () => fail('expected an error'),
      error: (error) => expect(error.status).toBe(503),
    });

    spectator
      .expectOne('/api/fna/briefing', HttpMethod.GET)
      .flush('Unavailable', { status: 503, statusText: 'Service Unavailable' });
  });

  it('upserts a position on success', () => {
    const expectedPosition: PortfolioPosition = {
      id: 'p2',
      ticker: 'KBC',
      shareCount: 5,
      lastPrice: null,
      lastPriceFetchedAt: null,
    };

    spectator.service.upsertPosition('KBC', 5).subscribe((position) => {
      expect(position).toEqual(expectedPosition);
    });

    const request = spectator.expectOne('/api/fna/portfolio/KBC?shares=5', HttpMethod.PUT);
    request.flush(expectedPosition);
  });

  it('returns an HTTP error when upsert position fails', () => {
    spectator.service.upsertPosition('KBC', 5).subscribe({
      next: () => fail('expected an error'),
      error: (error) => expect(error.status).toBe(400),
    });

    spectator
      .expectOne('/api/fna/portfolio/KBC?shares=5', HttpMethod.PUT)
      .flush('Bad Request', { status: 400, statusText: 'Bad Request' });
  });

  it('removes a position on success', () => {
    spectator.service.removePosition('p1').subscribe(() => {
      expect(true).toBe(true);
    });

    spectator.expectOne('/api/fna/portfolio/p1', HttpMethod.DELETE).flush(null);
  });

  it('returns an HTTP error when remove position fails', () => {
    spectator.service.removePosition('p1').subscribe({
      next: () => fail('expected an error'),
      error: (error) => expect(error.status).toBe(404),
    });

    spectator
      .expectOne('/api/fna/portfolio/p1', HttpMethod.DELETE)
      .flush('Not Found', { status: 404, statusText: 'Not Found' });
  });
});
