# Contract: Consoles API (replaces the Switch games API)

Base `/api/gamecatalog/consoles`. All endpoints **admin only** (catch-all); guests receive `403`. The Switch-only endpoints
(`/switch/search`, `/switch/games`, `/switch/bulk/*`) are removed; the same capabilities exist per console. `format` / `InstallationFormat` no longer exist anywhere.

## Known consoles (autocomplete)

### `GET /consoles/known?q=play`

Autocomplete over the backend `PlatformCatalog`: consoles from the **fifth generation onward**, including handhelds, ordered by generation then name.
`q` is a case-insensitive match on name or alias; empty `q` returns all.

```json
[ { "name": "PlayStation 2", "family": "PLAYSTATION", "generation": 6, "handheld": false } ]
```

Included (grouped by generation; the list is data in the catalog, extended in one place):
5: PlayStation, Sega Saturn, Nintendo 64, Game Boy Color; 6: PlayStation 2, Xbox, GameCube, Dreamcast, Game Boy Advance; 7: PlayStation 3, Xbox 360,
Wii, Nintendo DS, PSP; 8: PlayStation 4, Xbox One, Wii U, Nintendo Switch, Nintendo 3DS, PlayStation Vita; 9: PlayStation 5, Xbox Series X|S, Nintendo Switch 2.
`"nin"` yields Nintendo 64, Nintendo DS, Nintendo 3DS, Nintendo Switch, Nintendo Switch 2 (and GameCube/Wii/Wii U by brand alias).

## Consoles

| Method + path | Body / result |
|---|---|
| `GET /consoles` | `[{ id, platform, family, name, label, gameCount }]` |
| `POST /consoles` | `{ platform, name? }` → `201` console. `name` defaults to `platform`. `platform` is a catalog name **or** any custom text (neutral colour). `409 NAME_TAKEN` (case-insensitive, across hosts and consoles), `400 NAME_TOO_LONG`. The same platform may be added again under a different name. |
| `PATCH /consoles/{id}` | `{ name }` → console. Same name rules. |
| `GET /consoles/{id}/impact` | `{ games: 41, alsoElsewhere: 12, wouldBeRemoved: 29 }` for the removal dialog |
| `DELETE /consoles/{id}` | Removes the console and its entries; games with no other place are removed (with marks, embedding, local artwork); others stay. `204`. |

## Games on a console

| Method + path | Body / result |
|---|---|
| `GET /consoles/{id}/search?q=mario` | IGDB search **restricted to the console's platform** (the platform's verified IGDB id comes from the catalog, research B4). `[{ igdbGameId, title, releaseYear, genres[], developer, coverUrl, bannerUrl, alreadyOnConsole }]`. Empty when IGDB is unconfigured or the platform is custom. |
| `POST /consoles/{id}/games` | `{ igdbGameId }` **or** `{ title }` → `201 { gameId, title, linkedExisting }`. Resolves the game through `GameIdentityService` (same game on Steam, an emulator or another console is linked, never duplicated). `409 ALREADY_ON_CONSOLE`. No `format`. |
| `PATCH /consoles/{id}/games/{gameId}` | `{ igdbGameId }` re-links to the right database match (merging if that match already exists). |
| `DELETE /consoles/{id}/games/{gameId}` | Removes this console's entry; the game stays if it has another place. `204`. |
| `POST /consoles/{id}/games/bulk/preview` | `{ lines: ["Mario Kart 8 Deluxe", …] }` → per line `{ line, status: MATCHED|NO_MATCH|ALREADY_PRESENT, match? }` (as before, scoped to the console) |
| `POST /consoles/{id}/games/bulk/confirm` | `{ items: [{ line, igdbGameId?, title? }] }` → `{ added, skipped, failed[] }` |

Adding a game triggers the auto-fill worker (facts, artwork, description, embedding) after commit; the response does not wait for any of it
(FR-019). A manual title with no match gets a placeholder and is retried automatically (FR-020, Q5).

## Frontend routes (informational)

`/games/consoles` (list, add console, add game), `/games/consoles/:id/games/bulk`. "Add game" is disabled with an explanation and a shortcut to "Add console"
while there are no consoles.

## Implementation notes (as built, 2026-10-08)

- `ConsoleResponse` is `{ id, platform, family, chip, name, label, gameCount }` where `chip` is the platform chip with its colours.
- `GET /consoles/{id}/games` lists a console's games (`gameId, title, releaseYear, coverUrl, coverEndpoint`); it was missing from the table above.
- Old addresses `/games/chat`, `/games/switch` and `/games/switch/bulk` redirect to LibBot and Consoles in the app router.
