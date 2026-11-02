# Data Model: User Management Control Panel

**Spec**: [spec.md](spec.md) · **Research**: [research.md](research.md)

Most of the data this feature touches lives in Keycloak and is read or changed through the Admin REST API. JordyLab owns
one new table.

---

## 1. Owned by JordyLab (schema `settings`)

### `settings.guest_chat_limit` (new)

One table holds both the saved default and the personal limits. A row with a null `user_subject` is the default.

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | UUID | no | primary key |
| `user_subject` | TEXT | yes | Keycloak user id of the person. Null for the saved default row |
| `daily_limit` | INTEGER | no | whole number, 0 or more (FR-011). 0 blocks chat |
| `updated_by_subject` | TEXT | no | the admin who saved it (the admin's Keycloak id) |
| `created_at`, `updated_at` | TIMESTAMPTZ | no | from the base entity audit fields |

Constraints:

- `CHECK (daily_limit >= 0)`
- `UNIQUE (user_subject)`. PostgreSQL allows several null subjects under a plain unique constraint.
- A partial unique index allows at most one default row: unique on a constant, restricted to rows where
  `user_subject IS NULL`. The exact expression is chosen in the migration task, but the rule is "at most one default".

Lifecycle:

- The default row is created the first time the admin saves a default. Until then, the environment value
  (`JORDYLAB_GUEST_CHAT_DAILY_LIMIT`, default 20) applies (FR-009).
- A personal row is created when the admin sets a personal limit, and deleted when the admin clears it, when the person's
  approval is revoked (FR-010), or when the person is deleted (FR-015).

Entity: `GuestChatLimit` in `settings.domain`. Builder enforces `dailyLimit >= 0` and a non-blank `updatedBySubject`.
The default row is built with `userSubject = null`. Repository: `GuestChatLimitRepository` with
`findByUserSubject(String)`, `findDefault()` (where `user_subject IS NULL`), and `deleteByUserSubject(String)`.

Unchanged in `settings`:

- `settings.guest_chat_usage` gains a `deleteByUserSubject` path, used by the deletion cleanup (R4 step 4). Its rows are
  per person per day.
- `settings.ai_feature_model_setting` and `settings.ai_feature_last_run` are untouched.

Cleanup on person deletion covers `guest_chat_usage` rows and the personal `guest_chat_limit` row, and nothing else in
JordyLab's own tables. Game votes are removed through the `UserAccessRemoved` event in `gamecatalog`.

---

## 2. Owned by Keycloak (realm `jordylab`)

### Realm roles

| Role | Meaning | Assigned by | Removed by |
|---|---|---|---|
| `admin` | the one admin (composite: includes `guest` and `gamecatalog-scanner`) | never by the panel | never by the panel |
| `guest` | approved person (no longer grants Game Catalog by itself) | approve | revoke |
| `app-gamecatalog` (new) | may use the Game Catalog | approve (default set), remove-app grant | revoke, remove-app |
| `gamecatalog-scanner` | scan client device role | unchanged | unchanged |
| `mobile-release-publisher` | CI publisher | unchanged | unchanged |

`app-gamecatalog` is not composite and is not part of the `admin` composite. Admins pass the Game Catalog matchers through
`admin` directly.

### Realm settings

| Setting | Current | New | Why |
|---|---|---|---|
| `accessTokenLifespan` | 1800 | 60 | role changes and deletions reach access tokens within one minute (research R2) |

### Users

Each person is a Keycloak user with `enabled`, `email`, names, `createdTimestamp`, realm role mappings and sessions. The
status shown in the panel is derived, not stored (as today):

- disabled → `REJECTED`
- holds `guest` or `admin` → `APPROVED`
- otherwise → `PENDING`

A person with `app-gamecatalog` but not `guest` cannot occur through the panel, because approve grants `guest` and
revoke removes both. Such a state is treated as not approved.

### Sessions

| Kind | Source | Ended by |
|---|---|---|
| web (regular) | `GET /users/{id}/sessions`, clients other than `jordylab-mobile` | `DELETE /sessions/{id}` |
| phone (regular) | `GET /users/{id}/sessions`, client `jordylab-mobile` | `DELETE /sessions/{id}` |
| phone (offline) | `GET /users/{id}/offline-sessions/{clientUuid}` | `DELETE /sessions/{id}?isOffline=true` (to verify, research R3) |

---

## 3. Shared events (`shared.event`)

| Event | Fields | Published by | Consumed by |
|---|---|---|---|
| `UserAccessRemoved` (existing) | `userSubject` | `settings`: reject, revoke, delete | `gamecatalog.MarkCleanupListener` (removes votes) |
| `AppAccessRemoved` (new) | `userSubject`, `appKey` | `settings`: remove-app | `gamecatalog.MarkCleanupListener` when `appKey` is the Game Catalog key |
| `UserDeleted` (new) | `userSubject` | `settings`: delete (R4 step 4) | `gamecatalog` LibBot (forgets conversations); feedback module (future: anonymise tickets) |

All three are records, published inside a transaction so that listeners run after commit, and delivered through the
Modulith event publication log.

---

## 4. In-memory state (no change)

`ConversationStore` in `gamecatalog` holds LibBot conversations for ten minutes of idle time, keyed by the person and the
conversation id. It is not persisted. A deletion removes that person's entries (new method `forget(userSubject)`).

---

## 5. Application model (`settings`, not persisted)

- **`ManagedApp`** (enum, code only): `GAME_CATALOG` (key `gamecatalog`, name "Game Catalog", grantable, role
  `app-gamecatalog`, default grant), `FNA` (key `fna`, not grantable, reason "Holds the admin's private data", no role),
  `SETTINGS` (key `settings`, never grantable, no role, reason "Administration").
  Adding an app means adding a constant here and a role in Keycloak; nothing is granted until the admin does so.
- **`EffectiveGuestChatLimit`** (record): `limit`, `source` (`PERSONAL`, `DEFAULT_SAVED`, `ENVIRONMENT`).
- **`PersonSession`** (record): `sessionId`, `kind` (`WEB`, `PHONE`), `offline` (boolean), `startedAt`, `lastAccessedAt`.

---

## 6. Entity relationships

```text
Person (Keycloak user) ──< realm role mappings (guest, app-gamecatalog)
Person ──< sessions (Keycloak, web and phone)
Person ──0..1 guest_chat_limit (personal)          guest_chat_limit(user_subject NULL) = default, at most one
Person ──< guest_chat_usage (per day, cleared on delete)
Person ──< game votes (gamecatalog, removed on revoke, app removal and delete)
Person ──< LibBot conversations (memory, cleared on delete)
```

---

## 7. Validation rules (from the spec)

| Rule | Where it is enforced |
|---|---|
| Grant only grantable apps, and only to approved people | `AppAccessService` (FR-006) |
| Refuse unknown app ids | `AppAccessService` → `APP_NOT_FOUND` |
| Refuse own account for delete, revoke, grant, limit, sessions | `UserAdministration` services (FR-024, FR-020) |
| Limits are whole numbers ≥ 0 | `GuestChatLimit` builder, and the request record |
| Admin is never limited | `GuestChatLimitService` (exempt), and `GuestChatLimitFilter` |
| Delete only after the account is disabled | `UserDeletionService` order (R4) |
