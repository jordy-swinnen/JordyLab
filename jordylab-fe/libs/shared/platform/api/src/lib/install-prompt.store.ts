import { computed, inject, Injectable, signal } from '@angular/core';
import { AuthService } from '@jordylab-fe/shared/auth';
import { PlatformService } from './platform.service';

export type InstallPromptKind = 'android' | 'ios' | null;

/**
 * Chromium's install event (`beforeinstallprompt`) lets a page trigger the browser's own install flow. It is not part of
 * lib.dom, so the shape the app relies on is declared here (spec 018 FR-003, research D3).
 */
export interface InstallPromptEvent extends Event {
  prompt(): Promise<void>;
  readonly userChoice: Promise<{ outcome: 'accepted' | 'dismissed'; platform: string }>;
}

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

/** True when the app is already open as the installed app rather than in a browser tab (FR-004). */
export function isRunningStandalone(): boolean {
  const iosStandalone = (navigator as Navigator & { standalone?: boolean }).standalone;

  return iosStandalone === true || window.matchMedia?.('(display-mode: standalone)').matches === true;
}

/**
 * The one-time install surface (spec 018 FR-003–FR-006, research D3): Android gets the browser's native install prompt
 * when the browser offered it (otherwise instructions), iOS gets the Add-to-Home-Screen sheet, desktop gets nothing here
 * (the sidebar QR entry is separate and always available). Never for a pending/logged-out visitor, never while already
 * running standalone, never within 30 days of "Not now".
 */
@Injectable({ providedIn: 'root' })
export class InstallPromptStore {
  readonly #platform = inject(PlatformService);
  readonly #auth = inject(AuthService);

  readonly #dismissedUntil = signal<number | null>(readDismissedUntil());
  readonly #installEvent = signal<InstallPromptEvent | null>(null);

  /** True when the browser offered its install prompt, so installing is one tap instead of instructions. */
  readonly canPromptInstall = computed(() => this.#installEvent() !== null);

  readonly promptKind = computed<InstallPromptKind>(() => {
    if (!this.#auth.hasAppRole() || isRunningStandalone()) {
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
    if (platform === 'web-ios') {
      return 'ios';
    }

    return null;
  });

  /**
   * Stashes the browser's install event instead of suppressing it (the old APK-era behaviour). Call once from the app
   * shell; the sheet and the Settings → App card both use the stashed event.
   */
  captureInstallPrompt(): void {
    window.addEventListener('beforeinstallprompt', (event) => {
      // Chrome's own mini-infobar is replaced by our sheet / the Settings card, but the event is kept, not dropped.
      event.preventDefault();
      this.#installEvent.set(event as InstallPromptEvent);
    });
    window.addEventListener('appinstalled', () => {
      this.#installEvent.set(null);
      this.dismiss();
    });
  }

  /**
   * Opens the browser's install flow. `unavailable` when the browser never offered one — callers show instructions
   * instead. A prompt can only be used once, so the stashed event is consumed either way.
   */
  async promptInstall(): Promise<'accepted' | 'dismissed' | 'unavailable'> {
    const installEvent = this.#installEvent();
    if (!installEvent) {
      return 'unavailable';
    }
    await installEvent.prompt();
    const choice = await installEvent.userChoice;
    this.#installEvent.set(null);
    if (choice.outcome === 'accepted') {
      this.dismiss();
    }

    return choice.outcome;
  }

  dismiss(): void {
    const until = Date.now() + DISMISSAL_DURATION_MS;
    this.#dismissedUntil.set(until);
    writeDismissedUntil(until);
  }
}
