# Data Model: Authentication Inside the Backend Without Keycloak

**Feature**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md) · **Research**: [research.md](research.md)

All new tables live in a new **`identity`** schema owned by the `identity` module (Flyway migration starting `CREATE SCHEMA IF NOT EXISTS identity; SET search_path TO identity;`). Entities follow the canonical JordyLab structure (`BaseEntity` audit columns, UUID ids, Lombok `@Builder` with guards in `build()`, named mutation methods, `registerEvent`).

## Entity: `UserAccount`

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | `UUID` | PK | **The Keycloak `sub` for migrated users** (FR-011); random UUID for new users — same format, so existing owner columns keep matching |
| `username` | `TEXT` | `NOT NULL`, `UNIQUE`, stored lowercase | Sign-in identifier |
| `display_name` | `TEXT` | nullable | Optional (replaces Keycloak first/last name; no email — FR-001) |
| `password_hash` | `TEXT` | nullable | BCrypt `{bcrypt}`; `NULL` only for migrated users until their first reset; sign-in with a null hash always fails |
| `role` | `TEXT` | `CHECK (role IN ('admin','guest'))`, nullable | `NULL` while `PENDING`; `guest` on approval, `admin` by admin action |
| `status` | `TEXT` | `NOT NULL`, `CHECK (... 'PENDING','APPROVED','REJECTED','REVOKED')` | Stored, never derived (D7) |
| `failed_attempts` | `INT` | `NOT NULL DEFAULT 0` | Lockout (D9) |
| `locked_until` | `TIMESTAMP` | nullable | Lockout expiry |
| audit columns | | `BaseEntity` | inherited |

**App grants** — `identity.user_app_grant` as an `@ElementCollection` of `ManagedApp` on `UserAccount`: `user_account_id UUID NOT NULL`, `app TEXT NOT NULL CHECK (app IN ('GAME_CATALOG'))`, unique `(user_account_id, app)`. Mutations `grantApp(ManagedApp)` / `revokeApp(ManagedApp)` register `AppAccessRemoved`.

**State machine**:

```text
                     register (self, no role, no grants)
                           │
                           ▼
                        PENDING ──approve()──▶ APPROVED (role guest + default grants: GAME_CATALOG)
                           │                            │
                       reject()                     revoke() / delete()
                           ▼                            ▼
                        REJECTED ◀──approve()──       REVOKED
```

- `approve(role)`: any non-`APPROVED` state → `APPROVED`, `guest` by default, re-applies `ManagedApp.defaultGrants()`.
- `reject()`/`revoke()` register `UserAccessRemoved`; the service ends sessions, clears grants on revoke, and refuses the last approved admin.
- Sign-in: `PENDING` → authenticated, no authorities (frontend → `/awaiting-approval`); `APPROVED` → `ROLE_admin` or `ROLE_guest` + `ROLE_app-gamecatalog` per grant; `REJECTED`/`REVOKED` → refused with a status-specific error.
- Named mutations: `recordFailedSignIn()`, `recordSuccessfulSignIn()`, `applyPassword(hash)`, `changeDisplayName(name)`.

## Entity: `UserCredential` (passkeys — framework contract)

Spring Security's `JdbcUserCredentialRepository` reads/writes this table; created by Flyway from the module's `user-credentials.sql` (verify the exact DDL against 7.1.1 during implementation). Columns (Spring's contract): `username`, `credential_id`, `user_id` (the user handle bytes — the UUID bytes of `user_account.id`), `public_key`, `signature_count`, `last_used`, `created`, optional `label`. No entity class of ours; accessed through the repository. Multiple rows per user = multiple passkeys.

## Entity: `PasswordResetRequest`

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | `UUID` | PK | |
| `user_account_id` | `UUID` | `NOT NULL`, FK → `user_account` | |
| `requested_password_hash` | `TEXT` | `NOT NULL` | BCrypt hash of the user-chosen password — the plaintext never persists |
| `status` | `TEXT` | `NOT NULL`, `CHECK ('PENDING','APPROVED','REJECTED')` | |
| `decided_at` | `TIMESTAMP` | nullable | |
| `decided_by` | `UUID` | nullable, FK → `user_account` | The deciding admin |

Partial unique index `ON (user_account_id) WHERE status = 'PENDING'` — one pending request per account. Lifecycle: `PENDING` → `APPROVED` (hash copied via `applyPassword`, request closed) / `REJECTED` (old password keeps working). Own-password change or admin direct reset supersedes it. Unknown usernames never create rows.

## Entity: `MachineToken`

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | `UUID` | PK | |
| `name` | `TEXT` | `NOT NULL` | "Add machine" label |
| `token_hash` | `TEXT` | `NOT NULL`, `UNIQUE` | SHA-256 of the full token |
| `scope` | `TEXT` | `NOT NULL`, `CHECK (scope = 'INGEST')` | Single scope after 018 removed the APK stack (D8) |
| `created_by` | `UUID` | `NOT NULL`, FK → `user_account` | |
| `last_used_at` | `TIMESTAMP` | nullable | Throttled (≤ once/minute) |
| `revoked_at` | `TIMESTAMP` | nullable | Refused on the next call |

Token value (never stored): `jlmt_` + base64url(32 random bytes) = 256 bits. Shown once; only the hash persists.

## Framework-owned session tables (Flyway-created, auto-DDL off)

`identity.spring_session` and `identity.spring_session_attributes` — the standard Spring Session JDBC DDL. The admin session panel reads/ends sessions through `FindByIndexNameSessionRepository` (web sessions only; scanner "sessions" are machine tokens on their own page).

## Settings-module tables (unchanged by this feature)

`settings.guest_chat_usage` and `settings.guest_chat_limit` keep their current shape and behavior. Revoke/delete no longer calls into settings directly: new `settings` listeners on `UserAccessRemoved`/`UserDeleted` clear the personal limit and usage (D17).

## Cutover mapping (Release A, D10)

Guarded `INSERT ... SELECT` from the still-present `keycloak` schema:

| `identity.user_account` | Source |
|---|---|
| `id` | `keycloak.user_entity.id` (the `sub`) |
| `username` | `LOWER(user_entity.username)` |
| `display_name` | first + last name concatenated, when present |
| `password_hash` | `NULL` (reset flow covers first sign-in) |
| `role` | `'admin'` if the user holds `admin` via role mappings, else `'guest'` if `guest`, else `NULL` |
| `status` | `'APPROVED'` for every person user (service accounts skipped) |

| `identity.user_app_grant` | Source |
|---|---|
| `(id, 'GAME_CATALOG')` | users holding the `app-gamecatalog` realm role |

Release B then drops the `keycloak` schema after the verified backup.

## What deliberately does not change

- `gamecatalog.game_mark.user_subject`, `gamecatalog.refresh_run.started_by`, `settings.ai_feature_model_setting.updated_by_subject`, `settings.guest_chat_usage.user_subject` — same values, same queries (id stability).
- `gamecatalog.scan_source` `(machine_id, source_type)` adoption — machine identity was always in the request body.
- `settings.guest_chat_usage` / `guest_chat_limit` rows and services — behavior preserved, cleanup now event-driven.
- Every other schema (`finance`, `gamecatalog` data, `public.event_publication`).
