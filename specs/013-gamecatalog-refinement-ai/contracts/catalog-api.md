# Contract: Catalog API (library, places, marks, ROM status, hosts, sources, refresh runs)

Base path `/api/gamecatalog`. JSON. Auth: Keycloak bearer. "R" = `admin` or `guest`; "A" = `admin` only. Everything not listed keeps its
current behaviour. Unlisted `/api/gamecatalog/**` stays admin-only (SecurityConfig catch-all).

## Removed

| Endpoint | Replaced by |
|---|---|
| `POST /chat` | `POST /libbot/ask` ([libbot-api.md](./libbot-api.md)) |
| `POST /games/refresh` ("Refresh pending data") | `POST /refresh-runs` and the automatic auto-fill worker |
| `GET /hosts` | `GET /places` |
| `/switch/**` | `/consoles/**` ([consoles-api.md](./consoles-api.md)) |

## Library

### `GET /games` (R)

Query (all optional, repeatable where marked `*`):

| Param | Values | Notes |
|---|---|---|
| `search` | text | title substring |
| `platform*` | canonical platform names | any-of |
| `where*` | place ids (host id or console id) | any-of; replaces `host` |
| `installStatus` | `INSTALLED` (default) \| `NOT_INSTALLED` \| `ALL` | |
| `source*` | `STEAM_OWNED`, `STEAM_FAMILY`, `EMULATED`, `CONSOLE` | any-of; replaces `librarySource` |
| `minLocalPlayers` | 2..8 | confirmed matches only; see `unknownPlayerCount` |
| `romStatus*` | `UNKNOWN`, `VALIDATED`, `BROKEN` | game matches when at least one emulated copy has it |
| `mark*` | `WANT_TO_PLAY`, `PLAYED_LIKED`, `PLAYED_DISLIKED` | any-of |
| `markScope` | `ALL` (default) \| `MINE` | whose marks `mark` looks at |
| `sort` | `TITLE` (default) \| `MOST_WANTED` \| `MOST_LIKED` | |
| `page`, `size` | | as today |

Response: page envelope as today; `content[]` items:

```json
{
  "id": "…", "title": "Baldur's Gate: Dark Alliance",
  "platforms": [ {"name": "PlayStation 2", "family": "PLAYSTATION", "background": "#0070D1", "foreground": "#FFFFFF"} ],
  "sources": ["STEAM_FAMILY", "EMULATED"],
  "installStatus": "INSTALLED",
  "cover": { "status": "EXTERNAL_URL", "externalUrl": "…", "localUrl": null },
  "localMultiplayer": true,
  "votes": { "wantToPlay": 5, "playedLiked": 2, "playedDisliked": 0 },
  "myMark": "WANT_TO_PLAY",
  "romSummary": { "state": "MIXED", "validated": 1, "broken": 0, "unknown": 1, "total": 2 }
}
```

`romSummary` is `null` for games with no emulated copy. `state` is `UNKNOWN | VALIDATED | BROKEN | MIXED`. The envelope gains
`"unknownPlayerCount": 31` only when `minLocalPlayers` is set: how many visible games were left out because their count is unknown.

### `GET /games/{id}` (R)

Adds to the existing detail: `platforms[]`, `sources[]`, `votes`, `myMark`, `description`, `factSources` and `places[]`:

```json
"description": { "text": "Aseprite is a pixel art …", "source": "AI", "model": "anthropic/claude-sonnet-5", "requestedModel": "jev-router", "writtenAt": "2026-10-07T10:12:00Z" },
"factSources": { "facts": "STEAM", "multiplayer": "IGDB" }
```

`description.source` is `AI` or `STEAM` (or the whole object is `null`). For `AI`, `model` is what the provider reported (null for text written before this feature),
`requestedModel` is present only when it differs (a router, or the provider fallback). The frontend builds the heading from these fields; it contains no
vendor or model name of its own. `factSources` feeds the Spec sheet's source note (FR-061).

```json
"places": [
  { "kind": "HOST_COPY", "installationId": "…", "hostId": "…", "label": "Living room PC",
    "platform": "PlayStation 2", "installed": true, "romStatus": "VALIDATED" },
  { "kind": "STEAM_LIBRARY", "librarySource": "FAMILY", "familyOwners": "Anna" },
  { "kind": "CONSOLE", "consoleId": "…", "label": "Nintendo Switch", "platform": "Nintendo Switch" }
]
```

`label` is `Host.label()`: the display name when set, otherwise the hostname (FR-030). The raw hostname is **not** returned here: it is shown only as secondary text on the Sources page (FR-031).
`romStatus` is present only on `HOST_COPY` places whose source is an emulation source. `format`/`hostFormats` are gone.

### `GET /platforms` (R)

Platforms present in visible games, each with its chip colours:
`[{ "name": "PlayStation 2", "family": "PLAYSTATION", "background": "#0070D1", "foreground": "#FFFFFF", "border": null }]`.
Colours come from the backend `PlatformCatalog`; the frontend hard-codes none.

### `GET /places` (R)

Hosts and consoles that currently hold visible games, for the "Where" filter: `[{ "id": "…", "kind": "HOST"|"CONSOLE", "label": "Living room PC" }]`.

## Marks

### `PUT /games/{id}/mark` (R)

Body `{ "mark": "WANT_TO_PLAY" | "PLAYED_LIKED" | "PLAYED_DISLIKED" | null }`. Sets, replaces or (null) clears the **caller's** mark; the caller is the
JWT subject. `200` with `{ "votes": {…}, "myMark": … }`. `404` if the game is not visible. Totals are public; no response ever lists voters.

## ROM status

### `PUT /games/{id}/installations/{installationId}/rom-status` (R)

Body `{ "status": "UNKNOWN" | "VALIDATED" | "BROKEN" }`. `200` with the updated place. `409 ROM_STATUS_NOT_APPLICABLE` when the installation is not on an
emulation source. `404` when the installation does not belong to the game or is not visible. Last write wins.

## Hosts and sources

### `GET /sources` (A)

Scan sources only (Steam libraries and emulation scans); consoles and the Switch entry never appear.

```json
{ "sources": [ { "id": "…", "sourceKey": "cachyos-htpc:EMUDECK", "sourceType": "EMUDECK",
  "hostId": "…", "hostname": "cachyos-htpc", "displayName": "Living room PC", "label": "Living room PC",
  "enabled": true, "installedCount": 161, "lastSuccessAt": "…", "lastCheckedAt": "…", "lastOutcome": "APPLIED" } ] }
```

The exception lists behind those counts: `GET /sources/health/exceptions?kind=COVER|DESCRIPTION|INDEX` (A) returns `[{ "gameId": "…", "title": "…" }]`.

Also returns `"health": { "gamesWithoutCover": 12, "gamesWithoutDescription": 3, "gamesPendingIndex": 0, "totalGames": 228 }` for the
Sources page (FR-021).

### `PUT /hosts/{id}/display-name` (A)

Body `{ "displayName": "Living room PC" | null }`. Applies to every source of that host. `200` with the host. `409 NAME_TAKEN` (case-insensitive, hosts and
consoles), `400 NAME_TOO_LONG` (> 40). A blank string clears the name.

### `PUT /sources/{id}/enabled` (A): unchanged.

### `GET /sources/{id}/hide-impact` (A)

`{ "hiddenGames": 87, "stillVisibleElsewhere": 5 }`: games that would become hidden if this source were disabled (only place), and games that stay visible
through another place. Used by the confirmation dialog (US12 / FR-029).

## Refresh runs (A)

### `POST /refresh-runs`

Body `{ "kind": "DATA" | "AI" }`. Starts a run for every visible game. `201` with the run. `409 RUN_ALREADY_ACTIVE` if a run of that kind is `RUNNING`.
The AI kind requires `"confirmCost": true`; without it `400 COST_CONFIRMATION_REQUIRED` with `{ "games": 228 }` so the dialog can state the count.

### `GET /refresh-runs/current?kind=DATA|AI`

Latest run of that kind (running or last finished), or `204`. The UI polls every 2 s while `status = RUNNING`.

```json
{ "id": "…", "kind": "AI", "status": "RUNNING", "total": 228, "processed": 91, "failed": 2,
  "failureSummary": null, "startedAt": "…", "finishedAt": null }
```

### `POST /refresh-runs/{id}/stop`

Sets `stopRequested`; the run ends after the current game with `STOPPED`. `200` with the run; `409` if already finished.

Semantics: `DATA` re-fetches facts and artwork for every game (never overwrites what the admin hand-corrected: `TitleSource.MANUAL`, relinked ids);
`AI` regenerates descriptions and AI-derived facts. Both commit per game, continue past individual failures, and stop early after 5 consecutive failures
with the same cause (`INSUFFICIENT_CREDITS`, `AUTH_FAILED`), recording it in `failureSummary`.

## Security rows (added to `RoleMatrixTest`)

| Endpoint | admin | guest |
|---|---|---|
| `GET /games`, `/games/{id}`, `/platforms`, `/places` | yes | yes |
| `PUT /games/{id}/mark`, `PUT .../rom-status` | yes | yes |
| `GET /sources`, `PUT /hosts/*/display-name`, `PUT /sources/*/enabled`, `/sources/*/hide-impact` | yes | **403** |
| `/refresh-runs/**` | yes | **403** |
| `/consoles/**` | yes | **403** |

## Implementation notes (as built, 2026-10-08)

Differences from the text above that the code settled on; the code and its tests are the reference.

- **Covers are flat fields**, not a nested `cover` object: summaries carry `coverStatus`, `coverUrl` (external address) and `coverEndpoint` (served artwork), the same names the app already used; details add `bannerStatus`, `bannerUrl`, `bannerEndpoint`.
- **Marks** on the summary are `votes{wantToPlay, playedLiked, playedDisliked}`, `myMark` and `romSummary` as written; the detail response also carries `votes` and `myMark`, and every response that returns a game detail (including the admin refresh endpoints) is computed for the caller.
- **Description** on the detail is the object `description{text, source, model, requestedModel, writtenAt}` plus `factSources{facts, multiplayer}`; `metadataSource` stays for the existing "has catalog data" check.
- **`GET /platforms`** items are the chip objects `{name, family, background, foreground, border}`; **`GET /places`** wraps the list: `{ "places": [ … ] }`.
- **`GET /sources`** items also carry `platformChip` so the Sources page paints the platform chip without a colour table of its own.
- **`PUT /games/{id}/mark`** answers `{ votes, myMark }`; **`PUT …/rom-status`** answers the updated place (`PlaceResponse`).
- **`UserAccessRemoved`** is published from `settings` but declared in `shared.event`, because `gamecatalog` listening to an event in `settings` would be a module cycle (`settings` already listens to `gamecatalog`).
- **`POST /refresh-runs`** takes `confirmCost` as an optional boolean (absent = false); `409 RUN_NOT_RUNNING` is returned when stopping a finished run.
