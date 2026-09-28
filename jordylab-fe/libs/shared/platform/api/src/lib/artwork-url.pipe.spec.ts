import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { API_BASE_URL } from './api-base-url.token';
import { ArtworkUrlPipe } from './artwork-url.pipe';
import { PlatformService } from './platform.service';

function createPipe(isNative: boolean, apiBaseUrl = 'https://jordylab.example') {
  TestBed.configureTestingModule({
    providers: [
      { provide: PlatformService, useValue: { isNative: () => isNative } },
      { provide: API_BASE_URL, useValue: apiBaseUrl },
    ],
  });

  return TestBed.runInInjectionContext(() => new ArtworkUrlPipe());
}

describe('ArtworkUrlPipe', () => {
  it('leaves the URL unchanged on web', () => {
    expect(createPipe(false).transform('/api/gamecatalog/games/1/cover')).toBe(
      '/api/gamecatalog/games/1/cover',
    );
  });

  it('prefixes a relative artwork URL with the API base URL when native', () => {
    expect(createPipe(true).transform('/api/gamecatalog/games/1/cover')).toBe(
      'https://jordylab.example/api/gamecatalog/games/1/cover',
    );
  });

  it('passes through null/undefined unchanged', () => {
    const pipe = createPipe(true);

    expect(pipe.transform(null)).toBeNull();
    expect(pipe.transform(undefined)).toBeUndefined();
  });
});
