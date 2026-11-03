# Research: Authentication Inside the Backend Without Keycloak

**Feature**: [spec.md](spec.md) · **Date**: 2026-11-02, revised 2026-11-03 · **Branch**: `019-auth-without-keycloak`

Sources: fresh repo exploration against the post-018/post-014 main (2026-11-03), the draft (`specs/_drafts/auth-without-keycloak/`), and the owner's decisions: username+password base (2026-11-02, spec Clarifications) and passkey sign-in for fingerprint/face (2026-11-03, recorded in the draft).

The draft's "facts to verify in the repo" are resolved:

1. *WebAuthn dependency availability* — `org.springframework.security:spring-security-webauthn` is managed by the Spring Security BOM 7.1.1 (which Boot 4.1.1 imports); it is **not** in the local Gradle cache yet, so the first build needs network.
2. *Where Keycloak `sub` values are stored* — four columns, all opaque UUID strings: `gamecatalog.game_mark.user_subject`, `gamecatalog.refresh_run.started_by`, `settings.ai_feature_model_setting.updated_by_subject`, `settings.guest_chat_usage.user_subject`. No `users` table exists; identity lives only in Keycloak.
3. *Places reading roles from claims* — the converter in `shared/config/SecurityConfig.java` (`realm_access.roles` → `ROLE_*`) plus three `"ROLE_admin"` string checks (`LibBotController`, `GuestChatQuotaController`, `GuestChatLimitFilter`). Authorization is one deny-by-default URL matrix; no `@PreAuthorize` exists.
4. *What the `jordylab-backend` service account does* — powers `settings/rest/client/KeycloakAdminClient.java` only (list/enable/disable users, grant/revoke roles, reset password, end sessions, list offline sessions, delete users). It disappears with Keycloak.

**Fresh-base deltas (specs 014, 017, 018 merged after the first draft of this plan)**:

- **018 removed the `mobile` module entirely** — no `MobileReleaseController`, no `/api/mobile/**`, no `mobile-release-publisher` role, no `mobile-release-ci` client, no APK job, no `assetlinks.json`. `NtfyClient` moved to `shared/rest/client` (`jordylab.notifications.*`); `UserSignUpPending`/`BriefingReady` events were deleted and `PendingSignupWatcherService` (a 5-minute Keycloak poller) calls ntfy directly. **Consequence**: the machine-token design has a single `INGEST` scope — the planned `MOBILE_RELEASE_PUBLISH` scope is dropped.
- **014 extended user management** (implemented, spec has 117/120 tasks done): per-app grants (`app-gamecatalog`, admin is composite), approval default grants (`ManagedApp.defaultGrants()`), guest chat limits (`settings.guest_chat_limit`, default + personal rows, `JORDYLAB_GUEST_CHAT_DAILY_LIMIT` 20), session listing/ending (`UserSessionService`, WEB/SCANNER), staged deletion (`UserDeletionService`, refuses self/admin), last-admin guard, 60-second access-token lifetime, events `UserAccessRemoved`/`AppAccessRemoved`/`UserDeleted` in `shared.event`. **Consequence**: the identity module must preserve all of it (D17) — the original 019 scope is not sufficient.
- **017 (Compose deployment) is Draft with no tasks**; production is still k8s, and 017 explicitly keeps Keycloak until this feature. The removal checklist stays k8s-oriented with a note.
- **e2e** is still Postgres+Keycloak with Keycloak-page automation (`session.ts`), Keycloak registration (`guest.ts`), and `make-realm.py`; the Android flow is gone; `pwa.spec.ts` exists.
- Frontend: 7 environment files (jordylab dev/prod/e2e, fna dev/prod, gamecatalog dev/prod), `hasGameCatalogAccess` signal in `AuthService`, `pkce.ts` already dead code, user menu with Keycloak AIA actions (change password/name/email).

---

## D1 — Authentication mechanism: Spring Security username/password sessions + WebAuthn passkeys

**Decision**: Replace the OAuth2 resource server with Spring Security 7.1.1's standard stack: a JSON login endpoint backed by `AuthenticationManager`, server-side HTTP sessions, BCrypt password encoding, and **Spring Security's WebAuthn support** (`spring-security-webauthn` 7.1.1) for passkeys.

**Rationale**: Owner decisions 2026-11-02 + 2026-11-03. Spring Security's own password encoding satisfies FR-002; its WebAuthn module provides the ceremony endpoints, credential repository and user-details contract the spec needs without a third-party library.

**Alternatives considered**: passkey-only (the draft's original direction — rejected by the owner's password decision); Keycloak (the feature removes it); simplewebauthn/webauthn4j (extra dependency and hand-rolled ceremony wiring for no gain over the first-party module).

## D2 — Password hashing and policy

**Decision**: BCrypt via Spring Security's `PasswordEncoder` (`{bcrypt}`, strength 10). Policy: minimum length 12 and not equal to the username (today's realm policy `length(12) and notUsername and notEmail` — email is moot now), configurable under `jordylab.identity.password.*`.

**Rationale**: No extra dependency (Argon2 would add BouncyCastle); matches what users have today.

**Alternatives considered**: Argon2id (stronger, extra dependency); per-user salt columns (BCrypt embeds the salt).

## D3 — Session store: Spring Session JDBC in the `identity` schema

**Decision**: Spring Session JDBC 4.1.1 (Boot-BOM-managed) with `identity.spring_session` + `identity.spring_session_attributes` created by Flyway (auto-DDL off), so restarts and rolling deploys keep everyone signed in (SC-003) and admin session-listing/ending works off the same store (`FindByIndexNameSessionRepository`). Idle timeout 30 days with a persistent cookie, matching the previous Keycloak `ssoSessionIdleTimeout`.

**Rationale**: Postgres is the only shared store; in-memory sessions die on deploy (regression vs JWT); Redis adds infrastructure for a handful of users. 014's session management becomes trivial once sessions are rows we own.

**Alternatives considered**: in-memory (dies on deploy); Redis (new infra); stateless signed cookies (no real logout/revocation — contradicts SC-005).

**To verify during implementation**: the exact property for a schema-qualified Spring Session table prefix (`spring.session.jdbc.table-name-prefix: identity.`); if the version cannot schema-qualify, fall back to a custom schema DDL — tables stay in the `identity` schema either way.

## D4 — CSRF and CORS

**Decision**: CSRF on the session chain via `CookieCsrfTokenRepository` (HttpOnly off) + XOR request-attribute handling, cookie `XSRF-TOKEN` / header `X-XSRF-TOKEN` — Angular's built-in names (the frontend already ships the default XSRF interceptor and same-origin `/api/**` calls, so no frontend wiring). `GET /api/identity/auth/csrf` forces the deferred token so the cookie exists before the first POST. The machine-token chain is stateless, CSRF off. CORS is removed entirely (same-origin PWA after 018).

**Rationale**: zero frontend XSRF work; matches the documented Spring Security SPA pattern.

**Alternatives considered**: keeping CORS (nothing is cross-origin anymore); CSRF off (rejected — FR-007).

## D5 — Two security filter chains, owned by the `identity` module

**Decision**: Delete `shared/config/SecurityConfig.java`; create `IdentitySecurityConfiguration` in the `identity` root package with two ordered chains:

1. **Machine-token chain** (order 1, `securityMatcher` on `/api/gamecatalog/ingest/check` and `/api/gamecatalog/ingest/scan`): bearer `jlmt_…` lookup → `ROLE_gamecatalog-scanner`. Stateless, CSRF off.
2. **Session chain** (order 2, catch-all): session authentication, CSRF on, today's URL matrix with the *current* rules — `hasAnyRole("admin","app-gamecatalog")` for catalog reads/marks/LibBot, `hasRole("admin")` for fna/settings/identity administration, `/webauthn/authenticate` permitAll, `/webauthn/register` authenticated, deny-by-default — plus Spring Security's WebAuthn configurer (`http.webAuthn(...)`) with JSON success/failure handlers that return the signed-in user instead of redirecting.

Controller-based login applies the session-fixation strategy; `PENDING` users sign in with no authority (frontend → `/awaiting-approval`).

**Rationale**: FR-008 keeps the two modes; the URL matrix is the tested pattern (`RoleMatrixTest`). Identity owning the chains is cleaner than `shared` importing identity internals.

**Alternatives considered**: one chain with multiple entry points (CSRF/session semantics differ); moving the matrix to `@PreAuthorize` (rewrite for no gain).

## D6 — New `identity` module, public API, moved events

**Decision**: New top-level `dev.jordy.jordylab.identity` module with the canonical package layout and the `identity` schema. Root public API: `AuthenticatedUser(UUID id, String username, String role, Set<ManagedApp> appGrants)`, `CurrentUserResolver` (`Optional<AuthenticatedUser> currentUser()`), `ManagedApp` (`GAME_CATALOG` grantable; `FNA`/`SETTINGS` never grantable), and the events `UserAccessRemoved`, `AppAccessRemoved`, `UserDeleted` (moved from `shared.event`; consumers: `gamecatalog`'s `MarkCleanupListener`, and new `settings` listeners for personal-limit cleanup). Every module switches from `@AuthenticationPrincipal Jwt` to `CurrentUserResolver`; ids stay identical (D7).

`PendingSignupWatcherService` is deleted: registration calls `NtfyClient` directly (shared named interface `notifications`), exactly like `BriefingGeneratorService` does — no event, no poller.

**Rationale**: FR-009 (extraction-ready module); the id-stability trick removes all FK work; registration-time notification is strictly better than polling.

**Alternatives considered**: identity inside `settings` (rejected — extraction story); exposing Spring Security principals directly (couples every module to the framework).

## D7 — User model: stored status, role, separate app grants, id = Keycloak sub

**Decision**: `identity.user_account`: `id UUID` (migrated users keep the Keycloak `sub`; new users random UUIDs), unique lowercase `username`, optional `display_name`, nullable `password_hash` (null only until a migrated user's first reset), `role` (`admin`/`guest`/null), `status` (`PENDING`/`APPROVED`/`REJECTED`/`REVOKED`), lockout fields. **App grants** in `identity.user_app_grant` (`user_account_id`, `app` enum) as an `@ElementCollection` on `UserAccount` (`grantApp`/`revokeApp` register `AppAccessRemoved`). Authorities from a session: `admin` → `ROLE_admin`; `guest` → `ROLE_guest`; grants → `ROLE_app-gamecatalog`; `PENDING` → none; `REJECTED`/`REVOKED` cannot sign in.

Approval assigns `guest` + `ManagedApp.defaultGrants()` (Game Catalog), matching 014. The last-approved-admin guard (reject/revoke/delete) carries over. Status is stored, not derived from a remote directory.

**Rationale**: the current model's semantics survive without Keycloak; grants as a collection keep the aggregate in one entity (canonical entity rules).

**Alternatives considered**: keeping grants inside a JSON column (opaque, no FK data hygiene); a separate aggregate with its own repository (extra ceremony for a set of enums).

## D8 — Machine tokens (scanner only)

**Decision**: `identity.machine_token`: `name`, `token_hash` (SHA-256), **single scope `INGEST`**, `created_by`, `created_at`, `last_used_at` (throttled ≤1/min), `revoked_at`. Token format `jlmt_<base64url(32 random bytes)>`, shown once. The scan client (`gamecatalog-scanner/src/jordylab_scan/auth.py`, frozen template rendered by `ClientService`) drops the Keycloak device flow: token from `JORDYLAB_SCAN_TOKEN` or a prompt, cached at the unchanged `~/.config/jordylab/scan/token.json` (0600), sent as a bearer on `/check` and `/scan`. `tools/build_client.py` regenerates the template; `jordylab.script.keycloak-url` config disappears.

**Rationale**: FR-006; after 018 the scanner is the only non-interactive caller (the APK CI job and its client are gone).

**Alternatives considered**: keeping a second scope for a hypothetical future CI caller (YAGNI — the deferred `PathPrefix` route can be added when a caller exists); bcrypt for tokens (wasteful for 256-bit random values).

## D9 — Bootstrap admin, brute-force lockout, break-glass

**Decision**: `jordylab.identity.bootstrap.username/password` creates the first admin (status `APPROVED`, role `admin`) **only when `user_account` is empty**, otherwise logs and ignores. Local dev: literal `jordy` admin; prod: SOPS `IDENTITY_BOOTSTRAP_ADMIN_*` env; e2e: per-run generated password. Lockout: 5 consecutive failures → 15-minute lock, counters on `user_account`. Break-glass: runbook section — BCrypt one-liner (`htpasswd -bnBC 10 "" '…' | tr -d ':\n'`) + `UPDATE identity.user_account SET password_hash = …` against CNPG; operator-only, no code path.

**Rationale**: the prod realm imports no person users, so first-boot bootstrap is required (Keycloak's own pattern); lockout is the brute-force protection the spec assumes; SQL is the least code for a once-ever procedure.

**Alternatives considered**: seeding via Flyway (password in a migration — rejected); a CLI reset profile (more code to maintain); no lockout (unwise on the public internet).

## D10 — Cutover: SQL migration from the `keycloak` schema, two releases

**Decision**: **Release A** — a guarded SQL migration copies person users from the still-present `keycloak` schema: `id = user_entity.id`, `username = LOWER(user_entity.username)`, display name concatenated, `role` from realm-role membership (`admin` beats `guest`, else null), grants from `app-gamecatalog` membership, status `APPROVED`, `password_hash` **null** (Keycloak's hash format is not Spring's; the reset flow covers first sign-in). Service accounts are skipped. Guard: `DO $$ … IF EXISTS (schema 'keycloak') … ELSE RAISE NOTICE …` so fresh/test databases no-op. Keycloak keeps running but unused. **Window**: verify sign-ins, take the verified CNPG backup. **Release B** — drop the `keycloak` schema and land the removal checklist (D14).

**Rationale**: never migrate hashes at this scale; two releases keep a rollback path; the guard keeps test databases working.

**Alternatives considered**: migrating hashes (format mismatch, fragile); one big-bang release (no rollback window; spec requires backup-then-remove).

**To verify during implementation**: exact Keycloak 26.7 table/column names (`user_entity`, `keycloak_role`, `user_role_mapping`) against the live dev database with read-only `psql`.

## D11 — E2E without Keycloak

**Decision**: Throwaway stack becomes Postgres-only (`compose.e2e.yaml` drops Keycloak; `make-realm.py` and the Keycloak readiness/env plumbing are deleted). `run.sh` generates `E2E_ADMIN_PASSWORD` for the bootstrap admin and starts with `SPRING_PROFILES_ACTIVE=local,e2e`. Journeys sign in through the app's real Angular login form (replacing the Keycloak-page automation in `session.ts`); `guest.ts` registers through the app's `/register` form and the admin approves at `/settings/users` (preserving the existing journey); `global-setup.ts` creates an `INGEST` machine token through the admin API for catalog setup. A **passkey journey** uses Playwright's CDP virtual authenticator (`WebAuthn.enable` + `addVirtualAuthenticator`, CTAP2, internal transport, user verification) to enroll and sign in. FR-013's profile-gated test sign-in (`POST /api/identity/test/sign-in`, `@Profile("e2e")`) stays for fast storageState setup, with a prod-profile test asserting 404.

**Rationale**: passwords and WebAuthn are trivially automatable; the virtual authenticator makes the fingerprint journey genuinely covered, not mocked.

**Alternatives considered**: real-form-only with no test endpoint (cleaner, discards FR-013's explicit mechanism); keeping a test-only Keycloak (defeats the feature).

## D12 — Frontend: session `AuthService`, real forms, passkey UI, no `keycloak-js`

**Decision**: Remove `keycloak-js`; rewrite `libs/shared/auth` in place: session `AuthService` (`GET /api/identity/me` on boot, `login(username, password)`, `logout()`, `changePassword()`, `updateDisplayName()`, roles/grants from `/me`, keep `isAdmin`/`isGuest`/`hasGameCatalogAccess`), delete the bearer interceptor, `pkce.ts` and the silent-SSO page, real `/login`, `/register`, `/forgot-password` forms (no email field; display name optional), a **passkey sign-in action** on the login page (username-first) and a **Passkeys card** in Settings → App (list/add/remove) using a small WebAuthn helper (`navigator.credentials.create/get` + base64url conversion). `UserMenuComponent` loses the Keycloak AIA actions: change password and edit display name become app dialogs. Users page stays in `libs/settings` (calls `/api/identity/users`, gains grant toggles, session panel, delete), machines page joins it. Guards (`authGuard`, `roleGuard`, `/no-apps`, `/awaiting-approval`) keep their semantics.

**Rationale**: smallest surface satisfying the spec; guard/role logic is already auth-agnostic; keeping user admin in the settings area matches the owner's mental model.

**Alternatives considered**: a new `libs/identity` frontend domain (churn without benefit); moving login into `apps/jordylab` (harness apps reuse it).

## D13 — Precondition: the PWA (018) must land first — now met

**Decision**: This feature builds on 018, which is merged: no Capacitor, no WebView origin, no biometric/App-Link login, no Android e2e. The PWA runs on the platform origin, so first-party cookies and WebAuthn work on the phone without cross-origin workarounds.

**Rationale**: 018 removed the exact complication the draft flagged; confirmed in the current tree.

## D14 — Removal checklist (release B), verified against the current tree

**Decision**: Remove, in release B, after the verified backup:

- **Backend**: `spring-boot-starter-oauth2-resource-server`; `issuer-uri` and `jordylab.settings.keycloak.*` / `jordylab.script.keycloak-url` config; `KeycloakAdminClient`, `KeycloakAdminProperties`, `KeycloakUnavailableException`, `KeycloakUserAdministrationService`, `PendingSignupWatcherService`, `AppUser`/`UserStatus`; the `keycloak` schema (migration). Replaced by the identity module (D1–D8, D17).
- **Tests**: `KeycloakIntegrationTest` and subclasses (`RoleMatrixTest`, `UserManagementIntegrationTest`, `KeycloakSessionVerificationIntegrationTest`), `KeycloakAdminClientRealmExportIntegrationTest`, `RealmConfigurationTest`, `ProdKeycloakUrlConfigurationTest`, the test realm fixture — replaced by native integration tests (one fewer Testcontainers image per JVM).
- **Compose (local)**: the `keycloak` service, `compose/keycloak-init.sql`, `compose/keycloak-realm-export.json`, `compose/keycloak-theme/`; `KEYCLOAK_*` in `.env.example`.
- **Deploy (k8s today)**: `deploy/containers/keycloak/`, `deploy/k8s/base/keycloak.yaml`, the `keycloak-route` in `gateway.yaml`, `keycloak-config` ConfigMap, `jordylab-db-keycloak` Secret, the `keycloak` role/schema/bootstrap SQL in `cnpg-cluster.yaml`, `KEYCLOAK_*` env in `backend.yaml`/`backend-config`, SOPS keys `KEYCLOAK_ADMIN_BOOTSTRAP_*`/`KEYCLOAK_BACKEND_CLIENT_SECRET` (replaced by `IDENTITY_BOOTSTRAP_ADMIN_*`). **If spec 017's Compose deployment has landed instead, remove the equivalent Keycloak service/Caddy route/env from `deploy/compose/` and the 017 workflow.**
- **CI**: the `keycloak` entries in `build.yml` build matrix, `release.yml` retag matrix, `deploy-prod.yml` image set + `rollout status deploy/keycloak` (or the Compose equivalent).
- **Frontend/e2e**: everything in D11/D12, plus `environment.e2e.ts`'s `window.__JORDYLAB_E2E__` injection and the `keycloak*` fields in all seven environment files, `silent-check-sso.*`, `pre-bootstrap-auth.ts`, `pkce.ts`, the Keycloak references in `pwa.spec.ts`/`csp.spec.ts`/`accessibility.spec.ts`/`tools/app-boot-check.mjs` (if present).
- **Docs**: root/`jordylab-be`/`jordylab-fe` `AGENTS.md`, `README.md`, `docs/runbook.md` (§11/§13/§15 + the one-time APK-stack section), `docs/environments.md`, `docs/learn/*`, `.claude/skills/jordylab-ops/SKILL.md`, the devops agent descriptions. Historical campaign docs (`docs/testing/*`) and old specs keep their mentions.

## D15 — Password reset flow details

**Decision**: A signed-out user requests a reset with `{username, newPassword}`; the endpoint always answers identically (no enumeration; unknown usernames create nothing) and stores only the **hash** of the chosen password on `identity.password_reset_request` (one `PENDING` per user via a partial unique index). The request appears in the Users page; approval copies the hash onto the account and closes the request (old password dies immediately); rejection keeps it. Own-password change or admin direct reset supersedes a pending request. Admin direct reset keeps today's semantics: a generated one-time temporary password shown once (`TemporaryPasswordGenerator` moved to `identity/util`).

**Rationale**: the owner's flow, completed without email or links; the admin never sees the user's chosen password.

**Alternatives considered**: admin-set temporary password as the only mechanism (admin learns every password); email links (rejected).

## D16 — Passkeys via Spring Security's WebAuthn module

**Decision**: Add `org.springframework.security:spring-security-webauthn` (7.1.1, BOM-managed; first build downloads it) and configure `http.webAuthn(...)` on the session chain:

- **Relying party**: id `jordylab.be` (prod) / `localhost` (dev, e2e); name `JordyLab`; allowed origins `https://jordylab.be` / `http://localhost:<port>`; properties under `jordylab.identity.webauthn.*`.
- **User details**: `IdentityUserDetails implements UserDetails, WebAuthnUserDetails` — username, password hash, authorities from role+grants, `userHandle = UUID bytes`, `getCredentials()` from `JdbcUserCredentialRepository`; `REJECTED`/`REVOKED` map to disabled so the WebAuthn flow refuses them; `PENDING` → enabled, no authorities.
- **Storage**: Spring Security's `JdbcUserCredentialRepository` against `identity.user_credentials` (exact DDL taken from the module's `user-credentials.sql` and created by Flyway).
- **Endpoints**: Spring Security's `/webauthn/authenticate` (permitAll; username-first: the SPA posts the username from the login form, receives request options, asserts with the platform authenticator) and `/webauthn/register` (authenticated; adds a credential). JSON success/failure handlers return the user payload and keep the session flow identical to password login (fixation protection included).
- **Management API**: `GET /api/identity/passkeys` and `DELETE /api/identity/passkeys/{credentialId}` (own credentials only) wrap the repository for the Settings card.
- **Enrollment UX**: Settings → App "Passkeys" card (list/add/remove) + a "Sign in with a passkey" action on the login form (requires the username field).
- **Testing**: Java tests cover the user-details adapter, status mapping, options-endpoint authorization, credential management and CSRF. The real ceremony is covered in e2e with Playwright's CDP virtual authenticator (CTAP2, internal, user-verified) — no fake ceremony in Java.
- **Recovery**: passwords remain; losing every passkey is recoverable; the break-glass and reset flows are untouched by passkeys.

**Rationale**: first-party module with the ceremony logic, JDBC credential storage and the `WebAuthnUserDetails` contract; the phone's fingerprint/face arrives through the platform authenticator of the installed PWA, exactly the expectation recorded on 2026-11-03.

**Alternatives considered**: simplewebauthn/webauthn4j (extra dependency, hand-wired ceremonies); passkey-only accounts (rejected — password recovery stays); usernameless/discoverable flows (deferred — username-first keeps the Spring support simple and the login form unchanged in shape).

**Risks**: the WebAuthn filter's endpoint paths and JSON contract must be verified against the 7.1.1 docs during implementation; RP-id changes invalidate passkeys (documented, recoverable); first build needs the artifact download.

## D17 — Preserve spec 014's user-management semantics without Keycloak

**Decision**: The identity module absorbs everything today's Keycloak-backed settings stack does, mapped as follows:

| Today (Keycloak admin API) | New (identity module) |
|---|---|
| Status derived from enabled + roles | Stored `status`; same visible states |
| Approve = enable + `guest` + default grants | `status=APPROVED`, `role=guest`, `ManagedApp.defaultGrants()` (Game Catalog) |
| Grant/revoke `app-gamecatalog` | `UserAccount.grantApp/revokeApp` → `ROLE_app-gamecatalog`, `AppAccessRemoved` event |
| Revoke = remove roles/guest, end sessions, clear personal limit, `UserAccessRemoved` | Same, with sessions deleted from Spring Session and the limit cleared by a **new settings listener** on `UserAccessRemoved` |
| Reset password (temporary, shown once) | Same (`TemporaryPasswordGenerator` moved) |
| Session listing/ending (WEB + SCANNER offline) | WEB sessions from `FindByIndexNameSessionRepository`; SCANNER sessions become machine tokens (their own page with last-used/revoke) |
| Deletion staged DISABLE→END_SESSIONS→LOCAL_CLEANUP→DELETE_ACCOUNT, refuses self/admin, events | Same observable behavior, now a transactional identity operation; `UserDeleted` + `UserAccessRemoved` published; settings clears usage/limit on the event |
| 60-second token lifetime as the revocation bound | **Immediate**: revoke/delete deletes the sessions (SC-005) |
| `PendingSignupWatcherService` polling Keycloak every 5 min → ntfy | Registration calls `NtfyClient` directly |
| Last-admin guard | Same, codified on `UserAccountService` |

Guest chat limits themselves stay in `settings` (table, service, UI) — only the cleanup wiring moves to events, which also keeps `ModularityTests` happy (no identity→settings import).

**Rationale**: parity with 014 is a launch requirement; event-driven cleanup is the existing pattern (`MarkCleanupListener`).

**Alternatives considered**: identity calling settings internals (module cycle); leaving personal limits behind on revoke (data hygiene regression).

---

## Exploration summary (evidence base, current tree)

**Backend**: one `SecurityFilterChain` in `shared/config/SecurityConfig.java` (resource server, `realm_access.roles` → `ROLE_*`; audit generated tokens: URL matrix with `admin`/`app-gamecatalog`/`gamecatalog-scanner`, deny-by-default; CSRF off; no sessions); identity = JWT `sub` consumed in ~9 controllers/filters; owner columns in 4 tables; settings user admin via hand-rolled `KeycloakAdminClient` (service account, admin REST) with grants, limits, sessions, staged deletion, last-admin guard, temporary passwords; events in `shared.event`; ingest = JWT device-grant token, machine identity from the request body; Spring Boot 4.1.1 / Spring Security 7.1.1 / Modulith 2.1.1; `spring-boot-starter-security` present; `spring-session-*` and `spring-security-webauthn` absent from the cache; Flyway SQL per module schema; `RoleMatrixTest` is the executable access matrix; Keycloak integration tests boot a real Keycloak container.

**Frontend**: `keycloak-js` (PKCE, silent check-sso) wrapped by `libs/shared/auth` (guards, interceptor, single-button login, Keycloak AIA user menu); `hasGameCatalogAccess` from `app-gamecatalog`; 7 environment files; same-origin relative `/api/**`; Angular's default XSRF interceptor active; no Capacitor/mobile code; PWA manifest + installability service worker; `pkce.ts` dead code.

**E2E**: `e2e/run.sh` starts Postgres + Keycloak, imports a generated realm (`make-realm.py`), builds the e2e web app; journeys automate Keycloak's login/registration pages; `global-setup.ts` uses a `client_credentials` ingest client; `pwa.spec.ts` covers the manifest/share target; `e2e-web` is the required CI check.

**Deploy/CI**: production is still k3s (017 Compose is a draft without tasks); Keycloak 26.7.4 image, `/auth` HTTPRoute, `keycloak-config` ConfigMap, SOPS secrets (names only), CNPG keycloak role/schema, Barman backups of the whole DB; Flyway on backend startup; `deploy-prod.yml` sets the keycloak image and waits for `deploy/keycloak`; `build.yml`/`release.yml` matrix includes `keycloak`; no APK job (removed by 018); local compose still runs Keycloak on 8180 with the dev realm and theme.
