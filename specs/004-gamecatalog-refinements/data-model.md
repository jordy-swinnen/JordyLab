# Phase 1 Data Model: Game Catalog Refinements

**Date**: 2026-09-27
**Spec**: [spec.md](./spec.md)
**Plan**: [plan.md](./plan.md)

Additive + reshaping changes to the `gamecatalog` schema owned by `jordylab-be` Flyway. Entities keep the canonical structure from `jordylab-be/AGENTS.md`. One new aggregate (`GameInstallation`), one reshaped aggregate (`Game`), no changes to `ScanSource`/`SyncReport`.

---

## Schema changes (single migration `V20260927__gamecatalog_multihost_refinements.sql`)

### `game_installation` — new table

| Field (Java) | Column | Type | Constraints | Notes |
|---|---|---|---|---|
| `id` | `id` | UUID | PK | `UUID.randomUUID()` in `build()` |
| `game` | `game_id` | UUID | NOT NULL, FK → `game(id)` | shared catalog entry |
| `source` | `source_id` | UUID | NOT NULL, FK → `scan_source(id)` | the host link (source = machine × library type) |
| `externalRef` | `external_ref` | TEXT | NOT NULL, ≤ 500 | Steam appid or ROM primary path — per-host identity |
| `presence` | `presence` | TEXT | NOT NULL, enum `gamecatalog.presence` | `INSTALLED` / `UNINSTALLED` |
| `firstSeenAt` | `first_seen_at` | TIMESTAMPTZ | NOT NULL | moved from `game` |
| `lastSeenAt` | `last_seen_at` | TIMESTAMPTZ | NOT NULL | moved from `game` |
| `uninstalledAt` | `uninstalled_at` | TIMESTAMPTZ | nullable | grace clock — moved from `game` |

- `UNIQUE (source_id, external_ref)` — the reconciliation key within a source (was implicitly unique via `game`; now explicit).
- `INDEX (game_id)` — visibility lookups; `INDEX (source_id)` — snapshot reconciliation + shrink guard.
- `presence` constraint values and all timestamps backfilled from the existing `game` rows (see migration sketch below).

### `game` — reshaped

| Field (Java) | Column | Type | Change | Notes |
|---|---|---|---|---|
| `id` | `id` | UUID | kept | |
| `platform` | `platform` | TEXT | kept | cross-host adoption key part |
| `title` | `title` | TEXT | kept | adoption key part (ROM: `LOWER(title)` match) |
| `steamAppId` | `steam_app_id` | TEXT | **new**, nullable, ≤ 32 | set for `platform = 'Steam'`; deterministic cross-host identity + artwork/metadata key |
| `genres` | `genres` | TEXT | **new**, nullable, ≤ 200 | comma-separated, sanitized (R4) |
| `developer` | `developer` | TEXT | **new**, nullable, ≤ 100 | sanitized |
| `publisher` | `publisher` | TEXT | **new**, nullable, ≤ 100 | sanitized |
| `releaseYear` | `release_year` | INTEGER | **new**, nullable, 1950..2028 | bounded |
| `metadataStatus` | `metadata_status` | TEXT | **new**, NOT NULL, default `PENDING` | `PENDING` / `OK` / `FAILED` (Steam appdetails; ROM games reach `OK` via enrichment) |
| `metadataAttempts` | `metadata_attempts` | INTEGER | **new**, NOT NULL, default 0 | mirror of `enrichment_attempts` |
| `coverStatus` | `cover_status` | TEXT | renamed from `artwork_status` + data migration | Steam `EXTERNAL_URL` rows reset to `PENDING` (R3); ROM rows untouched |
| `coverRef` | `cover_ref` | TEXT | renamed from `artwork_ref` | |
| `bannerStatus` | `banner_status` | TEXT | **new**, NOT NULL, default `PENDING` | same `ArtworkStatus` enum |
| `bannerRef` | `banner_ref` | TEXT | **new**, nullable | |
| `genre`, `maxLocalPlayers`, `onlineMultiplayer`, `singlePlayer`, `description`, `enrichmentStatus`, `enrichmentAttempts`, `artworkFallbackRequests` | — | — | kept | unchanged semantics |
| `source`, `externalRef`, `presence`, `firstSeenAt`, `lastSeenAt`, `uninstalledAt` | — | — | **dropped** (moved to `game_installation`) | after the backfill |

### Migration sketch

```sql
CREATE SCHEMA IF NOT EXISTS gamecatalog;
SET search_path TO gamecatalog;

CREATE TABLE game_installation ( … as above … );
ALTER TABLE game_installation
    ADD CONSTRAINT uq_game_installation_source_ref UNIQUE (source_id, external_ref);

-- Backfill: one installation per existing game row, carrying its per-source state.
-- Guard first: the move must not collide (it cannot by construction — game.source_id +
-- game.external_ref were unique — but the guard documents the invariant).
INSERT INTO game_installation (id, game_id, source_id, external_ref, presence,
    first_seen_at, last_seen_at, uninstalled_at)
SELECT gen_random_uuid(), id, source_id, external_ref, presence::text,
    first_seen_at, last_seen_at, uninstalled_at
FROM game;

ALTER TABLE game ADD COLUMN steam_app_id TEXT;
ALTER TABLE game ADD COLUMN genres TEXT; … (metadata columns as above) …
ALTER TABLE game ADD COLUMN cover_status TEXT; ALTER TABLE game ADD COLUMN cover_ref TEXT;
ALTER TABLE game ADD COLUMN banner_status TEXT NOT NULL DEFAULT 'PENDING';
ALTER TABLE game ADD COLUMN banner_ref TEXT;
UPDATE game SET cover_status = artwork_status, cover_ref = artwork_ref;
-- R3: stale Steam header.jpg covers re-resolve to portrait library art.
UPDATE game SET cover_status = 'PENDING', cover_ref = NULL
WHERE cover_status = 'EXTERNAL_URL' AND platform = 'Steam';
-- Steam appid derivation from the moved external_ref, before the column drops.
UPDATE game SET steam_app_id = external_ref FROM game_installation gi
WHERE gi.game_id = game.id AND game.platform = 'Steam';
ALTER TABLE game DROP COLUMN source_id, DROP COLUMN external_ref, DROP COLUMN presence,
    DROP COLUMN first_seen_at, DROP COLUMN last_seen_at, DROP COLUMN uninstalled_at,
    DROP COLUMN artwork_status, DROP COLUMN artwork_ref;
```

Postgres 16 provides `gen_random_uuid()` natively. `ArtworkStatus`/`Presence`/`EnrichmentStatus`-style enums remain TEXT with the same names — no type changes.

## Entities

### `GameInstallation` — new aggregate

Canonical entity structure: `@Entity @Table(schema="gamecatalog", name="game_installation") @Getter @Builder @AllArgsConstructor(access=PRIVATE) @NoArgsConstructor(access=PROTECTED) extends BaseEntity<GameInstallation>`; `equals`/`hashCode` inherited (EqualsVerifier test with the three standard suppressions).

Builder guards (in `build()` only): `game` non-null, `source` non-null, `externalRef` text ≤ 500, `presence` non-null (default `INSTALLED`), `firstSeenAt`/`lastSeenAt` non-null, `id` random-default.

Named mutation methods (domain events registered, no raw setters on mutable state):

- `seenAgain(Instant seenAt)` — `presence = INSTALLED`, `uninstalledAt = null`, `lastSeenAt = seenAt` (moved from `Game`).
- `markUninstalled(Instant uninstalledAt)` — `presence = UNINSTALLED`, `uninstalledAt = set`.

### `Game` — reshaped aggregate

Keeps its canonical structure; builder guards updated: `steamAppId` ≤ 32 when present, `genres` ≤ 200, `developer`/`publisher` ≤ 100, `releaseYear` null or 1950..2028, `metadataStatus` default `PENDING`, `coverStatus`/`bannerStatus` default `PENDING`, presence defaults removed (moved out). New/changed named mutations:

- `applyDeterministicMetadata(String genres, String developer, String publisher, Integer releaseYear)` — sets the four fields + `metadataStatus = OK`; null arguments persist as null (field unknown), never fabricated.
- `recordMetadataFailure(int maxAttempts)` / `resetMetadataForRetry()` — mirror the enrichment attempt pattern.
- `applyCoverArtwork(ArtworkStatus status, String coverRef)` / `applyBannerArtwork(ArtworkStatus status, String bannerRef)` / `requestLocalCoverFallback()` — the artwork slot mutations, moved from the single-slot `applyArtwork`/`requestLocalArtworkFallback`.
- `applyEnrichment(...)` gains the four deterministic fields for ROM enrichment (same one method — enrichment produces the whole fact set).
- **No adoption mutation exists** — adoption is the *absence* of mutation: reconciliation upserts a `GameInstallation` and never touches the matched `Game` (FR-005 is structural).

### Repositories

- `GameInstallationRepository` (new): `findBySourceIdAndExternalRef`, `findAllBySourceId`, `findByPresenceAndUninstalledAtBefore`, `countInstalledBySourceId`, `deleteByGameId` (purge orphan cleanup).
- `GameRepository` (reshaped):
  - `findVisibleGames(search, platform, host, pageable)` — host filter joins installations: `EXISTS (SELECT 1 FROM GameInstallation gi JOIN ScanSource s … WHERE gi.game.id = g.id AND gi.presence='INSTALLED' AND s.enabled = true AND s.hostname = :host)`.
  - `findVisibleHosts()` — distinct `s.hostname` over installed installations on enabled sources, sorted.
  - `findForChatFilter(...)` — gains `genresSearch` (substring), `developerSearch` (substring), `releaseYearMin`/`releaseYearMax`, `hosts` list (same EXISTS join), same `ENRICHED`/`OK`-field visibility posture as today.
  - `findByPlatformAndSteamAppId(String platform, String steamAppId)` — Steam adoption lookup.
  - `findByPlatformAndLowercaseTitle(String platform, String title)` — ROM adoption lookup (function-based lowercase comparison; bounded by `platform` first).
  - `findVisibleById`, `findVisiblePlatforms`, metadata/enrichment pending/failed queries — same shapes, installation-aware visibility (`EXISTS` installed-on-enabled-source).

## State transitions

```
GameInstallation.presence:
  INSTALLED  ──(absent from successful snapshot)──▶ UNINSTALLED ──(30d purge)──▶ deleted
  UNINSTALLED ──(rediscovered within grace)──▶ INSTALLED (retained game data reused)

Game.coverStatus / bannerStatus (per slot, same ArtworkStatus enum):
  PENDING ──external probe hit──▶ EXTERNAL_URL
  PENDING ──probe miss + no local art──▶ PLACEHOLDER
  PENDING/LOCAL_FALLBACK_REQUESTED ──local upload flow (cover slot only)──▶ LOCAL_UPLOAD
  EXTERNAL_URL(Steam header-era covers) ──migration──▶ PENDING ──▶ portrait EXTERNAL_URL

Game.metadataStatus (Steam appdetails path; ROM path completes via enrichment):
  PENDING ──appdetails ok + validated──▶ OK
  PENDING ──failure──▶ (attempts++) ──max──▶ FAILED ──daily reset──▶ PENDING
```

## Wire model changes (see contracts/catalog-api.md)

- `GameSummaryResponse` + `GameDetailResponse`: `coverUrl`/`coverEndpoint` (renamed from `artwork*`), `bannerUrl`/`bannerEndpoint` (detail only), detail + `genres`/`developer`/`publisher`/`releaseYear` + `hosts: [{hostname, sourceType}]`.
- `ChatRequest` + `gameIds: UUID[]` (≤ 5); `ChatFilter` + `genresSearch`/`developerSearch`/`releaseYearMin`/`releaseYearMax`/`hosts`; translation system prompt fields extended accordingly.
- `GET /games?host=`, `GET /hosts` (new `HostsResponse`).
- Scan-client wire payloads (`ScanRequest`/`ClientGame`/check) — **unchanged** (R5/R6).
