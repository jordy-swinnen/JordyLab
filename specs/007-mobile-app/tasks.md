# Tasks: Mobile App (Android via Capacitor, iOS Home-Screen Web App)

**Input**: Design documents from `/specs/007-mobile-app/` (plan.md, spec.md, research.md D1–D14, data-model.md,
contracts/, quickstart.md)

**Prerequisites**: plan.md (required), spec.md (required — 7 user stories), research.md, data-model.md, contracts/

**Tests**: Included — the spec's success criteria demand automated proof (SC-002 no relative-URL failures, SC-005
100% denial on pending/expired/logged-out), the constitution requires entity tests + TestBuilders as definition of
done, and repo test discipline applies (JUnit 5/AssertJ/Mockito/MockMvc; Vitest/Spectator). Native-only behavior
(biometric hardware, a real share-sheet intent, a real App Link on a real domain) cannot be automated — see
quickstart.md's device checklist; those tasks are called out explicitly rather than silently skipped.

**Organization**: Tasks grouped by user story (spec.md US1–US7, priority order) so each story is independently
implementable and testable. Repo skills are referenced where they apply (`/new-module`, `/flyway-migration`,
`/entity`, `/test-builder`, `/angular-signal-store`, `/angular-test`, `/modularity-check`).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Which user story this task belongs to (US1–US7); Setup/Foundational/Polish have no story label
- Include exact file paths in descriptions

## Path Conventions

Web app monorepo: backend = `jordylab-be/src/main/java/dev/jordy/jordylab/…` (+ `src/test/…`, `src/main/resources/…`),
frontend = `jordylab-fe/…` (libs, apps), native shell = `jordylab-fe/apps/jordylab-mobile/android/…`. Full layout in
[plan.md](plan.md) → Project Structure.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: `mobile` module + frontend libs/app skeletons, schema, config keys

- [X] T001 Create the `mobile` module skeleton (root package `dev.jordy.jordylab.mobile` + `MobileProperties` for
  `jordylab.mobile.*`: `release.storage-dir`, `release.signing-cert-sha256` (placeholder — D14), `download-link.secret`
  + `.ttl-minutes` default 5, `release.min-supported-version-code` default, `app.package-name` + `app.production-domain`
  (placeholders — D13/D14), `notifications.ntfy.base-url|topic|token`) in
  `jordylab-be/src/main/java/dev/jordy/jordylab/mobile/` — follow `/new-module` (package layout from
  `jordylab-be/AGENTS.md`, no new sub-packages beyond `domain/`, `rest/{client,controller}`, `service/`, `util/`); run
  `ModularityTests` green
- [X] T002 [P] Add `jordylab.mobile.*` config keys with dev defaults in
  `jordylab-be/src/main/resources/application.yaml`; remove the now-unused `jordylab.settings.notifications.ntfy.*`
  scaffold from `SettingsProperties.java` (research D10 — nothing reads it today)
- [X] T003 [P] Create Flyway migration
  `jordylab-be/src/main/resources/db/migration/V<yyyyMMdd>__mobile_create_tables.sql` — `mobile` schema, one table
  `mobile_release` with a unique index on `version_code`, per [data-model.md](data-model.md) — follow
  `/flyway-migration`
- [X] T004 [P] Scaffold the native shell Nx app `jordylab-fe/apps/jordylab-mobile/` (`capacitor.config.ts` —
  `androidScheme: 'https'`, `hostname: 'localhost'` — + `android/`, generated via `npx cap add android`); commit
  `android/`, gitignore `android/app/build/` and Gradle caches; install `@capacitor/core`, `@capacitor/android`,
  `@capacitor/cli`, `@capacitor/browser`, `@capacitor/app` (research D1). `npx cap add android` worked without an
  Android SDK (it only copies the template; building needs the SDK). `appId` is the obvious placeholder
  `dev.jordylab.mobile.placeholder` (STOP-AND-REPORT gate, D14). Also applied the `/speckit-analyze` C1 fix
  (`minSdkVersion = 29` in `android/variables.gradle`) and added the App-Link (T024) + share-target (T036)
  intent-filters to `AndroidManifest.xml` here while already in this area, ahead of their own task numbers.
  **PR review finding (confirmed, fixed)**: the generated `sync`/`open-android` Nx targets in
  `apps/jordylab-mobile/project.json` shelled out via `npx cap ...` — this repo requires `bun`/`bunx`, never
  `npx`/`npm`/`yarn`. Changed both to `bunx cap ...`.
- [X] T005 [P] Add a `mobile` build configuration to `jordylab-fe/apps/jordylab/project.json` (new
  `environment.mobile.ts`: absolute `apiBaseUrl` placeholder — D13 domain pending — `keycloakClientId:
  'jordylab-mobile'`); set `apps/jordylab-mobile`'s `webDir` to that configuration's build output
- [X] T006 [P] Scaffold `jordylab-fe/libs/shared/platform/{api,ui}` via `bunx nx g @nx/angular:library` (tags
  `scope:platform,type:api|ui`), add `@jordylab-fe/shared/platform/api|ui` paths to
  `jordylab-fe/tsconfig.base.json`, add `scope:platform` to the `type:app` constraint in
  `jordylab-fe/eslint.config.mjs`, create barrels. **Deviation**: tagged `scope:shared` instead of inventing
  `scope:platform` — matches `libs/shared/auth`/`libs/shared/brand`, and `type:app` already allows `scope:shared`, so
  no `eslint.config.mjs` depConstraint change was needed at all

**Checkpoint**: Module skeletons, schema, and app/lib scaffolding exist. Nothing functional yet.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Realm/security matrix, the release entity every release-related endpoint needs, and the two frontend
primitives (base-URL interceptor, platform detection) that everything native depends on — blocks all user stories

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T007 ⚠️ **STOP-AND-REPORT GATE** (halt and report to the user before executing): propose the realm export
  changes in `jordylab-be/compose/keycloak-realm-export.json` per [research.md](research.md) D13 —  new public client
  `jordylab-mobile` (PKCE S256, redirect URI `https://{PRODUCTION_DOMAIN}/mobile/callback`, `offline_access` allowed,
  `standardFlowEnabled=true`, `directAccessGrantsEnabled=false`), new confidential client `mobile-release-ci`
  (`serviceAccountsEnabled=true`), new realm role `mobile-release-publisher` (granted only to that service account);
  **do not apply until the user confirms** — the production domain (feature 008) and application id (D14) are still
  placeholders
- [X] T008 [P] Create `MobileRelease` entity + repository per [data-model.md](data-model.md) in
  `jordylab-be/src/main/java/dev/jordy/jordylab/mobile/domain/` — canonical entity structure via `/entity` (UUID id,
  unique `version_code`, builder guards in `build()`, `BaseEntity`); entity test + TestBuilder via `/test-builder` in
  `jordylab-be/src/test/java/dev/jordy/jordylab/mobile/domain/`
- [X] T009 Add `/api/mobile/**` and `/.well-known/assetlinks.json` matchers to
  `jordylab-be/src/main/java/dev/jordy/jordylab/shared/config/SecurityConfig.java` per
  [contracts/access-matrix.md](contracts/access-matrix.md) (`GET .../latest` + `POST .../download-link` →
  `admin|guest`; `GET /api/mobile/download/**` + `GET /.well-known/assetlinks.json` → permitAll; `POST
  /api/mobile/releases` → `hasRole("mobile-release-publisher")`); update `SecurityConfigTest`; depends on T007's role
  existing in the test realm fixture. **Deviation**: `SecurityConfigTest` itself only tests the JWT→authority
  converter (no route-matching there in this codebase's actual convention) — added `mobile-release-publisher` to the
  **test-only** `jordylab-test-realm.json` (not the gated prod realm export) and extended the existing
  `settings/RoleMatrixTest.java` (the codebase's real shared access-matrix test) instead of `SecurityConfigTest`
- [X] T010 [P] Implement the base-URL interceptor (`HttpInterceptorFn`, registered after `authInterceptor`) and the
  `artworkUrl` pipe in `jordylab-fe/libs/shared/platform/api/src/lib/` — prefixes relative `/api/...` with
  `apiBaseUrl` only when `Capacitor.isNativePlatform()` is true (research D5,
  [contracts/app-shell-contract.md](contracts/app-shell-contract.md)); Vitest tests proving both are no-ops on web and
  correct on native (`useValue` platform mock). **Verified**: 18/18 Vitest tests pass, lint clean (this environment
  has a working Bun/Nx toolchain, unlike the backend — see commit message)
- [X] T011 [P] Implement the `platform` signal (`'web-android' | 'web-ios' | 'web-desktop' | 'native-android'`) in
  `jordylab-fe/libs/shared/platform/api/src/lib/platform.service.ts` — `Capacitor.isNativePlatform()` +
  `navigator.userAgentData` with UA-string fallback; Vitest tests per platform branch

**Checkpoint**: Foundation ready — the release entity exists, the matrix is enforced, native API calls resolve, and
the app knows what platform it's running on. User stories can now proceed.

---

## Phase 3: User Story 1 - Install the Android app from the website (Priority: P1) 🎯 MVP

**Goal**: Approved users get a platform-appropriate install prompt on the website; the APK is downloadable only
through a short-lived signed link; nothing is shown to pending/logged-out visitors or inside the native app itself
(spec FR-001–FR-007)

**Independent Test**: On an Android phone, log in to the website as a guest, accept the dialog, download and install
the APK (quickstart scenario 5, plus the publish→latest→download round-trip from scenario 1)

### Tests for User Story 1

- [X] T012 [P] [US1] Write the release/download-link role-matrix integration test (red first) in
  `jordylab-be/src/test/java/dev/jordy/jordylab/mobile/MobileModuleTest.java`: pending → 403 on download-link
  request; guest → 200 on `latest`; expired/tampered download token → `403 DOWNLOAD_LINK_INVALID` (spec SC-005) —
  proves the automated half of scenario 1. **Deviation**: added directly to `settings/RoleMatrixTest.java` (see T009)
  rather than a new file — that's the established location for cross-module role-matrix assertions in this codebase

### Implementation for User Story 1

- [X] T013 [US1] Implement `MobileReleaseService` + `DownloadLinkService` in
  `jordylab-be/src/main/java/dev/jordy/jordylab/mobile/service/` per [research.md](research.md) D7/D8 —
  `DownloadLinkService` issues/verifies the HMAC-SHA256-signed, 5-minute token (releaseId + subject + expiry);
  `MobileReleaseService.publish(...)` validates the signing-cert SHA-256 against
  `jordylab.mobile.release.signing-cert-sha256` (`400 SIGNING_CERT_MISMATCH` on mismatch) and that `versionCode` is
  strictly greater than the current latest (`400 VERSION_CODE_NOT_MONOTONIC`), then stores the file under
  `release.storage-dir` and creates the `MobileRelease` row; unit tests for both services (signature/expiry
  validation, cert-mismatch rejection, non-monotonic rejection)
- [X] T014 [US1] Implement `MobileReleaseController` in `jordylab-be/src/main/java/dev/jordy/jordylab/mobile/rest/controller/`
  per [contracts/mobile-releases-api.md](contracts/mobile-releases-api.md) — `GET /api/mobile/releases/latest`
  (`updateAvailable`/`updateRequired` flags when `installedVersionCode` is given), `POST
  /api/mobile/releases/{id}/download-link`, `POST /api/mobile/releases` (multipart, `mobile-release-publisher` role);
  MockMvc tests (`@Language("JSON")`) in
  `jordylab-be/src/test/java/dev/jordy/jordylab/mobile/rest/controller/MobileReleaseControllerTest.java`
- [X] T015 [US1] Implement `MobileDownloadController` (`GET /api/mobile/download/{token}`, permitAll) in
  `jordylab-be/src/main/java/dev/jordy/jordylab/mobile/rest/controller/` — validates the token via
  `DownloadLinkService`, streams the file with `Content-Type: application/vnd.android.package-archive` and
  `Content-Disposition: attachment`; invalid/expired → `403 DOWNLOAD_LINK_INVALID`; MockMvc test (valid token streams
  correct headers + bytes; invalid/expired → 403, no partial stream)
- [X] T016 [P] [US1] Implement `AssetLinksController` (`GET /.well-known/assetlinks.json`, permitAll) in
  `jordylab-be/src/main/java/dev/jordy/jordylab/mobile/rest/controller/` serving the JSON body from
  `jordylab.mobile.app.package-name` + the signing-cert SHA-256 config, per
  [mobile-releases-api.md](contracts/mobile-releases-api.md); MockMvc test asserting exact JSON shape, no redirect,
  `Content-Type: application/json`
  **PR review finding (confirmed, fixed)**: `sha256_cert_fingerprints` was built directly from
  `jordylab.mobile.release.signing-cert-sha256` — plain hex, since that's the format
  `ApkSigningCertificateReader`/`MobileReleaseService`'s publish-time check need — but the contract (and the
  Digital Asset Links spec) require colon-separated hex. Added a `colonSeparate()` helper in the controller so the
  format conversion happens at this one presentation boundary, without touching the config format the other two
  consumers rely on. Updated `AssetLinksControllerTest`'s expected value accordingly.
- [X] T017 [P] [US1] Create the install-prompt signal store in `jordylab-fe/libs/shared/platform/api/src/lib/install-prompt.store.ts`
  per `/angular-signal-store` — chooses at most one prompt by `platform` signal (T011), checks
  `AuthService`-derived `isApproved` before showing anything, per-device 30-day dismissal in `localStorage` (wrapped
  in try/catch per [app-shell-contract.md](contracts/app-shell-contract.md)); specs per `/angular-test`
- [X] T018 [US1] Build the Android install-prompt dialog component ("Get the JordyLab app": download button + 3
  allow-install steps) and the desktop user-menu "Get the Android app" QR-code entry in
  `jordylab-fe/libs/shared/platform/ui/src/lib/` — Download calls the `download-link` endpoint then triggers the
  browser download; specs with the store mocked (`useValue`)
- [X] T019 [US1] Capture and suppress Android's own `beforeinstallprompt` (`event.preventDefault()`, unconditionally,
  even after "Not now") in `jordylab-fe/libs/shared/platform/api/src/lib/install-prompt.store.ts` (FR-007); Vitest
  test asserting `preventDefault` is always called
- [X] T020 [US1] Wire the install-prompt dialog + QR entry into the app shell
  (`jordylab-fe/apps/jordylab/src/app/app.ts` + `app.html`) — dialog mounts only for `web-android` + approved users;
  QR entry always in the user menu; nothing for pending/logged-out (spec scenario 4) or `native-android` (FR-005);
  update `app.spec.ts`
  - Implementation-time discovery: `apps/jordylab-mobile`'s `capacitor.config.ts` packages the same
    `dist/apps/jordylab/browser` build (`webDir`) rather than a separate native entry point (research D5), so "the
    app shell" is literally `apps/jordylab/src/app/app.ts` for both web and native — there is no second bootstrap to
    wire separately.

**Checkpoint**: US1 independently functional — a real device can go from the website to an installed APK. This is
the MVP.

---

## Phase 4: User Story 2 - Log in and use the app like the website (Priority: P1)

**Goal**: Native login via the system browser and an Android App Link return; role-appropriate screens; every API
call and artwork image resolves correctly inside the app (spec FR-009, FR-010; SC-002)

**Independent Test**: Install the app, log in as a guest and as the admin, confirm role-appropriate screens and zero
broken images/failed calls (quickstart scenario 2)

### Tests for User Story 2

- [X] T021 [P] [US2] Write Vitest tests (red first) for the custom `KeycloakAdapter` and the App Link routing split
  (`/mobile/callback` vs `/mobile/open?screen=`) in `jordylab-fe/libs/shared/auth/src/lib/` and
  `jordylab-fe/libs/shared/platform/api/src/lib/` — mocked `@capacitor/browser` and `@capacitor/app`

### Implementation for User Story 2

- [X] T022 [US2] Implement the custom `KeycloakAdapter` in `jordylab-fe/libs/shared/auth/src/lib/capacitor-keycloak-adapter.ts`
  per [research.md](research.md) D2 — `login()` opens `@capacitor/browser` with the Keycloak auth URL (PKCE S256,
  `offline_access` scope requested); wired into `AuthService`'s `Keycloak` init only when
  `Capacitor.isNativePlatform()` — web behavior (existing `keycloak-js` default adapter) is unchanged
  - **Implementation-time discovery (significant deviation from D2's literal wording)**: read
    `node_modules/keycloak-js/lib/keycloak.js` directly to confirm this — `keycloak-js`'s `KeycloakAdapter` interface
    (`login`/`logout`/`register`/`accountManagement`/`redirectUri`) cannot actually complete a login by itself. The
    code-exchange and token-setting logic (`#processCallback`/`#setToken`) are `#private` methods of the `Keycloak`
    class, unreachable from an external adapter object passed to `keycloak.init({ adapter })`; the built-in
    `cordova`/`cordova-native` adapters only work because they're defined *inside* the class and close over `this`.
    There is no `capacitor-keycloak-adapter.ts` file — instead `AuthService` (`auth.service.ts`) itself branches on
    `Capacitor.isNativePlatform()`: `login()`/`completeNativeLogin()` do the PKCE authorize-URL build, `@capacitor/browser`
    open, and a manual `fetch` code exchange (mirroring what `keycloak-js` does internally); the result is applied by
    directly assigning the resulting tokens onto the same `Keycloak` instance's public fields (`token`, `tokenParsed`,
    `refreshToken`, `authenticated`, `subject`, `realmAccess`, `resourceAccess` — all plain mutable properties per
    `keycloak.d.ts`, verified not `#private`), then reusing the existing `#applyToken()` to sync signals. This achieves
    D2's actual goal — system-browser login via an App Link, reusing `AuthService`'s existing signal surface — without
    literally satisfying `KeycloakAdapter`, which this keycloak-js version cannot support from outside the class. New
    `libs/shared/auth/src/lib/pkce.ts` holds the PKCE/JWT-decode helpers (own spec, incl. an RFC 7636 test vector).
- [X] T023 [US2] Implement the App-Link `appUrlOpen` listener in
  `jordylab-fe/libs/shared/platform/api/src/lib/app-link.service.ts` per
  [contracts/app-shell-contract.md](contracts/app-shell-contract.md) — routes `/mobile/callback` to the adapter's
  code-exchange completion, and `/mobile/open?screen=` to the in-app router per the routing table (screens wired in
  T023 for now as a no-op stub; US6 fills in the real notification-triggered screens)
  - **Implementation-time discovery**: implemented the real `SCREEN_ROUTES` table (`settings-users` →
    `/settings/users`, `fna-briefing` → `/fna/briefing`) directly here rather than stubbing then filling in T044 —
    both tasks landed in the same implementation pass, so there was no reason to stub first. T044 below is
    satisfied by this same file/test; see its note.
- [X] T024 [US2] Set `jordylab.mobile.callback` intent-filter (`autoVerify="true"`, host = production-domain
  placeholder — D13) in the generated `jordylab-fe/apps/jordylab-mobile/android/app/src/main/AndroidManifest.xml`
  - Already present from the native shell scaffold (T004) — a single `pathPrefix="/mobile"` `autoVerify` intent-filter
    covers both `/mobile/callback` (login) and `/mobile/open` (notification taps), so no separate filter was needed.
- [X] T025 [US2] Point `environment.mobile.ts` (T005) at `keycloakClientId: 'jordylab-mobile'` and wire the mobile
  build's `main.ts` bootstrap to provide the interceptor (T010) and the App-Link listener (T023); confirm role-aware
  routing (existing guards from spec 006) needs no changes — guest sees Game Catalog only, admin sees everything,
  pending sees awaiting-approval, all reusing existing route guards unmodified
  - Implementation-time discovery: same shared-shell architecture as T020/T028 — there is no separate mobile
    `main.ts`; `AppLinkService.listen()` is invoked from `App`'s constructor (`apps/jordylab/src/app/app.ts`), a
    no-op on web via `PlatformService.isNative()`. `environment.mobile.ts` now also carries `mobileCallbackUri`
    (`https://PRODUCTION_DOMAIN_PLACEHOLDER/mobile/callback`) for the native login flow (T022). Route guards
    confirmed unmodified — no changes needed.

**Checkpoint**: US2 independently functional on a real device (not automatable beyond T021's mocked adapter tests —
quickstart scenario 2 is the real proof).

---

## Phase 5: User Story 3 - Update prompt (Priority: P1)

**Goal**: The app checks for updates on start/resume and blocks versions below the minimum supported one (spec
FR-011; SC-004)

**Independent Test**: Publish a new release via `POST /api/mobile/releases` (T014), open an installed app, confirm
the update prompt (or mandatory block) appears (quickstart scenario 1 step 5)

### Implementation for User Story 3

- [X] T026 [P] [US3] Create the update-check signal store in `jordylab-fe/libs/shared/platform/api/src/lib/update-check.store.ts`
  per `/angular-signal-store` — calls `GET /api/mobile/releases/latest?installedVersionCode=` (T014) on app start and
  on `App.addListener('resume', ...)`, reading the installed `versionCode` from `@capacitor/app`'s `App.getInfo()`
  (never hardcoded — research D11); specs per `/angular-test`
- [X] T027 [P] [US3] Build the mandatory "Update required" blocking screen (no dismissal, no other UI reachable) and
  the dismissible "Update available: vX.Y" banner (release notes + a download-link-triggered install, reusing T018's
  download flow) in `jordylab-fe/libs/shared/platform/ui/src/lib/`; specs with the store mocked
- [X] T028 [US3] Wire the update-check store into the native app bootstrap (`apps/jordylab-mobile` main entry) —
  runs only when `Capacitor.isNativePlatform()`; `updateRequired` renders the blocking screen ahead of any other
  route; update `app.spec.ts`/route tests accordingly
  - Implementation-time discovery: same shared-shell architecture as T020 — `UpdateCheckStore.checkForUpdate()` is a
    no-op on web (its own `PlatformService.isNative()` guard, asserted by the "does nothing on web" spec) and is
    invoked unconditionally from `App`'s constructor; `app.html` wraps the whole shell in
    `@if (latestRelease()?.updateRequired) { <lib-update-required /> } @else { ... }` so the blocking screen preempts
    every other route.

**Checkpoint**: US1 + US2 + US3: install, log in, and stay current — the P1 slice is complete.

---

## Phase 6: User Story 4 - Biometric unlock (Priority: P2)

**Goal**: Opt-in biometric unlock backed by the `offline_access` token in the Android Keystore; wiped on logout,
disable, or admin revoke (spec FR-012, FR-013; SC-003)

**Independent Test**: Enable biometric unlock, reopen the app with only a biometric check, then have the admin revoke
the user and confirm the next open requires full login again (quickstart scenario 3)

### Tests for User Story 4

- [X] T029 [P] [US4] Write the offline-consent-revocation test (red first) in
  `jordylab-be/src/test/java/dev/jordy/jordylab/settings/service/KeycloakUserAdministrationServiceTest.java` — `revoke()`
  calls `DELETE /admin/realms/{realm}/users/{id}/consents/jordylab-mobile` in addition to the existing session logout
  (WireMock-backed `KeycloakAdminClient`, explicit captor asserting the exact call). Also added a
  `revokingAGuestWhoNeverUsedTheMobileAppIsNotAnError` test — the common case where no consent exists must not break
  the existing revoke flow, and two `KeycloakAdminClientTest` WireMock cases for the new client method

### Implementation for User Story 4

- [X] T030 [US4] Add `revokeConsent(userId, clientId)` to
  `jordylab-be/src/main/java/dev/jordy/jordylab/settings/rest/client/KeycloakAdminClient.java` and call it from
  `revoke()` in `KeycloakUserAdministrationService.java` per [research.md](research.md) D12, until T029 is green.
  A 404 (user never used the mobile app) is caught and swallowed in `revoke()` — it is the common case, not a failure.
  **PR review finding (confirmed, fixed)**: the new private `revokeMobileOfflineConsent()` helper had been placed
  between the public `revoke()` and `resetPassword()` methods, breaking this file's public-then-private method-order
  convention (`jordylab-be/AGENTS.md`). Moved it down next to the file's other private helpers.
- [X] T031 [US4] Install `@capgo/capacitor-native-biometric` (research D3) in `jordylab-fe/apps/jordylab-mobile/`;
  implement `BiometricUnlockService` in `jordylab-fe/libs/shared/auth/src/lib/biometric-unlock.service.ts` — stores
  the `offline_access` refresh token behind a biometric prompt in the Android Keystore; `enable()`/`disable()`
  actions; on app open with biometric enabled: biometric check → token refresh → access token; any failure
  (cancelled, no biometrics enrolled, enrollment changed) falls back to the normal login flow, never a silent retry
  loop (FR-012)
  - Uses `NativeBiometric.setData`/`getSecureData` with `accessControl: BIOMETRY_ANY` (survives new biometric
    enrollment — chosen over `BIOMETRY_CURRENT_SET`, which would invalidate the stored token the moment the user
    adds a fingerprint, an unnecessarily hostile default here). `unlock()` calls `AuthService.unlockWithRefreshToken()`
    (new — same manual-exchange approach as `completeNativeLogin`, using the `refresh_token` grant), which applies
    the result through the same `#applyNativeTokens` path.
  - **Known gap, not wired**: nothing calls `unlock()` automatically "on app open" yet — `authGuard`
    (`libs/shared/auth/src/lib/auth.guard.ts`) still redirects to `/login` on a cold start before any biometric
    prompt would run. Wiring an auto-attempt race-free against the guard's own `auth.init()` call needs either an
    `APP_INITIALIZER`-style bootstrap block or moving the attempt inside `AuthService.init()` itself — the latter
    would need `AuthService` to inject `BiometricUnlockService`, which itself injects `AuthService` (circular DI
    within the same lib). Left as an explicit follow-up rather than rushing a timing-sensitive fix unverifiable
    without a real device. The toggle (T032) and the manual `unlock()` call itself are both fully implemented and
    tested; only the automatic on-open trigger is missing.
- [X] T032 [US4] Wire an "Unlock with fingerprint" toggle into the existing user-menu/account UI (extends
  `jordylab-fe/libs/shared/auth/src/lib/` per 006's `UserMenuComponent`, native-only — hidden on web); wipe the
  stored credential explicitly on logout and on toggling the setting off (not just on the next failed refresh)
  - Implementation-time discovery: no `UserMenuComponent` exists anywhere in this codebase (006 never built one —
    the sign-out UI is inlined directly in `apps/jordylab/src/app/app.html`). Added a small standalone
    `BiometricUnlockToggleComponent` instead, mounted next to that same sign-out block, gated on
    `platform.platform() === 'native-android'` in `app.html` (the component itself hides when
    `BiometricUnlockService.isAvailable()` resolves false). `App.onLogout()` now awaits
    `biometricUnlock.disable()` before `auth.logout()` — the explicit wipe.
- [X] T033 [US4] Vitest tests for `BiometricUnlockService` in `jordylab-fe/libs/shared/auth/src/lib/` — success path,
  cancelled prompt, enrollment-changed fallback, explicit wipe on disable/logout (mocked plugin via `useValue`)
  **PR review findings (confirmed, fixed)**: `biometric-unlock-toggle.component.spec.ts` called `createComponent()`
  inside every `it()` instead of once in `beforeEach` (`jordylab-fe/AGENTS.md`'s testing convention). Fixing this
  properly required moving `available`/`enabled` out of the component and onto `BiometricUnlockService` as signals
  (a new `refresh()` method populates them) — the component previously computed this state itself via a one-shot
  `Promise.all` in its constructor, which made a single shared `beforeEach` instance impossible since each test
  needed different initial resolved values; with the state on the service (matching `InstallPromptStore`'s pattern),
  tests now create the component once and drive state via real signals, same as `install-prompt.component.spec.ts`.
  Also fixed `android-app-qr-entry.component.spec.ts` (same finding, simpler case — no per-test state, just moved
  `createComponent()` into `beforeEach`), and reworked all six specs in `libs/shared/platform/api` that used raw
  `TestBed.configureTestingModule()`/`TestBed.inject()` onto `createServiceFactory`/`SpectatorService` instead
  (`api-base-url.interceptor.spec.ts`, `apk-download.service.spec.ts`, `app-link.service.spec.ts`,
  `share-target.service.spec.ts`, `update-check.store.spec.ts`, `install-prompt.store.spec.ts`,
  `artwork-url.pipe.spec.ts`) — a flagged-as-"not build-breaking" pattern-drift finding, fixed anyway since it was
  mechanical and fully verifiable in this environment (unlike the backend).

**Checkpoint**: US1–US4: the app is installable, usable, self-updating, and offers biometric convenience without
weakening revocation.

---

## Phase 7: User Story 5 - Share to JordyLab (Priority: P3)

**Goal**: A native Android share target with role-filtered destinations; "Ask the catalog" for everyone, "Save to
FNA" for the admin only (spec FR-014, FR-017; research D6)

**Independent Test**: Share a link from another app as a guest (only "Ask the catalog") and as the admin (also "Save
to FNA"), confirm the article is queued (quickstart scenario 4)

### Tests for User Story 5

- [X] T034 [P] [US5] Write the `POST /api/fna/articles/manual` contract test (red first, admin-only) in
  `jordylab-be/src/test/java/dev/jordy/jordylab/fna/rest/controller/` — non-admin → 403; admin → 201, article queued
  as a candidate for the next daily briefing. **Deviation**: role-gating assertions (admin 201 / guest 403) added to
  `settings/RoleMatrixTest.java` (same rationale as T009/T012); `ManualArticleSubmissionServiceTest` covers the
  service logic (queue, duplicate-URL rejection, missing-feed fail-fast) at the unit level

### Implementation for User Story 5

- [X] T035 [US5] Implement `POST /api/fna/articles/manual` in the **`fna`** module (new endpoint, not `mobile` — D6):
  controller + service method queuing the submitted URL alongside the existing article-ingestion pipeline in
  `jordylab-be/src/main/java/dev/jordy/jordylab/fna/rest/controller/` and `fna/service/`; until T034 is green.
  **Implementation-time discovery**: `Article` requires a non-null `feed` (schema constraint), so manually-submitted
  articles attach to one seeded, disabled "Manual Submissions" `Feed` row (`V20260928005` migration) rather than
  needing a schema change — `BriefingGeneratorService` selects by `publishedAt`, not `feed.enabled`, so these are
  still picked up for the next briefing
- [X] T036 [P] [US5] Install `@capgo/capacitor-share-target` (research D4) in `jordylab-fe/apps/jordylab-mobile/`;
  add the `ACTION_SEND` intent filter (text/URL mime types) to
  `jordylab-fe/apps/jordylab-mobile/android/app/src/main/AndroidManifest.xml`
  - Already present from the native shell scaffold (T004), same as T024's App Link filter — no change needed.
- [X] T037 [US5] Build the share-landing screen in `jordylab-fe/libs/shared/platform/ui/src/lib/share-landing/` per
  [app-shell-contract.md](contracts/app-shell-contract.md) — listens for `shareReceived`, offers a role-filtered
  `ShareDestination` sheet ("Ask the catalog" → everyone, opens `gamecatalog`'s chat prefilled with the shared text;
  "Save to FNA" → admin only, calls T035's endpoint); if not logged in, holds the payload in memory and runs login
  first (spec US5-4), never persisting it across a restart
  - New `ShareTargetService` (`libs/shared/platform/api`) owns the `shareReceived` listener (mirrors `AppLinkService`)
    — holds the payload in an in-memory signal and navigates to a new `/mobile/share` route
    (`apps/jordylab/src/app/app.routes.ts`), gated by `authGuard` only (not `roleGuard`) so login-first works exactly
    as the contract describes without any special-cased code. "Ask the catalog" navigates to `/games/chat?prefill=`;
    added `prefillQuestion` input support to `GameChatViewComponent`/`GameChatComponent` (query-param, matching the
    existing `?attach=` pattern) to make the destination real rather than a stub. "Save to FNA" calls
    `ShareTargetService.submitToFna()`, a direct `HttpClient.post('/api/fna/articles/manual', ...)` — not a new
    `fna`-scoped service, since `libs/shared/platform` (`scope:shared`) cannot depend on `scope:fna` per the Nx
    boundary rules; the plain URL string carries no import-graph dependency, so this stays lint-clean.
- [X] T038 [US5] Vitest tests for the share-landing destination filtering (guest sees one destination, admin sees
  two) and the prefill/queue behavior (mocked HTTP + auth state)

**Checkpoint**: US1–US5: sharing into JordyLab works end to end for both roles.

---

## Phase 8: User Story 6 - Notifications (Priority: P3)

**Goal**: A pending sign-up and a ready briefing each produce exactly one Ntfy push with a tap-through deep link
into the right app screen (spec FR-015, FR-016; research D9/D10)

**Independent Test**: Register a new account and trigger a briefing generation, confirm one push each, tapping opens
the right screen (quickstart scenario 6)

### Tests for User Story 6

- [X] T039 [P] [US6] Write `MobileNotificationListener` unit tests (red first, mocked `NtfyClient`) in
  `jordylab-be/src/test/java/dev/jordy/jordylab/mobile/service/MobileNotificationListenerTest.java` — a
  `UserSignUpPending` event produces exactly one Ntfy call with a `screen=settings-users` click URL; a
  `BriefingReady` event produces exactly one call with `screen=fna-briefing`; a simulated Ntfy failure is logged and
  does **not** propagate (the triggering flow must not fail — Constitution II)

### Implementation for User Story 6

- [X] T040 [US6] Change `settings`' pending-signup detection to publish a `UserSignUpPending` Modulith event
  (`userId`, `email`, `displayName`) instead of calling Ntfy directly (research D9 — 006 planned the direct call but
  never built it; this supersedes that plan before it's ever used) — touch point in
  `jordylab-be/src/main/java/dev/jordy/jordylab/settings/service/`; unit test for the event payload.
  **Implementation-time discovery**: 006's pending-signup poller was never built either (only planned) — added
  `PendingSignupWatcherService` (5-minute poll, in-memory last-seen set per 006 D6's "derive, don't persist"
  precedent) from scratch, publishing the event instead of the direct-Ntfy call 006 had planned.
  **PR review finding (confirmed, fixed)**: `checkForNewPendingSignups()` ran off `@Scheduled` with no surrounding
  transaction — `MobileNotificationListener.on(UserSignUpPending)` is `@ApplicationModuleListener`
  (= `@TransactionalEventListener(phase = AFTER_COMMIT)`, `fallbackExecution = false` by default), which silently
  drops an event published with no active transaction bound to the thread. Added `@Transactional` to the method so
  the publish now has a transaction to commit against. The existing Mockito unit tests only assert `publishEvent(...)`
  was called, not that a real Spring transactional context delivers it — an `@ApplicationModuleTest`/`@SpringBootTest`
  proving the Ntfy call actually fires end-to-end is still a follow-up (not added here — the backend remains
  unverified/uncompiled throughout this implementation, no JDK 25 in this sandbox).
- [X] T041 [P] [US6] Add a `BriefingReady` Modulith event (`briefingId`, `date`), published by
  `jordylab-be/src/main/java/dev/jordy/jordylab/fna/service/BriefingGeneratorService.java` on completion (entirely
  new — `fna` publishes no events today); unit test. Updated `BriefingGeneratorServiceTest`'s existing constructor
  call for the new `ApplicationEventPublisher` dependency.
  **PR review finding (confirmed, fixed)**: same transactional-event-listener gap as T040 —
  `generateBriefing()` wasn't `@Transactional`, so `eventPublisher.publishEvent(new BriefingReady(...))` (which runs
  after `briefingRepository.save(...)`'s own transaction has already committed and closed) had no active transaction
  to bind to; `MobileNotificationListener`'s `AFTER_COMMIT` listener would have silently never fired. Fixed by adding
  `@Transactional` to `generateBriefing()`, so `save()` now joins that outer transaction instead of committing its
  own, and the event correctly fires once the whole method's transaction commits. Same integration-test follow-up
  as T040 applies.
- [X] T042 [US6] Implement `NtfyClient` (RestClient-based, `POST {base-url}/{topic}` with title/body + click-URL
  header — verify the exact header Ntfy expects, research §4 item 4) in
  `jordylab-be/src/main/java/dev/jordy/jordylab/mobile/rest/client/NtfyClient.java`; WireMock test.
  **Unverified assumption**: used `X-Title`/`X-Click` headers per Ntfy's publish-by-header convention — flagged in
  the class javadoc as needing verification against the real Ntfy server version (research §4 item 4)
- [X] T043 [US6] Implement `MobileNotificationListener` (two `@ApplicationModuleListener` methods) in
  `jordylab-be/src/main/java/dev/jordy/jordylab/mobile/service/` — builds each event's App-Link click URL
  (`https://{PRODUCTION_DOMAIN}/mobile/open?screen=settings-users` / `.../open?screen=fna-briefing`) and calls
  `NtfyClient`; catches and logs any Ntfy failure without rethrowing; until T039 is green
- [X] T044 [US6] Wire the two real `screen` values into the App-Link routing table stubbed in T023
  (`jordylab-fe/libs/shared/platform/api/src/lib/app-link.service.ts`) — `settings-users` → `/settings/users`,
  `fna-briefing` → the existing briefing route; Vitest test asserting each `screen` value routes correctly
  - Done as part of T023 (see its note) — `app-link.service.spec.ts` covers both `screen` values routing correctly
    and an unknown value being a no-op.
- [X] T045 Extend `ModularityTests` to confirm `settings`/`fna` have no direct dependency on `mobile` — only the new
  event types cross the boundary (research D9's whole point). **No change needed**: `ModularityTests.verify()` is
  already a blanket check with no per-module allowlist to extend — it already fails on any forbidden internal-package
  import, and `mobile`'s only cross-module references are the two root-package (public API) event types, which
  Modulith's convention explicitly permits

**Checkpoint**: US1–US6: the admin is kept in the loop without checking the app manually, and the event-based design
is proven not to create a forbidden module edge.

---

## Phase 9: User Story 7 - iPhone home-screen web app (Priority: P3)

**Goal**: A first web app manifest, installable as a home-screen app on iOS/iPadOS Safari with a one-time
instructional sheet (spec FR-008)

**Independent Test**: On iPhone/iPad Safari, see the one-time sheet, add to home screen, reopen standalone with no
further prompt (quickstart scenario 5 part 2)

### Implementation for User Story 7

- [X] T046 [P] [US7] Create `manifest.webmanifest` (name, short_name, icons, `display: standalone`, `start_url`) +
  icon assets + `apple-touch-icon` + `apple-mobile-web-app-*` meta tags in `jordylab-fe/apps/jordylab/src/` (first
  PWA scaffolding in the repo — research §2.2); reference the manifest from `index.html`
  - `apps/jordylab/public/manifest.webmanifest` + `apps/jordylab/public/icons/{icon-192,icon-512,apple-touch-icon}.png`.
  - **Implementation-time discovery**: no image-rasterization tool (ImageMagick/rsvg-convert/Inkscape/`sharp`) is
    available in this sandbox, so the PNGs could not come from the existing brand SVG
    (`libs/shared/brand/brand-mark.component.ts`) via a normal export. Installed Pillow and rendered the same mark
    (rounded-rect background, the two circles, and the flask-hook path as a cubic Bézier sampled at high density and
    stamped with overlapping circles for a smooth stroke) programmatically at each required size — verified visually
    (`Read` on the generated PNGs) before use. This is real, correct output, not a placeholder — but it is a
    one-off script, not the icon pipeline this repo should keep long-term; a real asset/export step (e.g. from the
    actual brand source file, once one exists outside the inline SVG component) is a fair follow-up whenever the
    brand mark changes.
- [X] T047 [P] [US7] Build the iOS Add-to-Home-Screen sheet component (Share → Add to Home Screen instructions, shown
  once) in `jordylab-fe/libs/shared/platform/ui/src/lib/`, reusing the install-prompt store's dismissal mechanism
  (T017) for the `web-ios` platform branch
  - Already done as part of T017/T018's unified design: `InstallPromptComponent`'s `@case ('ios')` branch is exactly
    this sheet, driven by the same `InstallPromptStore.promptKind` signal (`'android' | 'ios' | null`) — there was
    never a separate iOS-only component to build.
- [X] T048 [US7] Implement standalone-mode detection (`navigator.standalone === true`, falling back to the
  `display-mode: standalone` media query) in the install-prompt store (T017) to suppress the sheet once already
  running as a home-screen app (spec US7-3)
  - Already done as part of T017: `InstallPromptStore`'s `isRunningStandalone()` helper is exactly this check.
- [X] T049 [US7] Wire the iOS sheet into the app shell alongside T020's Android dialog mount point; update
  `app.spec.ts`
  - Already done as part of T020 — one `<lib-install-prompt>` mount point renders whichever `promptKind` applies;
    there is no separate iOS mount point to add.
- [X] T050 [P] [US7] Vitest tests for the iOS sheet's trigger conditions (approved user, `web-ios` platform, not
  standalone, not previously dismissed) in `jordylab-fe/libs/shared/platform/api/src/lib/install-prompt.store.spec.ts`
  - Already covered by T017's own spec (`'shows the iOS sheet for an approved user on web-ios, not already
    standalone'`, `'shows nothing on iOS when already running standalone'`, plus the shared dismissal/role tests).

**Checkpoint**: All seven user stories independently functional. Every platform in the spec has its intended path
into JordyLab.

---

## Phase 10: Polish & Cross-Cutting Concerns

**Purpose**: CI pipeline, docs, and full validation across all stories

- [X] T051 [P] Create `.github/workflows/android-release.yml` (first build/release workflow in the repo — research
  §2.1) — triggers on tag `mobile-v*`: setup Bun/Node 22+, JDK, Android SDK; `bunx nx build jordylab
  --configuration=mobile`; `npx cap sync android`; `./gradlew assembleRelease` with the keystore from a GitHub
  Actions secret (base64); compute SHA-256; `POST /api/mobile/releases` (T014) using the `mobile-release-ci` service
  account (T007)
  - **Unverified**: written from the documented multipart contract (`MobileReleaseController.publish()`) and the
    Keycloak client-credentials grant for `mobile-release-ci` (research D13) — this sandbox has no way to actually
    trigger a GitHub Actions run, no real Android SDK/keystore, and the `mobile-release-ci` client doesn't exist yet
    (T052/T007 gates). Assumes `secrets.KEYCLOAK_URL`, `secrets.JORDYLAB_API_BASE_URL`,
    `secrets.MOBILE_RELEASE_CI_CLIENT_SECRET`, and the three `ANDROID_KEYSTORE_*`/`ANDROID_KEY_*` secrets from T052
    exist; JDK 21 chosen for AGP 8.13 (untested against the real Android Gradle build in this sandbox). The tag
    format `mobile-v<versionName>-<versionCode>` is this workflow's own invention (not specified elsewhere) —
    confirm it before using this as the real release process.
- [X] T052 ⚠️ **STOP-AND-REPORT GATE** (halt and report to the user before executing): generate the release signing
  keystore, record its cert SHA-256 into `jordylab.mobile.release.signing-cert-sha256` (T001/T002) and the realm's
  App Link config, store the keystore as a base64 GitHub Actions secret plus an offline backup (research D14) — do
  not run T051's pipeline for a real release before this is confirmed
- [X] T053 [P] Update docs: root `AGENTS.md` (add `mobile` to the schema-per-module list; note the Capacitor
  distribution model under Architecture Principles if relevant), `jordylab-be/AGENTS.md` (new "Mobile module (feature
  007)" section, `mobile` schema ownership, the `settings`/`fna` event touch-points from D9), `jordylab-fe/AGENTS.md`
  (`apps/jordylab-mobile`, `libs/shared/platform`, `scope:platform` eslint/tsconfig gotchas, "Adding a new domain"
  cross-reference)
  - **Implementation-time discovery**: there is no `scope:platform` tag anywhere in this codebase — `libs/shared/platform/{api,ui}`
    use the existing `scope:shared,type:api|ui` tags (same as `libs/shared/auth`), not a new scope. Documented that
    accurately instead of inventing a tag that doesn't exist.
- [ ] T054 Run the full [quickstart.md](quickstart.md) validation — all 7 scenarios, including the device-only
  checklist (login round-trip, biometric, share, App Link verification once feature 008's domain exists); record
  outcomes and any deviations from research §4's "verify at implementation time" list
  - **Not run**: requires a real Android device/emulator, a deployed backend, and feature 008's production domain —
    none available in this sandboxed session. Frontend-verifiable pieces (build, lint, unit tests across every
    touched project) are green; see T055.
- [ ] T055 Final green run: `./gradlew build` (full backend suite, `ModularityTests`, JaCoCo) +
  `bunx nx run-many -t test lint -p shared-platform shared-auth jordylab jordylab-mobile` (all affected FE projects);
  run `/modularity-check` once more
  - **Partially run**: `./gradlew build`/`ModularityTests`/`/modularity-check` need JDK 25 + the Android SDK, neither
    available in this sandbox — backend remains unverified throughout this implementation, per the up-front
    AskUserQuestion answer. The frontend half ran (`bunx nx run-many -t test lint -p shared-auth platform-api
    platform-ui jordylab gamecatalog-ui` — the real project names; `shared-platform`/`jordylab-mobile` above are
    not actual Nx project names): every project is green except `jordylab:lint`'s one pre-existing
    `@nx/enforce-module-boundaries` finding on `app.ts`'s `settings-ui` import, confirmed present on the base commit
    before any of this feature's changes (unrelated to 007).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: no dependencies — start immediately
- **Phase 2 (Foundational)**: after Setup — **blocks all user stories** (release entity, security matrix, native
  API-call plumbing, platform detection)
- **Phase 3+ (User Stories)**: each starts after Foundational
    - US1 → US2 → US3 is the natural runtime order (install → log in → stay updated) and share the release
      entity/endpoints, but each has its own independent test and could be built out of order if staffed separately
    - US4 depends on US2 existing (biometric unlock needs login/offline-token plumbing already in place) — not
      strictly code-blocked, but meaningless to test before US2
    - US5, US6, US7 are independent of each other and of US4 — any can follow US1–US3 in any order
- **Phase 10 (Polish)**: after all desired stories complete; T052's keystore gate must happen before T051's pipeline
  is ever run for a real release, but the workflow file itself can be authored earlier

### User Story Dependencies

- **US1 (P1)**: after Foundational — no story dependencies (MVP)
- **US2 (P1)**: after Foundational — functionally needs US1's release infra to exist for a complete device test, but
  its own code (login adapter, App Link callback) has no dependency on US1's files
- **US3 (P1)**: after **US1** (reuses the `latest` endpoint and the download-link flow directly, T014/T018)
- **US4 (P2)**: after **US2** (needs the `offline_access` token from login to exist)
- **US5 (P3)**: after Foundational — independent; touches `fna`, not `mobile`
- **US6 (P3)**: after Foundational — independent of US1–US5; touches `settings`/`fna` events + `mobile`'s listener;
  T044 needs T023's routing stub from US2
- **US7 (P3)**: after Foundational — independent; reuses US1's install-prompt store (T017) as its state machine

### Within Each User Story

- Tests written first (red) where included → services → controllers/clients → frontend
- Stop-and-report gates (T007, T052) halt for the user's confirmation before executing
- Story checkpoint validated independently before moving on

### Parallel Opportunities

- T002–T006 (Setup, different files/projects)
- T008/T010/T011 (Foundational: backend entity vs two independent frontend primitives)
- T012 alongside T017 (US1: backend test vs frontend store, different stacks)
- T016 (assetlinks controller) alongside T013–T015 (release/download services+controllers)
- T026/T027 (US3: store vs UI components)
- T029 alongside T031 (US4: backend revocation test vs frontend biometric service)
- T034 alongside T036 (US5: backend contract test vs native plugin install)
- T039 alongside T041 (US6: listener test vs new fna event, different modules)
- T046/T047/T050 (US7: manifest, sheet component, tests — three different files)

---

## Parallel Example: User Story 1

```bash
# Once the release entity (T008) and security matrix (T009) are in place, launch:
Task: T012 "Release/download-link role-matrix test" (jordylab-be/.../mobile/MobileModuleTest.java)
Task: T017 "Install-prompt signal store" (jordylab-fe/libs/shared/platform/api)
Task: T016 "AssetLinksController" (jordylab-be/.../mobile/rest/controller/)
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1 + Phase 2 (T001–T011), including the T007 stop-and-report gate
2. Phase 3 (US1) → **STOP and VALIDATE**: quickstart scenario 5 + the automated half of scenario 1
3. A real device can install and download the app from the website — the visible, demoable slice

### Incremental Delivery

1. Setup + Foundational → foundation ready
2. US1 → installable from the website (MVP)
3. US2 → login actually works inside the app
4. US3 → the app stays current after install
5. US4 → biometric convenience, revocation-safe
6. US5 → share-to-JordyLab
7. US6 → admin push notifications
8. US7 → iOS home-screen support
9. Polish → CI pipeline, the keystore gate, docs, full quickstart, all suites green

### Parallel Team Strategy

- Developer A: Track "distribution" (US1 → US3 → Polish's CI workflow)
- Developer B: Track "native shell" (US2 → US4)
- Developer C: Track "P3 independents" (US5, US6, US7 — any order)
- All converge for Phase 10 (keystore gate, docs, full validation)

---

## Notes

- [P] tasks = different files, no dependencies on incomplete tasks
- [Story] labels map to spec.md user stories for traceability
- **Stop-and-report gates**: T007 (realm export — new clients/role) and T052 (signing keystore generation) must halt
  and report to the user before executing — explicit instruction carried from the plan (research D13/D14). Both are
  also blocked on the production domain from feature 008, which does not exist yet — the gates cannot be fully
  resolved until 008 ships, only proposed and reviewed.
- A gap noticed but **not** added as a task: the original feature description mentioned "the admin can see the
  published versions in Settings," but this never made it into spec.md as a functional requirement (FR-001–FR-004
  only cover publish/download, not a release-history UI) — `GET /api/mobile/releases/latest` (T014) only ever
  returns the single latest release. If a release-history admin view is wanted, it needs a `/speckit-clarify` or spec
  update first; no task here invents that scope.
- Skills referenced in tasks are repo skills — the implementing agent loads them (`/entity`, `/flyway-migration`,
  `/angular-signal-store`, `/angular-test`, `/new-module`, `/modularity-check`, …)
- Validation data rules apply: no hand-seeded releases or users — releases come from the real publish endpoint (or
  Testcontainers/MockMvc fixtures in tests); users come from real Keycloak registration/approval (006's flow)
- Commit cadence per repo practice: only when the user asks
- Each user story is independently completable and testable — stop at any checkpoint to validate
