import { Capacitor } from '@capacitor/core';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { PlatformService } from './platform.service';

function stubNavigator(userAgent: string, userAgentData?: { mobile?: boolean; platform?: string }, maxTouchPoints = 0) {
  Object.defineProperty(navigator, 'userAgent', { value: userAgent, configurable: true });
  Object.defineProperty(navigator, 'maxTouchPoints', { value: maxTouchPoints, configurable: true });
  Object.defineProperty(navigator, 'userAgentData', { value: userAgentData, configurable: true });
}

describe('PlatformService', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    delete (navigator as Navigator & { userAgentData?: unknown }).userAgentData;
  });

  it('reports native-android when Capacitor.isNativePlatform() is true, regardless of UA', () => {
    vi.spyOn(Capacitor, 'isNativePlatform').mockReturnValue(true);
    stubNavigator('Mozilla/5.0 (Linux; Android 14)');

    expect(new PlatformService().platform()).toBe('native-android');
  });

  it('reports web-android from userAgentData when not native', () => {
    vi.spyOn(Capacitor, 'isNativePlatform').mockReturnValue(false);
    stubNavigator('Mozilla/5.0 (Linux; Android 14) Chrome', { mobile: true, platform: 'Android' });

    expect(new PlatformService().platform()).toBe('web-android');
  });

  it('falls back to the UA string for web-android when userAgentData is unavailable', () => {
    vi.spyOn(Capacitor, 'isNativePlatform').mockReturnValue(false);
    stubNavigator('Mozilla/5.0 (Linux; Android 14) AppleWebKit Chrome Mobile');

    expect(new PlatformService().platform()).toBe('web-android');
  });

  it('reports web-ios for an iPhone UA', () => {
    vi.spyOn(Capacitor, 'isNativePlatform').mockReturnValue(false);
    stubNavigator('Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit Safari');

    expect(new PlatformService().platform()).toBe('web-ios');
  });

  it('reports web-ios for iPadOS 13+ reporting as MacIntel with touch support', () => {
    vi.spyOn(Capacitor, 'isNativePlatform').mockReturnValue(false);
    stubNavigator('Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15) AppleWebKit Safari', undefined, 5);

    expect(new PlatformService().platform()).toBe('web-ios');
  });

  it('reports web-desktop for a plain desktop UA', () => {
    vi.spyOn(Capacitor, 'isNativePlatform').mockReturnValue(false);
    stubNavigator('Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15) AppleWebKit Safari', undefined, 0);

    expect(new PlatformService().platform()).toBe('web-desktop');
  });

  it('isNative() mirrors the native-android platform value', () => {
    vi.spyOn(Capacitor, 'isNativePlatform').mockReturnValue(true);
    stubNavigator('Mozilla/5.0 (Linux; Android 14)');

    expect(new PlatformService().isNative()).toBe(true);
  });
});
