# Contract: Access Matrix & Authentication Modes

**Feature**: [spec.md](spec.md) · Supersedes the auth sections of 006's and 014's user-management matrix · Endpoints: [identity-api.md](identity-api.md)

One `IdentitySecurityConfiguration` (in the `identity` module) defines two ordered filter chains. Authorization keeps today's URL rules and authority names — the mechanism underneath changes (FR-008).

## Chain 1 — machine token (order 1, stateless, CSRF off)

`securityMatcher`: `POST /api/gamecatalog/ingest/check`, `POST /api/gamecatalog/ingest/scan`.

Authentication: `Authorization: Bearer jlmt_…` → SHA-256 lookup in `identity.machine_token` → non-revoked → grants `ROLE_gamecatalog-scanner` (the single `INGEST` scope). Missing/unknown/revoked token → `401`. A valid token on any other path gets no session and falls through to `denyAll` ('403').

There is no APK/release endpoint after 018 — the former `MOBILE_RELEASE_PUBLISH` scope is gone.

## Chain 2 — browser session (order 2, everything else, CSRF on)

Session authentication via Spring Session JDBC (30-day idle, survives restarts and rolling deploys — SC-003). Authorities from the signed-in `UserAccount`:

| Account state | Authorities |
|---|---|
| `APPROVED`, role `admin` | `ROLE_admin` |
| `APPROVED`, role `guest`, grant `GAME_CATALOG` | `ROLE_guest`, `ROLE_app-gamecatalog` |
| `APPROVED`, role `guest`, no grants | `ROLE_guest` |
| `PENDING` | authenticated, **no authority** → frontend `/awaiting-approval` |
| `REJECTED` / `REVOKED` | cannot sign in (password and passkey both refused) |

URL rules — carried from the current `shared/config/SecurityConfig.java`, plus the identity/passkey additions:

| Request | Rule |
|---|---|
| `DispatcherType.ERROR` | permitAll (kept — hides real error statuses otherwise) |
| `/actuator/health/**`, `/actuator/info` | permitAll |
| `/actuator/metrics/**` | `hasRole("admin")` |
| `/h2-console/**` | permitAll (dev only; kept as today) |
| `/api/identity/auth/**` (register, login, logout, csrf, reset-requests) | permitAll (CSRF on POSTs) |
| `GET /api/identity/me`, `PATCH /api/identity/me`, `/api/identity/auth/change-password` | authenticated |
| `/api/identity/passkeys/**` | authenticated (own credentials only) |
| `/webauthn/authenticate` | permitAll (CSRF on POSTs) |
| `/webauthn/register` | authenticated |
| `/api/identity/users/**`, `/api/identity/machines/**` | `hasRole("admin")` |
| `GET /api/gamecatalog/ingest/client` | `hasRole("admin")` (web download) |
| `POST /api/gamecatalog/ingest/scan`, `POST /api/gamecatalog/ingest/check` | `hasRole("gamecatalog-scanner")` (chain 1) |
| `/api/fna/**`, `/api/settings/**` | `hasRole("admin")` |
| `GET /api/gamecatalog/games/**`, `/api/gamecatalog/platforms`, `/api/gamecatalog/places` | `hasAnyRole("admin","app-gamecatalog")` |
| `PUT /api/gamecatalog/games/*/mark`, `PUT /api/gamecatalog/games/*/installations/*/rom-status` | `hasAnyRole("admin","app-gamecatalog")` |
| `/api/gamecatalog/libbot/**` | `hasAnyRole("admin","app-gamecatalog")` |
| `/api/gamecatalog/**` (rest) | `hasRole("admin")` |
| `/api/**` (anything else) | denyAll |
| anyRequest | denyAll |

Notes:

- `app-gamecatalog` replaces the old `guest`-based catalog access introduced by 014 — a guest without the grant gets `403` on catalog endpoints, exactly as today.
- The in-code `"ROLE_admin"` checks (`LibBotController`, `GuestChatQuotaController`, `GuestChatLimitFilter`) keep working — authority strings unchanged.
- `RoleMatrixTest` is rewritten against this matrix with native auth (no Keycloak container) and stays the executable spec.
- The 60-second access-token bound from 014 is replaced by immediate session deletion on revoke/delete (SC-005) — strictly tighter.

## Role and authority model

| Concept | Values | Where it lives |
|---|---|---|
| Person role | `admin`, `guest`, none | `identity.user_account.role` |
| Account status | `PENDING`, `APPROVED`, `REJECTED`, `REVOKED` | `identity.user_account.status` |
| App grant | `GAME_CATALOG` (grantable; `FNA`/`SETTINGS` are never grantable) | `identity.user_app_grant`, registry `ManagedApp` |
| Machine token scope | `INGEST` | `identity.machine_token.scope` |
| Granted authorities | `ROLE_admin`, `ROLE_guest`, `ROLE_app-gamecatalog`, `ROLE_gamecatalog-scanner` | session (role+grants) or token (scope) |

The Keycloak composite (`admin` ⊃ `guest` + `app-gamecatalog` + scanner) becomes: admin sees everything through `ROLE_admin`; catalog access for non-admins comes from the grant; scanning always needs a machine token (the owner scans with one like every other machine).

## Module boundary

- Public API of `dev.jordy.jordylab.identity`: `AuthenticatedUser` (id, username, role, app grants), `CurrentUserResolver`, `ManagedApp`, events `UserAccessRemoved`, `AppAccessRemoved`, `UserDeleted` (root package).
- Every module resolves the current user through `CurrentUserResolver` — no Spring Security principal types cross the boundary. `shared/event` events move to identity; consumers (`gamecatalog` marks, new `settings` limit/usage cleanup) import from there.
- `shared/config/SecurityConfig.java` is deleted; `shared.config`'s named interface drops it. `settings`' Keycloak admin stack (`KeycloakAdminClient`, `KeycloakUserAdministrationService`, `UserDeletionService`, `UserSessionService`, `PendingSignupWatcherService`, `AppUser`/`UserStatus`) is replaced by the identity module; `GuestChatLimit*` stays in `settings` (fed by events).
