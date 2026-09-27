# Contract: External Steam Interfaces (005)

Three external interfaces. All responses are treated as untrusted: numeric app IDs only, titles
sanitized + NFC-normalised.

## 1. Owned library — `IPlayerService/GetOwnedGames` (documented)

```
GET https://api.steampowered.com/IPlayerService/GetOwnedGames/v1/
    ?key={STEAM_WEB_API_KEY}
    &steamid={STEAM_ID}
    &include_appinfo=true
    &include_played_free_games=true
    &format=json
```

Response (relevant shape):

```json
{ "response": { "game_count": 2, "games": [
  { "appid": 400, "name": "Portal", "playtime_forever": 0 },
  { "appid": 620,  "name": "Portal 2", "playtime_forever": 12 }
] } }
```

- `response.games` absent / empty → outcome `FAILED` (`EMPTY_RESPONSE`); no rows touched.
- `include_appinfo=true` gives the authoritative **library name** (title authority, FR-012).
- Free/limited titles may be omitted by Steam — that is not an error (edge case).
- **Secret handling**: the key is a query parameter. The client must never log the full URI;
  log method + path + status only. WireMock fixture uses a placeholder key.

Tests: `SteamOwnedGamesClientTest` (WireMock, inline JSON stubs — no separate fixture files, matching
the pattern already used by `SteamAppDetailsClientTest`/`IgdbClientTest`), covering the normal
response, the documented no-`games`-key empty-library shape, a non-array `games` value, entries
missing `appid`, an HTTP error, and an unreadable body.

## 2. Family library — `IFamilyGroupsService` (undocumented)

```
GET https://api.steampowered.com/IFamilyGroupsService/GetFamilyGroupForUser/v1/
    ?access_token={token}&steamid={STEAM_ID}&format=json
GET https://api.steampowered.com/IFamilyGroupsService/GetSharedLibraryApps/v1/
    ?access_token={token}&family_groupid={groupId}&include_own=true&format=json
```

Token is a user access token valid ~24 h, supplied per request; never stored.

**Field names are provisional until confirmed against a sanitised live capture.** Expected shape
(verify before coding):

```json
{ "response": {
  "apps": [
    { "appid": 730, "name": "Counter-Strike 2", "owner_steamids": ["765…"], "exclude_reason": 0 }
  ] } }
```

- Any app with a non-zero `exclude_reason` (not shareable) is **omitted entirely** (Q2).
- `owner_steamids` are resolved to display names when available; stored as `family_owner_names`.
- A response whose shape does not match (missing `response`, non-array `apps`) → `FAILED`
  (`UNKNOWN_RESPONSE`); no rows touched.
- Token rejected (401/403) → `FAILED` (`TOKEN_EXPIRED`), HTTP 502 to the caller.

Tests: `SteamFamilyClientTest` (WireMock, inline JSON stubs) covers the group-id field-name
fallback chain, an excluded (non-shareable) app, entries missing `appid`, a missing group id, a
non-array `apps`, a missing top-level `response` key, and the `401`/`403` → `TOKEN_EXPIRED` and
other-status → `UNKNOWN_RESPONSE` mappings.

## 3. Store metadata — `store.steampowered.com/api/appdetails` (keyless, rate-limited)

```
GET https://store.steampowered.com/api/appdetails
    ?appids={appid}
    &filters=basic,genres,categories,developers,publishers,release_date
```

**Correction (verified)**: `filters=basic` alone does **not** return genres/categories/developers/
publishers/release_date, so the current client produces all-null metadata. The filter set above fixes it.

Fields used:

| Field | Use |
|-------|-----|
| `type` | authoritative non-game exclusion — only `"game"` is catalogued (FR-013) |
| `short_description` | deterministic description for not-installed library games (FR-022) |
| `genres[].description` | joined `, `, cap 200 — deterministic, fill-only |
| `categories[].id` | multiplayer booleans (see below) |
| `developers[]` / `publishers[]` | first member, cap 100 |
| `release_date.date` | parsed year (regex), 1950..2028 |

**Verified against live responses**: Steam reuses/shifts numeric category ids over time, so
`SteamAppDetailsClient` matches on `categories[].description` **text**, not `id`, deriving
`local_multiplayer`/`split_screen`/`online_multiplayer`/`single_player` (see
`data-model.md`'s "local multiplayer metadata" section for the exact text matched).
`categoriesPresent = false` (no `categories` array at all in the response) signals "no data",
distinct from "checked, has no multiplayer categories present". Steam categories never carry a
player count, so `maxLocalPlayers` is only ever set from the IGDB path below — never AI-guessed.

**Rate limit**: pace calls ≥1.5 s apart. On HTTP 429, stop the batch immediately and do **not**
increment `metadata_attempts` (a rate limit is not a game failure); the next pass resumes.

**Tool exclusion**: in addition to the `type != game` check, a static deny-list
(`ToolExclusion`) covers the offline/manifest path — `228980` Steamworks Common Redistributables,
`1070560`/`1391110`/`1628350` Steam Linux Runtime 1.0/2.0/3.0, `1493710` Proton Experimental.
The migration performs a one-off cleanup of already-catalogued tool rows.

Fixtures: `appdetails-game.json`, `appdetails-tool.json`, `appdetails-404.json`, `appdetails-429.json`.

## 4. IGDB — `api.igdb.com/v4` (ROM/non-Steam multiplayer fallback, added within 005)

Used for ROM titles (no `steam_app_id`) and as the fallback when a Steam game's appdetails
response has no `categories` at all. Twitch OAuth client-credentials — `IGDB_CLIENT_ID` /
`IGDB_CLIENT_SECRET`; unconfigured (either blank) degrades to a graceful no-op everywhere.

```
POST https://id.twitch.tv/oauth2/token
    ?client_id={IGDB_CLIENT_ID}&client_secret={IGDB_CLIENT_SECRET}&grant_type=client_credentials
```

Token is cached in memory (`expires_in - 60s` margin) and refreshed on expiry or a live `401`
(retried exactly once); never logged (the token URL carries the secret as a query parameter).

```
POST https://api.igdb.com/v4/games
    Client-ID: {IGDB_CLIENT_ID}
    Authorization: Bearer {token}
    body: search "<title>"; fields name; where version_parent = null & game_type = 0; limit 10;
```

- IGDB migrated its old numeric `category` field to `game_type` — `game_type = 0` is the "main
  game" filter (was `category = 0`).
- Response entries are filtered to **exact** normalized-name matches against the requested title
  (case/punctuation-insensitive), since IGDB is a fuzzy `search`. IGDB holds duplicate same-named
  entries (regions, re-releases); all exact matches are kept, in search order.

```
POST https://api.igdb.com/v4/multiplayer_modes
    body: fields game,platform,offlinecoop,offlinecoopmax,offlinemax,lancoop,campaigncoop,
          onlinecoop,onlinemax,splitscreen,splitscreenonline; where game = (<id1,id2,...>); limit 500;
```

- All exact-name-match ids from the search are queried in **one batched** call (not one call per
  id) — only some duplicate entries carry `multiplayer_modes` rows, so the first id (in search
  order) with data wins.
- Per-platform rows for the same game are OR-aggregated: `local = offlinecoop || lancoop ||
  splitscreen`, `split = splitscreen`, `online = onlinecoop`. `maxLocalPlayers` is only trusted
  (`max(offlinemax, offlinecoopmax)`) when `local` is true — `offlinemax` alone also counts
  alternating/turn-based play (e.g. Super Mario World reports "2 players" with no co-op).

**Secret handling**: neither the client secret nor the access token is ever logged. **Failure
handling**: any non-2xx (other than a `401` mid-flight, which triggers the one retry above), an
unreadable response, or being unconfigured all resolve to an empty result — never an exception
that would fail the caller's batch. Test fixtures: inline WireMock JSON stubs in `IgdbClientTest`
(`/v4/games`, `/v4/multiplayer_modes`, `/oauth2/token`) — no separate fixture files.
