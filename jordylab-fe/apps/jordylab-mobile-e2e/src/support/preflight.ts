import { execFileSync } from 'node:child_process';
import pins from '../pins.json' with { type: 'json' };

const WEBVIEW_PACKAGES = ['com.google.android.webview', 'com.android.webview'];

/**
 * The WebView package version the emulator reports, e.g. "124.0.6367.113". This image has no `webview` dumpsys service, so the version
 * comes from the WebView implementation package itself (the Google build first, the AOSP one as the alternative).
 */
export function installedWebViewVersion(): string {
  for (const packageName of WEBVIEW_PACKAGES) {
    const report = execFileSync('adb', ['shell', 'dumpsys', 'package', packageName], { encoding: 'utf8' });
    const match = /versionName=(\S+)/.exec(report);
    if (match) {
      return match[1].trim();
    }
  }
  throw new Error(`Could not read the WebView version: none of ${WEBVIEW_PACKAGES.join(', ')} is installed. Is exactly one emulator running?`);
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
