# Contract: User Management API

**Extends**: `specs/006-settings-module/contracts/settings-users-api.md` (existing approve, reject, revoke, reset-password and
pending-count are unchanged). **Access rules**: [access-matrix changes](#access-matrix-changes) below.

All routes are under `/api/settings`, admin only, unless stated otherwise. Errors use the standard body
`{"reason": "…"}`.

---

## Users

### `GET /api/settings/users` (changed response)

Adds the access and limit fields to each person. Query `status` as before (`ALL`, `PENDING`, `APPROVED`, `REJECTED`).

```json
{
  "users": [
    {
      "id": "4f675aad-fa21-4b3b-9555-1b698b4e0c0a",
      "email": "friend@example.com",
      "firstName": "Sam",
      "lastName": "Example",
      "createdAt": "2026-10-20T09:12:00Z",
      "status": "APPROVED",
      "isAdmin": false,
      "apps": [
        { "key": "gamecatalog", "name": "Game Catalog", "granted": true, "grantable": true, "reason": null },
        { "key": "fna", "name": "FNA", "granted": false, "grantable": false, "reason": "Holds the admin's private data" }
      ],
      "chatLimit": { "limit": 35, "source": "PERSONAL" },
      "sessionCount": 2
    }
  ]
}
```

- `apps` lists every `ManagedApp`, including the non-grantable ones, so the panel can explain them.
- `chatLimit.source` is `PERSONAL`, `DEFAULT_SAVED` or `ENVIRONMENT`. Admins return `chatLimit: null`.
- `sessionCount` counts regular and phone offline sessions. It is `null` when Keycloak cannot be reached for this person.

### `POST /api/settings/users/{id}/approve`, `…/reject`, `…/revoke`, `…/reset-password`

Unchanged in path and response. Behaviour changes:

- **approve** also grants the default app roles (Game Catalog).
- **revoke** also removes every app role, deletes the personal limit, and keeps its existing error reasons. A retried
  revoke for a person who is no longer approved is accepted when local leftovers exist (research R6).

### `DELETE /api/settings/users/{id}` (new)

Deletes a person. The panel asks for confirmation first (FR-014); the endpoint itself does not require a confirmation
field.

| Status | Reason | When |
|---|---|---|
| 204 | | deleted (Keycloak account gone, local rows gone) |
| 400 | `CANNOT_CHANGE_OWN_ACCOUNT` | the id is the admin's own |
| 400 | `ADMIN_PROTECTED` | the person is an admin |
| 404 | `USER_NOT_FOUND` | unknown id |
| 503 | `KEYCLOAK_UNAVAILABLE` | Keycloak failed at any step; the person stays disabled, and the response says so |

On 503 the response body is `{"reason": "KEYCLOAK_UNAVAILABLE", "stage": "DELETE_ACCOUNT"}`, so the panel can tell the
admin that the account is blocked and that deletion can be retried. `stage` is one of `DISABLE`, `END_SESSIONS`,
`LOCAL_CLEANUP`, `DELETE_ACCOUNT`.

---

## App access

### `GET /api/settings/apps` (new)

The app registry the panel renders. Static, from code.

```json
{
  "apps": [
    { "key": "gamecatalog", "name": "Game Catalog", "grantable": true, "defaultGrant": true, "reason": null },
    { "key": "fna", "name": "FNA", "grantable": false, "defaultGrant": false, "reason": "Holds the admin's private data" },
    { "key": "settings", "name": "Settings", "grantable": false, "defaultGrant": false, "reason": "Administration" }
  ]
}
```

### `PUT /api/settings/users/{id}/apps/{appKey}` (new): grant

Idempotent. Grants the app to an approved person.

| Status | Reason | When |
|---|---|---|
| 204 | | granted (or already granted) |
| 400 | `APP_NOT_GRANTABLE` | the app is not grantable (FNA, Settings) |
| 400 | `USER_NOT_APPROVED` | the person is pending or rejected |
| 400 | `CANNOT_CHANGE_OWN_ACCOUNT` | the id is the admin's own |
| 404 | `APP_NOT_FOUND` | unknown app key |
| 404 | `USER_NOT_FOUND` | unknown person id |
| 503 | `KEYCLOAK_UNAVAILABLE` | Keycloak failed; nothing changed |

### `DELETE /api/settings/users/{id}/apps/{appKey}` (new): remove access

Idempotent. Removes the app role. For the Game Catalog it also publishes `AppAccessRemoved`, which removes the person's
game votes (clarification Q3). The person keeps every other app and their session (clarification Q1).

Status and reasons as for grant, except that `APP_NOT_GRANTABLE` and `USER_NOT_APPROVED` do not apply.

---

## Guest chat limits

### `GET /api/settings/guest-chat-limit` (new)

```json
{ "dailyLimit": 20, "source": "ENVIRONMENT", "updatedAt": null, "updatedBy": null }
```

`source` is `DEFAULT_SAVED` once the admin has saved a value, and `ENVIRONMENT` until then.

### `PUT /api/settings/guest-chat-limit` (new)

Body: `{ "dailyLimit": 25 }`. Whole number, 0 or more; 0 blocks chat for everyone without a personal limit.

| Status | Reason | When |
|---|---|---|
| 200 | | saved; body as for `GET` |
| 400 | `INVALID_LIMIT` | negative or not a whole number |

### `PUT /api/settings/users/{id}/chat-limit` (new): personal limit

Body: `{ "dailyLimit": 35 }`. Same rules as the default. Replaces the default for this person only.

| Status | Reason | When |
|---|---|---|
| 204 | | saved |
| 400 | `INVALID_LIMIT` | negative or not whole |
| 400 | `CANNOT_CHANGE_OWN_ACCOUNT` | the id is the admin's own |
| 400 | `ADMIN_PROTECTED` | the person is an admin (admins are never limited) |
| 404 | `USER_NOT_FOUND` | unknown id |

### `DELETE /api/settings/users/{id}/chat-limit` (new): clear

Returns the person to the default limit from their next message. Idempotent (204 when nothing was set).

---

## Sessions

### `GET /api/settings/users/{id}/sessions` (new)

```json
{
  "sessions": [
    { "id": "3f1c…", "kind": "WEB", "offline": false, "startedAt": "2026-11-02T08:01:00Z", "lastAccessedAt": "2026-11-02T08:40:00Z" },
    { "id": "9a77…", "kind": "PHONE", "offline": true, "startedAt": "2026-10-28T17:20:00Z", "lastAccessedAt": "2026-11-01T22:05:00Z" }
  ]
}
```

| Status | Reason | When |
|---|---|---|
| 200 | | list (may be empty) |
| 400 | `CANNOT_CHANGE_OWN_ACCOUNT` | the id is the admin's own (the panel does not list the admin's own sessions) |
| 404 | `USER_NOT_FOUND` | unknown id |
| 503 | `KEYCLOAK_UNAVAILABLE` | Keycloak failed |

### `DELETE /api/settings/users/{id}/sessions/{sessionId}?kind=WEB|PHONE` (new)

Ends one session. The query `kind` tells the backend whether to call the offline delete (`PHONE` with `offline=true`).
Ending one session never ends others (FR-019).

| Status | Reason | When |
|---|---|---|
| 204 | | ended (or already gone) |
| 404 | `SESSION_NOT_FOUND` | unknown session id for this person |
| 503 | `KEYCLOAK_UNAVAILABLE` | Keycloak failed |

### `POST /api/settings/users/{id}/sessions/end-all` (new)

Ends every session of the person: regular sessions are logged out and the `jordylab-mobile` consent is revoked, which
also removes its offline sessions (research R3). Confirmation is handled by the panel (FR-019).

| Status | Reason | When |
|---|---|---|
| 204 | | ended |
| 400 | `CANNOT_CHANGE_OWN_ACCOUNT` | the id is the admin's own |
| 404 | `USER_NOT_FOUND` | unknown id |
| 503 | `KEYCLOAK_UNAVAILABLE` | Keycloak failed |

---

## Error reasons, consolidated

| Reason | Used by |
|---|---|
| `USER_NOT_FOUND` | all person routes |
| `USER_NOT_APPROVED` | revoke (existing), grant |
| `LAST_ADMIN_PROTECTED` | reject, revoke (existing), delete |
| `ADMIN_PROTECTED` | delete, personal limit |
| `CANNOT_CHANGE_OWN_ACCOUNT` | delete, revoke, grant, limit, sessions |
| `APP_NOT_FOUND`, `APP_NOT_GRANTABLE` | grant, remove access |
| `INVALID_LIMIT` | limits |
| `SESSION_NOT_FOUND` | end one session |
| `KEYCLOAK_UNAVAILABLE` | all Keycloak-backed routes (with `stage` on delete) |

---

## Access matrix changes

Changes to `specs/006-settings-module/contracts/access-matrix.md` and `SecurityConfig`:

| Route | Before | After |
|---|---|---|
| `GET /api/gamecatalog/games/**`, `GET /platforms`, `GET /places` | `admin` or `guest` | `admin` or `app-gamecatalog` |
| `PUT /api/gamecatalog/games/*/mark`, `…/rom-status` | `admin` or `guest` | `admin` or `app-gamecatalog` |
| `/api/gamecatalog/libbot/**` | `admin` or `guest` | `admin` or `app-gamecatalog` |
| `GET /api/mobile/releases/latest`, `POST …/download-link` | `admin` or `guest` | unchanged (`admin` or `guest`) |
| `/api/settings/**` (including the new routes) | `admin` | unchanged (`admin`) |
| `/api/fna/**` | `admin` | unchanged |

Pending people (no `guest`, no `app-gamecatalog`) keep being refused everywhere. A guest without the app role receives 403 on
Game Catalog routes, which the frontend shows as the "No apps yet" page, not as an error.

## Frontend role contract (changes)

- `AppRole` gains `'app-gamecatalog'`.
- `roleGuard` for the `games` route: `roleGuard('admin', 'app-gamecatalog')`.
- The navigation item for Library is visible to admins and to holders of `app-gamecatalog`.
- A guest with `guest` but no app role is sent to `/no-apps`, a page that is always open to approved people.
