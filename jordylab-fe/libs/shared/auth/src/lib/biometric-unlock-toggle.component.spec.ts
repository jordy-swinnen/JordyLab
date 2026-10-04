import { signal } from '@angular/core';
import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { BiometricUnlockToggleComponent } from './biometric-unlock-toggle.component';
import { BiometricUnlockService } from './biometric-unlock.service';

describe('BiometricUnlockToggleComponent', () => {
  const available = signal(false);
  const enabled = signal(false);
  const refresh = vi.fn().mockResolvedValue(undefined);
  const enable = vi.fn();
  const disable = vi.fn();

  const createComponent = createComponentFactory({
    component: BiometricUnlockToggleComponent,
    providers: [
      {
        provide: BiometricUnlockService,
        useValue: {
          available: available.asReadonly(),
          enabled: enabled.asReadonly(),
          refresh,
          enable,
          disable,
        },
      },
    ],
  });

  let spectator: Spectator<BiometricUnlockToggleComponent>;

  beforeEach(() => {
    available.set(false);
    enabled.set(false);
    refresh.mockClear();
    enable.mockReset();
    disable.mockReset();
    spectator = createComponent();
  });

  it('renders nothing when biometrics are unavailable', () => {
    expect(spectator.query('input')).toBeNull();
  });

  it('shows the toggle, checked, when biometrics are available and already enabled', () => {
    available.set(true);
    enabled.set(true);
    spectator.detectChanges();

    const checkbox = spectator.query<HTMLInputElement>('input[type="checkbox"]');
    expect(checkbox).not.toBeNull();
    expect(checkbox?.checked).toBe(true);
  });

  it('enables biometric unlock when toggled on', async () => {
    available.set(true);
    enabled.set(false);
    enable.mockImplementation(async () => {
      enabled.set(true);

      return true;
    });
    spectator.detectChanges();

    spectator.click('input[type="checkbox"]');
    await Promise.resolve();
    spectator.detectChanges();

    expect(enable).toHaveBeenCalledTimes(1);
    expect(spectator.query<HTMLInputElement>('input[type="checkbox"]')?.checked).toBe(true);
  });

  it('disables biometric unlock when toggled off', async () => {
    available.set(true);
    enabled.set(true);
    disable.mockImplementation(async () => {
      enabled.set(false);
    });
    spectator.detectChanges();

    spectator.click('input[type="checkbox"]');
    await Promise.resolve();
    spectator.detectChanges();

    expect(disable).toHaveBeenCalledTimes(1);
    expect(spectator.query<HTMLInputElement>('input[type="checkbox"]')?.checked).toBe(false);
  });

  it('says so and un-ticks the box when enabling fails', async () => {
    available.set(true);
    enabled.set(false);
    enable.mockResolvedValue(false);
    spectator.detectChanges();

    spectator.click('input[type="checkbox"]');
    await Promise.resolve();
    await Promise.resolve();
    spectator.detectChanges();

    expect(spectator.query<HTMLInputElement>('input[type="checkbox"]')?.checked).toBe(false);
    expect(spectator.query('[role="alert"]')?.textContent).toContain('Could not turn on fingerprint unlock');
  });
});
