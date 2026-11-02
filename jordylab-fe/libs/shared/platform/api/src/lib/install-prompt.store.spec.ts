import { signal } from '@angular/core';
import { createServiceFactory, SpectatorService } from '@ngneat/spectator/vitest';
import { AuthService } from '@jordylab-fe/shared/auth';
import { InstallPromptEvent, InstallPromptStore } from './install-prompt.store';
import { AppPlatform, PlatformService } from './platform.service';

describe('InstallPromptStore', () => {
  let spectator: SpectatorService<InstallPromptStore>;

  const createStore = createServiceFactory({
    service: InstallPromptStore,
  });

  function create(platform: AppPlatform, hasAppRole: boolean) {
    spectator = createStore({
      providers: [
        { provide: PlatformService, useValue: { platform: signal(platform) } },
        { provide: AuthService, useValue: { hasAppRole: signal(hasAppRole) } },
      ],
    });
  }

  function dispatchInstallEvent(outcome: 'accepted' | 'dismissed'): InstallPromptEvent {
    const prompt = vi.fn(() => Promise.resolve());
    const event = Object.assign(new Event('beforeinstallprompt', { cancelable: true }), {
      prompt,
      userChoice: Promise.resolve({ outcome, platform: 'web' }),
    });

    window.dispatchEvent(event);

    return event as InstallPromptEvent;
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

  it('shows the Android sheet for an approved user on web-android', () => {
    create('web-android', true);

    expect(spectator.service.promptKind()).toBe('android');
  });

  it('shows the iOS sheet for an approved user on web-ios, not already standalone', () => {
    create('web-ios', true);

    expect(spectator.service.promptKind()).toBe('ios');
  });

  it('shows nothing on web-desktop (the QR entry is a separate, always-available UI element)', () => {
    create('web-desktop', true);

    expect(spectator.service.promptKind()).toBeNull();
  });

  it('shows nothing for a pending/logged-out visitor', () => {
    create('web-android', false);

    expect(spectator.service.promptKind()).toBeNull();
  });

  it('shows nothing once the app runs standalone, on Android or iOS (FR-004)', () => {
    Object.defineProperty(window, 'matchMedia', {
      value: () => ({ matches: true }) as MediaQueryList,
      configurable: true,
    });
    create('web-android', true);

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

  it('captures the browser install event and promptInstall() opens the native prompt', async () => {
    create('web-android', true);
    spectator.service.captureInstallPrompt();
    expect(spectator.service.canPromptInstall()).toBe(false);

    const event = dispatchInstallEvent('dismissed');
    expect(spectator.service.canPromptInstall()).toBe(true);

    const outcome = await spectator.service.promptInstall();

    expect(event.prompt).toHaveBeenCalled();
    expect(outcome).toBe('dismissed');
    expect(spectator.service.canPromptInstall()).toBe(false);
  });

  it('dismisses for 30 days when the install is accepted', async () => {
    create('web-android', true);
    spectator.service.captureInstallPrompt();
    dispatchInstallEvent('accepted');

    await spectator.service.promptInstall();

    expect(spectator.service.promptKind()).toBeNull();
  });

  it('reports unavailable and does nothing when the browser never offered an install prompt', async () => {
    create('web-android', true);

    const outcome = await spectator.service.promptInstall();

    expect(outcome).toBe('unavailable');
  });

  it('stops offering the prompt once the app is installed (appinstalled)', () => {
    create('web-android', true);
    spectator.service.captureInstallPrompt();
    dispatchInstallEvent('dismissed');

    window.dispatchEvent(new Event('appinstalled'));

    expect(spectator.service.canPromptInstall()).toBe(false);
    expect(spectator.service.promptKind()).toBeNull();
  });
});
