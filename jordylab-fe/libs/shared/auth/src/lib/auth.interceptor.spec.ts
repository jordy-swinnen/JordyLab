import { createServiceFactory, SpectatorService } from '@ngneat/spectator/vitest';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AuthService } from './auth.service';
import { authInterceptor } from './auth.interceptor';

describe('authInterceptor', () => {
  let spectator: SpectatorService<HttpClient>;
  let httpMock: HttpTestingController;
  let getToken: ReturnType<typeof vi.fn>;

  const createService = createServiceFactory({
    service: HttpClient,
    providers: [
      provideHttpClient(withInterceptors([authInterceptor])),
      provideHttpClientTesting(),
      { provide: AuthService, useValue: { getToken: (...args: unknown[]) => getToken(...args) } },
    ],
  });

  beforeEach(() => {
    getToken = vi.fn();
    spectator = createService();
    httpMock = spectator.inject(HttpTestingController);
  });

  it('adds the bearer token when one is available', () => {
    getToken.mockResolvedValueOnce('the-access-token');

    spectator.service.get('/api/fna/articles').subscribe();

    return Promise.resolve().then(() => {
      const req = httpMock.expectOne('/api/fna/articles');
      expect(req.request.headers.get('Authorization')).toBe('Bearer the-access-token');
      req.flush([]);
    });
  });

  it('forwards the request unchanged when there is no token', () => {
    getToken.mockResolvedValueOnce(null);

    spectator.service.get('/api/fna/articles').subscribe();

    return Promise.resolve().then(() => {
      const req = httpMock.expectOne('/api/fna/articles');
      expect(req.request.headers.has('Authorization')).toBe(false);
      req.flush([]);
    });
  });
});
