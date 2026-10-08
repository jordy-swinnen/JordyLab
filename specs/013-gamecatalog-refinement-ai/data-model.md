# Data Model: Game Catalog Refinement and LibBot

**Spec**: [spec.md](./spec.md) | **Research**: [research.md](./research.md) | **Schema**: `gamecatalog` (all new tables), `settings` (one event, no table)

Conventions (repo rules): UUID ids, `created_at`/`updated_at` from `BaseEntity`, builder-only creation with `Preconditions` in `build()`, named
mutation methods, Flyway owns all DDL, every migration starts with `CREATE SCHEMA IF NOT EXISTS` + `SET search_path`.

## Entity map

```text
Host 1──* ScanSource 1──* GameInstallation *──1 Game *──* GameLibraryEntry
                                  │            │ 1
                                  │            ├──* ConsoleGameEntry *──1 Console
                                  │            ├──* GameMark (per user)
                                  │            └──1 GameEmbedding
RefreshRun (standalone)           └ rom_status (emulated copies only)
```

A **place** is a `GameInstallation` (scan copy), a `GameLibraryEntry` (Steam library) or a `ConsoleGameEntry`. A game is visible while
it has at least one *effective* place (spec FR-023, FR-026, FR-029).

## New and changed entities

### Host (new aggregate, `host`)

| Field | Type | Rules |
|---|---|---|
| `id` | UUID | |
| `hostname` | text, required | from the machine; unique ignoring case; never edited by the admin |
| `displayName` | text, nullable | trimmed; blank = cleared (stored null); ≤ 40 chars; unique ignoring case, and not equal (ignoring case) to any console name |

Behaviour (entity, the single place that decides the shown name): `label()` returns `displayName` when present, otherwise `hostname`.
`rename(String)` validates (length, blank-as-clear) and registers `HostRenamed`. Cross-aggregate uniqueness (hosts vs consoles) is checked in
`PlaceNameService` under an advisory lock; per-table uniqueness is also a unique index.

### ScanSource (changed)

- gains `host` (`ManyToOne`, required); **loses** `hostname` (read it from `host`). `sourceKey` stays `hostname:TYPE`.
- `SourceType` loses `SWITCH`; remaining `STEAM`, `EMUDECK`. `platform` stays the default platform of the source type.
- `enabled` semantics unchanged. New query `countGamesHiddenBy(sourceId)` (games visible now that would not be visible with this source disabled).

### Console (new aggregate, `console`)

| Field | Type | Rules |
|---|---|---|
| `id` | UUID | |
| `platform` | text, required | canonical name from `PlatformCatalog`, or a custom name (neutral colour) |
| `name` | text, required | 1..40 chars; unique ignoring case; defaults to the platform name; same cross-uniqueness as host names |

Methods: `rename`, `isCustomPlatform()`. Removal is a service operation: delete its entries; delete games left with no place and no
library entry (and their marks, embeddings, local artwork), keep the rest.

### ConsoleGameEntry (new, `console_game_entry`)

`id`, `game` (required), `console` (required), unique `(game_id, console_id)`. Always effective: consoles have no install state. Created and
deleted only by the admin (replaces the manual `GameInstallation`).

### GameInstallation (changed)

- **removed**: `manual`, `format`, `InstallationFormat`, the check constraint and index from the Switch migration.
- **added**: `platform` (text, required: the platform of *this copy*, canonical, from the parser, so one game can be a SNES ROM here and a
  Switch entry there); `romStatus` (`UNKNOWN | VALIDATED | BROKEN`, nullable).
- `romStatus` is non-null exactly for copies whose source is an emulation source; `changeRomStatus(RomStatus, SourceType)` fails the
  precondition for any other source type (FR-051). New copies on emulation sources start `UNKNOWN`.
- `seenAgain`, `markUninstalled` unchanged, so `romStatus` survives rescans and the grace period (FR-055).

### Game (changed)

- **removed**: `platform`. **added**: `titleKey` (text, required, normalised; indexed, not unique), nothing else on the entity for marks or votes.
- `igdbGameId`: unique **alone** (`uq_game_igdb_game_id`), replacing `uq_game_platform_igdb_game_id`. An IGDB id identifies a game across platforms.
- `steamAppId` unique partial index unchanged.
- Derived (queries, never stored): platforms (distinct of places), sources (A/B/C below), install status, vote totals, ROM summary.
- `updateCatalogInfo(title, platform, source)` loses the platform parameter; title authority (`TitleSource`) unchanged.
- **Description provenance (FR-059)**: `descriptionSource` (`AI | STEAM`, nullable when there is no description), `descriptionModel` (the model the provider
  reported as answering; null for Steam text and for older AI text), `descriptionRequestedModel` (the selected id, stored only when it differs from the
  answering model, i.e. a router such as `jev-router` or the provider fallback), `descriptionWrittenAt`. `applyEnrichment(...)` takes an
  `AiAuthorship(answeredModel, requestedModel, writtenAt)` value object; `applyDeterministicDescription(...)` sets `STEAM`. Backfill in migration 3: `ENRICHED`
  games → `AI` with null model, other games that have a description → `STEAM`. The existing derived `metadataSource` for "facts from" stays and now only feeds the Spec sheet.
- New artwork/data bookkeeping for the auto-fill worker: `factsCheckedAt`, `artworkCheckedAt` (timestamps of the last free-lookup attempt),
  `embeddingStatus` is *not* stored (derived from `game_embedding`). Existing `*_status`/`*_attempts` columns are reused.

**Derived source labels** (FR-039), computed by one `GameSources.of(...)` helper:

| Label | When |
|---|---|
| Steam (Owned) | active OWNED library entry, **or** an installed Steam copy with no active library entry (until a sync resolves it) |
| Steam (Family) | active FAMILY entry and no active OWNED entry |
| Emulated | an installed copy on an enabled emulation source |
| Console | at least one console entry |

`LibrarySource.LOCAL` is retired. A game can show several labels.

### GameMark (new, `game_mark`)

| Field | Type | Rules |
|---|---|---|
| `id` | UUID | entity id (repo rule: ids are UUID) |
| `game_id` | UUID FK | with `user_subject`: **unique index** `uq_game_mark_game_user` (one mark per user per game) |
| `user_subject` | text | Keycloak `sub` from the JWT, never from the client |
| `mark` | `WANT_TO_PLAY \| PLAYED_LIKED \| PLAYED_DISLIKED` | check constraint |

Methods: `GameMark.of(game, subject, mark)`; changing replaces the row's `mark`; clearing deletes the row. Totals are `COUNT(*) GROUP BY mark`.
Index `(game_id, mark)` for totals. Deleted with the game, and by `UserAccessRemoved(subject)` (FR-046). Voter identity is never returned by any endpoint (FR-044).

### GameEmbedding (new, `game_embedding`)

`game_id` PK/FK, `model` (text), `content_hash` (text), `embedding vector(1536)`, `embedded_at`. HNSW index on `embedding vector_cosine_ops`.
A row is stale when `model` differs from configuration or `content_hash` differs from the hash of the current document text. Deleted with the game.

### RefreshRun (new, `refresh_run`)

| Field | Type | Rules |
|---|---|---|
| `id` | UUID | |
| `kind` | `DATA \| AI` | |
| `status` | `RUNNING \| SUCCEEDED \| FAILED \| STOPPED \| INTERRUPTED` | partial unique index on `(kind) WHERE status = 'RUNNING'`: one running run per kind |
| `total`, `processed`, `failed` | int | progress |
| `stopRequested` | boolean | set by `POST .../stop`; checked between games |
| `failureSummary` | text, nullable | counts by cause, for example "42 failed: INSUFFICIENT_CREDITS" |
| `startedBy` | text | JWT subject |
| `startedAt`, `finishedAt` | timestamptz | |

Methods: `advance(ok)`, `requestStop()`, `finish(status)`. On application start, rows still `RUNNING` become `INTERRUPTED`.

### Conversation (not persisted)

In-process only (research A7): `ConversationKey(userSubject, conversationId)` → `Deque<ConversationTurn>` (max 10), where
`ConversationTurn(userText, answerText, citedGameIds, outcome)`. Caffeine `expireAfterAccess(2h)`, `maximumSize(2000)`. No table, no migration.

### PlatformCatalog (immutable registry, code not data)

Entries: canonical name, aliases, brand family (`PLAYSTATION | XBOX | NINTENDO | SEGA | STEAM | OTHER`), generation, IGDB platform id and exact name (verified against live IGDB, research B4), libretro repo,
handheld flag, `ChipColors(background, foreground, border?)`. Looked up by `canonical(String)`; unknown names come back as `OTHER` with the neutral
colours, so custom consoles always render.

## Migrations

All named `V<yyyyMMdd>NNN__...`, appended after `V20261005001`. Use the next free dates when implementing (shown here as 20261008).

| # | File | Kind | Does |
|---|---|---|---|
| 1 | `V20261008001__gamecatalog_places_hosts_consoles.sql` | SQL | `CREATE EXTENSION IF NOT EXISTS vector`; create `host` (backfill one per distinct hostname, excluding the virtual Switch), `console`, `console_game_entry`; add `scan_source.host_id` (backfill, then NOT NULL); add `game_installation.platform` (backfill from `game.platform`) and `rom_status` (`'UNKNOWN'` for copies on `EMUDECK` sources); create "Nintendo Switch" console from the virtual source and move its manual installations to `console_game_entry`; delete those installations and the `SWITCH` scan source; drop `manual`, `format`, constraint and index; add `game.title_key` (nullable for now), `facts_checked_at`, `artwork_checked_at` |
| 2 | `V20261008002__GameIdentityBackfillAndMerge.java` | **Java** | normalise platform names through `PlatformCatalog` on installations and console platform; compute `title_key` for every game with `TitleKeys.normalise`; merge duplicate games (research B3: first by equal `igdb_game_id`, then by `title_key`), repointing installations, library entries, console entries and dropping duplicates on `uq_game_library_entry_game_source` and `console_game_entry (game_id, console_id)`; honours the dry-run property; logs every merge |
| 3 | `V20261008003__gamecatalog_identity_marks_runs.sql` | SQL | `title_key` NOT NULL + index; drop `game.platform`; swap `uq_game_platform_igdb_game_id` for unique `igdb_game_id`; create `game_mark`, `refresh_run` (+ partial unique), `game_embedding` (+ HNSW) |

Order matters: the Java migration needs `title_key` and `game_installation.platform` from #1 and must run before `game.platform` is dropped in #3.
`game_embedding` rows are produced by the auto-fill worker after start, not by the migration (no model call in a migration).

**Flyway Java migration note**: the repo has SQL migrations only; Flyway 11 (via `flyway-database-postgresql`) supports Java migrations on the classpath
(`db/migration`) with no extra dependency. It receives a `Context` connection, so it uses plain JDBC, not Spring beans; `TitleKeys` and
`PlatformCatalog` are pure static classes for that reason.

**Rollback**: migrations are append-only. The safety net is the on-demand CNPG backup before the prod release and the dry-run before any real run.
There is no down-migration; the merge is not reversible, which is why the dry-run output is reviewed first.

## Validation rules (where each spec rule lives)

| Spec rule | Enforced in |
|---|---|
| One mark per user per game (FR-043) | unique index `(game_id, user_subject)` |
| ROM status only on emulated copies (FR-051) | `GameInstallation.changeRomStatus` precondition; service rejects with 409; tested |
| Host/console names unique ignoring case, ≤ 40 (FR-031) | unique indexes `lower(...)`, `Host.rename`, `Console.rename`, `PlaceNameService` |
| One running refresh per kind (FR-047) | partial unique index on `refresh_run` |
| Same game across platforms, no duplicates (FR-023, FR-024) | `GameIdentityService` under `pg_advisory_xact_lock(hashtext(title_key))`; unique `steam_app_id`, `igdb_game_id` |
| A game leaves only with its last place (FR-026) | `PlaceRemovalService.releaseGameIfOrphaned` (one implementation used by purge, console removal, console game delete) |
| Disabled source hides, deletes nothing (FR-029) | visibility predicate unchanged, tested with hide, partial-hide, restore |
| References ≤ 10 and named in the answer (FR-003) | `LibBotAnswerValidator` |

## Visibility predicate (one definition)

A game is visible iff **any** of:
1. an installation with `presence = 'INSTALLED'` whose source `enabled = true`;
2. a `game_library_entry` with `removed_at IS NULL`;
3. a `console_game_entry`.

It is defined once (a JPQL fragment/specification used by the grid, detail, platforms, places, LibBot retrieval, hide-impact count) instead of being
copy-pasted into five `@Query` strings as it is today (`GameRepository`).
