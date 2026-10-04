# Feature Specification: Automated E2E Testing (Web and Android)

**Feature Branch**: assigned by SpecKit when specified
**Created**: 2026-10-05
**Status**: Draft (reference for `/speckit-specify`; see `research.md`)
**Module**: `jordylab-fe` (web and mobile), CI, local test infrastructure

---

## Overview

End-to-end checking is manual or agent-driven today, and the Android app has no automated coverage. This feature adds
Playwright tests for the main web journeys and a small Appium suite for what only breaks inside the installed Android
app. Every run uses its own throwaway Postgres and Keycloak and removes them afterwards, whatever happens.

---

## User Scenarios & Testing

### User Story: The main web journeys are tested automatically (Priority: High)

As the developer, I want the most important journeys from the manual campaign covered by Playwright, so that agents and
CI catch regressions without a manual pass.

**Independent Test**: break a covered journey on purpose; the web suite fails locally and in CI.

### User Story: Every run cleans up after itself (Priority: High)

As the developer, I want the test Postgres and Keycloak gone after every run, so that nothing piles up on my machine.

**Independent Test**: run the suite, let it pass; run it, make it fail; start it and interrupt it. After each,
no container, volume or network from the run is left.

### User Story: The Android login works in the installed app (Priority: High)

As the developer, I want native Keycloak login (Custom Tab, then App Link back into the app) tested on an emulator,
because it is the part of the app most likely to break on Android only.

**Independent Test**: on an emulator with a debug build, the suite logs in and lands on the signed-in home screen.

### User Story: Other WebView-only behaviour is covered (Priority: Medium)

As the developer, I want the install prompt, the update check and the share target tested in the installed app.

**Independent Test**: each behaviour has one passing Appium test; breaking it makes that test fail.

### User Story: CI runs both layers (Priority: Medium)

As the developer, I want the web suite to gate merges and the Android suite to run on merge or nightly.

**Independent Test**: a pull request with a broken web journey cannot merge; the Android job reports its result on main.

### Edge Cases

- The emulator's WebView version does not match the pinned chromedriver: the run fails with a clear message, not a hang.
- Ports already in use by the dev stack: the E2E stack picks free ports.
- Keycloak is slow to start: the suite waits on a readiness check instead of sleeping.

---

## Requirements

- Web tests MUST use Playwright, run against a freshly built app, log in once through Keycloak and reuse the session.
- Selectors MUST be test ids or ARIA roles, never internal class names.
- The Android suite MUST use Appium 3, WebdriverIO and UiAutomator2 in TypeScript, on an emulator, against a debug build.
- The Android suite MUST switch between the native and WebView contexts, and MUST pin and document the Appium and
  chromedriver versions for the emulator's WebView.
- Biometric unlock MUST stay a manual device checklist.
- Tests MUST NOT touch the dev database and MUST NOT hand-seed rows. Test data MUST come from the app's own API or UI.
- Each run MUST start its own Postgres and Keycloak (separate compose file, unique project name, free ports, nothing
  persisted) and MUST remove them on success, failure, interrupt and in CI even when a step fails.
- A final check MUST fail the run when any container, volume or network from the run is left over.
- The web job MUST gate merges; the Android job MUST run on merge or nightly; both MUST end with the cleanup step.
- A short E2E section MUST be added where both agent tools read it, including how this relates to agent-browser.
- New docs MUST NOT contain the phrase the licence check rejects.

## Success Criteria

- The agreed set of web journeys is automated and green on main.
- Native login, install prompt, update check and share target each have a passing Android test.
- Zero leftover containers, volumes or networks after passed, failed and interrupted runs.
- A deliberately broken journey blocks a merge.

## Out of Scope

iOS, biometric automation, visual regression testing, load testing, running against production.

## Assumptions

- Hosted GitHub Actions Linux runners can run the Android emulator for this repo.
- The dev realm export (or an E2E copy of it) provides a test user.

## Clarifications Needed

- Which journeys go first?
- Local emulator on the Mac, or CI only for the Android layer?
- Where the Appium suite lives.
