---

description: "Task list for Game Catalog implementation"

---

# Tasks: Game Catalog

**Input**: Design documents from `/specs/002-game-catalog/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/](./contracts/), [quickstart.md](./quickstart.md)

**Tests**: INCLUDED — the JordyLab constitution mandates testing discipline (entity tests are definition-of-done per `jordylab-be/AGENTS.md`; 80% coverage gates on both repos; Vitest + Spectator on the frontend; pytest on the agent). Write tests before or alongside each implementation task as noted.

**Organization**: Tasks are grouped by user story (US1–US5 from spec.md) to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1–US5)
- Paths are repo-relative; backend package root is `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog` (test root mirrors under `jordylab-be/src/test/...`)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Scaffold the three components (backend module is created in Phase 2 — no generator exists for it)

- [x] T001 Create `gamecatalog-sync-service/` project skeleton: `src/gamecatalog_sync/`, `tests/`, `tests/fixtures/`, `requirements.txt` (`httpx`, `pydantic`, `vdf`; dev: `pytest`, `pytest-mock`, `pytest-cov`, `respx`, `ruff`), `.python-version` (3.12), `pyproject.toml` with ruff config per `garmin-sync-service/AGENTS.md`
- [x] T002 Generate frontend api lib: `cd jordylab-fe && bunx nx g @nx/angular:lib --name=gamecatalog-api --directory=libs/gamecatalog/api --tags="scope:gamecatalog,type:api" --unitTestRunner=vitest-analog`
- [x] T003 Generate frontend ui lib: `cd jordylab-fe && bunx nx g @nx/angular:lib --name=gamecatalog-ui --directory=libs/gamecatalog/ui --tags="scope:gamecatalog,type:ui" --unitTestRunner=vitest-analog`
- [x] T004 Wire frontend workspace for the new domain: add path aliases `@jordylab-fe/gamecatalog/api` and `@jordylab-fe/gamecatalog/ui` in `jordylab-fe/tsconfig.base.json`; add `{ sourceTag: 'scope:gamecatalog', onlyDependOnLibsWithTags: ['scope:gamecatalog', 'scope:shared'] }` to `jordylab-fe/eslint.config.mjs` depConstraints; align both libs' `vite.config.mts` + `src/test-setup.ts` with the `libs/fna/*` pattern (spectator inline, 80% line threshold)
- [x] T005 [P] Add spartan/ui helm components used by the ui lib: `cd jordylab-fe && bunx @spartan-ng/cli@latest add card && bunx @spartan-ng/cli@latest add badge && bunx @spartan-ng/cli@latest add input && bunx @spartan-ng/cli@latest add skeleton`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Schema, domain model, auth, and config that MUST be complete before ANY user story

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [x] T006 Create Flyway migration `jordylab-be/src/main/resources/db/migration/V20260802__gamecatalog_create_tables.sql` per data-model.md: `CREATE SCHEMA IF NOT EXISTS gamecatalog; SET search_path TO gamecatalog;` then `scan_source`, `game`, `sync_report` tables with all constraints, the `(source_id, external_ref)` unique index, and query indexes (`(presence, platform)`, `lower(title)`, partial `(enrichment_status)`, `(presence, uninstalled_at)`)
- [x] T007 [P] Create enums in `jordylab-be/.../gamecatalog/domain/`: `Presence.java`, `EnrichmentStatus.java`, `ArtworkStatus.java`, `SourceType.java`, `SyncOutcome.java` (values per data-model.md)
- [x] T008 [P] Create `ScanSource` entity + `ScanSourceTest` (canonical builder, EqualsVerifier, named mutations `announce`/`setEnabled`/`recordAttempt`/`recordApplied`) in `jordylab-be/.../gamecatalog/domain/ScanSource.java` — `@Table(schema = "gamecatalog", name = "scan_source")`
- [x] T009 [P] Create `Game` entity + `GameTest` (canonical builder, EqualsVerifier, named mutations `seenAgain`/`markUninstalled`/`applyEnrichment`/`recordEnrichmentFailure`/`applyArtwork`) in `jordylab-be/.../gamecatalog/domain/Game.java` — `@Table(schema = "gamecatalog", name = "game")`
- [x] T010 [P] Create `SyncReport` entity + `SyncReportTest` (immutable after build) in `jordylab-be/.../gamecatalog/domain/SyncReport.java` — `@Table(schema = "gamecatalog", name = "sync_report")`
- [x] T011 [P] Create repositories in `jordylab-be/.../gamecatalog/domain/repository/`: `ScanSourceRepository` (`findBySourceKey`), `GameRepository` (visibility-rule queries: `presence = INSTALLED` and source enabled; `findBySourceIdAndExternalRef`; `findByEnrichmentStatus` with limit; purge query `presence = UNINSTALLED and uninstalledAt < cutoff`), `SyncReportRepository`
- [x] T012 [P] Create `GameCatalogProperties` `@ConfigurationProperties(prefix = "jordylab.gamecatalog")` record at module root `jordylab-be/.../gamecatalog/GameCatalogProperties.java` + `@EnableConfigurationProperties` config class + property binding test (keys per data-model.md: ingest.token, ingest.max-games-per-source, artwork.dir, artwork.max-bytes, artwork.external-lookup-enabled, grace-period-days, enrichment.batch-size, enrichment.max-attempts, chat.max-result-games)
- [x] T013 Create `IngestAuthFilter` (`OncePerRequestFilter` scoped to `/api/gamecatalog/ingest/**`, constant-time `MessageDigest.isEqual` bearer comparison, 401 invalid / 503 unconfigured-token / pass-through otherwise) in `jordylab-be/.../gamecatalog/rest/IngestAuthFilter.java` + `IngestAuthFilterTest` (Mockito, no `any()`)
- [x] T014 [P] Create `TextSanitizer` `@UtilityClass` in `jordylab-be/.../gamecatalog/util/TextSanitizer.java` (strip control chars + markup from titles) + `TextSanitizerTest`
- [x] T015 Add gamecatalog AI routing entry to `jordylab-be/src/main/resources/application.yaml` under `jordylab.ai.modules.gamecatalog` (`provider: anthropic`, model matching the fna entry) and add `jordylab.gamecatalog.*` keys with env-backed token `${GAMECATALOG_INGEST_TOKEN:}`

**Checkpoint**: `./gradlew build` green (migration applies, entities map, filter + properties bind) — user story implementation can now begin

---

## Phase 3: User Story 1 - Automatic catalog synchronization from JordyBox (Priority: P1) 🎯 MVP

**Goal**: JordyBox agent pushes per-source snapshots on a schedule (outbound-only); server authenticates, validates/sanitizes per entry, reconciles add/update/hide with 30-day grace + purge, records sync metadata, and never reconciles on scan failure or out-of-order/duplicate submissions

**Independent Test**: Seed a fake Steam library + ROM folder on the dev machine, run the agent against the local backend, verify games appear via `/api/gamecatalog/games`; remove a ROM, re-run, verify hidden-then-grace-then-purge behavior; send malformed/unauthenticated/out-of-order/scan-failed submissions and verify contract outcomes (spec US1 scenarios 1–7, contracts/ingest-api.md)

### Tests for User Story 1 ⚠️ (write first, watch fail)

- [x] T016 [P] [US1] Write `IngestionServiceTest` in `jordylab-be/src/test/.../gamecatalog/service/IngestionServiceTest.java`: source announce/upsert, per-entry rejection matrix (`TITLE_BLANK`, `TITLE_TOO_LONG`, `REF_BLANK`, `REF_TOO_LONG`, `PLATFORM_MISMATCH`, ...), title sanitization applied, entry cap enforced, `sourceEnabled` echo (Mockito, explicit values, AssertJ `assertSoftly`)
- [x] T017 [P] [US1] Write `ReconciliationServiceTest` in `jordylab-be/src/test/.../gamecatalog/service/ReconciliationServiceTest.java`: outcome decision tree (`APPLIED`/`NO_CHANGE`/`OUT_OF_ORDER`/`SCAN_FAILED`/`REJECTED`), add/update/hide counts, grace restore via `seenAgain` (data retained), purge query cutoff, `SCAN_FAILED` performs zero writes
- [x] T018 [P] [US1] Write `IngestControllerTest` (`@WebMvcTest` + `@MockitoBean`, `@Language("JSON")`) in `jordylab-be/src/test/.../gamecatalog/rest/controller/IngestControllerTest.java`: contract shapes for sync/config endpoints, 400 on unparseable, outcome passthrough
- [x] T019 [P] [US1] Write agent tests in `gamecatalog-sync-service/tests/`: `test_steam_scanner.py` (fixture VDF library: libraryfolders + appmanifest parsing, installed-only), `test_rom_scanner.py` (tmp_path trees: extension filter, `.m3u` grouping, disc-pattern grouping, downloaded_media detection, `UNMOUNTED` vs empty-dir), `test_normalize.py` (region/revision tag matrix), `test_state.py` (sequence increments, corrupt-state recovery)
- [x] T020 [P] [US1] Write agent `test_server_client.py` + `test_sync.py` in `gamecatalog-sync-service/tests/` using `respx`: 401 propagation, config check-in merge (disabled source skipped), unchanged-hash skip, artwork request handling deferred (US2), state persisted after success

### Implementation for User Story 1

- [x] T021 [US1] Implement `IngestionService` in `jordylab-be/.../gamecatalog/service/IngestionService.java`: bean-validation + manual per-entry checks per contracts/ingest-api.md, `TextSanitizer` on titles, source announce (create-or-update `ScanSource`), delegation to `ReconciliationService`, response assembly with per-entry `rejections[]`
- [x] T022 [US1] Implement `ReconciliationService` in `jordylab-be/.../gamecatalog/service/ReconciliationService.java`: outcome decision tree (sequence/hash per R8), upsert-by-`(source, externalRef)`, `lastSeenAt`/`seenAgain` restore, `markUninstalled` for missing, `SyncReport` persistence with counts, source `recordAttempt`/`recordApplied`
- [x] T023 [US1] Implement purge job in `ReconciliationService` (`@Scheduled` daily): delete `UNINSTALLED` games older than `grace-period-days` (30), log counts (fail-fast on DB error, next tick retries)
- [x] T024 [US1] Create request/response records in `jordylab-be/.../gamecatalog/rest/controller/model/`: `SyncRequest`, `SyncSourcePayload`, `GamePayload`, `SyncResponse`, `SyncCounts`, `EntryRejection`, `IngestConfigResponse` (bean-validation annotations per contract)
- [x] T025 [US1] Implement `IngestController` in `jordylab-be/.../gamecatalog/rest/controller/IngestController.java`: `POST /api/gamecatalog/ingest/sync`, `GET /api/gamecatalog/ingest/config` (returns enabled map; unknown keys treated enabled per contract); no `@CrossOrigin` (agent-only, no browser caller)
- [x] T026 [P] [US1] Implement agent `config.py` + `state.py` in `gamecatalog-sync-service/src/gamecatalog_sync/`: pydantic config model (unique keys, known types, absolute paths, token from `GAMECATALOG_INGEST_TOKEN` env — fail-fast named errors), JSON state file with per-source sequence + last payload hash
- [x] T027 [P] [US1] Implement agent `normalize.py` + `steam_scanner.py` in `gamecatalog-sync-service/src/gamecatalog_sync/`: tag-stripping normalization (contracts/agent-config.md rules), VDF-based Steam scan (`libraryfolders.vdf` → `appmanifest_*.acf`, installed-only, appid as externalRef)
- [x] T028 [P] [US1] Implement agent `rom_scanner.py` in `gamecatalog-sync-service/src/gamecatalog_sync/rom_scanner.py`: recursive whitelist scan, `.m3u` + disc-pattern grouping, title normalization, `localArtworkAvailable` detection, scan-failure classification (`UNMOUNTED`/`PATH_MISSING`/`PERMISSION_DENIED`/`PARSE_ERROR`)
- [x] T029 [US1] Implement agent `server_client.py` (httpx, bearer header from env, timeouts) + `sync.py` (run algorithm per contracts/agent-config.md: config pull → scan → hash-skip → submit → persist state) + `main.py` CLI entry in `gamecatalog-sync-service/src/gamecatalog_sync/`
- [x] T030 [US1] Write `GameCatalogModuleTest` (`@ApplicationModuleTest`) in `jordylab-be/src/test/.../gamecatalog/`: module slice boots, migration applied to `gamecatalog` schema, full sync round-trip against Testcontainers Postgres (submit → apply → resubmit duplicate → `NO_CHANGE`)

**Checkpoint**: US1 fully functional — fake-library E2E (quickstart §4 steps 1–4, 7–9) passes; agent tests + backend tests green

---

## Phase 4: User Story 2 - Browse the catalog as a card grid (Priority: P2)

**Goal**: Web app shows installed games as a paginated card grid with per-platform badges and thumbnails (hybrid artwork: external URL → agent-upload fallback → placeholder), title search, and platform filter

**Independent Test**: With a synced catalog, open `/games`: cards render title/badge/thumbnail (or placeholder); search narrows by title; platform chips filter; empty catalog shows explicit empty state (spec US2 scenarios 1–5, contracts/catalog-api.md)

### Tests for User Story 2 ⚠️ (write first, watch fail)

- [x] T031 [P] [US2] Write `GameQueryServiceTest` + `ArtworkServiceTest` in `jordylab-be/src/test/.../gamecatalog/service/`: visibility rule (INSTALLED ∧ enabled source), search/pagination/sort, external-artwork resolution (Steam CDN URL, libretro probe hit/miss), `LOCAL_FALLBACK_REQUESTED` → upload → `LOCAL_UPLOAD`, placeholder fallback, upload validation (magic bytes, size cap)
- [x] T032 [P] [US2] Write `GameCatalogControllerTest` (`@WebMvcTest` + `@MockitoBean`) in `jordylab-be/src/test/.../gamecatalog/rest/controller/GameCatalogControllerTest.java`: games page shape, platforms list, artwork endpoint (200 bytes + nosniff / 404)
- [x] T033 [P] [US2] Write frontend specs in `jordylab-fe/libs/gamecatalog/api/src/lib/gamecatalog-api.service.spec.ts` (Spectator `expectOne` for games/platforms/artwork URL) and `jordylab-fe/libs/gamecatalog/ui/src/lib/game-grid/game-grid.component.spec.ts` (loading/error/populated, search + filter events, placeholder rendering — hand-written api mock pattern from `libs/fna/ui`)

### Implementation for User Story 2

- [x] T034 [US2] Implement `GameQueryService` in `jordylab-be/.../gamecatalog/service/GameQueryService.java`: paginated visible-games query (`search`, `platform`, sort by `lower(title)`), distinct-platforms query, grid/detail DTO mapping with `artworkUrl`/`artworkEndpoint` selection
- [x] T035 [US2] Implement `ArtworkLookupClient` in `jordylab-be/.../gamecatalog/rest/client/ArtworkLookupClient.java` (RestClient, bounded timeout): Steam CDN URL construction, libretro-thumbnails existence probe with platform→repo map + libretro filename escaping (contracts/agent-config.md); WireMock-based tests
- [x] T036 [US2] Implement `ArtworkService` in `jordylab-be/.../gamecatalog/service/ArtworkService.java`: resolve-on-discovery pipeline (`PENDING`→`EXTERNAL_URL`/`LOCAL_FALLBACK_REQUESTED`→`LOCAL_UPLOAD`/`PLACEHOLDER`), upload validation (magic-byte sniff JPEG/PNG, ≤ 2 MB), filesystem storage under `artwork.dir`, `artworkRequested` list for sync responses (wire into `IngestionService` response), stale-request aging to `PLACEHOLDER`
- [x] T037 [US2] Implement `GameCatalogController` in `jordylab-be/.../gamecatalog/rest/controller/GameCatalogController.java` + model records: `GET /api/gamecatalog/games` (paginated), `GET /api/gamecatalog/platforms`, `GET /api/gamecatalog/games/{id}/artwork` (bytes + `X-Content-Type-Options: nosniff` + cache headers); `@CrossOrigin(origins = "http://localhost:4200")` per fna precedent; agent `POST /api/gamecatalog/ingest/artwork/{externalRef}` added to `IngestController` (T025 file)
- [x] T038 [US2] Implement agent artwork upload in `gamecatalog-sync-service/src/gamecatalog_sync/`: locate `downloaded_media/boxart/<stem>.(png|jpg)`, `POST /artwork/{externalRef}?sourceKey=…` after each sync for requested refs, `localArtworkAvailable` accuracy in payloads; extend `test_server_client.py`/`test_rom_scanner.py`
- [x] T039 [US2] Implement frontend api lib in `jordylab-fe/libs/gamecatalog/api/src/lib/`: `gamecatalog.models.ts` (`GameSummary`, `GamesPage`, `GameDetail`, `ScanSource`, `ChatAnswer`, ...), `gamecatalog-api.service.ts` (`getGames`, `getPlatforms`, `artworkUrl` helper), barrel `index.ts` exports
- [x] T040 [US2] Implement grid in `jordylab-fe/libs/gamecatalog/ui/src/lib/game-grid/`: `game-grid.component.ts` (container: signals state, search input debounce, platform chip filter, pagination) + `game-grid-view.component.ts` (presentation: hlm card grid, platform badge, thumbnail with placeholder fallback, skeletons while loading) + export container from lib `index.ts`
- [x] T041 [US2] Register `/games` route (`loadComponent` from `@jordylab-fe/gamecatalog/ui`) and "Games" nav link in `jordylab-fe/apps/fna/src/app/app.routes.ts` + `app.html`; update `apps/fna/src/app/app.spec.ts` nav-link count

**Checkpoint**: US1+US2 both work — grid live against the dev backend with real synced data; search/filter/pagination verified (quickstart §3–4)

---

## Phase 5: User Story 3 - Game detail with AI-generated description (Priority: P3)

**Goal**: Each game gets AI enrichment (structured multiplayer facts + prose) generated after discovery, persisted, retried on failure with an explicit unavailable state; the detail view renders it

**Independent Test**: With a synced catalog, run enrichment (scheduled batch or triggered): detail view shows genre, local co-op player count / online / single-player, and prose; AI outage → explicit "description unavailable" state, grid unaffected (spec US3 scenarios 1–4)

### Tests for User Story 3 ⚠️ (write first, watch fail)

- [x] T042 [P] [US3] Write `EnrichmentServiceTest` in `jordylab-be/src/test/.../gamecatalog/service/EnrichmentServiceTest.java`: strict-JSON extraction (valid → fields persisted + `ENRICHED`), malformed AI output → attempt recorded, attempts → `FAILED` at max, daily `FAILED`→`PENDING` reset, AI failure (`AiCallResult.success == false`) never fabricates (Mockito `ResilientAiService` with explicit prompt captor)
- [x] T043 [P] [US3] Write frontend `game-detail.component.spec.ts` in `jordylab-fe/libs/gamecatalog/ui/src/lib/game-detail/`: enriched rendering (facts + prose + artwork), `PENDING`/`FAILED` → explicit unavailable state, 404 path

### Implementation for User Story 3

- [x] T044 [US3] Implement `EnrichmentService` in `jordylab-be/.../gamecatalog/service/EnrichmentService.java`: `@Scheduled` batch (every 15 min, `enrichment.batch-size` = 50 `PENDING` games), system prompt demanding strict JSON `{genre, maxLocalPlayers, onlineMultiplayer, singlePlayer, description}`, parse + validate + bounds-check, `applyEnrichment` / `recordEnrichmentFailure`, daily reset job for `FAILED`
- [x] T045 [US3] Add `GET /api/gamecatalog/games/{id}` detail endpoint to `GameCatalogController` (T037 file) + `GameDetailResponse` record: enrichment fields nullable per contract, 404 when not visible
- [x] T046 [US3] Implement detail view in `jordylab-fe/libs/gamecatalog/ui/src/lib/game-detail/`: `game-detail.component.ts` (container, route param) + `game-detail-view.component.ts` (platform badge, artwork, multiplayer facts block, prose, explicit unavailable state); `getGame(id)` in api service (T039 file); `/games/:id` route + grid card links

**Checkpoint**: US1–US3 work — enrichment round-trip visible in detail view; AI-outage path verified (quickstart §4 step 5)

---

## Phase 6: User Story 4 - Natural-language questions across the catalog (Priority: P4)

**Goal**: Chat answers questions strictly from catalog data via the two-call structured-grounding pattern, citing real DB rows; explicit unavailable state on AI outage; explicit no-match instead of general knowledge

**Independent Test**: Ask "which games support 4+ player local co-op?" — answer names only catalog games and citations resolve to detail views; ask about an uninstalled game → explicit no-match; AI down → unavailable state (spec US4 scenarios 1–4, contracts/catalog-api.md)

### Tests for User Story 4 ⚠️ (write first, watch fail)

- [x] T047 [P] [US4] Write `ChatServiceTest` in `jordylab-be/src/test/.../gamecatalog/service/ChatServiceTest.java`: translation JSON validation (allow-listed fields, platform allow-list, bounds; invalid → `CHAT_UNAVAILABLE`), grounded query execution, citations = DB rows only, zero-row → `noMatch`, AI failure on either call → named failure (never general knowledge)
- [x] T048 [P] [US4] Write frontend `game-chat.component.spec.ts` in `jordylab-fe/libs/gamecatalog/ui/src/lib/game-chat/`: message list rendering, citation links navigate, unavailable state, input validation

### Implementation for User Story 4

- [x] T049 [US4] Implement `ChatService` in `jordylab-be/.../gamecatalog/service/ChatService.java`: call 1 (question → strict JSON filter, validated against allow-list), parameterized query execution (cap `chat.max-result-games`), call 2 (compose answer from rows), response assembly per contract
- [x] T050 [US4] Add `POST /api/gamecatalog/chat` to `GameCatalogController` (T037 file) + `ChatRequest`/`ChatResponse` records: 400 invalid question, 503 `CHAT_UNAVAILABLE`
- [x] T051 [US4] Implement chat view in `jordylab-fe/libs/gamecatalog/ui/src/lib/game-chat/`: `game-chat.component.ts` (container: ephemeral session message list as signals) + `game-chat-view.component.ts` (messages, citation chips linking to `/games/:id`, input + submit, unavailable/empty states); `chat(question)` in api service (T039 file); `/games/chat` route

**Checkpoint**: US1–US4 work — grounded chat verified end-to-end (quickstart §4 step 6)

---

## Phase 7: User Story 5 - View and manage scan sources (Priority: P5)

**Goal**: Web app lists announced sources (path, type, platform, enabled, last sync outcome) and toggles enabled state — hiding/restoring that source's games immediately without purge

**Independent Test**: Sources view shows correct metadata; disable a source → its games vanish from grid/detail/chat (rows retained); re-enable → restored after next sync; agent skips disabled sources after check-in (spec US5 scenarios 1–4)

### Tests for User Story 5 ⚠️ (write first, watch fail)

- [x] T052 [P] [US5] Write `ScanSourceServiceTest` + `ScanSourceControllerTest` (`@WebMvcTest`) in `jordylab-be/src/test/.../gamecatalog/`: list shape with `installedGameCount`, toggle behavior, 404 unknown id, disabled-source invisibility across game queries (integration with `GameQueryService`)
- [x] T053 [P] [US5] Write frontend `source-manager.component.spec.ts` in `jordylab-fe/libs/gamecatalog/ui/src/lib/source-manager/`: list rendering (path/type/enabled/last outcome), toggle event, error state

### Implementation for User Story 5

- [x] T054 [US5] Implement `ScanSourceService` + `ScanSourceController` in `jordylab-be/.../gamecatalog/service/ScanSourceService.java` + `.../rest/controller/ScanSourceController.java` + model records: `GET /api/gamecatalog/sources` (with per-source visible game count), `PUT /api/gamecatalog/sources/{id}/enabled`; `@CrossOrigin` per fna precedent
- [x] T055 [US5] Implement sources view in `jordylab-fe/libs/gamecatalog/ui/src/lib/source-manager/`: `source-manager.component.ts` (container) + `source-manager-view.component.ts` (table/cards with enable toggle, last-sync badge by outcome); `getSources()`/`setSourceEnabled(id, enabled)` in api service (T039 file); `/games/sources` route

**Checkpoint**: All five stories independently functional (quickstart §4 step 10)

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Repo-wide consistency and validation

- [x] T056 Run `./gradlew :test --tests "*ModularityTests*"` in `jordylab-be` and fix any module-boundary violations (gamecatalog must not leak internals)
- [x] T057 Verify coverage gates: `./gradlew build` (JaCoCo ≥ 80%) and `cd jordylab-fe && bunx nx run-many -t test` (per-lib ≥ 80%) and `cd gamecatalog-sync-service && python -m pytest --cov=src` — add tests where gaps appear
- [x] T058 [P] Update root `AGENTS.md`: repository structure gains `gamecatalog-sync-service/`, modules table marks `gamecatalog` as built (ingestion + catalog + chat), note the agent's HTTPS-push deviation from the garmin direct-DB model; create `gamecatalog-sync-service/AGENTS.md` from the garmin conventions template adapted to this service (httpx push, config/state files, scan rules)
- [x] T059 [P] Add `gamecatalog-sync-service/README.md`: setup, config.yaml example, token provisioning, cron/systemd-timer scheduling on JordyBox
- [x] T060 [P] Add `jordylab-be/compose.yaml`/ops wiring notes if needed: `GAMECATALOG_INGEST_TOKEN` env and artwork volume mount documented in quickstart §5 (compose change only if the local dev stack needs the volume)
- [x] T061 Execute the full quickstart.md validation (§1–§4 scripted E2E + trust checks) and record results

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately. T002→T003→T004 sequential (same workspace files); T001 and T005 parallel with those.
- **Foundational (Phase 2)**: Depends on nothing (backend-only); T006 before T008–T011 (schema first), T007 parallel with all; **BLOCKS all user stories**.
- **User Stories (Phases 3–7)**: All depend on Phase 2. US1 is the MVP and unblocks real data; US2–US5 can proceed in parallel after US1 (US2's grid is the frontend entry point others attach routes to — sequence US2 before US3/US4/US5 frontend work, or stub routes).
- **Polish (Phase 8)**: After desired stories complete.

### User Story Dependencies

- **US1 (P1)**: After Foundational — no story dependencies. Backend (T021–T025, T030) and agent (T026–T029) are parallelizable sub-streams meeting at the E2E checkpoint.
- **US2 (P2)**: After Foundational; integrates with US1's ingestion (artwork requests in sync responses) — testable with seeded data alone.
- **US3 (P3)**: After Foundational; needs T034/T037 if built before US2 (query service/controller) — otherwise only enrichment + detail route.
- **US4 (P4)**: After Foundational; same query-service dependency note as US3.
- **US5 (P5)**: After Foundational; toggling uses US1's config check-in (already built).

### Within Each User Story

- Tests (T0xx test tasks) written first and seen failing before implementation tasks
- Entities/repositories (Phase 2) before services; services before controllers; backend before frontend wiring; containers before routes

### Parallel Opportunities

- Phase 2: T007–T012, T014 all parallel (distinct files)
- US1: backend stream (T016–T018, T021–T025, T030) ∥ agent stream (T019–T020, T026–T029)
- US2: T031–T033 parallel; T034–T036 parallel; frontend (T039–T041) ∥ agent artwork (T038) after backend endpoints exist
- US3/US4/US5 test tasks per story parallel; the three stories are parallelizable after US2 lands the frontend shell routes

---

## Parallel Example: User Story 1

```bash
# Launch backend and agent test streams together:
Task: "Write IngestionServiceTest + ReconciliationServiceTest + IngestControllerTest (backend)"
Task: "Write agent scanner/state/client tests with respx (agent)"

# Then implementation streams in parallel:
Task: "IngestionService + ReconciliationService + IngestController (backend)"
Task: "config/state/scanners/server_client/sync/main (agent)"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1 Setup + Phase 2 Foundational
2. Phase 3 US1 → **STOP and VALIDATE**: fake-library E2E proves outbound-only sync, validation, reconciliation, grace/purge, and scan-failure safety
3. Deployable increment: trustworthy self-updating catalog dataset (queryable via curl)

### Incremental Delivery

1. US1 (sync) → validate → deploy
2. US2 (grid + artwork) → validate → deploy — first user-visible value
3. US3 (enrichment + detail) → validate → deploy
4. US4 (chat) → validate → deploy
5. US5 (sources management) → validate → deploy
6. Each story adds value without breaking previous stories

### Parallel Team Strategy

After Phase 2: one stream on US1-backend, one on US1-agent; after US1, US2 frontend/backend streams split; US3–US5 parallelize across backend (services share only the T037 controller file — sequence edits there) and frontend (distinct feature folders, shared api models file — sequence T039 edits).
