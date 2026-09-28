import { HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { API_BASE_URL } from './api-base-url.token';
import { PlatformService } from './platform.service';
import { resolveApiUrl } from './resolve-api-url';

/**
 * Prefixes relative `/api/...` requests with the absolute API origin when running natively
 * (research D5) — registered after `authInterceptor`. A no-op on every web build (production,
 * development), proven by tests asserting the request is unchanged when not native.
 */
export const apiBaseUrlInterceptor: HttpInterceptorFn = (req, next) => {
  const platform = inject(PlatformService);
  const apiBaseUrl = inject(API_BASE_URL, { optional: true }) ?? '';
  const resolvedUrl = resolveApiUrl(req.url, platform.isNative(), apiBaseUrl);

  if (resolvedUrl === req.url) {
    return next(req);
  }
  const resolvedRequest: HttpRequest<unknown> = req.clone({ url: resolvedUrl });

  return next(resolvedRequest);
};
