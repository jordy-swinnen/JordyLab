# Android E2E pins

What the Android suite runs against, pinned together so a change is visible. `src/pins.json` holds the same values for the preflight
check, which fails the run (naming both versions) when the emulator's WebView is not the pinned one.

| What | Pin |
|------|-----|
| Emulator image | `system-images;android-35;google_apis;x86_64` (API 35, Google APIs without Play Store; CI uses `reactivecircus/android-emulator-runner@v2`) |
| WebView on that image | see `src/pins.json` (`webViewVersion`); set from the first CI run's preflight output |
| Appium | 3.8.0 |
| UiAutomator2 driver | 8.7.0 (`bun run setup` installs exactly this into `.appium/`) |
| WebdriverIO | 9.32.0 (`@wdio/globals` 9.31.3, the version `@wdio/cli` 9.32.0 depends on) |
| chromedriver | resolved by Appium's chromedriver autodownload for the WebView it finds (`--allow-insecure uiautomator2:chromedriver_autodownload`); the WebView pin above is what makes this reproducible |

Changing the emulator image changes the WebView: update `src/pins.json` and this file in the same commit and re-run.
