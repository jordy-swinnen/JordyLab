import {
  createServiceFactory,
  SpectatorService,
} from '@ngneat/spectator/vitest';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { aPriceHistoryMock } from './mocks/price-history.model.mock';
import { PriceHistoryStore } from './price-history.store';

describe('PriceHistoryStore', () => {
  let spectator: SpectatorService<PriceHistoryStore>;
  let httpMock: HttpTestingController;

  const createService = createServiceFactory({
    service: PriceHistoryStore,
    providers: [provideHttpClient(), provideHttpClientTesting()],
  });

  beforeEach(() => {
    spectator = createService();
    httpMock = spectator.inject(HttpTestingController);
  });

  it('loads the recorded history of a holding for the chosen period', () => {
    spectator.service.load('p1', 'ONE_YEAR');

    const request = httpMock.expectOne(
      (req) =>
        req.url === '/api/fna/portfolio/p1/price-history' &&
        req.params.get('period') === 'ONE_YEAR',
    );
    request.flush(aPriceHistoryMock());

    expect(spectator.service.history()?.status).toBe('AVAILABLE');
    expect(spectator.service.isLoading()).toBe(false);
    expect(spectator.service.error()).toBeNull();
  });

  it('keeps a no-history answer as a status, not as an error', () => {
    spectator.service.load('p1', 'MAX');

    httpMock
      .expectOne((req) => req.params.get('period') === 'MAX')
      .flush(
        aPriceHistoryMock({
          period: 'MAX',
          status: 'NO_HISTORY_YET',
          points: [],
          summary: null,
        }),
      );

    expect(spectator.service.history()?.status).toBe('NO_HISTORY_YET');
    expect(spectator.service.error()).toBeNull();
  });

  it('says a failed request in plain words and forgets the old history', () => {
    spectator.service.load('p1', 'ONE_YEAR');

    httpMock
      .expectOne((req) => req.url === '/api/fna/portfolio/p1/price-history')
      .flush('', { status: 404, statusText: 'Not Found' });

    expect(spectator.service.error()).toBe(
      "Couldn't load the price history. Try again in a moment.",
    );
    expect(spectator.service.history()).toBeNull();
  });

  it('keeps only the latest response when a newer holding is requested while the older one is in flight', () => {
    spectator.service.load('p1', 'ONE_YEAR');
    const first = httpMock.expectOne(
      (req) => req.url === '/api/fna/portfolio/p1/price-history',
    );
    spectator.service.load('p2', 'ONE_YEAR');
    const second = httpMock.expectOne(
      (req) => req.url === '/api/fna/portfolio/p2/price-history',
    );

    second.flush(aPriceHistoryMock({ positionId: 'p2' }));

    expect(first.cancelled).toBe(true);
    expect(spectator.service.history()?.positionId).toBe('p2');
  });

  it('reloads the history when the period changes', () => {
    spectator.service.load('p1', 'ONE_YEAR');
    httpMock
      .expectOne((req) => req.params.get('period') === 'ONE_YEAR')
      .flush(aPriceHistoryMock());

    spectator.service.load('p1', 'MAX');
    httpMock
      .expectOne((req) => req.params.get('period') === 'MAX')
      .flush(aPriceHistoryMock({ period: 'MAX' }));

    expect(spectator.service.history()?.period).toBe('MAX');
  });

  it('clear forgets the history and cancels the request still in flight', () => {
    spectator.service.load('p1', 'ONE_YEAR');
    const request = httpMock.expectOne(
      (req) => req.url === '/api/fna/portfolio/p1/price-history',
    );

    spectator.service.clear();

    expect(request.cancelled).toBe(true);
    expect(spectator.service.history()).toBeNull();
    expect(spectator.service.isLoading()).toBe(false);
  });
});
