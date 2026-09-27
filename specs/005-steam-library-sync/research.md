# Phase 0 Research: Steam Library & Family Library Sync

**Date**: 2026-09-27
**Spec**: [spec.md](./spec.md) · **Plan**: [plan.md](./plan.md)
**Branch**: `005-steam-library-sync` (off `main` @ `6272841`, 004 merged)

Decisions and evidence for the 005 Steam library feature. Base read: branch `004-gamecatalog-refinements` (now merged).

---

## Clarify decisions (2026-09-27) — these supersede parts of the plan input below

| # | Decision | Supersedes |
|---|----------|------------|
| Q1 | **"Not installed" is global.** It means no installation on any enabled source; the host filter is hidden/disabled while "Not installed" is active. No per-host "not installed" semantics. | US3.4 options; adds FR-023 |
| Q2 | **Excluded family titles are omitted entirely** — no entry, no enrichment, no cost. | The `share_excluded` column and `exclude_reason` handling in §4/§5 below are dropped; the Library entry keeps only family owner display names |
| Q3 | **No local LLM.** Local (Ollama) inference stays out of scope; gamecatalog enrichment switches Anthropic **Sonnet → Haiku**. Not-installed library games get **zero AI** — deterministic Steam data (incl. `short_description`) and artwork only. The "Local-only backlog / `AiFallbackPolicy.LOCAL_ONLY`" bullet in §3 is **dropped**; backlog ordering now concerns metadata fetches only. The `short_description` Option A/B decision is settled for not-installed games (deterministic only); for installed games the AI description remains. | §3 "Local-only backlog", §5 local-only notes |
| Q4 | **Branch off `main`** after the 004 merge (done). Spec artifacts ride the 005 branch. | — |

---

## Verification findings (2026-09-27) — codebase read on 004

The plan input below was written from a read of 004 and marked several claims "verify". They were checked against the working tree; corrections and confirmations:

1. **No `findBySteamAppId` exists** — the current lookup is `findByPlatformAndSteamAppId(platform, appId)` (`GameRepository.java:33`), used by `ReconciliationService.findAdoptableGame` (`:126-134`). Gap 1's "look up by app ID alone" requires adding `findBySteamAppId`.
2. **`idx_game_steam_app_id` is a plain partial index, not unique** (`V20260927001__gamecatalog_multihost_refinements.sql:69`). Gap 1 confirmed.
3. **No `title_source` / title-provenance column exists.** `applySnapshot` overwrites title+platform via `game.updateCatalogInfo(...)` on the existing-installation path (`ReconciliationService.java:56-60`); adoption never mutates. Gap 4 confirmed.
4. **`applyDeterministicMetadata` OVERWRITES, does not fill nulls** (`Game.java:102-108`) and unconditionally sets `metadataStatus = OK`. `EnrichmentService.enrichOne` calls both `applyEnrichment` and `applyDeterministicMetadata` with AI values (`EnrichmentService.java:81-102`) → **AI overwrites deterministic data today**. Gap 6 confirmed real.
5. **`SteamAppDetailsClient` uses `filters=basic`** (`SteamAppDetailsClient.java:59-65`) and maps only genres/developers/publishers/release_date into `SteamMetadata`. Per Steam's documented filter semantics, `basic` returns type, name, steam_appid, required_age, dlc, detailed_description, about_the_game, short_description, supported_languages, header_image, website, requirements — **not** genres/categories/developers/publishers/release_date. So `SteamMetadata` is effectively all-null and `metadataStatus` becomes `OK` with nulls: **deterministic metadata is broken today** — the fields the UI shows come from the AI. `basic` *does* include `type` (tool/game discrimination) and `short_description` (free description). Fix: `filters=basic,genres,categories,developers,publishers,release_date` (or drop `filters`).
6. **No rate limiting / 429 handling** in `SteamAppDetailsClient` (`:70-78` — any `RestClientException` → `Optional.empty()`); the only retry is the `metadata_attempts` counter (max 3). Gap: pacing + 429 → stop batch without `recordMetadataFailure`.
7. **No Steam tool/runtime exclusion anywhere** — `SteamLibraryParser.java:32,40-66` accepts every manifest; the Python client `grouping.py:202-248` only dedupes app IDs. Repo-wide grep for Proton/Runtime/Redistributables IDs finds nothing. Gap 7 confirmed net-new. The `appdetails` `type` field (in `basic`) is the authoritative check; a small app-ID deny-list covers the offline path.
8. **`ResilientAiService` has no fallback chain and no Ollama** — Anthropic-only (`ResilientAiService.java:33`); per-module model via `jordylab.ai.modules.<module>.model` in `application.yaml` (`:45-55`). **Model switch is a one-line config change** (`gamecatalog.model: claude-sonnet-5` → `claude-haiku-5`). Health cache is outcome-based, not a probe (`ProviderHealthCache.java:22-44`). The root-AGENTS.md "Ollama fallback" describes target architecture only.
9. **No Modulith events are published or listened to today** — no `@ApplicationModuleListener`/`publishEvent` anywhere in `src/main`; the `event_publication` table (`V20260925__create_event_publication_table.sql`) is provisioned but unused. 005's "applied Steam scan → owned sync due" listener would be its first user. No `@Scheduled` in gamecatalog.
10. **`sync_report.source_id` is a NOT NULL FK to `scan_source(id)`** (`V20260802`), so library runs need their own `library_sync_run` table. `SyncOutcome` is a Java enum `{APPLIED, NO_CHANGE, OUT_OF_ORDER, SCAN_FAILED, REJECTED}` with no DB check constraint (`SyncOutcome.java:3-9`).
11. **Purge** `purgeUninstalledGames` (`ReconciliationService.java:69-101`): orphan = after deleting `UNINSTALLED` installations older than `grace-period-days` (30), `countByGameId(gameId) == 0`. Deletes the game row (description goes with it) + local cover file. Gap 2 confirmed.
12. **Visibility queries hard-code `presence = 'INSTALLED' AND source.enabled = true`** (`GameRepository.java:39-86`: `findVisibleGames`, `findVisibleById`, `findVisiblePlatforms`, `findVisibleHosts`, `findForChatFilter`). No `installStatus`/`librarySource` params exist. Gap 3 confirmed.
13. **Backlog ordering** is `findByEnrichmentStatusOrderByCreatedDateAsc` (`GameRepository.java:19`); metadata uses `findByMetadataStatusAndSteamAppIdIsNotNull` (`:25`). Batch sizes: metadata 25, enrichment 8; `CatalogRefreshService` runs metadata then enrichment (no artwork pass — artwork runs only inline after a scan).
14. **Chat filter recipe** (from the 004 `hosts` integration): add to `ALLOWED_FILTER_FIELDS` + prompt schema, add to the `ChatFilter` record + `parseFilter` with visible-member validation, add the JPQL predicate, add the controller query param, add the FE `GamesQuery` param. `findForChatFilter` also gates on `enrichmentStatus = 'ENRICHED'`.
15. **Frontend has no URL filter sync** — `game-library.store.ts` filters are in-memory signals (`selectHost` → `#selectedHost` + `#queryTrigger`). "Preserved the same way the host filter is" therefore means the same in-memory signal behaviour; no router work. The Steam panel in `source-manager` is the natural home for library sync UI.
16. **Working tree note**: the 004 branch had uncommitted chat cover-URL work (`ChatGameRef`, `ChatService`, FE chat), carried onto this branch (`ArtworkUrls.java` untracked). Not part of 005's scope; left as-is.

---

## Plan input (original, 2026-09-27) — as provided

### 1. What already exists and should be reused (don't rebuild)

- **Host-independent `Game` + per-host `GameInstallation`** (`V20260927001__gamecatalog_multihost_refinements.sql`). `ReconciliationService.addInstallationFor` already adopts an existing game (Steam: platform + app ID; ROM: platform + lowercase title) and only adds an installation. Its Javadoc states enrichment, metadata and artwork are never touched on adoption. **The library sync must go through the same resolve-or-create path, not a parallel one.**
- **Status fields on `Game`** (`EnrichmentStatus`, `MetadataStatus`, cover/banner status, attempt counters) make each data pass idempotent; a game is only processed while `PENDING`.
- **Change detection and guards in `ScanService`**: payload SHA-256 → `NO_CHANGE`, `isSuspiciousShrink` with `max-shrink-fraction: 0.5`, rejection reasons, `SyncReport`. Mirror these for library syncs.
- **Inline passes, no schedulers** (`CatalogRefreshService`, `SteamMetadataService.fetchPending`, `EnrichmentService.enrichPending`, batch sizes 25 / 8).
- **Spring Modulith event publication table** (`V20260925__create_event_publication_table.sql`) — use it to decouple "Steam scan applied" → "library sync due" without a scheduler.

### 2. Gaps in the current code this feature must fix

1. **No DB-level uniqueness for Steam identity** — replace plain partial index with `CREATE UNIQUE INDEX uq_game_steam_app_id ON game (steam_app_id) WHERE steam_app_id IS NOT NULL;` (duplicate-check query in the migration first). Resolve with insert-or-fetch (`INSERT ... ON CONFLICT DO NOTHING` then select, or catch `DataIntegrityViolationException` and re-read). Look up by app ID alone (`findBySteamAppId`).
2. **Purge deletes library games** — orphan check must become "no installations AND no active or grace-period library entry".
3. **Visibility requires an installation** — `installed OR active library entry`, plus `installStatus` / `librarySource` parameters.
4. **Title flip-flop** — store `title_source` (`LIBRARY` / `MANIFEST` / `ROM`); a scan must not overwrite a library title.
5. **Steam metadata comes back empty** (verified, see findings #5) — fix the `filters` value; use `type` and `short_description`.
6. **AI output overwrites deterministic data** (verified, findings #4) — enrichment may only fill still-null fields.
7. **Steam tools are catalogued and enriched** (verified, findings #7) — exclude by `type` + app-ID deny-list; one-off cleanup of existing rows.

### 3. Cost-economy design

- **Order of passes per game:** create/link → artwork (cheap, deterministic via app ID) → Steam metadata → AI enrichment. Enrichment for a Steam game waits until metadata status is terminal (`OK`/`FAILED`) so the prompt asks only for what is missing.
- **Use Steam `categories`** for single/multiplayer flags deterministically; `maxLocalPlayers` still needs the AI or stays null.
- **Not-installed games: no AI** (Q3) — deterministic `short_description` + metadata only.
- **Backlog ordering:** installed games before not-installed (metadata fetches).
- **Steam store rate limit (~200 req / 5 min):** pace `SteamAppDetailsClient` calls (≥1.5 s apart). On HTTP 429, stop the batch and do not call `recordMetadataFailure`.
- **Instrumentation:** count AI calls and store calls per sync run (FR-007); Micrometer counters are the project pattern.

### 4. Data model (new migration)

- `game`: unique partial index on `steam_app_id`; `title_source`.
- `game_library_entry` (new): `id`, `game_id` FK, `library_source` (`OWNED` | `FAMILY`), `first_seen_at`, `last_seen_at`, `removed_at`, `family_owner_names` text, audit columns. `UNIQUE (game_id, library_source)`. Index `(library_source, removed_at)`.
- `library_sync_run` (new): `id`, `library_source`, `started_at`, `finished_at`, `outcome`, `content_hash`, `entries_submitted/added/removed`, `metadata_calls`, `ai_calls`, `error_code`.
- **Rejected alternative:** modelling the Steam account as a `ScanSource` with a fake hostname (would make library games count as "installed", the exact distinction this feature adds).

### 5. Sync flow

**Owned library (`SteamLibrarySyncService`):**
1. `GET https://api.steampowered.com/IPlayerService/GetOwnedGames/v1/` with `key`, `steamid`, `include_appinfo=true`, `include_played_free_games=true`.
2. Normalise to a sorted `(appid, name)` list → SHA-256 → if equal to the last successful run, record `NO_CHANGE` and stop.
3. Empty response, or shrink beyond `max-shrink-fraction` → `REJECTED` / suspicious, no writes (unless `force`).
4. For each app ID: resolve-or-create the game → upsert the library entry. Existing games are not touched.
5. Active entries missing from the response → set `removed_at`.
6. Run the same bounded inline passes as a scan (artwork → metadata → enrichment).

**Trigger:** manual endpoint, plus a Modulith event published after an `APPLIED` Steam scan; the listener runs the owned sync only if the last successful run is older than `jordylab.gamecatalog.library.min-interval` (e.g. `PT12H`). No `@Scheduled`.

**Family library (`SteamFamilySyncService`):** manual endpoint accepts the token (write-only, Keycloak-protected) → `GetFamilyGroupForUser` → `GetSharedLibraryApps` → same steps 2–6 with source `FAMILY`. Token used in memory only. Response field names to be confirmed from a real sanitised capture before coding, stored as a test fixture.

### 6. Security

- `STEAM_WEB_API_KEY`, `STEAM_ID` as env secrets (like `ANTHROPIC_API_KEY`).
- Both Steam endpoints take secrets as query parameters — no `RestClient`/request logging may print full URIs.
- Treat Steam responses as untrusted: numeric app IDs; titles through `TextSanitizer` + NFC normalisation like scan entries.
- Never accept or store `steamLoginSecure`.

### 7. API & frontend

- `GET /api/gamecatalog/games?installStatus=INSTALLED|NOT_INSTALLED|ALL&librarySource=OWNED,FAMILY,LOCAL&host=…`. Default `installStatus=INSTALLED`.
- `GameSummaryResponse` / `GameDetailResponse`: add `installStatus`, `librarySource`; detail adds `familyOwners`.
- `POST /api/gamecatalog/library/steam/sync`, `POST /api/gamecatalog/library/steam-family/sync` (token in body), `GET /api/gamecatalog/library/status`.
- Frontend: extend `game-library.store.ts` filter state the same way `host` is done; add models + mocks; status/source badges on cards; library section in settings; chat filter fields.

### 8. Tests

- **SC-001 cost test:** an existing enriched game gets a new installation and a library entry → zero interactions with `SteamAppDetailsClient`, `ArtworkLookupClient` and `ResilientAiService`.
- **SC-002:** the same owned response twice → second run `NO_CHANGE` with no repository writes.
- **SC-004:** concurrent scan + library sync for the same new app ID (Testcontainers, two threads) → exactly one game.
- **Purge:** an owned game with an expired installation survives the purge; a removed library entry + no installation → purged after grace.
- **Title authority**, **tool exclusion**, **429 handling**, **no-AI-for-not-installed**.
- Contract fixtures for `GetOwnedGames` (normal, `{}` empty, error) and the family endpoints.
- Repo rule: no hand-seeding the database; validate end-to-end with the real scanner plus a real library sync (quickstart).
