import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { ApkDownloadService } from './apk-download.service';
import { API_BASE_URL } from './api-base-url.token';
import { PlatformService } from './platform.service';

function createService(isNative: boolean) {
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: PlatformService, useValue: { isNative: () => isNative } },
      { provide: API_BASE_URL, useValue: 'https://jordylab.example' },
    ],
  });

  return {
    service: TestBed.inject(ApkDownloadService),
    httpMock: TestBed.inject(HttpTestingController),
  };
}

describe('ApkDownloadService', () => {
  it('resolves the relative download URL unchanged on web (no prefixing)', async () => {
    const { service, httpMock } = createService(false);

    const resolvePromise = service.resolveDownloadUrl('release-1');
    httpMock
      .expectOne('/api/mobile/releases/release-1/download-link')
      .flush({ downloadUrl: '/api/mobile/download/signed-token', expiresAt: '2026-09-28T10:05:00Z' });

    expect(await resolvePromise).toBe('/api/mobile/download/signed-token');
  });

  it('resolveLatestDownloadUrl fetches latest first, then the download link for its id', async () => {
    const { service, httpMock } = createService(false);

    const resolvePromise = service.resolveLatestDownloadUrl();
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
    const { service, httpMock } = createService(true);

    const resolvePromise = service.resolveDownloadUrl('release-1');
    httpMock
      .expectOne('/api/mobile/releases/release-1/download-link')
      .flush({ downloadUrl: '/api/mobile/download/signed-token', expiresAt: '2026-09-28T10:05:00Z' });

    expect(await resolvePromise).toBe('https://jordylab.example/api/mobile/download/signed-token');
  });
});
