import { HttpClient } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { App } from '@capacitor/app';
import { firstValueFrom } from 'rxjs';
import { LatestReleaseResponse } from './latest-release.model';
import { PlatformService } from './platform.service';

/**
 * Calls `GET /api/mobile/releases/latest` on app start and resume (spec FR-011,
 * contracts/mobile-releases-api.md) — a no-op on web. The installed `versionCode` always comes
 * from the native build via `App.getInfo()` (research D11), never hardcoded in TypeScript.
 */
@Injectable({ providedIn: 'root' })
export class UpdateCheckStore {
  readonly #http = inject(HttpClient);
  readonly #platform = inject(PlatformService);

  readonly #latest = signal<LatestReleaseResponse | null>(null);

  readonly latest = this.#latest.asReadonly();

  async checkForUpdate(): Promise<void> {
    if (!this.#platform.isNative()) {
      return;
    }
    const info = await App.getInfo();
    const response = await firstValueFrom(
      this.#http.get<LatestReleaseResponse>('/api/mobile/releases/latest', {
        params: { installedVersionCode: info.build },
      }),
    );
    this.#latest.set(response);
  }

  listenForResume(): void {
    App.addListener('resume', () => {
      void this.checkForUpdate();
    });
  }
}
