# Automated E2E Testing (Web and Android): Research

Date: 2026-10-05. Checked against the repo on `main` and live sources. Sources are listed at the end.

## Verdict: two layers, Playwright for the web and Appium for the Android shell

No single free framework covers both the web and the Capacitor Android app well. The Android app is the same Angular
build inside a WebView, so most journeys only need testing once, in a browser. A small Android suite then covers what
only breaks inside the installed app.

| Option | Licence | Fit for this repo |
|---|---|---|
| **Playwright** | Apache 2.0 | Web layer. Fast, free, runs in CI. Its Android support is officially **experimental** and cannot drive the Custom Tab login |
| **Appium 3 + WebdriverIO + UiAutomator2** | Apache 2.0 (Appium) | Android layer (**chosen**). The usual tool for WebView/Capacitor apps: real native input plus the full WebView DOM through context switching. More setup (drivers, chromedriver matching the WebView version) |
| Maestro | open source | Simplest YAML flows and good at native screens, but its WebView content mode is broken on Android System WebView 148+ (open issue, devtools mode only). Not chosen |
| Espresso, Detox | Apache 2.0 | Android-native or React Native focused; no fit |

Note on bias: one comparison that ranks Appium first comes from a company that sells Appium-based testing.

## What the repo has today

- A manual end-to-end campaign with a large coverage matrix and a bug log under `docs/testing/`, plus agent-driven
  checks in the built-in browser and with agent-browser. No Playwright, Cypress, Appium or Maestro in the repo.
- The mobile app: `apps/jordylab-mobile` packages `dist/apps/jordylab/browser` with Capacitor (`appId` `be.jordylab.app`).
  Native-only behaviour lives behind `PlatformService.isNative()`: login through `@capacitor/browser` (Custom Tab,
  PKCE) with an App Link back into the app, install prompt, update check, biometric unlock, share target.
- The mobile spec puts native-only behaviour out of automated-test reach and keeps a device checklist instead.
  Biometrics stays that way; the rest can now be automated.
- Local infra: `jordylab-be/compose.yaml` runs pgvector and Keycloak on **fixed ports** (5432, 8180) with **no
  volumes**, and imports the dev realm (which has a dev user). An E2E stack therefore needs its own compose file,
  project name and ports so it can run next to the dev stack.
- Testcontainers on Podman runs with Ryuk disabled, so cleanup cannot rely on Ryuk: teardown must be explicit.
- The repo rule forbids hand-seeding the database. Synthetic data is allowed only inside test code. E2E data is
  therefore created through the app's own API or UI, against a throwaway database.
- CI: GitHub Actions `build.yml` (secret scan, licence check, backend tests, frontend tests, image build). Hosted Linux
  runners can run the Android emulator (`reactivecircus/android-emulator-runner`); an Android job takes about ten to
  fifteen minutes, so it runs on merge or nightly.
- The developer Mac has no Android SDK or `adb` installed yet.

## Licence check

The `licence-check` job fails on the phrase for that licence anywhere outside `node_modules`, `dist`, `build` and
`specs`. Dependencies under `node_modules` are fine (WebdriverIO carries that licence). New docs and READMEs must not
contain the phrase.

## Open questions

- Which journeys from the manual matrix become automated web tests first?
- Is a local emulator run expected on the Mac, or is CI enough for the Android layer?
- Where does the Appium suite live: inside `jordylab-fe` as its own Nx project, or as a separate workspace folder?

## Sources

- Playwright Android (experimental): https://playwright.dev/docs/api/class-android
- Maestro WebView 148+ issue: https://github.com/mobile-dev-inc/Maestro/issues/3646
- Capacitor app moving to WebdriverIO + Appium: https://github.com/quwisky/trinity-matrix-client/issues/853
- Playwright + emulator CI pattern for Capacitor: https://hybridmob.com/tutorials/e2e-testing-ionic-capacitor-playwright-maestro
- Framework comparison (vendor with an Appium bias): https://www.qawolf.com/blog/best-mobile-app-testing-frameworks-2026
