# Contract: Catalog API changes (inbound)

Additive changes to the endpoints in `specs/013-gamecatalog-refinement-ai/contracts/catalog-api.md`. Nothing is renamed or
removed; existing clients keep working. Access rules are unchanged (`/api/gamecatalog/sources/**` is admin only; game detail is
readable by `admin` and `guest`).

## `GET /api/gamecatalog/games/{id}`: game detail

Two additions:

```json
{
  "maxLocalPlayers": 4,
  "maxLocalPlayersSource": "THEGAMESDB",
  "multiplayerSource": "IGDB",
  "factSources": { "facts": "AI", "multiplayer": "IGDB", "playerCount": "THEGAMESDB" }
}
```

| Field | Values | Meaning |
|-------|--------|---------|
| `maxLocalPlayersSource` | `IGDB`, `THEGAMESDB`, `null` | Source of `maxLocalPlayers`; `null` exactly when the count is `null`. |
| `factSources.playerCount` | same | Same value, for the Spec sheet's source note. |

`multiplayerSource` / `factSources.multiplayer` keep meaning the source of the flags. The frontend shows the count with its
source ("Local players: 4 · TheGamesDB") and keeps the "Multiplayer data" row for the flags.

## `GET /api/gamecatalog/sources`: library health block

`health` gains `playerCounts`:

```json
"health": {
  "totalGames": 509,
  "gamesWithoutCover": 0,
  "gamesWithoutDescription": 3,
  "gamesPendingIndex": 0,
  "playerCounts": {
    "localMultiplayerGames": 105,
    "filled": { "IGDB": 61, "THEGAMESDB": 13 },
    "unknown": 31,
    "unknownByReason": {
      "STEAM_NO_SOURCE": 29,
      "ENTRIES_DISAGREE": 2,
      "NO_MATCH": 0,
      "COUNT_BELOW_TWO": 0,
      "CONSOLE_NOT_LISTED": 0,
      "NOT_LOOKED_UP_YET": 0
    },
    "theGamesDb": {
      "configured": true,
      "pausedUntil": null,
      "pauseReason": null,
      "remainingAllowance": 938
    }
  }
}
```

(Numbers illustrate the expected state after the first bulk refresh; they are not seeded.)

- Counted over **visible** games (same `GameVisibility` predicate as the other health numbers) with `localMultiplayer = true`.
- `filled` counts by `max_local_players_source`.
- `unknownByReason`: `STEAM_NO_SOURCE` = a Steam app ID and no count; the four stored reasons from `player_count_gap`;
  `NOT_LOOKED_UP_YET` = a console game without count and without a stored reason (not asked yet, or the source was paused or
  unconfigured). The reasons add up to `unknown`.
- `theGamesDb.pauseReason`: `KEY_REFUSED_OR_ALLOWANCE_USED`, `ALLOWANCE_LOW`, `UNREACHABLE` or `null`.
- Games whose local flag is unknown are not in this block at all (they are not local-multiplayer games yet).

## `GET /api/gamecatalog/sources/health/exceptions`: games behind a number

New kind `PLAYER_COUNT` with an optional `reason` (one of the `unknownByReason` keys). Without `reason`, every unknown game is
listed. Each listed game carries its reason; the existing kinds return `reason: null`.

```http
GET /api/gamecatalog/sources/health/exceptions?kind=PLAYER_COUNT&reason=ENTRIES_DISAGREE
```

```json
{
  "kind": "PLAYER_COUNT",
  "total": 2,
  "games": [
    { "id": "…", "title": "Mario Party 7", "reason": "ENTRIES_DISAGREE" },
    { "id": "…", "title": "PlayStation All-Stars Battle Royale", "reason": "ENTRIES_DISAGREE" }
  ]
}
```

An unknown `reason` value → `400` (same handling as an unknown `kind` today).

## LibBot (`POST /api/gamecatalog/libbot/ask`)

No contract change. The composer's fact line for a game with a count becomes `up to 4 local players (TheGamesDB)` or
`up to 4 local players (IGDB)`, so the answer can name the source; references keep linking to the detail page.
