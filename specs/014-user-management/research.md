# Research: User Management Control Panel

**Date**: 2026-11-02 · **Spec**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md)

Each decision answers an open technical question from the spec and the Technical Context. Facts were checked
against the repository on this branch and against the Keycloak 26.7.4 Admin REST documentation.

---

## R1. Where per-app grants live

**Decision**: one Keycloak realm role per grantable app. Game Catalog gets `app-gamecatalog`. FNA and Settings get no
role, because they are never grantable. The `guest` role keeps meaning "approved".

**Rationale**:

- Enforcement is already role-based end to end: `SecurityConfig` matchers, `roleGuard` on the frontend, and the token
  roles in `AuthService.roles()`. A grant can be expressed in the same vocabulary.
- Approve and revoke already change realm roles (`grantRealmRole` / `revokeRealmRole`), so the existing service grows
  by a loop, not a new subsystem.
- Deleting the account removes its role mappings, so no grant data is left behind (FR-015).
- The navigation needs no new endpoint: it reads the token roles. The Users panel already receives each person's
  `realmRoles` in `AppUser`.

**Alternatives considered**:

- **A grants table in the `settings` schema, checked per request.** Takes effect on the next request with no
  token involvement. Rejected because it creates a second source of truth beside Keycloak roles, adds a query to
  every Game Catalog request, and needs a new "my apps" endpoint for the navigation. Its one real advantage, instant
  revocation, is delivered anyway by the access-token bound in R2, which deletion needs too.
- **Groups instead of roles.** Same propagation behaviour as roles, with more Admin REST calls and no direct use in
  Spring's `hasRole`. Rejected.

---

## R2. How quickly a change reaches a signed-in person

**Decision**: lower the realm's `accessTokenLifespan` from 1800 s to **60 s**. Backend and frontend keep their current
structure. The change is applied with `kcadm` on the live realm and mirrored in the realm files.

**Rationale**:

- The backend validates JWTs statelessly. A removed role or a deleted account is not seen by an already-issued access
  token until it expires. Today that window is 30 minutes. That gap already exists for the current Revoke, and
  "within one minute" (FR-007, SC-001, SC-002) cannot be met with it.
- At 60 s, the worst case from change to refusal is one token lifetime. The frontend refreshes when less than 30 s
  remain (`AuthService.getToken` calls `updateToken(30)`), so the new token carries the new roles.
- Deletion works through the same path. The account is disabled and its sessions are ended first, so the refresh
  fails. The last access token dies within the same 60 s.

**Alternatives considered**:

- **30 s lifetime.** Rejected: `updateToken(30)` would refresh on almost every request, doubling Keycloak traffic
  for no gain in the requirement.
- **A backend cut-off check.** Store "tokens issued before T for subject S are refused" and check it in a JWT
  validator. Gives instant denial for deletion, but adds stateful per-request work, must survive restarts, and still
  needs R1's grant freshness for app revocation. More code for the same outcome.
- **Keep 30 minutes.** Rejected: contradicts FR-007 and SC-002.

**Risk**: refresh frequency rises to roughly one call per 30 s per open tab. For a handful of people, this is
negligible. The realm change is an operational step and needs the owner's sign-off before it is applied to production.

---

## R3. Ending sessions, including phone offline sessions

**Decision**:

- List: `GET /users/{id}/sessions` for regular sessions. Each session's `clients` map tells web (any client other than
  `jordylab-mobile`) from phone (`jordylab-mobile`). List offline sessions with
  `GET /users/{id}/offline-sessions/{clientUuid}`, where `clientUuid` is the internal UUID of `jordylab-mobile`, found
  with `GET /clients?clientId=jordylab-mobile`. Passing the client id instead returns "Client not found" (a known
  Keycloak pitfall).
- End one web session: `DELETE /sessions/{sessionId}`.
- End one phone offline session: `DELETE /sessions/{sessionId}?isOffline=true`. **Verified** on Keycloak 26.7.4 on
  2026-11-02 (`KeycloakSessionVerificationIntegrationTest`): deleting the offline session removes it from the user's
  offline-session list and its refresh token then fails with an `invalid_grant`. The fallback below is therefore not
  needed.
- End all sessions: `POST /users/{id}/logout` plus `DELETE /users/{id}/consents/jordylab-mobile`.

**Rationale**: the Keycloak documentation states that an admin logout does **not** end offline sessions, so "end all"
must always include the consent revocation. Consent revocation kills every offline token of that client for that user.
The existing `revoke()` already does this.

**T-V2 verified** on the same run: the `jordylab-backend` service account (with `manage-users` and `view-users`) can
log a user out and delete the user. No extra scope is needed.

**Fallback (not used)**: had `isOffline=true` failed on 26.7.4, "end one phone session" would have become "end all phone
sessions", because consent revocation is per client, not per device. FR-019 would then have needed a spec amendment.
The verification passed, so FR-019 stands as written.

---

## R4. Order of steps when a person is deleted

**Decision**: each step is idempotent, and a retry resumes where the last attempt stopped.

1. Refuse self-deletion and admin deletion (400, before any change).
2. Disable the account in Keycloak. This blocks sign-in and token refresh (FR-015 "block first").
3. Log out all regular sessions and revoke the `jordylab-mobile` consent. A missing consent is not an error.
4. Local cleanup in one transaction: delete chat usage rows and the personal limit row. Publish `UserAccessRemoved`
   (marks, existing) and `UserDeleted` (conversations, and later feedback tickets).
5. Delete the Keycloak user last. Keycloak's own delete also removes remaining sessions and offline tokens.

**Why this order**: if step 5 fails, the person is disabled and their data is already gone, so nothing stays usable
and the admin can retry (FR-017). Deleting the Keycloak user first would leave local rows with no account to find on
retry.

**Event delivery**: Modulith's event publication log (`event_publication`, already in use for `MobileNotificationListener`
and `GuestChatUsageListener`) keeps a failed listener's event for retry, so a failed marks cleanup is not lost.

---

## R5. Where the guest limits live

**Decision**: one table, `settings.guest_chat_limit`. A row with a null `user_subject` is the saved default, and there
is at most one. A row with a `user_subject` is a personal limit for that person. The environment value is the
starting default and applies only while no default row exists.

**Resolution**: personal row if present, else saved default, else `jordylab.settings.guest-chat.daily-limit`. The
admin is exempt and never resolved.

**Rules**:

- Saved values are stored as given. Zero is allowed (FR-011). `SettingsProperties.GuestChat` coerces a configured value
  of 0 or less back to 20, which is correct for the environment default but must not apply to saved values, so the
  resolver never passes saved values through `SettingsProperties`.
- One resolver serves both `GuestChatQuotaService` (the quota shown above LibBot) and `GuestChatLimitFilter` (the
  pre-check that returns 429). Two copies of the rule would drift.

**Alternatives considered**: a key-value settings table (rejected: text primary keys break the rule that IDs are UUIDs);
a column on `guest_chat_usage` (rejected: usage rows are per day and disappear).

---

## R6. Approve and revoke semantics

**Decision**:

- **Approve** (existing action, extended): enable if disabled, grant `guest`, and grant the default app roles
  (Game Catalog). Each grant is skipped when already present.
- **Revoke** (existing action, extended): remove `guest` and every app role, end sessions, revoke the mobile consent,
  then in one transaction delete the personal limit row and publish `UserAccessRemoved` (which removes the person's
  game votes, as spec 013 FR-046 already requires).
- **Remove one app** (new action): remove that app's role, and if the app is Game Catalog, publish `AppAccessRemoved`
  so the person's game votes are removed (spec clarification Q3).

**Retry after a partial revoke**: if the local cleanup fails after Keycloak succeeded, the person is no longer approved.
A retried revoke must then be accepted for cleanup only when a personal limit row or votes still exist, instead of
returning `USER_NOT_APPROVED`. This is the one place the existing error contract changes, and it is limited to that
case.

---

## R7. Cross-module wiring

**Decision**: all new cross-module behaviour goes through events, as the mobile and settings modules already do.

- `shared.event.AppAccessRemoved(String userSubject, String appKey)`: published by `settings` when one app is removed.
  `gamecatalog.MarkCleanupListener` deletes the person's marks when `appKey` equals the Game Catalog key.
- `shared.event.UserDeleted(String userSubject)`: published by `settings` at step 4 of R4.
  - `gamecatalog` forgets the person's in-memory LibBot conversations (`ConversationStore`).
  - The feedback module, which does not exist yet, will anonymise its tickets on this event. This is a dependency
    for the feedback spec (see plan, Dependencies).
- The Game Catalog key is a public constant in the `gamecatalog` root package. `settings` already depends on
  `gamecatalog`, so it may reference that constant. The reverse dependency would create a cycle, so the constant is
  never read from `settings` back into `gamecatalog`.

---

## R8. Enforcement points

**Decision**: `SecurityConfig` changes only the Game Catalog rows. Those that were `hasAnyRole("admin","guest")` become
`hasAnyRole("admin","app-gamecatalog")`: reads, marks, ROM status and LibBot. Mobile rows keep `guest`, because
mobile downloads are not an app grant. `/api/settings/**` stays admin-only. FNA stays admin-only.

On the frontend, `AppRole` gains `'app-gamecatalog'`. The `games` route becomes `roleGuard('admin', 'app-gamecatalog')`.
The navigation item for Library is shown only to holders of that role, and admins see everything.

A guest with no granted app has no valid landing page: `ROLE_HOME.guest` points at a page they cannot open. The plan
adds a small "No apps yet" page and routes such guests there. This is a new edge case for the spec (see plan, Spec
follow-ups).

---

## R9. Keycloak Admin REST calls the panel needs

| Need | Call | Status in `KeycloakAdminClient` |
|---|---|---|
| Regular sessions | `GET /users/{id}/sessions` | add |
| Phone offline sessions | `GET /users/{id}/offline-sessions/{clientUuid}` | add |
| Client UUID of `jordylab-mobile` | `GET /clients?clientId=jordylab-mobile` | add (cache) |
| End one session | `DELETE /sessions/{id}` and `?isOffline=true` | add |
| Disable | `PUT /users/{id}` with `enabled=false` | exists (`setEnabled`) |
| Logout | `POST /users/{id}/logout` | exists (`endSessions`) |
| Consent revoke | `DELETE /users/{id}/consents/{client}` | exists (`revokeConsent`) |
| Delete user | `DELETE /users/{id}` | add |
| Grant / remove role | `POST` / `DELETE /users/{id}/role-mappings/realm` | exists |

The service account already holds `view-users`, `manage-users` and `view-realm` (deploy README, 2026-09-30). Session
and user deletion fall under `manage-users` (verified 2026-11-02, T-V2). **`view-clients` is additionally required**: the
session list resolves the phone app's client UUID with `GET /clients?clientId=jordylab-mobile`, and `view-realm` alone is
refused. The production realm needs that one added role before the sessions story ships (kcadm, recorded in the deploy
README with the other spec 014 changes).

**Found while browser-testing and fixed (2026-11-02):** the realm import applies the service-account user's
`clientRoles` list, not only `clientScopeMappings`. That list had three roles, so a fresh realm came up without
`view-clients` and the sessions call answered 403. Both realm files now list `view-clients` in
`users[service-account-jordylab-backend].clientRoles.realm-management` as well, and a fresh import was verified to carry
all four roles (`RealmConfigurationTest` guards it). The **live** realm is not re-imported, so the changes were applied
explicitly with `kcadm` on 2026-11-02 and verified: `view-clients` on the service account, role `app-gamecatalog`
created and granted to the three approved guests, and `accessTokenLifespan` set to 60. Only the backend/frontend deploy
remains.

**Cost**: the Users list already makes one Admin REST call per person for roles. Sessions add one call and offline
sessions add one more per person. For the expected 10 to 20 people this is well under the 2 s budget, but it is
measured in the quickstart.

---

## R10. Frontend state and tests

**Decision**: follow the existing `UsersStore` pattern (`libs/settings/api`), which is a signal store with private
writable signals, public readonly signals, and methods that call the API. Add two more stores in the same lib: one for
the guest limits, and one for a person's sessions, loaded when that person's entry is opened. Containers inject the
stores directly.

**Confirmation dialog**: the delete action needs a confirmation step (FR-014). The spartan/ui helm set in this repo
does not yet include a dialog, so the plan adds one with the spartan CLI.

**Tests**: Vitest and Spectator, with mocks as plain objects using `useValue` and `vi.fn()`, and real `signal(...)`
instances for reactive state, per the frontend AGENTS.md. Fixtures go in `libs/settings/api/src/lib/mocks/`.

---

## Summary of decisions that differ from the draft

- Draft `research.md` (and spec-draft) suggested that revoking an app ends the person's sessions. Clarification Q1
  decided otherwise: revoking one app keeps the person signed in. R1 and R2 deliver that.
- Draft suggested Keycloak roles and session ending. R2 adds the access-token lifetime change, which the draft did not
  mention. It is needed for FR-007 and SC-002 and also closes the existing 30-minute gap for Revoke.
