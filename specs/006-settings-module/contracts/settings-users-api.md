# Contract — Settings Users API

Admin-only user administration over Keycloak (spec FR-001–FR-010, research D3/D6). All endpoints require the `admin`
realm role (see [access-matrix.md](access-matrix.md)). Backed by the settings module's `KeycloakAdminClient` (
RestClient + client-credentials via the confidential `jordylab-backend` client).

## Endpoints

### `GET /api/settings/users?status={PENDING|APPROVED|REJECTED|ALL}`

List users as the Users page shows them. Default `ALL`; ordered by `createdAt` ascending.

**200** body:

```json
{
  "users": [
    {
      "id": "8f14e45f-ea1f-4b0a-9c5d-3c1a2b3d4e5f",
      "email": "friend@example.org",
      "firstName": "Ada",
      "lastName": "Palmer",
      "createdAt": "2026-09-27T18:40:11Z",
      "enabled": true,
      "realmRoles": [
        "default-roles-jordylab"
      ],
      "status": "PENDING"
    }
  ]
}
```

`status` is derived server-side (enabled + `guest` presence — [data-model.md](../data-model.md)). No password or
credential field ever appears in any user representation (FR-005).

### `GET /api/settings/users/pending-count`

Badge + notifier feed. **200** body: `{"count": 3}`.

### `POST /api/settings/users/{id}/approve`

Grant `guest` (and enable if the account was disabled). Idempotent — approving an approved user is `204`.

**204** No content.

### `POST /api/settings/users/{id}/reject`

Disable the account (clarify round decision). The email cannot sign up again; the account stays listed as `REJECTED`.

**204** No content.
**400** `{"reason": "LAST_ADMIN_PROTECTED"}` — target is the only enabled `admin` (FR-007).

### `POST /api/settings/users/{id}/revoke`

Remove `guest` and end all of the user's Keycloak sessions. The user returns to `PENDING` (can be re-approved; until
then they see the awaiting-approval page; any still-valid access token dies on expiry — 30 min realm lifespan).

**204** No content.
**400** `{"reason": "USER_NOT_APPROVED"}` — target does not currently hold `guest`.
**400** `{"reason": "LAST_ADMIN_PROTECTED"}` — target is the only enabled `admin`.

### `POST /api/settings/users/{id}/reset-password`

Set a generated temporary password (must be changed at next login). The cleartext is returned **once** to the admin for
out-of-band sharing and is never stored or logged (FR-005/FR-006).

**200** body: `{"temporaryPassword": "…"}`

## Shared error shapes

| Status | Body                                 | When                                                                                |
|--------|--------------------------------------|-------------------------------------------------------------------------------------|
| 403    | (Spring Security default)            | caller is not `admin` — deny by default                                             |
| 404    | `{"reason": "USER_NOT_FOUND"}`       | unknown user id on any `{id}` route                                                 |
| 503    | `{"reason": "KEYCLOAK_UNAVAILABLE"}` | Admin REST/token call failed — explicit failure, no partial state (constitution II) |

## Non-functional contract

- No admin secret ever crosses into a browser response; the service-account token stays server-side (FR-010).
- Keycloak remains the system of record: JordyLab stores no user rows; every list reads Keycloak live (small realm —
  single-digit users expected).
