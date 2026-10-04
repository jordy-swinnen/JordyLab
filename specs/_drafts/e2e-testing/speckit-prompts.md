# Automated E2E Testing: SpecKit Prompts

Background is in `research.md`, the expected spec is in `spec-draft.md`.

> **Numbering:** none. SpecKit assigns the next free number when this draft is specified, so drafts can be taken in any order.

---

## `/speckit-specify`

```
Short name: e2e-testing.

Add automated end-to-end tests in two layers, on top of the manual campaign and agent-browser checks I already do.

WHY: I develop mostly with agents. Today end-to-end checking is manual or agent-driven, and the Android app (the same Angular build in a Capacitor WebView) has no automated coverage at all. I want regressions caught by automation, locally and in CI.

WEB LAYER: Playwright tests for the main web journeys of the Angular frontend, run against a freshly built app. Log in once through Keycloak as a test user and reuse that session. Use stable selectors (test ids and ARIA roles). Cover the journeys from the manual E2E campaign that matter most; the full list comes out of clarify.

ANDROID LAYER: a small Appium 3 + WebdriverIO + UiAutomator2 suite, TypeScript, run on an emulator against a debug build. It covers only what breaks inside the WebView: native Keycloak login (Custom Tab, then App Link back into the app), the install prompt, the update check and the share target. It switches between the native and WebView contexts. Biometric unlock stays a manual device checklist because it cannot be automated. Pin the Appium and chromedriver versions to the emulator's WebView version and document it.

DATA AND ENVIRONMENT: tests never touch my dev database and never hand-seed rows. Each run starts its own throwaway Postgres and Keycloak (separate compose file, unique project name, free ports, nothing persisted), creates test data only through the app's own API or UI, and always removes everything afterwards: on success, on failure, on interrupt, and in CI even when a step fails. A final check fails the run if any container, volume or network from the run is left over.

CI: a web job that gates merges, and a separate Android job (emulator) that runs on merge or nightly, both ending with the cleanup step.

DOCS: a short E2E section where both agent tools read it, and a note on how this relates to agent-browser.

Out of scope: iOS, biometric automation, visual regression testing, load testing, running against production.
```

## `/speckit-clarify`: expected questions and suggested answers

- Which journeys first? → sign-in, the game catalog grid and detail, catalog chat, FNA briefing view, settings for admin.
- Local emulator or CI only? → CI first; a local run documented for later.
- Where does the Appium suite live? → its own Nx project in `jordylab-fe`, so Nx caching and affected runs apply.
