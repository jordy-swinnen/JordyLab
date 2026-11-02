# Guest Feedback and Admin Triage: REST Contract (Phase 1)

All endpoints under `/api/feedback/**`, JWT-authenticated like every `/api/**` route. Gating lives in `shared/config/SecurityConfig.java` (URL matchers, deny-by-default) — there is no method-level security anywhere in the platform and none is introduced here.

## Access matrix

| Endpoint | Guest | Admin | No role |
|---|---|---|---|
| `POST /api/feedback/tickets` | ✅ | 403 | 401/403 |
| `GET /api/feedback/tickets` | ✅ (own only) | 403 | 401/403 |
| `GET /api/feedback/apps` | ✅ | 403 | 401/403 |
| `GET /api/feedback/inbox` | 403 | ✅ | 401/403 |
| `POST /api/feedback/inbox/{id}/accept` | 403 | ✅ | 401/403 |
| `POST /api/feedback/inbox/{id}/backlog` | 403 | ✅ | 401/403 |
| `POST /api/feedback/inbox/{id}/reject` | 403 | ✅ | 401/403 |
| `PUT /api/feedback/inbox/{id}/texts` | 403 | ✅ | 401/403 |

`/api/settings/**` stays `hasRole("admin")` — unchanged (FR-003). The admin's 403 on the guest endpoints is deliberate (FR-001: the admin never sees the guest tab), and `RoleMatrixTest` pins all rows above.

## Guest endpoints

### `GET /api/feedback/apps` — apps the caller may use

```json
{ "apps": [ { "id": "GAME_CATALOG", "name": "Game Catalog" } ] }
```

### `POST /api/feedback/tickets` — submit a ticket

Request (all fields required):

```json
{
  "app": "GAME_CATALOG",
  "type": "BUG_REPORT",
  "title": "Grid loses my scroll position",
  "description": "When I open a game and go back, the grid jumps to the top."
}
```

`type` ∈ `BUG_REPORT | FEATURE_REQUEST | IMPROVEMENT | IDEA`. Title ≤ 120, description ≤ 5 000 characters after trim.

Response `201 Created` — the guest view of the created ticket (same shape as the list item):

```json
{
  "id": "0c1e6f04-8d54-4a4b-9d3f-1e2a5b7c9d10",
  "app": "Game Catalog",
  "type": "Bug report",
  "title": "Grid loses my scroll position",
  "description": "When I open a game and go back, the grid jumps to the top.",
  "state": "NEW",
  "reply": null,
  "createdAt": "2026-11-02T18:40:12Z"
}
```

### `GET /api/feedback/tickets` — the caller's own tickets, newest first

```json
{ "tickets": [ { …guest view as above… } ] }
```

**Guest view fields — the guest may ever see exactly these**: `id`, `app` (display name), `type` (display name), `title`, `description`, `state`, `reply`, `createdAt`. Never: any other sender's data, `sender` names, `privateNote`, `linearIssueUrl`, `acceptError`, any Linear-side progress (FR-013/FR-015/FR-018). `state` is the raw `NEW | ACCEPTED | BACKLOG | REJECTED`.

## Admin endpoints

### `GET /api/feedback/inbox?state=NEW|BACKLOG|ACCEPTED|REJECTED` — all tickets, newest first

`state` optional; omit for all. Admin view (strict superset of the guest view):

```json
{
  "tickets": [
    {
      "id": "0c1e6f04-…",
      "app": "Game Catalog",
      "type": "Bug report",
      "title": "Grid loses my scroll position",
      "description": "…",
      "state": "NEW",
      "reply": null,
      "privateNote": null,
      "sender": "Ada Palmer",
      "createdAt": "2026-11-02T18:40:12Z",
      "linearIssueUrl": null,
      "acceptError": null
    }
  ]
}
```

### `POST /api/feedback/inbox/{id}/accept` — accept into Linear

Request (the optional note **is** the ticket's private note — clarify decision; it is stored on the ticket and included in the Linear issue):

```json
{ "privateNote": "Reproduced on CachyOS too — real bug." }
```

Body optional entirely (`{}` or omitted → no note). Response `200` with the admin view, now `state: "ACCEPTED"`, `linearIssueUrl` set, `acceptError` null.

Idempotent: a repeat call (double click, retry after timeout) returns the stored result without contacting Linear again, or adopts the issue a timed-out first attempt already created (see [linear-client.md](linear-client.md)) — never a second issue.

### `POST /api/feedback/inbox/{id}/backlog` · `POST /api/feedback/inbox/{id}/reject` — no body

`200` with the updated admin view. `reject` is terminal; `backlog` from `NEW` only; both allowed only from non-terminal states.

### `PUT /api/feedback/inbox/{id}/texts` — set the two admin texts

Request (both fields required; empty string clears; wholesale replacement):

```json
 { "reply": "Thanks — accepted, fix is planned.", "privateNote": "" }
```

`200` with the updated admin view. Allowed in any state (a rejected ticket can still carry a reply).

## Error codes (stable strings, house style)

| HTTP | Code | When |
|---|---|---|
| 400 | `VALIDATION` | Blank/oversize title or description; unknown `type`; malformed body |
| 400 | `APP_NOT_ALLOWED` | `app` not in the caller's allowed set (FR-005) |
| 404 | `NOT_FOUND` | Unknown ticket id on inbox endpoints |
| 409 | `INVALID_STATE` | Terminal ticket (accept/backlog/reject on `ACCEPTED`/`REJECTED`, backlog on `BACKLOG`/`ACCEPTED`) |
| 429 | `FEEDBACK_LIMIT_REACHED` | Sixth ticket of the day for this guest (mirrors `CHAT_LIMIT_REACHED`) |
| 503 | `LINEAR_UNAVAILABLE` | Linear unreachable, timed out, credential rejected, or key unconfigured — ticket stays in its prior state with `acceptError` set, retriable (FR-019) |

Error body shape follows the existing controller-local convention: `{ "reason": "CODE" }`.

## Non-goals of this contract

No guest by-id endpoint (the list is the guest's whole surface); no pagination (personal scale); no webhook/status-sync endpoints (explicitly out of scope for v1); no endpoint ever returns the Linear credential or any Linear-side issue state beyond the stored url.
