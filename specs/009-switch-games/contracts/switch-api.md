# Contract: Switch Game Administration API

**Base path**: `/api/gamecatalog/switch`

**Authentication/authorization**: All endpoints under `/api/gamecatalog/switch/**` are covered by the existing
`SecurityConfig` catch-all `/api/gamecatalog/**` → `hasRole("admin")`. Guests receive `403 Forbidden` for any request
here. Read access to Switch games happens through the existing `/api/gamecatalog/games*` endpoints (see
[catalog-api.md](./catalog-api.md)).

## Endpoints

### `GET /api/gamecatalog/switch/search?q={title}&limit={limit}`

Search IGDB for Nintendo Switch games by title.

**Request parameters**:

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `q` | String | required | Substring/title to search (at least 3 characters) |
| `limit` | int | 10 | Max results |

**Response** `200 OK`:

```json
[
  {
    "igdbGameId": "196617",
    "title": "Mario Kart 8 Deluxe",
    "coverUrl": "https://images.igdb.com/igdb/image/upload/t_cover_big/co1x7d.jpg",
    "releaseYear": 2017,
    "platform": "Nintendo Switch"
  }
]
```

**Behaviour when IGDB is unconfigured/unavailable**: returns an empty array (`[]`); the UI falls back to the manual
add form with a clear message.

---

### `POST /api/gamecatalog/switch/games`

Add a single Switch game.

**Request body**:

```json
{
  "igdbGameId": "196617",
  "format": "PHYSICAL"
}
```

For a manual entry with no IGDB match:

```json
{
  "title": "Some Regional Release",
  "format": "DIGITAL"
}
```

**Response** `201 Created`:

```json
{
  "gameId": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Response** `409 Conflict` if the game already exists (same `(platform, igdbGameId)` or same `(platform, normalised title)`).

---

### `PATCH /api/gamecatalog/switch/games/{gameId}`

Edit a Switch game's format or relink it to a different IGDB match.

**Request body** (one or both fields):

```json
{
  "format": "DIGITAL",
  "igdbGameId": "196617"
}
```

Setting `igdbGameId` to a new value fills metadata/artwork/multiplayer from IGDB. The AI description is **not**
regenerated. Setting `igdbGameId` to `null` is not allowed.

**Response** `200 OK` with the updated game summary.

**Response** `409 Conflict` if the new `(platform, igdbGameId)` already belongs to another game.

---

### `DELETE /api/gamecatalog/switch/games/{gameId}`

Remove the Switch host installation for a game. The `Game` row is only deleted if no other installations or library
entries remain.

**Response** `204 No Content`.

**Response** `403 Forbidden` for guests.

## Bulk add (US3)

### `POST /api/gamecatalog/switch/bulk/preview`

Match a pasted list against IGDB; **nothing is saved** (US3 AS1). Added by spec 011 BUG-016.

**Request body**: `{"text": "Pikmin 4\nMario Kart 8 Deluxe"}` — one title per line. Lines are trimmed, `™®©` and list
markers (`- `, `• `, `1. `) are removed, empty lines are ignored, case-insensitive duplicates collapse onto the first.
More than **100** distinct lines → `400`.

**Response** `200 OK`:

```json
{
  "lines": [
    {
      "line": "Pikmin 4",
      "status": "MATCH",
      "candidates": [{"igdbGameId": 59843, "title": "Pikmin 4", "releaseYear": 2023, "coverUrl": "…"}],
      "existingGameId": null,
      "include": true
    }
  ]
}
```

`status`: `MATCH` (best candidate has the same title, ticked), `NEEDS_REVIEW` (title differs → ticked; DLC/bundle
wording → unticked), `NO_MATCH` (no candidate, unticked; can still be added by title), `ALREADY_PRESENT` (already on
the Switch, `existingGameId` set, unticked). Up to 5 candidates, best first.

### `POST /api/gamecatalog/switch/bulk/confirm`

Add the ticked lines (US3 AS2). Each line runs the single-add flow in its own transaction.

**Request body**: `{"items": [{"line": "Pikmin 4", "igdbGameId": 59843, "title": null, "format": "DIGITAL"}]}` —
1–100 items; without `igdbGameId` the line is added by `title` (or `line`). `format` is required.

**Response** `200 OK`: `{"added": [SwitchGameResponse…], "alreadyPresent": ["Splatoon 3"], "skipped": [{"line": "…",
"reason": "IGDB game not found"}]}`.

IGDB calls are paced to IGDB's 4 requests/second, so a 40-line preview takes roughly 10 seconds.

## Search scope

`/search` and the bulk preview return IGDB `game_type` main game, standalone expansion, remake, remaster, expanded
game and port (spec 011 BUG-042); DLC, bundles, mods, episodes, seasons, packs and updates are excluded.

## Common error responses

- `400 Bad Request` — malformed body, missing required field, invalid format enum, unknown IGDB id, > 100 lines
- `403 Forbidden` — caller is not admin
- `404 Not Found` — game not found (for edit/delete)
- `409 Conflict` — duplicate game
- `422 Unprocessable Entity` — inconsistent state (e.g. IGDB match not found during a relink)
- `503 Service Unavailable` — IGDB unavailable; the UI degrades to the manual-add path

## Security

All endpoints require the `admin` role via the existing SecurityConfig catch-all. No new public endpoints are added.
