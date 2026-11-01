# Feature Specification: Console Player Counts from a Second Source

**Feature Branch**: `014-console-player-counts`

**Created**: 2026-11-01

**Status**: Draft

**Module**: `gamecatalog` (existing)

**Builds on**: 005 (local-multiplayer metadata, Steam library), 013 (game catalog refinement and LibBot)

**Draft**: `specs/_drafts/multiplayer-player-counts/spec-draft.md` (the measured source table and the decisions below come from it and are not re-opened here). The hand-set count for games no source covers is a separate follow-up (`specs/_drafts/curated-player-counts/`).

**Input**: User description: "Specify the feature drafted in specs/_drafts/multiplayer-player-counts/spec-draft.md (player counts for console games from a second source, TheGamesDB). Read that draft first and treat it as the source of the requirements, success criteria, out-of-scope list and measured source table; do not re-open its decisions."

---

## Context

LibBot answers "what can we play with N people?" from each game's maximum number of local players. On production, 44 of the 105 games flagged local multiplayer have no such number, so the answer ends with "for 85 more games the player count isn't known yet". The first source (IGDB) has no player-count records for many older console titles, and Steam's categories never give a count.

Of the 44 games, **15 are console games** (GameCube, Nintendo 64, PlayStation, PlayStation 2, PlayStation 3 — Mario Party 6 and 7, WWF No Mercy, Wave Race: Blue Storm, Gauntlet: Dark Legacy and others) and **29 are Steam PC games**. This feature fills the console games from a second, open source. The Steam games are deliberately out of scope: no source tried gives a reliable local count for PC games.

### What was measured (2026-11-01, real titles)

| Source | Outcome |
|--------|---------|
| TheGamesDB | **Chosen for console games.** 15 of 15 console games matched with a count; 13 where the source's entries for a title agree, 2 where they disagree (Mario Party 7: 7, 7 and 4; PlayStation All-Stars Battle Royale: 4 and 2). Free key, 1,000 lookups a month. |
| ScreenScraper | Dropped: its published terms do not cover a personal, non-distributed app. |
| LaunchBox games database | Dropped: no official download, terms or programmatic access. |
| Libretro "max users" lists | Dropped: covers one of the five consoles and is keyed by file checksum. |
| Wikidata | Dropped: a maximum for none of the 15 console games. |
| Co-Optimus, MobyGames | Dropped: refuse automated reads; MobyGames also costs money. |
| TheGamesDB for Steam games | Rejected: its single number does not say local or online (nine matches say 1 player for a game Steam calls local multiplayer; others read 12 and 20). |

---

## Clarifications

### Session 2026-11-01

- Q: Should the new source also be asked for console games whose local-multiplayer flag is not yet known, or only for games already flagged local multiplayer? → A: Only games already flagged local multiplayer. The source's number does not say local or online, so a count is never stored for a game that is not known to be local multiplayer.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - LibBot judges more console games for a party (Priority: P1)

The owner (or a guest) asks LibBot what to play with a given number of people. Console games whose player count is now known are judged against the party size, so the answer names fewer games as "player count unknown".

**Why this priority**: This is the reason for the feature. The unknown note on a party-size answer was called unacceptable by the owner.

**Independent Test**: Run the bulk "Refresh game data" run on a library with console games that have no count, then ask "what can we play with 4 people?". The fixed games appear as fits or non-fits, and the "player count isn't known yet" number is lower than before.

**Acceptance Scenarios**:

1. **Given** a local-multiplayer console game with no count and a source that lists one agreed count for it, **When** the bulk refresh runs, **Then** the game gets that count and LibBot judges it against the party size.
2. **Given** the same library before and after the run, **When** the owner asks the same party-size question, **Then** the answer names fewer games as unknown.
3. **Given** a console game still without a count, **When** LibBot answers a party-size question, **Then** the note about unknown counts still appears for it.

---

### User Story 2 - Counts are filled automatically, correctly and only when sure (Priority: P1)

Console games flagged local multiplayer that have no count are looked up in the background and on the bulk refresh, after the existing source has had its turn. A count is stored only when the source gives the same number for every entry that matches the game's title on its console. Anything doubtful is left unknown rather than guessed.

**Why this priority**: A wrong number is worse than "unknown": it makes LibBot recommend or exclude games wrongly. The rule keeps the data trustworthy.

**Independent Test**: On the 15 console games from the measurement, the run fills the games whose entries agree and leaves Mario Party 7 and PlayStation All-Stars Battle Royale unknown.

**Acceptance Scenarios**:

1. **Given** every matching entry for a game says 4, **When** the lookup runs, **Then** the count 4 is stored with the new source named.
2. **Given** entries for a game say 7, 7 and 4, **When** the lookup runs, **Then** nothing is stored and the game is listed for review.
3. **Given** a source entry that says 1 player for a game flagged local multiplayer, **When** the lookup runs, **Then** it is treated as no count.
4. **Given** the title only matches loosely (not the same normalised title on the same console), **When** the lookup runs, **Then** it is treated as no match and nothing is stored.
5. **Given** a game that already has a count, **When** any run happens, **Then** its count is left unchanged.
6. **Given** a game with no count that no source can fill, **When** several sweeps run, **Then** it is parked after the existing bounded number of attempts and is retried only by the bulk refresh.
7. **Given** a console game whose local-multiplayer flag is unknown, **When** a sweep or the bulk refresh runs, **Then** the new source is not asked about it and no count is stored; once the existing source sets the flag to local multiplayer, the game becomes eligible on the next run.

---

### User Story 3 - The owner can see where each count came from (Priority: P2)

On a game's detail page and in LibBot's citations, a count names the source it came from, separately from where the game's other multiplayer facts came from.

**Why this priority**: The owner wants every number to be traceable, and a spot check against the source's own site depends on it.

**Independent Test**: Open a console game that was filled by the new source and a game filled by the existing source; each shows its own source for the count.

**Acceptance Scenarios**:

1. **Given** a game whose multiplayer facts came from the existing source and whose count came from the new one, **When** the detail page is opened, **Then** the count names the new source and the facts keep naming the old one.
2. **Given** LibBot cites a game's player count, **When** the answer is shown, **Then** the citation can name the source of that count.

---

### User Story 4 - The Sources page shows how well each source does (Priority: P2)

On the Sources page the owner sees, per source, how many games it filled; for the new source, how many it was asked about and had no count for, by reason; and how many games remain unknown overall. The owner can open the list of games behind each number, including the games where entries disagreed.

**Why this priority**: It makes gaps visible instead of silent and shows whether a further source is ever needed. It also feeds the follow-up for hand-set counts.

**Independent Test**: After a bulk refresh, the page shows the three numbers per source and the lists match the games in the library.

**Acceptance Scenarios**:

1. **Given** a finished bulk refresh, **When** the owner opens the Sources page, **Then** it shows, per source, filled, no-count and still-unknown numbers.
2. **Given** a game whose entries disagreed, **When** the owner opens the list behind "no count", **Then** the game is listed with the reason.
3. **Given** a console the source does not list, **When** the owner reads the page, **Then** games on it are reported as "console not listed" and are never sent to the source.

---

### User Story 5 - The new source is optional and safe to run (Priority: P3)

The source needs an access key. Without it, nothing changes compared with today and the backend starts normally. With it, lookups stay within the source's monthly allowance and never expose the key.

**Why this priority**: The feature must not break local development or a fresh install, and the key must stay secret.

**Independent Test**: Start the backend without the key and run a refresh: no error, no lookups, same results as before. Start with the key: lookups happen and no log line, error message or response contains the key.

**Acceptance Scenarios**:

1. **Given** no key is configured, **When** the backend starts and a refresh runs, **Then** it starts, the new source is skipped, and one message says so.
2. **Given** the monthly allowance is used up (or the source answers that it is), **When** a refresh runs, **Then** the source pauses until the allowance returns and the games are not charged a failed attempt.
3. **Given** a lookup fails with an error that contains the request address, **When** it is logged or shown, **Then** the key does not appear.
4. **Given** the setup documentation, **When** the owner follows it, **Then** they can add, rotate and check the key by behaviour without ever printing it.

---

### Edge Cases

- A title has several entries on one console, with the same count: stored.
- A title has several entries, some with a different count: not stored, listed for review.
- A title has entries whose names differ only by edition or region tags: they count as the same title for the agreement rule; a bundle or variant with a different name does not.
- A game is installed on more than one console: it is looked up for each, and stored only if all agree.
- A game with no console (a Steam game): never looked up in this feature.
- A console game whose local-multiplayer flag is unknown (30 of the library's console games on 2026-11-01): never looked up in this feature, so it can never receive a count from a number that might be an online limit.
- The source is unreachable or slow: the game is not charged a failed attempt for a transient outage, and the run continues with the next source.
- A count outside the sensible range for a local-multiplayer game (under 2): treated as no count.
- A bulk refresh run again in the same month: every game still without a count is looked up again, including those whose entries disagreed or did not match (the source's data may have been corrected since). Games that already have a count are not looked up, so a repeat run costs only as many lookups as there are unfilled games.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST ask its sources for a game's player count in a fixed order and stop at the first one that has a count: the existing source first, then the new source for console games (games with no Steam identity) that are already flagged local multiplayer. A game whose local-multiplayer flag is unknown MUST NOT be looked up in the new source. A source that has flags but no count MUST NOT end the search. Steam games MUST keep using the existing source only.
- **FR-002**: A count MUST only ever come from a source's own data field. It MUST NOT be inferred by an AI, taken from free text, or guessed. The earlier rule that removed AI-guessed values stays in force.
- **FR-003**: The system MUST store a count from the new source only when every entry for the same title on the game's console gives the same number. When entries disagree, nothing is stored and the game is listed for review.
- **FR-004**: A count under 2 for a game flagged local multiplayer MUST be treated as no count.
- **FR-005**: A game MUST be matched to a source entry by the catalog's normalised title and the game's console, with exact equality only. A weaker match MUST be treated as no match.
- **FR-006**: Each stored count MUST remember which source it came from, separately from the source of the game's other multiplayer facts. The game detail page and LibBot's citations MUST be able to name it.
- **FR-007**: A count already in the catalog MUST NOT be overwritten by any run.
- **FR-008**: A game that no source has a count for MUST be parked after the existing bounded number of attempts and retried only by the bulk "Refresh game data" run. It MUST NOT loop on every sweep.
- **FR-009**: The new source MUST be optional. With no key configured the derivation MUST skip it, log that once, and behave exactly as before; the backend MUST start normally.
- **FR-010**: The key MUST be stored and handled like the existing source credentials: named in the example environment file, kept as a sealed secret for production and in the untracked environment file locally. The key, and any request address or error text that contains it, MUST NOT be logged, returned or shown.
- **FR-011**: Lookups MUST stay within the source's monthly allowance: only games without a count are looked up, once per game and console, paced between calls. When the source reports the allowance used up (or refuses the key), the system MUST pause that source and MUST NOT count each game as a failed attempt. The same holds when the source is unreachable: a transient outage MUST NOT use up a game's bounded attempts. The application MUST identify itself honestly in its requests.
- **FR-012**: A console the source does not list MUST never be sent to the source (no request, no allowance spent) and MUST be reported with the reason "console not listed". Like any other unresolved game, it counts the normal bounded attempts and is then parked.
- **FR-013**: The Sources page MUST show, per source, how many games it filled; for the new source, how many games it was asked about and had no count for, split by reason; and how many games remain unknown overall (the existing source's misses are not recorded per source, so only this overall figure applies to it). It MUST list the games behind each number, including the games whose entries disagreed.
- **FR-014**: The setup MUST be documented in the runbook and the example environment file: which secret exists, where it is kept, how to rotate it, and how to check by behaviour (a successful request, not by reading the key) that the source works.
- **FR-015**: The product docs, the data-source notes and the backlog explanation in the admin UI MUST be updated in the same change.

### Key Entities

- **Player count**: the maximum number of local players of a game; a whole number of 2 or more; absent when unknown. Carries the source it came from.
- **Count source**: the origin of a count (the existing source, the new source, or none). Kept apart from the origin of the game's flags.
- **Source entry**: one record in the new source for a title on a console, with the source's own player-count field.
- **Review entry**: a game left without a count together with the reason the new source gave (entries disagree, no exact match, console not listed, count under 2). While the source is unconfigured or paused (allowance used up, key refused, unreachable) games get no reason; the Sources page shows the source's state instead and counts them as not looked up yet.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: After a bulk "Refresh game data" run, fewer than 10 % of all local-multiplayer console games in the production library have no player count, and every remaining one is listed by title with its reason. The percentage is over the whole set (56 such games on 2026-11-01, of which 15 had no count), not over the 15-game sample: in the measurement, 13 of those 15 would be filled, leaving 2 of 56 (about 4 %).
- **SC-002**: Every stored count shows its source, and a spot check of at least ten console games against the source's own site agrees with the stored number.
- **SC-003**: A count the measurement judged wrong is never stored: Mario Party 7 stays unknown unless the source's entries agree.
- **SC-004**: A party-size LibBot question names fewer unknown games than before the run, and the note still appears for the games that remain.
- **SC-005**: With no key configured, behaviour and results match today's exactly and the backend starts without error.
- **SC-006**: Each full bulk refresh uses at most 100 lookups for the current library (about 15 to 30 in the measurement, because only unfilled games are looked up), so at least ten refreshes in a month stay within the 1,000-lookup allowance.
- **SC-007**: No player count in the database exists that did not come from a source's own field, and the key appears in no log, response or page.
- **SC-008**: Steam PC games are unchanged by this feature (measured separately; not expected to improve).

## Assumptions

- The key is already issued (obtained through the source's own access request) and is held in the owner's local environment file; the production value is added to the sealed secrets by the owner.
- The free allowance of 1,000 lookups a month is enough; paid tiers are not needed and not pursued.
- The library is played in emulators, so the game's own maximum applies and accessories such as multitaps need no handling.
- The source's published player-count field is the best open number available for these consoles; where it is wrong for a single title, correcting it by hand is the separate follow-up, not part of this feature.
- The five consoles in the library (GameCube, Nintendo 64, PlayStation, PlayStation 2, PlayStation 3) are the ones the source lists; any other console the library gains falls under FR-012 until mapped.
- The source's public pages show no terms of use; the key page shows only the allowance. If terms that bar this use appear, the source is dropped.
- The per-game "refresh multiplayer" action in the admin UI runs the same derivation as the bulk run, so it also uses the new source for an eligible game.
- Out of scope: Steam PC games, console games whose local-multiplayer flag is unknown, AI-estimated counts, scraping pages without agreed access, changing how local, split-screen and online flags are derived, online or co-op player limits, and the hand-set count (separate draft).
