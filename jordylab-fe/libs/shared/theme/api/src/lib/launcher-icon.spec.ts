import { Capacitor, registerPlugin } from '@capacitor/core';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { setLauncherIcon } from './launcher-icon';

const setOnPlugin = vi.hoisted(() => vi.fn());

vi.mock('@capacitor/core', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@capacitor/core')>()),
  registerPlugin: vi.fn(() => ({ set: setOnPlugin })),
}));

describe('setLauncherIcon', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    setOnPlugin.mockReset();
  });

  it('registers the native LauncherIcon plugin', () => {
    expect(registerPlugin).toHaveBeenCalledWith('LauncherIcon');
  });

  it('does nothing in the browser', async () => {
    vi.spyOn(Capacitor, 'isNativePlatform').mockReturnValue(false);

    await setLauncherIcon('flare');

    expect(setOnPlugin).not.toHaveBeenCalled();
  });

  it('asks the native plugin for the theme icon in the app', async () => {
    vi.spyOn(Capacitor, 'isNativePlatform').mockReturnValue(true);
    setOnPlugin.mockResolvedValue({ theme: 'flare' });

    await setLauncherIcon('flare');

    expect(setOnPlugin).toHaveBeenCalledWith({ theme: 'flare' });
  });

  it('keeps going when an older build has no such plugin', async () => {
    vi.spyOn(Capacitor, 'isNativePlatform').mockReturnValue(true);
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => undefined);
    setOnPlugin.mockRejectedValue({ code: 'UNIMPLEMENTED' });

    await expect(setLauncherIcon('flare')).resolves.toBeUndefined();
    expect(warn).not.toHaveBeenCalled();
  });

  it('logs any other failure and carries on', async () => {
    vi.spyOn(Capacitor, 'isNativePlatform').mockReturnValue(true);
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => undefined);
    setOnPlugin.mockRejectedValue(new Error('Unknown theme'));

    await expect(setLauncherIcon('flare')).resolves.toBeUndefined();
    expect(warn).toHaveBeenCalled();
  });
});
