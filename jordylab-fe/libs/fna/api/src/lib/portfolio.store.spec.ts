import { createServiceFactory, SpectatorService } from '@ngneat/spectator/vitest';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { aPortfolioPositionMock } from './mocks/portfolio-position.model.mock';
import { aWishlistItemMock } from './mocks/wishlist-item.model.mock';
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

  function flushInitialLoad(
    positions = [] as ReturnType<typeof aPortfolioPositionMock>[],
    wishlist = [] as ReturnType<typeof aWishlistItemMock>[]
  ): void {
    httpMock.expectOne('/api/fna/portfolio').flush(positions);
    httpMock.expectOne('/api/fna/wishlist').flush(wishlist);
  }

  it('loads positions on construction', () => {
    flushInitialLoad([aPortfolioPositionMock()]);

    expect(spectator.service.positions()).toHaveLength(1);
  });

  it('loads wishlist items on construction', () => {
    flushInitialLoad([], [aWishlistItemMock()]);

    expect(spectator.service.wishlist()).toHaveLength(1);
  });

  it('sets an error message when loading fails', () => {
    const req = httpMock.expectOne('/api/fna/portfolio');
    req.error(new ProgressEvent('error'));
    httpMock.expectOne('/api/fna/wishlist').flush([]);

    expect(spectator.service.error()).toBe('Failed to load portfolio.');
  });

  it('derives each position value as shares times last price, null when unpriced', () => {
    flushInitialLoad([
      aPortfolioPositionMock({ shareCount: 0.137, lastPrice: 73805.21 }),
      aPortfolioPositionMock({ id: 'p2', ticker: 'KBC', shareCount: 5, lastPrice: null, lastPriceFetchedAt: null }),
    ]);

    const rows = spectator.service.positionRows();

    expect(rows.map((row) => row.value)).toEqual([0.137 * 73805.21, null]);
    expect(rows[0].ticker).toBe('ABI');
  });

  it('computes total worth from priced positions only', () => {
    flushInitialLoad([
      aPortfolioPositionMock({ shareCount: 10, lastPrice: 70.5 }),
      aPortfolioPositionMock({ id: 'p2', ticker: 'KBC', shareCount: 5, lastPrice: null, lastPriceFetchedAt: null }),
    ]);

    expect(spectator.service.totalWorth()).toBe(705);
    expect(spectator.service.hasAnyPrices()).toBe(true);
  });

  it('keeps wishlist items out of total worth and the priced-positions flag', () => {
    flushInitialLoad([], [aWishlistItemMock({ lastPrice: 642.8 })]);

    expect(spectator.service.wishlist()).toHaveLength(1);
    expect(spectator.service.totalWorth()).toBe(0);
    expect(spectator.service.hasAnyPrices()).toBe(false);
    expect(spectator.service.positionRows()).toHaveLength(0);
  });

  it('upserts a position and reloads the portfolio', () => {
    flushInitialLoad();

    spectator.service.upsertPosition('KBC', 5);

    const upsertReq = httpMock.expectOne('/api/fna/portfolio/KBC?shares=5');
    upsertReq.flush(aPortfolioPositionMock({ ticker: 'KBC', shareCount: 5 }));

    flushInitialLoad([aPortfolioPositionMock({ ticker: 'KBC', shareCount: 5 })]);

    expect(spectator.service.positions()).toHaveLength(1);
  });

  it('removes a position and reloads the portfolio', () => {
    flushInitialLoad([aPortfolioPositionMock()]);

    spectator.service.removePosition('p1');

    const removeReq = httpMock.expectOne('/api/fna/portfolio/p1');
    removeReq.flush(null);

    flushInitialLoad([]);

    expect(spectator.service.positions()).toHaveLength(0);
  });

  it('adds a wishlist item and reloads both lists', () => {
    flushInitialLoad();

    spectator.service.addWishlistItem('ASML');

    const addReq = httpMock.expectOne('/api/fna/wishlist/ASML');
    addReq.flush(aWishlistItemMock());

    flushInitialLoad([], [aWishlistItemMock()]);

    expect(spectator.service.wishlist()).toHaveLength(1);
  });

  it('reports a ticker already held in the portfolio without hiding the page', () => {
    flushInitialLoad();

    spectator.service.addWishlistItem('KBC');

    const addReq = httpMock.expectOne('/api/fna/wishlist/KBC');
    addReq.flush({ reason: 'TICKER_ALREADY_HELD' }, { status: 409, statusText: 'Conflict' });

    expect(spectator.service.wishlistError()).toBe('This ticker is already in your portfolio.');
    expect(spectator.service.error()).toBeNull();
    httpMock.expectNone('/api/fna/portfolio');
  });

  it('removes a wishlist item and reloads both lists', () => {
    flushInitialLoad([], [aWishlistItemMock()]);

    spectator.service.removeWishlistItem('w1');

    const removeReq = httpMock.expectOne('/api/fna/wishlist/w1');
    removeReq.flush(null);

    flushInitialLoad();

    expect(spectator.service.wishlist()).toHaveLength(0);
  });
});
