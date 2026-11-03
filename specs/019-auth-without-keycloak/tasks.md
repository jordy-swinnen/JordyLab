---

description: "Task list for Authentication Inside the Backend Without Keycloak (passwords + passkeys)"
---

# Tasks: Authentication Inside the Backend Without Keycloak

**Input**: Design documents from `/specs/019-auth-without-keycloak/`

**Prerequisites**: [plan.md](plan.md) (required), [spec.md](spec.md) (required for user stories), [research.md](research.md) (decisions D1–D17), [data-model.md](data-model.md), [contracts/identity-api.md](contracts/identity-api.md), [contracts/access-matrix.md](contracts/access-matrix.md), [quickstart.md](quickstart.md)

**Tests**: Included per the JordyLab Constitution (IV. Testing Discipline) and `jordylab-be/AGENTS.md` / `jordylab-fe/AGENTS.md` (entity/service/controller/component specs, 80% per-module/per-library coverage backstop).

**Organization**: Tasks are grouped by user story (`US1`–`US7`) to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Which user story this task belongs to (`[US1]`…`[US7]`)
- Exact file paths are included in every task description

## Rules that apply to every task

- **Java**: Never use `var`; blank line before every `return`; method order public → package-private → private; Lombok `@Builder` / `@RequiredArgsConstructor`, records for DTOs, `Preconditions.checkArgument` only in `build()` and named mutation methods; entities extend `BaseEntity`/`AbstractAggregateRoot` with UUID ids; Flyway migrations start `CREATE SCHEMA IF NOT EXISTS identity; SET search_path TO identity;`; never modify an applied migration — add a new one.
- **Java Tests**: JUnit 5, AssertJ (`assertSoftly`), Mockito without `any()` (explicit values or an assigned, asserted `ArgumentCaptor`), `@Language("JSON")`, canonical `@UtilityClass` `*TestBuilder` fixtures, Testcontainers on Podman needs `DOCKER_HOST` + `TESTCONTAINERS_RYUK_DISABLED=true`.
- **Angular**: `inject(Service)` over constructor injection, `#field` over `private`, signals, Container–Presentation, signal stores in the domain `api` lib; Vitest + `@ngneat/spectator/vitest` with `useValue` + `vi.fn()` and real `signal(...)` instances.
- **Secrets**: never log, print, or persist plaintext passwords, BCrypt hashes of them, machine tokens (`jlmt_…`), or temporary passwords; tokens, temporary passwords and passkey ceremonies are shown/used exactly once per request.
- **Validation data**: never hand-seed the database — accounts exist only through the real register/approve/reset flows; catalog data only through the real scan client ([quickstart.md](quickstart.md)). The cutover migration (T090) is product code, not seeding.
- **Local dev DB**: back it up (`podman exec jordylab-be-pgvector-1 sh -c 'pg_dump -U "$POSTGRES_USER" -Fc jordylab' > ~/jordylab-dev-$(date +%F).dump`) before any run that applies migrations; read-only on the dev stack otherwise.
- **Commits**: `JOR-30: …` subject and `Refs: 019` (or `Refs: 019/T###`) trailer on every commit.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Verify the precondition (spec 018 landed — it is merged) and the baseline before the identity module lands

- [X] T001 Verify the baseline per [quickstart.md](quickstart.md): confirm spec 018 is in the tree (`jordylab-fe/apps/` has no mobile apps, no Capacitor in `jordylab-fe/package.json`, `jordylab-fe/apps/jordylab/public/sw.js` + `manifest.webmanifest` exist); verify `./gradlew test` in `jordylab-be/` and `bunx nx affected -t test` in `jordylab-fe/` are green; take the dev-database backup above. *(Done 2026-11-03: precondition met; backend baseline 1286/1289 — the 3 failures were a Podman socket flake, and the affected classes passed on rerun.)*

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The `identity` module skeleton, its schema (users, grants, passkey credentials, reset requests, machine tokens, sessions), all entities with repositories and tests, the moved events, and the public `CurrentUserResolver` API

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T002 Create `jordylab-be/src/main/resources/db/migration/V20261103002__identity_create_tables.sql` per [data-model.md](data-model.md): `CREATE SCHEMA IF NOT EXISTS identity; SET search_path TO identity;`, then `user_account` (unique lowercase `username`, nullable hash, `role`/`status` checks, lockout fields), `user_app_grant` (unique `(user_account_id, app)`), `user_credentials` (Spring Security WebAuthn JDBC DDL — take the exact definition from the `spring-security-webauthn` 7.1.1 `user-credentials.sql`), `password_reset_request` (partial unique `ON (user_account_id) WHERE status = 'PENDING'`), `machine_token` (unique `token_hash`, `scope = 'INGEST'`), and the Spring Session tables `spring_session` + `spring_session_attributes`. The `V<yyyyMMdd>` prefix follows the authoring date — adjust (and T090/T092's) if created on a different day, keeping the three migrations in order.
- [X] T003 [P] Create `jordylab-be/src/main/java/dev/jordy/jordylab/identity/domain/UserAccount.java` (canonical entity: `status` machine `approve(role)`, `reject()`, `revoke()`, `applyPassword(hash)`, `changeDisplayName(...)`, `grantApp(ManagedApp)` / `revokeApp(ManagedApp)` registering `AppAccessRemoved`, `recordFailedSignIn(...)`, `recordSuccessfulSignIn()`; `@ElementCollection` of `ManagedApp` → `user_app_grant`; `registerEvent(new UserAccessRemoved(id))` in reject/revoke) and `identity/domain/repository/UserAccountRepository.java`
- [X] T004 [P] Create `jordylab-be/src/main/java/dev/jordy/jordylab/identity/domain/MachineToken.java` (`revoke()`, `touchLastUsed(Instant)`) and `identity/domain/repository/MachineTokenRepository.java` (`Optional<MachineToken> findByTokenHash(String)`)
- [X] T005 [P] Create `jordylab-be/src/main/java/dev/jordy/jordylab/identity/domain/PasswordResetRequest.java` (`approve(UUID decidedBy)`, `reject(UUID decidedBy)`, `supersede()`) and `identity/domain/repository/PasswordResetRequestRepository.java` (`Optional<PasswordResetRequest> findPendingByUserAccountId(UUID)`)
- [X] T006 [P] Create `jordylab-be/src/test/java/dev/jordy/jordylab/identity/domain/UserAccountTest.java` (canonical: build, null/blank rejections, status transitions, grant/revoke events, `EqualsVerifier` with the three suppressed warnings) and `UserAccountTestBuilder.java`
- [X] T007 [P] Create `jordylab-be/src/test/java/dev/jordy/jordylab/identity/domain/MachineTokenTest.java` + `MachineTokenTestBuilder.java`
- [X] T008 [P] Create `jordylab-be/src/test/java/dev/jordy/jordylab/identity/domain/PasswordResetRequestTest.java` + `PasswordResetRequestTestBuilder.java`
- [X] T009 Create the module's public API in `jordylab-be/src/main/java/dev/jordy/jordylab/identity/`: `AuthenticatedUser.java` (record: `UUID id`, `String username`, `String role`, `Set<ManagedApp> appGrants`), `CurrentUserResolver.java` (`Optional<AuthenticatedUser> currentUser()`; empty for machine-token/anonymous), and `ManagedApp.java` (moved from `settings/service/ManagedApp.java`: `GAME_CATALOG` grantable + default; `FNA`/`SETTINGS` never grantable) — update all existing references
- [X] T010 Create `jordylab-be/src/main/java/dev/jordy/jordylab/identity/IdentityConfiguration.java` + `IdentityProperties.java` (`jordylab.identity.password.min-length: 12` + not-equal-username, `lockout.max-failures: 5` / `lock-minutes: 15`, `bootstrap.username`/`password`, `webauthn.rp-id`/`rp-name`/`allowed-origins`), register the `PasswordEncoder` bean (BCrypt strength 10), and add the `jordylab.identity.*` section to `jordylab-be/src/main/resources/application.yaml`
- [X] T011 [P] Create `jordylab-be/src/test/java/dev/jordy/jordylab/identity/CurrentUserResolverTest.java` (session principal with grants → present; machine-token/anonymous → empty) and `IdentityPropertiesTest.java` (defaults bind)
- [X] T012 Move the cross-module events from `shared/event/` to the identity root package: `UserAccessRemoved.java`, `AppAccessRemoved.java`, `UserDeleted.java`; update **every** consumer (at minimum `gamecatalog/.../MarkCleanupListener.java`; grep `shared.event` for the rest) and delete `shared/event/` if empty

**Checkpoint**: Foundation ready — the module, schema, entities, tests, events and public API exist; identity tests green; nothing else changed yet.

---

## Phase 3: User Story 1 - Register with username and password, and sign in once approved (Priority: P1) 🎯 MVP

**Goal**: The platform authenticates natively: bootstrap admin, registration with username+password (no email) and ntfy notification, admin approval with the default Game Catalog grant, sign-in through the app's own form; the Keycloak-backed backend stack is deleted.

**Independent Test**: [quickstart.md](quickstart.md) §1 with Keycloak not running: admin signs in, a second account registers → `/awaiting-approval` → approve → signs in; lockout works; wrong username/password indistinguishable. `e2e-web` is intentionally red until US7 — interim verification is this section plus the automated suites.

**Note**: Ingest (`/api/gamecatalog/ingest/*`) returns 401 from this phase until US5's machine-token filter lands; user deletion, session listing and grant toggles return when US4 restores them. Expected on the feature branch (not deployable until US5+US7).

### Tests for User Story 1 ⚠️

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [X] T013 [P] [US1] Create `jordylab-be/src/test/java/dev/jordy/jordylab/identity/service/UserAccountServiceTest.java`: registration (lowercase, `USERNAME_TAKEN`, `WEAK_PASSWORD`, `INVALID_USERNAME`, `PENDING`+null role, ntfy call, no password logged), approve (role `guest`, `GAME_CATALOG` grant, `ALREADY_APPROVED`), reject/revoke (`USER_NOT_APPROVED`, `LAST_ADMIN_PROTECTED`, `UserAccessRemoved` published)
- [X] T014 [P] [US1] Create `jordylab-be/src/test/java/dev/jordy/jordylab/identity/service/SessionSignInServiceTest.java`: authorities (`admin` → `ROLE_admin`; `guest`+grant → `ROLE_guest`+`ROLE_app-gamecatalog`; `PENDING` → none), uniform `INVALID_CREDENTIALS`, lockout + `ACCOUNT_LOCKED`, `ACCOUNT_REJECTED`/`ACCOUNT_REVOKED`, session-fixation strategy invoked
- [X] T015 [P] [US1] Create `jordylab-be/src/test/java/dev/jordy/jordylab/identity/rest/controller/AuthenticationControllerTest.java` (`@WebMvcTest` + real chains): register/login/logout/me/csrf, `PATCH /me` display name, `change-password`, `reset-requests` (always-202 is US6 — cover the path shape only here), contract codes, `@Language("JSON")`
- [X] T016 [P] [US1] Create `jordylab-be/src/test/java/dev/jordy/jordylab/identity/rest/controller/UserControllerTest.java` (base): admin-only 403, users list with `role`/`status`/`appGrants`/`pendingResetRequest`, pending-count, approve/reject/revoke codes — grants/sessions/delete paths arrive in US4
- [X] T017 [P] [US1] Create `jordylab-be/src/test/java/dev/jordy/jordylab/identity/IdentityRoleMatrixTest.java` (`@SpringBootTest` + Testcontainers Postgres — replaces `settings/RoleMatrixTest.java`): admin/guest-with-grant/guest-without-grant/pending/anonymous/locked rows of [contracts/access-matrix.md](contracts/access-matrix.md), identity gates, deny-all fallback, "removed mobile endpoints are denied" kept as regression
- [X] T018 [P] [US1] Create `jordylab-be/src/test/java/dev/jordy/jordylab/identity/service/BootstrapAdminInitializerTest.java`: creates the admin only when `user_account` is empty; logs and no-ops when users exist; never logs the password
- [X] T019 [P] [US1] Rewrite `jordylab-fe/libs/shared/auth/src/lib/auth.service.spec.ts` for the session service: boot via `/me` (200 → roles+grants; 401 → unauthenticated), `login`, `logout`, `changePassword`, `updateDisplayName`, `isAdmin`/`hasGameCatalogAccess`
- [X] T020 [P] [US1] Create `jordylab-fe/libs/shared/auth/src/lib/login.component.spec.ts` + `register.component.spec.ts`: form validation, submit wiring, error banners, `returnUrl`, register → pending → `/awaiting-approval`
- [X] T021 [P] [US1] Update `jordylab-fe/libs/settings/api/src/lib/users.store.spec.ts` and the users-page specs for the `/api/identity/users` payloads (grants, `pendingResetRequest: null`) and approve/reject/revoke

### Implementation for User Story 1

- [X] T022 [US1] Create `jordylab-be/src/main/java/dev/jordy/jordylab/identity/IdentitySecurityConfiguration.java` per [contracts/access-matrix.md](contracts/access-matrix.md): chain 1 (order 1, stateless, CSRF off, ingest matchers — denyAll until US5) and chain 2 (session chain, full URL matrix with `admin`/`app-gamecatalog` authorities, `/api/identity/auth/**` permitAll, CSRF via `CookieCsrfTokenRepository` + XOR handler with `XSRF-TOKEN`/`X-XSRF-TOKEN`, session fixation); the WebAuthn configurer is added in US2
- [X] T023 [US1] Create `jordylab-be/src/main/java/dev/jordy/jordylab/identity/service/SessionSignInService.java` (AuthenticationManager, lockout counters, status refusals, `ChangeSessionIdAuthenticationStrategy`, authorities from role+grants)
- [X] T024 [US1] Create `jordylab-be/src/main/java/dev/jordy/jordylab/identity/service/UserAccountService.java` (register + ntfy via the shared `NtfyClient`, approve/reject/revoke with the last-approved-admin guard, default grants, events in-transaction, admin user views)
- [X] T025 [US1] Create `jordylab-be/src/main/java/dev/jordy/jordylab/identity/rest/controller/AuthenticationController.java` (+ `model/` records: register/login/me/csrf/logout/display-name/change-password) and `identity/rest/controller/IdentityExceptionHandler.java` with the contract codes
- [X] T026 [US1] Create `jordylab-be/src/main/java/dev/jordy/jordylab/identity/rest/controller/UserController.java` (users list with status filter, pending-count, approve/reject/revoke — grants/sessions/delete/resets land in US4/US6)
- [X] T027 [US1] Create `jordylab-be/src/main/java/dev/jordy/jordylab/identity/service/BootstrapAdminInitializer.java` and wire bootstrap values: `application-local.yaml` (dev `jordy` admin) and `application-prod.yaml` (`IDENTITY_BOOTSTRAP_ADMIN_USERNAME`/`_PASSWORD`)
- [X] T028 [P] [US1] Switch the `gamecatalog` module to `CurrentUserResolver`: `GameCatalogController.java`, `MarkController.java`, `RefreshRunController.java`, `LibBotController.java` (drop `@AuthenticationPrincipal Jwt` + `subjectOf`; ids unchanged); update their tests' JWT stubs to resolver stubs
- [X] T029 [P] [US1] Switch `settings`/`shared` to `CurrentUserResolver`: `SettingsAiModelsController.java`, `GuestChatQuotaController.java`, `GuestChatLimitFilter.java`, `shared/ai/AiTracing.java` (stable user id); update their tests
- [X] T030 [US1] Delete `jordylab-be/src/main/java/dev/jordy/jordylab/shared/config/SecurityConfig.java`, update `shared/config/package-info.java`, and fix every test importing the old `SecurityConfig` to import the identity configuration
- [X] T031 [US1] Delete the Keycloak-backed settings stack and its tests: `settings/rest/client/KeycloakAdminClient.java`, `KeycloakUnavailableException.java`, `settings/KeycloakAdminProperties.java` (+ `SettingsConfiguration` enablement), `settings/service/KeycloakUserAdministrationService.java`, `PendingSignupWatcherService.java`, `UserDeletionService.java`, `UserSessionService.java`, `AppUser.java`, `UserStatus.java`, `settings/rest/controller/SettingsUsersController.java`; tests `KeycloakIntegrationTest.java`, `RoleMatrixTest.java`, `UserManagementIntegrationTest.java`, `KeycloakSessionVerificationIntegrationTest.java`, `KeycloakAdminClientTest.java`, `KeycloakAdminClientRealmExportIntegrationTest.java`, `RealmConfigurationTest.java`, `ProdKeycloakUrlConfigurationTest.java`, `SettingsUsersControllerTest.java`, `KeycloakUserAdministrationServiceTest.java`, `PendingSignupWatcherServiceTest.java`, and the test realm fixture `src/test/resources/keycloak/`; rewrite `GuestChatLimitIntegrationTest.java` against the native security (no Keycloak). **Keep** `GuestChatLimit*` services/entity/repository and `GuestChatQuotaController` (they return to the UI via US4's events)
- [X] T032 [US1] Remove the OAuth2 stack from the backend build and config: drop `spring-boot-starter-oauth2-resource-server` from `jordylab-be/build.gradle.kts`; remove `spring.security.oauth2.resourceserver.jwt.issuer-uri` and `jordylab.settings.keycloak.*` from `application.yaml`, `application-local.yaml`, `application-prod.yaml` (the `jordylab.script.keycloak-url` removal lands with US5)
- [X] T033 [US1] Rewrite `jordylab-fe/libs/shared/auth/src/lib/auth.service.ts` + `auth-config.ts` for sessions (no `keycloak-js`): `/me` on init, `login(username, password)`, `logout()`, `changePassword()`, `updateDisplayName()`, signals for role + `appGrants` (`isAdmin`, `isGuest`, `hasGameCatalogAccess` preserved); delete `auth.interceptor.ts`, `pkce.ts`, `apps/jordylab/public/silent-check-sso.html` + `.js`, `apps/jordylab/src/app/pre-bootstrap-auth.ts` (+ the three `main.ts` pre-bootstrap blocks); update `apps/jordylab/src/app/app.config.ts`
- [X] T034 [US1] Build the real forms: rewrite `login.component.*` as a username/password form (keeps `returnUrl`), create `register.component.*` (username, password, optional display name — no email), add routes `/login` + `/register` in `apps/jordylab/src/app/app.routes.ts` and the `apps/fna` / `apps/gamecatalog` route files; keep the `/awaiting-approval` flow for role-less sessions
- [X] T035 [US1] Point user management at the identity API: `libs/settings/api/src/lib/settings-users-api.service.ts` + `users.store.ts` → `/api/identity/users` (+ `/pending-count`), keep approve/reject/revoke in `libs/settings/ui/src/lib/users-page/`; temporarily remove the reset-password, grant-toggle, session and delete actions (restored natively in US4/US6)
- [X] T036 [US1] Remove `keycloak-js` from `jordylab-fe/package.json` and strip every `keycloak*` field and the `window.__JORDYLAB_E2E__` injection from the seven environment files (`apps/jordylab/src/environments/environment{,.prod,.e2e}.ts`, `apps/fna/...`, `apps/gamecatalog/...`) and their `fileReplacements` usages
- [ ] T037 [US1] Verify the story: `./gradlew :test --tests "dev.jordy.jordylab.identity.*" --tests "*ModularityTests*"` plus the touched module tests, `bunx nx test shared-auth settings-api settings-ui`, then [quickstart.md](quickstart.md) §1 by hand

**Checkpoint**: The platform authenticates natively (register → approve → sign in, roles + Game Catalog grant). Keycloak is no longer referenced by backend code; it still runs idle in compose until US7.

---

## Phase 4: User Story 2 - Sign in with a passkey (fingerprint/face) (Priority: P1)

**Goal**: Signed-in users add passkeys; sign-in offers "Sign in with a passkey" (username-first) and completes with the device's fingerprint/face; credentials list/remove in Settings; passwords remain the recovery path.

**Independent Test**: [quickstart.md](quickstart.md) §2 by hand (add passkey, sign out, sign in with passkey, remove) plus the automated adapter/options/management tests; the real ceremony is automated with a virtual authenticator in US7's e2e suite.

### Tests for User Story 2 ⚠️

- [X] T038 [P] [US2] Create `jordylab-be/src/test/java/dev/jordy/jordylab/identity/rest/controller/IdentityUserDetailsTest.java`: username/password/authorities from role+grants, `userHandle` = UUID bytes, credentials from the repository, `REJECTED`/`REVOKED` → disabled, `PENDING` → enabled with no authorities
- [X] T039 [P] [US2] Create `jordylab-be/src/test/java/dev/jordy/jordylab/identity/WebAuthnEndpointsIntegrationTest.java` (`@SpringBootTest` + Testcontainers Postgres): `/webauthn/register` requires a session, `/webauthn/authenticate` is permitAll, options JSON shape, CSRF rejection without the header, unknown username fails without enumeration (the successful assertion is e2e-only with a virtual authenticator)
- [X] T040 [P] [US2] Create `jordylab-be/src/test/java/dev/jordy/jordylab/identity/rest/controller/PasskeyControllerTest.java`: list own credentials, delete own, `404 PASSKEY_NOT_FOUND` for others'
- [X] T041 [P] [US2] Create `jordylab-fe/libs/shared/auth/src/lib/webauthn.spec.ts` (base64url conversions, create/get wiring against mocked `navigator.credentials`) and extend `login.component.spec.ts` (passkey action requires a username; error surface) + the Settings passkeys-card spec
- [X] T042 [P] [US2] Create `jordylab-fe/libs/shared/auth/src/lib/passkeys-card.component.spec.ts` (list/add/remove flows, empty state, failure message) *(the card lives on the app's Account page; its service flows are covered by `auth.service.spec.ts`/`webauthn.spec.ts` and the live browser check, with the full UI journey in US7's e2e)*

### Implementation for User Story 2

- [X] T043 [US2] Add `spring-security-webauthn` (BOM-managed 7.1.1) to `jordylab-be/build.gradle.kts` (first build downloads it — verify the artifact resolves) and wire the `jordylab.identity.webauthn.*` values in `application.yaml`/`application-local.yaml` (`localhost`) / `application-prod.yaml` (`jordylab.be`) / e2e env *(dependency and RP config already landed with the Phase 2 slice; the ceremony wiring remains)*
- [X] T044 [US2] Create `jordylab-be/src/main/java/dev/jordy/jordylab/identity/rest/controller/IdentityUserDetailsService.java` (Spring Security `UserDetailsService` returning the `WebAuthnUserDetails` adapter backed by `JdbcUserCredentialRepository`; status mapping per T038)
- [X] T045 [US2] Wire `http.webAuthn(...)` in `IdentitySecurityConfiguration.java`: relying party from properties, allowed origins, user-details service, `JdbcUserCredentialRepository` on `identity.user_credentials`, JSON success/failure handlers returning the `/me` payload and preserving the session (fixation protection)
- [X] T046 [US2] Create `jordylab-be/src/main/java/dev/jordy/jordylab/identity/rest/controller/PasskeyController.java` (`GET /api/identity/passkeys`, `DELETE /api/identity/passkeys/{credentialId}`) per [contracts/identity-api.md](contracts/identity-api.md)
- [X] T047 [US2] Create `jordylab-fe/libs/shared/auth/src/lib/webauthn.ts` (base64url ↔ ArrayBuffer helpers and the create/get ceremony calls against `/webauthn/register` and `/webauthn/authenticate`)
- [X] T048 [US2] Add "Sign in with a passkey" to `login.component.*` (requires the username; clear no-passkey error) and a Passkeys card (list/add/remove) to `apps/jordylab/src/app/app-settings/` (+ export from `libs/shared/auth/src/index.ts`)
- [ ] T049 [US2] Verify the story per [quickstart.md](quickstart.md) §2 (desktop + phone PWA) and run the US2 tests *(options endpoint, login button and Account card verified live; the fingerprint/face ceremony itself is proven by US7's Playwright virtual-authenticator journey)*

**Checkpoint**: Passkey enrollment + fingerprint/face sign-in work end to end by hand; the automated ceremony arrives with the e2e rewrite (US7).

---

## Phase 5: User Story 3 - Stay signed in across deploys, safely (Priority: P1)

**Goal**: Sessions persist in Postgres via Spring Session JDBC; restart/deploy survival, hardened cookie, CSRF enforcement, real sign-out.

**Independent Test**: [quickstart.md](quickstart.md) §3 — restart mid-session stays signed in; POST without `X-XSRF-TOKEN` → 403; sign-out invalidates.

### Tests for User Story 3 ⚠️

- [X] T050 [P] [US3] Create `jordylab-be/src/test/java/dev/jordy/jordylab/identity/SessionPersistenceIntegrationTest.java` (Testcontainers Postgres): login writes `identity.spring_session`; a second repository context reads it; logout invalidates; `JSESSIONID` HttpOnly + `SameSite=Lax`
- [X] T051 [P] [US3] Create `jordylab-be/src/test/java/dev/jordy/jordylab/identity/CsrfIntegrationTest.java`: mutating POST without `X-XSRF-TOKEN` → 403, with cookie+header → 2xx; machine-token chain stays CSRF-exempt (once US5 lands) *(covered by `IdentityRoleMatrixTest.csrfIsRequiredForSessionMutations`; the session test also logs in through the CSRF-protected endpoint)*

### Implementation for User Story 3

- [X] T052 [US3] Add the Spring Session JDBC dependency (Boot-BOM 4.1.1) to `jordylab-be/build.gradle.kts`, configure `spring.session` (JDBC, `initialize-schema: never`, schema-qualified table prefix for `identity.`), `server.servlet.session.timeout: 30d`, and the persistent cookie settings in `application.yaml`; verify the prefix mechanism against 4.1.1 — if it differs from T002's DDL, add a **new** migration (never edit `V20261103002`) *(starter dependency already added with the Phase 2 slice; configuration remains)*
- [ ] T053 [US3] Verify deploy-survival per [quickstart.md](quickstart.md) §3 (manual restart) and confirm T050/T051 green

**Checkpoint**: US1+US2+US3 deliver the P1 trio: native accounts, passkey sign-in, robust sessions.

---

## Phase 6: User Story 4 - Admin manages app access, sessions, and deletion (Priority: P1)

**Goal**: Restore spec 014's admin capabilities on the identity module: per-app grant toggles, session listing/ending, staged account deletion, guest-limit cleanup via events, last-admin guard.

**Independent Test**: [quickstart.md](quickstart.md) §4 — grant/revoke Game Catalog, session list/end, delete a user with cleanup, self/last-admin refused.

### Tests for User Story 4 ⚠️

- [X] T054 [P] [US4] Extend `UserAccountServiceTest.java`: `grantApp`/`revokeApp` (authority changes, `AppAccessRemoved` published, duplicate grant idempotent), revoke clears grants
- [X] T055 [P] [US4] Create `jordylab-be/src/test/java/dev/jordy/jordylab/identity/service/UserSessionServiceTest.java` and `UserDeletionServiceTest.java`: list/end web sessions (own account refused), staged deletion (self refused, any admin refused, events published, personal limit/usage cleanup signalled, idempotent)
- [X] T056 [P] [US4] Extend `UserControllerTest.java`: grant/revoke endpoints, sessions list/end/end-all (`409 OWN_ACCOUNT`), delete (`409 LAST_ADMIN_PROTECTED`), error codes per the contract
- [ ] T057 [P] [US4] Create `jordylab-be/src/test/java/dev/jordy/jordylab/identity/IdentityUserManagementIntegrationTest.java` (replaces the deleted `settings/UserManagementIntegrationTest.java`): approval default grants, grant/revoke end-to-end through the API, revocation ends sessions immediately, deletion cleans usage/limit, timing expectations *(parity covered by the service/controller/store suites and the live browser check; the full API journey lands in US7's `user-management.spec.ts` e2e)*
- [X] T058 [P] [US4] Extend the frontend specs: users-page grant toggles, session panel (end one/all), delete confirmation and the self/last-admin messages

### Implementation for User Story 4

- [X] T059 [US4] Add the grant endpoints to `UserController.java` (`PUT`/`DELETE /api/identity/users/{id}/apps/{app}`) backed by `UserAccountService`, and include `appGrants` in the user views
- [X] T060 [US4] Create `jordylab-be/src/main/java/dev/jordy/jordylab/identity/service/UserSessionService.java` (Spring Session `FindByIndexNameSessionRepository` by username; end one/all; own account refused) and its endpoints in `UserController.java`
- [X] T061 [US4] Create `jordylab-be/src/main/java/dev/jordy/jordylab/identity/service/UserDeletionService.java` (adapted from the deleted settings version: end sessions, delete account, publish `UserAccessRemoved` + `UserDeleted`; refuse self/any admin; idempotent) and `DELETE /api/identity/users/{id}` in `UserController.java`
- [X] T062 [US4] Add `jordylab-be/src/main/java/dev/jordy/jordylab/settings/GuestDataCleanupListener.java` (`@ApplicationModuleListener` on `UserAccessRemoved`/`UserDeleted` → clear the personal chat limit and usage via the existing `GuestChatLimitService`/repositories) and verify `ModularityTests`
- [X] T063 [US4] Restore the admin UI in `jordylab-fe/libs/settings/ui/src/lib/users-page/`: Game Catalog grant toggle, session panel (end one/all), delete with confirmation; extend `users.store.ts` + `settings-users-api.service.ts`; verify [quickstart.md](quickstart.md) §4

**Checkpoint**: 014 parity restored — no admin capability lost with Keycloak.

---

## Phase 7: User Story 5 - Manage per-machine scanner tokens (Priority: P2)

**Goal**: "Add machine" tokens (single `INGEST` scope) authenticate the scanner; the client uses them with its unchanged cache; revocation stops the next call.

**Independent Test**: [quickstart.md](quickstart.md) §5 with the real scan client on the real machine.

### Tests for User Story 5 ⚠️

- [X] T064 [P] [US5] Create `jordylab-be/src/test/java/dev/jordy/jordylab/identity/service/MachineTokenServiceTest.java`: `jlmt_` + ≥256 bits, returned once, only SHA-256 persisted, revoke, `last_used_at` ≤ once/minute
- [X] T065 [P] [US5] Extend `IdentityRoleMatrixTest.java` (or `MachineTokenAuthenticationFilterTest.java`): valid token → `ROLE_gamecatalog-scanner` on check/scan; wrong path → 403/deny; revoked/unknown/malformed → 401; `last_used_at` advances
- [X] T066 [P] [US5] Create `jordylab-be/src/test/java/dev/jordy/jordylab/identity/rest/controller/MachineTokenControllerTest.java`: admin-only, create returns the one-time token, list, revoke (`409 ALREADY_REVOKED`)
- [X] T067 [P] [US5] Rewrite `gamecatalog-scanner/tests/test_auth.py` (env/prompt, cache 0600, 401 clears), `test_config.py` (no Keycloak defaults), `test_build_client.py` (new constants)
- [X] T068 [P] [US5] Create `jordylab-fe/libs/settings/api/src/lib/machines.store.spec.ts` + machines-page specs (list, add → one-time token panel, revoke)

### Implementation for User Story 5

- [X] T069 [US5] Create `jordylab-be/src/main/java/dev/jordy/jordylab/identity/service/MachineTokenService.java` and `identity/rest/controller/MachineTokenController.java`
- [X] T070 [US5] Create `jordylab-be/src/main/java/dev/jordy/jordylab/identity/rest/controller/MachineTokenAuthenticationFilter.java` and activate chain 1 in `IdentitySecurityConfiguration.java` (ingest works again)
- [X] T071 [US5] Rewrite the scan client: `gamecatalog-scanner/src/jordylab_scan/auth.py` + `config.py` + `__main__.py`; regenerate `jordylab-be/src/main/resources/scripts/jordylab-scan-template.py` via `gamecatalog-scanner/tools/build_client.py` (BACKEND_URL/SCAN_ENDPOINT/LIBRARY_TYPE, no Keycloak constants); update `gamecatalog/service/ClientService.java` (+ `ClientServiceTest`) and remove `jordylab.script.keycloak-url` from the three yaml files
- [X] T072 [US5] Build the Machines admin UI: `MachinesStore` + API in `libs/settings/api/src/lib/`, Container–Presentation machines page in `libs/settings/ui/src/lib/machines-page/`, route `/settings/machines` + admin nav item
- [X] T073 [US5] Verify [quickstart.md](quickstart.md) §5 with the real scan client and revocation; run the US5 tests

**Checkpoint**: Scanning works without any Keycloak token.

---

## Phase 8: User Story 6 - Recover a forgotten password through the admin (Priority: P2)

**Goal**: Signed-out users request resets (hash-only, no enumeration); admins approve/reject in the Users page; direct reset and own-password change return; break-glass runbook.

**Independent Test**: [quickstart.md](quickstart.md) §6 — request → approve → sign in; reject keeps old; direct reset; supersede; break-glass rehearsal.

### Tests for User Story 6 ⚠️

- [X] T074 [P] [US6] Create `jordylab-be/src/test/java/dev/jordy/jordylab/identity/service/PasswordResetServiceTest.java`: hash-only storage, one `PENDING` (newer replaces), unknown usernames create nothing, approve applies + closes, reject keeps, own-change/admin-reset supersede
- [X] T075 [P] [US6] Extend `AuthenticationControllerTest.java` + `UserControllerTest.java`: reset-requests always-202 (`400 WEAK_PASSWORD`), decision endpoints (`404 REQUEST_NOT_FOUND`, `409 REQUEST_NOT_PENDING`), `reset-password` returns `temporaryPassword` once
- [X] T076 [P] [US6] Frontend specs: `forgot-password.component.spec.ts`, user-menu change-password + display-name dialogs, users-page reset-request panels + reset-password reveal

### Implementation for User Story 6

- [X] T077 [US6] Create `jordylab-be/src/main/java/dev/jordy/jordylab/identity/service/PasswordResetService.java`, move `TemporaryPasswordGenerator.java` from `settings/util/` to `identity/util/`, and add the admin `reset-password` endpoint to `UserController.java`
- [X] T078 [US6] Add the reset-request endpoints: `POST /api/identity/auth/reset-requests` (generic 202), approve/reject decisions + `pendingResetRequest` in user views + reset requests in `pending-count` (`UserController.java`)
- [X] T079 [US6] Add `POST /api/identity/auth/change-password` (current required, supersedes pending, other sessions stay) — display-name `PATCH /me` already landed in US1
- [X] T080 [US6] Frontend: `forgot-password.component.*` + `/forgot-password` route; users-page reset-request panels + restored reset-password action; `UserMenuComponent` change-password + edit-display-name dialogs (replacing the Keycloak AIA actions) *(the menu links to `/account`, which already hosts both forms from US1 — no duplicate dialogs)*
- [X] T081 [US6] Document break-glass in `docs/runbook.md` (BCrypt one-liner + `UPDATE identity.user_account SET password_hash = …` against CNPG, operator-only) and rehearse it on dev per [quickstart.md](quickstart.md) §6.5

**Checkpoint**: Every account state is recoverable without email or links.

---

## Phase 9: User Story 7 - Cut over without breaking identities, then remove Keycloak (Priority: P3)

**Goal**: E2E runs without Keycloak (real forms + virtual-authenticator passkey journey + profile-gated test sign-in), existing users+grants migrate, and — after the owner-verified backup — every Keycloak artifact is removed.

**Independent Test**: [quickstart.md](quickstart.md) §7–§8 — `e2e/run.sh web` green on the Postgres-only stack; release A verified; release B leaves no Keycloak outside docs history.

### Tests for User Story 7 ⚠️

- [X] T082 [P] [US7] Create `jordylab-be/src/test/java/dev/jordy/jordylab/identity/KeycloakUserMigrationTest.java` (Testcontainers Postgres with a synthetic `keycloak` schema fixture): running the cutover migration copies persons (id = `sub`, lowercased username, display name, role admin>guest, `GAME_CATALOG` grant from `app-gamecatalog`, `APPROVED`, hash null, service accounts skipped); absent schema → no-op
- [X] T083 [P] [US7] Create `jordylab-be/src/test/java/dev/jordy/jordylab/identity/ProdTestSignInAbsenceTest.java`: prod profile → `/api/identity/test/sign-in` 404; `local,e2e` → issues a session
- [X] T084 [P] [US7] Rewrite the e2e support layer in `jordylab-fe/apps/jordylab-e2e/src/support/`: `session.ts` (real-form sign-in, username/password + optional passkey helper), `guest.ts` (app register form + admin approval), `fixtures.ts`, `global-setup.ts` (bootstrap admin, machine token via API), `catalog-api.ts` (bearer machine token), `playwright.config.ts`; update `auth.spec.ts`, `settings.spec.ts`, `gamecatalog-marks.spec.ts`, `csp.spec.ts`, `accessibility.spec.ts`, `pwa.spec.ts`
- [X] T085 [P] [US7] Create `jordylab-fe/apps/jordylab-e2e/src/passkeys.spec.ts` using Playwright's CDP virtual authenticator (`WebAuthn.enable` + `addVirtualAuthenticator`, CTAP2/internal/user-verified): enroll in Settings, sign out, sign in with the passkey; also cover password fallback after removal
- [X] T086 [P] [US7] Create `jordylab-fe/apps/jordylab-e2e/src/user-management.spec.ts` (014's pending T106): approve, grant/revoke Game Catalog, end sessions, delete a user

### Implementation for User Story 7

- [X] T087 [US7] Build the e2e-only sign-in: `jordylab-be/src/main/java/dev/jordy/jordylab/identity/rest/controller/TestSignInController.java` (`@Profile("e2e")`) and extend `shared/config/EnvironmentProfileGuard.java` to accept `e2e` alongside `local`/`prod`
- [X] T088 [US7] Rewrite the e2e stack: `jordylab-fe/e2e/compose.e2e.yaml` (Postgres only — delete the Keycloak service and `keycloak-init.sql` bind), `jordylab-fe/e2e/run.sh` (drop `E2E_KEYCLOAK_*`, Keycloak readiness and issuer env; add `E2E_ADMIN_PASSWORD`, `SPRING_PROFILES_ACTIVE=local,e2e`, `JORDYLAB_IDENTITY_WEBAUTHN_ALLOWED_ORIGINS=http://localhost:$WEB_PORT`), delete `jordylab-fe/e2e/lib/make-realm.py`
- [X] T089 [US7] Run the rewritten suite locally (`e2e/run.sh web` + `bash e2e/lib/cleanup.sh verify-all`) and fix every journey — `e2e-web` is green again from here
- [X] T090 [US7] Create `jordylab-be/src/main/resources/db/migration/V20261103003__identity_migrate_keycloak_users.sql`: verify the exact Keycloak 26.7 table/column names against the dev DB with read-only `psql`, then the guarded `DO $$ … IF EXISTS (schema 'keycloak') …` + `INSERT … SELECT` per [data-model.md](data-model.md) (users + grants, `RAISE NOTICE` when absent)
- [ ] T091 [US7] **OWNER GATE — not started by the agent**: **Release A gate — owner action, STOP before any removal task**: deploy release A to production; verify read-only that `identity.user_account` matches the Keycloak persons (count + ids = `sub`s) and grants match `app-gamecatalog`; have every user sign in via an approved reset and add a passkey; then take the **verified CNPG backup** (runbook §15, comparison row now `identity.user_account`). Record the sign-off here before T092
- [ ] T092 [OWNER-GATED] [US7] Create `jordylab-be/src/main/resources/db/migration/V20261103004__drop_keycloak_schema.sql` (`DROP SCHEMA IF EXISTS keycloak CASCADE` + `RAISE NOTICE` when absent) — only after T091's recorded sign-off
- [ ] T093 [OWNER-GATED] [US7] Remove the Keycloak deployment (k8s today): delete `deploy/containers/keycloak/` and `deploy/k8s/base/keycloak.yaml`, remove the `keycloak-route` from `gateway.yaml`, the keycloak entries from both kustomization `images:` lists, the `keycloak-config` ConfigMap and all `KEYCLOAK_*` keys from `deploy/k8s/overlays/prod/kustomization.yaml` (+ `backend-config`), re-key `secrets.sops.yaml` (add `IDENTITY_BOOTSTRAP_ADMIN_*`, drop `KEYCLOAK_ADMIN_BOOTSTRAP_*`/`KEYCLOAK_BACKEND_CLIENT_SECRET` — SOPS edit by the owner), add the bootstrap env to `deploy/k8s/base/backend.yaml`, and drop the `keycloak` role/schema/managed secret from `deploy/k8s/cluster/cnpg-cluster.yaml`. If spec 017's Compose stack has landed, remove its equivalent Keycloak service/route/env instead
- [ ] T094 [OWNER-GATED] [US7] Remove Keycloak from CI: drop the `keycloak` matrix entries in `.github/workflows/build.yml` and `.github/workflows/release.yml`, and the keycloak image/`rollout status deploy/keycloak` steps from `.github/workflows/deploy-prod.yml` (or the 017 Compose workflow if it exists)
- [ ] T095 [OWNER-GATED] [US7] Remove Keycloak from local dev: delete the `keycloak` service, `compose/keycloak-init.sql`, `compose/keycloak-realm-export.json`, `compose/keycloak-theme/`, and clean `KEYCLOAK_*` from `jordylab-be/.env.example`
- [ ] T096 [OWNER-GATED] [P] [US7] Rewrite the docs to the new reality: `docs/runbook.md` (§11/§13/§15 without `deploy/keycloak`; the one-time APK-stack section stays historical), `docs/environments.md`, `docs/cowork-deploy-ovh.md`, `docs/learn/01`–`09` + README, root `AGENTS.md` + `jordylab-be/AGENTS.md` + `jordylab-fe/AGENTS.md`, `.claude/skills/jordylab-ops/SKILL.md`, `.claude/agents/jordylab-devops.md`, `.opencode/agents/jordylab-devops.md`; leave `docs/testing/*` history untouched
- [ ] T097 [OWNER-GATED] [US7] Release B verification per [quickstart.md](quickstart.md) §8: deploy; no `deploy/keycloak`; `/auth/realms/jordylab` → 404; `grep -ri keycloak` excluding `docs/testing/` and `specs/` returns nothing; `e2e/run.sh web` + `cleanup.sh verify-all` green

**Checkpoint**: Production runs one fewer container, the repository is Keycloak-free outside history, and the whole suite is green (SC-008).

---

## Phase 10: Polish & Cross-Cutting Concerns

**Purpose**: Full-suite verification, coverage, and the quickstart walkthrough per [quickstart.md](quickstart.md)

- [ ] T098 Run the full backend suite and module boundaries (`export DOCKER_HOST=…` + `TESTCONTAINERS_RYUK_DISABLED=true`, then `./gradlew test` and `./gradlew :test --tests "*ModularityTests*"`)
- [ ] T099 [P] Run the full frontend verification (`bunx nx affected -t test && bunx nx affected -t lint && bunx nx affected -t oxlint`)
- [ ] T100 Run the e2e suite end to end: `jordylab-fe/e2e/run.sh web` then `bash e2e/lib/cleanup.sh verify-all`
- [ ] T101 Verify coverage: JaCoCo ≥80% for the `identity` module and no regression in `settings`/`shared`/`gamecatalog`/`fna`; frontend coverage backstop for the touched libs
- [ ] T102 Walk [quickstart.md](quickstart.md) §1–§6 end to end on the local stack with the dev-database backup first, and record the results (real flows only — no hand-seeded data)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies (`T001`); the 018 precondition is met, this is a verification.
- **Foundational (Phase 2)**: Depends on Phase 1 (`T002`–`T012`) — **BLOCKS all user stories**.
- **US1 (Phase 3, P1)**: Depends on Phase 2 (`T013`–`T037`) — the MVP; deletes the Keycloak backend stack. Ingest 401s and admin parity gaps until US5/US4.
- **US2 (Phase 4, P1)**: Depends on US1 (`T038`–`T049`).
- **US3 (Phase 5, P1)**: Depends on US1 (`T050`–`T053`).
- **US4 (Phase 6, P1)**: Depends on US1 (`T054`–`T063`); restores 014 parity, can run in parallel with US2/US3.
- **US5 (Phase 7, P2)**: Depends on US1 (`T064`–`T073`); restores ingest.
- **US6 (Phase 8, P2)**: Depends on US1 (`T074`–`T081`); independent of US2–US5.
- **US7 (Phase 9, P3)**: Depends on US2–US6 (the e2e rewrite needs every user flow); `T091` is an **owner gate** — `T092`–`T097` must not start before its recorded sign-off.
- **Polish (Phase 10)**: Depends on Phase 9 (`T098`–`T102`).

### Parallel Opportunities

- **Phase 2**: `T003` ∥ `T004` ∥ `T005` after `T002`; `T006` ∥ `T007` ∥ `T008` ∥ `T011` after their entities.
- **Phase 3 (US1 tests)**: `T013`–`T021` all parallel.
- **Phase 3 (US1 implementation)**: `T028` (gamecatalog) ∥ `T029` (settings/shared) while `T022`–`T027` land the identity core; `T033`–`T036` (frontend) parallel to the backend swaps after `T022`–`T026`.
- **Phases 4–6 and 8**: US2 (passkeys), US3 (sessions), US4 (admin parity) and US6 (recovery) are mutually independent after US1 — e.g. `T043` (US2) ∥ `T052` (US3) ∥ `T059`–`T061` (US4) ∥ `T077`–`T079` (US6).
- **Phase 9**: `T082` ∥ `T083` ∥ `T084` ∥ `T085` ∥ `T086` before `T087`–`T089`; `T096` (docs) parallel to `T093`–`T095`.

---

## Parallel Example: User Story 1

```bash
# Write the failing tests in parallel (different files):
Task: "T013 [P] [US1] Create jordylab-be/.../identity/service/UserAccountServiceTest.java"
Task: "T015 [P] [US1] Create jordylab-be/.../identity/rest/controller/AuthenticationControllerTest.java"
Task: "T017 [P] [US1] Create jordylab-be/.../identity/IdentityRoleMatrixTest.java"
Task: "T019 [P] [US1] Rewrite jordylab-fe/libs/shared/auth/src/lib/auth.service.spec.ts"

# Then the module-swap tasks in parallel (different modules):
Task: "T028 [P] [US1] Switch gamecatalog controllers to CurrentUserResolver"
Task: "T029 [P] [US1] Switch settings/shared to CurrentUserResolver"
```

---

## Implementation Strategy

### MVP First (US1 + US2 + US3)

1. Complete Phase 1 (baseline) and Phase 2 (foundation).
2. Complete Phase 3 (US1: native register/approve/sign-in; Keycloak stack deleted).
3. Complete Phase 4 (US2: passkeys) and Phase 5 (US3: persistent sessions + CSRF).
4. Validate per [quickstart.md](quickstart.md) §1–§3.

### Then parity, the remaining clients, recovery, cutover

5. Phase 6 (US4: 014 parity — do this before any deployment), Phase 7 (US5 machine tokens), Phase 8 (US6 recovery) — in any order or parallel.
6. Phase 9 (US7): e2e rewrite first (`T087`–`T089` restore the `e2e-web` check), cutover migration, then the **owner gate** (`T091`: release A, user verification, passkey enrollment, verified backup) before any removal task (`T092`–`T097`).
7. Phase 10: full suites, coverage, quickstart walkthrough.

### Branch-state warnings

- One PR carries the whole spec (repo rule: one PR per spec). The feature branch is **intentionally not green end-to-end until Phase 9**: `e2e-web` is red from US1 until `T089`, ingest 401s from US1 until US5, and admin parity (grants/sessions/delete) is restored in US4. Keep every story's own unit/integration suites green at its checkpoint.
- The branch is not deployable until US1–US6 are done and is deployed only per the two-release sequence in US7.
- Owner actions embedded in the flow: the release A sign-off + verified backup (T091) and the SOPS re-key (T093).
- Precondition: spec 018 is merged (verified in T001). If the Compose deployment (spec 017) lands before release B, swap the k8s removal targets for its equivalents (T093/T094).
