# Contract: Catalog API (frontend ↔ jordylab-be) — 004 refinements

**Amends** `specs/002-game-catalog/contracts/catalog-api.md` (and the chat/artwork sections superseded there). The ingest API (`specs/003-gamecatalog-python-scanner/contracts/ingest-api.md`) is **unchanged** by 004 — the scan client needs no update.

Base path: `/api/gamecatalog` — all endpoints require a Keycloak-issued bearer token (unchanged).

**Visibility rule (restated with the multi-host model)**: a game is visible iff it has at least one `game_installation` with `presence = INSTALLED` whose `scan_source` is `enabled`. Every endpoint below applies this rule. A host link hidden by a disabled source no longer hides the whole game if another enabled host still serves it.

---

## GET `/api/gamecatalog/games`

Paginated grid data.

Query params: `search` (optional, case-insensitive substring on title), `platform` (optional, exact), **`host` (optional, exact hostname — new in 004: only games with an installed installation on an enabled source of that hostname)**, `page` (0-based, default 0), `size` (default 60, max 200). Filters combine with AND semantics.

### Response `200`

```json
{
  "content": [
    {
      "id": "uuid",
      "title": "Super Mario World",
      "platform": "SNES",
      "coverStatus": "EXTERNAL_URL",
      "coverUrl": "https://…/Named_Boxarts/Super%20Mario%20World.png",
      "coverEndpoint": null
    }
  ],
  "page": 0, "size": 60, "totalElements": 312, "totalPages": 6
}
```

- `artworkStatus`/`artworkUrl`/`artworkEndpoint` are renamed to `coverStatus`/`coverUrl`/`coverEndpoint` (the portrait, card-fitted slot). `coverUrl` set when `coverStatus = EXTERNAL_URL`; `coverEndpoint` set to `/api/gamecatalog/games/{id}/artwork` when `LOCAL_UPLOAD`; both null ⇒ placeholder plate.
- Steam covers resolve to Steam CDN portrait library art (`library_600x900` family) — never `header.jpg`-style wide images.

## GET `/api/gamecatalog/hosts` — new in 004

### Response `200`

```json
{ "hosts": ["jordybox", "ryzen-desktop"] }
```

Distinct hostnames of visible games (sorted) — drives the host filter chips (FR-009).

## GET `/api/gamecatalog/games/{id}`

### Response `200`

```json
{
  "id": "uuid",
  "title": "Super Mario World",
  "platform": "SNES",
  "coverStatus": "EXTERNAL_URL",
  "coverUrl": "https://…",
  "coverEndpoint": null,
  "bannerStatus": "EXTERNAL_URL",
  "bannerUrl": "https://…/Named_Snaps/Super%20Mario%20World.png",
  "bannerEndpoint": null,
  "enrichmentStatus": "ENRICHED",
  "genre": "Platformer",
  "genres": "Platformer, Action",
  "developer": "Nintendo",
  "publisher": "Nintendo",
  "releaseYear": 1990,
  "metadataSource": "AI",
  "maxLocalPlayers": 2,
  "onlineMultiplayer": false,
  "singlePlayer": true,
  "description": "…",
  "hosts": [
    { "hostname": "jordybox", "sourceType": "EMUDECK" }
  ],
  "firstSeenAt": "2026-08-02T10:15:00Z"
}
```

- `banner*` — the wide hero slot (FR-002): `bannerUrl` when `bannerStatus = EXTERNAL_URL`, `bannerEndpoint` when `LOCAL_UPLOAD`; both null ⇒ the frontend renders the styled banner plate (never a stretched cover).
- `genres`/`developer`/`publisher`/`releaseYear` — deterministic fields (FR-010/FR-011), each null when unknown, omitted from rendering, never fabricated. `metadataSource` is `"STEAM"` (fetched from Steam store data) or `"AI"` (enrichment-produced) — shown as provenance in the spec sheet.
- `hosts[]` — the game's installed host links (FR-012): `hostname` + `sourceType` per source with an installed installation; empty never happens for a visible game (≥ 1 by the visibility rule).
- `404` when not visible (no installed installation on an enabled source, unknown id). `enrichmentStatus ≠ ENRICHED` ⇒ multiplayer fields/description null, explicit "description unavailable" state (unchanged, FR-018 of 002).

## GET `/api/gamecatalog/games/{id}/artwork`

Serves locally stored artwork (unchanged from 002) — now semantically the **cover** slot. Banner has no local upload in v1 (external or plate only).

## GET `/api/gamecatalog/platforms`

Unchanged from 002.

## POST `/api/gamecatalog/chat`

### Request — new optional field in 004

```json
{
  "question": "is this good for 4 players on the couch?",
  "gameIds": ["uuid"]
}
```

- `question`: required, 1–1000 chars (unchanged).
- `gameIds`: optional, max 5 UUIDs — the attached games (FR-013). Each must be **visible** at ask time, else `400`.

### Response `200` — unchanged shape

```json
{
  "answer": "Yes — **Castle Crashers** supports 4-player local co-op…",
  "games": [ { "id": "uuid", "title": "…", "platform": "…" } ],
  "noMatch": false
}
```

- `games` = attached games ∪ the DB rows the answer was composed from (attached games are always cited, FR-014).
- `noMatch: true` only when there are **no attachments and** the grounded query returned zero rows; with attachments, the answer composes from the attached rows.
- The grounded filter now also accepts the deterministic fields: genres substring, developer substring, release-year range (`releaseYearMin`/`releaseYearMax`), and hosts — translated from the question with the same strict-JSON + visible-platforms/-hosts validation posture as 002 (extended allowed-field set).

| Error | Status | Body |
|-------|--------|------|
| Question blank/too long | `400` | `{ "reason": "QUESTION_INVALID" }` |
| `gameIds` > 5, malformed, or any id not visible | `400` | `{ "reason": "GAME_IDS_INVALID" }` |
| AI provider unavailable / translation output invalid | `503` | `{ "reason": "CHAT_UNAVAILABLE" }` (unchanged) |

## GET `/api/gamecatalog/sources` / PUT `/api/gamecatalog/sources/{id}/enabled`

Unchanged from 002/003 in shape and semantics — with the 004 visibility rule, disabling a source hides only that host's installations; games still installed via another enabled host remain visible (FR-006).
