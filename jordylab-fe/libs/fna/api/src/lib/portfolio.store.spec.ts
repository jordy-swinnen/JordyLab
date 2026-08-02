import { createServiceFactory, SpectatorService } from '@ngneat/spectator/vitest';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { aPortfolioPositionMock } from './mocks/portfolio-position.model.mock';
import { PortfolioStore } from './portfolio.store';

describe('PortfolioStore', () => {
  let spectator: SpectatorService<PortfolioStore>;
  let httpMock: HttpTestingController;

  const createService = createServiceFactory({
    service: PortfolioStore,
    providers: [provideHttpClient(), provideHttpClientTesting()],
  });

  beforeEach(() => {
    spectator = createService();
    httpMock = spectator.inject(HttpTestingController);
  });

  it('loads positions on construction', () => {
    const req = httpMock.expectOne('/api/fna/portfolio');
    req.flush([aPortfolioPositionMock()]);

    expect(spectator.service.positions()).toHaveLength(1);
  });

  it('sets an error message when loading fails', () => {
    const req = httpMock.expectOne('/api/fna/portfolio');
    req.error(new ProgressEvent('error'));

    expect(spectator.service.error()).toBe('Failed to load portfolio.');
  });

  it('computes total worth from priced positions only', () => {
    const req = httpMock.expectOne('/api/fna/portfolio');
    req.flush([
      aPortfolioPositionMock({ shareCount: 10, lastPrice: 70.5 }),
      aPortfolioPositionMock({ id: 'p2', ticker: 'KBC', shareCount: 5, lastPrice: null, lastPriceFetchedAt: null }),
    ]);

    expect(spectator.service.totalWorth()).toBe(705);
    expect(spectator.service.hasAnyPrices()).toBe(true);
  });

  it('upserts a position and reloads the portfolio', () => {
    httpMock.expectOne('/api/fna/portfolio').flush([]);

    spectator.service.upsertPosition('KBC', 5);

    const upsertReq = httpMock.expectOne('/api/fna/portfolio/KBC?shares=5');
    upsertReq.flush(aPortfolioPositionMock({ ticker: 'KBC', shareCount: 5 }));

    const reloadReq = httpMock.expectOne('/api/fna/portfolio');
    reloadReq.flush([aPortfolioPositionMock({ ticker: 'KBC', shareCount: 5 })]);

    expect(spectator.service.positions()).toHaveLength(1);
  });

  it('removes a position and reloads the portfolio', () => {
    httpMock.expectOne('/api/fna/portfolio').flush([aPortfolioPositionMock()]);

    spectator.service.removePosition('p1');

    const removeReq = httpMock.expectOne('/api/fna/portfolio/p1');
    removeReq.flush(null);

    const reloadReq = httpMock.expectOne('/api/fna/portfolio');
    reloadReq.flush([]);

    expect(spectator.service.positions()).toHaveLength(0);
  });
});
