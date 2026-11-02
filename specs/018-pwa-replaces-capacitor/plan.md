# Implementation Plan: Replace the Native Android Wrapper with an Installable Web App

**Branch**: `jordyswinnen/jor-38-replace-the-capacitor-app-with-a-pwa` | **Date**: 2026-11-02 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/018-pwa-replaces-capacitor/spec.md`

## Summary

The Android app is the same web build wrapped by Capacitor 8 and sideloaded as a signed APK. This feature makes the `jordylab` web app itself installable (web app manifest with a Web Share Target, a minimal installability-only service worker, an in-app browser-install prompt), rewires the share-landing screen to read the operating system's share query parameters, moves the admin ntfy push sender from the deleted `mobile` backend module into `shared` (called directly by `settings` and `fna`), and then removes the entire native stack: the `jordylab-mobile` and `jordylab-mobile-e2e` Nx apps, all Capacitor/`@capgo` dependencies, the `mobile` backend module with its endpoints and schema, the `mobile-release-ci`/`jordylab-mobile` Keycloak clients, the APK CI job and Android e2e workflow, and the production `mobile-releases` volume.

## Technical Context

**Language/Version**: Java 25 (Spring Boot 4.1.1, Spring Modulith 2.1.1, Spring Security 7) · TypeScript with Angular 22 / Nx 23 / Bun · Python 3 for the e2e realm generator

**Primary Dependencies**: `keycloak-js` 26 (web OIDC), spartan/ui, Tailwind (frontend) · Flyway, JPA, `RestClient` (backend) · Web platform APIs: Web App Manifest, Web Share Target (`GET`), `beforeinstallprompt`, Service Worker, `navigator.share`-adjacent share sheet · ntfy v2.28 (self-hosted, kept)

**Storage**: PostgreSQL via CloudNativePG. **Removes** the `mobile` schema (one table, `mobile.mobile_release`); adds no tables. Client-side: per-device `localStorage` keys (`jordylab.install-prompt.dismissed-until` kept, `jordylab.theme` untouched).

**Testing**: Backend — JUnit 5, AssertJ (`assertSoftly`), Mockito (explicit values, assigned captors), Testcontainers Postgres, `@ApplicationModuleTest`, `ModularityTests`, JaCoCo ≥ 80 % per package. Frontend — Vitest + `@ngneat/spectator/vitest`, `useValue` + `vi.fn()` mocks, real `signal(...)` instances. End-to-end — Playwright suite `e2e-web` (`e2e/run.sh web`), extended with the manifest and share-target journeys. Owner phone checks for what only a real device proves (install, share sheet, ntfy tap).

**Target Platform**: Web browsers — Android Chrome (install + Web Share Target), iOS/iPadOS Safari (Add to Home Screen), desktop (QR entry). Production: k3s + nginx frontend image, `https://jordylab.be`. No native runtime remains.

**Performance Goals**: No regression: the service worker adds a pass-through `fetch` listener only; first paint and chunk loading unchanged. Release pipeline drops the entire Android build/sign/upload stage.

**Constraints**: The service worker MUST NOT cache `/api/**`, LibBot SSE streams, or `/auth/**` (spec FR-002). `ModularityTests` must stay green with `mobile` gone (spec FR-010). Existing ntfy delivery semantics (log-and-swallow failures, no-op when unconfigured) are preserved (FR-009). Same-origin web app is a prerequisite for the later `auth-without-keycloak` draft — no cross-origin session machinery may be reintroduced.

**Scale/Scope**: One deployable web app (`apps/jordylab`), 3 domain route libs + shared libs, 1 backend monolith. Users: owner + a handful of approved guests. Removal touches ~40 frontend files, the whole `mobile` module (23 Java files), 4 deploy manifests, 3 workflows, 2 Keycloak realm exports, and the e2e harness.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | How the design satisfies it |
|---|---|---|
| I. Clean Code Discipline | Pass | Removal-dominated change; new code is small and single-purpose (pass-through SW, query-param share landing, install store extension). No clever tricks. |
| II. Fail Fast, No Silent Failures | Pass | ntfy misconfiguration still logs at startup and every failure logs a warning without failing the triggering flow (unchanged `NtfyClient` semantics). |
| III. Immutable, Builder-First | Pass | No new JPA entities; `NotificationProperties` is a `record`. |
| IV. Testing Discipline | Pass | Vitest + Spectator for every rewritten frontend unit; JUnit/AssertJ/WireMock for the moved `NtfyClient` and its two callers; Playwright journeys for manifest + share target; `ModularityTests` re-run after the module deletion. |
| V. Language & Tooling Currency | Pass | Angular 22 signals/inject/`#field` kept in all new components; Java 25, no `var`. |
| Architecture patterns | Pass | No NgRx; install state stays in the existing signal store; Container–Presentation kept; the share landing reads route params, not a service singleton. |
| Modulith boundaries | Pass | `mobile` deleted; `NtfyClient` moves to `shared` (modules may depend on `shared`); `settings`/`fna` call it directly — no cross-module internals. |

No violations — complexity tracking not needed.

## Project Structure

### Documentation (this feature)

```text
specs/018-pwa-replaces-capacitor/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   ├── pwa-manifest-contract.md
│   ├── share-target-contract.md
│   ├── notifications-contract.md
│   └── removal-inventory.md
└── tasks.md             # Phase 2 (/speckit-tasks — not created here)
```

### Source Code (repository root)

```text
jordylab-fe/
├── apps/
│   ├── jordylab/                     # the one deployable app — becomes the PWA
│   │   ├── public/
│   │   │   ├── manifest.webmanifest  # EDITED: + id, scope, share_target, maskable icons
│   │   │   └── sw.js                 # NEW: installability-only service worker
│   │   └── src/
│   │       ├── app/
│   │       │   ├── app.ts / app.html         # EDITED: drop update/APK/app-link wiring; PWA install
│   │       │   ├── app.routes.ts             # EDITED: /share route, legacy /mobile/* redirects
│   │       │   └── app-settings/             # EDITED: fingerprint row out, install card in
│   │       └── environments/                 # environment.mobile*.ts DELETED
│   ├── jordylab-e2e/src/             # EDITED: + pwa.spec.ts (manifest + share journeys)
│   ├── jordylab-mobile/              # DELETED (capacitor.config.ts, android/, project.json)
│   └── jordylab-mobile-e2e/          # DELETED (Appium/WDIO suite)
├── libs/shared/
│   ├── platform/api/                 # REWRITTEN: install store captures beforeinstallprompt;
│   │   │                             #   apk/update-check/app-link/api-base-url DELETED;
│   │   │                             #   share-target.service reads query params (or moves into ui)
│   ├── platform/ui/                  # install-prompt REWRITTEN (PWA), QR entry kept (retitled),
│   │   │                             #   update banner/required DELETED, share-landing REWRITTEN
│   └── auth/                         # native login/biometric branches DELETED; login(redirectUrl)
│                                     #   carries returnUrl through Keycloak
└── e2e/                              # run.sh android mode DELETED; make-realm.py mobile client
                                      #   DELETED; android-setup.sh DELETED

jordylab-be/
└── src/main/java/dev/jordy/jordylab/
    ├── mobile/                       # DELETED (23 files: controllers, services, entity, NtfyClient)
    ├── shared/rest/client/NtfyClient.java          # NEW home (moved), click-path resolving
    ├── shared/config/NotificationProperties.java   # NEW (jordylab.notifications.*)
    ├── settings/                     # PendingSignupWatcherService calls NtfyClient directly;
    │                                 #   UserSignUpPending event + mobile-consent revoke DELETED
    └── fna/                          # BriefingGeneratorService calls NtfyClient directly;
                                      #   BriefingReady event DELETED
+ resources: application.yaml (jordylab.mobile.* out, jordylab.notifications.* in),
  Flyway V20261102003__mobile_drop_schema.sql, compose/keycloak-realm-export.json cleanup

deploy/
├── k8s/base/backend.yaml             # mobile-releases volume/mount DELETED
├── k8s/base/artwork-pvc.yaml         # mobile-releases PVC DELETED (artwork stays)
├── k8s/base/keycloak.yaml            # MOBILE_RELEASE_CI_CLIENT_SECRET wiring DELETED
├── k8s/overlays/prod/kustomization.yaml  # MOBILE_* literals out, JORDYLAB_APP_BASE_URL in
├── k8s/overlays/prod/secrets.sops.yaml   # MOBILE_* keys out (SOPS re-encrypt)
└── keycloak/realm-prod.json          # jordylab-mobile + mobile-release-ci + role DELETED

.github/workflows/
├── e2e-android.yml                   # DELETED
└── release.yml                       # apk job + versionCode derivation DELETED
```

**Structure Decision**: Web-application layout (existing `jordylab-fe` + `jordylab-be` + `deploy/`). No new projects are created; two Nx apps are deleted. The PWA surface lives entirely in the existing `apps/jordylab` app and `libs/shared/platform`.

## Complexity Tracking

Not applicable — no Constitution violations to justify.
