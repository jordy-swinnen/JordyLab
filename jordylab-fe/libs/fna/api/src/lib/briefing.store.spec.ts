import { createServiceFactory, SpectatorService } from '@ngneat/spectator/vitest';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { aBriefingMock } from './mocks/briefing.model.mock';
import { BriefingStore } from './briefing.store';

describe('BriefingStore', () => {
  let spectator: SpectatorService<BriefingStore>;
  let httpMock: HttpTestingController;

  const createService = createServiceFactory({
    service: BriefingStore,
    providers: [provideHttpClient(), provideHttpClientTesting()],
  });

  beforeEach(() => {
    spectator = createService();
    httpMock = spectator.inject(HttpTestingController);
  });

  it('loads the latest briefing on construction', () => {
    const req = httpMock.expectOne('/api/fna/briefing');
    req.flush(aBriefingMock());

    expect(spectator.service.briefing()).toEqual(aBriefingMock());
    expect(spectator.service.loading()).toBe(false);
  });

  it('treats a 204 response as no briefing', () => {
    const req = httpMock.expectOne('/api/fna/briefing');
    req.flush(null, { status: 204, statusText: 'No Content' });

    expect(spectator.service.briefing()).toBeNull();
  });

  it('sets an error message when loading fails', () => {
    const req = httpMock.expectOne('/api/fna/briefing');
    req.error(new ProgressEvent('error'));

    expect(spectator.service.error()).toBe('Failed to load briefing.');
    expect(spectator.service.loading()).toBe(false);
  });

  it('generates a new briefing', () => {
    httpMock.expectOne('/api/fna/briefing').flush(null, { status: 204, statusText: 'No Content' });

    spectator.service.generate();

    const req = httpMock.expectOne('/api/fna/briefing/trigger');
    req.flush(aBriefingMock());

    expect(spectator.service.briefing()).toEqual(aBriefingMock());
    expect(spectator.service.loading()).toBe(false);
  });

  it('sets an error message when generation fails', () => {
    httpMock.expectOne('/api/fna/briefing').flush(null, { status: 204, statusText: 'No Content' });

    spectator.service.generate();

    const req = httpMock.expectOne('/api/fna/briefing/trigger');
    req.error(new ProgressEvent('error'));

    expect(spectator.service.error()).toBe('Failed to generate briefing.');
    expect(spectator.service.loading()).toBe(false);
  });
});
