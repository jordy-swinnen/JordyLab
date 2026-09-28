import { signal } from '@angular/core';
import { createServiceFactory, SpectatorService } from '@ngneat/spectator/vitest';
import { AuthService } from '@jordylab-fe/shared/auth';
import { InstallPromptStore } from './install-prompt.store';
import { AppPlatform, PlatformService } from './platform.service';

describe('InstallPromptStore', () => {
  let spectator: SpectatorService<InstallPromptStore>;

  const createStore = createServiceFactory({
    service: InstallPromptStore,
  });

  function create(platform: AppPlatform, hasAppRole: boolean) {
    spectator = createStore({
      providers: [
        {
          provide: PlatformService,
          useValue: { platform: signal(platform), isNative: () => platform === 'native-android' },
        },
        { provide: AuthService, useValue: { hasAppRole: signal(hasAppRole) } },
      ],
    });
  }

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
    create('web-android', true);

    expect(spectator.service.promptKind()).toBe('android');
  });

  it('shows the iOS sheet for an approved user on web-ios, not already standalone', () => {
    create('web-ios', true);

    expect(spectator.service.promptKind()).toBe('ios');
  });

  it('shows nothing on web-desktop (QR entry is a separate, always-available UI element)', () => {
    create('web-desktop', true);

    expect(spectator.service.promptKind()).toBeNull();
  });

  it('shows nothing inside the native app', () => {
    create('native-android', true);

    expect(spectator.service.promptKind()).toBeNull();
  });

  it('shows nothing for a pending/logged-out visitor', () => {
    create('web-android', false);

    expect(spectator.service.promptKind()).toBeNull();
  });

  it('shows nothing on iOS when already running standalone', () => {
    Object.defineProperty(navigator, 'standalone', { value: true, configurable: true });
    create('web-ios', true);

    expect(spectator.service.promptKind()).toBeNull();
  });

  it('hides the prompt for 30 days after dismiss()', () => {
    create('web-android', true);
    expect(spectator.service.promptKind()).toBe('android');

    spectator.service.dismiss();

    expect(spectator.service.promptKind()).toBeNull();
    const storedUntil = Number(localStorage.getItem('jordylab.install-prompt.dismissed-until'));
    expect(storedUntil).toBeGreaterThan(Date.now() + 29 * 24 * 60 * 60 * 1000);
  });

  it('suppressBrowserInstallPrompt() calls preventDefault on beforeinstallprompt', () => {
    create('web-android', true);
    spectator.service.suppressBrowserInstallPrompt();
    const event = new Event('beforeinstallprompt', { cancelable: true });
    const preventDefaultSpy = vi.spyOn(event, 'preventDefault');

    window.dispatchEvent(event);

    expect(preventDefaultSpy).toHaveBeenCalled();
  });
});
