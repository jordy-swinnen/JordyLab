# Data Model: Nintendo Switch Games in the Catalog

## Entities

### `Game`

Represents the catalog entry for a game, independent of any host or source.

| Field | Type | Notes |
|-------|------|-------|
| `id` | UUID | Existing |
| `platform` | String | `"Nintendo Switch"` for Switch games; plain String per existing model |
| `title` | String | Existing; protected by `titleSource` authority |
| `titleSource` | `TitleSource` | Will be `MANUAL` for all Switch-added games so scans cannot rename them |
| `steamAppId` | String | Existing; null for Switch games |
| `igdbGameId` | String | **NEW**, nullable; unique partial index when not null (like `steamAppId`) |
| `genre`, `genres`, `developer`, `publisher`, `releaseYear` | various | Existing metadata; filled from IGDB for searched games, left null for manual custom entries until linked |
| `localMultiplayer`, `splitScreen`, `onlineMultiplayer`, `singlePlayer`, `maxLocalPlayers` | nullable booleans/int | Existing; filled from IGDB `multiplayer_modes` for searched games |
| `multiplayerSource` | `MultiplayerSource` | Existing; `IGDB` when derived from IGDB, `UNKNOWN` otherwise |
| `description` | String | Existing; AI-enriched once on creation for searched games; may be absent for unlinked manual entries |
| `enrichmentStatus` | `EnrichmentStatus` | Existing |
| `coverStatus`/`coverRef`, `bannerStatus`/`bannerRef` | `ArtworkStatus`/String | Existing; IGDB image URLs for searched games, placeholder for manual custom entries |

**Identity rules**:
- When an IGDB match exists: unique by `(platform, igdbGameId)`.
- When no IGDB match exists (manual custom): unique by `(platform, normalised title)`.
- The EmuDeck ROM resolve-or-create path already adopts by `(platform, LOWER(title))`, so a manual Switch game with the
  same normalised title links to the existing ROM game.

### `TitleSource` (enum)

Existing values: `ROM(1)`, `MANIFEST(1)`, `LIBRARY(2)`.

**NEW**: `MANUAL(3)` — highest authority. `outranks(other)` returns true for any existing source, so scans never
overwrite a manual title.

### `SourceType` (enum)

Existing values: `STEAM` (platform `"Steam"`), `EMUDECK` (platform `"EmuDeck"`).

**NEW**: `SWITCH` (platform `"Nintendo Switch"`). This value is **never emitted by the scan client**, so `ScanService`
cannot resolve or adopt a Switch source from a scan payload. This makes the virtual host inert to scans, syncs and
purges by construction.

### `ScanSource`

Represents a host. A single well-known row represents the virtual Nintendo Switch host:

| Field | Value |
|-------|-------|
| `sourceKey` | `"Nintendo Switch"` (or hostname + `:` + `SWITCH`) |
| `hostname` | `"Nintendo Switch"` |
| `sourceType` | `SWITCH` |
| `platform` | `"Nintendo Switch"` |
| `enabled` | `true` |
| `machineId` | sentinel value indicating a manual host (e.g. `"manual"`) |

Created by the migration or lazily on first add.

### `GameInstallation`

Per-host presence of a game. Switch games use a `GameInstallation` row on the virtual Switch `ScanSource`.

**NEW columns**:

| Field | Type | Notes |
|-------|------|-------|
| `manual` | boolean, default `false` | `true` for Switch manual entries; excludes the row from scan reconciliation and purge |
| `format` | enum `PHYSICAL`/`DIGITAL`, nullable | Physical or digital format for Switch entries; null for Steam/EmuDeck installations |

For Switch entries:
- `externalRef` = the game's UUID string (guarantees `(source_id, external_ref)` uniqueness).
- `presence` = `INSTALLED` (the game is present/owned on the host).
- `firstSeenAt`/`lastSeenAt` = add/update time.
- `source` = the virtual Switch `ScanSource`.

**Rationale for storing format here**: `GameInstallation` is the existing per-host unit; using it avoids changing
`GameLibraryEntry`'s unique key or backfilling host references for existing Steam-owned rows.

### `GameLibraryEntry`

Global library membership. No schema change.

- For a game that is only owned on Switch, create an `OWNED` entry.
- For a game already owned on Steam (existing `OWNED` entry) and also owned on Switch, reuse the existing `OWNED` entry.
  The Switch-specific format lives on the `GameInstallation` row.

This preserves the existing `UNIQUE (game_id, library_source)`.

## State transitions

### Add via IGDB search (US1)

1. Admin submits title search.
2. Backend calls `IgdbClient` with platform filter `130`.
3. Admin picks a result and chooses `PHYSICAL`/`DIGITAL`.
4. Backend resolves or creates `Game` by `(platform, igdbGameId)`.
   - If new: `Game.titleSource = MANUAL`, fill metadata/artwork/multiplayer from IGDB, enqueue AI enrichment once.
   - If existing: attach the Switch host via a new `GameInstallation` (manual=true, format chosen); do not duplicate.
5. Create `GameInstallation` on virtual Switch source (manual=true, format chosen, presence=INSTALLED).
6. Create `GameLibraryEntry.OWNED` if the game does not already have one.

### Add manually (US2)

1. Admin chooses "Add manually", enters title and format.
2. Backend normalises the title.
3. Resolve or create `Game` by `(platform, normalised title)` with no `igdbGameId`.
   - If new: `Game.titleSource = MANUAL`, placeholder artwork, no AI enrichment until linked.
   - If existing: attach the Switch host via a new `GameInstallation`.
4. Create `GameInstallation` on virtual Switch source (manual=true, format chosen, presence=INSTALLED).
5. Create `GameLibraryEntry.OWNED` if needed.

### Link later (US2)

1. Admin searches and selects an IGDB match for an existing manual custom `Game`.
2. Backend sets `Game.igdbGameId`, fills metadata/artwork/multiplayer from IGDB, and enriches the description once.
3. `Game.titleSource` stays `MANUAL`.

### Bulk paste (US3)

Transient `BulkReviewLine` per pasted line:

| Field | Type | Notes |
|-------|------|-------|
| `line` | String | Original pasted title |
| `status` | enum | `MATCH` / `NO_MATCH` / `ALREADY_PRESENT` / `NEEDS_REVIEW` |
| `proposedGame` | IGDB result or existing Game | Best match |
| `format` | `PHYSICAL`/`DIGITAL` | Admin can override per line or globally |
| `include` | boolean | Whether to add on confirm |

No persistence until confirm. On confirm, each included line follows the US1/US2 add flow.

### Edit (US5)

- Change `format`: update the `GameInstallation.format` value only; do not touch metadata, artwork or AI description.
- Relink to a different IGDB match: update `Game.igdbGameId` and refresh metadata/artwork/multiplayer from IGDB; AI
  description is **not** regenerated.

### Remove (US5)

1. Admin confirms removal.
2. Delete the `GameInstallation` row on the Switch source.
3. Delete the `GameLibraryEntry.OWNED` row **only if** the game has no other source of ownership (no Steam/Family
  library entry, no other host installation).
4. Do **not** cascade-delete the `Game` if it still has other installations or library entries.

### Scan / sync / purge interaction (FR-007, SC-005)

- `ReconciliationService.hideMissingInstallations` iterates per source from a scan. The Switch source is never present
  in scan payloads, so its installations are never marked `UNINSTALLED`.
- `ReconciliationService.purgeUninstalledGames` loads installations by `presence = UNINSTALLED`. Switch installations
  are `INSTALLED` and `manual = true`, so they are never loaded.
- Additional explicit guard: both methods skip rows where `manual = true` (defence in depth).

## Validation rules

- `igdbGameId`, when present, must be numeric and unique per platform.
- `format` is required when `manual = true`.
- `manual = true` installations must be on a `SourceType.SWITCH` source.
- A game can have at most one Switch `GameInstallation`.
- Only `admin` role may create, edit or delete Switch installations.

## Migration summary

`V20260929__gamecatalog_switch_games.sql`:

1. Add `igdb_game_id` column to `game` (nullable String) with a unique partial index for non-null values.
2. Add new `MANUAL` value to `title_source` enum (highest authority).
3. Add new `SWITCH` value to `source_type` enum.
4. Add `manual` (boolean, default false) and `format` (enum `PHYSICAL`/`DIGITAL`, nullable) to `game_installation`.
5. Insert the well-known virtual `ScanSource` row for the Nintendo Switch host.
