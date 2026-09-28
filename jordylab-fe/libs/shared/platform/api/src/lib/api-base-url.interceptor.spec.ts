import { HttpRequest } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { describe, expect, it } from 'vitest';
import { apiBaseUrlInterceptor } from './api-base-url.interceptor';
import { API_BASE_URL } from './api-base-url.token';
import { PlatformService } from './platform.service';

function runInterceptor(url: string, isNative: boolean, apiBaseUrl: string | null) {
  TestBed.configureTestingModule({
    providers: [
      { provide: PlatformService, useValue: { isNative: () => isNative } },
      ...(apiBaseUrl === null ? [] : [{ provide: API_BASE_URL, useValue: apiBaseUrl }]),
    ],
  });
  const request = new HttpRequest('GET', url);
  let capturedRequest: HttpRequest<unknown> | undefined;
  const next = (req: HttpRequest<unknown>) => {
    capturedRequest = req;

    return of();
  };
  TestBed.runInInjectionContext(() => apiBaseUrlInterceptor(request, next).subscribe());

  return capturedRequest;
}

describe('apiBaseUrlInterceptor', () => {
  it('leaves the request unchanged on web', () => {
    const result = runInterceptor('/api/gamecatalog/games', false, 'https://jordylab.example');

    expect(result?.url).toBe('/api/gamecatalog/games');
  });

  it('prefixes the request URL with the API base URL when native', () => {
    const result = runInterceptor('/api/gamecatalog/games', true, 'https://jordylab.example');

    expect(result?.url).toBe('https://jordylab.example/api/gamecatalog/games');
  });

  it('is a no-op when native but API_BASE_URL was never provided', () => {
    const result = runInterceptor('/api/gamecatalog/games', true, null);

    expect(result?.url).toBe('/api/gamecatalog/games');
  });
});
