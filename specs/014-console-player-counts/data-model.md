# Data Model: Console Player Counts from a Second Source

**Feature**: [spec.md](spec.md) · **Research**: [research.md](research.md)

## `gamecatalog.game` (existing aggregate root `Game`): two new columns

| Column | Type | Null | Meaning |
|--------|------|------|---------|
| `max_local_players` | `INTEGER` | yes | Unchanged. The maximum number of local players; entity guard 1–64. |
| `max_local_players_source` | `TEXT` | yes | **New.** Where `max_local_players` came from: `IGDB` or `THEGAMESDB`. Null exactly when `max_local_players` is null. |
| `player_count_gap` | `TEXT` | yes | **New.** Why the second source left the game without a count: `NO_MATCH`, `ENTRIES_DISAGREE`, `COUNT_BELOW_TWO`, `CONSOLE_NOT_LISTED`. Null when a count exists, when the second source has not answered for this game yet, or when the game is not eligible. |
| `multiplayer_source` | `TEXT` | no | Unchanged meaning, now explicitly the source of the **flags** (local, split screen, online): `STEAM`, `IGDB`, `UNKNOWN`. |
| `multiplayer_attempts` | `INTEGER` | no | Unchanged: failed lookups; parked at the metadata maximum attempts (3). |

### New enums (`gamecatalog.domain`)

- `PlayerCountSource { IGDB, THEGAMESDB }`, stored as its name (`@Enumerated(EnumType.STRING)`).
- `PlayerCountGap { NO_MATCH, ENTRIES_DISAGREE, COUNT_BELOW_TWO, CONSOLE_NOT_LISTED }`, stored as its name.

### Invariants

1. `max_local_players_source IS NULL` ⇔ `max_local_players IS NULL` (every count names its source; FR-006, SC-007).
2. `player_count_gap IS NOT NULL` ⇒ `max_local_players IS NULL` (a reason only for a missing count).
3. A count, once set, is never replaced by a re-derivation (fill-only, FR-007); `max_local_players_source` follows it.
4. `player_count_gap` is written only for an eligible game: `steam_app_id IS NULL AND local_multiplayer = true`.

### Entity changes (`Game`)

- Every existing method that sets `maxLocalPlayers` from a non-null value (`applyDeterministicMultiplayer`,
  `applyFillOnlyMultiplayer`) also sets `maxLocalPlayersSource = IGDB` (their only caller with a count is IGDB) and clears
  `playerCountGap`. Calls that pass `null` leave both untouched, as today.
- `applyPlayerCount(int maxLocalPlayers, PlayerCountSource source)`: fill-only. No effect when a count exists; otherwise sets
  the count and its source, clears `playerCountGap`, and resets `multiplayerAttempts` like the other fill methods.
- `recordPlayerCountGap(PlayerCountGap gap)`: sets the reason; no effect when a count exists.
- The builder's guard for `maxLocalPlayers` (1–64) stays. The second source never passes a value below 2 (it records
  `COUNT_BELOW_TWO` instead), so the guard is not hit.

### Migration `V20261102001__gamecatalog_player_count_source.sql`

```sql
CREATE SCHEMA IF NOT EXISTS gamecatalog;
SET search_path TO gamecatalog;

ALTER TABLE game ADD COLUMN max_local_players_source TEXT;
ALTER TABLE game ADD COLUMN player_count_gap TEXT;

-- Only IGDB has ever written a count (Steam passes none; AI-guessed values were removed earlier).
UPDATE game SET max_local_players_source = 'IGDB' WHERE max_local_players IS NOT NULL;
```

No constraint or index: the columns are low-cardinality filters over a few hundred rows, read by the Sources page counts
only. (Shape shown for review; the file itself is written in the implementation phase.)

## Lookup result (`TheGamesDbClient`, in memory)

`PlayerCountLookup` record, one per (title key, console):

| Field | Meaning |
|-------|---------|
| `outcome` | `FOUND`, `GAP`, `DEFERRED` |
| `maxLocalPlayers` | the agreed count (≥ 2) when `FOUND` |
| `gap` | the `PlayerCountGap` when `GAP` |

A game on several consoles combines its per-console results: all `FOUND` with one value → that value; any two `FOUND` values
that differ → `ENTRIES_DISAGREE`; any `DEFERRED` and no conflict yet → `DEFERRED`; otherwise the first `GAP` reason.

## Source state (`TheGamesDbClient`, in memory)

| Field | Meaning |
|-------|---------|
| `configured` | a key is present |
| `pausedUntil` | instant until which no call is made, or null |
| `pauseReason` | `KEY_REFUSED_OR_ALLOWANCE_USED` (HTTP 403), `ALLOWANCE_LOW`, `UNREACHABLE`, or null |
| `remainingAllowance` | the last `remaining_monthly_allowance` seen, or null before the first call |

Never persisted (one backend replica); exposed on the Sources page.

## State transitions of a console game's count

```text
                     IGDB has a count
  (no count) ───────────────────────────────▶ count, source IGDB          (final)
      │
      │ IGDB has none, flag local = true, no Steam app ID
      ▼
  second source ── agreed value ≥ 2 ────────▶ count, source THEGAMESDB    (final)
      │
      ├── values differ / no exact match / value < 2 / console not listed
      │        ▶ gap reason stored, attempt counted ── 3 attempts ──▶ parked
      │                                              (bulk refresh resets attempts → looked up again)
      │
      └── paused or unreachable ▶ nothing stored, no attempt counted (next sweep tries again)
```

Games with a Steam app ID or with `local_multiplayer` null or false never enter the second-source branch.
