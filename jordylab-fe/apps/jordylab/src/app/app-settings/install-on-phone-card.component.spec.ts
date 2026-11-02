import { signal } from '@angular/core';
import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { InstallPromptStore, PlatformService } from '@jordylab-fe/shared/platform/api';
import { InstallOnPhoneCardComponent } from './install-on-phone-card.component';

describe('InstallOnPhoneCardComponent', () => {
  const platform = signal<'web-android' | 'web-ios' | 'web-desktop'>('web-android');
  const canPromptInstall = signal(false);
  const promptInstall = vi.fn(() => Promise.resolve<'accepted' | 'dismissed' | 'unavailable'>('accepted'));

  const storeMock = {
    canPromptInstall: canPromptInstall.asReadonly(),
    promptInstall,
  };

  const createComponent = createComponentFactory({
    component: InstallOnPhoneCardComponent,
    providers: [
      { provide: InstallPromptStore, useValue: storeMock },
      { provide: PlatformService, useValue: { platform: platform.asReadonly() } },
    ],
  });

  let spectator: Spectator<InstallOnPhoneCardComponent>;

  beforeEach(() => {
    platform.set('web-android');
    canPromptInstall.set(false);
    promptInstall.mockClear();
    Object.defineProperty(window, 'matchMedia', {
      value: () => ({ matches: false }) as MediaQueryList,
      writable: true,
      configurable: true,
    });
    spectator = createComponent();
  });

  it('shows on an Android browser that is not running the installed app', () => {
    expect(spectator.query('[data-testid="install-on-phone"]')).toBeTruthy();
  });

  it('shows on an iOS browser that is not running the installed app', () => {
    platform.set('web-ios');
    spectator.detectChanges();

    expect(spectator.query('[data-testid="install-on-phone"]')).toBeTruthy();
  });

  it('is absent on desktop', () => {
    platform.set('web-desktop');
    spectator.detectChanges();

    expect(spectator.query('[data-testid="install-on-phone"]')).toBeFalsy();
  });

  it('is absent once the app runs standalone (nothing left to install)', () => {
    Object.defineProperty(window, 'matchMedia', {
      value: () => ({ matches: true }) as MediaQueryList,
      writable: true,
      configurable: true,
    });
    spectator = createComponent();

    expect(spectator.query('[data-testid="install-on-phone"]')).toBeFalsy();
  });

  it('offers the install button when the browser provided its install prompt', () => {
    canPromptInstall.set(true);
    spectator.detectChanges();

    spectator.click('[data-testid="install-on-phone"] button');

    expect(promptInstall).toHaveBeenCalled();
  });

  it('falls back to step-by-step instructions when no install prompt is available', () => {
    expect(spectator.queryAll('[data-testid="install-on-phone"] li')).toHaveLength(3);
    expect(spectator.query('[data-testid="install-on-phone"] button')).toBeFalsy();
  });

  it('always carries the note about uninstalling the old Android app', () => {
    expect(spectator.query('[data-testid="install-on-phone"]')?.textContent).toContain('Uninstall it');
  });
});
