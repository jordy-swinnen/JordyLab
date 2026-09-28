import { inject, Pipe, PipeTransform } from '@angular/core';
import { API_BASE_URL } from './api-base-url.token';
import { PlatformService } from './platform.service';
import { resolveApiUrl } from './resolve-api-url';

/**
 * Resolves a relative `/api/...` artwork URL the same way {@link apiBaseUrlInterceptor} resolves
 * `HttpClient` requests — needed because `<img [src]="...">` bindings never go through an
 * `HttpInterceptor` (research D5, contracts/app-shell-contract.md).
 *
 * @example `<img [src]="game.coverUrl | artworkUrl">`
 */
@Pipe({ name: 'artworkUrl' })
export class ArtworkUrlPipe implements PipeTransform {
  readonly #platform = inject(PlatformService);
  readonly #apiBaseUrl = inject(API_BASE_URL, { optional: true }) ?? '';

  transform(url: string | null | undefined): string | null | undefined {
    if (!url) {
      return url;
    }

    return resolveApiUrl(url, this.#platform.isNative(), this.#apiBaseUrl);
  }
}
