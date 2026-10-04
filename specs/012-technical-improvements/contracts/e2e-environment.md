# Contract: E2E environment runner

`jordylab-fe/e2e/run.sh <web|android>` (one entry point for local and CI use).

| Phase | Contract |
|-------|----------|
| 0. Sweep | remove leftovers of earlier runs whose project name starts with `jordylab-e2e-` and that are older than this run |
| 1. Start | pick a `runId`; compose project `jordylab-e2e-<runId>`; start Postgres and Keycloak from `compose.e2e.yaml` with the label `dev.jordylab.e2e.run=<runId>`; no fixed host ports, no volumes; import the throwaway realm |
| 2. Ready | wait on readiness checks (Postgres accepting connections, Keycloak realm discovery document, backend health) with a time limit; fail with the service name and last output on timeout; no fixed sleeps |
| 3. Backend + app | start the backend jar against the throwaway services (no AI provider keys); build and serve the app fresh (web) or build the e2e debug APK (android) |
| 4. Test | run Playwright (web) or WebdriverIO (android); data only through the app's API or UI |
| 5. Clean | `trap` on EXIT, INT, TERM: stop the backend, `compose down --volumes --remove-orphans`, remove the run's labeled containers/volumes/networks |
| 6. Verify clean | list containers, volumes and networks by project name and label; any hit prints them and exits non-zero |

Exit code: the test result, unless the verify-clean step fails (then non-zero regardless).
Never touches: `jordylab-be/compose.yaml`, the dev database, the dev realm. Credentials are generated per run, written
to an untracked env file, and never printed.

CI wrappers call the same script; the `always()` step re-runs phases 5 and 6 so a failed or cancelled step still
cleans up.

# Contract: web suite

- Project `apps/jordylab-e2e`; `globalSetup` logs in once through the Keycloak login page as the test user and saves
  storage state reused by every test.
- Selectors: `getByRole`/`getByLabel`/`getByTestId` only.
- Output: HTML report and traces on failure, uploaded as a CI artifact.

# Contract: Android suite

- Project `apps/jordylab-mobile-e2e` (WebdriverIO + Appium 3 + UiAutomator2, TypeScript).
- Preflight: read the emulator's WebView version with adb; compare with `PINS.md`; on mismatch fail naming both.
- Tests: native login (Custom Tab → App Link → WebView context → signed-in home), install prompt, update check, share target.
- Context handling: helper that switches between `NATIVE_APP` and the WebView context and fails clearly when the
  WebView context is absent after a timeout.
