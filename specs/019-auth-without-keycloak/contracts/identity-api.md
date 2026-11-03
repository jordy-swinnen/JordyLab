# Contract: Identity REST API

**Feature**: [spec.md](spec.md) · Amends: 003 (scanner auth), 006/014 (settings user management) · Auth chains: [access-matrix.md](access-matrix.md)

All application endpoints live under `/api/identity/**` (except Spring Security's WebAuthn ceremony endpoints). JSON bodies; errors use `{ "reason": "CODE", "message": "..." }`. Plaintext passwords, hashes, tokens and temporary passwords are never logged, and one-time values are returned exactly once.

## Authentication (public — `/api/identity/auth/**`)

| Method & path | Auth | Body | Success | Errors |
|---|---|---|---|---|
| `POST /api/identity/auth/register` | anonymous + CSRF | `{ username, password, displayName? }` | `201 { id, username, status: "PENDING" }`; ntfy notification to the admin | `400 USERNAME_TAKEN`, `400 WEAK_PASSWORD`, `400 INVALID_USERNAME` |
| `POST /api/identity/auth/login` | anonymous + CSRF | `{ username, password }` | `200 { id, username, displayName, role, status, appGrants[] }` + session cookie (fixation-protected) | `401 INVALID_CREDENTIALS` (unknown username and wrong password identical), `401 ACCOUNT_LOCKED`, `403 ACCOUNT_REJECTED`, `403 ACCOUNT_REVOKED` |
| `POST /api/identity/auth/logout` | session + CSRF | — | `204`, session invalidated, cookie cleared | — |
| `GET /api/identity/me` | session | — | `200 { id, username, displayName, role, status, appGrants[] }` | `401` |
| `GET /api/identity/auth/csrf` | anonymous | — | `204`, sets `XSRF-TOKEN` | — |
| `PATCH /api/identity/me` | session + CSRF | `{ displayName }` | `204` | `400 INVALID_NAME` |
| `POST /api/identity/auth/change-password` | session + CSRF | `{ currentPassword, newPassword }` | `204` (pending reset superseded) | `400 WEAK_PASSWORD`, `401 INVALID_CREDENTIALS` |
| `POST /api/identity/auth/reset-requests` | anonymous + CSRF | `{ username, newPassword }` | `202` **always** (identical for unknown usernames; nothing created for them) | `400 WEAK_PASSWORD` |

`PENDING` users sign in with `role: null` and `appGrants: []` — the frontend routes them to `/awaiting-approval`.

## Passkeys (WebAuthn — Spring Security module + management API)

| Method & path | Auth | Body | Behaviour |
|---|---|---|---|
| `POST /webauthn/register` | session + CSRF | WebAuthn creation options, then the authenticator's response | Two-phase ceremony: returns `PublicKeyCredentialCreationOptions` for the signed-in user, then stores the credential via `JdbcUserCredentialRepository`. The device prompts for fingerprint/face. |
| `POST /webauthn/authenticate` | anonymous + CSRF | `{ username }`, then the assertion | Returns `PublicKeyCredentialRequestOptions` for that user; the assertion establishes the same session/authorities as password login (JSON success handler). Unknown username → identical failure shape as a wrong assertion. |
| `GET /api/identity/passkeys` | session | — | `200 { passkeys: [{ credentialId, label, createdAt, lastUsedAt }] }` (own credentials) |
| `DELETE /api/identity/passkeys/{credentialId}` | session + CSRF | — | `204`; `404 PASSKEY_NOT_FOUND` when not the caller's |

Status rules: `REJECTED`/`REVOKED` are disabled for the WebAuthn user details (refused); `PENDING` authenticates with no authority, exactly like the password flow. Relying party: `jordylab.be` (prod) / `localhost` (dev, e2e), configured under `jordylab.identity.webauthn.*`.

## User management (admin — `/api/identity/users/**`, `ROLE_admin`)

Preserves spec 014's capability set, now backed by the identity module:

| Method & path | Body | Success | Notes / errors |
|---|---|---|---|
| `GET /api/identity/users?status=ALL\|PENDING\|APPROVED\|REJECTED\|REVOKED` | — | `200 { users: [{ id, username, displayName, role, status, appGrants[], createdAt, pendingResetRequest }] }` | `pendingResetRequest: { requestId, requestedAt } \| null` |
| `GET /api/identity/users/pending-count` | — | `200 { pendingCount }` | sign-ups + reset requests |
| `POST /api/identity/users/{id}/approve` | `{ role?: "guest"\|"admin" }` | `204` | grants `guest` + `GAME_CATALOG` by default; `409 ALREADY_APPROVED`, `404 USER_NOT_FOUND` |
| `POST /api/identity/users/{id}/reject` | — | `204` | publishes `UserAccessRemoved`, ends sessions; `409 LAST_ADMIN_PROTECTED`, `409 USER_NOT_APPROVED` |
| `POST /api/identity/users/{id}/revoke` | — | `204` | removes role + grants, ends sessions; `409 LAST_ADMIN_PROTECTED`, `409 USER_NOT_APPROVED` |
| `PUT /api/identity/users/{id}/apps/{app}` | — | `204` | `app` = `GAME_CATALOG`; grants the app (`app-gamecatalog` authority); `400 UNKNOWN_APP` |
| `DELETE /api/identity/users/{id}/apps/{app}` | — | `204` | revokes the grant and app data (`AppAccessRemoved`); `404 GRANT_NOT_FOUND` |
| `GET /api/identity/users/{id}/sessions` | — | `200 { sessions: [{ sessionId, startedAt, lastAccessedAt }] }` | web sessions from Spring Session |
| `POST /api/identity/users/{id}/sessions/{sessionId}/end` | — | `204` | `409 OWN_ACCOUNT` for one's own; `404 SESSION_NOT_FOUND` |
| `POST /api/identity/users/{id}/sessions/end-all` | — | `204` | `409 OWN_ACCOUNT` |
| `DELETE /api/identity/users/{id}` | — | `204` | staged delete: sessions ended, usage + personal limit cleared via events, account removed; refuses self and any admin; idempotent (`404` on repeat is acceptable) |
| `POST /api/identity/users/{id}/reset-password` | — | `200 { temporaryPassword }` | generated, shown once; supersedes a pending request |
| `POST /api/identity/users/{id}/reset-requests/{requestId}/approve` | — | `204` | applies the requested hash; `409 REQUEST_NOT_PENDING` |
| `POST /api/identity/users/{id}/reset-requests/{requestId}/reject` | — | `204` | old password keeps working; `409 REQUEST_NOT_PENDING` |

`LAST_ADMIN_PROTECTED` covers reject/revoke/delete of the last approved admin. Approving a revoked account re-applies the default grants.

## Machine tokens (admin — `/api/identity/machines/**`, `ROLE_admin`)

| Method & path | Body | Success | Errors |
|---|---|---|---|
| `GET /api/identity/machines` | — | `200 { machines: [{ id, name, createdAt, lastUsedAt, revokedAt }] }` | — |
| `POST /api/identity/machines` | `{ name }` | `201 { id, name, token }` — the full `jlmt_…` token, shown **exactly once** | `400 INVALID_NAME` |
| `POST /api/identity/machines/{id}/revoke` | — | `204` | `404 MACHINE_NOT_FOUND`, `409 ALREADY_REVOKED` |

After 018 there is a single scope (`INGEST` → `ROLE_gamecatalog-scanner`); the APK CI job and its client no longer exist.

## Test-only sign-in (e2e profile — `POST /api/identity/test/sign-in`)

Exists **only** under the `e2e` Spring profile: `{ username }` → a session for that user without credentials, used by Playwright setup. A prod-profile test asserts it is absent (404) — FR-013.

## CSRF & cookie contract (session chain)

- `XSRF-TOKEN` cookie (HttpOnly off) is written by `GET /api/identity/auth/csrf`; Angular's default interceptor returns it as `X-XSRF-TOKEN` on same-origin mutating requests. The backend accepts the raw header token.
- Session cookie `JSESSIONID`: HttpOnly, `SameSite=Lax`, secure in prod, persistent (max-age = 30-day idle timeout).
- The machine-token chain (`/api/gamecatalog/ingest/check`, `/scan`) is CSRF-exempt and sessionless.

## Scanner client contract (amends 003)

The downloaded client (`GET /api/gamecatalog/ingest/client`, admin, unchanged) is regenerated with `BACKEND_URL`/`SCAN_ENDPOINT`/`LIBRARY_TYPE` constants (no `KEYCLOAK_URL`/`REALM`/`CLIENT_ID`). Auth: token from `JORDYLAB_SCAN_TOKEN` or a prompt; cached at `~/.config/jordylab/scan/token.json` (0600, unchanged); sent as `Authorization: Bearer` on `/check` and `/scan`; `401` clears the cache and re-prompts. All scan semantics (machineId adoption, fingerprints, shrink guard, idempotency) are unchanged.
