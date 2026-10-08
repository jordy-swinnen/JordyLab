# Android E2E (Appium)

A small suite for what only breaks inside the installed Android app, which is the web app in a Capacitor WebView. The web journeys live in
`apps/jordylab-e2e` (Playwright); do not repeat them here.

| Test | Covers |
|------|--------|
| `src/specs/login.e2e.ts` | Native Keycloak login: tap sign in in the WebView, sign in in the Chrome Custom Tab, come back through the App Link, signed-in app |
| `src/specs/install-prompt.e2e.ts` | Inside the app a signed-in user is never offered to install it |
| `src/browser/install-prompt.e2e.ts` | In Chrome on Android a signed-in visitor gets the install dialog and "Not now" silences it |
| `src/specs/update-check.e2e.ts` | A newer release published through the app's own publish endpoint shows "Update available" |
| `src/specs/share-target.e2e.ts` | An `ACTION_SEND` text share opens the share landing with the text; the same share that starts the app from scratch (signed out) asks for the login first and then shows the text |

Tests switch between the `NATIVE_APP` and the WebView context (`src/support/contexts.ts`); Keycloak's page in the Custom Tab is driven through
accessibility nodes.

## Running

CI: Actions → **E2E Android** → Run workflow; it also runs after a successful Release and when Android-specific files change in a pull request. It is not a required check.

Locally you need the Android SDK (`ANDROID_HOME`), one running emulator that matches `PINS.md`, `adb`, JDK 21 for the Android build (`ANDROID_JAVA_HOME`)
next to JDK 25, Podman or Docker, and Bun:

```bash
cd jordylab-fe
e2e/run.sh android    # builds two debug APKs, starts the throwaway Keycloak + backend on fixed ports 18180/18080/18200, runs the suites, cleans up
```

The app is built with fixed logical addresses (`environment.mobile-e2e.ts`) that the emulator reaches through `adb reverse`; the debug APK declares the
App Link host `e2e.jordylab.test` (`-PjordylabAppLinkHost`), approved for the app with `pm set-app-links-user-selection` (`e2e/android-setup.sh`). Release
builds keep `jordylab.be`; CI checks that.

## Manual checklist: biometric unlock (not automated)

Fingerprint prompts cannot be driven by Appium, so check these by hand on a real phone after a release that touches sign-in:

1. Settings → App: enable fingerprint unlock; the system fingerprint prompt appears and accepts the registered finger.
2. Close and reopen the app: "Unlock with fingerprint" appears on the login page and unlocks without the Keycloak page.
3. Cancel the prompt: the app stays locked and tells you the fingerprint check failed or was cancelled.
4. Disable the setting: the unlock button is gone after the next start.
5. Repeat steps 2 to 4 **20 times in a row** (spec 013 US14): after every fingerprint sign-in the library opens and no red message is
   shown at any moment, not even briefly. Once in aeroplane mode only the connection message may appear. Full wording: `docs/runbook.md` §21.
