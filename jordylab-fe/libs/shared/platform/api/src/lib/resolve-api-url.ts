/**
 * Prefixes a relative `/api/...` URL with the absolute API origin — only when running natively.
 * Shared by {@link apiBaseUrlInterceptor} (HttpClient requests) and {@link ArtworkUrlPipe}
 * (`<img>` bindings, which interceptors can't reach) so the two never drift (research D5,
 * contracts/app-shell-contract.md). Web behavior is always a no-op passthrough.
 */
export function resolveApiUrl(url: string, isNative: boolean, apiBaseUrl: string): string {
  if (!isNative || !url.startsWith('/api/')) {
    return url;
  }

  return apiBaseUrl.replace(/\/$/, '') + url;
}
