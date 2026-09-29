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

## Future endpoints

Bulk paste preview/confirm (`POST /api/gamecatalog/switch/bulk/preview` and `POST /api/gamecatalog/switch/bulk/confirm`)
is planned but not implemented in this PR; it will be added in a follow-up.

## Common error responses

- `400 Bad Request` — malformed body, missing required field, invalid format enum
- `403 Forbidden` — caller is not admin
- `404 Not Found` — game not found (for edit/delete)
- `409 Conflict` — duplicate game
- `422 Unprocessable Entity` — inconsistent state (e.g. IGDB match not found during a relink)
- `503 Service Unavailable` — IGDB unavailable; the UI degrades to the manual-add path

## Security

All endpoints require the `admin` role via the existing SecurityConfig catch-all. No new public endpoints are added.
