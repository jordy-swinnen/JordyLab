---

description: "Task list for Replace the Native Android Wrapper with an Installable Web App"
---

# Tasks: Replace the Native Android Wrapper with an Installable Web App

**Input**: Design documents from `/specs/018-pwa-replaces-capacitor/`

**Prerequisites**: [plan.md](plan.md) (required), [spec.md](spec.md) (required for user stories), [research.md](research.md), [data-model.md](data-model.md), [contracts/pwa-manifest-contract.md](contracts/pwa-manifest-contract.md), [contracts/share-target-contract.md](contracts/share-target-contract.md), [contracts/notifications-contract.md](contracts/notifications-contract.md), [contracts/removal-inventory.md](contracts/removal-inventory.md), [quickstart.md](quickstart.md)

**Tests**: Included per the JordyLab Constitution (IV. Testing Discipline) and `jordylab-be/AGENTS.md` / `jordylab-fe/AGENTS.md` (80 % per-package/per-library coverage backstop; every rewritten unit ships with its spec). Story phases list the spec tasks first — they fail against the current code, then the implementation makes them pass.

**Organization**: Tasks are grouped by user story (`US1` install P1, `US2` share P1, `US3` notifications P2, `US4` retire the stack P2) so each story lands and verifies independently. The removal story (US4) comes **last** on purpose: every replacement is live before the native code it replaces is deleted, so prod keeps working at every commit.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Which user story this task belongs to (`[US1]`…`[US4]`)
- Exact file paths are included in every task description

## Rules that apply to every task

- **Java**: Never use `var`; blank line before every `return`; method order public → package-private → private; Lombok `@RequiredArgsConstructor`/records; no hand-written constructors.
- **Java tests**: JUnit 5, AssertJ (`assertSoftly`), Mockito without `any()` (explicit values or an assigned, asserted `ArgumentCaptor`), `@Language("JSON")` on inline JSON.
- **Angular**: `inject(Service)` over constructor injection, `#field` over `private`, signals for state, Container–Presentation, Vitest + `@ngneat/spectator/vitest`, mocks as plain objects via `useValue` with `vi.fn()` and real `signal(...)` instances — never `useClass` mock classes.
- **After every TypeScript edit**: run `jordylab-fe/tools/lint-changed.sh <file>` and fix what it prints.
- **Commits**: subject `JOR-38: <summary>` (ticket first, per the repo commit rules) with a `Refs: 018` or `Refs: 018/T###` trailer. Never `--no-verify`.
- **Dev stack**: read-only. Throwaway stacks come only from `jordylab-fe/e2e/run.sh`. Never hand-seed the database.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: A green baseline on a current branch, and the conditional gate confirmed, before any change

- [X] T001 Sync this branch with the latest `origin/main` (`git pull --rebase origin main` — nothing is committed yet, so this is trivial) and verify the green baseline: `jordylab-be` `./gradlew check --no-daemon` and `jordylab-fe` `bunx nx run-many -t lint` + `bunx nx run-many -t test` all pass
- [X] T002 Confirm the presence-spike decision gate (Linear `JOR-36`, `specs/_drafts/presence-ha-prototype/`) has resolved in favour of handling geofencing outside the JordyLab app — the spec's first Assumption. If it is still open, **stop and report** instead of starting (the draft is conditional on it). **Resolved 2026-11-02**: JOR-36 re-scoped to a custom Android home-screen widget spike (no native background geofencing) — owner confirmed "Proceed".

---

## Phase 2: Foundational — the installable shell (blocks US1 and US2)

**Purpose**: The web app manifest and service worker that make `apps/jordylab` installable at all. US3 (backend-only) may start in parallel with this phase; US1 and US2 build on it.

**⚠️**: Do not add `share_target` here — it lands with US2, together with the `/share` route it points at.

- [X] T003 [P] Extend `jordylab-fe/apps/jordylab/public/manifest.webmanifest` per [contracts/pwa-manifest-contract.md](contracts/pwa-manifest-contract.md): add `id: "/"` and `scope: "/"`, and `"purpose": "any maskable"` on the 192/512 icons. Colours stay `#131314` (016 default)
- [X] T004 [P] Create `jordylab-fe/apps/jordylab/public/sw.js` per research D1: `install` → `skipWaiting()`, `activate` → `clients.claim()`, and a `fetch` listener that **never calls `respondWith`** — installability criterion satisfied, nothing cached, `/api/**`, LibBot SSE and `/auth/**` stay network-only (FR-002)
- [X] T005 Register the service worker from `jordylab-fe/apps/jordylab/src/main.ts` after `bootstrapApplication` settles: `if ('serviceWorker' in navigator) navigator.serviceWorker.register('/sw.js')` — registration failure logs, never breaks the app. Only the `jordylab` app registers (the fna/gamecatalog harnesses have no `sw.js`)
- [X] T006 [P] Create `jordylab-fe/apps/jordylab-e2e/src/pwa.spec.ts` with the manifest + service-worker journeys from [quickstart.md](quickstart.md) §B: `GET /manifest.webmanifest` → 200, `application/manifest+json`, every manifest-contract field (no `share_target` yet); `GET /sw.js` → 200; a service worker registers on the start URL
- [X] T007 Verify the shell: `cd jordylab-fe && e2e/run.sh web` green (manifest/SW journeys pass, the rest unaffected), and `bun tools/app-boot-check.mjs` still passes — the CSP is Report-Only with `script-src 'self'`, and a same-origin `sw.js` + manifest must not trip it or the app-boot policy check

**Checkpoint**: The web app is installable by manifest + SW criteria (SC-001's foundation). No user-visible behaviour has changed for existing users.

---

## Phase 3: User Story 1 - Install and launch JordyLab as a web app (Priority: P1) 🎯 MVP

**Goal**: Approved users install JordyLab from the browser (Android native prompt, iOS instructions), launch standalone on the site's own origin, and find a permanent install entry in `Settings → App` plus the desktop QR.

**Independent Test**: Unit specs prove capture/dismissal/standalone/role gating; the manifest+SW e2e journeys from Phase 2 pass; the owner completes phone check C1 in [quickstart.md](quickstart.md) (install → standalone launch → browse).

### Tests for User Story 1 ⚠️

- [X] T008 [P] [US1] Rewrite `jordylab-fe/libs/shared/platform/api/src/lib/install-prompt.store.spec.ts` to the new contract (fails against the current suppressing store): `beforeinstallprompt` is captured and `promptInstall()` calls the stashed event's `prompt()`; 30-day dismissal via `jordylab.install-prompt.dismissed-until` (existing devices stay suppressed); hidden in standalone mode and without an app role; `web-ios` sheet path without a captured event
- [X] T009 [P] [US1] Rewrite `jordylab-fe/libs/shared/platform/ui/src/lib/install-prompt.component.spec.ts`: the Android sheet triggers the native install prompt (no `status` input, no `download` output), the iOS sheet keeps its instructions + "Got it" dismissal
- [X] T010 [P] [US1] Create `jordylab-fe/apps/jordylab/src/app/app-settings/install-on-phone-card.component.spec.ts`: shown on `web-android`/`web-ios` when not standalone; install button when the event is captured, per-browser instruction fallback otherwise; carries the uninstall-old-APK note

### Implementation for User Story 1

- [X] T011 [US1] Rewrite `jordylab-fe/libs/shared/platform/api/src/lib/install-prompt.store.ts` per [contracts/pwa-manifest-contract.md](contracts/pwa-manifest-contract.md): capture (not suppress) `beforeinstallprompt`, keep `dismiss()` + `isRunningStandalone()`, delete `suppressBrowserInstallPrompt()`
- [X] T012 [US1] Rewrite `jordylab-fe/libs/shared/platform/ui/src/lib/install-prompt.component.ts` to the two sheets (Android → `promptInstall()`, iOS → instructions), self-contained (the store owns prompting)
- [X] T013 [US1] Create `jordylab-fe/apps/jordylab/src/app/app-settings/install-on-phone-card.component.ts` (the T010 spec drives it)
- [X] T014 [US1] Mount the card in `jordylab-fe/apps/jordylab/src/app/app-settings/app-settings-page.component.ts` (phone browsers, not standalone) and retitle the desktop entry to "Get the phone app" in `jordylab-fe/apps/jordylab/src/app/app.html` + the hint copy in `jordylab-fe/libs/shared/platform/ui/src/lib/android-app-qr-entry.component.ts` (QR target stays the site origin)
- [X] T015 [US1] Rewire `jordylab-fe/apps/jordylab/src/app/app.ts` + `app.html`: remove the APK download handler (`onInstallDownload`, `installStatus`, `ApkDownloadService` usage) and the `suppressBrowserInstallPrompt()` call; the install sheet mounts unchanged. The update-check/biometric wiring stays until US4
- [X] T016 [US1] Verify US1: `bunx nx run-many -t test -p platform-api platform-ui jordylab` green, `e2e/run.sh web` green, then hand phone check C1 (install + standalone launch) to the owner per [quickstart.md](quickstart.md) §C

**Checkpoint**: US1 complete — the app installs from the browser on Android/iOS and launches standalone (US1's journeys work; the APK dialog path is gone from the UI while the backend still serves it — harmless).

---

## Phase 4: User Story 2 - Share links or text into the installed web app (Priority: P1)

**Goal**: An installed Android PWA appears in the OS share sheet; `/share?title=&text=&url=` shows the payload, offers "Ask LibBot" (all) and "Save to FNA" (admin), survives sign-in, and degrades calmly when empty. Legacy `/mobile/open` notification links redirect.

**Independent Test**: The five e2e share journeys in `pwa.spec.ts` pass on the throwaway stack (guest, admin, signed-out return, empty state, URL-in-text) — [quickstart.md](quickstart.md) §B.

### Tests for User Story 2 ⚠️

- [X] T017 [P] [US2] Rewrite `jordylab-fe/libs/shared/platform/ui/src/lib/share-landing/share-landing.component.spec.ts` per [contracts/share-target-contract.md](contracts/share-target-contract.md): params `title`/`text`/`url`; `targetUrl` = `url` param else first `http(s)://` in `text`; guest sees only Ask LibBot; admin also Save to FNA (disabled with explanation when `targetUrl` is null); submit failure shows a retryable error; empty params → calm empty state
- [X] T018 [P] [US2] Extend `jordylab-fe/libs/shared/auth/src/lib/auth.service.spec.ts` + `login.component.spec.ts`: `login(redirectUrl)` forwards the safe in-app path as the Keycloak redirect URI; the login page passes its `returnUrl` (a signed-out share survives the round trip, FR-008)

### Implementation for User Story 2

- [X] T019 [US2] Rework `jordylab-fe/apps/jordylab/src/app/app.routes.ts`: rename `mobile/share` → `share` (same `authGuard`), and add the legacy redirects per research D7 — componentless routes whose `canActivate` returns a `UrlTree`: `/mobile/open?screen=settings-users` → `/settings/users`, `?screen=fna-briefing` → `/fna/briefing`, otherwise `/`; `/mobile/callback` → `/login`
- [X] T020 [US2] Rewrite `jordylab-fe/libs/shared/platform/ui/src/lib/share-landing/share-landing.component.ts` to read `ActivatedRoute.queryParamMap` (the T017 spec drives it); move the FNA submit (`POST /api/fna/articles/manual`) into the component and delete `jordylab-fe/libs/shared/platform/api/src/lib/share-target.service.ts` + `share-target.service.spec.ts` (the payload lives in the URL now — [contracts/share-target-contract.md](contracts/share-target-contract.md)); update the `libs/shared/platform/api/src/index.ts` + `ui/src/index.ts` barrels
- [X] T021 [US2] Implement the login change in `jordylab-fe/libs/shared/auth/src/lib/auth.service.ts` (optional redirect target on `login()`, default stays the origin) and pass the sanitized `returnUrl` from `jordylab-fe/libs/shared/auth/src/lib/login.component.ts` (the T018 specs drive it). No native branches are touched yet — that is US4
- [X] T022 [P] [US2] Add `share_target` to `jordylab-fe/apps/jordylab/public/manifest.webmanifest`: `{ "action": "/share", "method": "GET", "params": { "title": "title", "text": "text", "url": "url" } }` — only now that the route exists
- [X] T023 [US2] Extend `jordylab-fe/apps/jordylab-e2e/src/pwa.spec.ts` with the five share journeys from [contracts/share-target-contract.md](contracts/share-target-contract.md) (guest → LibBot prefill; admin → Save to FNA; signed-out → login → share intact; bare `/share` → empty state; URL inside `text` only → extracted)
- [X] T024 [US2] Verify US2: `e2e/run.sh web` green including the share journeys; the manifest journey now also asserts `share_target`

**Checkpoint**: US1 + US2 both work independently — the PWA installs, appears in the share sheet, and every share journey passes. The Capacitor share plugin still exists but nothing new depends on it.

---

## Phase 5: User Story 3 - Keep admin push notifications with direct web links (Priority: P2)

**Goal**: `NtfyClient` lives in `shared`, `settings` and `fna` call it directly with web click-through paths, the two notification events are deleted, and `ModularityTests` stays green.

**Independent Test**: Backend runs from [quickstart.md](quickstart.md) §A2 — `ModularityTests`, the moved `NtfyClientTest` (WireMock), and the direct-call tests for the poller and briefing generator all green; `./gradlew check` passes with the JaCoCo package gate covering `shared.rest.client`.

### Tests for User Story 3 ⚠️

- [X] T025 [P] [US3] Move/adapt the WireMock ntfy test to `jordylab-be/src/test/java/dev/jordy/jordylab/shared/rest/client/NtfyClientTest.java` per [contracts/notifications-contract.md](contracts/notifications-contract.md): no-op + startup warning while unconfigured; `POST {base}/{topic}` with `X-Title`/`X-Click`/optional bearer; `clickPath` resolved against `app-base-url`; `RestClientException` swallowed and logged
- [X] T026 [P] [US3] Update the callers' tests for the direct call: the pending-sign-up poller test asserts `publish("New JordyLab sign-up", <body>, "/settings/users")` and the briefing generator test asserts `publish(..., "/fna/briefing")` — both with the swallow-on-failure case (assigned `ArgumentCaptor`, explicit values)

### Implementation for User Story 3

- [X] T027 [US3] Create `jordylab-be/src/main/java/dev/jordy/jordylab/shared/config/NotificationProperties.java` (`@ConfigurationProperties("jordylab.notifications")`: `ntfy.base-url/topic/token` ← same `NTFY_*` env vars, plus `app-base-url`) and `jordylab-be/src/main/java/dev/jordy/jordylab/shared/rest/client/NtfyClient.java` with `publish(String title, String body, String clickPath)` — delivery semantics identical to today (research D4, [contracts/notifications-contract.md](contracts/notifications-contract.md))
- [X] T028 [US3] Rewire `jordylab-be/src/main/java/dev/jordy/jordylab/settings/service/PendingSignupWatcherService.java` to call `NtfyClient` directly (same title/body wording) and delete `jordylab-be/src/main/java/dev/jordy/jordylab/settings/UserSignUpPending.java` + its publications (the events' only listener is being deleted with them)
- [X] T029 [US3] Rewire `jordylab-be/src/main/java/dev/jordy/jordylab/fna/service/BriefingGeneratorService.java` to call `NtfyClient` directly and delete `jordylab-be/src/main/java/dev/jordy/jordylab/fna/BriefingReady.java` + its publication
- [X] T030 [US3] Delete `jordylab-be/src/main/java/dev/jordy/jordylab/mobile/service/MobileNotificationListener.java` and the old `jordylab-be/src/main/java/dev/jordy/jordylab/mobile/rest/client/NtfyClient.java` (+ their tests), remove the `notifications` record from `jordylab-be/src/main/java/dev/jordy/jordylab/mobile/MobileProperties.java`, and swap the config block in `jordylab-be/src/main/resources/application.yaml`: `jordylab.mobile.notifications.ntfy.*` out, `jordylab.notifications.*` in (`app-base-url: ${JORDYLAB_APP_BASE_URL:}`). The rest of the `mobile` module stays until US4
- [X] T031 [P] [US3] Add `JORDYLAB_APP_BASE_URL=https://jordylab.be` to the `backend-config` ConfigMap in `deploy/k8s/overlays/prod/kustomization.yaml` (keep the `NTFY_*` literal; the `MOBILE_*` literals are removed in US4)
- [X] T032 [US3] Verify US3: `./gradlew test --tests "*ModularityTests*" --tests "*Ntfy*" --tests "*PendingSignup*" --tests "*BriefingGenerator*"` then `./gradlew check` — all green, `ModularityTests` green with no listener indirection left

**Checkpoint**: US3 complete — notifications flow through `shared` with web click-throughs; `mobile` is now only release hosting. US1–US3 all independently functional.

---

## Phase 6: User Story 4 - Retire the native wrapper, package hosting and Android CI (Priority: P2)

**Goal**: Everything in [contracts/removal-inventory.md](contracts/removal-inventory.md) is gone: the `mobile` module and schema, the Capacitor apps and dependencies, the native-only frontend services, the Keycloak clients, the Android workflows and deploy plumbing — with `e2e-web`, `./gradlew check` and the lint/test suites green throughout.

**Independent Test**: [quickstart.md](quickstart.md) §A3's removal grep gate returns empty, `./gradlew check` + `bunx nx run-many -t lint` + `-t test` + `e2e/run.sh web` all green on the codebase without the deleted parts.

### Backend removal

- [X] T033 [P] [US4] Delete the `mobile` module: every file under `jordylab-be/src/main/java/dev/jordy/jordylab/mobile/` and `jordylab-be/src/test/java/dev/jordy/jordylab/mobile/` (release/download/assetlinks controllers + models, `MobileReleaseService`, `DownloadLinkService`, their five exceptions, `MobileRelease` + repository, `ApkSigningCertificateReader`, `MobileProperties`, `MobileConfiguration`) per [contracts/removal-inventory.md](contracts/removal-inventory.md)
- [X] T034 [P] [US4] Clean `jordylab-be/src/main/java/dev/jordy/jordylab/shared/config/SecurityConfig.java`: remove the `/api/mobile/**` matchers and the `/.well-known/assetlinks.json` `permitAll` (the `/api/**` `denyAll` then covers the vanished paths) + update the Javadoc; in `jordylab-be/src/main/resources/application.yaml` remove the leftover `jordylab.mobile.*` block and the `spring.servlet.multipart` 200 MB/210 MB overrides (APK-upload only — back to Spring defaults); update any endpoint-gating tests that referenced them
- [X] T035 [P] [US4] Create `jordylab-be/src/main/resources/db/migration/V20261102003__mobile_drop_schema.sql`: `DROP TABLE IF EXISTS mobile.mobile_release;` + `DROP SCHEMA IF EXISTS mobile CASCADE;` + delete the stale Modulith `event_publication` rows for the two deleted events — **read the exact table/column names from `V20260925__create_event_publication_table.sql` first** (research D8); the Testcontainers boot in `./gradlew check` proves the migration applies
- [X] T036 [P] [US4] Remove `revokeMobileOfflineConsent` + `MOBILE_CLIENT_ID` from `jordylab-be/src/main/java/dev/jordy/jordylab/settings/service/KeycloakUserAdministrationService.java` and `revokeConsent` from `jordylab-be/src/main/java/dev/jordy/jordylab/settings/rest/client/KeycloakAdminClient.java` (its only caller); update their tests
- [X] T037 [P] [US4] Remove `com.android.tools.build:apksig` and the `google()` repository block from `jordylab-be/build.gradle.kts`

### Frontend removal

- [X] T038 [P] [US4] Remove the seven native dependencies from `jordylab-fe/package.json` (`@capacitor/android`, `@capacitor/app`, `@capacitor/browser`, `@capacitor/cli`, `@capacitor/core`, `@capgo/capacitor-native-biometric`, `@capgo/capacitor-share-target`) and run `bun install` (updates `jordylab-fe/bun.lock`)
- [X] T039 [P] [US4] Delete `jordylab-fe/apps/jordylab-mobile/` and `jordylab-fe/apps/jordylab-mobile-e2e/` (the latter with its own `package.json`/`bun.lock`/`PINS.md`); remove the `mobile` and `mobile-e2e` build configurations from `jordylab-fe/apps/jordylab/project.json`; delete `jordylab-fe/apps/jordylab/src/environments/environment.mobile.ts` and `environment.mobile-e2e.ts`; clean any Android build-output entries from `jordylab-fe/.gitignore`
- [X] T040 [P] [US4] Delete the native-only platform-api files + specs in `jordylab-fe/libs/shared/platform/api/src/lib/`: `apk-download.service.ts`, `update-check.store.ts`, `latest-release.model.ts`, `app-link.service.ts`, `api-base-url.interceptor.ts`, `api-base-url.token.ts`, `resolve-api-url.ts` (+ each `.spec.ts`); for `artwork-url.pipe.ts` grep its usages first and then either reduce it to a documented identity passthrough with its spec or delete it together with every usage (removal-inventory note) — the barrels in `libs/shared/platform/api/src/index.ts` + `ui/src/index.ts` follow
- [X] T041 [P] [US4] Delete `jordylab-fe/libs/shared/platform/ui/src/lib/update-available-banner.component.ts` + `update-required.component.ts` (+ specs) and finish the shell cleanup in `jordylab-fe/apps/jordylab/src/app/app.ts` + `app.html`: remove `UpdateCheckStore`/`AppLinkService` wiring, `#restoreNativeSession`, the biometric wipe in `onLogout`, and the `lib-update-required`/`lib-update-available-banner` template blocks; remove the `API_BASE_URL` provider + `apiBaseUrlInterceptor` from `jordylab-fe/apps/jordylab/src/app/app.config.ts`; delete `jordylab-fe/apps/jordylab/src/app/app-native-start.spec.ts`; update `app.spec.ts` + `app.config.spec.ts`
- [X] T042 [P] [US4] Remove the native auth machinery in `jordylab-fe/libs/shared/auth/`: delete `src/lib/biometric-unlock.service.ts` + `biometric-unlock-toggle.component.ts` (+ specs); strip `AuthService`'s native branches (`#loginNative`, `completeNativeLogin`, `#logoutNative`, `getRefreshToken`, `unlockWithRefreshToken`, `#applyNativeTokens`, `nativeFailure`, the `Capacitor`/`Browser` imports) and the `mobileCallbackUri` field from `src/lib/auth-config.ts`; remove the fingerprint button + biometric merge from `src/lib/login.component.ts`; update `auth.service.spec.ts`, `login.component.spec.ts`, `user-menu.component.ts`'s sign-out contract comment, and the lib barrel
- [X] T043 [P] [US4] Remove the native-only fingerprint section from `jordylab-fe/apps/jordylab/src/app/app-settings/app-settings-page.component.ts` (the US1 install card stays; `platform.isNative()` gate goes with it)
- [X] T044 [US4] Clean `jordylab-fe/libs/shared/platform/api/src/lib/platform.service.ts`: remove the `@capacitor/core` import and `isNative()` (the UA-based `platform()` detection stays — the install surfaces need it), rewrite `platform.service.spec.ts` without the Capacitor spy, then `grep -rn "isNative" jordylab-fe/libs jordylab-fe/apps --exclude-dir=node_modules` and fix every remaining caller (must be empty after T040–T043)

### Realm, e2e harness and CI

- [X] T045 [P] [US4] Clean both realm exports per research D9: remove the `jordylab-mobile` client, the `mobile-release-ci` client + service account, the `mobile-release-publisher` role + scope mapping and the `admin` composite description mention from `jordylab-be/compose/keycloak-realm-export.json` and `deploy/keycloak/realm-prod.json`
- [X] T046 [P] [US4] Clean the e2e harness: remove the `android` mode from `jordylab-fe/e2e/run.sh` (`prepare_android`, the `ANDROID_*` constants and fixed ports, `signing_certificate_digest`, `E2E_DOWNLOAD_LINK_SECRET`, every `MOBILE_*` env var), delete `jordylab-fe/e2e/android-setup.sh`, and clean `jordylab-fe/e2e/lib/make-realm.py` (the `jordylab-mobile` branch, `mobile-release-publisher` from the e2e-ingest roles, `offline_access` from the e2e admin); then `grep -rn "android\|mobile" jordylab-fe/e2e/` and clear any leftovers (`prove-cleanup.sh`, `cleanup.sh`, READMEs)
- [X] T047 [P] [US4] Delete `.github/workflows/e2e-android.yml` and remove the `apk` job + the Android `version_code` derivation from the `verify` job in `.github/workflows/release.yml` (keep tag verification and the release/deploy flow)

### Deploy manifests

- [X] T048 [P] [US4] Remove the `mobile-releases` volume + mount from `deploy/k8s/base/backend.yaml`, the `mobile-releases` PVC from `deploy/k8s/base/artwork-pvc.yaml`, the `MOBILE_RELEASE_CI_CLIENT_SECRET` env wiring from `deploy/k8s/base/keycloak.yaml`, and the `MOBILE_PRODUCTION_DOMAIN`/`MOBILE_APPLICATION_ID`/`MOBILE_RELEASE_SIGNING_CERT_SHA256` literals from `deploy/k8s/overlays/prod/kustomization.yaml` (the `ntfy-route` in `deploy/k8s/base/gateway.yaml` stays)
- [X] T049 [US4] Remove `MOBILE_RELEASE_CI_CLIENT_SECRET` and `MOBILE_DOWNLOAD_LINK_SECRET` from `deploy/k8s/overlays/prod/secrets.sops.yaml` via SOPS and re-encrypt — **stop and report if the age key is not available locally**; the owner completes this one otherwise (the live PVC/clients/secrets cleanup is the runbook cutover, T052/T054, not this task)
- [X] T050 [US4] Verify US4 end to end per [quickstart.md](quickstart.md) §A: `./gradlew check` green (ModularityTests + Testcontainers migration boot + JaCoCo gate), `bunx nx run-many -t lint` + `-t test` green, `e2e/run.sh web` green, and the removal grep gate (§A3) returns **empty**

**Checkpoint**: All four stories complete. The repository has no Capacitor, no `mobile` module, no Android CI, and every journey of the mapping table works through the web app (SC-002/SC-003).

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Docs, runbook and the final validation — the repo must describe the world it is now in

- [X] T051 [P] Update the three `AGENTS.md` files: root (drop the `mobile` module row from the Modules table, the `mobile-release-ci`/`jordylab-mobile` clients from the Keycloak paragraph, and replace the mobile-app architecture bullet with the PWA description), `jordylab-be/AGENTS.md` (delete the Mobile module section, the `mobile` schema-ownership row, the mobile endpoint gates; document `NtfyClient` in `shared`), `jordylab-fe/AGENTS.md` (rewrite the "Mobile app" section as the PWA section — manifest, `sw.js`, share target, install surfaces; drop the e2e "Android layer" paragraph)
- [X] T052 [P] Update `docs/runbook.md`: §20 loses "APK publish credentials" and gains the "Removing the Android app stack" cutover subsection (research D10's five steps + verification); §21's fingerprint manual test goes; refresh the Contents
- [X] T053 [P] Update `deploy/keycloak/README.md` (remove the mobile client/role creation commands + changelog row) and `docs/testing/manual-test-runbook.md` (MRB-06 becomes the phone PWA share-target check; MRB-07 wording now expects the tap to open the web route)
- [X] T054 Run the full [quickstart.md](quickstart.md) validation (§A unit + grep gate, §B `e2e/run.sh web`) and record the results in this spec; hand §C (owner phone checks) and §D (production cutover) to the owner
- [X] T055 No follow-ups materialised — the existing 016 icons work as maskable and the two legacy screen mappings cover every notification that exists; no Linear ticket needed

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: no dependencies — start here
- **Phase 2 (Foundational shell)**: after Phase 1; blocks US1 and US2. **US3 can run in parallel with Phase 2–4** — it is backend-only and touches no file the other stories touch
- **US1 → US2**: sequential — both edit `manifest.webmanifest` and `pwa.spec.ts` (share_target + share journeys land only with the `/share` route)
- **US4**: after US1, US2 and US3 — it deletes what those stories replaced (the share plugin, the install surfaces' old versions, the old ntfy home)
- **Phase 7 (Polish)**: after US4

### User Story Dependencies

- **US1 (P1)**: needs Phase 2 only
- **US2 (P1)**: needs Phase 2 + the US1 manifest edit (same file)
- **US3 (P2)**: needs Phase 1 only — fully independent of the frontend stories
- **US4 (P2)**: needs US1 + US2 + US3 complete (removal-inventory order keeps prod green at every commit)

### Within Each User Story

- Spec tasks first (they fail against current code), then implementation, then the story's verification task
- Backend tasks in US3: properties/client before callers before config/deploy edits
- US4 runs its four sub-groups (backend, frontend, realm/harness/CI, deploy) in the listed order; within a sub-group the [P] tasks are parallel

### Parallel Opportunities

- Phase 2: T003 ∥ T004 ∥ T006 (three different files)
- US1: T008 ∥ T009 ∥ T010 (three spec files), then T011–T015 mostly sequential (same files as their specs are done in pair order)
- US2: T017 ∥ T018, then T022 ∥ T023 while T019–T021 land
- US3: T025 ∥ T026, then T027 → T028/T029 (parallel, different modules) → T030 → T031 ∥ T032
- US4: T033–T037 (five independent backend files), T038–T043 (six parallel frontend clusters), T045 ∥ T046 ∥ T047, T048 ∥ T049
- **Cross-story**: US3 (backend) can proceed entirely in parallel with Phase 2 → US2 (frontend)

---

## Parallel Example: User Story 4 (frontend cluster)

```text
Task: "Delete the native-only platform-api files + specs (T040)"
Task: "Delete update banner/required components + shell cleanup (T041)"
Task: "Remove the native auth machinery in libs/shared/auth (T042)"
Task: "Remove the fingerprint section from app-settings (T043)"
→ all different files; converge on T044 (PlatformService + isNative grep)
```

---

## Implementation Strategy

### MVP First (Phase 2 + US1)

1. Phase 1 (baseline + gate) → Phase 2 (installable shell) → US1 (install surfaces)
2. **STOP and VALIDATE**: manifest + SW e2e journeys green; owner installs on the phone (quickstart C1)
3. This alone is a deployable increment: the app installs from the browser while the APK stack still exists

### Incremental Delivery

1. Setup + shell → US1 → validate (MVP)
2. US2 → share journeys green → deploy/demo
3. US3 (can slot in any time after Setup) → notifications verified
4. US4 → the native stack is gone; full grep gate + suites green
5. Phase 7 → docs/runbook describe the new world; owner runs the phone checklist and the production cutover

---

## Notes

- Every commit: `JOR-38: <summary>` + `Refs: 018` (or `Refs: 018/T###`); never `--no-verify`
- The dev stack (Podman compose) stays read-only throughout; only `e2e/run.sh` stacks are created/removed
- The production cutover (runbook §20, research D10) is executed by the owner after the release that contains US4 deploys — the agent may run those steps only with the owner's explicit approval (jordylab-ops rules)
