# Contract: Catalog API (frontend ↔ jordylab-be)

Base path: `/api/gamecatalog` — **all endpoints require a Keycloak-issued bearer token** (the host shell's HTTP interceptor adds it; unauthenticated requests get `401`). CORS allowed origins are configured via `jordylab.cors.allowed-origins` in `application.yaml`.

Visibility rule applied to **every** endpoint below: a game is visible iff `presence = INSTALLED` **and** its source is `enabled` (FR-005, FR-024).

---

## GET `/api/gamecatalog/games`

Paginated grid data.

Query params: `search` (optional, case-insensitive substring on title), `platform` (optional, exact), `page` (0-based, default 0), `size` (default 60, max 200).

### Response `200`

```json
{
  "content": [
    {
      "id": "uuid",
      "title": "Super Mario World",
      "platform": "SNES",
      "artworkStatus": "EXTERNAL_URL",
      "artworkUrl": "https://…/Named_Boxarts/Super%20Mario%20World.png",
      "artworkEndpoint": null
    }
  ],
  "page": 0,
  "size": 60,
  "totalElements": 312,
  "totalPages": 6
}
```

- `artworkUrl` set when `artworkStatus = EXTERNAL_URL`; `artworkEndpoint` set to `/api/gamecatalog/games/{id}/artwork` when `LOCAL_UPLOAD`; both null ⇒ frontend renders placeholder (FR-013).
- Sorted by `lower(title)` ascending. SC-006 target: < 1 s at 5,000 rows.

## GET `/api/gamecatalog/games/{id}`

### Response `200`

```json
{
  "id": "uuid",
  "title": "Super Mario World",
  "platform": "SNES",
  "sourceKey": "snes",
  "artworkStatus": "EXTERNAL_URL",
  "artworkUrl": "https://…",
  "artworkEndpoint": null,
  "enrichmentStatus": "ENRICHED",
  "genre": "Platformer",
  "maxLocalPlayers": 2,
  "onlineMultiplayer": false,
  "singlePlayer": true,
  "description": "…",
  "firstSeenAt": "2026-08-02T10:15:00Z"
}
```

`404` when not visible (uninstalled, disabled source, unknown id). `enrichmentStatus ≠ ENRICHED` ⇒ multiplayer fields/description are null and the frontend shows the explicit "description unavailable" state (FR-018).

## GET `/api/gamecatalog/games/{id}/artwork`

Serves a locally uploaded image. `200` image bytes with sniffed `Content-Type` + `X-Content-Type-Options: nosniff` + long-lived `Cache-Control`; `404` otherwise.

## GET `/api/gamecatalog/platforms`

### Response `200`

```json
{ "platforms": ["SNES", "PlayStation 2", "Steam"] }
```

Distinct platforms of **visible** games, sorted — drives the filter chips (FR-012).

## POST `/api/gamecatalog/chat`

### Request

```json
{ "question": "which games support 5-player local co-op?" }
```

`question`: required, 1–1000 chars.

### Response `200`

```json
{
  "answer": "Three installed games support 5+ player local co-op: …",
  "games": [ { "id": "uuid", "title": "…", "platform": "…" } ],
  "noMatch": false
}
```

- `games` = the **actual DB rows** the answer was composed from (never model-invented, R5) — the frontend renders them as links to `/games/{id}` (FR-021).
- `noMatch: true` when the grounded query returned zero rows; `answer` then states the catalog has no match (FR-020, SC-005).

| Error | Status | Body |
|-------|--------|------|
| Question blank/too long | `400` | `{ "reason": "QUESTION_INVALID" }` |
| AI provider unavailable / translation output invalid | `503` | `{ "reason": "CHAT_UNAVAILABLE" }` (FR-022) |

## GET `/api/gamecatalog/sources`

### Response `200`

```json
{
  "sources": [
    {
      "id": "uuid",
      "sourceKey": "jordybox:STEAM",
      "hostname": "jordybox",
      "sourceType": "STEAM",
      "platform": "Steam",
      "enabled": true,
      "lastAttemptAt": "2026-08-06T10:20:00Z",
      "lastSuccessAt": "2026-08-06T10:20:00Z",
      "lastOutcome": "APPLIED",
      "installedGameCount": 412
    }
  ]
}
```

`sources[].sourceKey` is derived as `<hostname>:<sourceType>` at creation time. `path` is gone — the script picks the directory on the host at scan time.

## PUT `/api/gamecatalog/sources/{id}/enabled`

### Request

```json
{ "enabled": false }
```

### Response `200`

```json
{ "id": "uuid", "enabled": false }
```

Effect: immediate — visibility rule hides the source's games from all endpoints; agent learns at its next check-in (FR-024/FR-025). No purge clock starts. `404` unknown id.
