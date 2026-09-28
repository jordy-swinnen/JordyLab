import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { createServiceFactory, SpectatorService } from '@ngneat/spectator/vitest';
import { LatestReleaseResponse } from './latest-release.model';
import { PlatformService } from './platform.service';
import { UpdateCheckStore } from './update-check.store';

vi.mock('@capacitor/app', () => ({
  App: {
    getInfo: vi.fn().mockResolvedValue({ name: 'JordyLab', id: 'dev.jordylab.mobile.placeholder', build: '12', version: '1.2.0' }),
    addListener: vi.fn(),
  },
}));

const RESPONSE: LatestReleaseResponse = {
  id: '7c8d9e0f-1a2b-4c3d-8e4f-5a6b7c8d9e0f',
  versionName: '1.3.0',
  versionCode: 14,
  releaseNotes: 'Fixed things.',
  sha256: 'a'.repeat(64),
  sizeBytes: 123,
  minSupportedVersionCode: 10,
  publishedAt: '2026-09-28T10:00:00Z',
  updateAvailable: true,
  updateRequired: false,
};

describe('UpdateCheckStore', () => {
  let spectator: SpectatorService<UpdateCheckStore>;
  let httpMock: HttpTestingController;

  const createStore = createServiceFactory({
    service: UpdateCheckStore,
    providers: [provideHttpClient(), provideHttpClientTesting()],
  });

  function create(isNative: boolean) {
    spectator = createStore({
      providers: [{ provide: PlatformService, useValue: { isNative: () => isNative } }],
    });
    httpMock = spectator.inject(HttpTestingController);
  }

  afterEach(() => {
    vi.clearAllMocks();
  });

  it('does nothing on web', async () => {
    create(false);

    await spectator.service.checkForUpdate();

    httpMock.expectNone(() => true);
    expect(spectator.service.latest()).toBeNull();
  });

  it('calls the latest endpoint with the native build as installedVersionCode', async () => {
    create(true);

    const checkPromise = spectator.service.checkForUpdate();
    // Let the mocked App.getInfo() promise resolve before the HTTP call is made.
    await Promise.resolve();
    await Promise.resolve();
    const req = httpMock.expectOne(
      (request) => request.url === '/api/mobile/releases/latest' && request.params.get('installedVersionCode') === '12',
    );
    req.flush(RESPONSE);
    await checkPromise;

    expect(spectator.service.latest()).toEqual(RESPONSE);
  });
});
