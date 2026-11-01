# Feature Draft: Authentication inside the backend, without Keycloak

**Status**: Draft, direction leaning (Jordy: "maybe we should"; extracted apps will have their own separate accounts)
**Target path**: `specs/_drafts/auth-without-keycloak/spec.md`
**Amends**: the settings spec (users, AI models), the game catalog scanner spec (device-code login), the OVH k3s deployment spec (Keycloak in prod)
**Depends on**: `pwa-replaces-capacitor` first (the Capacitor WebView origin complicates cookie sessions). Interacts with `continuous-deploy` (sessions must survive deploys).

> Written without repo access. The Keycloak inventory comes from `jordylab-infrastructure-guide.md`. Never hand-roll password storage: this draft uses no passwords at all.

## Problem

Keycloak is a second JVM, a realm file, a schema in the shared Postgres, a public `/auth` route and four clients to maintain for a handful of users. Extracted apps will have their own accounts, so a shared identity provider buys no cross-app sign-in. Spring Security 7 (shipped with Boot 4) includes passkey (WebAuthn) support with JDBC persistence and one-time-token login, which cover this audience.

## What exists today to replace

Realm `jordylab` with clients `jordylab-host`, `gamecatalog-script` (device authorization grant, offline token), `jordylab-backend` (service account), `mobile-release-ci`. Roles `admin`, `guest`, `gamecatalog-scanner`. Self-registration with admin approval (settings module). JWT resource server in the backend. Keycloak tables in the `keycloak` schema. Prod realm in `deploy/keycloak/realm-prod.json`. Local compose runs Keycloak on port 8180.

## Requirements

- Users sign in with passkeys. No passwords exist anywhere in the system.
- Enrollment and recovery use single-use, short-lived invite or sign-in links created by an admin. Delivery is the admin copying the link (or sending it via ntfy), unless the open question on invite delivery chooses email.
- Self-registration with admin approval and the roles `admin` and `guest` keep working as in the settings spec.
- The scanner authenticates with a per-machine token created in the UI ("Add machine"), shown once, stored hashed, scoped to ingestion, revocable, with last-used time. The token has at least 256 bits of randomness. The existing cache path `~/.config/jordylab/scan/token.json` (mode 0600) stays.
- Browser sessions are server-side and stored in the database (or equivalent), so deploys do not log anyone out. Cookies are HttpOnly with SameSite set and CSRF protection works with the Angular XSRF mechanism.
- Two filter chains: session-based for the web app, bearer-token for the scanner ingest endpoints. Existing `@PreAuthorize` rules keep working on the same authority names.
- The identity code lives in its own module with its own schema and a narrow public API (a `CurrentUser` abstraction), so extracting an app later means taking that module along and starting its own user store.
- Break-glass: a documented operator-only procedure creates an admin sign-in link from the cluster or database, so losing the only passkey is recoverable. Users are encouraged to register two passkeys.
- Cutover preserves user identities. If other modules store the Keycloak `sub` as owner id, the new user table keeps it as a legacy external id so no foreign keys change.
- Removal checklist after cutover and a verified backup: Keycloak deployment and `/auth` route, realm files, the `keycloak` schema, compose service, `KC_*` variables, issuer-uri config, frontend OIDC code.
- E2E setup stops depending on Keycloak. `e2e-web` currently starts throwaway Postgres + Keycloak. Replace it with a test-only sign-in available only under a dedicated profile, plus a test that proves it is absent in the prod configuration.

## Success criteria

- An admin invites a friend, the friend registers a passkey on Android and on a desktop browser, and the admin approves them, all without Keycloak running.
- A deploy during an active session does not sign the user out.
- The scanner on JordyBox and on the Mac ingests with a new token, and revoking the token stops it at the next call.
- After removal, production runs one fewer container and the repository has no Keycloak references outside docs history.

## Out of scope

Password login, single sign-on across apps, MFA beyond passkeys, social login (can follow later, Spring Security supports OAuth2 login), federation.

## Open questions

1. Magic links need email delivery and JordyLab has none. Is invite-link copy/paste enough, or add an SMTP provider?
2. Own module, or inside `settings`? A separate `identity` module fits extraction better, `settings` is less work.
3. Passkeys need a stable relying party id (`jordylab.be`; `localhost` in dev). Moving the production domain later would invalidate them.
4. What does the `jordylab-backend` service account do (likely calling Keycloak's admin API for user management)? That code disappears with Keycloak.
5. Which passkey store does the Android phone use, and does the recovery story hold if that account is lost?

## Facts to verify in the repo

- Whether the build already has `spring-security-webauthn` available for the Spring Security version Boot 4.1.1 brings (the docs show the dependency differs by version).
- Where Keycloak `sub` values are stored in other modules.
- All places that read roles from JWT claims.
