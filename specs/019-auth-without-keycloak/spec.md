# Feature Specification: Authentication Inside the Backend Without Keycloak

**Feature Branch**: `019-auth-without-keycloak`

**Created**: 2026-11-02 · **Updated**: 2026-11-03

**Status**: Draft

**Module**: `identity` (new)

**Amends**: 003 (gamecatalog scanner device-code login), 006 (settings — registration, roles), 008 (prod Keycloak deployment and the public `/auth` route), 012 (the `e2e-web` CI check's Keycloak dependency), 014 (user management — app grants, guest limits, sessions, deletion)

**Builds on**: 018 (the PWA replaced Capacitor — the app runs on the platform origin, so cookie sessions and passkeys work everywhere; merged)

**Reference**: Linear `JOR-30`

**Input**: User description: "Keycloak is a second JVM, a realm file, a schema in the shared Postgres, a public `/auth` route and four clients to maintain for a handful of users. Extracted apps will have their own accounts, so a shared identity provider buys no cross-app sign-in. Spring Security 7 includes passkey (WebAuthn) support with JDBC persistence and one-time-token login, which cover this audience." — Linear JOR-30, from the draft in `specs/_drafts/auth-without-keycloak/`. The owner has since decided: **username + password stay the base** (2026-11-02), and **passkey sign-in returns for fingerprint/face** (2026-11-03, recorded in the draft).

---

## Clarifications

### Session 2026-11-02

- Q: How should invite and recovery links be delivered (admin copy/paste, ntfy, or email via a new SMTP provider)? → A: No invite or recovery links at all. Keep the current behaviour: sign in with username and password, no email required; a password reset request shows up in the user-management module (settings), where the admin approves it.

### Session 2026-11-03

- Q: The draft now records the owner's expectation that fingerprint sign-in returns with this feature, while the approved spec kept passkeys out of scope. Which scope? → A: **Passwords + passkeys.** Keep username/password as the base (registration, admin-approved resets, break-glass) and add passkey enrollment and passkey sign-in so the phone's fingerprint/face signs in through a passkey.
- The PWA dependency (018) is now merged, so this feature builds on a same-origin web app with no WebView/Capacitor code.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Register with username and password, and sign in once approved (Priority: P1)

As a new user, I can register an account choosing my own username and password — no email address needed — and once the admin approves me I sign in with that username and password on any device, so the platform authenticates its users natively without Keycloak.

**Why this priority**: This is the minimum that replaces Keycloak. If only this story is implemented, the platform authenticates its users with its own accounts.

**Independent Test**: With Keycloak not running: register a fresh account with just a username and password, approve it in settings, and sign in with those credentials ([quickstart.md](quickstart.md) §1).

**Acceptance Scenarios**:

1. **Given** the sign-in page, **When** a new user registers with a username and a password, **Then** the account is created in a pending state and no email address is requested or required.
2. **Given** a pending registration, **When** the admin approves it, **Then** the user receives the `guest` role and the default app access (Game Catalog) as today, and can sign in with their username and password.
3. **Given** a registration whose username is already taken, **When** it is submitted, **Then** it is rejected with a clear message.
4. **Given** wrong credentials, **When** sign-in is attempted, **Then** it is refused with a clear message, without revealing whether the username or the password was wrong.
5. **Given** repeated failed sign-in attempts for one account, **When** they continue, **Then** the system slows or refuses them (basic brute-force protection).
6. **Given** any password, **When** it is stored, **Then** it is kept only as a modern one-way hash — never in plaintext and never reversibly encrypted.
7. **Given** a new pending sign-up, **When** it is created, **Then** the admin receives an ntfy notification (parity with today's pending-signup watcher) and the pending count in the UI increases.

---

### User Story 2 - Sign in with a passkey (fingerprint/face) (Priority: P1)

As a signed-in user, I can add a passkey on my phone or browser; afterwards I sign in by choosing my passkey and confirming with my fingerprint or face instead of typing my password — and if the passkey is unavailable, my password still works.

**Why this priority**: The owner's recorded expectation for this feature (2026-11-03): the phone's fingerprint sign-in, lost with the native app, comes back through passkeys.

**Independent Test**: Add a passkey in Settings, sign out, sign back in with the passkey (fingerprint/face on the phone, platform authenticator in a desktop browser) without typing the password; then remove the passkey and confirm password sign-in still works ([quickstart.md](quickstart.md) §2).

**Acceptance Scenarios**:

1. **Given** a signed-in user, **When** they add a passkey, **Then** the device's fingerprint/face/platform-authenticator prompt completes and the passkey appears in their passkey list.
2. **Given** a user with a passkey, **When** they choose "Sign in with a passkey", **Then** the browser asks for the passkey, the fingerprint/face confirmation completes, and they are signed in with the same session and authorities a password sign-in would grant.
3. **Given** a user with no passkeys, **When** they try passkey sign-in, **Then** they see a clear message that no passkey exists for the account (and can use the password).
4. **Given** a user with several passkeys, **When** they view Settings, **Then** they can add another and remove each existing one.
5. **Given** a pending account with a passkey, **When** it signs in with the passkey, **Then** it gets a role-less session exactly like password sign-in; rejected/revoked accounts are refused.
6. **Given** any passkey ceremony, **When** it runs, **Then** biometric data never leaves the device — only public-key credential material is stored server-side.

---

### User Story 3 - Stay signed in across deploys, safely (Priority: P1)

As a signed-in user, I stay signed in when the backend restarts or is redeployed, my session cookie is protected against cross-site abuse, and signing out actually signs me out, so native sessions are at least as good as the tokens they replace.

**Why this priority**: The current JWTs survive deploys by nature; sessions that die on every deploy would be a regression.

**Independent Test**: Sign in, restart the backend, still signed in; POST without the CSRF token → rejected; sign out → the old cookie is refused ([quickstart.md](quickstart.md) §3).

**Acceptance Scenarios**:

1. **Given** an active session, **When** the backend restarts or a deploy rolls out, **Then** the user remains signed in — sessions live server-side, outside any single backend instance's memory.
2. **Given** a signed-in user, **When** a cross-site request arrives carrying the session cookie without the app's CSRF token, **Then** the request is rejected.
3. **Given** the session cookie, **When** it is issued, **Then** it is HttpOnly, carries a SameSite policy, and cooperates with the frontend's built-in XSRF mechanism.
4. **Given** a signed-in user, **When** they sign out, **Then** the server-side session is invalidated and the cookie no longer grants access.

---

### User Story 4 - Admin manages app access, sessions, and deletion (Priority: P1)

As the admin, I keep the user management I have today: approve or reject sign-ups, grant or revoke Game Catalog access per person, see and end a user's sessions, delete a non-admin account with its data cleaned up, and never lose the last admin — all without Keycloak.

**Why this priority**: This is existing, working functionality (spec 014). Replacing Keycloak must not lose it.

**Independent Test**: Approve a user, verify the default Game Catalog grant, revoke it and confirm the app refuses the catalog, re-grant, list and end the user's session, then delete the account and confirm access and data are gone ([quickstart.md](quickstart.md) §4).

**Acceptance Scenarios**:

1. **Given** a pending account, **When** the admin approves it, **Then** it receives `guest` and the default app grants (Game Catalog), matching today's behavior.
2. **Given** an approved user, **When** the admin revokes a grant, **Then** that app's pages and API refuse the user immediately (sessions ended), and the marks/usage tied to them are cleaned up as today.
3. **Given** an approved user, **When** the admin views their sessions, **Then** the current web sessions appear and the admin can end one or all (their own account is refused).
4. **Given** a non-admin account, **When** the admin deletes it, **Then** the account, its sessions, guest usage and personal chat limit are removed and the `UserAccessRemoved`/`UserDeleted` cleanups run; a second delete is a no-op.
5. **Given** the last approved admin, **When** the admin tries to reject, revoke or delete that account, **Then** the action is refused with a clear message.
6. **Given** a revoked or deleted user, **When** any request arrives with their old session, **Then** it is refused on the next request — no time window.

---

### User Story 5 - Manage per-machine scanner tokens (Priority: P2)

As the admin, I can create a per-machine ingestion token in the UI ("Add machine"), see it exactly once, later revoke it, and see when each machine last used it, so the library scanner on JordyBox and on the Mac ingests without any user login and a retired machine can be cut off immediately.

**Why this priority**: The scanner is the remaining non-interactive client; it needs its own authentication once the Keycloak device flow disappears.

**Independent Test**: Add a machine, copy the one-time token into the scan client, run a scan, confirm ingestion succeeds; revoke the token and confirm the next call is refused ([quickstart.md](quickstart.md) §5).

**Acceptance Scenarios**:

1. **Given** an admin in the machine management UI, **When** they add a machine, **Then** the system generates a token with at least 256 bits of randomness, shows it exactly once, and stores only a hash of it.
2. **Given** the scan client with a machine token, **When** it calls the ingest endpoints, **Then** it is authenticated and limited to ingestion; any other endpoint refuses it.
3. **Given** a revoked machine token, **When** the scan client calls again, **Then** it is refused on that very next call.
4. **Given** one or more machines, **When** the admin views the machine list, **Then** each shows its last-used time and revocation state.
5. **Given** the existing scan client, **When** it stores its credentials, **Then** it keeps using its current cache location and `0600` permissions.

---

### User Story 6 - Recover a forgotten password through the admin (Priority: P2)

As a user who forgot their password, I can request a reset with just my username and a new password; the request appears in the admin's user management, and once approved my new password works — no email and no links anywhere. As the operator, if the admin loses their password, a documented break-glass procedure restores access.

**Why this priority**: Without recovery, forgotten passwords would lock users out permanently; admin-approved recovery is the no-email, no-link answer for this platform.

**Independent Test**: Request a reset, approve it in user management, sign in with the new password; rejection keeps the old one; the break-glass rehearsal restores a locked-out admin ([quickstart.md](quickstart.md) §6).

**Acceptance Scenarios**:

1. **Given** a user who cannot sign in, **When** they request a reset with their username and a new password, **Then** the request appears in user management with the username and request time.
2. **Given** a pending reset request, **When** the admin approves it, **Then** the new password becomes active, the old one stops working, and the user signs in.
3. **Given** a pending reset request, **When** the admin rejects it, **Then** the old password keeps working.
4. **Given** a signed-in user, **When** they change their own password (current one confirmed), **Then** the change applies and any pending reset request is cleared.
5. **Given** any user, **When** an admin sets a new password directly, **Then** a one-time temporary password is shown once and applies immediately.
6. **Given** a reset request for an unknown username, **When** it is submitted, **Then** the requester is not told whether the account exists.
7. **Given** the only admin has lost their password, **When** the operator follows the documented break-glass procedure, **Then** the admin's password is reset from the cluster or database and access is restored.

---

### User Story 7 - Cut over without breaking identities, then remove Keycloak (Priority: P3)

As the owner, I migrate the existing users with their identities and grants preserved, verify everything works, and then remove every Keycloak piece — container, route, realm files, schema, compose service, config, and frontend OIDC code — so the platform runs leaner.

**Why this priority**: The payoff lands once cutover and removal are done, after the new authentication is proven.

**Independent Test**: `e2e/run.sh web` green without Keycloak; release A deploys and every migrated user signs in (passkey or password); verified backup; release B leaves no Keycloak outside docs history ([quickstart.md](quickstart.md) §7–§8).

**Acceptance Scenarios**:

1. **Given** existing users in Keycloak, **When** cutover runs, **Then** each keeps their identity: the new user store carries the Keycloak subject as the id and the `app-gamecatalog` membership as a grant, so owner columns and grants keep working with no foreign-key changes.
2. **Given** the existing users, **When** cutover completes, **Then** every one can sign in — password reset if needed — and register a passkey.
3. **Given** a verified database backup, **When** removal runs per the checklist, **Then** the Keycloak deployment and `/auth` route, realm files, `keycloak` schema, compose service, `KC_*` variables, issuer-uri configuration, and frontend OIDC code are removed.
4. **Given** the e2e pipeline, **When** it runs after the change, **Then** it no longer starts Keycloak: journeys sign in through the app's own form (and a passkey journey uses a virtual authenticator), with a profile-gated test sign-in whose absence from production is proven by a test.
5. **Given** production after removal, **When** the platform runs, **Then** it runs one fewer container and the repository has no Keycloak references outside docs history.

### Edge Cases

- Registration with an already-used username, or a password below the minimum length (12, not equal to the username) → rejected with a clear message.
- Repeated failed sign-ins for one account → slowed or refused (basic brute-force protection); exact behavior set during planning.
- A reset request for an unknown username → no request reaches the queue; the reply is identical (no enumeration).
- A pending reset while the user changes their own password or an admin sets one directly → the newer change wins; the request is cleared.
- No approved admin remains → the documented break-glass procedure is the only way in.
- A passkey ceremony on a device without a platform authenticator → clear failure message; password sign-in still works.
- A user loses all passkeys → password sign-in remains; they remove the stale passkeys after signing in and add a new one.
- The production domain changes → enrolled passkeys stop working (they are bound to the relying party id); users sign in with the password and re-enroll (accepted risk, documented).
- A passkey sign-in attempt for a rejected/revoked account → refused like password sign-in; for a pending account → role-less session.
- A machine token is compromised or its machine retired → revoked; refused on the next call; never displayed again after creation.
- A deploy mid-journey → the in-flight step may fail and can be retried; completed sessions survive.
- The same user on several devices → independent sessions; sign out on one does not affect the others; passkeys are shared by device.
- A guest-role user goes through every flow → identical to an admin's, with role differences showing only in authorization.
- Revoking access while the user is signed in → their sessions end immediately (no token-lifetime window).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Users MUST sign in with a username and a password. The system MUST NOT require an email address for registration, sign-in, or recovery.
- **FR-002**: Passwords MUST be stored only as industry-standard one-way hashes — never in plaintext, never reversibly encrypted, never with hand-rolled custom hashing.
- **FR-003**: Self-registration with admin approval MUST keep working as extended by spec 014: approval assigns the `guest` role plus the default app grants (Game Catalog); the per-app grant model (`app-gamecatalog`) stays.
- **FR-004**: A user who cannot sign in MUST be able to request a password reset with just their username and a chosen new password; requests appear in user management for approval; approval activates it and invalidates the old one; rejection keeps the old one.
- **FR-005**: An admin MUST be able to set a new password for a user directly (shown once), and a signed-in user MUST be able to change their own password after confirming the current one; either supersedes a pending reset request.
- **FR-006**: The scanner MUST authenticate with a per-machine token: created by an admin ("Add machine"), shown exactly once, stored only as a hash, scoped to ingestion, revocable, with a visible last-used time, and at least 256 bits of randomness. The scan client's existing credential cache (location and `0600` permissions) MUST keep working.
- **FR-007**: Browser sessions MUST be stored server-side, outside any single backend instance's memory, so restarts and deploys do not sign users out. The session cookie MUST be HttpOnly with a SameSite policy, and CSRF protection MUST cooperate with the frontend's built-in XSRF mechanism.
- **FR-008**: The system MUST keep two authentication modes: browser sessions for the web app, and the machine token for the scanner ingest endpoints. Role rules MUST keep today's names: `admin`, `guest`, `app-gamecatalog` for people; the scanner's authorization comes from the token's ingestion scope.
- **FR-009**: Identity — users, app grants, passkey credentials, reset requests, machine tokens, sessions — MUST live in its own module with its own schema and a narrow public API exposing the current signed-in user, so an extracted app can take the module along and start its own user store.
- **FR-010**: A documented, operator-only break-glass procedure MUST reset an admin's password from the cluster or database, so a lost password never locks everyone out.
- **FR-011**: Cutover MUST preserve user identities and access: the new user store MUST carry the Keycloak subject as the id (no foreign-key changes) and the realm role memberships as roles/grants; every existing user MUST be able to sign in after cutover.
- **FR-012**: After cutover and a verified backup, a removal checklist MUST remove: the Keycloak deployment and public `/auth` route, realm files, the `keycloak` schema, the compose service, the `KC_*` variables, the issuer-uri configuration, and the frontend OIDC code.
- **FR-013**: The e2e pipeline MUST stop depending on Keycloak: journeys sign in through the app's own forms, a passkey journey runs with a virtual authenticator, and a test MUST prove the profile-gated test sign-in is absent from the production configuration.
- **FR-014**: Signed-in users MUST be able to register one or more passkeys (WebAuthn) with the device's fingerprint/face/platform authenticator, list their passkeys, and remove them. Only public-key credential material is stored server-side; biometric data never leaves the device.
- **FR-015**: Users MUST be able to sign in with a passkey as an alternative to the password, receiving the same session, roles and app grants. Password sign-in MUST remain available (the recovery path).
- **FR-016**: Passkey ceremonies MUST bind to the platform's relying party (production domain; `localhost` in dev/e2e), respect account status exactly like password sign-in (`PENDING` → role-less session; `REJECTED`/`REVOKED` refused), and run under the same session-fixation and CSRF protections.
- **FR-017**: User management MUST keep spec 014's capabilities without Keycloak: grant/revoke app access, list and end a user's sessions, and delete a non-admin account with its data cleaned up (`UserAccessRemoved`/`UserDeleted` cleanups), refusing self-harm and the last approved admin.
- **FR-018**: Guest chat limits MUST keep working unchanged (settings module); revoking or deleting a user MUST clear that user's personal limit via events, as today.
- **FR-019**: A new pending sign-up MUST notify the admin via ntfy (the shared client), replacing today's Keycloak poller.
- **FR-020**: A signed-in user MUST be able to change their display name; email is no longer part of the account.

### Key Entities *(include if feature involves data)*

- **User**: username (unique, lowercase), display name, password hash (nullable only until a migrated user's first reset), role (`admin` or `guest` or none), status (`PENDING`/`APPROVED`/`REJECTED`/`REVOKED`), lockout counters, and — for migrated users — the id equals their Keycloak subject.
- **App grant**: a user's access to a grantable app (`Game Catalog`), assigned by approval defaults or an admin; drives the `app-gamecatalog` authority.
- **Passkey**: a WebAuthn credential (public key + credential id) bound to a user; several per user; removable; the device holds the private key behind fingerprint/face.
- **Password reset request**: username + the user-chosen new password's hash + request time; pending until an admin approves or rejects it.
- **Machine token**: a scanner credential identifying one machine; ingestion scope, 256-bit random, shown once, stored hashed, revocable, with a last-used timestamp.
- **Session**: a server-side record binding a signed-in user to their browser; persists across restarts and deploys; listable and endable by the admin; invalidated on sign-out.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A friend registers with a username and password (no email), the admin approves them, and the friend signs in — the whole journey completes with Keycloak not running.
- **SC-002**: A user registers a passkey on the phone, signs out, and signs back in with fingerprint/face through the passkey — no password typed; the same journey works in the automated e2e suite with a virtual authenticator.
- **SC-003**: A deploy during an active session does not sign the user out.
- **SC-004**: The scanner on JordyBox and on the Mac ingests with machine tokens, and revoking a token stops that machine on its next call.
- **SC-005**: Revoking a user's access ends their sessions immediately — the next request with their old session is refused.
- **SC-006**: A forgotten password is recovered through the admin-approved reset request, with no email and no links.
- **SC-007**: An operator restores a locked-out admin unaided by following the documented break-glass procedure.
- **SC-008**: After removal, production runs one fewer container and the repository has no Keycloak references outside docs history.

## Out of Scope

- Usernameless/discoverable-credential passkey flows and conditional UI (autofill) — v1 uses username-first ceremonies with a visible "Sign in with a passkey" action.
- Passkey attestation policy beyond the library defaults; hardware security keys beyond what platform authenticators offer.
- Passkey-only accounts — a password always remains as the recovery path.
- Invite, magic, or recovery links of any kind, and email delivery of anything.
- Single sign-on across extracted apps; multi-factor authentication beyond the passkey's device verification; social login and federation.
- Running Keycloak in parallel long-term; it exists only during the cutover window.

## Assumptions

- The PWA (018) has landed: same-origin app, no Capacitor, cookie sessions and WebAuthn run on the platform origin.
- Passkeys are additive (owner decision 2026-11-03): password sign-in, admin-approved resets and break-glass stay; passkeys are an alternative sign-in method and are enrolled after password sign-in.
- Passkeys bind to a stable relying party id — `jordylab.be` in production, `localhost` in dev/e2e. Moving the production domain later invalidates enrolled passkeys; users recover with the password and re-enroll (accepted risk, documented).
- WebAuthn requires a secure context (HTTPS or localhost); the installed PWA and both dev servers satisfy this.
- Reset completion: the user chooses the new password when requesting the reset; it activates on admin approval (admin never sees it). The admin's direct set covers users who cannot request.
- Password policy: configurable minimum length 12 + not-equal-username, matching today's realm policy; no forced complexity or rotation.
- Basic brute-force protection (lockout after repeated failures) is included; exact thresholds set during planning.
- Email plays no role in authentication or recovery; the account's optional display name replaces today's first/last name fields.
- Identity lives in its own `identity` module with its own schema; the extraction story drives this (FR-009).
- Production is deployed on the k3s cluster at cutover time; the Compose deployment (spec 017) is still a draft — if it lands first, the removal checklist targets its equivalent Keycloak pieces.
- The existing users are few, so cutover is a scripted migration validated against real data; a verified database backup precedes the Keycloak removal.
- Session lifetime is bounded and configurable (30-day idle expectation), with sign-out always available.
