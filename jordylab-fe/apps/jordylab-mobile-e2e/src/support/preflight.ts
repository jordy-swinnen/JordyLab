import { execFileSync } from 'node:child_process';
import pins from '../pins.json' with { type: 'json' };

/** The WebView package version the emulator reports, e.g. "133.0.6943.137", read with adb. */
export function installedWebViewVersion(): string {
  const report = execFileSync('adb', ['shell', 'dumpsys', 'webview'], { encoding: 'utf8' });
  const match = /Current WebView package \(name, version\): \(([^,]+), ([^)]+)\)/.exec(report);
  if (!match) {
    throw new Error('Could not read the WebView version from `adb shell dumpsys webview`. Is exactly one emulator running?');
  }

  return match[2].trim();
}

/**
 * Fails fast, before any session starts, when the emulator's WebView is not the pinned one. Appium picks the chromedriver for the
 * WebView it finds, so a different image would silently change what the tests run against; this names both versions instead.
 */
export function assertPinnedWebView(): void {
  const actual = installedWebViewVersion();
  if (actual !== pins.webViewVersion) {
    throw new Error(
      `The emulator's WebView is ${actual} but PINS.md and src/pins.json pin ${pins.webViewVersion} (image ${pins.emulatorImage}). ` +
        'Update both together (and re-run) if the image was changed on purpose.',
    );
  }
  console.log(`preflight: WebView ${actual} matches the pin; Appium ${pins.appium}, UiAutomator2 driver ${pins.uiautomator2Driver}`);
}
