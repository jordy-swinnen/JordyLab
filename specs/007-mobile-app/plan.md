# Implementation Plan: Mobile App (Android via Capacitor, iOS Home-Screen Web App)

**Branch**: `007-mobile-app` | **Date**: 2026-09-28 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/007-mobile-app/spec.md`; tech context from
`specs/_drafts/mobile-app/speckit-prompts.md` §3, verified against the live codebase and vendor docs in
[research.md](research.md) (decisions D1–D14).

## Summary

A new Android app (`apps/jordylab-mobile`, Capacitor 8) that bundles the existing `jordylab` Angular UI, distributed as
a sideloaded, CI-signed APK to approved users only — no Play Store. A small new backend module, **`mobile`**, publishes
signed releases, issues short-lived signed download links, serves `/.well-known/assetlinks.json` for Android App Link
verification, and turns two existing business events (a pending sign-up, a ready FNA briefing) into Ntfy push
notifications with a deep link back into the app. Login reuses the existing Keycloak realm through a new public client
(`jordylab-mobile`) and a custom `keycloak-js` adapter that opens the system browser and returns via an Android App
Link, requesting `offline_access` so biometric unlock can work without a password most days. The website gains
platform-aware, one-time install prompts (Android APK dialog, iOS Add-to-Home-Screen sheet, desktop QR code) and a
first web app manifest for iOS home-screen support — none of this exists in the frontend today. Share-to-JordyLab and
"Save to FNA" turn out to need almost no new backend surface: the share target is a native Android feature calling
existing/new `fna` and `gamecatalog` endpoints directly, not a `mobile`-module concern.

Two cross-feature touch-points on already-planned 006 code are called out explicitly (research D9, D12): `settings`'
sign-up detection is adjusted to publish a Modulith event instead of calling Ntfy directly (006's Ntfy sender was never
actually built — only config scaffolding exists), and `settings`' user-revoke flow gains an offline-consent
revocation call so a revoked guest's biometric-unlock token stops working immediately, not just their regular session.

## Technical Context

**Language/Version**: Java 25 (backend) · TypeScript + Angular 21, zoneless (frontend, host app + new native shell) ·
Kotlin/Gradle (generated Android project, untouched beyond `variables.gradle`) · Bun + Nx 22.5.4

**Primary Dependencies**: Spring Boot 4.0.3, Spring Modulith 2.0.3 (first real cross-module application events — D9),
Spring Security (OAuth2 resource server), Flyway, Lombok · Capacitor **8.5.1** (D1) — `@capacitor/android`,
`@capacitor/browser`, `@capacitor/app` · `@capgo/capacitor-native-biometric` ^8 (D3) · `@capgo/capacitor-share-target`
^8 (D4) · `keycloak-js` 26.2.4 (existing — extended with a custom `KeycloakAdapter`, D2) · spartan/ui + Night Lab
tokens · Ntfy (existing instance, first real integration — D9/D10) · test: Vitest + Spectator, JUnit 5 / AssertJ /
Mockito / Testcontainers / MockMvc (existing stack, no new test frameworks)

**Storage**: PostgreSQL 16 — new `mobile` schema, one table (`MobileRelease`, [data-model.md](data-model.md)); signed
APK files on a filesystem volume (`jordylab.mobile.release.storage-dir`), mirroring gamecatalog's artwork-dir pattern
(D7) — no object storage, no DB blob

**Testing**: JUnit 5 / AssertJ (`assertSoftly`) / Mockito / MockMvc (`@Language("JSON")`) for release/download-link
endpoints and role gating; Vitest + Spectator for the platform-detection, URL-interceptor, install-prompt and
update-check logic; a manual device-test checklist ([quickstart.md](quickstart.md)) for anything that needs a real
phone (install, biometric, share, App Link callback) — none of that is mockable in CI

**Target Platform**: Android 10+ (API 29+, spec FR-018) sideloaded APK; iOS/iPadOS Safari home-screen web app (no
native build); existing Podman-compose dev stack; production is a public HTTPS domain delivered by **feature 008,
not yet specified** — see Assumptions

**Project Type**: web application (existing modular monolith + Nx SPA) **plus** a new native Android shell around the
same SPA — no second UI codebase

**Performance Goals**: install-to-logged-in-app in the app in under 3 minutes on a fresh phone (spec SC-001) ·
update-prompt reaches an installed device with zero manual steps beyond publishing (SC-004) · download-link issuance
and validation are single indexed lookups, no measurable latency budget beyond normal API response times

**Constraints**: no Play Store, F-Droid, or native iOS distribution (spec, out of scope) · one release signing key,
created once, never rotated (FR-003) — **stop-and-report gate** before it is generated · download links expire in
minutes and are single-purpose (FR-002) · credentials never pass through app code — login is system-browser-only
(FR-010) · biometric session lives only in hardware-backed secure storage (FR-012) · Android 10+ only (FR-018) · the
production domain and final Android application id are **not yet decided** — both are **stop-and-report gates**
(spec depends on feature 008)

**Scale/Scope**: 1 new backend module (`mobile`, 1 entity, ~4 endpoints + 1 well-known endpoint + 2 event listeners) ·
2 new Modulith events on existing modules (`settings`, `fna`) · 1 new Nx app (`apps/jordylab-mobile`) · 1 new shared
frontend lib (`libs/shared/platform`) · 1 new Keycloak client + 1 new realm role · 1 new CI workflow · single-digit
friend-group scale throughout (same as 006) — no load/scale design needed

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| #   | Principle (constitution)                              | How the design satisfies it                                                                                                                                                                                          | Status |
|-----|--------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------|
| I   | Clean Code (SOLID/KISS/YAGNI/DRY)                       | The `mobile` module does one job (releases + download links + assetlinks + notification fan-out); share-to-JordyLab is kept OUT of `mobile` and wired as plain new endpoints on the modules that already own that data (D6) — no speculative broker layer | PASS |
| II  | Fail Fast, No Silent Failures                           | Expired/tampered download token → explicit `403 DOWNLOAD_LINK_INVALID`, never a silent stream failure; signing-cert mismatch fails the CI publish call outright (`400 SIGNING_CERT_MISMATCH`, D7); Ntfy failures are logged and swallowed only for the notification itself (a push failure must never block the sign-up/briefing flow that triggered it) — documented explicitly, not accidental | PASS |
| III | Immutable, Builder-First                                | `MobileRelease` follows the canonical entity shape (builder + `Preconditions` in `build()`, `BaseEntity`, UUID id) — see [data-model.md](data-model.md) | PASS |
| IV  | Testing Discipline                                      | MockMvc + `@Language("JSON")` for the release/download endpoints and role matrix; TestBuilder fixture for `MobileRelease`; Vitest/Spectator (`useValue` + `vi.fn` + real signals) for platform-detection/interceptor/install-prompt/update-check; native-only behavior (biometric, share, real App Link) is explicitly out of automated-test reach — device checklist instead, not skipped silently | PASS |
| V   | Language & Tooling Currency                             | Java 25 / Angular 21 / Nx 22 / signals unchanged; Capacitor 8.5.1 is current stable, not the v9 pre-release (D1, verified live) | PASS |
| —   | Module boundaries (root AGENTS + Modulith)              | `mobile` depends on nothing but `shared`; `settings`→`mobile` and `fna`→`mobile` edges are avoided by using Modulith application events instead of direct calls (D9) — the first real use of the event infrastructure that 006 already found "exists but is unused"; `ModularityTests` must stay green with the new module + new event types | PASS |
| —   | Container–Presentation / signal store in api lib (FE)   | New `libs/shared/platform/{api,ui}` follows the same api/ui split as every domain lib; install-prompt state is a signal store, not component-local state | PASS |

**Post-Phase-1 re-check (after data-model, contracts, quickstart): unchanged — PASS.** No complexity-tracking entries
are needed. The two touch-points on 006's already-planned code (D9 event instead of direct Ntfy call, D12 offline-consent
revocation) are refactors of not-yet-built behavior, not new complexity — see Complexity Tracking for why they were not
avoided instead.

## Project Structure

### Documentation (this feature)

```text
specs/007-mobile-app/
├── plan.md                       # this file
├── research.md                    # Phase 0 — verified facts + decisions D1–D14
├── data-model.md                  # Phase 1 — MobileRelease, DownloadLink (signed, not persisted)
├── quickstart.md                  # Phase 1 — device + automated validation guide
├── contracts/
│   ├── access-matrix.md           # route→role matrix for /api/mobile/** and the well-known endpoint
│   ├── mobile-releases-api.md     # release publish / latest / download-link / download REST contract
│   └── app-shell-contract.md      # frontend: base-URL interceptor, artwork URL helper, install prompts,
│                                  #   manifest, App Link → in-app screen routing table
└── tasks.md                       # Phase 2 — /speckit-tasks (not created by /speckit-plan)
```

### Source Code (repository root)

Web application: modular-monolith backend + Nx SPA frontend, plus a new native Android shell wrapping the same SPA.
New code concentrated in a new backend module and two new frontend libs/apps; small, explicit touch-points on
`settings`, `fna`, the realm export, and the app shell's routing/nav.

```text
jordylab-be/src/main/java/dev/jordy/jordylab/
├── mobile/                                    # NEW module
│   ├── MobileProperties.java                  # jordylab.mobile.* (release storage dir, download-link secret/ttl,
│   │                                          #   min-supported-version-code default, package name + cert
│   │                                          #   fingerprint for assetlinks.json, ntfy base-url/topic/token — D10)
│   ├── domain/                                 # MobileRelease + repository/
│   ├── rest/
│   │   ├── client/NtfyClient.java             # RestClient-based Ntfy publisher (title, body, click URL) — D9/D10
│   │   └── controller/                        # MobileReleaseController (latest, download-link, publish — CI role),
│   │                                          #   MobileDownloadController (public, token-validated streaming),
│   │                                          #   AssetLinksController (GET /.well-known/assetlinks.json), model/
│   ├── service/                                # MobileReleaseService, DownloadLinkService (HMAC issue/verify),
│   │                                          #   MobileNotificationListener (@ApplicationModuleListener ×2 — D9)
│   └── util/
├── settings/service/…                          # TOUCH — pending-signup detection publishes UserSignUpPending
│                                              #   (Modulith event) instead of calling Ntfy directly (D9); revoke()
│                                              #   gains an offline-consent revocation call (D12)
├── fna/service/BriefingGeneratorService.java   # TOUCH — publishes BriefingReady (Modulith event) on completion (D9)
│                                               #   plus a NEW manual-article endpoint for Save-to-FNA (D6)
├── gamecatalog/…                               # no backend change — chat prefill is a frontend-only share behavior
└── shared/config/SecurityConfig.java           # + /api/mobile/** matchers, + /.well-known/assetlinks.json permitAll

jordylab-be/src/main/resources/
├── application.yaml                            # jordylab.mobile.* config (D10 relocates the unused
│                                              #   jordylab.settings.notifications.ntfy.* scaffold here)
└── db/migration/V<yyyyMMdd>__mobile_create_tables.sql   # NEW — mobile schema, mobile_release table

jordylab-be/…
├── compose/keycloak-realm-export.json          # STOP-AND-REPORT GATE (D13): new public client `jordylab-mobile`
│                                              #   (PKCE S256, redirect URI = App Link callback, offline_access
│                                              #   allowed), new confidential client `mobile-release-ci`
│                                              #   (service account), new realm role `mobile-release-publisher`
└── src/test/…                                  # MobileModuleTest (MockMvc, role matrix, token validation),
                                              #   ModularityTests extended for the new module + event types

jordylab-fe/
├── apps/jordylab-mobile/                       # NEW Nx app — capacitor.config.ts + android/ (committed;
│                                              #   android/app/build/ and .gradle caches gitignored)
├── apps/jordylab/project.json                  # + `mobile` build configuration (environment.mobile.ts: absolute
│                                              #   apiBaseUrl, keycloakClientId=jordylab-mobile)
├── libs/shared/platform/{api,ui}/              # NEW — platform detection (native/Android/iOS/desktop), the
│                                              #   apiBaseUrl interceptor + artwork-URL helper, install-prompt
│                                              #   signal store + dismissal storage, update-check service/store,
│                                              #   App-Link → route mapping, install dialog/sheet/QR components
├── libs/shared/auth/                           # + custom KeycloakAdapter (system browser + App Link return,
│                                              #   `offline_access` scope — D2), biometric-unlock wiring
├── apps/jordylab/                               # web app manifest + icons + apple-touch-icon (iOS home screen,
│                                              #   FR-008); install-prompt mount point; user-menu "Get the Android
│                                              #   app" (QR on desktop, download link on Android outside the app)
├── libs/gamecatalog/ui/                        # + share-target landing (chat prefilled from shared text)
├── libs/fna/ui or api/                         # + Save-to-FNA share destination calling the new manual-article
│                                              #   endpoint
└── eslint.config.mjs / tsconfig.base.json      # + scope:platform, @jordylab-fe/shared/platform/* paths

.github/workflows/android-release.yml           # (retired 2026-10-01 → release.yml, spec 011 BUG-008) NEW — first build/release workflow in the repo (no existing one to
                                              #   mirror beyond naming/permissions style); triggers on tag `mobile-v*`
```

**Structure Decision**: web application (backend + frontend) on the existing modular monolith, plus one additional
native shell app in the same Nx workspace (no separate repo, no separate UI codebase). The `mobile` backend module
owns only releases, download links, and the notification fan-out; everything share-target-related is deliberately
placed on the modules that already own the relevant data (`fna`, `gamecatalog`) rather than routed through `mobile`,
keeping `mobile`'s dependency surface at just `shared` (see research D6/D9 for why).

## Complexity Tracking

> Fill ONLY if Constitution Check has violations that must be justified

No constitution violations. Two items look like they touch other features' already-planned code and are recorded here
for visibility, not as violations:

| Touch-point                                                        | Why needed                                                                                                                                 | Simpler alternative rejected because                                                                                                   |
|----------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------|
| `settings`' pending-signup poller: direct Ntfy call → Modulith event | `mobile` needs to add a click-through App Link to the same notification; two independent Ntfy senders for one event would double-push admin | Duplicating a second, richer Ntfy call from `mobile` alongside 006's plain one (rejected: two notifications for one sign-up, confusing and wasteful) |
| `settings`' `revoke()`: + offline-consent revocation call            | FR-013 requires revoking a user to kill their biometric-unlock (offline) session, not just their regular one — confirmed via Keycloak docs that regular logout does **not** revoke offline tokens | Leaving it as-is (rejected: directly violates FR-013 — a revoked guest could keep using the app via biometric unlock indefinitely)    |
