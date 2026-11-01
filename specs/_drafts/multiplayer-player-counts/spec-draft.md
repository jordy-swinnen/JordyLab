# Feature Draft: Player counts for console games from a second source

**Status**: Draft, ready for specification. The source is chosen and measured; nothing blocks specifying it.
**Target path**: `specs/_drafts/multiplayer-player-counts/spec.md`
**Amends**: the game catalog's local-multiplayer derivation (`MultiplayerService`, the IGDB lookup, the `multiplayer_source` and `multiplayer_attempts` fields) and the "player count isn't known yet" note LibBot adds to party-size answers
**Follow-up**: a hand-set player count for the games no source covers is drafted separately (`specs/_drafts/curated-player-counts/`)

> Written from a production check on 2026-11-01 and a measured probe of the candidate sources the same day. Counts below come from the services' own responses; "plausible" is a judgement against general knowledge and still needs the spot check in the success criteria.

## Problem

LibBot judges "what can we play with N people?" from `max_local_players`. After the IGDB lookup was fixed to match titles across articles and editions and to fall back on game modes, 44 of 105 local-multiplayer games still have no player count (about 42 %). The cause is data, not code: IGDB's multiplayer-mode records are missing for many older console titles, and Steam's categories say a game is local multiplayer but never how many players. For those games the answer ends with an honest "unknown", which the owner has called unacceptable for a library of this size.

The 44 games are two different kinds:

- **15 console games** (GameCube, Nintendo 64, PlayStation, PlayStation 2, PlayStation 3): for example Mario Party 6 and 7, WWF No Mercy, Wave Race: Blue Storm, Gauntlet: Dark Legacy, the Tony Hawk and Ratchet & Clank entries. This draft fixes these.
- **29 Steam PC games**: for example Boomerang Fu, PICO PARK, Move or Die, SpeedRunners. No source measured here is reliable for them; they stay unknown in this change (see Out of scope).

## Measured sources

Every source below was tried against the real 44 titles, or its own documentation was read. A service that refuses automated access is dropped, not worked around.

| Source | Outcome | Evidence |
|--------|---------|----------|
| **TheGamesDB** | **Chosen for console games** | See the table below. Free key from the service's own access request, 1,000 calls a month, `players` field on every game record, platforms answered by id. |
| ScreenScraper | Dropped | Its published rule allows the API only in free, distributed apps or with prior authorisation; a personal, non-distributed app is not covered and developer credentials are handed out by hand on a forum. The owner declined. |
| LaunchBox games database | Dropped | No official bulk download, terms page or API on any official page. |
| Libretro "max users" lists | Dropped | Only the Nintendo 64 list exists among the five systems, and entries are keyed by ROM checksum, which the scanner does not send. |
| Wikidata | Dropped | Has a maximum-players property, but a value for none of the 15 console games and for 2 of the 29 Steam games. |
| Co-Optimus | Dropped | Refuses automated reads (HTTP 403). |
| MobyGames | Not pursued | The API page refuses automated reads (HTTP 403), so its pricing, terms and fields could not be read. The owner chose to proceed without it. |
| PCGamingWiki | Dropped earlier | Its API refused arbitrary queries. |

### TheGamesDB on the 15 console games

Matching used the catalog's normalised title key and the game's platform, with no fuzzy matching.

| | |
|---|---|
| Titles tried | 15 |
| Matched | 15 |
| Count returned | 15 |
| Entries agree on one count | 13 |
| Entries disagree | 2: Mario Party 7 (7, 7 and 4) and PlayStation All-Stars Battle Royale (4 and 2) |
| Plausible among the 13 | 12; Ratchet & Clank: Up Your Arsenal (4) is doubtful |

So the rule "store a count only when every matching entry agrees" would fill 13 of 15 and correctly leave Mario Party 7 (whose 7 is wrong) unknown. Typical agreed values: Mario Party 6 is 4, WWF No Mercy 4, Tony Hawk's Pro Skater 2 is 2, Need for Speed: Hot Pursuit 2 is 2, Risk: Global Domination 6.

### TheGamesDB on the 29 Steam games (why they stay out)

24 matched. Nine of those say 1 player although Steam calls the game local multiplayer, and several others look like online limits (Golf With Your Friends 12, Drink More Glurp 20). Only about 9 of the 15 counts of 2 or more look right. The field does not say whether it counts local or online players, so it is not safe for PC games.

## Requirements

Priorities: High, Medium, Low.

- **High**: The derivation asks sources in a fixed order and stops at the first one that has a count: the existing IGDB lookup first, then TheGamesDB for games without a Steam app ID. A source that has flags but no count does not end the search. Steam games keep going through the IGDB lookup only.
- **High**: A count is only ever read from a source's own data field. Counts are never inferred by an AI, never taken from free text, and never guessed. The earlier rule and the migration that removed AI-guessed values stay in force.
- **High**: A count from TheGamesDB is stored only when every entry for the same title on the game's platform gives the same number. If entries disagree, nothing is stored and the game is listed for review (see the Sources page requirement).
- **High**: A count under 2 for a game flagged local multiplayer is treated as no count, since the flag and the count would contradict each other.
- **High**: Titles are matched with the catalog's normalised title key, restricted to the game's platform, and only an exact key match counts. A weaker match is no match, never a guess.
- **High**: Each stored count remembers which source it came from, separately from where the flags came from (today one field covers both, so a game whose flags are from IGDB and whose count is from TheGamesDB would show the wrong source). The game detail page and LibBot's citations can name it.
- **High**: Counts already in the catalog are never overwritten (same fill-only behaviour as today).
- **High**: The source is optional. With no key configured the derivation skips it, logs once, and behaves as it does today. The backend starts without it.
- **High**: The key is stored like the existing IGDB credentials: the example environment file lists the name `THEGAMESDB_API_KEY`, the production value lives in the sealed secrets, and the local value in the untracked environment file. Because the service takes the key as a query parameter, request addresses and error messages that contain them are never logged or shown.
- **High**: External calls respect the 1,000-calls-a-month allowance: only games without a count are looked up, one search per game and platform, paced, and a response that signals an exhausted allowance pauses the source until the allowance refreshes instead of counting as a failure of each game. The application identifies itself honestly in the request.
- **Medium**: A game no source has a count for is parked after the existing bounded number of attempts and retried only by the bulk "Refresh game data" run, as now. It must not loop on every sweep.
- **Medium**: The Sources page shows, per source, how many games it filled, how many it had no count for, and how many remain unknown, and lists the games behind each number, including the games whose entries disagreed.
- **Medium**: The setup is documented in the runbook and the example environment file: which secret exists, where it is kept, how to rotate it, and how to check by behaviour that the source works without printing the key (a platforms request answering success).
- **Medium**: The product docs, the data-source notes and the backlog explanation in the admin UI are updated in the same change.
- **Low**: A lookup whose platform has no mapping on the source side (a console the source does not list) is skipped and reported on the Sources page rather than retried.

## Success criteria

- After a bulk "Refresh game data" run, the share of local-multiplayer console games without a player count drops to under 10 %, measured against the real library on production, and the remainder are listed by title. In the probe, 13 of the 15 would be filled. Steam PC games are measured separately and are not expected to change.
- Every stored count shows its source, and a spot check of at least ten games against the source's own site agrees.
- No stored count is one that the probe judged wrong: Mario Party 7 stays unknown unless the entries agree.
- A party-size LibBot question names fewer unknown games than before, and the note still appears for the rest.
- With the key unset, nothing changes compared with today and the backend starts normally.
- No count in the database exists that did not come from a source's own field.

## Out of scope

- Steam PC games: no source measured here is reliable for them. A hand-set count for them and for source errors is the follow-up draft; research into a better PC source is part of that draft too.
- Having an AI estimate or extract counts, scraping pages for which no access has been agreed.
- Accessories such as a multitap: the library is emulated, so the game's own maximum applies and no accessory logic is needed.
- Changing how flags (local, split screen, online) are derived, online or co-op player limits.

## Open items

- **Terms of use (check during planning, not blocking)**: the public pages of the service show no terms page, and the key was granted through its own access request. While signed in, the owner reads what the key page says about permitted use and the plan records it. If it does not allow this use, the source is dropped.
- A second console source was searched for and none was found that is reliable and open; the Sources page numbers will show whether one is needed after the first run.

## Verified in the repo

- Today `MultiplayerService` asks Steam categories first (flags only, never a count), then the IGDB lookup when a count or flag is still missing. A new source plugs in as one more fill step after the IGDB one; the IGDB step currently returns "not resolved" to mean both "nothing found" and "found but no count", so the chain must fall through to the next source before a failure is recorded.
- The fill-only rule lives in `Game`: the count is set only while it is null, and the source only while it is null or unknown. There is no marker that tells a hand-set count from a source value, because nothing can set one by hand yet (the follow-up draft adds it).
- `multiplayer_source` is a single text column (Steam, IGDB, unknown) for flags and count together, with no database constraint, so a new value needs no schema change but the count still needs its own source field.
- Attempts are capped by the metadata maximum attempts (3). The bulk refresh resets the counter for every game; the background sweep runs the bounded backlog every time. A game with flags but no count stays in the backlog until parked.
- A game is a console game when it has no Steam app ID; its platform comes from its installations and console entries (`GamePlatformService`), which the multiplayer step does not receive today and must be given.
- The IGDB credentials are read from `IGDB_CLIENT_ID` / `IGDB_CLIENT_SECRET`, gated by an "is configured" check, paced at 260 ms, never logged, and listed by name in the example environment file, the production sealed secrets and the runbook. The new key follows the same pattern.
- The IGDB client has two title normalisations (the shared title key and a private one); the new source uses the shared title key.
- The Sources page shows library health (covers, descriptions, search index) with the games behind each number, but nothing about multiplayer data; per-source counts are new work.
