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
  const switchElement = (): HTMLButtonElement => spectator.query('button[role="switch"]') as HTMLButtonElement;

  beforeEach(() => {
    available.set(false);
    enabled.set(false);
    refresh.mockClear();
    enable.mockReset();
    disable.mockReset();
    spectator = createComponent();
  });

  it('explains that unlock is unavailable and disables the switch when the phone has no biometrics', () => {
    expect(spectator.query('section')?.textContent).toContain('no fingerprint');
    expect(switchElement().disabled).toBe(true);
  });

  it('shows the switch on when biometrics are available and unlock is already enabled', () => {
    available.set(true);
    enabled.set(true);
    spectator.detectChanges();

    expect(switchElement().getAttribute('aria-checked')).toBe('true');
    expect(switchElement().disabled).toBe(false);
  });

  it('enables biometric unlock when switched on', async () => {
    available.set(true);
    enable.mockImplementation(async () => {
      enabled.set(true);

      return true;
    });
    spectator.detectChanges();

    spectator.click(switchElement());
    await Promise.resolve();
    spectator.detectChanges();

    expect(enable).toHaveBeenCalledTimes(1);
    expect(switchElement().getAttribute('aria-checked')).toBe('true');
  });

  it('disables biometric unlock when switched off', async () => {
    available.set(true);
    enabled.set(true);
    disable.mockImplementation(async () => {
      enabled.set(false);
    });
    spectator.detectChanges();

    spectator.click(switchElement());
    await Promise.resolve();
    spectator.detectChanges();

    expect(disable).toHaveBeenCalledTimes(1);
    expect(switchElement().getAttribute('aria-checked')).toBe('false');
  });

  it('stays off and says why when enabling fails', async () => {
    available.set(true);
    enable.mockResolvedValue(false);
    spectator.detectChanges();

    spectator.click(switchElement());
    await Promise.resolve();
    await Promise.resolve();
    spectator.detectChanges();

    expect(switchElement().getAttribute('aria-checked')).toBe('false');
    expect(spectator.query('[role="alert"]')?.textContent).toContain('Could not turn on fingerprint unlock');
  });
});
