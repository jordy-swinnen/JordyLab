import { Injectable, signal, Signal } from '@angular/core';
import { Capacitor } from '@capacitor/core';

export type AppPlatform = 'web-android' | 'web-ios' | 'web-desktop' | 'native-android';

interface NavigatorUserAgentData {
  readonly mobile?: boolean;
  readonly platform?: string;
}

function detectWebPlatform(): 'web-android' | 'web-ios' | 'web-desktop' {
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

function detectPlatform(): AppPlatform {
  if (Capacitor.isNativePlatform()) {
    return 'native-android';
  }

  return detectWebPlatform();
}

/**
 * Tells the app shell and install-prompt store which platform it's running on (research D5,
 * contracts/app-shell-contract.md). Computed once — the platform can't change during a session.
 */
@Injectable({ providedIn: 'root' })
export class PlatformService {
  readonly #platform = signal<AppPlatform>(detectPlatform());

  readonly platform: Signal<AppPlatform> = this.#platform.asReadonly();

  isNative(): boolean {
    return this.#platform() === 'native-android';
  }
}
