import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { API_BASE_URL } from './api-base-url.token';
import { LatestReleaseResponse } from './latest-release.model';
import { PlatformService } from './platform.service';
import { resolveApiUrl } from './resolve-api-url';

interface DownloadLinkResponse {
  readonly downloadUrl: string;
  readonly expiresAt: string;
}

/**
 * Requests a signed download link for a release and resolves it to a navigable URL (spec FR-002,
 * contracts/mobile-releases-api.md). Used by both the web install dialog (US1) and the native
 * update prompt (US3) — the download itself is a plain navigation, not an `HttpClient` request,
 * so it needs its own base-URL resolution (research D5) rather than relying on the interceptor.
 * Returns the URL rather than navigating itself, so the actual `window.location` side effect
 * stays at the call site (the component), keeping this service a pure, testable unit.
 */
@Injectable({ providedIn: 'root' })
export class ApkDownloadService {
  readonly #http = inject(HttpClient);
  readonly #platform = inject(PlatformService);
  readonly #apiBaseUrl = inject(API_BASE_URL, { optional: true }) ?? '';

  async resolveDownloadUrl(releaseId: string): Promise<string> {
    const response = await firstValueFrom(
      this.#http.post<DownloadLinkResponse>(`/api/mobile/releases/${releaseId}/download-link`, {}),
    );

    return resolveApiUrl(response.downloadUrl, this.#platform.isNative(), this.#apiBaseUrl);
  }

  /**
   * Convenience for callers that don't already have a release id in hand — the website's install
   * dialog (US1) has no reason to run the native-only {@link UpdateCheckStore} first, so it just
   * fetches "latest" itself before requesting the download link.
   */
  async resolveLatestDownloadUrl(): Promise<string> {
    const latest = await firstValueFrom(
      this.#http.get<LatestReleaseResponse>('/api/mobile/releases/latest'),
    );

    return this.resolveDownloadUrl(latest.id);
  }
}
