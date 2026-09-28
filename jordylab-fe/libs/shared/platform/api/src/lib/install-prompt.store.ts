import { computed, inject, Injectable, signal } from '@angular/core';
import { AuthService } from '@jordylab-fe/shared/auth';
import { PlatformService } from './platform.service';

export type InstallPromptKind = 'android' | 'ios' | null;

const DISMISSAL_STORAGE_KEY = 'jordylab.install-prompt.dismissed-until';
const DISMISSAL_DURATION_MS = 30 * 24 * 60 * 60 * 1000;

function readDismissedUntil(): number | null {
  try {
    const raw = localStorage.getItem(DISMISSAL_STORAGE_KEY);

    return raw ? Number(raw) : null;
  } catch {
    return null;
  }
}

function writeDismissedUntil(until: number): void {
  try {
    localStorage.setItem(DISMISSAL_STORAGE_KEY, String(until));
  } catch {
    // Private browsing / blocked storage — the prompt will just reappear next visit.
  }
}

function isRunningStandalone(): boolean {
  const iosStandalone = (navigator as Navigator & { standalone?: boolean }).standalone;

  return iosStandalone === true || window.matchMedia?.('(display-mode: standalone)').matches === true;
}

/**
 * Chooses at most one install prompt by platform (spec FR-005, research D5,
 * contracts/app-shell-contract.md): the Android APK dialog, the iOS Add-to-Home-Screen sheet, or
 * nothing — never inside the native app, never for a pending/logged-out visitor, never once
 * already running standalone (iOS), never within 30 days of "Not now" (FR-006).
 */
@Injectable({ providedIn: 'root' })
export class InstallPromptStore {
  readonly #platform = inject(PlatformService);
  readonly #auth = inject(AuthService);

  readonly #dismissedUntil = signal<number | null>(readDismissedUntil());

  readonly promptKind = computed<InstallPromptKind>(() => {
    if (this.#platform.isNative() || !this.#auth.hasAppRole()) {
      return null;
    }
    const dismissedUntil = this.#dismissedUntil();
    if (dismissedUntil !== null && Date.now() < dismissedUntil) {
      return null;
    }

    const platform = this.#platform.platform();
    if (platform === 'web-android') {
      return 'android';
    }
    if (platform === 'web-ios' && !isRunningStandalone()) {
      return 'ios';
    }

    return null;
  });

  dismiss(): void {
    const until = Date.now() + DISMISSAL_DURATION_MS;
    this.#dismissedUntil.set(until);
    writeDismissedUntil(until);
  }

  /**
   * Suppresses Chrome's own "install web app" prompt on Android unconditionally — even after
   * "Not now" on our own dialog (FR-007, spec Edge Cases). Call once from the app shell.
   */
  suppressBrowserInstallPrompt(): void {
    window.addEventListener('beforeinstallprompt', (event) => {
      event.preventDefault();
    });
  }
}
