# Research: Console Player Counts from a Second Source

**Feature**: [spec.md](spec.md) · **Date**: 2026-11-01

All facts below were read from the code on `014-console-player-counts`, from production (read-only queries), or from live
TheGamesDB responses made with the owner's key. The source comparison that chose TheGamesDB is in the draft
(`specs/_drafts/multiplayer-player-counts/spec-draft.md`) and in the spec's Context; it is not repeated here.

## R1. The lookup call

- **Decision**: one `GET https://api.thegamesdb.net/v1/Games/ByGameName` per (game, console), with `name=<catalog title>`,
  `fields=players`, `filter[platform]=<TheGamesDB platform id>` and `apikey=<key>`.
- **Rationale**: measured on 2026-11-01. `fields=players` is required: without it the game records carry `id`, `game_title`,
  `platform`, `region_id`, `country_id`, `release_date` and `developers`, but no `players`. Filtering by platform keeps the result
  to that console (15 of 15 console titles matched this way). The response's top level carries `code`, `status`, `data`,
  `pages`, `remaining_monthly_allowance`, `extra_allowance` and `allowance_refresh_timer` (seconds; 2,606,609 s ≈ 30 days on
  2026-11-01, so the allowance window is monthly). `players` is an integer.
- **Alternatives considered**: `GET /v1/Games/ByGameID` after a search (two calls per game, no gain: the search already returns
  `players`); calling `Platforms` at start-up to map names (spends allowance on every restart, and the names differ from the
  catalog's anyway, see R4).

## R2. The key is echoed back in every response

- **Decision**: the client never logs, stores or returns a response body or a request URI. Failures are logged with the HTTP
  status or the exception class only. Pagination is done by building the `page` parameter, never by following the `pages.next`
  URL; the first page is read, and a further page only when `pages.next` is non-null (at most three pages per lookup).
- **Rationale**: the `pages.current` / `pages.next` URLs in every response contain the full request, **including the key**
  (found while probing, when one probe printed that field; the owner regenerated the key the same day). Spring's `RestClientException` messages can also contain the
  request URI. The existing `IgdbClient` already logs only `exception.getClass().getSimpleName()` for the same reason (its token
  URI carries the client secret); the new client copies that rule.
- **Alternatives considered**: sending the key as a header (TheGamesDB documents only the query parameter); redacting URLs in a
  log filter (a second safety net is welcome, but the rule must hold without it).

## R3. Refusals, the allowance and outages

- **Decision**: a source-level pause, held in memory by the client:
  - HTTP 403 → pause until `allowance_refresh_timer` seconds from now (a refused key answers
    `{"code":403,"status":"Invalid API key was provided.","remaining_monthly_allowance":0,"allowance_refresh_timer":0}`; with a
    timer of 0 the pause lasts one hour, so a fixed key is picked up without a restart).
  - `remaining_monthly_allowance` at or below a reserve (default 20) → pause until the refresh timer runs out.
  - A timeout, a connection failure or HTTP 5xx → pause for 15 minutes.
  - While paused, a lookup answers "deferred": the game is saved without a failed attempt (FR-011) and the derivation moves on.
- **Rationale**: the API answers 403 for a bad key and for a used-up allowance alike, so both must stop calls without charging
  games. The reserve keeps the owner's manual probes and a bulk refresh from racing the monthly cap. The backend runs as one
  replica on the single-node cluster, so in-memory state is enough; after a restart, at most one call is answered 403 before the
  pause is set again.
- **Alternatives considered**: a `SteamRateLimitedException`-style exception that stops the whole batch (that would also stop
  IGDB work for every other game in the batch); persisting the pause (not needed with one replica).

## R4. Console → TheGamesDB platform id

- **Decision**: a fixed map in the new client, keyed by the catalog's platform name (`PlatformCatalog`), checked against the live
  `Platforms` list by an opt-in test tagged `integration-thegamesdb` and run by `./gradlew theGamesDbCheck` (mirrors
  `igdbCheck`). A catalog console with no entry is reported as "console not listed" (FR-012).
- **Rationale**: TheGamesDB's names differ from the catalog's ("Nintendo GameCube" vs "GameCube", "Sony Playstation 2" vs
  "PlayStation 2"), so a name lookup at run time would need the same table anyway. Every console in the catalog has an exact
  TheGamesDB name (read from the live list of 153 platforms on 2026-11-01):

  | Catalog | TheGamesDB | Id | | Catalog | TheGamesDB | Id |
  |---|---|---|---|---|---|---|
  | PlayStation | Sony Playstation | 10 | | Wii | Nintendo Wii | 9 |
  | PlayStation 2 | Sony Playstation 2 | 11 | | Wii U | Nintendo Wii U | 38 |
  | PlayStation 3 | Sony Playstation 3 | 12 | | Nintendo Switch | Nintendo Switch | 4971 |
  | PlayStation 4 | Sony Playstation 4 | 4919 | | Nintendo Switch 2 | Nintendo Switch 2 | 5021 |
  | PlayStation 5 | Sony Playstation 5 | 4980 | | Nintendo DS | Nintendo DS | 8 |
  | PSP | Sony Playstation Portable | 13 | | Nintendo 3DS | Nintendo 3DS | 4912 |
  | PlayStation Vita | Sony Playstation Vita | 39 | | Game Boy Advance | Nintendo Game Boy Advance | 5 |
  | Xbox | Microsoft Xbox | 14 | | Game Boy Color | Nintendo Game Boy Color | 41 |
  | Xbox 360 | Microsoft Xbox 360 | 15 | | Game Boy | Nintendo Game Boy | 4 |
  | Xbox One | Microsoft Xbox One | 4920 | | SNES | Super Nintendo (SNES) | 6 |
  | Xbox Series X\|S | Microsoft Xbox Series X | 4981 | | NES | Nintendo Entertainment System (NES) | 7 |
  | Nintendo 64 | Nintendo 64 | 3 | | Saturn | Sega Saturn | 17 |
  | GameCube | Nintendo GameCube | 2 | | Dreamcast | Sega Dreamcast | 16 |
  | Genesis | Sega Genesis | 18 | | Game Gear | Sega Game Gear | 20 |
  | Sega 32X | Sega 32X | 33 | | Sega CD | Sega CD | 21 |
  | Master System | Sega Master System | 35 | | Atari 2600 | Atari 2600 | 22 |

  `Steam` (TheGamesDB "PC", id 1) is deliberately absent: Steam games are out of scope.
- **Alternatives considered**: a new column on every `PlatformCatalog` entry (touches all 33 entries and the shared catalog for
  one source's ids); mapping by name at run time (see R1).

## R5. Matching and the agreement rule

- **Decision**: an entry matches when `TitleKeys.keyFor(entry.game_title)` equals the game's `titleKey` (the stored
  `TitleKeys` key) on one of the game's consoles. Entries without a `players` value are ignored. Among the matching entries
  that carry a value, the count is stored only when all values are equal; different values → "entries disagree". No matching
  entry with a value → "no exact match". A single agreed value below 2 → "count under 2".
- **Rationale**: FR-003/004/005. `TitleKeys` strips bracketed tags and edition suffixes, so "Mario Party 7 [Nintendo GameCube
  Mic Included]" (4) counts as the same title as the two "Mario Party 7" entries (7, 7). That is what makes Mario Party 7
  disagree and stay unknown (SC-003); a looser rule that dropped bracketed variants would store the wrong 7. A bundle named
  differently ("Mario Party 7 Microphone Bundle", 1) does not match. `IgdbClient` has a second, private normaliser
  (`normalizeTitle`); the new client uses only `TitleKeys`.
- **Alternatives considered**: the highest or the most common value (both store 7 for Mario Party 7, which is wrong); preferring
  a region (the catalog's cleaned titles carry no region).

## R6. Where the count's source is stored

- **Decision**: a new nullable column `game.max_local_players_source` (`IGDB` | `THEGAMESDB`), set whenever a count is set and
  cleared never (counts are never cleared either). A migration backfills `IGDB` for every existing non-null count.
- **Rationale**: production on 2026-11-01 had 62 counts: 41 on games with `multiplayer_source = IGDB` and **21 on games with
  `multiplayer_source = STEAM`** whose counts IGDB filled after the Steam categories set the flags. Their detail page says
  "Multiplayer data: Steam" for a number Steam never gave. Only IGDB writes counts today (`MultiplayerService.fillFromIgdb`,
  `FactsStep.applyIgdb`, `ConsoleGameService`); Steam passes `null` and the AI-guessed values were removed by an earlier
  migration, so the backfill is exact.
- **Alternatives considered**: a new `MultiplayerSource.THEGAMESDB` value (the flags would then claim to come from TheGamesDB,
  which gives no flags); a JSON provenance map per field (more than one field needs).

## R7. Why a game has no count

- **Decision**: a new nullable column `game.player_count_gap` holding the reason the second source left a game without a count:
  `NO_MATCH`, `ENTRIES_DISAGREE`, `COUNT_BELOW_TWO`, `CONSOLE_NOT_LISTED`. It is written only by the second-source step, cleared
  when a count is stored, and left untouched by a deferred lookup. Steam games and games with an unknown flag have no reason
  stored; the Sources page derives theirs ("Steam game, no source", "local multiplayer not known") from the row.
- **Rationale**: FR-013 and the review list need the reason per game. The number and the list must come from the same
  predicate, as `LibraryHealthRepository` already does for covers and descriptions.
- **Alternatives considered**: a separate review table (one reason per game is all that is needed, and it lives and dies with
  the game row); keeping reasons in memory (lost on restart, and the Sources page would disagree with itself).

## R8. Order, eligibility and attempts in `MultiplayerService`

- **Decision**: `deriveOne` becomes Steam categories → IGDB fill → second source, and returns an outcome (`RESOLVED`,
  `UNRESOLVED`, `DEFERRED`) instead of a boolean. The second source runs only when the game has no Steam app ID,
  `localMultiplayer` is `true` (clarification of 2026-11-01) and `maxLocalPlayers` is still null after the IGDB step (which may
  set the flag in the same pass through the game-modes fallback). `UNRESOLVED` records a failed attempt as today; `DEFERRED`
  does not. Within one batch the result per (title key, console) is cached, like the IGDB per-title map. The game's consoles come
  from `GamePlatformService.platformsOf(gameId)`.
- **Rationale**: FR-001, FR-008, FR-011. The bounded backlog query and the bulk run's reset already give "parked after three
  attempts, retried only by the bulk refresh". A per-game manual refresh goes through the same `deriveOne` (assumption in the
  spec).
- **Alternatives considered**: running the second source from `FactsStep` (it runs before the multiplayer step and only for
  facts; the multiplayer backlog is the one place that knows a count is missing).

## R9. Pacing and identification

- **Decision**: at most one call per second (`jordylab.gamecatalog.thegamesdb.min-interval-ms: 1000`), connect and read timeout
  5 s, `User-Agent: JordyLab/1.0 (+https://jordylab.be)`. Settings live under `jordylab.gamecatalog.thegamesdb.*`
  (`base-url`, `timeout-ms`, `min-interval-ms`, `allowance-reserve`); the key is `THEGAMESDB_API_KEY`.
- **Rationale**: TheGamesDB publishes no per-second limit; one per second is polite and costs a bulk refresh (15 to 30
  lookups) under half a minute. The application names itself honestly (spec FR-011).
- **Alternatives considered**: IGDB's 260 ms (IGDB documents 4/s; TheGamesDB documents nothing).

## R10. Allowance budget

- **Decision**: no per-run cap beyond the reserve in R3. A full bulk refresh looks up only eligible games without a count.
- **Rationale**: production has 56 local-multiplayer console games, 15 without a count. One lookup per console (all 15 sit on
  one console each) means 15 calls per bulk refresh, plus up to three per game across background sweeps before it parks
  (≤ 45). SC-006 (≤ 100 per run, ten runs a month) holds with a wide margin; the reserve stops the source before the cap if
  the library grows.

## R11. Showing the source to users

- **Decision**: the game detail page shows the count's source next to the count ("4 · TheGamesDB"), and the existing
  "Multiplayer data" row keeps naming the flags' source. `GameDetailResponse` gains `maxLocalPlayersSource`, and
  `FactSourcesResponse` gains `playerCount`. For LibBot, the fact line the composer receives becomes "up to 4 local players
  (TheGamesDB)", so an answer can name the source; each reference already links to the detail page. The LibBot stream contract
  does not change.
- **Rationale**: FR-006 with the smallest contract change. The golden replay tier replays recorded model outputs
  (`ReplayAiService`), so a changed fact line in the prompt does not invalidate the recordings; the live tier
  (`./gradlew goldenLive`) is the check that a real model still answers well.
- **Alternatives considered**: adding the count and source to every LibBot reference card (a frontend change for a detail most
  answers never need).

## R12. The Sources page

- **Decision**: the library-health response gains a `playerCounts` block (local-multiplayer games, filled per source, unknown
  per reason, and the second source's state: configured, paused until, why). The exceptions endpoint gains the kind
  `PLAYER_COUNT` with an optional `reason`, and each listed game carries its reason. The frontend adds one "Player counts"
  section beside the existing health rows, reusing the same "show the games" pattern.
- **Rationale**: FR-013 and constitution principle II (no silent failures): a paused source must be visible, not only logged.
- **Alternatives considered**: a separate page (the Sources page is where the owner already looks for gaps).

## R13. Testing

- **Decision**: `TheGamesDbClientTest` (WireMock: exact match, disagreement, bracketed variant, missing `players`, count 1,
  second page, 403 pause with and without a timer, low allowance, timeout, unconfigured, and an assertion that no logged line
  contains the key); `MultiplayerServiceTest` (order, eligibility, deferred vs unresolved attempts, cache per title and console,
  Steam games never asked); `GameTest` (count source set with every count, gap cleared on fill); a migration check through the
  module test; `LibraryHealthServiceTest`/repository test for the new counts; frontend specs for the Sources section and the
  detail row; the opt-in `theGamesDbCheck` for the platform map. Synthetic data only in tests; validation on real data goes
  through the real scanner and the real source (quickstart).
- **Rationale**: matches the existing `IgdbClientTest`/`MultiplayerServiceTest` patterns and the constitution's testing rules.
