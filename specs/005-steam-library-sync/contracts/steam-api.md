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

Fixtures (`gamecatalog/rest/client/SteamOwnedGamesClientTest`):
`owned-games-normal.json`, `owned-games-empty.json`, `owned-games-error.html`.

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

The sanitised capture is frozen as `family-shared-apps.json` BEFORE the mapping is written
(research action). A placeholder fixture is used until the capture exists.

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

Category IDs (verify against a live response): `2` Single-player, `1` Multi-player, `9` Co-op,
`24` Shared/Split Screen, `36` Online PvP, `37` Shared/Split Screen PvP, `38` Online Co-op,
`39` Shared/Split Screen Co-op. `maxLocalPlayers` is **not** provided by Steam and remains AI/null.

**Rate limit**: pace calls ≥1.5 s apart. On HTTP 429, stop the batch immediately and do **not**
increment `metadata_attempts` (a rate limit is not a game failure); the next pass resumes.

**Tool exclusion**: in addition to the `type != game` check, a static deny-list
(`ToolExclusion`) covers the offline/manifest path — `228980` Steamworks Common Redistributables,
`1070560`/`1391110`/`1628350` Steam Linux Runtime 1.0/2.0/3.0, `1493710` Proton Experimental.
The migration performs a one-off cleanup of already-catalogued tool rows.

Fixtures: `appdetails-game.json`, `appdetails-tool.json`, `appdetails-404.json`, `appdetails-429.json`.
