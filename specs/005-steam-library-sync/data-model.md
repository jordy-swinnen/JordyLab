# Data Model: Steam Library & Family Library Sync

**Feature**: `005-steam-library-sync` | **Migration**: `V20260928001__gamecatalog_library.sql`

All changes are additive to the `gamecatalog` schema (owned by `jordylab-be` Flyway).

---

## `game` (changed)

| Column | Type | Notes |
|--------|------|-------|
| `title_source` | `TEXT` (nullable) | `LIBRARY` \| `MANIFEST` \| `ROM`. Records the authority that set the current `title`. Null for ROMs created before this feature (treated as `ROM`). |

**Constraint added** — replaces the plain partial index `idx_game_steam_app_id`:

```sql
CREATE UNIQUE INDEX uq_game_steam_app_id
    ON game (steam_app_id) WHERE steam_app_id IS NOT NULL;
```

The migration must first make the existing data satisfiable: detect duplicate non-null `steam_app_id`
rows, and if any exist, log/abort with a clear message (they should not, since adoption already keys on
`platform + steam_app_id`). A guarded `DO $$` block raises an exception listing duplicates so the
operator resolves them deliberately rather than the migration silently deleting catalog rows.

`Game` domain changes:
- New `titleSource` field (`TitleSource` enum).
- `applyDeterministicMetadata` becomes **fill-only**: each of `genres`, `developer`, `publisher`,
  `releaseYear` is assigned only when currently null; `metadataStatus` is set to `OK` only when the
  call supplied at least one value. This stops `EnrichmentService` from overwriting Steam facts (FR-005).
- New `updateTitle(title, TitleSource)` used by both scan and library sync with authority ranking; the
  old `updateCatalogInfo(title, platform)` keeps platform updates but calls `updateTitle(..., MANIFEST)`.

**Title authority** (FR-012): rank `LIBRARY (2) > MANIFEST (1) = ROM (1)`. A source may only set the
title if its rank is `>=` the current `title_source` rank; a lower-ranked source never overwrites.

---

## `game_installation` (unchanged)

No schema change. Presence (`INSTALLED`/`UNINSTALLED`), `installed_at`, `uninstalled_at`, `external_ref`,
FK to `game` and `scan_source`. Existing `findByPresenceAndUninstalledAtBefore`, `countByGameId`,
`countInstalledBySourceId` are reused.

---

## `game_library_entry` (new)

| Column | Type | Constraints |
|--------|------|-------------|
| `id` | `UUID` | PK |
| `game_id` | `UUID` | NOT NULL, FK `game(id)` |
| `library_source` | `TEXT` | NOT NULL, `OWNED` \| `FAMILY` |
| `first_seen_at` | `TIMESTAMPTZ` | NOT NULL |
| `last_seen_at` | `TIMESTAMPTZ` | NOT NULL |
| `removed_at` | `TIMESTAMPTZ` | nullable — set when a successful sync no longer reports the game |
| `family_owner_names` | `TEXT` | nullable — comma-joined display names (FAMILY only) |
| audit columns | | created/updated/version per `BaseEntity` |

```sql
CREATE TABLE game_library_entry (
    id                 UUID PRIMARY KEY,
    game_id            UUID        NOT NULL REFERENCES game (id),
    library_source     TEXT        NOT NULL,
    first_seen_at      TIMESTAMPTZ NOT NULL,
    last_seen_at       TIMESTAMPTZ NOT NULL,
    removed_at         TIMESTAMPTZ,
    family_owner_names TEXT,
    created_date       TIMESTAMPTZ NOT NULL,
    updated_date       TIMESTAMPTZ NOT NULL,
    version            BIGINT      NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uq_game_library_entry_game_source ON game_library_entry (game_id, library_source);
CREATE INDEX idx_game_library_entry_source_removed ON game_library_entry (library_source, removed_at);
```

**Not stored**: `share_excluded` / `exclude_reason` — excluded family titles are omitted entirely (Q2).

**Validity rules**:
- `library_source` must be `OWNED` or `FAMILY`.
- A row is **active** when `removed_at IS NULL`; a row is in the **grace window** when
  `removed_at >= now() - gracePeriodDays`.
- At most one row per `(game_id, library_source)` (unique index).

**State transitions**:

```
                 upsert (absent from response)
        ┌──────────────────────────────────────────┐
        │                                          ▼
   [absent] ──first report──▶ [active] ──missing from successful sync──▶ [removed]
                                 ▲                                        │
                                 └────────reported again (same sync or next)──┘
                                          (removed_at cleared)
   [removed] ──grace expires──▶ eligible for purge (only if no installations)
```

---

## `library_sync_run` (new)

| Column | Type | Notes |
|--------|------|-------|
| `id` | `UUID` | PK |
| `library_source` | `TEXT` | `OWNED` \| `FAMILY` |
| `started_at` / `finished_at` | `TIMESTAMPTZ` | NOT NULL |
| `outcome` | `TEXT` | `APPLIED` \| `NO_CHANGE` \| `FAILED` \| `SUSPICIOUS` |
| `content_hash` | `TEXT` | nullable — SHA-256 of the normalised `(appid, name)` list |
| `entries_submitted` | `INTEGER` | NOT NULL DEFAULT 0 |
| `entries_added` | `INTEGER` | NOT NULL DEFAULT 0 |
| `entries_removed` | `INTEGER` | NOT NULL DEFAULT 0 |
| `metadata_calls` | `INTEGER` | NOT NULL DEFAULT 0 |
| `ai_calls` | `INTEGER` | NOT NULL DEFAULT 0 |
| `error_code` | `TEXT` | nullable — e.g. `EMPTY_RESPONSE`, `TOKEN_EXPIRED`, `UNKNOWN_RESPONSE` |
| audit columns | | per `BaseEntity` |

```sql
CREATE TABLE library_sync_run (
    id                UUID PRIMARY KEY,
    library_source    TEXT        NOT NULL,
    started_at        TIMESTAMPTZ NOT NULL,
    finished_at       TIMESTAMPTZ NOT NULL,
    outcome           TEXT        NOT NULL,
    content_hash      TEXT,
    entries_submitted INTEGER     NOT NULL DEFAULT 0,
    entries_added     INTEGER     NOT NULL DEFAULT 0,
    entries_removed   INTEGER     NOT NULL DEFAULT 0,
    metadata_calls    INTEGER     NOT NULL DEFAULT 0,
    ai_calls          INTEGER     NOT NULL DEFAULT 0,
    error_code        TEXT,
    created_date      TIMESTAMPTZ NOT NULL,
    updated_date      TIMESTAMPTZ NOT NULL,
    version           BIGINT      NOT NULL DEFAULT 0
);
CREATE INDEX idx_library_sync_run_source_finished ON library_sync_run (library_source, finished_at DESC);
```

`library_sync_run` does **not** reuse `sync_report` (which has a NOT NULL FK to `scan_source`).

---

## `LibrarySource` / `InstallStatus` derivation (FR-017, FR-018)

| Derived value | Rule |
|---------------|------|
| `InstallStatus` | `INSTALLED` if `EXISTS` installation with `presence = 'INSTALLED'` on an `enabled` source; else `NOT_INSTALLED` |
| `LibrarySource` of a game | `OWNED` if any active `OWNED` entry; else `FAMILY` if any active `FAMILY` entry; else `LOCAL` |
| Visibility | `installed` **or** `EXISTS` active library entry (`removed_at IS NULL`) |

`LOCAL` is not a stored value — it is the derived default for scan-only games (all ROMs and unowned free titles).

---

## Steam account configuration (no table)

| Setting | Source | Exposure |
|---------|--------|----------|
| Steam Web API key | `STEAM_WEB_API_KEY` env | never exposed |
| Steam account id | `STEAM_ID` env | returned in library status (it is not secret) |
| Family token | request body, in-memory for one sync | never stored/logged; status returns only `familyTokenPresent: false` (always, since it is not retained) |

---

## Purge condition (FR-014)

`purgeUninstalledGames` extends to:

```
delete game  when  no installation remains (installed or within grace)
                   AND no active library entry
                   AND no library entry within grace (removed_at >= now - grace)
```

Installations are deleted first (as today); orphaned games are then checked against
`GameLibraryEntryRepository.existsActiveOrGraceByGameId(gameId)`. Local cover files are deleted with the game.

---

## Index / query impact

- `uq_game_steam_app_id` makes resolve-or-create safe under concurrency (insert-or-fetch).
- `idx_game_library_entry_source_removed` supports the visibility `EXISTS` and the shrink guard's
  active-count query.
- `findVisibleGames` / `findForChatFilter` add an `OR EXISTS (game_library_entry e WHERE e.game = g AND e.removed_at IS NULL)` branch and `installStatus` / `librarySource` predicates; existing
  platform/search/host predicates are unchanged.
