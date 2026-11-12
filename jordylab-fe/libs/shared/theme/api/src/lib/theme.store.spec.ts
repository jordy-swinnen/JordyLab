import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { HttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
// The colour file is a workspace-root file, not a library, so it has no alias to import through.
// eslint-disable-next-line @nx/enforce-module-boundaries
import { themes as themeFile } from '../../../../../../themes.js';
import { THEME_DEFINITIONS, THEME_STORAGE_KEY } from './theme-preference.model';
import { ThemeStore } from './theme.store';

type ChangeListener = (event: { matches: boolean }) => void;

describe('ThemeStore', () => {
  let deviceIsLight = false;
  let changeListener: ChangeListener | undefined;

  beforeEach(() => {
    window.localStorage.clear();
    document.documentElement.removeAttribute('data-theme');
    document.head.innerHTML = '<meta name="theme-color" content="#000000"><link rel="icon" href="favicon.svg">';
    deviceIsLight = false;
    changeListener = undefined;
    vi.stubGlobal(
      'matchMedia',
      vi.fn(() => ({
        get matches() {
          return deviceIsLight;
        },
        addEventListener: (_eventName: string, listener: ChangeListener) => {
          changeListener = listener;
        },
      })),
    );
    window.matchMedia = globalThis.matchMedia;
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  function createStore(): ThemeStore {
    TestBed.resetTestingModule();

    return TestBed.inject(ThemeStore);
  }

  it('shows Content Led on a dark device with nothing stored', () => {
    const store = createStore();

    expect(store.activeTheme()).toBe('content');
    expect(document.documentElement.getAttribute('data-theme')).toBe('content');
  });

  it('shows Paper on a light device with nothing stored', () => {
    deviceIsLight = true;

    expect(createStore().activeTheme()).toBe('paper');
  });

  it('prefers a stored theme over the device setting', () => {
    window.localStorage.setItem(THEME_STORAGE_KEY, 'heather');

    const store = createStore();

    expect(store.activeTheme()).toBe('heather');
    expect(store.preference()).toBe('heather');
  });

  it('treats an unknown stored value as following the device', () => {
    window.localStorage.setItem(THEME_STORAGE_KEY, 'neon');

    expect(createStore().preference()).toBe('system');
  });

  it('remembers a chosen theme and applies it at once', () => {
    const store = createStore();

    store.choose('oxblood');

    expect(window.localStorage.getItem(THEME_STORAGE_KEY)).toBe('oxblood');
    expect(document.documentElement.getAttribute('data-theme')).toBe('oxblood');
  });

  it('follows a change of the device setting while following the device', () => {
    const store = createStore();

    changeListener?.({ matches: true });

    expect(store.activeTheme()).toBe('paper');
    expect(document.documentElement.getAttribute('data-theme')).toBe('paper');
  });

  it('ignores a change of the device setting once a theme is chosen', () => {
    const store = createStore();
    store.choose('flare');

    changeListener?.({ matches: true });

    expect(store.activeTheme()).toBe('flare');
  });

  it('keeps the choice for the session when storage refuses to write', () => {
    const store = createStore();
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('storage full');
    });

    store.choose('glasshouse');

    expect(store.activeTheme()).toBe('glasshouse');
  });

  it('starts on the device setting when storage refuses to read', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('storage blocked');
    });

    expect(createStore().preference()).toBe('system');
  });

  it('puts the active ground colour in the browser chrome colour', () => {
    document.documentElement.style.setProperty('--background', '240 3% 8%');
    const store = createStore();

    store.choose('paper');

    expect(document.querySelector('meta[name="theme-color"]')?.getAttribute('content')).toBe('hsl(240 3% 8%)');
  });

});

describe('ThemeStore account preference', () => {
  beforeEach(() => {
    window.localStorage.clear();
    window.matchMedia = vi.fn(() => ({
      matches: false,
      addEventListener: vi.fn(),
    })) as unknown as typeof window.matchMedia;
  });

  function createStoreWith(http: unknown): ThemeStore {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ providers: [{ provide: HttpClient, useValue: http }] });

    return TestBed.inject(ThemeStore);
  }

  it('applies the theme stored on the account', () => {
    const http = { get: vi.fn(() => of({ theme: 'heather' })), put: vi.fn(() => of({})) };
    const store = createStoreWith(http);

    store.syncAccountPreference();

    expect(store.preference()).toBe('heather');
    expect(document.documentElement.getAttribute('data-theme')).toBe('heather');
  });

  it('migrates a device-only choice to the account on the first read', () => {
    window.localStorage.setItem(THEME_STORAGE_KEY, 'oxblood');
    const http = { get: vi.fn(() => of({ theme: 'system' })), put: vi.fn(() => of({})) };
    const store = createStoreWith(http);

    store.syncAccountPreference();

    expect(store.preference()).toBe('oxblood');
    expect(http.put).toHaveBeenCalledWith('/api/identity/me/preferences', { theme: 'oxblood' });
  });

  it('writes a chosen theme to the account', () => {
    const http = { get: vi.fn(() => of({ theme: 'system' })), put: vi.fn(() => of({})) };
    const store = createStoreWith(http);

    store.choose('flare');

    expect(http.put).toHaveBeenCalledWith('/api/identity/me/preferences', { theme: 'flare' });
  });

  it('keeps the stored choice when the account read fails', () => {
    window.localStorage.setItem(THEME_STORAGE_KEY, 'glasshouse');
    const http = { get: vi.fn(() => throwError(() => new Error('offline'))), put: vi.fn(() => of({})) };
    const store = createStoreWith(http);

    store.syncAccountPreference();

    expect(store.preference()).toBe('glasshouse');
  });
});

describe('ThemeStore browser tab icon', () => {
  beforeEach(() => {
    document.head.innerHTML = '<meta name="theme-color" content="#000000"><link rel="icon" href="favicon.svg">';
    document.documentElement.style.setProperty('--background', '240 3% 8%');
    document.documentElement.style.setProperty('--primary', '350 82% 62%');
    document.documentElement.style.setProperty('--primary-foreground', '353 80% 9%');
    window.matchMedia = vi.fn(() => ({ matches: false, addEventListener: vi.fn() })) as unknown as typeof window.matchMedia;
  });

  afterEach(() => {
    document.documentElement.removeAttribute('style');
  });

  it('draws the mark in the accent colours of the active theme', () => {
    TestBed.resetTestingModule();
    TestBed.inject(ThemeStore);

    const icon = decodeURIComponent(document.querySelector('link[rel="icon"]')?.getAttribute('href') ?? '');

    expect(icon).toContain('fill="hsl(350 82% 62%)"');
    expect(icon).toContain('stroke="hsl(353 80% 9%)"');
  });

  it('leaves the icon alone when the page has none', () => {
    document.head.innerHTML = '';
    TestBed.resetTestingModule();

    expect(() => TestBed.inject(ThemeStore)).not.toThrow();
  });
});

describe('theme lists', () => {
  const themeIds = THEME_DEFINITIONS.map((definition) => definition.id);

  it('match the colour file in id, name and kind', () => {
    expect(THEME_DEFINITIONS.map(({ id, name, kind }) => ({ id, name, kind }))).toEqual(
      themeFile.map(({ id, name, kind }) => ({ id, name, kind })),
    );
  });

  it('match the id list in the boot script', () => {
    const bootScript = readFileSync(resolve(import.meta.dirname, '../../../../../../apps/jordylab/public/theme-boot.js'), 'utf8');
    const idsInScript = [...(/const themeIds = \[([^\]]*)\]/.exec(bootScript)?.[1] ?? '').matchAll(/'([a-z]+)'/g)].map((match) => match[1]);

    expect(idsInScript).toEqual(themeIds);
  });

});
