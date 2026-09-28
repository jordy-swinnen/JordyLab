import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { BiometricUnlockToggleComponent } from './biometric-unlock-toggle.component';
import { BiometricUnlockService } from './biometric-unlock.service';

// The constructor's Promise.all([...]) resolves outside Angular's zoneless change-detection
// tracking, so tests flush it with two microtask hops (one for each promise, per the pattern
// established in libs/shared/platform/api's async-store specs) rather than fixture.whenStable().
async function flushMicrotasks(): Promise<void> {
  await Promise.resolve();
  await Promise.resolve();
}

describe('BiometricUnlockToggleComponent', () => {
  const isAvailable = vi.fn();
  const isEnabled = vi.fn();
  const enable = vi.fn();
  const disable = vi.fn();

  const createComponent = createComponentFactory({
    component: BiometricUnlockToggleComponent,
    providers: [
      { provide: BiometricUnlockService, useValue: { isAvailable, isEnabled, enable, disable } },
    ],
  });

  let spectator: Spectator<BiometricUnlockToggleComponent>;

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders nothing when biometrics are unavailable', async () => {
    isAvailable.mockResolvedValue(false);
    isEnabled.mockResolvedValue(false);
    spectator = createComponent();
    await flushMicrotasks();
    spectator.detectChanges();

    expect(spectator.query('input')).toBeNull();
  });

  it('shows the toggle, checked, when biometrics are available and already enabled', async () => {
    isAvailable.mockResolvedValue(true);
    isEnabled.mockResolvedValue(true);
    spectator = createComponent();
    await flushMicrotasks();
    spectator.detectChanges();

    const checkbox = spectator.query<HTMLInputElement>('input[type="checkbox"]');
    expect(checkbox).not.toBeNull();
    expect(checkbox?.checked).toBe(true);
  });

  it('enables biometric unlock when toggled on', async () => {
    isAvailable.mockResolvedValue(true);
    isEnabled.mockResolvedValue(false);
    enable.mockResolvedValue(true);
    spectator = createComponent();
    await flushMicrotasks();
    spectator.detectChanges();

    spectator.click('input[type="checkbox"]');
    await flushMicrotasks();
    spectator.detectChanges();

    expect(enable).toHaveBeenCalledTimes(1);
    expect(spectator.query<HTMLInputElement>('input[type="checkbox"]')?.checked).toBe(true);
  });

  it('disables biometric unlock when toggled off', async () => {
    isAvailable.mockResolvedValue(true);
    isEnabled.mockResolvedValue(true);
    disable.mockResolvedValue(undefined);
    spectator = createComponent();
    await flushMicrotasks();
    spectator.detectChanges();

    spectator.click('input[type="checkbox"]');
    await flushMicrotasks();
    spectator.detectChanges();

    expect(disable).toHaveBeenCalledTimes(1);
    expect(spectator.query<HTMLInputElement>('input[type="checkbox"]')?.checked).toBe(false);
  });
});
