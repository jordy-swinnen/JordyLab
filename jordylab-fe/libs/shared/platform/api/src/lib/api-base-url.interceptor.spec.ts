import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { createServiceFactory, SpectatorService } from '@ngneat/spectator/vitest';
import { apiBaseUrlInterceptor } from './api-base-url.interceptor';
import { API_BASE_URL } from './api-base-url.token';
import { PlatformService } from './platform.service';

describe('apiBaseUrlInterceptor', () => {
  let spectator: SpectatorService<HttpClient>;
  let httpMock: HttpTestingController;

  const createService = createServiceFactory({
    service: HttpClient,
    providers: [provideHttpClient(withInterceptors([apiBaseUrlInterceptor])), provideHttpClientTesting()],
  });

  function create(isNative: boolean, apiBaseUrl: string | null) {
    spectator = createService({
      providers: [
        { provide: PlatformService, useValue: { isNative: () => isNative } },
        ...(apiBaseUrl === null ? [] : [{ provide: API_BASE_URL, useValue: apiBaseUrl }]),
      ],
    });
    httpMock = spectator.inject(HttpTestingController);
  }

  it('leaves the request unchanged on web', () => {
    create(false, 'https://jordylab.example');

    spectator.service.get('/api/gamecatalog/games').subscribe();

    httpMock.expectOne('/api/gamecatalog/games');
  });

  it('prefixes the request URL with the API base URL when native', () => {
    create(true, 'https://jordylab.example');

    spectator.service.get('/api/gamecatalog/games').subscribe();

    httpMock.expectOne('https://jordylab.example/api/gamecatalog/games');
  });

  it('is a no-op when native but API_BASE_URL was never provided', () => {
    create(true, null);

    spectator.service.get('/api/gamecatalog/games').subscribe();

    httpMock.expectOne('/api/gamecatalog/games');
  });
});
