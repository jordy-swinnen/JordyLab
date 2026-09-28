import { createServiceFactory, SpectatorService } from '@ngneat/spectator/vitest';
import { API_BASE_URL } from './api-base-url.token';
import { ArtworkUrlPipe } from './artwork-url.pipe';
import { PlatformService } from './platform.service';

describe('ArtworkUrlPipe', () => {
  let spectator: SpectatorService<ArtworkUrlPipe>;

  const createPipe = createServiceFactory({
    service: ArtworkUrlPipe,
  });

  function create(isNative: boolean, apiBaseUrl = 'https://jordylab.example') {
    spectator = createPipe({
      providers: [
        { provide: PlatformService, useValue: { isNative: () => isNative } },
        { provide: API_BASE_URL, useValue: apiBaseUrl },
      ],
    });
  }

  it('leaves the URL unchanged on web', () => {
    create(false);

    expect(spectator.service.transform('/api/gamecatalog/games/1/cover')).toBe(
      '/api/gamecatalog/games/1/cover',
    );
  });

  it('prefixes a relative artwork URL with the API base URL when native', () => {
    create(true);

    expect(spectator.service.transform('/api/gamecatalog/games/1/cover')).toBe(
      'https://jordylab.example/api/gamecatalog/games/1/cover',
    );
  });

  it('passes through null/undefined unchanged', () => {
    create(true);

    expect(spectator.service.transform(null)).toBeNull();
    expect(spectator.service.transform(undefined)).toBeUndefined();
  });
});
