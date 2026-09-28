import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { LatestReleaseResponse } from './latest-release.model';
import { PlatformService } from './platform.service';
import { UpdateCheckStore } from './update-check.store';

vi.mock('@capacitor/app', () => ({
  App: {
    getInfo: vi.fn().mockResolvedValue({ name: 'JordyLab', id: 'dev.jordylab.mobile.placeholder', build: '12', version: '1.2.0' }),
    addListener: vi.fn(),
  },
}));

function createStore(isNative: boolean) {
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: PlatformService, useValue: { isNative: () => isNative } },
    ],
  });

  return {
    store: TestBed.inject(UpdateCheckStore),
    httpMock: TestBed.inject(HttpTestingController),
  };
}

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
  afterEach(() => {
    vi.clearAllMocks();
  });

  it('does nothing on web', async () => {
    const { store, httpMock } = createStore(false);

    await store.checkForUpdate();

    httpMock.expectNone(() => true);
    expect(store.latest()).toBeNull();
  });

  it('calls the latest endpoint with the native build as installedVersionCode', async () => {
    const { store, httpMock } = createStore(true);

    const checkPromise = store.checkForUpdate();
    // Let the mocked App.getInfo() promise resolve before the HTTP call is made.
    await Promise.resolve();
    await Promise.resolve();
    const req = httpMock.expectOne(
      (request) => request.url === '/api/mobile/releases/latest' && request.params.get('installedVersionCode') === '12',
    );
    req.flush(RESPONSE);
    await checkPromise;

    expect(store.latest()).toEqual(RESPONSE);
  });
});
