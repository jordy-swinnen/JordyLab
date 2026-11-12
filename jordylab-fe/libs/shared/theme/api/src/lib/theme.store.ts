import { computed, inject, Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { brandIconDataUri } from './brand-icon';
import {
  DEFAULT_DARK_THEME_ID,
  DEFAULT_LIGHT_THEME_ID,
  THEME_DEFINITIONS,
  THEME_STORAGE_KEY,
  ThemeId,
  ThemePreference,
} from './theme-preference.model';

const LIGHT_DEVICE_QUERY = '(prefers-color-scheme: light)';

interface DeviceLightQuery {
  readonly matches: boolean;
  addEventListener: (eventName: string, listener: (event: { matches: boolean }) => void) => void;
}

/** Some test environments have no `matchMedia`; a dark device is the app's default look. */
function deviceLightQuery(): DeviceLightQuery {
  if (typeof window.matchMedia === 'function') {
    return window.matchMedia(LIGHT_DEVICE_QUERY);
  }

  return { matches: false, addEventListener: () => undefined };
}

/**
 * Which theme the app shows, chosen per account (spec 024): the choice follows the signed-in account across devices, so
 * the desktop app shows the same theme. The device's local storage is kept as a fallback for a signed-out browser and is
 * migrated to the account on the first authenticated read. The theme itself is a `data-theme` attribute on the root
 * element, so changing it re-colours every screen without re-rendering anything. The boot script
 * (`theme-boot.js`) sets the same attribute before the first paint; this store takes over once the app has started.
 */
@Injectable({ providedIn: 'root' })
export class ThemeStore {
  readonly #http = inject(HttpClient, { optional: true });
  readonly #deviceLightQuery = deviceLightQuery();
  readonly #preference = signal<ThemePreference>(this.#readStoredPreference());
  readonly #deviceIsLight = signal(this.#deviceLightQuery.matches);

  readonly preference = this.#preference.asReadonly();
  readonly themes = THEME_DEFINITIONS;
  readonly activeTheme = computed<ThemeId>(() => {
    const preference = this.#preference();
    if (preference !== 'system') {
      return preference;
    }

    return this.#deviceIsLight() ? DEFAULT_LIGHT_THEME_ID : DEFAULT_DARK_THEME_ID;
  });

  constructor() {
    this.#deviceLightQuery.addEventListener('change', (event) => {
      this.#deviceIsLight.set(event.matches);
      this.#apply();
    });
    this.#apply();
  }

  choose(preference: ThemePreference): void {
    this.#preference.set(preference);
    this.#store(preference);
    this.#apply();
    this.#http
      ?.put<{ theme: ThemePreference }>('/api/identity/me/preferences', { theme: preference })
      .subscribe({ error: () => undefined });
  }

  /**
   * Reads the account's theme once the person is signed in. The first read migrates a device-only choice to the account,
   * so an existing user keeps the theme they had. A signed-out or offline read leaves the stored choice untouched.
   */
  syncAccountPreference(): void {
    if (!this.#http) {
      return;
    }
    this.#http.get<{ theme: ThemePreference }>('/api/identity/me/preferences').subscribe({
      next: ({ theme }) => {
        if (!this.#isKnown(theme)) {
          return;
        }
        if (theme === 'system') {
          const stored = window.localStorage.getItem(THEME_STORAGE_KEY);
          if (this.#isKnown(stored) && stored !== 'system') {
            this.#preference.set(stored as ThemePreference);
            this.#apply();
            this.#http
              ?.put<{ theme: ThemePreference }>('/api/identity/me/preferences', { theme: stored })
              .subscribe({ error: () => undefined });

            return;
          }
        }
        this.#preference.set(theme);
        this.#apply();
      },
      error: () => undefined,
    });
  }

  #isKnown(value: string | null): boolean {
    return value === 'system' || THEME_DEFINITIONS.some((definition) => definition.id === value);
  }

  #readStoredPreference(): ThemePreference {
    try {
      const stored = window.localStorage.getItem(THEME_STORAGE_KEY);
      const isKnown = stored === 'system' || THEME_DEFINITIONS.some((definition) => definition.id === stored);

      return isKnown ? (stored as ThemePreference) : 'system';
    } catch {
      return 'system';
    }
  }

  /** A device that refuses storage (private mode, storage full) keeps the choice for this session only. */
  #store(preference: ThemePreference): void {
    try {
      window.localStorage.setItem(THEME_STORAGE_KEY, preference);
    } catch {
      return;
    }
  }

  #apply(): void {
    const root = document.documentElement;
    root.setAttribute('data-theme', this.activeTheme());
    const styles = getComputedStyle(root);
    const ground = styles.getPropertyValue('--background').trim();
    if (!ground) {
      return;
    }
    document.querySelectorAll('meta[name="theme-color"]').forEach((meta) => meta.setAttribute('content', `hsl(${ground})`));
    this.#applyBrandIcon(styles.getPropertyValue('--primary').trim(), styles.getPropertyValue('--primary-foreground').trim());
  }

  /** The browser tab's icon takes the theme's accent, like the mark inside the app. */
  #applyBrandIcon(accent: string, accentInk: string): void {
    const link = document.querySelector<HTMLLinkElement>('link[rel="icon"]');
    if (!link || !accent || !accentInk) {
      return;
    }
    link.setAttribute('href', brandIconDataUri(`hsl(${accent})`, `hsl(${accentInk})`));
  }

}
