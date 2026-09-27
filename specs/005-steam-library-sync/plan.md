# Implementation Plan: Steam Library & Family Library Sync

**Branch**: `005-steam-library-sync` | **Date**: 2026-09-27 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/005-steam-library-sync/spec.md`

**Builds on**: `004-gamecatalog-refinements` (merged on `main` @ `6272841`). Multi-host `Game`/`GameInstallation`, inline metadata + enrichment passes, no schedulers.

## Summary

Add a second way a Steam game belongs to the catalog: **library membership**. Today a game is visible only while it has an installed installation, and an orphaned game is deleted (with its description and artwork) after the 30-day grace period. 005 makes a game visible when it is *installed or in a library*, and makes installation status + library source filterable.

Backend-first, five parts:

1. **Identity hardening** — replace the plain partial `idx_game_steam_app_id` with a **unique** partial index (duplicate-check in the migration first); add `findBySteamAppId`; resolve-or-create with insert-or-fetch so a concurrent scan and library sync cannot create two games (FR-001, SC-004).
2. **Library model** — new `game_library_entry` (one per game per `OWNED`/`FAMILY` source; first/last seen, removed-at, family owner names) and `library_sync_run` (outcome, content hash, counts, `metadata_calls`, `ai_calls`). Excluded family titles are omitted entirely (Q2).
3. **Sync services** — `SteamLibrarySyncService` (documented `GetOwnedGames`, key + steamid) and `SteamFamilySyncService` (undocumented `IFamilyGroupsService`, in-memory token). Both mirror `ScanService`'s SHA-256 `NO_CHANGE` short-circuit and suspicious-shrink guard, go through the same resolve-or-create path as scans, and run the same bounded inline passes. Owned sync piggybacks on an applied Steam scan via a Modulith event (first user of `event_publication`), gated by a min-interval — no scheduler.
4. **Cost economy** — `SteamAppDetailsClient` currently requests `filters=basic`, which **does not include** genres/categories/developers/publishers/release_date, so deterministic metadata is effectively broken and the UI's metadata comes from the LLM. Fix the filter set, use `type` to exclude Steam tools/runtimes and `short_description` as the free description; pace store calls (≥1.5 s) and treat 429 as "stop the batch", not a game failure; not-installed library games get **zero AI** (FR-022); enrichment may only fill still-null fields; gamecatalog's Anthropic model switches Sonnet → Haiku; backlog orders installed games first.
5. **Visibility, status & filtering** — every visibility query becomes `installed OR active library entry`, gain `installStatus` (default `INSTALLED`) and `librarySource` (`OWNED > FAMILY > LOCAL`) parameters and propagate through the list, detail, platforms, hosts, chat filter and frontend (status/source badges, filters mirroring the host pattern, library section in Sources).

## Technical Context

**Language/Version**: Java 25 (backend), Angular 21 / Nx 22 / TypeScript 5.x (frontend), Bun

**Primary Dependencies**: Spring Boot 4.0.3, Spring Modulith 2.0.3, Spring Data JPA, Flyway, Lombok (backend — no new dependencies); spartan/ui helm (frontend — no new dependencies); `gamecatalog-scanner/` unchanged (library sync is server-side only)

**Storage**: PostgreSQL 16 — additive migration in the `gamecatalog` schema (owned by `jordylab-be` Flyway): unique partial index + `title_source` on `game`, new `game_library_entry`, new `library_sync_run`

**Testing**: JUnit 5, AssertJ, Mockito, Testcontainers, WireMock (`GetOwnedGames` + appdetails), MockMvc (backend, 80% JaCoCo gate); Vitest + `@ngneat/spectator/vitest` (frontend, 80% gate)

**Target Platform**: Hetzner VPS Compose (backend), browser SPA (host app); no change to JordyBox or the scan client

**Project Type**: Web application (existing monolith module + domain libs — no new projects)

**Performance Goals**: Grid/filter stays within 004's envelope (< 2 s initial, < 1 s search at 5,000 games); library visibility predicate is an indexed `EXISTS` on `game_library_entry`; Steam store fetch is batch-bounded (25) and paced ≥1.5 s apart, off the request path; first sync inserts games fast, then metadata/enrichment trickle

**Constraints**:
- No schedulers (004 rule): owned sync triggers manually or via a Modulith event listener on an applied Steam scan, gated by `jordylab.gamecatalog.library.min-interval`
- Steam store `appdetails` is keyless but rate-limited (~200 req / 5 min): paced, batch-bounded, 429 pauses the batch without a failure increment
- Steam Web API key + steamid from env secrets (`STEAM_WEB_API_KEY`, `STEAM_ID`); family token is in-memory only, never stored, logged or returned
- Family endpoints are undocumented: response field names confirmed against a sanitised live capture and frozen as a WireMock fixture before coding
- Migrations append-only; existing Steam games must survive the uniqueness change (duplicate check + deterministic collapse)
- AI calls route through the existing `ResilientAiService`; the model switch is config only

**Scale/Scope**: Single user; 1 Steam account; up to ~5,000 games (~1,000 owned ± family); 1 backend module extended; 1 migration; 4 frontend views touched; 0 new projects; 0 scan-client change

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Clean Code Discipline | Pass | Extends the existing module in place; library sync reuses `ReconciliationService`'s resolve-or-create path rather than forking a parallel one (DRY); one `LibrarySource` enum and one `LibrarySyncRun` concept; fail-fast `Preconditions` guards in builders |
| II. Fail Fast, No Silent Failures | Pass | Empty/failed/suspicious syncs are explicit outcomes and mutate nothing; 429 is an explicit pause, not a silent metadata failure; excluded tools are explicit; unknown family response shape fails the run rather than guessing |
| III. Immutable, Builder-First Design | Pass | `GameLibraryEntry` + `LibrarySyncRun` follow the canonical entity structure (`@Builder`, guards in `build()`, `@Record` DTOs); status/outcome are enums, not strings |
| IV. Testing Discipline | Pass | TestBuilders for the two new entities; WireMock for both Steam clients (normal/empty/error fixtures); Testcontainers concurrency test (SC-004); `verifyNoInteractions` cost test (SC-001); Spectator specs per touched component; no `any()`, captors assigned |
| V. Language & Tooling Currency | Pass | Java 25 (no `var`), Angular 21 signals/zoneless, `inject()`, `#field`; Spring Boot 4 + Modulith 2 APIs verified against the current codebase |

No constitution violations — no complexity tracking needed.

## Project Structure

### Documentation (this feature)

```text
specs/005-steam-library-sync/
├── plan.md              # This file
├── research.md          # Phase 0: plan input + clarify decisions + verification findings
├── data-model.md        # Phase 1: library entry, sync run, unique index, title authority
├── quickstart.md        # Phase 1: end-to-end validation guide (real scanner + real sync)
├── contracts/
│   ├── catalog-api.md   # Games list/detail filters, library endpoints, chat filter fields
│   └── steam-api.md     # GetOwnedGames, family endpoints, appdetails filters + fixtures
├── checklists/
│   └── requirements.md  # Spec quality checklist (passed)
└── tasks.md             # Phase 2 output (/speckit-tasks)
```

### Source Code (repository root)

```text
jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/
├── GameCatalogProperties.java            # + library() record (minInterval, maxShrinkFraction,
│                                         #   staleAfterDays) + metadata pacing
├── domain/
│   ├── Game.java                         # + titleSource; applyDeterministicMetadata fill-nulls-only
│   ├── GameLibraryEntry.java             # NEW aggregate (game, source, seen/removed, familyOwners)
│   ├── LibrarySource.java                # NEW enum OWNED | FAMILY
│   ├── LibrarySyncOutcome.java           # NEW enum APPLIED | NO_CHANGE | FAILED | SUSPICIOUS
│   ├── LibrarySyncRun.java               # NEW aggregate (outcome, hash, counts, call counts)
│   ├── TitleSource.java                  # NEW enum LIBRARY | MANIFEST | ROM
│   └── repository/
│       ├── GameLibraryEntryRepository.java  # NEW
│       ├── LibrarySyncRunRepository.java    # NEW
│       └── GameRepository.java              # findBySteamAppId, visibility + filter params
├── rest/
│   ├── client/
│   │   ├── SteamAppDetailsClient.java    # filters fixed; type check; 429-aware; paced
│   │   ├── SteamOwnedGamesClient.java    # NEW RestClient (documented GetOwnedGames)
│   │   └── SteamFamilyClient.java        # NEW RestClient (undocumented family endpoints)
│   └── controller/
│       ├── GameCatalogController.java    # games?installStatus=&librarySource=, detail fields
│       ├── LibraryController.java        # NEW /library/steam/sync, /library/steam-family/sync, /library/status
│       └── model/                        # + installStatus/librarySource/familyOwners, LibraryStatusResponse
└── service/
    ├── ReconciliationService.java        # resolve-or-create by app ID; title authority; purge + library
    ├── SteamLibrarySyncService.java      # NEW owned sync (hash, shrink guard, entry upsert)
    ├── SteamFamilySyncService.java       # NEW family sync (in-memory token)
    ├── LibrarySyncScheduler.java         # NEW Modulith @ApplicationModuleListener (scan applied → due)
    ├── SteamMetadataService.java         # ordering installed-first; store call counting
    ├── EnrichmentService.java            # skip not-installed (FR-022); fill-nulls-only; call counting
    ├── ToolExclusion.java                # NEW @UtilityClass deny-list (Proton/runtimes/redistributables)
    ├── ChatService.java                  # + installStatus/librarySource filter fields
    └── GameQueryService.java             # status/source filter passthrough

jordylab-be/src/main/resources/db/migration/
└── V20260928001__gamecatalog_library.sql  # unique partial index (dup check), title_source,
                                           # game_library_entry, library_sync_run, tool cleanup

jordylab-be/src/main/resources/application.yaml
└── jordylab.gamecatalog.library.* + jordylab.ai.modules.gamecatalog.model: claude-haiku-5

jordylab-fe/libs/gamecatalog/
├── api/src/lib/
│   ├── gamecatalog.models.ts           # InstallStatus, LibrarySource, familyOwners, library status
│   ├── gamecatalog-api.service.ts      # GamesQuery + installStatus/librarySource, library endpoints
│   ├── game-library.store.ts           # selectedInstallStatus/selectedLibrarySource signals
│   └── mocks/                          # + install-status/library-source/library-status mocks
└── ui/src/lib/
    ├── game-grid/game-grid-view.*      # status + source filter chips; badges on cards
    ├── game-detail/game-detail-view.*  # status/source rows, family owners
    └── source-manager/source-manager-* # library section: owned/family sync buttons, last-run
                                        #   (metadata/AI call counts), family token field
```

**Structure Decision**: Everything extends the existing backend `gamecatalog` module and `libs/gamecatalog/{api,ui}` in place, matching the monolith-first rule and the 002–004 layout. The only new structural elements are the two library domain aggregates (required by the new lifecycle) and three `rest/client` classes (two new Steam endpoints, one corrected). The scan client is untouched because library membership never comes from disk scanning.

## Phase 0 / Phase 1 outputs

- `research.md` — plan input, clarify decisions (Q1–Q4) and verified findings, with the supersessions they cause.
- `data-model.md` — `Game` changes, `GameLibraryEntry`, `LibrarySyncRun`, `LibrarySource`, `TitleSource`, validity rules and lifecycle transitions.
- `contracts/catalog-api.md` + `contracts/steam-api.md` — REST surface and the three external Steam interfaces (with fixtures).
- `quickstart.md` — real-data end-to-end validation (no hand-seeding).
