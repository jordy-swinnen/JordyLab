# Implementation Plan: Game Catalog

**Branch**: `002-game-catalog` | **Date**: 2026-08-02 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/002-game-catalog/spec.md`

## Summary

Build the Game Catalog feature across three components. (1) **Backend**: a new `gamecatalog` Spring Modulith module in `jordylab-be` exposing a bearer-authenticated ingestion API (snapshot sync + fallback artwork upload + config check-in for the JordyBox agent), the catalog query/chat/sources APIs for the web app, AI enrichment via the existing `ResilientAiService` (structured multiplayer facts + prose, strict-JSON extraction), and hybrid artwork sourcing (Steam CDN / libretro-thumbnails first, agent-uploaded local art as fallback). (2) **Frontend**: `libs/gamecatalog/{api,ui}` in the Nx workspace — card grid with search/filter, detail view, chat, and source management — routed from the existing `apps/fna` shell. (3) **Agent**: a new top-level Python 3.12 service (`gamecatalog-sync-service`, sibling to `garmin-sync-service`) — a one-shot CLI run by cron/systemd timer on JordyBox that scans Steam VDF manifests and EmuDeck ROM folders, normalizes titles, groups multi-disc sets, and pushes per-source snapshots over outbound HTTPS only.

Chat is grounded via a two-call structured-query pattern (question → validated JSON filter → DB query → composed answer with DB-row citations), satisfying FR-020 without an embedding pipeline; pgvector semantic search is deferred (no embedding provider is wired, and the structured facts cover the spec's questions).

## Technical Context

**Language/Version**: Java 25 (backend), Python 3.12 (agent), Angular 21 / Nx 22 / TypeScript 5.x (frontend), Bun

**Primary Dependencies**:
- Backend: Spring Boot 4.0.3, Spring Modulith 2.0.3, Spring AI 2.0.0-M2 (Anthropic via existing `ResilientAiService`), Spring Data JPA, Flyway, Lombok, Guava (Preconditions)
- Agent: `httpx` (HTTP), `pydantic` (config/payload models), `vdf` (Steam manifests); dev: `pytest`, `pytest-mock`, `pytest-cov`, `respx`, `ruff`
- Frontend: Nx 22, spartan/ui helm (add: card, badge, input, skeleton; button already installed), `@ngneat/spectator/vitest`, Vitest

**Storage**: PostgreSQL 16 (new `gamecatalog` schema owned by `jordylab-be` Flyway); artwork uploads stored on server filesystem (Docker volume), DB stores references. pgvector present but unused in v1 (see research R5).

**Testing**:
- Backend: JUnit 5, AssertJ, Mockito, Testcontainers (pgvector image), WireMock, MockMvc — JaCoCo 80% gate
- Frontend: Vitest + `@ngneat/spectator/vitest` — 80% line threshold per lib
- Agent: pytest + `respx` HTTP mocking, `tmp_path` filesystem fixtures

**Target Platform**: Hetzner VPS Docker Compose (backend); JordyBox (Windows/Linux HTPC — agent runs as scheduled one-shot process, outbound-only); browser SPA

**Project Type**: Web application (monolith + SPA) plus standalone CLI agent service

**Performance Goals**: Grid initial view < 2s and title search < 1s at 5,000 games (SC-006 — paginated queries, indexed lookup columns); sync of a 5,000-game source completes within bounded payload limits (contracts/ingest-api.md)

**Constraints**:
- JordyBox never accepts inbound connections — agent is a one-shot CLI; all flows (config pull, snapshot push, artwork upload) are agent-initiated
- Authenticated ≠ trusted: every submission field validated/sanitized server-side before any write; bounded payload sizes
- Catalog mirrors reality: authoritative per-source snapshots, 30-day grace then purge for uninstalled games, disabled-source games retained indefinitely, scan failures never reconcile
- No Spring Security starter: ingestion auth via a scoped bearer-token filter (research R4)
- All AI via `ResilientAiService` (Anthropic wired; Ollama deferred per routing table)

**Scale/Scope**: Single user; one agent; ~10 scan sources; up to ~5,000 games; 4 frontend views; 1 new backend module; 1 new Python service

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Clean Code Discipline | Pass | Fixed package layout per `jordylab-be/AGENTS.md`; one-shot CLI over daemon; structured-query chat over embedding pipeline (KISS/YAGNI) |
| II. Fail Fast, No Silent Failures | Pass | Named errors everywhere: sync outcomes (`APPLIED/NO_CHANGE/OUT_OF_ORDER/SCAN_FAILED/REJECTED`), enrichment status + attempt counter, artwork status, explicit chat unavailability — no silent degradation |
| III. Immutable, Builder-First Design | Pass | Entities follow canonical builder + `Preconditions` in `build()`; DTOs are records; agent payloads are frozen pydantic models |
| IV. Testing Discipline | Pass | Entity tests + EqualsVerifier, TestBuilders, `@ApplicationModuleTest`, `@WebMvcTest` + `@MockitoBean`, Spectator specs, pytest+respx; 80% gates both repos |
| V. Language & Tooling Currency | Pass | Java 25 (no `var`), Python 3.12 type hints, Angular 21 signals/zoneless, Spring Boot 4 / Spring AI 2.0.0-M2 APIs verified against current codebase |

No constitution violations — no complexity tracking needed.

## Project Structure

### Documentation (this feature)

```text
specs/002-game-catalog/
├── plan.md              # This file
├── research.md          # Phase 0: decisions R1–R10
├── data-model.md        # Phase 1: entities, validation, state transitions
├── quickstart.md        # Phase 1: end-to-end validation guide
├── contracts/
│   ├── ingest-api.md    # Agent ↔ server: sync, artwork upload, config check-in
│   ├── catalog-api.md   # Frontend ↔ server: games, platforms, chat, sources
│   └── agent-config.md  # Agent local config/state file formats + scan rules
└── tasks.md             # Phase 2 output (/speckit-tasks — NOT created here)
```

### Source Code (repository root)

```text
jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/
├── GameCatalogProperties.java       # @ConfigurationProperties(prefix jordylab.gamecatalog)
├── domain/
│   ├── Game.java                    # aggregate: catalog entry
│   ├── ScanSource.java              # aggregate: announced source
│   ├── SyncReport.java              # aggregate: one submission's outcome
│   ├── Presence.java                # INSTALLED | UNINSTALLED
│   ├── EnrichmentStatus.java        # PENDING | ENRICHED | FAILED
│   ├── ArtworkStatus.java           # PENDING | EXTERNAL_URL | LOCAL_UPLOAD | PLACEHOLDER
│   ├── SourceType.java              # STEAM_LIBRARY | ROM_FOLDER
│   ├── SyncOutcome.java             # APPLIED | NO_CHANGE | OUT_OF_ORDER | SCAN_FAILED | REJECTED
│   └── repository/
│       ├── GameRepository.java
│       ├── ScanSourceRepository.java
│       └── SyncReportRepository.java
├── rest/
│   ├── IngestAuthFilter.java        # bearer-token filter scoped to /api/gamecatalog/ingest/**
│   ├── client/
│   │   └── ArtworkLookupClient.java # Steam CDN + libretro-thumbnails existence checks (RestClient)
│   └── controller/
│       ├── IngestController.java    # /api/gamecatalog/ingest — sync, artwork, config
│       ├── GameCatalogController.java # /api/gamecatalog — games, platforms, chat
│       ├── ScanSourceController.java  # /api/gamecatalog/sources
│       └── model/                   # request/response records per controller
├── service/
│   ├── IngestionService.java        # validation, sanitization, per-source snapshot intake
│   ├── ReconciliationService.java   # add/update/no-op/hide + grace purge job
│   ├── EnrichmentService.java       # scheduled AI enrichment (structured facts + prose)
│   ├── ArtworkService.java          # hybrid sourcing + upload validation/storage
│   ├── ChatService.java             # two-call structured grounding
│   ├── GameQueryService.java        # paginated grid/detail queries
│   └── ScanSourceService.java       # source listing, enable/disable, config check-in
└── util/
    └── TextSanitizer.java           # @UtilityClass — markup/control-char stripping

jordylab-be/src/main/resources/db/migration/
└── V20260802__gamecatalog_create_tables.sql   # CREATE SCHEMA gamecatalog + 3 tables

jordylab-fe/
├── libs/gamecatalog/
│   ├── api/                          # tags: scope:gamecatalog,type:api
│   │   └── src/lib/
│   │       ├── gamecatalog.models.ts
│   │       └── gamecatalog-api.service.ts
│   └── ui/                           # tags: scope:gamecatalog,type:ui
│       └── src/lib/
│           ├── game-grid/            # container + -view (route /games)
│           ├── game-detail/          # container + -view (route /games/:id)
│           ├── game-chat/            # container + -view (route /games/chat)
│           └── source-manager/       # container + -view (route /games/sources)
└── apps/fna/src/app/                 # routes + nav extended (existing shell)

gamecatalog-sync-service/             # NEW top-level Python project (sibling of garmin-sync-service)
├── src/gamecatalog_sync/
│   ├── config.py                     # pydantic settings: config.yaml + env token
│   ├── state.py                      # per-source sequence persistence (JSON state file)
│   ├── steam_scanner.py              # libraryfolders.vdf + appmanifest_*.acf parsing
│   ├── rom_scanner.py                # walk, extension filter, .m3u/disc grouping, media detect
│   ├── normalize.py                  # title normalization (region/revision tag stripping)
│   ├── server_client.py              # httpx: GET config, POST sync, POST artwork
│   ├── sync.py                       # orchestration: config pull → scan → push → artwork
│   └── main.py                       # CLI entry: python -m gamecatalog_sync
├── tests/                            # pytest + respx + tmp_path fixtures
└── requirements.txt
```

**Structure Decision**: Three-component feature following existing repo conventions. Backend module follows the fixed `jordylab-be/AGENTS.md` package layout exactly (no facade — module root holds only `GameCatalogProperties`; no cross-module consumers exist, matching the `fna` precedent). Frontend follows the two-lib `ui`+`api` Nx pattern and reuses the `apps/fna` shell (it is the platform's single app). The agent is a new top-level Python service adopting `garmin-sync-service` conventions, with one deliberate deviation: it pushes via HTTPS to the server instead of writing to Postgres directly (the spec's server-side-validation constraint forbids direct DB writes).

## Complexity Tracking

No constitution violations to justify.
