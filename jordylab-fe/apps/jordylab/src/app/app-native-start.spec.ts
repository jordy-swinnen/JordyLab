import { signal } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { RouterModule } from '@angular/router';
import { createComponentFactory } from '@ngneat/spectator/vitest';
import { AuthService, BiometricUnlockService } from '@jordylab-fe/shared/auth';
import { UsersStore } from '@jordylab-fe/settings/api';
import {
  AppLinkService,
  PlatformService,
  ShareTargetService,
  UpdateCheckStore,
} from '@jordylab-fe/shared/platform/api';
import { App } from './app';

describe('App on a native cold start', () => {
  const authInit = vi.fn();
  const biometricIsEnabled = vi.fn();
  const biometricUnlock = vi.fn();
  const checkForUpdate = vi.fn();

  const createComponent = createComponentFactory({
    component: App,
    imports: [RouterModule.forRoot([])],
    detectChanges: false,
    providers: [
      provideHttpClient(),
      {
        provide: AuthService,
        useValue: {
          username: () => 'jordy',
          init: authInit,
          logout: vi.fn(),
          hasAppRole: signal(true).asReadonly(),
          isAdmin: signal(false).asReadonly(),
        },
      },
      { provide: UsersStore, useValue: { pendingCount: signal(0).asReadonly() } },
      {
        provide: BiometricUnlockService,
        useValue: {
          disable: vi.fn(),
          isEnabled: biometricIsEnabled,
          unlock: biometricUnlock,
          available: signal(false).asReadonly(),
          enabled: signal(false).asReadonly(),
          refresh: () => Promise.resolve(),
        },
      },
      { provide: PlatformService, useValue: { isNative: () => true, platform: () => 'native-android' } },
      { provide: AppLinkService, useValue: { listen: vi.fn() } },
      { provide: ShareTargetService, useValue: { listen: vi.fn() } },
      {
        provide: UpdateCheckStore,
        useValue: { latest: signal(null).asReadonly(), checkForUpdate, listenForResume: vi.fn() },
      },
    ],
  });

  const settle = (): Promise<void> => new Promise((resolve) => setTimeout(resolve));

  beforeEach(() => {
    authInit.mockReset().mockResolvedValue(false);
    biometricIsEnabled.mockReset().mockResolvedValue(false);
    biometricUnlock.mockReset().mockResolvedValue(true);
    checkForUpdate.mockReset();
  });

  it('asks for the fingerprint when unlock is enabled and there is no session', async () => {
    biometricIsEnabled.mockResolvedValue(true);

    createComponent();
    await settle();

    expect(biometricUnlock).toHaveBeenCalledTimes(1);
  });

  it('does not ask when the session is already restored', async () => {
    authInit.mockResolvedValue(true);
    biometricIsEnabled.mockResolvedValue(true);

    createComponent();
    await settle();

    expect(biometricUnlock).not.toHaveBeenCalled();
  });

  it('does not ask when the user did not enable fingerprint unlock', async () => {
    createComponent();
    await settle();

    expect(biometricUnlock).not.toHaveBeenCalled();
  });

  it('survives a failing unlock without an unhandled rejection', async () => {
    biometricIsEnabled.mockResolvedValue(true);
    biometricUnlock.mockRejectedValue(new Error('boom'));
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => undefined);

    createComponent();
    await settle();

    expect(consoleError).toHaveBeenCalled();
    consoleError.mockRestore();
  });

  it('checks for an app update once the user has an application role', async () => {
    createComponent({ detectChanges: true });
    await settle();

    expect(checkForUpdate).toHaveBeenCalled();
  });

  it('offers the App settings page to a guest on native, without the admin-only Settings entries', () => {
    const spectator = createComponent();

    const settings = spectator.component['visibleGroups']().find((group) => group.label === 'Settings');

    expect(settings?.items.map((item) => item.path)).toEqual(['/settings/app']);
  });
});
