---
description: "Task list for Game Catalog Refinements (card-fitted covers + banners, multi-host games, host filter, deterministic metadata, chat attachment)"
---

# Tasks: Game Catalog Refinements

**Input**: Design documents from `specs/004-gamecatalog-refinements/`

**Prerequisites**: [spec.md](./spec.md), [plan.md](./plan.md), [data-model.md](./data-model.md), [contracts/catalog-api.md](./contracts/catalog-api.md), [research.md](./research.md), [quickstart.md](./quickstart.md)

**Scope (004)**: Backend `gamecatalog` module + `libs/gamecatalog/{api,ui}` in place. Scan client, ingest API, sources UI, and Keycloak realm are untouched. Deferred: SteamGridDB provider, content-hash ROM identity, multi-attachment UI (API tolerates 5 ids; UI attaches 1).

**Tests**: INCLUDED — entity tests are definition-of-done per `jordylab-be/AGENTS.md`; JaCoCo 80% and Vitest 80% gates apply; acceptance scenarios per quickstart.md.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependencies)
- **[Story]**: US1–US5 from spec.md

---

## Phase 1: Setup

- [x] T001 Feature branch `004-gamecatalog-refinements` cut from latest `main` (includes merged PR #13 Night Lab redesign)
- [x] T002 Baseline green before changes: backend `./gradlew :test --tests "*GameCatalog*" --tests "*ModularityTests*"` (Podman Testcontainers env per `jordylab-be/AGENTS.md`) and frontend `bunx nx run-many -t test --projects=gamecatalog-api,gamecatalog-ui` in `jordylab-fe/`

## Phase 2: Foundational — `GameInstallation` split, behavior-preserving (BLOCKS all stories)

**Purpose**: The data-model split (data-model.md) as one coherent, wire-compatible unit — every existing game gets exactly one backfilled installation; current behavior and the 002/003 API contract are preserved verbatim.

- [x] T003 Flyway migration `jordylab-be/src/main/resources/db/migration/V20260927__gamecatalog_multihost_refinements.sql`: `game_installation` table + `UNIQUE (source_id, external_ref)` + indexes; backfill `INSERT … SELECT gen_random_uuid() … FROM game`; `game` gains `steam_app_id`, `genres`, `developer`, `publisher`, `release_year`, `metadata_status` (default `PENDING`), `metadata_attempts` (default 0), `banner_status` (default `PENDING`), `banner_ref`; rename `artwork_status/`artwork_ref` → `cover_status`/`cover_ref` + copy; reset Steam `EXTERNAL_URL` covers to `PENDING` (R3); derive `steam_app_id` from installation refs; drop `source_id`, `external_ref`, `presence`, `first_seen_at`, `last_seen_at`, `uninstalled_at`, `artwork_status`, `artwork_ref` from `game`
- [x] T004 [P] `GameInstallation` entity in `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/domain/GameInstallation.java` (canonical structure; `seenAgain`/`markUninstalled` mutations with events) + `GameInstallationTest` + `GameInstallationTestBuilder` in `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/domain/`
- [x] T005 [P] Reshape `Game` in `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/domain/Game.java`: drop `source`/`externalRef`/`presence`/`firstSeenAt`/`lastSeenAt`/`uninstalledAt` (moved); add `steamAppId`, `genres`, `developer`, `publisher`, `releaseYear`, `metadataStatus`, `metadataAttempts`, `bannerStatus`, `bannerRef` (cover = renamed pair); guards per data-model.md; mutations `applyCoverArtwork`/`applyBannerArtwork`/`requestLocalCoverFallback`/`applyDeterministicMetadata`/`recordMetadataFailure`/`resetMetadataForRetry`/`applyEnrichment`(+4 fields) + update `GameTest`/`GameTestBuilder` in `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/domain/`
- [x] T006 [P] `GameInstallationRepository` in `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/domain/repository/GameInstallationRepository.java` (`findBySourceIdAndExternalRef`, `findAllBySourceId`, `findByPresenceAndUninstalledAtBefore`, `countInstalledBySourceId`, `deleteByGameId`) (depends T004)
- [x] T007 [P] `GameRepository` visibility reshape in `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/domain/repository/GameRepository.java`: `findVisibleGames`/`findVisibleById`/`findVisiblePlatforms`/`findForChatFilter`/enrichment+metadata status queries switch to `EXISTS (installation INSTALLED on enabled source)` joins (same filter params as today) (depends T005)
- [x] T008 `ReconciliationService` in `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/service/ReconciliationService.java`: per-source snapshot applies to installations (find → upsert installation → else create `Game` + first installation, same-source semantics); `purgeUninstalledGames` purges installations past grace and deletes orphan `Game` rows (+ local artwork files) (depends T006)
- [x] T009 `ScanService` + `ArtworkService` reshape: shrink guard + `processArtworkAfterSync` count/resolve via `GameInstallationRepository` in `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/service/{ScanService,ArtworkService}.java` (depends T008)
- [x] T010 [P] `GameQueryService` wire-compat pass in `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/service/GameQueryService.java`: `artwork*` response fields served from `cover_*` columns; detail `sourceKey` derived from the game's (single) installation (depends T007)
- [x] T011 Foundational green: update + run backend suite in `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/` — `ReconciliationServiceTest`, `ScanServiceTest`, `ArtworkServiceTest`, `GameQueryServiceTest`, `GameCatalogControllerTest`, `GameCatalogModuleTest`, `ModularityTests`; API responses byte-identical to pre-004 for existing fields (depends T008, T009, T010)

**Checkpoint**: Module green on the new model, single-host behavior + wire contract unchanged; user stories can proceed.

---

## Phase 3: User Story 1 — Card-fitted covers + wide detail banner (P1) 🎯 MVP

**Goal**: Portrait card covers (Steam `library_600x900` family; libretro `Named_Boxarts`) and a wide detail banner (Steam `library_hero`; libretro `Named_Snaps`), as two persisted artwork slots per data-model.md.

**Independent Test**: quickstart.md Story 1 — grid cards show portrait-fitted art (no `header.jpg` slivers); detail pages show wide banners (different from cover where available) with styled plates when missing.

- [x] T012 [US1] `ArtworkLookupClient` slot resolution in `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/rest/client/ArtworkLookupClient.java`: cover — Steam portrait (`library_600x900.jpg` then `library_600x900_2x.jpg`, derived from `steamAppId`), ROM `Named_Boxarts` (existing); banner — Steam `library_hero.jpg`, ROM `Named_Snaps` (reuse the repo map, HEAD-probe) + `ArtworkLookupClientTest` in `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/rest/client/ArtworkLookupClientTest.java`
- [x] T013 [US1] `ArtworkService` two-slot flow in `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/service/ArtworkService.java`: resolve cover + banner per slot; local-fallback chain stays cover-only; `ArtworkServiceTest` additions (depends T012)
- [x] T014 [US1] API rename + banner fields per `contracts/catalog-api.md`: `GameSummaryResponse`/`GameDetailResponse` in `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/rest/controller/model/` (`coverStatus`/`coverUrl`/`coverEndpoint`, detail + `bannerStatus`/`bannerUrl`/`bannerEndpoint`) + `GameQueryService`/`GameCatalogControllerTest` updates in `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/service/GameQueryService.java` and `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/rest/controller/GameCatalogControllerTest.java` (depends T013)
- [x] T015 [US1] Frontend model rename + banner fields in `jordylab-fe/libs/gamecatalog/api/src/lib/gamecatalog.models.ts` + `artworkUrl` → `coverUrl`/`bannerUrl` helpers in `jordylab-fe/libs/gamecatalog/api/src/lib/gamecatalog-api.service.ts` + mocks in `jordylab-fe/libs/gamecatalog/api/src/lib/mocks/{game-summary,game-detail,games-page}.model.mock.ts` (depends T014)
- [x] T016 [US1] Grid card cover slot in `jordylab-fe/libs/gamecatalog/ui/src/lib/game-grid/game-grid-view.component.{html,ts}` (portrait `aspect-[2/3]` unchanged; cover fields + placeholder plate path) (depends T015)
- [x] T017 [US1] Detail banner slot in `jordylab-fe/libs/gamecatalog/ui/src/lib/game-detail/game-detail-view.component.{html,ts}`: wide hero-ratio banner at top with plate fallback (never a stretched cover); cover art stays in the layout below (depends T015)
- [x] T018 [P] [US1] Frontend specs updated: `jordylab-fe/libs/gamecatalog/ui/src/lib/game-grid/game-grid.component.spec.ts`, `jordylab-fe/libs/gamecatalog/ui/src/lib/game-detail/game-detail.component.spec.ts`, `jordylab-fe/libs/gamecatalog/api/src/lib/{game-library,game-detail}.store.spec.ts`, `jordylab-fe/libs/gamecatalog/api/src/lib/gamecatalog-api.service.spec.ts` (depends T016, T017)

**Checkpoint**: Story 1 independently done — verify via quickstart.md Story 1 (grid + detail + no-op sync leaves slots unchanged).

---

## Phase 4: User Story 2 — One game, many hosts (P2)

**Goal**: Cross-host adoption (Steam: platform + `steamAppId`; ROM: platform + normalized title) — a new host adds a `GameInstallation` link only: no duplicate entry, no re-enrichment, no artwork reset, no full refresh (FR-005 is structural).

**Independent Test**: quickstart.md Story 2 — same Steam game from two hosts → one card, two `hosts[]`, enrichment/cover/metadata byte-identical; per-host uninstall keeps the game visible via the other host.

- [x] T019 [US2] Adoption lookups in `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/domain/repository/GameRepository.java`: `findByPlatformAndSteamAppId`, `findByPlatformAndLowercaseTitle` (platform-scoped, function-based lowercase compare) (depends T007)
- [x] T020 [US2] Cross-host adoption in `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/service/ReconciliationService.java`: before creating a `Game`, match Steam entries on platform+appid and ROM entries on platform+normalized title; on match upsert the source's installation and never touch the matched `Game` (no enrichment/artwork/metadata reset) (depends T019)
- [x] T021 [US2] Detail `hosts[]` per `contracts/catalog-api.md`: `GameDetailResponse` gains `hosts: [{hostname, sourceType}]` (replaces `sourceKey`) in `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/rest/controller/model/GameDetailResponse.java` + `GameQueryService` mapping from installed installations' sources (depends T020)
- [x] T022 [P] [US2] Backend story tests in `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/`: `ReconciliationServiceTest` adoption matrix (two sources → one game, zero enrichment resets), `GameQueryServiceTest` hosts visibility, `GameCatalogModuleTest` two-host end-to-end incl. per-host uninstall + orphan purge + disabled-source per-host (depends T020)
- [x] T023 [P] [US2] Frontend hosts row: `GameDetail.hosts` in `jordylab-fe/libs/gamecatalog/api/src/lib/gamecatalog.models.ts` + mocks + spec-sheet "Hosts" row in `jordylab-fe/libs/gamecatalog/ui/src/lib/game-detail/game-detail-view.component.html` + `game-detail.component.spec.ts` update (depends T021)

**Checkpoint**: Stories 1 + 2 independently working — one game, many hosts, no refresh.

---

## Phase 5: User Story 3 — Host filter on the overview (P3)

**Goal**: `GET /games?host=` + `GET /hosts` (AND-combined with search/platform) and host filter chips in the grid.

**Independent Test**: quickstart.md Story 3 — host chips narrow the grid to that host's games; combined filters AND; a game on two hosts appears in both host-filtered lists (needs ≥2 synced hosts for a meaningful check).

- [x] T024 [US3] Hosts endpoint: `findVisibleHosts` + host param on `findVisibleGames` in `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/domain/repository/GameRepository.java`; `getHosts` + `host` filter in `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/service/GameQueryService.java`; `HostsResponse` model + `GET /api/gamecatalog/hosts` + `host` param in `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/rest/controller/{GameCatalogController,model/HostsResponse}.java` + `GameCatalogControllerTest`/`GameQueryServiceTest` additions (depends T007)
- [x] T025 [P] [US3] Frontend API + store: `getGames(query.host)` + `getHosts()` in `jordylab-fe/libs/gamecatalog/api/src/lib/gamecatalog-api.service.ts`; `selectedHost` + `hosts` signals + `selectHost()` in `jordylab-fe/libs/gamecatalog/api/src/lib/game-library.store.ts`; `HostsResponse`-shaped mocks in `jordylab-fe/libs/gamecatalog/api/src/lib/mocks/` + store/service spec updates (depends T024)
- [x] T026 [US3] Host chips row in `jordylab-fe/libs/gamecatalog/ui/src/lib/game-grid/game-grid-view.component.{html,ts}` (chip row alongside platform chips, `All` default) + `selectedHost`/`hostChange` wiring in `jordylab-fe/libs/gamecatalog/ui/src/lib/game-grid/game-grid.component.ts` + `game-grid.component.spec.ts` update (depends T025)

**Checkpoint**: Host filter live on the overview; stories 1–3 all pass their independent tests.

---

## Phase 6: User Story 4 — Richer deterministic detail data (P4)

**Goal**: `genres`/`developer`/`publisher`/`releaseYear` persisted per data-model.md — Steam games from Steam store appdetails (deterministic), ROM games from the extended enrichment prompt — rendered in the spec sheet and filterable by the grounded chat.

**Independent Test**: quickstart.md Story 4 — Steam detail fields match the Steam store page (`metadataSource: "STEAM"`); ROM fields appear post-enrichment; appdetails outage degrades to omitted fields; chat answers year/developer/genre/host questions from structured fields only.

- [x] T027 [P] [US4] `metadata()` config record on `GameCatalogProperties` in `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/GameCatalogProperties.java` (+ defaults in `jordylab-be/src/main/resources/application.yaml`, `GameCatalogPropertiesTest`)
- [x] T028 [P] [US4] `SteamAppDetailsClient` in `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/rest/client/SteamAppDetailsClient.java`: RestClient `GET store.steampowered.com/api/appdetails?appids={steamAppId}&filters=basic`; parse + bound/validate `genres[]`→`genres` (≤200), `developers[0]`→`developer` (≤100), `publishers[0]`→`publisher` (≤100), `release_date.date`→`releaseYear` (1950..2028) + WireMock tests `SteamAppDetailsClientTest` in `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/rest/client/SteamAppDetailsClientTest.java`
- [x] T029 [US4] `SteamMetadataService` in `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/service/SteamMetadataService.java`: scheduled batch over Steam games with `metadataStatus = PENDING` (batch size/attempts from T027, daily FAILED reset mirroring `EnrichmentService`), applies via `applyDeterministicMetadata` (depends T028)
- [x] T030 [US4] Extended enrichment in `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/service/EnrichmentService.java`: system prompt + parse/validate gain `genres`/`developer`/`publisher`/`releaseYear` (same bounds), `applyEnrichment` sets them + `metadataStatus = OK` for ROM games + `EnrichmentServiceTest` additions
- [x] T031 [US4] Detail response + contract: `genres`/`developer`/`publisher`/`releaseYear`/`metadataSource` on `GameDetailResponse` in `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/rest/controller/model/GameDetailResponse.java` + `GameQueryService` mapping + `GameCatalogControllerTest` additions (depends T029, T030)
- [x] T032 [US4] Chat grounded-filter extension in `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/service/ChatService.java` + `GameRepository.findForChatFilter` in `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/domain/repository/GameRepository.java`: translation prompt + strict-JSON fields gain `genresSearch`, `developerSearch`, `releaseYearMin`, `releaseYearMax`, `hosts` (validated against visible hosts); composition rows include the new fields + `ChatServiceTest`/`GameCatalogModuleTest` additions (depends T030, T024)
- [x] T033 [P] [US4] Frontend spec-sheet rows: metadata fields + provenance in `jordylab-fe/libs/gamecatalog/api/src/lib/gamecatalog.models.ts` + `game-detail.model.mock.ts` + spec-sheet `Genre(s)`/`Developer`/`Publisher`/`Released` rows (value present → row, null → omitted) in `jordylab-fe/libs/gamecatalog/ui/src/lib/game-detail/game-detail-view.component.html` + `game-detail.component.spec.ts` update (depends T031)

**Checkpoint**: Deterministic data visible on Steam + ROM details and answerable through chat.

---

## Phase 7: User Story 5 — "Ask the catalog" with the game attached (P5)

**Goal**: `POST /chat` carries `gameIds`; the answer composes with the attached games' full rows (always cited, no-match suppressed); the detail button shows the spark icon and opens chat with the game attached as a removable chip.

**Independent Test**: quickstart.md Story 5 — spark-icon button → chat with attachment chip; "is this good for 4 players on the couch?" cites the attached game and uses its facts; removing the chip restores plain catalog-wide chat; invisible/oversized ids → `400 GAME_IDS_INVALID`.

- [x] T034 [US5] `ChatRequest.gameIds` + validation in `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/rest/controller/model/ChatRequest.java` + `GameCatalogController` (`GAME_IDS_INVALID` 400 for >5/invisible/malformed) in `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/rest/controller/GameCatalogController.java` + `GameCatalogControllerTest` additions
- [x] T035 [US5] Attachment context in `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/service/ChatService.java`: load visible attached games; "Attached games" section (full rows incl. hosts + metadata) ahead of grounded rows in the composition prompt; cited refs = attachments ∪ filter rows; suppress `NO_MATCH` when attachments exist + `ChatServiceTest` additions (depends T034)
- [x] T036 [P] [US5] Frontend API: `chat(question, gameIds)` in `jordylab-fe/libs/gamecatalog/api/src/lib/gamecatalog-api.service.ts` + models + `chat-answer.model.mock.ts` + `gamecatalog-api.service.spec.ts` update (depends T034)
- [x] T037 [US5] Attachment state in `jordylab-fe/libs/gamecatalog/api/src/lib/game-chat.store.ts`: `attachedGame` (set from `?attach=` param via `GameDetailStore`/API load, `clearAttachedGame()`), sent as `gameIds` with every ask while attached + `game-chat.store.spec.ts` update (depends T036)
- [x] T038 [P] [US5] Spark icon + attachment navigation on the detail button in `jordylab-fe/libs/gamecatalog/ui/src/lib/game-detail/game-detail-view.component.html`: spark SVG (four-pointed star path `M12 3l2 6 6 2-6 2-2 6-2-6-6-2 6-2z`, same shape as the host app's briefing icon) + `routerLink` to `/games/chat?attach={game.id}` (depends T036)
- [x] T039 [US5] Attachment chip UI in `jordylab-fe/libs/gamecatalog/ui/src/lib/game-chat/game-chat-view.component.{html,ts}` (chip above the input: cover-dot palette + title + remove `×`; disabled state while asking) + `attachedGame`/`removeAttachment` wiring in `jordylab-fe/libs/gamecatalog/ui/src/lib/game-chat/game-chat.component.ts` + `game-chat.component.spec.ts` update (depends T037)

**Checkpoint**: All five stories pass their independent tests end-to-end.

---

## Phase 8: Polish & Cross-Cutting Concerns

- [x] T040 [P] Update ops docs for what changed: `jordylab-be/AGENTS.md` (two artwork slots + resolution order, adoption keys, `SteamMetadataService`, `GameInstallation` model) and `specs/002-game-catalog/contracts/catalog-api.md` superseded-by note pointing at `specs/004-gamecatalog-refinements/contracts/catalog-api.md`
- [x] T041 Coverage + regression gates: JaCoCo 80% for `gamecatalog` changes + full backend `./gradlew :test --tests "*GameCatalog*" --tests "*ModularityTests*"` + frontend `bunx nx run-many -t test --projects=gamecatalog-api,gamecatalog-ui` + `bunx nx run-many -t lint` in `jordylab-fe/`
- [x] T042 End-to-end per `specs/004-gamecatalog-refinements/quickstart.md`: migration applies on existing dev data (backfill counts match); fresh scan from one host (003 flow intact); second-host adoption; host filter; deterministic fields; chat attachment incl. negative cases

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 Setup**: none — baseline must be green before Phase 2
- **Phase 2 Foundational**: blocks ALL stories (single migration + entity split; module must stay green and wire-compatible)
- **Phases 3–7 (US1–US5)**: each depends on Phase 2 only; US2→US3→US4 share files sequentially within their phase but stories are independently testable
- **Phase 8 Polish**: after all implemented stories

### User Story Dependencies

- **US1 (P1)**: Phase 2 only — no story dependencies (MVP)
- **US2 (P2)**: Phase 2 only (T020 is behavior-additive on T008)
- **US3 (P3)**: Phase 2 + US2 for a *meaningful* test (filter mechanically works with one host)
- **US4 (P4)**: Phase 2 + T024 (`findVisibleHosts` for chat host validation)
- **US5 (P5)**: Phase 2; richer context benefits from US4 fields but works without them

### Within Each User Story

- Entity/repo changes before service changes; services before controllers; backend contract before frontend mocks/views; component changes before spec updates
- Same-file tasks (GameRepository, ChatService, GameQueryService appear across stories) must run in phase order — they are never marked [P] across stories

### Parallel Opportunities

- T004 ∥ T005 (separate entity files) after T003; T006 ∥ T007 after their entities
- T022 ∥ T023 (US2 backend tests ∥ frontend hosts row); T025 ∥ T027 ∥ T028 (different files, once T024 lands); T033 ∥ T034-T035 chain start; T036 ∥ T038 once T034 lands
- Frontend and backend work within a story can interleave once the contract task (T014/T021/T024/T031/T034) is done

## Parallel Example: User Story 4

```text
After T024 (hosts endpoint):
  Task T027 [P] [US4] metadata() config on GameCatalogProperties
  Task T028 [P] [US4] SteamAppDetailsClient + WireMock tests

Then:
  Task T029 [US4] SteamMetadataService (depends T027, T028)
  Task T030 [US4] EnrichmentService extension (parallel with T029 — different file)
```

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1 baseline green → Phase 2 foundational split (wire-compatible)
2. Phase 3 US1 (covers + banners) → **STOP and VALIDATE** quickstart Story 1 → deploy if ready

### Incremental Delivery

1. Setup + Foundational → module green on the new model
2. + US1 covers/banners → validate → demo (MVP)
3. + US2 multi-host adoption → validate (quickstart Story 2)
4. + US3 host filter → validate
5. + US4 deterministic metadata + chat fields → validate
6. + US5 chat attachment → validate
7. Phase 8 polish (docs, gates, end-to-end) — each story adds value without breaking previous ones

---

**Notes**

- [P] tasks = different files, no dependencies; same-file chains are ordered and never [P]
- Verify acceptance per story against quickstart.md before checking the story's checkpoint
- Commit after each task or logical group; never commit secrets (per root AGENTS.md)
