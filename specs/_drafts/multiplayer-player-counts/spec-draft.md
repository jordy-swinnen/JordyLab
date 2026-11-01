# Feature Draft: Player counts from more than one source

**Status**: Draft, direction agreed (add sources beyond IGDB; counts stay deterministic)
**Target path**: `specs/_drafts/multiplayer-player-counts/spec.md`
**Amends**: the game catalog's local-multiplayer derivation (`MultiplayerService`, the IGDB lookup, the `multiplayer_source` and `multiplayer_attempts` fields) and the "player count isn't known yet" note LibBot adds to party-size answers

> Written from a production check on 2026-11-01 plus a first round of source research. The research did not confirm that any alternative source returns usable counts for this library. Verify the list at the bottom before planning.

## Problem

LibBot judges "what can we play with N people?" from `max_local_players`. After the IGDB lookup was fixed to match titles across articles and editions and to fall back on game modes, 44 of 105 local-multiplayer games still have no player count (about 42 %). The cause is data, not code: IGDB's multiplayer-mode records are missing for many older console titles, and Steam's categories say a game is local multiplayer but never how many players. For those games the answer ends with an honest "unknown", which the owner has called unacceptable for a library of this size.

The 44 games are two different kinds, and this draft only addresses the first:

- **Console ROMs** (roughly half): for example the Mario Party titles, WWF No Mercy, Wave Race, Gauntlet, the Tony Hawk and Ratchet & Clank entries.
- **Steam PC games** (roughly half): for example Boomerang Fu, PICO PARK, Move or Die, SpeedRunners, Golf With Your Friends.

## Requirements

- The derivation asks sources in a fixed order and stops at the first one that has a count: the existing IGDB lookup first, then a console source for ROM games. A source that has flags but no count does not end the search. Steam games keep going through the IGDB lookup only; no further PC source is part of this draft.
- A count is only ever read from a source's own data field. Counts are never inferred by an AI, never taken from free text, and never guessed. The earlier rule and the migration that removed AI-guessed values stay in force.
- Each stored count remembers which source it came from, and the game detail page and LibBot's citations can say so. Counts the owner has set by hand are never overwritten (same fill-only behaviour as today).
- A game no source has a count for is parked after a bounded number of attempts and is retried only by the bulk "Refresh game data" run, as now. It must not loop on every sweep.
- Each new source is optional: with no credentials configured the derivation skips it, logs once, and behaves as it does today. The backend must start without them.
- Credentials and keys are stored as secrets in the same way as the existing IGDB credentials and are never logged or echoed.
- External calls respect each service's published rate limits and terms, identify the application honestly, and use only the access the service grants for this kind of use. A source that refuses access is dropped from the plan, not worked around.
- The matching of a catalog title to a source's record uses the same normalised title key as the IGDB lookup. A match below a clear confidence threshold is treated as no match rather than guessed.
- The Sources page shows, per source, how many games it filled, how many it had no count for, and how many remain unknown.
- The product docs, the data-source notes and the backlog explanation in the admin UI are updated in the same change.

## Candidate sources (to confirm by a real probe before committing)

| Kind | Candidate | What is known | What is not |
|------|-----------|---------------|-------------|
| Console ROMs | ScreenScraper | Its game-info response carries a players field that other ROM tools read | Official documentation, how to obtain developer access, rate limits, coverage for this library |
| Console ROMs | TheGamesDB | A third-party wrapper lists a players field | Whether it is filled for older consoles, the key process |
| Console ROMs | LaunchBox games database | Community posts say it holds a max players value per game | Download or access route, licence, current field layout |

None of these is a decision. The first planning step is a time-boxed probe of each against the 44 games above, reported as a coverage table, before any integration is built.

## Success criteria

- After a bulk "Refresh game data" run, the share of local-multiplayer console ROM games without a player count drops to under 10 %, measured against the real library on production, and the remainder are listed by title. Steam PC games are measured separately and are not expected to improve from this change alone.
- Every stored count shows its source, and a spot check of at least ten games against the source's own site agrees.
- A party-size LibBot question names fewer unknown games than before, and the note still appears for the rest.
- With every new source unconfigured, nothing changes compared with today, and the backend starts normally.
- No count in the database exists that did not come from a source's own field.

## Out of scope

Having an AI estimate or extract counts, scraping pages for which no access has been agreed, a curated per-game count field in the admin UI (a possible later fallback for whatever stays unknown), changing how flags (local, split screen, online) are derived, online or co-op player limits.

## Open questions

1. Is a curated one-number-per-game field in the admin UI wanted as a last resort for the few games no source covers, or is "unknown" acceptable for them?
2. Should counts for ROM games prefer the ROM's own region or release when a source lists several versions with different counts?
3. A game that supports more players with an accessory (for example a multitap) than with the console alone: which number counts as the maximum?
4. Steam PC games make up roughly half of the games without a count and have no new source here. Do they stay on the Steam-category flags with an unknown count (possibly filled later by a curated field), or is a PC source worth researching in a separate piece of work?
5. Who creates the source accounts and keys, and where do they live (the existing sealed secrets for the cluster plus the local environment file)? The owner has to create them, an agent cannot.

## Facts to verify in the repo

- How `MultiplayerService` orders its lookups today and where a new source plugs in without breaking the fill-only rule.
- Which fields mark a count as owner-set, and how the bulk refresh resets attempts.
- How the existing IGDB credentials are configured and tested, to copy the same pattern.
- Which table or field tells a ROM game from a Steam game, so each kind goes to the right source.
- Whether the Sources page already has a place to show per-source fill counts.
