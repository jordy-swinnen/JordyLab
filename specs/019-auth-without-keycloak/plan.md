# Implementation Plan: Authentication Inside the Backend Without Keycloak

**Branch**: `019-auth-without-keycloak` | **Date**: 2026-11-02, revised 2026-11-03 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/019-auth-without-keycloak/spec.md`

## Summary

Replace Keycloak with native authentication: a new `identity` Spring Modulith module (own `identity` schema) owns `UserAccount` (stored status `PENDING/APPROVED/REJECTED/REVOKED`, role `admin/guest`, per-app grants, BCrypt hash, lockout counters), passkey credentials (Spring Security WebAuthn + JDBC), `PasswordResetRequest` (hash-only, admin-approved), `MachineToken` (`jlmt_` + 256-bit random, SHA-256, `INGEST` scope) and `UserDeleted`/`UserAccessRemoved`/`AppAccessRemoved` events. Spring Security 7 runs two filter chains: a stateless bearer-token chain for ingest, and a session chain (Spring Session JDBC, 30-day idle, CSRF via Angular's `XSRF-TOKEN`/`X-XSRF-TOKEN` names) carrying today's URL matrix — including 014's `admin`/`guest`/`app-gamecatalog` model — plus Spring Security's WebAuthn ceremony endpoints for passkey sign-in with fingerprint/face. All modules resolve the current user through `CurrentUserResolver`; migrated users keep their Keycloak `sub` as the id and their `app-gamecatalog` membership as a grant, so no owner column or FK changes. The frontend drops `keycloak-js` for a session `AuthService`, real login/register/forgot forms, a passkey sign-in action and a Settings → App Passkeys card; the settings Users page keeps 014's grant toggles, session panel and deletion, now backed by `/api/identity/**`. **Two releases**: A = cutover (SQL migration copies users+grants from the `keycloak` schema, passwordless until each user's first approved reset; Keycloak idle), then verified backup; B = removal (drop schema, delete pod/route/config/secrets/CI/docs). **Built on 018** (PWA merged — same-origin app, no Capacitor). Design decisions: [research.md](research.md) (D16 passkeys, D17 014 parity).

## Technical Context

**Language/Version**: Java 25 (backend), TypeScript / Angular 22 (frontend), Python 3.12 (frozen scan client)

**Primary Dependencies**: Spring Boot 4.1.1, Spring Security 7.1.1 (`spring-boot-starter-security` present; `spring-boot-starter-oauth2-resource-server` removed; `spring-security-crypto` for BCrypt), **`spring-security-webauthn` 7.1.1 (new, BOM-managed — first build downloads it)**, Spring Session JDBC 4.1.1 (new, BOM-managed), Spring Modulith 2.1.1, Flyway; Angular signals, spartan/ui; `keycloak-js` removed

**Storage**: PostgreSQL 16 — new `identity` schema (`user_account`, `user_app_grant`, `user_credentials`, `password_reset_request`, `machine_token`, Spring Session tables) via one Flyway migration; cutover migration reads the existing `keycloak` schema (persons only, id = `sub`, grants = `app-gamecatalog`); release-B migration drops `keycloak`. `settings.guest_chat_*` unchanged; cleanup becomes event-driven.

**Testing**: JUnit 5 + AssertJ (`assertSoftly`) + Mockito (never `any()`) + Testcontainers (**Postgres only** — the Keycloak Testcontainers machinery is deleted) + MockMvc; `EqualsVerifier`; Vitest + Spectator; Playwright e2e rewritten onto the Postgres-only stack, with a **CDP virtual authenticator** for the passkey journey

**Target Platform**: `jordylab-be` monolith + `jordylab-fe` PWA (spec 018) + production k3s (spec 017 Compose is still a draft; if it lands first, the removal checklist targets its equivalent pieces)

**Project Type**: web application (Spring Boot backend + Nx Angular frontend)

**Performance Goals**: sign-in ~1s (BCrypt strength 10 dominates); passkey sign-in bounded by the device prompt; machine-token check is one indexed SHA-256 lookup with a ≤1/min throttled `last_used_at`; no measurable latency change on existing endpoints

**Constraints**: sessions survive restarts and rolling deploys (SC-003); revoke/delete ends sessions immediately (SC-005); zero Keycloak references outside docs history after release B (SC-008); no email anywhere; secret values (bootstrap password, machine tokens, temporary passwords, password hashes) never logged or shown twice; WebAuthn requires HTTPS or localhost

**Scale/Scope**: a handful of users, 2–3 scan machines, dozens of sessions; every module's controller layer is touched lightly (Jwt → `CurrentUserResolver` swap); `settings` loses its Keycloak admin stack but keeps guest limits; no schema outside `identity` changes

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | How the design complies | Status |
|-----------|------------------------|--------|
| I. Clean Code | The `identity` module follows the canonical package layout (`domain/`+`repository/`, `rest/controller/`+`model/`, `service/`, `util/`, root public API). Full descriptive names (`UserAccount`, `PasswordResetRequest`, `MachineTokenAuthenticationFilter`, `CurrentUserResolver`, `IdentityUserDetails`); early returns; the Keycloak admin REST client disappears entirely. | Pass |
| II. Fail fast, no silent failures | Unknown/revoked machine tokens fail fast 401; lockout after 5 failures; passkey ceremonies for rejected/revoked accounts are disabled; the cutover migration no-ops loudly (`RAISE NOTICE`) on databases without the `keycloak` schema; bootstrap config is ignored with a log line when users exist; ntfy failures are logged and swallowed (parity with today). | Pass |
| III. Immutable, builder-first | Entities follow the canonical structure (`@Builder`, guards only in `build()`, named mutations `approve/reject/revoke/grantApp/revokeApp/applyPassword/recordFailedSignIn`, `registerEvent`); `AuthenticatedUser`, `ManagedApp`, all DTOs and the three events are records/enums; token and temporary-password values exist only in the one-time response. | Pass |
| IV. Testing discipline | Canonical entity tests + `EqualsVerifier`; TestBuilders per fixture; `@WebMvcTest` slices import the real security chains; `@ApplicationModuleTest`/`@SpringBootTest` with Testcontainers Postgres; rewritten `RoleMatrixTest` without Keycloak; the real WebAuthn ceremony is covered in Playwright with a virtual authenticator (no fake ceremony in Java); explicit Mockito values/captors; Vitest + Spectator with `useValue` + `vi.fn()` and real `signal(...)`. | Pass |
| V. Language & tooling currency | Java 25 without `var`; Angular 22 with `inject()` and `#field`; Spring Security WebAuthn and Spring Session JDBC adopted at the Boot-managed 7.1.1/4.1.1 — no pinned legacy versions. | Pass |
| Architecture constraints | New signal stores in the domain `api` lib (Machines, Passkeys in shared-auth or settings-api); Container–Presentation for the new pages; all modules talk to `identity` only through its root-package API; `ModularityTests` keeps verifying the edges; events are the only cross-module coupling (`UserAccessRemoved`, `AppAccessRemoved`, `UserDeleted`). | Pass |
| Project rules (AGENTS.md) | Flyway migrations start `CREATE SCHEMA IF NOT EXISTS identity; SET search_path TO identity;`; applied migrations never modified; the cutover migration is product code (the sanctioned exception to "never hand-seed" — validation uses real flows only); AI routing untouched; dev-database backup precedes risky local runs ([quickstart.md](quickstart.md)). | Pass |

**Post-design re-check (after Phase 1)**: still Pass. The design adds one module and two dependencies (Spring Session JDBC, Spring Security WebAuthn), both mandated by the spec (FR-007, FR-014–FR-016) — see Complexity Tracking.

## Project Structure

### Documentation (this feature)

```text
specs/019-auth-without-keycloak/
├── spec.md                     # /speckit-specify output (passwords + passkeys, no links)
├── plan.md                     # this file
├── research.md                 # Phase 0: decisions D1–D17 with alternatives
├── data-model.md               # Phase 1: identity schema, grants, passkey credentials, cutover
├── quickstart.md               # Phase 1: end-to-end validation guide (SC-001…SC-008)
├── contracts/
│   ├── identity-api.md         # REST contract (auth, passkeys, users, machines, CSRF, scanner)
│   └── access-matrix.md        # two chains, URL rules, roles/grants/scopes, module boundary
├── checklists/
│   └── requirements.md
└── tasks.md                    # Phase 2 output (/speckit-tasks — NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
jordylab-be/src/main/java/dev/jordy/jordylab/
├── identity/                                   # NEW module
│   ├── AuthenticatedUser.java                  # public record (id, username, role, appGrants)
│   ├── CurrentUserResolver.java                # public API: Optional<AuthenticatedUser> currentUser()
│   ├── ManagedApp.java                         # public enum (GAME_CATALOG grantable; FNA/SETTINGS not)
│   ├── UserAccessRemoved.java / AppAccessRemoved.java / UserDeleted.java   # moved from shared.event
│   ├── IdentityConfiguration.java              # properties + PasswordEncoder bean
│   ├── IdentitySecurityConfiguration.java      # two chains + spring security webAuthn(...) (replaces shared SecurityConfig)
│   ├── IdentityProperties.java                 # password policy, lockout, bootstrap, webauthn (rp id/name/origins)
│   ├── domain/
│   │   ├── UserAccount.java                    # status machine, role, @ElementCollection grants, lockout
│   │   ├── PasswordResetRequest.java
│   │   ├── MachineToken.java
│   │   └── repository/                         # UserAccountRepository, PasswordResetRequestRepository,
│   │                                           # MachineTokenRepository (findByTokenHash)
│   ├── rest/controller/
│   │   ├── AuthenticationController.java       # register/login/logout/me/csrf/change-password/display-name/reset-requests
│   │   ├── UserController.java                 # admin user mgmt: approve/reject/revoke/grants/sessions/delete/resets
│   │   ├── MachineTokenController.java
│   │   ├── PasskeyController.java              # list/delete own credentials
│   │   ├── MachineTokenAuthenticationFilter.java
│   │   ├── IdentityUserDetailsService.java     # UserDetails + WebAuthnUserDetails adapter
│   │   ├── TestSignInController.java           # e2e profile ONLY (@Profile("e2e"))
│   │   ├── IdentityExceptionHandler.java
│   │   └── model/                              # request/response records
│   ├── service/
│   │   ├── SessionSignInService.java           # authenticate, lockout, fixation, status checks
│   │   ├── UserAccountService.java             # register/approve/reject/revoke/grants, last-admin guard, events, ntfy
│   │   ├── UserDeletionService.java            # staged delete (parity with 014), events, cleanup
│   │   ├── UserSessionService.java             # list/end a user's web sessions (Spring Session)
│   │   ├── PasswordResetService.java
│   │   ├── MachineTokenService.java
│   │   └── BootstrapAdminInitializer.java      # first admin only when the table is empty
│   └── util/
│       └── TemporaryPasswordGenerator.java     # moved from settings/util
├── gamecatalog/                                # Jwt → CurrentUserResolver: GameCatalogController,
│   │                                           # MarkController, RefreshRunController, LibBotController;
│   │                                           # MarkCleanupListener imports identity events
├── settings/                                   # SettingsAiModelsController, GuestChatQuotaController,
│   │                                           # GuestChatLimitFilter → resolver; GuestChatLimit* STAYS;
│   │                                           # new GuestDataCleanupListener (UserAccessRemoved/UserDeleted);
│   │                                           # DELETED: KeycloakAdminClient, KeycloakAdminProperties,
│   │                                           # KeycloakUnavailableException, KeycloakUserAdministrationService,
│   │                                           # UserDeletionService (moves), UserSessionService (moves),
│   │                                           # PendingSignupWatcherService, AppUser/UserStatus, SettingsUsersController
├── shared/
│   ├── ai/AiTracing.java                       # userId from CurrentUserResolver (stable id)
│   ├── event/                                  # DELETED (events move to identity)
│   └── config/                                 # DELETED: SecurityConfig.java; package-info updated
└── (fna)                                       # untouched

jordylab-be/src/main/resources/
├── application.yaml                            # − issuer-uri, − jordylab.settings.keycloak; + spring.session (jdbc),
│                                               # + server.servlet.session timeout/cookie, + jordylab.identity.*
├── application-local.yaml / application-prod.yaml  # identity bootstrap + webauthn rp values; Keycloak entries removed
├── db/migration/
│   ├── V<date>01__identity_create_tables.sql       # schema + user_account, user_app_grant, user_credentials,
│   │                                               # password_reset_request, machine_token, Spring Session DDL
│   ├── V<date>02__identity_migrate_keycloak_users.sql  # release A: guarded copy (users + grants)
│   └── V<date>03__drop_keycloak_schema.sql         # release B (after the verified backup)
└── scripts/jordylab-scan-template.py           # regenerated: BACKEND_URL + token, no device flow

jordylab-be/src/test/java/dev/jordy/jordylab/
├── identity/                                   # entity tests + EqualsVerifier, TestBuilders, @WebMvcTest slices
│   │                                           # with the real chains, IdentityModuleTest, IdentityRoleMatrixTest
│   │                                           # (replaces settings RoleMatrixTest), IdentityUserManagementIntegrationTest
│   │                                           # (replaces settings UserManagementIntegrationTest), passkey adapter/
│   │                                           # options/management tests, SessionPersistenceIntegrationTest,
│   │                                           # CsrfIntegrationTest, KeycloakUserMigrationTest,
│   │                                           # ProdTestSignInAbsenceTest, service tests
└── (updated)                                   # gamecatalog/settings controller tests: Jwt stubs → resolver stubs;
                                                # DELETED: KeycloakIntegrationTest stack, KeycloakAdminClient tests,
                                                # KeycloakSessionVerificationIntegrationTest, RealmConfigurationTest,
                                                # ProdKeycloakUrlConfigurationTest, KeycloakUserAdministrationServiceTest,
                                                # PendingSignupWatcherServiceTest, SettingsUsersControllerTest

jordylab-fe/
├── package.json                                # keycloak-js removed
├── libs/shared/auth/src/lib/
│   ├── auth.service.ts (+spec)                 # session rewrite: /me, login, logout, changePassword,
│   │                                           # updateDisplayName; roles + appGrants signals
│   ├── webauthn.ts (+spec)                     # base64url helpers + create/get ceremony calls
│   ├── auth-config.ts                          # simplified — no keycloak fields
│   ├── auth.guard.ts / role.guard.ts           # semantics unchanged (hasGameCatalogAccess preserved)
│   ├── login.component.* (+spec)               # username/password form + "Sign in with a passkey"
│   ├── register.component.* (+spec)            # /register (no email)
│   ├── forgot-password.component.* (+spec)     # /forgot-password
│   ├── user-menu.component.* (+spec)           # change password + edit display name dialogs
│   └── (deleted)                               # auth.interceptor.ts, pkce.ts
├── libs/settings/api/src/lib/                  # users api → /api/identity/users (+grants, sessions, delete,
│   │                                           # reset-request decisions); machines api + store; passkeys handled
│   │                                           # in shared-auth
├── libs/settings/ui/src/lib/                   # users page: grant toggles, session panel, delete, reset panels;
│   │                                           # machines page (new, Container–Presentation)
├── apps/jordylab/                              # routes +/register +/forgot-password; Passkeys card in
│   │                                           # settings/app; environments cleaned (no keycloak*);
│   │                                           # (deleted) silent-check-sso.*, pre-bootstrap-auth.ts
├── apps/fna/, apps/gamecatalog/                # environments + main.ts pre-bootstrap cleanup
├── e2e/                                        # compose.e2e.yaml Postgres-only; run.sh (no Keycloak env,
│   │                                           # E2E_ADMIN_PASSWORD, SPRING_PROFILES_ACTIVE=local,e2e);
│   │                                           # (deleted) make-realm.py
└── apps/jordylab-e2e/                          # session.ts (real-form sign-in), guest.ts (real register),
                                                # passkeys.spec.ts (CDP virtual authenticator), global-setup.ts
                                                # (machine token for catalog), fixtures

deploy/
├── containers/keycloak/                        # DELETED (release B)
├── k8s/base/                                   # keycloak.yaml DELETED; gateway.yaml − keycloak-route;
│                                               # backend.yaml − KEYCLOAK_* env, + IDENTITY_BOOTSTRAP_*;
│                                               # kustomization updated
├── k8s/overlays/prod/                          # − keycloak-config; backend-config − KEYCLOAK_*;
│   │                                           # secrets.sops.yaml re-keyed: + IDENTITY_BOOTSTRAP_ADMIN_*,
│   │                                           # − KEYCLOAK_*/— (SOPS edit by the owner)
└── k8s/cluster/cnpg-cluster.yaml               # − keycloak role/schema/managed secret

jordylab-be/compose/                            # keycloak service, keycloak-init.sql, keycloak-realm-export.json,
                                                # keycloak-theme/ DELETED; .env.example cleaned

.github/workflows/                              # build.yml + release.yml matrices − keycloak;
                                                # deploy-prod.yml − keycloak image/rollout; e2e-web unchanged

docs/ + AGENTS files                            # runbook (break-glass, §11/§13/§15, APK-stack section),
                                                # environments.md, learn/*, README.md, root/be/fe AGENTS.md —
                                                # rewritten to the new reality; docs/testing/ history untouched
```

**Structure Decision**: Web application using the existing `jordylab-be` monolith (new top-level `identity` module — the spec-mandated extraction unit) and `jordylab-fe` (auth flows rewritten in `libs/shared/auth`, admin pages kept in `libs/settings`). Release-A code and release-B deletions live in one branch; `/speckit-tasks` sequences them so the `keycloak` schema drop and the deploy/CI/docs removal cannot land before the cutover is verified and the backup taken.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| New top-level module (`identity`) + two new dependencies (Spring Session JDBC, Spring Security WebAuthn) | All are explicit spec requirements: FR-009 mandates a separate extraction-ready identity module with its own schema; FR-007 mandates server-side sessions that survive deploys (impossible without a persistent store); FR-014–FR-016 mandate passkey sign-in (the first-party WebAuthn module provides the ceremony and credential storage) | Folding identity into `settings` with in-memory sessions and no passkeys: less code, but breaks the extraction story, logs everyone out on deploys, and drops the owner's recorded fingerprint expectation |

No other violations — the access matrix, authority names, owner columns, guest limits, and event flows carry over unchanged.
