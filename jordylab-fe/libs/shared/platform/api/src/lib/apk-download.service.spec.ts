import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { createServiceFactory, SpectatorService } from '@ngneat/spectator/vitest';
import { ApkDownloadService } from './apk-download.service';
import { API_BASE_URL } from './api-base-url.token';
import { PlatformService } from './platform.service';

describe('ApkDownloadService', () => {
  let spectator: SpectatorService<ApkDownloadService>;
  let httpMock: HttpTestingController;

  const createService = createServiceFactory({
    service: ApkDownloadService,
    providers: [provideHttpClient(), provideHttpClientTesting()],
  });

  function create(isNative: boolean) {
    spectator = createService({
      providers: [
        { provide: PlatformService, useValue: { isNative: () => isNative } },
        { provide: API_BASE_URL, useValue: 'https://jordylab.example' },
      ],
    });
    httpMock = spectator.inject(HttpTestingController);
  }

  it('resolves the relative download URL unchanged on web (no prefixing)', async () => {
    create(false);

    const resolvePromise = spectator.service.resolveDownloadUrl('release-1');
    httpMock
      .expectOne('/api/mobile/releases/release-1/download-link')
      .flush({ downloadUrl: '/api/mobile/download/signed-token', expiresAt: '2026-09-28T10:05:00Z' });

    expect(await resolvePromise).toBe('/api/mobile/download/signed-token');
  });

  it('resolveLatestDownloadUrl fetches latest first, then the download link for its id', async () => {
    create(false);

    const resolvePromise = spectator.service.resolveLatestDownloadUrl();
    httpMock
      .expectOne('/api/mobile/releases/latest')
      .flush({ id: 'release-1', versionName: '1.3.0', versionCode: 14, releaseNotes: '', sha256: 'a'.repeat(64),
        sizeBytes: 1, minSupportedVersionCode: 1, publishedAt: '2026-09-28T10:00:00Z', updateAvailable: false,
        updateRequired: false });
    await Promise.resolve();
    await Promise.resolve();
    httpMock
      .expectOne('/api/mobile/releases/release-1/download-link')
      .flush({ downloadUrl: '/api/mobile/download/signed-token', expiresAt: '2026-09-28T10:05:00Z' });

    expect(await resolvePromise).toBe('/api/mobile/download/signed-token');
  });

  it('prefixes the download URL with the API base URL when native', async () => {
    create(true);

    const resolvePromise = spectator.service.resolveDownloadUrl('release-1');
    httpMock
      .expectOne('/api/mobile/releases/release-1/download-link')
      .flush({ downloadUrl: '/api/mobile/download/signed-token', expiresAt: '2026-09-28T10:05:00Z' });

    expect(await resolvePromise).toBe('https://jordylab.example/api/mobile/download/signed-token');
  });
});
