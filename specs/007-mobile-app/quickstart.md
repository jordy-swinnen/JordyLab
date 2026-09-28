# Quickstart — Mobile App (Android via Capacitor, iOS Home-Screen Web App)

Manual + automated validation of [spec.md](spec.md). Each scenario maps to the FR/SC it proves. Interfaces:
[mobile-releases-api](contracts/mobile-releases-api.md) · [access matrix](contracts/access-matrix.md) ·
[app-shell-contract](contracts/app-shell-contract.md) · [data model](data-model.md).

> **Validation data rule** (root AGENTS.md): never hand-seed releases or users. Releases come from the real CI
> pipeline (or a local build using the same Gradle commands); users come from real Keycloak registration/approval
> (006's flow). If the production domain, application id, or signing key are not yet finalized (research D13/D14),
> device scenarios below that need a real App Link or a real install cannot be completed — say so explicitly rather
> than substituting a workaround.

## STOP-AND-REPORT reminders (research D13/D14)

Before any of these are attempted for real, the user must explicitly confirm:

1. The production domain (blocked on feature 008) — needed for the Keycloak redirect URI, `assetlinks.json`, and CORS.
2. The final Android application id (reverse-DNS on that domain) — permanent after the first release.
3. Generating the release signing keystore, and where its offline backup lives.
4. The realm export changes in [access-matrix.md](contracts/access-matrix.md) (`jordylab-mobile`, `mobile-release-ci`
   clients + `mobile-release-publisher` role).

None of this quickstart's scenarios require guessing these values — placeholders (`{PRODUCTION_DOMAIN}`,
`{APPLICATION_ID}`) are used consistently and must be filled in only once confirmed.

## Prerequisites

- Existing Podman compose stack (`jordylab-be/compose.yaml`) — Postgres + Keycloak, per 006's quickstart
- An approved `admin` and at least one approved `guest` account (006's flow — Settings → Users)
- JDK 25, Bun + Node, Android SDK (API 29+) + a JDK for Gradle, and either a physical Android 10+ phone or an
  emulator image at API 29+
- Env: `jordylab.mobile.release.storage-dir` (a writable local path in dev), `jordylab.mobile.release.signing-cert-sha256`
  (the release keystore's cert fingerprint, once generated), `jordylab.mobile.notifications.ntfy.*` (optional, for
  scenario 6)

## Start

```bash
# backend (from jordylab-be/)
./gradlew bootRun
# frontend web (from jordylab-fe/)
bunx nx serve jordylab
# frontend mobile build (from jordylab-fe/, once the `mobile` configuration + apps/jordylab-mobile exist)
bunx nx build jordylab --configuration=mobile
cd apps/jordylab-mobile && npx cap sync android
```

## Scenarios

### 1. Release publish → latest → device test matrix floor (US3, FR-001, FR-003, FR-018, SC-004)

1. Build a signed debug/test APK locally with the configured (or a throwaway dev) keystore.
2. `POST /api/mobile/releases` with the APK + metadata (as CI would — see
   [mobile-releases-api](contracts/mobile-releases-api.md)).
3. Expected: `201`, `sha256`/`sizeBytes` match the file; a second publish with the same or lower `versionCode` →
   `400 VERSION_CODE_NOT_MONOTONIC`; a publish with a mismatched signing cert → `400 SIGNING_CERT_MISMATCH`, no row
   created, uploaded file not kept.
4. `GET /api/mobile/releases/latest?installedVersionCode=<lower>` → `updateAvailable: true`.
5. On the device/emulator (Android 10+ only — install on an API 28 or lower image and confirm Android itself refuses
   the install, spec Edge Cases): install the APK, open it, confirm the "Update available" prompt does **not** show
   for the version just installed, but **does** show once a second, higher-`versionCode` release is published.

### 2. Login round-trip (US2, FR-010, research D2)

1. Fresh install, tap Log in → system browser opens the Keycloak login page (not an in-app WebView).
2. Log in as the approved guest → browser closes, app receives control via the App Link callback, shows the
   role-appropriate screens (Game Catalog only).
3. Log in as the admin in a second session/device → sees everything, same as the website.
4. Log in as a pending user → "awaiting approval" screen, matching the web behavior exactly.
5. Confirm every API call and every artwork `<img>` in the app resolves correctly — no broken images, no failed
   calls (SC-002; this is the base-URL interceptor + artwork pipe from
   [app-shell-contract](contracts/app-shell-contract.md) actually working end-to-end, which nothing in CI can prove
   by itself since it needs the real native WebView origin).

### 3. Biometric unlock (US4, FR-012, SC-003)

1. After a successful login, enable "Unlock with fingerprint."
2. Close and reopen the app → biometric prompt only, no password, session resumes.
3. Cancel the biometric prompt once → falls back to normal login (does not silently retry or lock out).
4. Change the phone's enrolled biometrics (add/remove a fingerprint) → next open falls back to normal login (FR-012).
5. As the admin, **Revoke** this guest (006's flow) → confirm the *next* app open, even with a correct biometric
   check, ends up back at login (research D12's offline-consent revocation call — this is the one scenario that
   actually proves D12 works, not just that the endpoint exists).
6. (Optional, needs 7 real days) Reopen after 7 days with biometric unlock still on → no password required (SC-003).

### 4. Share to JordyLab (US5, FR-014, FR-017, research D6)

1. From any other Android app's share menu, share a link to JordyLab as the guest → only "Ask the catalog" is
   offered; confirming opens chat with the text prefilled.
2. Share as the admin → "Save to FNA" is also offered; confirming calls `POST /api/fna/articles/manual` and the link
   appears queued (check via the FNA admin UI or DB) as a candidate for the next briefing.
3. Share while logged out → login runs first, then the share continues with the original payload (spec US5-4).

### 5. Web install prompts, one per platform (US1, US7, FR-005–FR-008)

1. **Android browser** (not the app): log in as an approved user on Chrome for Android → the APK dialog appears
   once; "Not now" hides it for 30 days (check via `localStorage`) but the user-menu entry remains; confirm Chrome's
   own `beforeinstallprompt` never fires visibly (FR-007).
2. **iOS Safari**: log in as an approved user → the Add-to-Home-Screen sheet appears once; add to home screen; reopen
   from the home screen icon → runs standalone, no sheet on subsequent opens (US7-3).
3. **Desktop**: log in → no automatic popup; user menu → "Get the Android app" shows a QR code pointing at the
   download page.
4. **Pending/logged-out visitor** on any platform → no prompt at all, and a direct download-link request → 401/403
   (spec scenario 4).
5. **Inside the native app**: none of the above ever appears (FR-005) — open the installed app and confirm no
   install UI is reachable anywhere.

### 6. Notifications (US6, FR-016, research D9)

With `jordylab.mobile.notifications.ntfy.*` configured against a real Ntfy topic:

1. Register a new account (006's sign-up flow) → confirm a `UserSignUpPending` event fires (log line or debugger) and
   a single Ntfy push arrives (not two — this is the D9 duplication check) with a click-through URL; tapping it opens
   the installed app at Settings → Users.
2. Trigger (or wait for) a daily briefing generation → confirm `BriefingReady` fires and a push arrives whose tap
   opens the briefing screen.

### 7. assetlinks.json & App Link verification (research §1.2, D13)

Once the production domain and application id are finalized:

```bash
curl -s https://{PRODUCTION_DOMAIN}/.well-known/assetlinks.json
```

Expected: `200`, `Content-Type: application/json`, no redirect, body matching
[mobile-releases-api's assetlinks.json contract](contracts/mobile-releases-api.md). Then confirm Android's own App
Link verification succeeds (`adb shell pm get-app-links {APPLICATION_ID}` shows `verified`) — this can only be tested
against the real, publicly reachable domain (research §4.3), not localhost.

## Automated verification

```bash
# backend (jordylab-be/, Podman socket per AGENTS.md)
./gradlew :test --tests "*ModularityTests*"     # module boundaries, incl. the new mobile module + event types
./gradlew :test --tests "*MobileModuleTest*"    # release/download-link endpoints, role gating, token validation
./gradlew test                                  # full suite

# frontend (jordylab-fe/)
bunx nx run-many -t test -p shared-platform      # interceptor, artwork pipe, platform detection, install-prompt store
bunx nx affected -t test lint                    # app shell / auth / nav updates
```

Expected: all green. `ModularityTests` confirms `mobile` keeps to `mobile → shared` only, and that the new
`UserSignUpPending`/`BriefingReady` events don't create a direct `settings`/`fna` → `mobile` dependency.

## Not testable in CI (device checklist only)

Everything under Scenarios 2–4 and 7 involves a real native WebView, real biometric hardware, a real share-sheet
intent, or a real publicly reachable domain — none of it is mockable in the existing test stack, and none of it
should be silently skipped. Track these as explicit manual gates before calling the feature done, not as "covered by
the backend tests."
