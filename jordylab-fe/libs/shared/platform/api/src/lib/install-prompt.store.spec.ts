import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { AuthService } from '@jordylab-fe/shared/auth';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { InstallPromptStore } from './install-prompt.store';
import { AppPlatform, PlatformService } from './platform.service';

function createStore(platform: AppPlatform, hasAppRole: boolean) {
  TestBed.configureTestingModule({
    providers: [
      { provide: PlatformService, useValue: { platform: signal(platform), isNative: () => platform === 'native-android' } },
      { provide: AuthService, useValue: { hasAppRole: signal(hasAppRole) } },
    ],
  });

  return TestBed.inject(InstallPromptStore);
}

describe('InstallPromptStore', () => {
  beforeEach(() => {
    localStorage.clear();
    Object.defineProperty(navigator, 'standalone', { value: undefined, configurable: true });
    Object.defineProperty(window, 'matchMedia', {
      value: () => ({ matches: false }) as MediaQueryList,
      configurable: true,
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('shows the Android dialog for an approved user on web-android', () => {
    expect(createStore('web-android', true).promptKind()).toBe('android');
  });

  it('shows the iOS sheet for an approved user on web-ios, not already standalone', () => {
    expect(createStore('web-ios', true).promptKind()).toBe('ios');
  });

  it('shows nothing on web-desktop (QR entry is a separate, always-available UI element)', () => {
    expect(createStore('web-desktop', true).promptKind()).toBeNull();
  });

  it('shows nothing inside the native app', () => {
    expect(createStore('native-android', true).promptKind()).toBeNull();
  });

  it('shows nothing for a pending/logged-out visitor', () => {
    expect(createStore('web-android', false).promptKind()).toBeNull();
  });

  it('shows nothing on iOS when already running standalone', () => {
    Object.defineProperty(navigator, 'standalone', { value: true, configurable: true });

    expect(createStore('web-ios', true).promptKind()).toBeNull();
  });

  it('hides the prompt for 30 days after dismiss()', () => {
    const store = createStore('web-android', true);
    expect(store.promptKind()).toBe('android');

    store.dismiss();

    expect(store.promptKind()).toBeNull();
    const storedUntil = Number(localStorage.getItem('jordylab.install-prompt.dismissed-until'));
    expect(storedUntil).toBeGreaterThan(Date.now() + 29 * 24 * 60 * 60 * 1000);
  });

  it('suppressBrowserInstallPrompt() calls preventDefault on beforeinstallprompt', () => {
    const store = createStore('web-android', true);
    store.suppressBrowserInstallPrompt();
    const event = new Event('beforeinstallprompt', { cancelable: true });
    const preventDefaultSpy = vi.spyOn(event, 'preventDefault');

    window.dispatchEvent(event);

    expect(preventDefaultSpy).toHaveBeenCalled();
  });
});
