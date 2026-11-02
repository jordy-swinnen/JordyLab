import { Injectable, signal, Signal } from '@angular/core';

export type AppPlatform = 'web-android' | 'web-ios' | 'web-desktop';

interface NavigatorUserAgentData {
  readonly mobile?: boolean;
  readonly platform?: string;
}

function detectPlatform(): AppPlatform {
  const uaData = (navigator as Navigator & { userAgentData?: NavigatorUserAgentData }).userAgentData;
  const platformHint = uaData?.platform?.toLowerCase() ?? '';
  const userAgent = navigator.userAgent.toLowerCase();

  if (platformHint.includes('android') || userAgent.includes('android')) {
    return 'web-android';
  }
  if (
    platformHint.includes('ios') ||
    /iphone|ipad|ipod/.test(userAgent) ||
    // iPadOS 13+ reports as "MacIntel" with touch support — the only reliable signal left.
    (userAgent.includes('mac') && navigator.maxTouchPoints > 1)
  ) {
    return 'web-ios';
  }

  return 'web-desktop';
}

/**
 * Tells the app shell and install surfaces which kind of browser they run in (spec 017 research D3): the install sheet
 * and the Settings → App card differ between Android, iOS and desktop. The old native-platform branch was removed with
 * the native wrapper. Computed once — the platform cannot change during a session.
 */
@Injectable({ providedIn: 'root' })
export class PlatformService {
  readonly #platform = signal<AppPlatform>(detectPlatform());

  readonly platform: Signal<AppPlatform> = this.#platform.asReadonly();
}
