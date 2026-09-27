# Contract — Access Matrix & Error Shapes

The complete authorization contract after this feature (spec FR-002, research D5/D10/D12). **Deny by default**: anything
not listed here is refused. Enforcement is backend-first; the frontend mirrors it for UX only.

## Backend route → role matrix

| Route                                                                                 | Today                 | After 006                                                       |
|---------------------------------------------------------------------------------------|-----------------------|-----------------------------------------------------------------|
| `GET /actuator/health`, `GET /actuator/info`                                          | permitAll             | permitAll (unchanged)                                           |
| `GET /actuator/metrics/**` (new exposure)                                             | —                     | `admin`                                                         |
| `/h2-console/**`                                                                      | permitAll             | permitAll (dev only, unchanged)                                 |
| `/api/fna/**`                                                                         | authenticated         | `admin`                                                         |
| `/api/settings/**` (new)                                                              | —                     | `admin`                                                         |
| `GET /api/gamecatalog/games/**` (list, detail, artwork)                               | authenticated         | `admin` or `guest`                                              |
| `GET /api/gamecatalog/platforms`, `GET /api/gamecatalog/hosts`                        | authenticated         | `admin` or `guest`                                              |
| `POST /api/gamecatalog/chat`                                                          | authenticated         | `admin` or `guest` **+ settings guest-chat filter** (see below) |
| `POST /api/gamecatalog/games/**` (refresh, metadata, enrichment, multiplayer refresh) | authenticated         | `admin`                                                         |
| `/api/gamecatalog/sources/**` (list, create, update, delete, toggle)                  | authenticated         | `admin`                                                         |
| `/api/gamecatalog/library/**` (Steam sync, family sync, status)                       | authenticated         | `admin`                                                         |
| `POST /api/gamecatalog/ingest/scan`, `POST /api/gamecatalog/ingest/check`             | `gamecatalog-scanner` | `gamecatalog-scanner` (unchanged — device flow)                 |
| `GET /api/gamecatalog/ingest/client`                                                  | `jordylab-user`       | `admin` (client download moves with the UI that links it)       |
| any other request                                                                     | denyAll               | denyAll (unchanged)                                             |

The scanner device flow (Keycloak `gamecatalog-script` client, Device Authorization Grant, offline token scoped to
`gamecatalog-scanner`) is untouched.

## Guest chat limit (settings-owned filter)

Applies to `POST /api/gamecatalog/chat` when the caller holds `guest` and not `admin`:

- Before the call: today's persisted count for `(sub, today)` must be `< jordylab.settings.guest-chat.daily-limit` (
  default **20**, clarified value).
- Exceeded → **429** `{"reason": "CHAT_LIMIT_REACHED", "resetsAt": "2026-09-28T00:00:00+02:00"}` — the only response
  that carries this reason; the frontend renders the friendly "limit reached, resets at …" message and no AI call
  happens.
- After the call: increment only on a 2xx response (failed AI calls don't burn a guest's budget — research D5).
- Admins are exempt; the counter row is never created for them.

## Standard error shapes (all `/api/**`)

| Status | Body                                                | Meaning                                                       | Frontend behavior                                   |
|--------|-----------------------------------------------------|---------------------------------------------------------------|-----------------------------------------------------|
| 401    | (resource-server default)                           | missing/expired token                                         | re-authenticate via Keycloak                        |
| 403    | (resource-server default)                           | role denies the route                                         | not parsed — the frontend uses role signals (below) |
| 400    | `{"reason": "…"}`                                   | domain validation (per-endpoint reasons in the API contracts) | surface the reason                                  |
| 404    | `{"reason": "USER_NOT_FOUND"}` (settings)           | unknown id                                                    | users page row-level error                          |
| 429    | `{"reason": "CHAT_LIMIT_REACHED", "resetsAt": "…"}` | guest daily limit                                             | friendly chat message, no AI call                   |
| 503    | `{"reason": "CHAT_UNAVAILABLE"                      | "KEYCLOAK_UNAVAILABLE"                                        | "GATEWAY_CATALOG_UNAVAILABLE"}`                     | explicit external failure | surface the reason; retry where meaningful |

403 bodies are not relied upon by the UI — role-driven UI state is computed from the token (below), so a guest never
builds a route they cannot call.

## Frontend role contract

- `AuthService` exposes a `roles` signal (`string[]`, parsed from `tokenParsed.realm_access.roles`) next to the existing
  `isAuthenticated`/`username`/`token` signals.
- `roleGuard(role)` (`CanActivateFn`): denies unauthenticated (→ `/login`) and un-authorized (→ role-appropriate page).
  Guards live **inside the domain route libs** so the host app and the two dev harness apps inherit them:
    - `fnaRoutes` → `roleGuard('admin')`
    - `gamecatalogRoutes` → `roleGuard('admin' | 'guest')` at the shell, `roleGuard('admin')` on sources/management
      children, grid/detail/chat open to both
    - `settingsRoutes` → `roleGuard('admin')`
- **Awaiting approval**: an authenticated user whose token carries no app role (`admin`/`guest`) gets the
  awaiting-approval page regardless of the route attempted; no API call they can make is allowed by the matrix above.
- **Nav visibility** (UX mirror of the matrix): guest → Game Catalog group only (Library, Chat; Sources item hidden);
  admin → all groups incl. Settings with the pending-count badge.
- **User menu** (admin + guest): Change password → `login({ action: 'UPDATE_PASSWORD' })`; Edit profile →
  `login({ action: 'UPDATE_PROFILE' })`; Sign out → existing logout.

## Roles & registration (Keycloak realm contract)

- Realm roles after 006: `admin`, `guest`, `gamecatalog-scanner` (+ Keycloak's own defaults). The `jordylab-user` role
  is removed (stop-and-report gate — research D10).
- Registration: `registrationAllowed: true`, `registrationEmailAsUsername: true` (email = login name), password policy (
  e.g. `length(12) and notUsername and notEmail`), no default app role (verified: the realm has no `defaultRole`
  granting one — research §1.3).
- `jordylab-host` (frontend) client unchanged; Register link on the Keycloak login page comes with
  `registrationAllowed` — no frontend code.
- New confidential client `jordylab-backend` (service account) holds `realm-management`: `view-users`, `manage-users`,
  `view-roles`; secret from env, backend-only.
