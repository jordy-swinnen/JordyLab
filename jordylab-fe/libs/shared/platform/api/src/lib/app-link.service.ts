import { inject, Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { App } from '@capacitor/app';
import { AuthService } from '@jordylab-fe/shared/auth';
import { PlatformService } from './platform.service';

const MOBILE_CALLBACK_PATH = '/mobile/callback';
const MOBILE_OPEN_PATH = '/mobile/open';

/**
 * `screen` → in-app route (contracts/app-shell-contract.md's App Link routing table, research
 * D9). `mobile.MobileNotificationListener` on the backend builds click-through URLs using these
 * exact `screen` values.
 */
const SCREEN_ROUTES: Readonly<Record<string, string>> = {
  'settings-users': '/settings/users',
  'fna-briefing': '/fna/briefing',
};

/**
 * Listens for the Android App Link (`https://{PRODUCTION_DOMAIN}/mobile/*`, research D2/D9) and
 * routes it by path: `/mobile/callback` completes the native login flow ({@link AuthService});
 * `/mobile/open?screen=` routes in-app per {@link SCREEN_ROUTES}. A no-op on web — `appUrlOpen`
 * only fires natively, but the check keeps this consistent with the rest of `libs/shared/platform`.
 */
@Injectable({ providedIn: 'root' })
export class AppLinkService {
  readonly #platform = inject(PlatformService);
  readonly #auth = inject(AuthService);
  readonly #router = inject(Router);

  listen(): void {
    if (!this.#platform.isNative()) {
      return;
    }
    App.addListener('appUrlOpen', (event) => {
      void this.#handleUrlOpen(event.url);
    });
  }

  async #handleUrlOpen(url: string): Promise<void> {
    const parsed = new URL(url);

    if (parsed.pathname === MOBILE_CALLBACK_PATH) {
      await this.#auth.completeNativeLogin(url);

      return;
    }

    if (parsed.pathname === MOBILE_OPEN_PATH) {
      const screen = parsed.searchParams.get('screen');
      const route = screen ? SCREEN_ROUTES[screen] : undefined;
      if (route) {
        await this.#router.navigateByUrl(route);
      } else {
        console.error('Unknown App Link screen', screen);
      }
    }
  }
}
