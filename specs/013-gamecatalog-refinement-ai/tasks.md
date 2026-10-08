# Tasks: Game Catalog Refinement and LibBot (AI) Rebuild

**Input**: Design documents from `/specs/013-gamecatalog-refinement-ai/` (plan.md, spec.md, research.md, data-model.md, contracts/, ui-design.md, ui-mockup.html, quickstart.md)

**Prerequisites**: plan.md, spec.md (15 user stories), research.md, data-model.md, contracts/

**Tests**: Included. The constitution makes entity tests, TestBuilders and `EqualsVerifier` part of the definition of done; the spec's success criteria need automated proof (golden set, contrast, overflow, role matrix, duplicates); the owner asked for local-first verification. Test tasks precede their implementation tasks where practical (red → green).

**Order of work (owner's rule)**: build and test everything locally first; release only when the **entire** spec works locally (Phase 19); then deploy and re-test on jordylab.be (Phases 20 and 21). No partial release.

**Organization**: Foundational phase first (the data model everything depends on), then one phase per user story in priority order (P1: US1 to US5, P2: US6 to US10, US13 to US15, P3: US11, US12), then polish, local acceptance, release and production acceptance. Repo skills are referenced where they apply (`/entity`, `/test-builder`, `/flyway-migration`, `/modularity-check`, `/angular-signal-store`, `/angular-test`).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: the user story the task serves (US1 to US15); Setup, Foundational, Polish and release phases carry no story label
- `[x]` = already done (the three verification spikes)
- Every commit carries `Refs: 013 T###` (root `AGENTS.md`)

## Path Conventions

Backend `jordylab-be/src/main/java/dev/jordy/jordylab/…` (tests `jordylab-be/src/test/…`, resources `jordylab-be/src/main/resources/…`); frontend `jordylab-fe/…` (`libs/gamecatalog/{api,ui}/src/lib`, `libs/shared/auth/src/lib`, `apps/jordylab/src/app`, `apps/jordylab-e2e/src`). Full layout: [plan.md](plan.md) → Project Structure. Never touch the `jordylab-be-*` dev containers except read-only (root `AGENTS.md`).

---

## Phase 1: Setup

**Purpose**: Baseline green, dependencies and configuration. The three verification spikes are already done and kept as regression tests.

- [x] T001 Run the baseline green before changing anything: `cd jordylab-be && ./gradlew test` (needs `DOCKER_HOST`/`TESTCONTAINERS_RYUK_DISABLED` for Podman, see `jordylab-be/AGENTS.md`), `cd jordylab-fe && bunx nx run-many -t test lint oxlint`; note any pre-existing failure in `specs/013-gamecatalog-refinement-ai/validation-results.md` (create it with a header) so it is not blamed on this feature
- [x] T002 Spike (done 2026-10-07): embeddings through the OpenRouter gateway are covered by `jordylab-be/src/test/java/dev/jordy/jordylab/shared/ai/AiEmbeddingGatewayWiringTest.java` (2 tests passing); keep it green
- [x] T003 Spike (done 2026-10-07): a router request exposes the model the provider actually used, covered by `aRouterRequestExposesTheModelTheProviderActuallyUsed` in `jordylab-be/src/test/java/dev/jordy/jordylab/shared/ai/AiGatewayWiringTest.java`; keep it green
- [x] T004 Spike (done 2026-10-07): IGDB platform ids and generations verified against live IGDB and recorded in `specs/013-gamecatalog-refinement-ai/research.md` (B4); the stored ids feed `PlatformCatalog` in the foundational phase
- [x] T005 [P] Add `implementation("com.github.ben-manes.caffeine:caffeine")` (version from the Boot BOM) to `jordylab-be/build.gradle.kts` for the conversation store
- [x] T006 Switch `spring.ai.model.embedding` from `none` to `openai` and add `spring.ai.openai.embedding.options.model: openai/text-embedding-3-small` and `dimensions: 1536` in `jordylab-be/src/main/resources/application.yaml`; keep `spring.ai.model.chat` unset and the `PgVectorStoreAutoConfiguration` exclusion; add `jordylab.ai.embedding.model` and a comment explaining the embedding model is configuration, not selectable (research A3)
- [x] T007 Add typed config records `autofill` (batch sizes, daily cron, `ai-max-attempts: 3`, `free-lookup-retry-hours: 24`) and `libbot` (`memory-exchanges: 10`, `memory-idle-hours: 2`, `max-references: 10`, `max-candidates: 15`, `max-unknown-titles: 5`, `max-message-length: 1000`) to `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/GameCatalogProperties.java`, remove `chat.maxResultGames`, add the keys to `jordylab-be/src/main/resources/application.yaml`, and extend `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/GameCatalogPropertiesTest.java`
- [x] T008 [P] Add the dry-run switch for the identity migration: a `jordylab.migration.dry-run` property read by the Flyway Java migration (created later) through `System.getProperty`/env, documented in `specs/013-gamecatalog-refinement-ai/quickstart.md` A2 (already described); no code yet beyond a constant class `jordylab-be/src/main/java/dev/jordy/jordylab/shared/config/MigrationSwitches.java`

---

## Phase 2: Foundational (blocking prerequisites)

**Purpose**: The data model (one game, many places), the platform catalog, hosts and consoles, and the extended AI path. Everything else builds on this. The backend must compile and its existing tests must be green again at the checkpoint, with the Switch-specific backend code removed (it is rebuilt as consoles in US6).

- [x] T009 [P] Create the pure normaliser `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/util/TitleKeys.java` (`@UtilityClass`, `normalise(String)`: lower-case, NFKD accents stripped, punctuation removed, leading "the" dropped, edition/region suffixes such as "(USA)", "[!]", "GOTY Edition" stripped, whitespace collapsed) with `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/util/TitleKeysTest.java` covering accents, punctuation, region tags, editions, "The " prefix, empty and null; no Spring dependency so the Flyway Java migration can use it
- [x] T010 [P] Create `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/domain/BrandFamily.java` (`PLAYSTATION, XBOX, NINTENDO, SEGA, STEAM, OTHER`), `ChipColors.java` (record: background, foreground, border), `PlatformEntry.java` (canonical name, aliases, family, generation, handheld, IGDB platform id and exact IGDB name, libretro repo, colours) and `PlatformCatalog.java` (`@UtilityClass`, `canonical(String)`, `entryFor(String)` returning the neutral `OTHER` entry for unknown names, `knownConsoles()` generation 5 to 9, `all()`), using the verified ids and colours in `specs/013-gamecatalog-refinement-ai/research.md` B4 and the colours in `specs/013-gamecatalog-refinement-ai/ui-design.md` §3.1, aliases taken from the maps in `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/service/scan/EmuDeckLibraryParser.java`, `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/rest/client/ArtworkLookupClient.java` and `IgdbClient.java`
- [x] T011 Write `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/domain/PlatformCatalogTest.java`: every alias resolves to one canonical name ("N64" → "Nintendo 64", "GBA" → "Game Boy Advance", "PSX" → "PlayStation"), unknown names return the neutral entry, `knownConsoles()` starts at generation 5 and includes PlayStation 1 to 5, Xbox to Series X|S, Nintendo 64 to Switch 2, handhelds, and **a WCAG 2 contrast test that computes the ratio for every entry and fails below 4.5:1 (text)** (FR-040, SC-011)
- [x] T012 [P] Create enums `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/domain/RomStatus.java` (`UNKNOWN, VALIDATED, BROKEN`) and `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/domain/MarkType.java` (`WANT_TO_PLAY, PLAYED_LIKED, PLAYED_DISLIKED`)
- [x] T013 [P] [Skill `/entity`] Create `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/domain/Host.java` (id, hostname unique ignoring case, nullable displayName; `label()`, `rename(String)` with trim, blank-clears, ≤ 40 chars; builder with `Preconditions` in `build()`; registers `HostRenamed`) and `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/domain/repository/HostRepository.java` (`findByHostnameIgnoreCase`, `existsByDisplayNameIgnoreCase`)
- [x] T014 [P] [Skill `/test-builder`] Write `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/domain/HostTest.java` (build ok/null/blank hostname, `label()` prefers the display name, `rename` trims, blank clears, 41 chars rejected, `EqualsVerifier` with the three repo suppressions) and `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/domain/HostTestBuilder.java` (`aDefaultHost`, `aHost`)
- [x] T015 [P] [Skill `/entity`] Create `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/domain/Console.java` (id, canonical-or-custom platform, name 1..40 unique ignoring case, defaults to the platform; `rename`, `isCustomPlatform()`) and `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/domain/repository/ConsoleRepository.java`; write `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/domain/ConsoleTest.java` + `ConsoleTestBuilder.java` with the same coverage and `EqualsVerifier`
- [x] T016 [P] [Skill `/entity`] Create `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/domain/ConsoleGameEntry.java` (game, console, unique pair) and `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/domain/repository/ConsoleGameEntryRepository.java` (`findAllByConsoleId`, `findAllByGameIdIn`, `existsByGameIdAndConsoleId`, `countByConsoleId`, `deleteAllByConsoleId`); write `ConsoleGameEntryTest.java` (with `EqualsVerifier`) + `ConsoleGameEntryTestBuilder.java` in `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/domain/`
- [x] T017 [P] [Skill `/entity`] Create `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/domain/GameMark.java` (UUID `id`, `game`, `userSubject`, `MarkType mark`, `changeTo(MarkType)`; one mark per user per game is a **unique index on `(game_id, user_subject)`**, not a composite key, because entity ids are always UUID and `BaseEntity.getId()` returns one) and `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/domain/repository/GameMarkRepository.java` (`findByGameIdAndUserSubject`, `countByGameIdGroupedByMark`, `deleteAllByUserSubject`, bulk vote counts for a list of game ids); tests `GameMarkTest.java` (with `EqualsVerifier`) + `GameMarkTestBuilder.java` in `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/domain/`
- [x] T018 [P] [Skill `/entity`] Create `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/domain/GameEmbedding.java` (game id, model, contentHash, `float[]`/pgvector embedding, embeddedAt; `isStale(model, hash)`) and `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/domain/repository/GameEmbeddingRepository.java` (native `upsert`, `deleteByGameId`, `findStaleOrMissingGameIds(model)`); test `GameEmbeddingTest.java` (with `EqualsVerifier`) + `GameEmbeddingTestBuilder.java`
- [x] T019 [P] [Skill `/entity`] Create `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/domain/RefreshRunKind.java`, `RefreshRunStatus.java`, `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/domain/RefreshRun.java` (`advance(boolean)`, `requestStop()`, `finish(status)`, `failureSummary`) and `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/domain/repository/RefreshRunRepository.java` (`findFirstByKindOrderByStartedAtDesc`, `markRunningAsInterrupted()`); tests `RefreshRunTest.java` (with `EqualsVerifier`) + `RefreshRunTestBuilder.java`
- [x] T020 Modify `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/domain/GameInstallation.java`: remove `manual`, `format`, `createManual`, `InstallationFormat` (delete `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/domain/InstallationFormat.java`) and the manual precondition; add required `platform` and nullable `RomStatus romStatus`; add `changeRomStatus(RomStatus, SourceType)` that fails a `Preconditions` check unless the source type is `EMUDECK` (FR-051); new copies on emulation sources start `UNKNOWN`; update `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/domain/GameInstallationTest.java` and `GameInstallationTestBuilder.java` (including the not-applicable case and `EqualsVerifier`)
- [x] T021 Modify `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/domain/ScanSource.java` and `SourceType.java`: replace the `hostname` column with a required `ManyToOne Host host`, keep `sourceKey` as `hostname:TYPE`, remove `SourceType.SWITCH`, keep `announce(...)` working through the host; update `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/domain/repository/ScanSourceRepository.java` (`findByHostIdAndSourceType`, `findAllByEnabledTrue...`) and `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/domain/ScanSourceTest.java` / `ScanSourceTestBuilder.java`
- [x] T022 Modify `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/domain/Game.java`: remove `platform`, add `titleKey` (computed with `TitleKeys` in `build()` and when the title changes), `factsCheckedAt`, `artworkCheckedAt`, and the description-provenance fields (`descriptionSource` enum `DescriptionSource{AI,STEAM}`, `descriptionModel`, `descriptionRequestedModel`, `descriptionWrittenAt`) with an `AiAuthorship` record (`jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/domain/AiAuthorship.java`); `updateCatalogInfo(title, source)` loses the platform parameter; `applyEnrichment(...)` takes an `AiAuthorship`; `applyDeterministicDescription(...)` records `STEAM`; update `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/domain/GameTest.java` and `GameTestBuilder.java`
- [x] T023 [Skill `/flyway-migration`] Create `jordylab-be/src/main/resources/db/migration/V20261008001__gamecatalog_places_hosts_consoles.sql` exactly as in `specs/013-gamecatalog-refinement-ai/data-model.md` (migration 1): `CREATE EXTENSION IF NOT EXISTS vector`, `host` (backfilled), `console`, `console_game_entry`, `scan_source.host_id`, `game_installation.platform` + `rom_status` backfills, move the Switch virtual source into a "Nintendo Switch" console, drop `manual`/`format`/constraint/index, add `game.title_key`, `facts_checked_at`, `artwork_checked_at`
- [x] T024 Create the Flyway **Java** migration `jordylab-be/src/main/java/db/migration/V20261008002__GameIdentityBackfillAndMerge.java` (package `db.migration`, Flyway's default classpath location `db/migration`; confirm `spring.flyway.locations` is not overridden in `jordylab-be/src/main/resources/application.yaml`, and that `ModularityTests` ignores the package) delegating to a plain class `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/util/GameIdentityBackfill.java` that: canonicalises platform names through `PlatformCatalog`, computes `title_key` with `TitleKeys`, merges duplicate games per research B3 (groups first by **equal `igdb_game_id`** regardless of title, because migration 3 makes it unique, then by `title_key`; survivor = enriched, then artwork, then oldest; never merge when known ids differ; repoint installations, library entries, console entries; keep the active row on `uq_game_library_entry_game_source` collisions and drop the duplicate on `console_game_entry (game_id, console_id)` collisions; copy facts the survivor lacks), logs every merge by title, and honours `MigrationSwitches` dry-run by rolling back
- [x] T025 Write `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/util/GameIdentityBackfillTest.java` (Testcontainers `pgvector/pgvector:pg16`): build the **old** schema by running the earlier migrations, insert test rows in test code only, run the backfill, and assert merge survivor choice, repointed children, `uq_game_library_entry_game_source` and `console_game_entry` collision handling, two games with the same IGDB id but different titles merged, different-id games left alone, and dry-run leaving the data untouched
- [x] T026 [Skill `/flyway-migration`] Create `jordylab-be/src/main/resources/db/migration/V20261008003__gamecatalog_identity_marks_runs.sql` (migration 3 in `specs/013-gamecatalog-refinement-ai/data-model.md`): `title_key` NOT NULL + index, drop `game.platform`, replace `uq_game_platform_igdb_game_id` with unique `uq_game_igdb_game_id`, add the description-provenance columns with the backfill (`ENRICHED` → `AI`/null model, other described games → `STEAM`), create `game_mark`, `refresh_run` (+ partial unique `RUNNING` per kind) and `game_embedding` (+ HNSW `vector_cosine_ops`)
- [ ] T027 Rehearse the migrations on a **copy** of the dev data (never on the dev stack): `podman exec` a read-only `pg_dump` from the running `jordylab-be-*` pgvector container into a file under `/private/tmp`, start a throwaway labelled `pgvector/pgvector:pg16` container (own exact name and `--label jordylab-013-rehearsal`), restore, run the backend against it with `--jordylab.migration.dry-run=true`, review the merge plan, then run for real on the copy and record counts before/after in `specs/013-gamecatalog-refinement-ai/validation-results.md`; remove only that container by its exact name (root `AGENTS.md`, Local containers)
- [x] T028 Define the visibility predicate once: create `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/domain/repository/GameVisibility.java` (JPQL fragment constants or a Spring Data `Specification`: installed copy on an enabled source OR active library entry OR console entry) and refactor every `@Query` in `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/domain/repository/GameRepository.java` (`findVisibleGames`, `findVisibleById`, `findVisiblePlatforms`, `findForChatFilter`, backlog queries) to use it; platforms now derive from places (installation.platform, console.platform, "Steam" for library entries); update `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/domain/repository/GameRepositoryTest.java` incl. console-only and disabled-source cases
- [x] T029 Create `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/service/GameIdentityService.java` implementing research B2 (external id match, differing known ids never merge, otherwise `title_key`, resolve-or-create under `pg_advisory_xact_lock(hashtext(titleKey))`) and `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/service/GameIdentityServiceTest.java` (Testcontainers: same title on two platforms is one game, different igdb ids stay separate, concurrent creation yields one row)
- [x] T030 Create `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/service/PlaceRemovalService.java` (`releaseGameIfOrphaned(gameId)` removing marks, embedding, library entries, local artwork file and the game only when no place and no library entry remain; one implementation for purge, console removal and console game delete) and `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/service/PlaceRemovalServiceTest.java`
- [x] T031 [P] Create `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/service/PlaceNameService.java` (host/console name uniqueness across both tables, case-insensitive, under an advisory lock; throws `NameTakenException`, `NameTooLongException` in `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/service/`) and `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/service/PlaceNameServiceTest.java`
- [x] T032 [P] Create `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/domain/GameSources.java` (`@UtilityClass`: derives `Steam (Owned) | Steam (Family) | Emulated | Console` per game from places; unresolved installed Steam copy counts as Steam (Owned)) and `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/domain/GameSourcesTest.java`
- [x] T033 Refactor the scan path to the new model: `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/service/ScanService.java` (`resolveSource` resolves-or-creates a `Host` first and never touches `displayName`; `ScanLock` key from the hostname), `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/service/ReconciliationService.java` (use `GameIdentityService`, set `installation.platform` from the payload through `PlatformCatalog.canonical`, delete the `isManual()` branches, purge through `PlaceRemovalService`), `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/service/scan/EmuDeckLibraryParser.java` (platform names through `PlatformCatalog`), and fix `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/service/ScanServiceTest.java`, `ReconciliationServiceTest.java`, `scan/EmuDeckLibraryParserTest.java`
- [x] T034 Refactor library sync and metadata to the new model: `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/service/SteamLibrarySyncService.java`, `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/service/SteamMetadataService.java`, `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/service/MultiplayerService.java`, `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/service/ArtworkService.java`, `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/util/ArtworkUrls.java` (stop reading `game.getPlatform()`; pass the place's platform where a lookup needs one) and fix `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/service/SteamLibrarySyncServiceTest.java`, `SteamMetadataServiceTest.java`, `MultiplayerServiceTest.java`, `ArtworkServiceTest.java`
- [x] T035 Delete the Switch-only backend (rebuilt as consoles in US6): `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/service/SwitchGameService.java`, `SwitchBulkService.java`, `SwitchGameAlreadyPresentException.java`, `SwitchGameNotFoundException.java`, `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/rest/controller/SwitchGameController.java`, `SwitchGameExceptionHandler.java`, the `Switch*` records under `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/rest/controller/model/`, and their tests `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/service/SwitchGameServiceTest.java`, `SwitchBulkServiceTest.java`, `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/rest/controller/SwitchGameControllerTest.java`, `SwitchGameControllerSecurityTest.java`; keep a note in `specs/013-gamecatalog-refinement-ai/validation-results.md` that the Switch pages are red until US6
- [x] T036 Refactor read models: `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/service/GameQueryService.java`, `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/service/ScanSourceService.java` and the records in `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/rest/controller/model/` (`GameSummaryResponse`, `GameDetailResponse`, `HostRef`, `ScanSourceResponse`) to compile against the new model with the **target field names from `specs/013-gamecatalog-refinement-ai/contracts/catalog-api.md`** (`platforms[]`, `sources[]`, `places[]`, `label`) even if values are filled in by later stories; fix `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/service/GameQueryServiceTest.java`, `ScanSourceServiceTest.java`, `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/rest/controller/GameCatalogControllerTest.java`, `ScanSourceControllerTest.java`
- [x] T037 Make the old chat compile for now: keep `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/service/ChatService.java` working against the new repository signatures (it is deleted in US1) and adapt `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/service/ChatServiceTest.java`
- [x] T038 Extend the single AI path in `jordylab-be/src/main/java/dev/jordy/jordylab/shared/ai/ResilientAiService.java`: add `call(AiFeature, List<Message>)` (the existing overload delegates), `embed(List<String>)` (gateway only, same event/metric, reason `NOT_CONFIGURED` when no gateway key), explicit optional temperature per feature, and `AiCallResult`/`AiCallCompleted` gain `answeredModel` (from `ChatResponse.getMetadata().getModel()`, falling back to the selected model), `inputTokens` and `outputTokens`; counters for tokens on `jordylab.ai.calls`; update `AiCallResult.java`, `AiCallCompleted.java` and their callers
- [x] T039 Create `jordylab-be/src/main/java/dev/jordy/jordylab/shared/ai/StructuredOutput.java` and the `callStructured(AiFeature, List<Message>, Class<T>)` method: append `BeanOutputConverter` schema instructions, parse, validate with Jakarta Validation, make one repair attempt quoting the validation error, otherwise fail with the new `ProviderFailureReason.INVALID_OUTPUT` (which triggers the existing fallback); never log model text
- [x] T040 Update `jordylab-be/src/main/java/dev/jordy/jordylab/shared/ai/AiFeature.java`: add `GAMECATALOG_EMBEDDING` with `selectable = false`, new display names/descriptions "LibBot: understanding the question" and "LibBot: writing the answer" keeping the keys `gamecatalog.chat.query` and `gamecatalog.chat.answer`; hide non-selectable features in `jordylab-be/src/main/java/dev/jordy/jordylab/settings/service/AiModelSettingsService.java` and `jordylab-be/src/main/java/dev/jordy/jordylab/settings/rest/controller/SettingsAiModelsController.java`; extend `jordylab-be/src/test/java/dev/jordy/jordylab/shared/ai/AiPropertiesTest.java`, `jordylab-be/src/test/java/dev/jordy/jordylab/settings/service/AiModelSettingsServiceTest.java`, `jordylab-be/src/test/java/dev/jordy/jordylab/settings/rest/controller/SettingsAiModelsControllerTest.java`
- [x] T041 Extend `jordylab-be/src/test/java/dev/jordy/jordylab/shared/ai/ResilientAiServiceTest.java` (and `AiCallResultTestBuilder.java`): message-list call, structured output success, repair success, repair failure → `INVALID_OUTPUT` → fallback, embedding success and `NOT_CONFIGURED`, `answeredModel` falls back to the selected model when the provider reports none, token counts recorded; keep `AiGatewayWiringTest` and `AiEmbeddingGatewayWiringTest` green
- [x] T042 Move the enrichment system prompt out of Java into `jordylab-be/src/main/resources/prompts/gamecatalog/enrichment.st` with the **identical text** (use `<>` delimiters if JSON is present, per `jordylab-be/AGENTS.md`), load it in `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/service/EnrichmentService.java`, and add a test in `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/service/EnrichmentServiceTest.java` asserting the loaded text equals the previous literal (gap analysis #6); the text itself is rewritten in US15
- [x] T043 Run `./gradlew compileJava compileTestJava test` for the `gamecatalog`, `shared` and `settings` packages and `./gradlew :test --tests '*ModularityTests'` (skill `/modularity-check`); fix every remaining compile error and boundary violation before leaving this phase

**Checkpoint**: `./gradlew test` for `gamecatalog`, `shared`, `settings` and `ModularityTests` green; the Switch frontend pages are knowingly broken until US6.

---

## Phase 3: User Story 1 - Ask LibBot and get a trustworthy answer (Priority: P1) 🎯 MVP

**Purpose**: LibBot's pipeline, validation and page: grounded answers, honest unknowns, no stray references, graceful errors.

**Goal**: A question gets a grounded answer, one clarifying question, or a polite decline; references are exactly the games named in the answer (≤ 10); unknown data is separated from confirmed matches; failures are plain and do not cost a guest message.

**Independent Test**: Run the golden replay tier and ask the cat, four-people, vague and installed-racing questions on the local app (quickstart A3 row 1).

- [x] T044 [P] [US1] Create the typed records `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/service/libbot/QuestionInterpretation.java` (intent, language, standaloneQuestion, facts{partySize, playingAlone, online, platforms, places, installStatus, genres, years, markFilter, markScope, likeMyLiked, semanticQuery, referencedGameIds}, reply) with Jakarta Validation constraints, `LibBotAnswer.java` (text, recommendedGameIds), `LibBotOutcome.java` (`ANSWERED, NO_MATCH, CLARIFY, OUT_OF_SCOPE, EMPTY_LIBRARY`), `Constraints.java` (derived hard requirements) and `Candidate.java` (game id, title, platforms, confirmed/unknown flag, votes, similarity) exactly as in `specs/013-gamecatalog-refinement-ai/contracts/libbot-api.md`
- [x] T045 [P] [US1] Write the golden-set fixtures `jordylab-be/src/test/resources/libbot/golden/golden-questions.json`: each case has question(s), conversation context, expected intent/outcome, expected derived constraints, max references, language; include the four owner-reported failures (cat question, "which games can i play with four people", "how many other games like this" without context, long reference list), US1 scenarios 1 to 7 and recorded model outputs for the replay tier under `jordylab-be/src/test/resources/libbot/golden/recorded/`
- [x] T046 [P] [US1] Write `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/service/libbot/LibBotAnswerValidatorTest.java` first (red): ids not in the candidate set dropped, more than 10 trimmed, titles not named in the text dropped, empty references for non-`ANSWERED` outcomes, case/accent-insensitive title match
- [x] T047 [US1] Create `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/service/libbot/LibBotAnswerValidator.java` making the test green (FR-003, SC-004)
- [x] T048 [P] [US1] Write `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/service/libbot/CandidateRetrieverTest.java` (Testcontainers, data created in test code): visibility reused (disabled source hides, console-only visible), confirmed vs unknown buckets for `minLocalPlayers`, the cap of 15 and 5 unknown titles, ordering confirmed first, lexical fallback when no embedding exists, semantic ordering with a fixed fake embedding
- [x] T049 [US1] Create `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/service/GameEmbeddingService.java` (builds the embedded document text from title, platforms, genres, developer, year, multiplayer facts in words and description; `contentHash`; embeds through `ResilientAiService.embed`; upserts into `game_embedding`; skips unchanged hashes) with `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/service/GameEmbeddingServiceTest.java` (WireMock-free: mock `ResilientAiService`, assert hash skip, model-change re-embed, failure leaves the row stale and logs without content)
- [x] T050 [US1] Create `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/service/libbot/CandidateRetriever.java` (native SQL via `JdbcClient`/`EntityManager`: visibility predicate + tri-state constraints + optional `embedding <=> :query` ordering + lexical `ILIKE` fallback; returns `RetrievalResult{confirmed, unknownCount, unknownTitles}`) satisfying the test; vote ranking, marks and ROM rules are added in US10 and US13
- [x] T051 [P] [US1] Write `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/service/libbot/ConstraintDeriverTest.java` for the base rules (party size → minLocalPlayers + localMultiplayer, alone → singlePlayer, online → onlineMultiplayer, unknown platform or place names dropped, impossible party size → `NO_MATCH` with the largest known group) and create `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/service/libbot/ConstraintDeriver.java`
- [x] T052 [P] [US1] Create the prompts as resources `jordylab-be/src/main/resources/prompts/gamecatalog/libbot-interpret.st` and `libbot-answer.st` (`<>` delimiters; catalog rows in a delimited data block; "answer only from these rows; say so when they do not contain the answer"; JSON output instructions come from the structured converter) and load them with `SystemPromptTemplate`
- [x] T053 [US1] Create `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/service/libbot/QuestionInterpreter.java` (calls `ResilientAiService.callStructured` with `GAMECATALOG_CHAT_QUERY`, temperature 0, passes visible platforms/places vocabulary, validates referenced ids against visible games) and `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/service/libbot/AnswerComposer.java` (`GAMECATALOG_CHAT_ANSWER`, candidate rows with votes placeholders, temperature 0.3); unit tests `QuestionInterpreterTest.java` and `AnswerComposerTest.java` with a mocked `ResilientAiService` (no `any()`; real expected values or captors that are assigned and asserted)
- [x] T054 [P] [US1] Create `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/service/libbot/LibBotMessages.java` (message templates from `jordylab-be/src/main/resources/libbot/messages_en.properties`: applied-constraint phrases, unknown-data note, decline with an example, "cannot answer right now", "please rephrase") with `LibBotMessagesTest.java`; Dutch file is added in US2
- [x] T055 [US1] Create `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/service/libbot/LibBotService.java` orchestrating interpret → derive → retrieve → compose with **no transaction**, short-circuiting `CLARIFY`/`OUT_OF_SCOPE`/`EMPTY_LIBRARY` after step 1, treating `attachedGameIds` (max 5, each must be visible, else `LibBotAttachmentException`) as `referencedGameIds`, emitting stage callbacks, mapping every failure to the typed `LibBotUnavailableException`, and publishing `LibBotMessageAnswered(userSubject, admin)` only after a successful answer; write `LibBotServiceTest.java` driven by the golden replay fixtures (a `GoldenReplayTest.java` in the same package runs every case against recorded model outputs and asserts outcome, constraints, reference rules)
- [x] T056 [US1] Create the public event `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/LibBotMessageAnswered.java` (record in the module root package, public API) and, in the settings module, `jordylab-be/src/main/java/dev/jordy/jordylab/settings/service/GuestChatUsageListener.java` (`@ApplicationModuleListener` incrementing `guest_chat_usage` for non-admins) with `jordylab-be/src/test/java/dev/jordy/jordylab/settings/service/GuestChatUsageListenerTest.java`; change `jordylab-be/src/main/java/dev/jordy/jordylab/settings/rest/controller/GuestChatLimitFilter.java` to **pre-check only** on `POST /api/gamecatalog/libbot/ask` (no post-response increment) and update `jordylab-be/src/test/java/dev/jordy/jordylab/settings/rest/controller/GuestChatLimitFilterTest.java` and `jordylab-be/src/test/java/dev/jordy/jordylab/settings/GuestChatLimitIntegrationTest.java` (a failed answer does not count, FR-013)
- [x] T057 [US1] Create `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/rest/controller/LibBotController.java` (`POST /libbot/ask` as `SseEmitter` on a virtual thread: `stage` events, one `answer` or `error`, heartbeat comments every 15 s; request record `LibBotAskRequest` and response records in `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/rest/controller/model/`; `GET /libbot/quota`) per `specs/013-gamecatalog-refinement-ai/contracts/libbot-api.md`, plus `LibBotControllerTest.java` (MockMvc async: stage order, answer shape, error shape, 400 on empty or > 1000 characters, 429 JSON before the stream) in `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/rest/controller/`
- [x] T058 [US1] Update `jordylab-be/src/main/java/dev/jordy/jordylab/shared/config/SecurityConfig.java`: allow `POST /api/gamecatalog/libbot/**` and `GET /api/gamecatalog/libbot/quota` for `admin` and `guest` before the admin catch-all, remove the old `/chat` matcher, and add the rows to `jordylab-be/src/test/java/dev/jordy/jordylab/settings/RoleMatrixTest.java`
- [x] T059 [US1] Remove the old chat: delete `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/service/ChatService.java`, `ChatUnavailableException`/`ChatAttachmentException` (replace with LibBot equivalents), `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/rest/controller/model/ChatRequest.java`, `ChatResponse.java`, `ChatGameRef.java`, the `/chat` mapping in `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/rest/controller/GameCatalogController.java`, `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/service/ChatServiceTest.java` and the chat cases in `GameCatalogControllerTest.java`; drop `findForChatFilter` from `GameRepository` and its test
- [x] T060 [P] [US1] Frontend models and API: replace the chat types in `jordylab-fe/libs/gamecatalog/api/src/lib/gamecatalog.models.ts` with the LibBot types (`LibBotAnswer`, `LibBotStage`, `LibBotReference`, quota) and add `askLibBot()` (reads the SSE stream with `fetch` + a stream reader, attaching the bearer token; cancellable) and `getLibBotQuota()` to `jordylab-fe/libs/gamecatalog/api/src/lib/gamecatalog-api.service.ts` with tests in `gamecatalog-api.service.spec.ts`; mocks `jordylab-fe/libs/gamecatalog/api/src/lib/mocks/libbot-answer.model.mock.ts` replacing `chat-answer.model.mock.ts`
- [x] T061 [US1] [Skill `/angular-signal-store`] Create `jordylab-fe/libs/gamecatalog/api/src/lib/libbot.store.ts` replacing `game-chat.store.ts` (private signals: messages, stage, busy, error, quota; `ask()`, `retry()`, `newConversation()`; keeps the typed text on error) with `libbot.store.spec.ts` (skill `/angular-test`: `useValue` + `vi.fn()`, real signals) and export it from `jordylab-fe/libs/gamecatalog/api/src/lib/../index.ts`
- [x] T062 [US1] Build the LibBot page per `specs/013-gamecatalog-refinement-ai/ui-design.md` §5: `jordylab-fe/libs/gamecatalog/ui/src/lib/libbot/libbot.component.ts` (container), `libbot-view.component.ts` and `libbot-view.component.html` (presentation: header with sparkle icon, quota line, message thread, stage indicator as `role="status"` with `prefers-reduced-motion`, applied-constraint chips, amber unknown-data note, reference chips, composer, error with Try again) replacing `jordylab-fe/libs/gamecatalog/ui/src/lib/game-chat/`; specs for both with Spectator; register `libbot` (and a redirect from `chat`) in `jordylab-fe/libs/gamecatalog/ui/src/lib/gamecatalog.routes.ts`
- [x] T063 [US1] Update the "Ask the catalog" entry on the game page in `jordylab-fe/libs/gamecatalog/ui/src/lib/game-detail/game-detail-view.component.html` and `game-detail.component.ts` to open `libbot` with the game attached, and fix `game-detail.component.spec.ts`
- [x] T064 [US1] Replace `jordylab-fe/apps/jordylab-e2e/src/gamecatalog-chat.spec.ts` with `jordylab-fe/apps/jordylab-e2e/src/gamecatalog-libbot.spec.ts`: the page renders for admin and guest, a question reaches the model call and the graceful "unavailable" state is asserted (the suite never calls a real model, per `jordylab-fe/AGENTS.md`)
- [x] T065 [US1] Run `./gradlew test --tests '*libbot*' --tests '*GuestChat*' --tests '*RoleMatrixTest' --tests '*ModularityTests'` and `bunx nx test gamecatalog-api gamecatalog-ui` and fix failures

**Checkpoint**: `./gradlew test --tests '*libbot*'` green, golden replay tier green, LibBot page answers locally.

---

## Phase 4: User Story 2 - Everyday phrasing becomes the right filter (Priority: P1)

**Purpose**: Situation understanding, in English and Dutch, with the applied constraints shown to the user.

**Goal**: "6 people here for game night" means 6+ local players, local only; "just me" means single player; "online with friends" means online; hosts by display name; the applied constraints are stated and correctable; Dutch works the same.

**Independent Test**: Send the US2 phrases (English and Dutch) and check the applied chips and games (quickstart A3 row 2); run the live golden tier for the situation cases.

- [x] T066 [P] [US2] Extend `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/service/libbot/ConstraintDeriverTest.java` with every US2 scenario (party size 6 and 4, alone, online, named host or console by its display name, "meaning to play with a friend" using the user's want-to-play marks as a constraint placeholder activated in US10, huge party size 40, correction in the next message "no, online is fine too") and extend `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/service/libbot/ConstraintDeriver.java` until green
- [x] T067 [P] [US2] Create `jordylab-be/src/main/resources/libbot/messages_nl.properties` with the Dutch templates (applied-constraint phrases, unknown-data note, decline with example, unavailable, rephrase) and extend `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/service/libbot/LibBotMessages.java` + `LibBotMessagesTest.java` to select by `language ∈ {en, nl}` (other → the fixed English rephrase reply, FR-056)
- [x] T068 [US2] Teach the prompts the language rule and the situation examples in `jordylab-be/src/main/resources/prompts/gamecatalog/libbot-interpret.st` and `libbot-answer.st` ("answer in <language>", facts extraction examples in English and Dutch such as "er zijn vandaag 6 mensen voor spelavond"); keep the derivation of constraints and the applied sentence in Java, never in the model
- [x] T069 [P] [US2] Add the US2 cases and their Dutch twins (plus the Dutch cat question and a Dutch out-of-scope decline) to `jordylab-be/src/test/resources/libbot/golden/golden-questions.json` with recorded outputs, and extend `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/service/libbot/GoldenReplayTest.java` to assert language, derived constraints and applied sentences
- [x] T070 [US2] Add the Gradle task `goldenLive` in `jordylab-be/build.gradle.kts` (tagged JUnit category `golden-live`, excluded from `test`, calls the real models, prints pass rate per category and token cost, fails below 90 % overall or below 100 % on the two hard rules) with `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/service/libbot/GoldenLiveTest.java`; document it in `jordylab-be/AGENTS.md` and `specs/013-gamecatalog-refinement-ai/quickstart.md` (already referenced)
- [x] T071 [P] [US2] Frontend: show the applied constraints as dashed chips under each answer and the Dutch composer placeholder in `jordylab-fe/libs/gamecatalog/ui/src/lib/libbot/libbot-view.component.html`, with spec coverage in `libbot-view.component.spec.ts`
- [ ] T072 [US2] Run `./gradlew goldenLive` locally with the dev keys (never print keys) and record pass rates and cost in `specs/013-gamecatalog-refinement-ai/validation-results.md`; fix prompts or derivation until the situation cases pass

**Checkpoint**: All situation cases pass in the replay tier; live tier ≥ 90 % for situation cases.

---

## Phase 5: User Story 3 - Follow-up questions within a conversation (Priority: P1)

**Purpose**: Session memory, bounded and never stored.

**Goal**: LibBot resolves "those", "the first one" and "this" from the last 10 exchanges of the current conversation; New conversation resets; users never see each other's conversations; nothing persists.

**Independent Test**: Ask the three-step follow-up flow, press New conversation, repeat as a guest in a second browser (quickstart A3 row 3).

- [x] T073 [P] [US3] Write `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/service/libbot/ConversationStoreTest.java` first: keeps the last 10 exchanges, evicts the oldest, key is `userSubject:conversationId`, another subject cannot read or delete the entry even with the same conversation id, idle expiry through an injected `Ticker`, `clear` is idempotent
- [x] T074 [US3] Create `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/service/libbot/ConversationStore.java` (Caffeine, `expireAfterAccess` from config, `maximumSize(2000)`, `ConversationTurn` record with user text, answer text, cited game ids, outcome) and wire it into `LibBotService` and `QuestionInterpreter` (history added as messages; cited ids resolve "those")
- [x] T075 [US3] Add `DELETE /api/gamecatalog/libbot/conversations/{conversationId}` to `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/rest/controller/LibBotController.java` (idempotent `204`, caller's own key only) with controller tests; derive the key from the JWT subject, never from the client alone
- [x] T076 [P] [US3] Add the follow-up golden cases ("which of those are installed", "how many other games like the first one", the "this" with no context → `CLARIFY`, a reference to a game hidden meanwhile) in English and Dutch to the fixtures and `GoldenReplayTest.java`; add a two-user isolation test in `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/service/libbot/LibBotServiceTest.java` and a very-long-conversation test (turn 25 still works)
- [x] T077 [P] [US3] Frontend: generate a `conversationId` per visit in `jordylab-fe/libs/gamecatalog/api/src/lib/libbot.store.ts`, send it on every ask, implement `newConversation()` (new id + `DELETE` call + cleared thread) and the "New conversation" button in `jordylab-fe/libs/gamecatalog/ui/src/lib/libbot/libbot-view.component.html`; extend `libbot.store.spec.ts` and `libbot-view.component.spec.ts` (reload yields an empty thread)

**Checkpoint**: Follow-up golden cases pass; two-user isolation test green.

---

## Phase 6: User Story 4 - Every game has artwork and facts without pressing anything (Priority: P1)

**Purpose**: The auto-fill worker, IGDB-backed facts and covers, daily retries, and visible health counts.

**Goal**: Within about ten minutes of a scan or add, new games have cover, facts and a description with no button pressed; at least 90 % of the library shows a real cover; leftovers heal daily; the admin sees what is still missing.

**Independent Test**: Run the real scanner against the local backend, wait, and read the Sources health counts and the grid (quickstart A3 row 4).

- [x] T078 [P] [US4] Extend `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/rest/client/IgdbClient.java`: `findGame(title, platformIgdbId)` with title-key-tolerant matching and a title-only fallback, `fetchFacts(igdbGameId)` (genres, developer, publisher, year, summary used only as grounding input, multiplayer via the existing batch call), `fetchCoverAndBanner(igdbGameId)` (cover `t_cover_big`, artwork/screenshot `t_screenshot_big` URLs), keeping the 260 ms pacing; tests in `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/rest/client/IgdbClientTest.java` with WireMock (match by platform, fallback by title, no match, rate limit, unconfigured → empty)
- [x] T079 [P] [US4] Extend `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/rest/client/ArtworkLookupClient.java` to try libretro thumbnail name **variants** derived from the normalised title (regional suffix forms, `&` → `_`, punctuation forms) and take repos from `PlatformCatalog`; tests in `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/rest/client/ArtworkLookupClientTest.java` (variants tried in order, first hit wins, all miss → empty)
- [x] T080 [US4] Create the auto-fill steps under `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/service/autofill/`: `FactsStep.java` (Steam appdetails for Steam games; IGDB otherwise; sets `factsCheckedAt`, increments attempts, parks after the configured ceiling), `ArtworkStep.java` (order: Steam CDN → IGDB cover and banner → libretro variants → placeholder; sets `artworkCheckedAt`), `DescriptionStep.java` (Steam games keep Steam's description; AI only for games with no store description, via `EnrichmentService`, limited to 3 attempts), `EmbeddingStep.java` (calls `GameEmbeddingService`), each idempotent and saved in its own short transaction (`TransactionTemplate`); **also remove `@Transactional` from `EnrichmentService.enrichPending/refresh` and from the per-game methods of `CatalogRefreshService` so no AI or HTTP call runs inside a transaction (FR-017, research A10)**, persisting each result in its own short transaction, with one test class per step in `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/service/autofill/` (Mockito for clients; Testcontainers where SQL matters)
- [x] T081 [US4] Create `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/service/autofill/CatalogAutoFillService.java` (single-flight guard, small batches from `GameRepository` backlog queries ordered installed-first, runs the four steps per game, catches per-game failures, never holds a transaction around an AI or HTTP call) and `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/CatalogChanged.java` (public event in the module root package) with `CatalogAutoFillServiceTest.java` (single-flight, one failing game does not stop the batch, free steps retry after `free-lookup-retry-hours`, AI step stops at 3 attempts)
- [x] T082 [US4] Trigger the worker: publish `CatalogChanged` after commit from `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/service/ScanService.java` (applied scans), the Steam library syncs and console game adds; add `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/service/autofill/AutoFillTrigger.java` (`@ApplicationModuleListener` on `CatalogChanged`, `@EventListener(ApplicationReadyEvent)` and a daily `@Scheduled` sweep from config using `shared/config/SchedulingConfiguration`); remove the inline `populateCatalogData()` enrichment/metadata calls from `submitScan` so the upload no longer waits on external calls; update `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/service/ScanServiceTest.java` (response returns without AI, event published)
- [x] T083 [US4] Stop using the old bulk-drain internally: nothing calls `CatalogRefreshService.refreshPending()` any more except the existing `POST /games/refresh` endpoint, which **stays until US11 replaces the button** (its removal is in the US11 frontend task, so the UI never calls a missing endpoint)
- [x] T084 [US4] Add library health: `GET /api/gamecatalog/sources` response gains `health{gamesWithoutCover, gamesWithoutDescription, gamesPendingIndex, totalGames}` (FR-021) via `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/service/LibraryHealthService.java` and tests; model in `jordylab-fe/libs/gamecatalog/api/src/lib/gamecatalog.models.ts`, display in `jordylab-fe/libs/gamecatalog/ui/src/lib/source-manager/source-manager-view.component.html` (counts, and a "show exceptions" list fed by `GET /api/gamecatalog/sources/health/exceptions?kind=COVER|DESCRIPTION|INDEX` returning game ids and titles, added to `LibraryHealthService` and its controller with a test) with specs and mocks `jordylab-fe/libs/gamecatalog/api/src/lib/mocks/library-health.model.mock.ts`
- [x] T085 [P] [US4] Add `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/rest/client/IgdbPlatformCatalogCheck.java` (tagged `integration-igdb`, excluded from `test`, run on demand with the dev credentials): re-fetches `/platforms` and fails if any `PlatformCatalog` id no longer carries its stored IGDB name; document the command in `jordylab-be/AGENTS.md`
- [ ] T086 [US4] Measure locally: run the real scanner on this Mac (Steam) against the local backend (the Linux box scan is the owner's HANDOFF in Phase 19; use whatever real data exists at this point), wait ten minutes without pressing anything, record the cover percentage and the exception list in `specs/013-gamecatalog-refinement-ai/validation-results.md`; if below 90 %, inspect the exceptions and improve matching before building more on top

**Checkpoint**: ≥ 90 % covers measured locally on the real libraries; scan upload returns without waiting for AI.

---

## Phase 7: User Story 5 - One game, many places, never duplicated (Priority: P1)

**Purpose**: Behaviour of the multi-place model: one entry per game, create/update/delete handled, disabled sources hide and restore.

**Goal**: The same title on Steam, an emulator host and a console is one game listing all platforms and places; removing a place keeps the game while another remains; disabling a source hides exclusive games and restores them.

**Independent Test**: Scenario tests below plus quickstart A3 row 5 on the real data.

- [x] T087 [US5] Write `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/service/MultiPlacePlayScenariosTest.java` (Testcontainers; data in test code): same title on Steam + EmuDeck + console is one game with three places; a second scan of another host adds a place without touching enrichment/artwork/marks; removing the emulated copy keeps the game; removing the last place after the grace period removes the game with its embedding; a manual title overrides nothing richer; two different games with different igdb ids stay separate; a game with an IGDB id and one with none and the same title merge
- [x] T088 [US5] Implement `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/service/GameQueryService.java` multi-place read model: `platforms[]` (distinct place platforms with chip colours from `PlatformCatalog`), `sources[]` via `GameSources`, `installStatus` from enabled places, `places[]` on the detail response (`HOST_COPY`, `STEAM_LIBRARY`, `CONSOLE`), per `specs/013-gamecatalog-refinement-ai/contracts/catalog-api.md`; extend `GameQueryServiceTest.java`
- [x] T089 [US5] Wire purge through `PlaceRemovalService` in `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/service/ReconciliationService.purgeUninstalledGames` and make sure artwork files and embeddings are removed with the game; extend `ReconciliationServiceTest.java`
- [x] T090 [P] [US5] Write `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/service/DisabledSourceVisibilityTest.java`: disabling hides games that live only on the source, keeps games with another place showing only the other places, deletes nothing, re-enabling restores everything (marks surviving a disable and re-enable are asserted in the US10 mark journey, T119), and the toggle endpoint response is unchanged
- [x] T091 [US5] Frontend: update `jordylab-fe/libs/gamecatalog/api/src/lib/gamecatalog.models.ts` (`GameSummary.platforms[]`, `sources[]`, `GameDetail.places[]`, remove `platform`, `hosts`, `hostFormats`), `jordylab-fe/libs/gamecatalog/api/src/lib/gamecatalog-api.service.ts`, the mocks in `jordylab-fe/libs/gamecatalog/api/src/lib/mocks/` and the stores `game-detail.store.ts`, `game-library.store.ts` so they compile; show all platforms as plain text chips in `jordylab-fe/libs/gamecatalog/ui/src/lib/game-grid/game-grid-view.component.html` and a places list in `jordylab-fe/libs/gamecatalog/ui/src/lib/game-detail/game-detail-view.component.html` (styling comes in US9); fix the specs

**Checkpoint**: Scenario test class green; no duplicates after merging the real dev data.

---

## Phase 8: User Story 6 - Consoles tab: add a console, then add games to it (Priority: P2)

**Purpose**: Consoles replace the Switch games tab.

**Goal**: The admin registers consoles (autocomplete of well-known consoles from generation 5, custom names allowed) and adds games to them by search, by title or by pasted list; no physical/digital field; existing Switch games live on a "Nintendo Switch" console; the Switch entry is gone from Scan sources.

**Independent Test**: Quickstart A3 row 6.

- [x] T092 [P] [US6] Write `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/service/ConsoleServiceTest.java` first: add with a catalog platform and default name, add custom platform, the same platform twice under different names, duplicate name (case-insensitive, also against a host's display name) rejected, over 40 characters rejected, rename, impact counts, removal keeps games that live elsewhere and removes the rest with marks and embeddings, `known(q)` filtering and ordering by generation
- [x] T093 [US6] Create `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/service/ConsoleService.java` (add, rename, list with game counts, `impact`, remove through `PlaceRemovalService`, `known(q)` from `PlatformCatalog`) and the records in `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/rest/controller/model/` (`ConsoleRequest`, `ConsoleResponse`, `KnownConsoleResponse`, `ConsoleImpactResponse`)
- [x] T094 [P] [US6] Write `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/service/ConsoleGameServiceTest.java` first (Mockito for `IgdbClient`, Testcontainers for persistence): add from IGDB id, add by title with and without a match, link to an existing game from Steam or another console (no duplicate), `ALREADY_ON_CONSOLE`, relink merging into an existing match, delete from one console keeps the game when another place exists, publishes `CatalogChanged`
- [x] T095 [US6] Create `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/service/ConsoleGameService.java` and `ConsoleBulkService.java` (generalising the deleted Switch services: console id in scope, IGDB search restricted to the console's platform id via `IgdbClient.searchGames(query, platformIgdbId)` added in this task with WireMock tests in `IgdbClientTest.java`, bulk preview with `MATCHED|NO_MATCH|ALREADY_PRESENT`, bulk confirm) and their request/response records
- [x] T096 [US6] Create `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/rest/controller/ConsoleController.java` and `ConsoleExceptionHandler.java` exactly per `specs/013-gamecatalog-refinement-ai/contracts/consoles-api.md` (all admin-only through the existing catch-all) with `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/rest/controller/ConsoleControllerTest.java` and `ConsoleControllerSecurityTest.java` (guest 403 on every endpoint, replacing the old Switch security test); add the rows to `jordylab-be/src/test/java/dev/jordy/jordylab/settings/RoleMatrixTest.java`
- [x] T097 [US6] Frontend API: console models and calls in `jordylab-fe/libs/gamecatalog/api/src/lib/gamecatalog.models.ts` and `gamecatalog-api.service.ts`, mocks `jordylab-fe/libs/gamecatalog/api/src/lib/mocks/console.model.mock.ts`, `known-console.model.mock.ts`, `console-search-result.model.mock.ts`; [Skill `/angular-signal-store`] `jordylab-fe/libs/gamecatalog/api/src/lib/console.store.ts` (consoles, known suggestions with debounce, selected console, add/rename/remove, game search/add/delete, bulk preview/confirm) replacing `switch-game.store.ts` and `switch-bulk.store.ts` (delete both with their specs), with `console.store.spec.ts`
- [x] T098 [US6] Frontend UI under `jordylab-fe/libs/gamecatalog/ui/src/lib/consoles/`: `consoles.component.ts` + `consoles-view.component.ts` and `consoles-view.component.html` (console list with game counts, "Add console" with autocomplete and custom name, remove with the impact dialog, "Add game" disabled with an explanation and a shortcut when there is no console, console picker, IGDB search with suggestions, add by title, edit/relink, delete) and `consoles-bulk.component.ts` (paste a list for the chosen console), no format field anywhere; delete `jordylab-fe/libs/gamecatalog/ui/src/lib/switch-game/` and `switch-bulk/`; specs for each; routes `consoles` and `consoles/:id/games/bulk` (admin only) in `jordylab-fe/libs/gamecatalog/ui/src/lib/gamecatalog.routes.ts` with redirects from `switch` and `switch/bulk`
- [x] T099 [US6] Remove the Switch format and manual UI from the game page: `jordylab-fe/libs/gamecatalog/ui/src/lib/game-detail/game-detail-view.component.html` and `jordylab-fe/libs/gamecatalog/ui/src/lib/game-detail/game-detail-view.component.ts` (switch admin section, format select, `switchFormat`, `formatLabel`) and `game-detail.store.ts` actions; the admin relink moves to the console games list; fix `game-detail*.spec.ts`
- [ ] T100 [US6] Verify the migration result with the real scanner: the Sources page lists only scan sources, the migrated "Nintendo Switch" console holds the former Switch games without a format, and record it in `specs/013-gamecatalog-refinement-ai/validation-results.md`

**Checkpoint**: Consoles flow green end to end locally; guest gets 403.

---

## Phase 9: User Story 7 - A filter bar that is calm by default and clear when active (Priority: P2)

**Purpose**: Backend filter parameters, the Filters panel and the active chip row, per the design.

**Goal**: Search plus one Filters button by default; every active filter is a removable chip with Clear all and a live count; filters survive reload and Back; works at 360 px by keyboard and screen reader.

**Independent Test**: Quickstart A3 row 7 and the axe/overflow journey.

- [x] T101 [P] [US7] Write `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/domain/repository/GameFilterQueryTest.java` first (Testcontainers): multi-platform any-of, `where` host and console ids, `source` any-of, `minLocalPlayers` returning confirmed matches and reporting `unknownPlayerCount`, install status, search, sort `TITLE|MOST_WANTED|MOST_LIKED` (vote parts asserted after US10), combined filters, paging
- [x] T102 [US7] Implement the new `GET /api/gamecatalog/games` parameters from `specs/013-gamecatalog-refinement-ai/contracts/catalog-api.md` in `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/domain/repository/GameFilterRepository.java` (custom repository with a dynamic query on top of `GameVisibility`), `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/service/GameQueryService.java` and `GameCatalogController.java` (replace `host`/`librarySource`), add `GET /places` (guest-readable: replace the `/hosts` matcher with `/places` in `jordylab-be/src/main/java/dev/jordy/jordylab/shared/config/SecurityConfig.java` and add the row to `jordylab-be/src/test/java/dev/jordy/jordylab/settings/RoleMatrixTest.java`) and the colour fields on `GET /platforms`; update `GameCatalogControllerTest.java`
- [x] T103 [US7] Frontend store: extend `jordylab-fe/libs/gamecatalog/api/src/lib/game-library.store.ts` to arrays and the new filters (`platform[]`, `where[]`, `installStatus`, `source[]`, `minLocalPlayers`, `romStatus[]`, `mark[]`, `markScope`, `sort`), mirror state to the query string (reload and Back restore it), expose `activeFilters` as a computed list of `{key, value, remove}` and `clearAll()`; default `Status: Installed` is a real removable chip; refetch the list and the vote totals when the page regains focus (`visibilitychange`, FR-044); extend `game-library.store.spec.ts` (skill `/angular-test`) and the API service/mocks
- [x] T104 [US7] Build the filter UI per `specs/013-gamecatalog-refinement-ai/ui-design.md` §2 in `jordylab-fe/libs/gamecatalog/ui/src/lib/game-grid/`: `filter-bar.component.ts` and `filter-bar.component.html` (search, Filters button with count badge, Sort), `active-filters.component.ts` and `active-filters.component.html` (chips with × buttons, Clear all, live count `aria-live`), `filters-panel.component.ts` and `filters-panel.component.html` (popover ≥ 641 px, bottom sheet ≤ 640 px; groups Platform, Where, Status, Source, Local players stepper, ROM status, Community marks + whose, Sort; focus moves in and returns on Esc), replacing the five chip rows in `game-grid-view.component.html`; empty-result state naming the filter to relax; specs for each component
- [x] T105 [US7] Add `jordylab-fe/apps/jordylab-e2e/src/gamecatalog-filters.spec.ts` (journey through the real app on the e2e stack: apply platform + status + players, chips appear, reload keeps them, Clear all, phone-width bottom sheet, keyboard open/close) and extend `jordylab-fe/apps/jordylab-e2e/src/accessibility.spec.ts` if a new state needs axe coverage

**Checkpoint**: Filter journey green at desktop and phone width.

---

## Phase 10: User Story 8 - Give a host a name I choose (Priority: P2)

**Purpose**: The display name on the Host entity, shown everywhere.

**Goal**: An admin-set display name replaces the hostname in every screen and in LibBot answers; blank restores the hostname; scans never change it; duplicates refused.

**Independent Test**: Quickstart A3 row 8.

- [x] T106 [P] [US8] Write `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/service/HostServiceTest.java` first: rename applies to every source of the host, blank clears, duplicate against another host or a console refused, a scan from a named host leaves the name unchanged, `label()` used by all read models
- [x] T107 [US8] Create `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/service/HostService.java` and `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/rest/controller/HostController.java` (`PUT /hosts/{id}/display-name`, `409 NAME_TAKEN`, `400 NAME_TOO_LONG`, admin only) with `HostControllerTest.java`; add the RoleMatrix row
- [x] T108 [US8] Replace every hostname display with `Host.label()`: `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/service/ScanSourceService.java` (`label` plus `hostname` as the secondary field for the admin), `GameQueryService.java` (`places[].label`, `/places`), the LibBot retrieval/answer vocabulary in `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/service/libbot/`, and any DTO still exposing the raw hostname to guests; grep `getHostname()` to prove none is left outside the scan path; extend the corresponding tests
- [x] T109 [US8] Frontend: host name editing on the Sources page in `jordylab-fe/libs/gamecatalog/ui/src/lib/source-manager/source-manager-view.component.html` and `jordylab-fe/libs/gamecatalog/ui/src/lib/source-manager/source-manager-view.component.ts` and `scan-source.store.ts` (inline edit, save, clear, conflict message; hostname as a secondary mono line), use `label` in the grid filters, detail page and LibBot references; update models, mocks (`scan-source.model.mock.ts`) and specs

**Checkpoint**: No screen shows a hostname as main label when a display name exists.

---

## Phase 11: User Story 9 - Labels you can read at a glance (Priority: P2)

**Purpose**: Source labels, brand colours and status chips.

**Goal**: Steam (Owned), Steam (Family), Emulated, Console labels; platform chips in official colours; Installed and Not installed in distinct colours with icons; all chips pass the contrast check.

**Independent Test**: Quickstart A3 row 9 and the axe journey.

- [x] T110 [US9] Serve the labels and colours: `GameSummaryResponse.sources[]` filled from `GameSources` and `platforms[]` from `PlatformCatalog` in `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/service/GameQueryService.java`, `GET /platforms` with colours; retire `LibrarySource.LOCAL` (`jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/domain/LibrarySource.java`, `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/rest/controller/model/*` and all usages); extend `GameQueryServiceTest.java`
- [x] T111 [US9] Build the chip family per `specs/013-gamecatalog-refinement-ai/ui-design.md` §3 in `jordylab-fe/libs/gamecatalog/ui/src/lib/chips/`: `platform-chip.component.ts` (colours from the platforms data, no hard-coded values), `status-chip.component.ts` (outlined, icon, dashed for not installed), `source-label.component.ts`, `rom-chip.component.ts`, `mark-chip.component.ts` (soft pill with count, outline for the user's own vote) with specs; delete `platformTagClass` and the palette branch from `jordylab-fe/libs/gamecatalog/ui/src/lib/cover.ts` and use the chips in the grid card and the game page; update `cover.spec.ts` and the view specs
- [x] T112 [US9] Replace "Owned/Family/Local" labels everywhere in the frontend (`game-grid-view.component.*`, `game-detail-view.component.*`, LibBot reference chips, `jordylab-fe/libs/gamecatalog/api/src/lib/gamecatalog.models.ts`) and check the axe journey at desktop and phone width with all chip variants present in the e2e data (`jordylab-fe/apps/jordylab-e2e/src/accessibility.spec.ts`)

**Checkpoint**: Chip contrast unit test and axe journey green.

---

## Phase 12: User Story 10 - Mark a game: want to play, played & liked, or played & disliked (Priority: P2)

**Purpose**: Public one-per-user votes with ranking weight.

**Goal**: Each user holds at most one of three marks per game; totals are public; more votes mean more weight in sorting and in LibBot (after hard requirements); removed accounts' votes disappear.

**Independent Test**: Quickstart A3 row 10 with an admin and a guest.

- [x] T113 [P] [US10] Write `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/service/MarkServiceTest.java` first: set, replace, clear, one mark per user (unique `(game_id, user_subject)` violation surfaces as replace, not as a second row), totals for all three, two users counted separately, never exposes voter identity, `404` for an invisible game
- [x] T114 [US10] Create `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/service/MarkService.java` and `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/rest/controller/MarkController.java` (`PUT /games/{id}/mark`, caller's subject from the JWT) with request/response records and `MarkControllerTest.java`; add the guest-allowed matchers in `jordylab-be/src/main/java/dev/jordy/jordylab/shared/config/SecurityConfig.java` and rows in `jordylab-be/src/test/java/dev/jordy/jordylab/settings/RoleMatrixTest.java`
- [x] T115 [US10] Add votes and `myMark` to the summary/detail read models in `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/service/GameQueryService.java` (bulk vote counts, one query per page) and the `mark`/`markScope`/`MOST_WANTED`/`MOST_LIKED` handling in `GameFilterRepository.java`; extend `GameFilterQueryTest.java` and `GameQueryServiceTest.java`
- [x] T116 [US10] Create the public event `UserAccessRemoved(userSubject)` in `jordylab-be/src/main/java/dev/jordy/jordylab/settings/UserAccessRemoved.java` (settings module root package, public API), publish it from `jordylab-be/src/main/java/dev/jordy/jordylab/settings/service/KeycloakUserAdministrationService.java` on revoke/reject/delete, add `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/service/MarkCleanupListener.java` (`@ApplicationModuleListener` deleting that subject's marks) and tests in `jordylab-be/src/test/java/dev/jordy/jordylab/settings/service/KeycloakUserAdministrationServiceTest.java` and `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/service/MarkCleanupListenerTest.java` (FR-046); run `ModularityTests`
- [x] T117 [US10] LibBot ranking and marks: extend `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/service/libbot/CandidateRetriever.java` and `ConstraintDeriver.java` so that, **after** hard requirements, candidates order by `(want + liked) − disliked` then similarity; the asker's disliked games are excluded; the asker's liked games are excluded for "like what I liked"; `markFilter`/`markScope` constraints use the asker's own or everyone's marks; votes and `myMark` go into the compose prompt rows and the answer mentions votes when they decided; extend `CandidateRetrieverTest.java`, `LibBotServiceTest.java` and add the vote cases to the golden fixtures (a 5-vote game outranks an equal fit; a popular game failing a requirement is never offered) incl. Dutch twins
- [x] T118 [US10] Frontend: [Skill `/angular-signal-store`] `jordylab-fe/libs/gamecatalog/api/src/lib/mark.store.ts` (set/replace/clear with optimistic update and rollback), mark buttons (44 px, `aria-pressed`, mutually exclusive) and the vote rail on the card in `jordylab-fe/libs/gamecatalog/ui/src/lib/game-grid/`, mark controls and totals on the game page, `MOST_WANTED`/`MOST_LIKED` sorting and mark filters wired in the Filters panel; models, mocks `mark-summary.model.mock.ts`, specs
- [x] T119 [US10] Add `jordylab-fe/apps/jordylab-e2e/src/gamecatalog-marks.spec.ts` (admin and a signed-up guest vote on overlapping games through the app, totals equal for both, replace and clear behaviour)

**Checkpoint**: Mark journey green; LibBot ranks by votes and never offers a requirement-failing game.

---

## Phase 13: User Story 11 - Admin refreshes everything on demand (Priority: P3)

**Purpose**: Durable bulk refresh runs for data and AI.

**Goal**: Two admin buttons on the Sources page start background runs over every game with progress, summary, stop and a cost confirmation for the AI run; the Refresh pending data button is gone.

**Independent Test**: Quickstart A3 row 11.

- [x] T120 [P] [US11] Write `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/service/autofill/RefreshRunServiceTest.java` first: start per kind, second start refused while running, progress advances per game, one failing game does not stop the run, stop request ends it after the current game, five consecutive identical failure causes (402, auth) stop early with the cause in `failureSummary`, hand-corrected data kept (`TitleSource.MANUAL`, relinked ids), `RUNNING` rows become `INTERRUPTED` on startup, AI start requires the cost confirmation
- [x] T121 [US11] Create `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/service/autofill/RefreshRunService.java` (virtual-thread executor, commits per game, reuses the auto-fill steps with `force` semantics: `DATA` re-fetches facts and artwork, `AI` regenerates descriptions only for games without a store description) and the `ApplicationReadyEvent` interrupter; create `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/rest/controller/RefreshRunController.java` (`POST /refresh-runs`, `GET /refresh-runs/current`, `POST /refresh-runs/{id}/stop`, `400 COST_CONFIRMATION_REQUIRED` with the game count) with `RefreshRunControllerTest.java`; admin only, RoleMatrix rows
- [x] T122 [US11] Frontend: [Skill `/angular-signal-store`] `jordylab-fe/libs/gamecatalog/api/src/lib/refresh-run.store.ts` (polls `/current` every 2 s while running, start, stop), models and mocks (`refresh-run.model.mock.ts`), and on `jordylab-fe/libs/gamecatalog/ui/src/lib/source-manager/` replace the "Refresh pending data" button with "Refresh game data" and "Regenerate AI data" (confirmation dialog stating paid AI calls and the game count, explicit confirm, progress bar, Stop, summary); delete the old refresh-all code paths in `scan-source.store.ts`; **then delete the backend `POST /games/refresh` mapping, `CatalogRefreshService.refreshPending()`, `RefreshAllResponse.java`, `RefreshCountResponse.java` and fix `GameCatalogControllerTest.java`**; specs

**Checkpoint**: Both runs complete, stop and refuse a second start locally.

---

## Phase 14: User Story 12 - Clear names and a clean Sources page (Priority: P3)

**Purpose**: Navigation labels, redirects and the hide-impact confirmation.

**Goal**: The nav reads LibBot (sparkle icon) and Consoles; Sources lists scan sources only; turning a source off first says how many games will be hidden and that nothing is deleted.

**Independent Test**: Quickstart A3 row 12.

- [x] T123 [P] [US12] Add `GET /sources/{id}/hide-impact` (`jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/service/ScanSourceService.java` using `GameVisibility`; `ScanSourceController.java`) returning `hiddenGames` and `stillVisibleElsewhere`, with tests in `ScanSourceServiceTest.java` and `ScanSourceControllerTest.java`
- [x] T124 [P] [US12] Frontend: rename nav items and icons in `jordylab-fe/apps/jordylab/src/app/app.ts` and `app.html` ("Chat" → "LibBot" with a sparkle icon, "Switch games" → "Consoles"), update `app.spec.ts` (admin: Library, LibBot, Sources, Consoles; guest: Library, LibBot), `app.routes.ts` redirects and `app.routes.spec.ts`; show the hide-impact confirmation before toggling a source in `jordylab-fe/libs/gamecatalog/ui/src/lib/source-manager/source-manager-view.component.*` and `scan-source.store.ts`; specs

**Checkpoint**: App shell specs green for admin and guest.

---

## Phase 15: User Story 13 - Mark whether an emulated game actually works (Priority: P2)

**Purpose**: Per-machine ROM status.

**Goal**: Each emulated copy carries Unknown/Validated/Broken; shared and public; shown per machine and summarised on the card; filterable; LibBot never offers a broken copy as playable on that machine.

**Independent Test**: Quickstart A3 row 13.

- [x] T125 [P] [US13] Write `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/service/RomStatusServiceTest.java` first: set/clear on an emulated copy, `ROM_STATUS_NOT_APPLICABLE` for Steam and console places, installation of another game rejected, last write wins, survives rescan and the grace period
- [x] T126 [US13] Create `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/service/RomStatusService.java` and `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/rest/controller/RomStatusController.java` (`PUT /games/{id}/installations/{installationId}/rom-status`, `409`, `404`) with `RomStatusControllerTest.java` (guest allowed); add matchers and RoleMatrix rows
- [x] T127 [US13] Add `romStatus` per host copy to `places[]` and `romSummary` to the summary in `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/service/GameQueryService.java`, the `romStatus` filter in `GameFilterRepository.java`, and the playable-places rule in `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/service/libbot/CandidateRetriever.java` (a game whose only playable places are Broken copies is excluded; Validated preferred; the compose prompt names machine and status); extend the three test classes and the golden fixtures (EN and NL)
- [x] T128 [US13] Frontend: per-machine ROM status controls on the game page (`jordylab-fe/libs/gamecatalog/ui/src/lib/game-detail/`), the summary chip on the card using `rom-chip.component.ts`, the ROM filter group in the Filters panel, models and mocks (`rom-summary.model.mock.ts`), specs

**Checkpoint**: Not-applicable 409 test green; LibBot excludes broken copies in the golden set.

---

## Phase 16: User Story 14 - The mobile app signs in cleanly and never scrolls sideways (Priority: P2)

**Purpose**: The fingerprint error flash and horizontal scroll.

**Goal**: No error message during a successful fingerprint sign-in; a real failure still shows one accurate message; no page scrolls sideways from 360 px.

**Independent Test**: Unit tests for the sign-in flow, the Playwright overflow check, and 20 manual fingerprint sign-ins on the phone (quickstart A3 row 14).

- [x] T129 [P] [US14] **Red first**: add failing cases to `jordylab-fe/libs/shared/auth/src/lib/auth.service.spec.ts` (`getToken()` with a Keycloak instance and no session returns `null`, sets **no** `nativeFailure` and does not navigate; with a session that then dies it reports the failure once) and to `jordylab-fe/libs/shared/auth/src/lib/biometric-unlock.service.spec.ts` (`unlock()` clears `failure` and `nativeFailure` when it starts and after success; offline shows only the connection message); run them and record that they fail
- [x] T130 [US14] Fix `jordylab-fe/libs/shared/auth/src/lib/auth.service.ts` (`getToken()` returns `null` quietly when there was never an authenticated session; the native failure message only for a session that existed) and `jordylab-fe/libs/shared/auth/src/lib/biometric-unlock.service.ts` (clear stale failures at the start of `unlock()`), update `jordylab-fe/libs/shared/auth/src/lib/login.component.ts` if it reads stale state, and make the red tests green plus `login.component.spec.ts`
- [x] T131 [P] [US14] Add the horizontal-overflow check to `jordylab-fe/apps/jordylab-e2e/src/accessibility.spec.ts`: for every signed-in page (library, game detail, LibBot, Sources, Consoles, Settings pages, FNA pages) at 360, 390 and 430 px assert `document.documentElement.scrollWidth <= clientWidth` and report the offending element; run it once to list the current offenders in `specs/013-gamecatalog-refinement-ai/validation-results.md`
- [x] T132 [US14] Fix every offender the check finds (`min-width: 0` on flex/grid children, `overflow-wrap: anywhere` on titles/answers, inner scroll containers for tables) across `jordylab-fe/libs/gamecatalog/ui/src/lib/**`, `jordylab-fe/libs/fna/ui/src/lib/**` and `jordylab-fe/libs/shared/**`, then re-run until the check is green at all three widths
- [x] T133 [US14] Run the Android startup suite (`cd jordylab-fe && e2e/run.sh android`, needs an emulator) and add the **manual** fingerprint checklist (20 sign-ins, expected: no red message at any moment) to the "Manual tests" section in `docs/runbook.md` and `jordylab-fe/apps/jordylab-mobile-e2e/README.md`

**Checkpoint**: Red tests turned green; overflow check green on every page.

---

## Phase 17: User Story 15 - The game page names the model that really wrote the text, and looks the same on a phone (Priority: P2)

**Purpose**: Description provenance, store-blurb quality, and one banner/cover composition on every width.

**Goal**: The About heading shows the real source (answering model and router, Steam, or plain "written by AI"); facts' source is in the Spec sheet; AI descriptions read like Steam blurbs and failing ones are never shown; the cover overlaps the banner on phones as on desktop.

**Independent Test**: Quickstart A3 row 15 incl. the 20 + 20 description comparison.

- [x] T134 [P] [US15] Write `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/service/DescriptionQualityValidatorTest.java` first: 2 to 4 sentences and about 40 to 110 words accepted; first person, "as an AI", marketing filler lists, invented scores, and a year contradicting the known release year rejected; empty or one-liner rejected
- [x] T135 [US15] Create `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/service/DescriptionQualityValidator.java` and rewrite `jordylab-be/src/main/resources/prompts/gamecatalog/enrichment.st` to produce a store blurb (plain third person, what the game is and what the player does, grounded only in the supplied facts and IGDB summary, 2 to 4 sentences); extend `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/service/EnrichmentService.java` to pass the IGDB summary as grounding input, validate the typed result, retry on failure (counts as an attempt) and never store a rejected text; update `EnrichmentServiceTest.java`
- [x] T136 [US15] Record authorship: `EnrichmentService` passes `AiCallResult` model data into `Game.applyEnrichment(..., AiAuthorship)` (answeredModel, requestedModel only when it differs, writtenAt); `applyDeterministicDescription` records `STEAM`; tests in `GameTest.java` and `EnrichmentServiceTest.java` (router case, fallback provider case, provider reports no model → selected id and "model not reported")
- [x] T137 [P] [US15] Expose `description{text, source, model, requestedModel, writtenAt}` and `factSources{facts, multiplayer}` in `GameDetailResponse` per `specs/013-gamecatalog-refinement-ai/contracts/catalog-api.md`, filled in `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/service/GameQueryService.java`; extend `GameQueryServiceTest.java` (AI with model, router, old text with no model, Steam text)
- [x] T138 [P] [US15] Add a description-quality set to the golden fixtures (games with known facts, recorded outputs) asserted by `DescriptionQualityValidator` in `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/service/libbot/GoldenReplayTest.java`, and a live-tier case in `GoldenLiveTest.java`
- [x] T139 [US15] Frontend heading and spec sheet: build the About heading from the `description` object only (cases in `specs/013-gamecatalog-refinement-ai/ui-design.md` §7: model, router, not reported, older text, Steam), drop "facts from …" from it, add the `SOURCE · FACTS FROM … · MULTIPLAYER FROM …` row to the Spec sheet in `jordylab-fe/libs/gamecatalog/ui/src/lib/game-detail/game-detail-view.component.html` and `jordylab-fe/libs/gamecatalog/ui/src/lib/game-detail/game-detail-view.component.ts`; models/mocks (`game-detail.model.mock.ts`) and specs; no vendor or model name literal remains (assert with a lint-style grep in the spec)
- [x] T140 [US15] One banner/cover composition on every width in `jordylab-fe/libs/gamecatalog/ui/src/lib/game-detail/game-detail-view.component.html`: banner `aspect-[16/9] sm:aspect-[16/5]`, cover `absolute -bottom-10 left-4 w-24 sm:left-6 sm:w-32 md:left-8 md:w-40` overlapping the bottom-left corner at all widths, content below `mt-16` on phones (also the skeleton and placeholder variants); verify in the browser pane at 360, 390 and desktop against the owner's screenshot and attach before/after to `specs/013-gamecatalog-refinement-ai/validation-results.md`; the overflow check covers it

**Checkpoint**: Quality validator and golden description set green; screenshot comparison at 360 px reviewed.

---

## Phase 18: Polish, documentation and cross-cutting concerns

**Purpose**: Docs the repo promises to keep current, deployment manifest, and the full local gate. Nothing here is released yet.

- [x] T141 [P] Update the agent docs: `jordylab-be/AGENTS.md` (remove "No scheduler in gamecatalog" and describe the auto-fill worker; rewrite the "Chat" bullets as LibBot; consoles replace Switch; hosts, places, marks, ROM status, embeddings, `goldenLive`, `IgdbPlatformCatalogCheck`), root `AGENTS.md` (module table text for gamecatalog, AI routing table rows `gamecatalog.chat.*` renamed to LibBot, "Switch" mentions), `jordylab-fe/AGENTS.md` (routes, e2e journeys list, overflow check), `jordylab-be/src/main/java/dev/jordy/jordylab/shared/ai/AGENTS.md` (embeddings, structured output, router authorship), and `docs/research/spring-ai-gap-analysis.md` (items 6, 7, 12, 15, 16 now built or partially built)
- [x] T142 [P] Update `docs/runbook.md`: add the on-demand CNPG `Backup` step (Barman Cloud plugin) next to §15, the pgvector `Database` resource step, and the LibBot/auto-fill operational notes (what to check when covers are missing, how to read the health counts)
- [x] T143 [P] Add the CNPG declarative `Database` resource enabling the `vector` extension (`spec.extensions: [{name: vector, ensure: present}]`, owner `jordylab`, reclaim policy `retain`) as `deploy/k8s/cluster/cnpg-database.yaml`, include it in the cluster kustomization, and rehearse it on the restore-drill cluster defined in `deploy/k8s/drills/restore-drill-cluster.yaml` (runbook §15) before the real apply; **owner approved this cluster change on 2026-10-07**, applied in Phase 20
- [x] T144 Add `Refs: 013 T###` trailers to every commit (root `AGENTS.md`, Commits) and log any bug found along the way in `docs/testing/bug-log.md` before fixing it; keep `.githooks/commit-msg` enabled (`git config core.hooksPath .githooks`)
- [x] T145 Full local gate: `cd jordylab-be && ./gradlew build` (tests + JaCoCo ≥ 80 % per package), `./gradlew :test --tests '*ModularityTests'`, `cd ../jordylab-fe && bunx nx run-many -t lint oxlint test`, `e2e/run.sh web` (journeys, axe, overflow), and fix everything red; record the results in `specs/013-gamecatalog-refinement-ai/validation-results.md`

---

## Phase 19: Local acceptance (everything works locally, the entire spec)

**Purpose**: Walk `specs/013-gamecatalog-refinement-ai/quickstart.md` section A on localhost with real data. This is the gate to release; there is no partial release.

- [ ] T146 Run the real scan client on this Mac (Steam) against the local backend (device-code login; never print tokens). The Linux box (cachyos-htpc, Steam + EmuDeck) is not reachable by the agent: write a **HANDOFF (owner)** block with the exact steps (download the client from the local backend, run it, report the counts) using the next free `HANDOFF-##` in `docs/testing/` and wait. If any scan cannot run, **stop and report, never seed** (root `AGENTS.md`, Validation Data)
- [ ] T147 Walk quickstart A3 rows 1 to 15 in the browser pane (desktop and 360 px) as admin and as a signed-up guest; for each row record pass/fail, evidence and a screenshot reference in `specs/013-gamecatalog-refinement-ai/validation-results.md`; log failures in `docs/testing/bug-log.md` first, then fix and re-run the row
- [ ] T148 Run `./gradlew goldenLive` and measure SC-001 (covers), SC-007 (auto-fill in ten minutes), SC-006 (latency, 20 questions) and SC-022 (20 AI-written vs 20 Steam descriptions reviewed by the owner); record the numbers
- [ ] T149 **HANDOFF (owner):** do the 20 fingerprint sign-ins on the phone with a locally built debug APK and the owner's judgement of the description comparison (SC-018, SC-022); the agent writes the HANDOFF block with exact steps in `docs/testing/` using the next free `HANDOFF-##` and waits
- [ ] T150 Decision gate: confirm in `specs/013-gamecatalog-refinement-ai/validation-results.md` that every user story and success criterion passed locally; only then continue to Phase 20

**Checkpoint**: Every row of quickstart A3 green, A1 gates green, golden live tier ≥ 90 %, SC-001 and SC-007 measured locally.

---

## Phase 20: Release

**Purpose**: Release candidate through the existing pipeline, with the cluster prerequisite first.

- [ ] T151 Open the PR from this branch (CI: backend tests, frontend tests, lint, `e2e-web`, commit references with `Refs: 013 …`), resolve all review comments, merge when green (the owner's standing authority to merge once all review comments are resolved)
- [ ] T152 Before the deploy: take an on-demand CNPG backup and confirm it completed; apply the `vector` extension `Database` resource through the deploy pipeline and verify with `kubectl -n jordylab exec cnpg-cluster-1 -c postgres -- psql -d jordylab -c '\dx'` that `vector` is listed (owner approved 2026-10-07; use the `jordylab-ops` skill, never print secrets or pod env values)
- [ ] T153 Tag the release candidate and approve the production deploy per `docs/runbook.md` §20; watch `kubectl -n jordylab rollout status deploy/backend`; review the Flyway and merge log lines (titles only); if anything fails, roll back per runbook §11

**Checkpoint**: Deploy healthy, migration log reviewed, extension present.

---

## Phase 21: Production acceptance (jordylab.be)

**Purpose**: Re-test the same scenarios on production and measure the production-only targets.

- [ ] T154 Run the real scan client against **jordylab.be** on this Mac; the Linux box scan is the owner's HANDOFF (same block pattern as Phase 19); never seed production data
- [ ] T155 Walk quickstart B2 (rows 1 to 15) on https://jordylab.be as admin and as the guest account in the browser pane; measure SC-001 from the Sources health counts, SC-007 after a scan, SC-006 with 20 questions, SC-017 and SC-020/SC-021; record everything with the release tag in `specs/013-gamecatalog-refinement-ai/validation-results.md`
- [ ] T156 **HANDOFF (owner):** 20 fingerprint sign-ins on the phone with the released APK (SC-018) and a quick look at the game page on the phone; the agent prepares the HANDOFF block and records the owner's result
- [ ] T157 Close out: log findings in `docs/testing/bug-log.md`, update `docs/testing/final-report.md` with the 013 results, and mark this feature's tasks complete

**Checkpoint**: Quickstart B2 all green on jordylab.be; results recorded; findings logged.

---

## Dependencies & Execution Order

### Phase dependencies

- **Phase 1 (Setup)**: none. **Phase 2 (Foundational)** needs Phase 1 and **blocks every user story**.
- **US1 → US2 → US3** are one pipeline: US2 and US3 extend classes created in US1.
- **US4 (auto-fill)** needs Phase 2; it is independent of US1 to US3 but feeds LibBot's search index, so run it before the final golden live run.
- **US5** needs Phase 2 (identity, places) and is a prerequisite for **US6**, **US7** and **US13**.
- **US6 (consoles)** needs US5. **US7 (filters)** needs US5 and the API shapes from US9 for chips (the store and panel can be built first). **US8**, **US9**, **US10** need Phase 2 and US5.
- **US10 (marks)** adds ranking to LibBot, so it needs US1; **US13 (ROM status)** needs US5 and extends LibBot retrieval, so it needs US1; **US11** reuses the auto-fill steps from US4. **US12** needs US6 (route names) and US8 (labels). **US14** and **US15** are independent of the others except that the overflow check should run after the new pages exist (run it last within its phase). **US15** needs US4 (IGDB summary grounding) and Phase 2 (`AiAuthorship`).
- **Polish (Phase 18)** needs all user stories. **Phase 19** needs Phase 18. **Phase 20** needs Phase 19 passing. **Phase 21** needs Phase 20.

### Within a user story

Tests first (red), then records/entities, services, controllers, frontend models/API, store, components, journeys. A task that edits a file another task edits is not marked [P].

## Parallel Opportunities

- Phase 2: `TitleKeys`, `PlatformCatalog`, the enums, and each new entity with its test (Host, Console, ConsoleGameEntry, GameMark, GameEmbedding, RefreshRun) touch different files and can run together; the modified entities (`Game`, `GameInstallation`, `ScanSource`) and the migrations are sequential.
- US1: the records, golden fixtures, validator test, retriever test, deriver test and prompts are separate files; the controller, security matcher, settings listener and the frontend model/API work can run in parallel after `LibBotService` exists.
- US4: `IgdbClient` and `ArtworkLookupClient` extensions and the platform check test are independent.
- After Phase 7, the P2 stories US8, US9, US10, US13, US14, US15 can be worked in parallel by different people or sessions (each touches different services; the shared files are `GameQueryService`, `GameFilterRepository` and the frontend `gamecatalog.models.ts`, so coordinate or rebase between them).

## Implementation Strategy

1. **Foundational first**, then **US1 as the internal MVP checkpoint**: a working LibBot answering from the new model proves the architecture before the rest is built. This is a checkpoint only; per the owner's rule nothing is released until the whole spec works locally.
2. Add **US2, US3** (same pipeline), then **US4** (data fills itself), then **US5** (places), checking the golden live tier and the real-data cover percentage as you go.
3. Build the P2 stories in this order: **US6** consoles, **US8** host names, **US9** labels and colours, **US7** filters, **US10** marks, **US13** ROM status, **US15** game page, **US14** mobile polish last within its phase because it audits every page.
4. **US11** and **US12** (P3) close the functional work. Then Polish, then **Phase 19 local acceptance**, then **Phase 20 release**, then **Phase 21 production acceptance**.
5. Stop and ask the owner before: any cluster mutation outside the approved `vector` extension change, any change to the scan client, or a different answer to the Decisions in `research.md`.

## Summary

- **Total tasks**: 157 (3 already done)

- **Per phase**:
  - Phase 1: Setup: 8 (T001 to T008)
  - Phase 2: Foundational (blocking prerequisites): 35 (T009 to T043)
  - Phase 3: User Story 1 - Ask LibBot and get a trustworthy answer (Priority: P1) 🎯 MVP: 22 (T044 to T065)
  - Phase 4: User Story 2 - Everyday phrasing becomes the right filter (Priority: P1): 7 (T066 to T072)
  - Phase 5: User Story 3 - Follow-up questions within a conversation (Priority: P1): 5 (T073 to T077)
  - Phase 6: User Story 4 - Every game has artwork and facts without pressing anything (Priority: P1): 9 (T078 to T086)
  - Phase 7: User Story 5 - One game, many places, never duplicated (Priority: P1): 5 (T087 to T091)
  - Phase 8: User Story 6 - Consoles tab: add a console, then add games to it (Priority: P2): 9 (T092 to T100)
  - Phase 9: User Story 7 - A filter bar that is calm by default and clear when active (Priority: P2): 5 (T101 to T105)
  - Phase 10: User Story 8 - Give a host a name I choose (Priority: P2): 4 (T106 to T109)
  - Phase 11: User Story 9 - Labels you can read at a glance (Priority: P2): 3 (T110 to T112)
  - Phase 12: User Story 10 - Mark a game: want to play, played & liked, or played & disliked (Priority: P2): 7 (T113 to T119)
  - Phase 13: User Story 11 - Admin refreshes everything on demand (Priority: P3): 3 (T120 to T122)
  - Phase 14: User Story 12 - Clear names and a clean Sources page (Priority: P3): 2 (T123 to T124)
  - Phase 15: User Story 13 - Mark whether an emulated game actually works (Priority: P2): 4 (T125 to T128)
  - Phase 16: User Story 14 - The mobile app signs in cleanly and never scrolls sideways (Priority: P2): 5 (T129 to T133)
  - Phase 17: User Story 15 - The game page names the model that really wrote the text, and looks the same on a phone (Priority: P2): 7 (T134 to T140)
  - Phase 18: Polish, documentation and cross-cutting concerns: 5 (T141 to T145)
  - Phase 19: Local acceptance (everything works locally, the entire spec): 5 (T146 to T150)
  - Phase 20: Release: 3 (T151 to T153)
  - Phase 21: Production acceptance (jordylab.be): 4 (T154 to T157)

- **Per user story**: US1 22, US2 7, US3 5, US4 9, US5 5, US6 9, US7 5, US8 4, US9 3, US10 7, US11 3, US12 2, US13 4, US14 5, US15 7

