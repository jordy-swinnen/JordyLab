# Tasks: Nintendo Switch Games in the Game Catalog (Manual Add)

**Input**: Design documents from `/specs/009-switch-games/` (plan.md, spec.md, research.md, data-model.md,
contracts/, quickstart.md)

**Prerequisites**: plan.md (required), spec.md (required — 5 user stories), research.md, data-model.md, contracts/

**Tests**: Included — the spec's success criteria demand automated proof (SC-004 duplicate prevention, SC-005 purge
exclusion), the constitution requires entity tests + TestBuilders as definition of done, and the plan's test list
(IGDB client against WireMock, guest 403, scans/syncs/purges never touching manual games) is explicit. Test tasks
precede their implementation tasks within each story where practical (red → green).

**Organization**: Tasks grouped by user story (spec.md US1–US5, priority order) so each story is independently
implementable and testable. Repo skills are referenced where they apply (`/flyway-migration`, `/entity`,
`/test-builder`, `/angular-signal-store`, `/angular-test`, `/modularity-check`).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2)
- Include exact file paths in descriptions

## Path Conventions

Web app monorepo: backend = `jordylab-be/src/main/java/dev/jordy/jordylab/…` (+ `src/test/…`,
`src/main/resources/…`), frontend = `jordylab-fe/…` (libs, apps). Full layout in [plan.md](plan.md) → Project
Structure.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Baseline green + verify existing config is sufficient for the in-place extension

- [x] T001 Run baseline gamecatalog tests green (`./gradlew :jordylab-be:test --tests 'dev.jordy.jordylab.gamecatalog.*'`
  and `bunx nx test gamecatalog-api`, `bunx nx test gamecatalog-ui`) and read `jordylab-be/AGENTS.md` +
  `jordylab-fe/AGENTS.md` for module conventions — fixed pre-existing test failures by excluding Spring Boot 4
  security auto-config from `jordylab-be/src/test/resources/application.yaml` and installing missing Capacitor packages
- [x] T002 [P] Verify `.env.example` contains `IGDB_CLIENT_ID` and `IGDB_CLIENT_SECRET`; document them in
  `jordylab-be/.env.example` if absent (names only) — verified present
- [x] T003 [P] Verify spartan/ui primitives needed for dialogs are available (`hlm-input`, `hlm-button`, `hlm-badge`,
  `hlm-card`) in `jordylab-fe/libs/ui/helm/`; add any missing via `bunx @spartan-ng/cli@latest add <component>` if
  required — verified present

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Migration, domain authority, pseudo-host, repository changes — blocks all user stories

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [x] T004 Create Flyway migration
  `jordylab-be/src/main/resources/db/migration/V20260929001__gamecatalog_switch_games.sql`: add
  `igdb_game_id` to `game` with unique partial index, add `manual`/`format` to `game_installation`, insert the virtual
  "Nintendo Switch" `ScanSource` row — named with `001` suffix to preserve Flyway ordering against existing timestamped
  migrations
- [x] T005 Add `MANUAL` value to `TitleSource` enum with highest authority (`outranks` all others) in
  `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/domain/TitleSource.java`
- [x] T006 Add `SWITCH` value to `SourceType` enum (`platform()` returns `"Nintendo Switch"`) in
  `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/domain/SourceType.java`
- [x] T007 Update `Game` entity with `igdbGameId` field and `TitleSource.MANUAL` handling in
  `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/domain/Game.java`
- [x] T008 Update `GameInstallation` entity with `manual` and `format` fields plus `createManual` factory in
  `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/domain/GameInstallation.java`
- [x] T009 Add TestBuilder defaults for the updated entities (`GameTestBuilder`, `GameInstallationTestBuilder`) in
  `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/domain/`
- [x] T010 Write entity unit tests for builder guards and `TitleSource.MANUAL` authority in
  `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/domain/GameTest.java`,
  `GameInstallationTest.java`, `TitleSourceTest.java`
- [x] T011 Add `findByPlatformAndIgdbGameId` and manual-exclusion helpers to
  `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/domain/repository/GameRepository.java` and
  `GameInstallationRepository.java`
- [x] T012 Verified `findVisibleGames`/`findVisibleHosts` include Switch installations by construction (the virtual
  Switch source is a normal enabled scan source and manual installations are `INSTALLED`)

**Checkpoint**: Foundation ready — migration runs, entities build, repositories compile, tests pass. User stories can
now begin.

---

## Phase 3: User Story 1 — Add a Switch game by searching (Priority: P1) 🎯 MVP

**Goal**: Admin searches IGDB for a Switch title, picks a result, chooses physical/digital, and the game appears in the
catalog with metadata, artwork and AI description.

**Independent Test**: Search "mario kart", pick "Mario Kart 8 Deluxe", mark physical, save, and check it appears in the
grid with cover, and on its detail page with banner, genres, developer, release year and local-multiplayer info.

### Tests for User Story 1

- [x] T013 [P] [US1] WireMock test for `IgdbClient` Switch title search (platform 130 filter, unconfigured fallback) in
  `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/rest/client/IgdbClientTest.java`
- [x] T014 [P] [US1] WireMock test for `IgdbClient` cover/artwork/game-mode fetch and IGDB image URL construction in
  `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/rest/client/IgdbClientTest.java`
- [x] T015 [P] [US1] Unit test for `SwitchGameService` add-by-IGDB: duplicate prevention, metadata/artwork/multiplayer
  fill, enrichment once, `GameInstallation` + `GameLibraryEntry` creation in
  `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/service/SwitchGameServiceTest.java`
- [x] T016 [US1] `@WebMvcTest` for `SwitchGameController` search + add endpoints in
  `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/rest/controller/SwitchGameControllerTest.java`

### Implementation for User Story 1

- [x] T017 [P] [US1] Extend `IgdbClient` with Switch title search, cover/artwork/game-mode fetch, and IGDB image URL
  builder in `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/rest/client/IgdbClient.java`
- [x] T018 [P] [US1] Add Switch request/response DTO records (`SwitchSearchRequest`, `SwitchSearchResult`,
  `SwitchGameRequest`, `SwitchGameResponse`) in
  `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/rest/controller/model/`
- [x] T019 [US1] Implement `SwitchGameService` add-by-IGDB flow (resolve-or-create `Game`, fill metadata/artwork/
  multiplayer, create Switch installation + library entry) in
  `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/service/SwitchGameService.java`
- [x] T020 [US1] Implement `SwitchGameController` (`GET /switch/search`, `POST /switch/games`) in
  `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/rest/controller/SwitchGameController.java`
- [x] T021 [US1] Add Switch API methods to `GameCatalogApiService` in
  `jordylab-fe/libs/gamecatalog/api/src/lib/gamecatalog-api.service.ts`
- [x] T022 [P] [US1] Create `switch-game.store.ts` signal store (search state, selected result, format, loading/error)
  in `jordylab-fe/libs/gamecatalog/api/src/lib/switch-game.store.ts` — follow `/angular-signal-store`
- [x] T023 [P] [US1] Add Switch models (`SwitchGameFormat`, `SwitchSearchResult`) and mock factories in
  `jordylab-fe/libs/gamecatalog/api/src/lib/gamecatalog.models.ts` and `mocks/`
- [x] T024 [US1] Create `switch-game` container component (search input, result cards, format select,
  save button) in `jordylab-fe/libs/gamecatalog/ui/src/lib/switch-game/` — follow `/angular-test`
- [x] T025 [US1] Wire the **Add Switch game** route (`/games/switch`) into `gamecatalog.routes.ts`
- [x] T026 [US1] Add Spectator tests for `switch-game.store` and update `gamecatalog.routes.spec.ts` in
  `jordylab-fe/libs/gamecatalog/api/src/lib/switch-game.store.spec.ts` and
  `jordylab-fe/libs/gamecatalog/ui/src/lib/gamecatalog.routes.spec.ts`

**Checkpoint**: User Story 1 is fully functional and testable independently — admin can search and add a Switch game.

---

## Phase 4: User Story 4 — Switch games behave like any other game (Priority: P1)

**Goal**: Switch games appear in grid, detail, host/platform/library-source filters and chat; scans/syncs/purges never
rename or remove them.

**Independent Test**: Browse the grid, filter by "Nintendo Switch" host and platform, open detail, ask chat a Switch
question, run a scan, and verify Switch games survive.

### Tests for User Story 4

- [ ] T027 [P] [US4] Integration/repository test that Switch games are returned by `findVisibleGames` and
  `findVisibleHosts` in `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/repository/GameRepositoryTest.java`
- [ ] T028 [P] [US4] Test that `ReconciliationService.hideMissingInstallations` and `purgeUninstalledGames` skip manual
  installations in
  `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/service/ReconciliationServiceTest.java`
- [ ] T029 [US4] Test that grounded chat includes Switch games in its corpus in
  `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/service/ChatServiceTest.java` (or equivalent)

### Implementation for User Story 4

- [ ] T030 [US4] Confirm `GameRepository.findVisibleGames`/`findVisibleHosts` include the Switch host via the existing
  installation predicate; adjust query if necessary in
  `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/repository/GameRepository.java`
- [ ] T031 [US4] Add explicit `manual = true` exclusion guards in `ReconciliationService.hideMissingInstallations` and
  `purgeUninstalledGames` in
  `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/service/ReconciliationService.java`
- [ ] T032 [US4] Add `hostFormats` to `GameDetailResponse` and populate Switch format in
  `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/rest/controller/model/GameDetailResponse.java` and
  `GameCatalogController.java`
- [ ] T033 [US4] Display Switch format on the detail page (read-only for guests) in
  `jordylab-fe/libs/gamecatalog/ui/src/lib/game-detail/game-detail-view.component.html`
- [ ] T034 [US4] Verify host/platform filter chips show "Nintendo Switch" and the grid refreshes correctly (validate via
  frontend test if needed)

**Checkpoint**: User Stories 1 and 4 both work independently — Switch games are visible, filterable and safe from
scans.

---

## Phase 5: User Story 2 — Add a game the search can't find (Priority: P2)

**Goal**: Admin can add a game manually when IGDB search has no match, and link it to a match later.

**Independent Test**: Add a fictional title manually with placeholder art, then relink it to a real IGDB match.

### Tests for User Story 2

- [ ] T035 [P] [US2] Unit test for `SwitchGameService` manual add: normalised-title duplicate prevention, placeholder
  artwork, no metadata until linked in
  `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/service/SwitchGameServiceTest.java`
- [ ] T036 [US2] `@WebMvcTest` for manual add and relink endpoints in
  `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/rest/controller/SwitchGameControllerTest.java`

### Implementation for User Story 2

- [ ] T037 [US2] Implement manual add path in `SwitchGameService` (normalise title, create `Game` without `igdbGameId`,
  placeholder artwork, Switch installation + library entry) in
  `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/service/SwitchGameService.java`
- [ ] T038 [US2] Implement link-later PATCH `/switch/games/{id}` (set `igdbGameId`, fill metadata/artwork/multiplayer,
  do not regenerate AI description) in `SwitchGameController` + `SwitchGameService`
- [ ] T039 [US2] Add manual-fallback UI to `switch-add-dialog` (no-match state + manual form) in
  `jordylab-fe/libs/gamecatalog/ui/src/lib/switch-add-dialog/`
- [ ] T040 [US2] Add relink UI to the detail page (admin-only) in
  `jordylab-fe/libs/gamecatalog/ui/src/lib/game-detail/game-detail-view.component.*`

**Checkpoint**: User Stories 1, 2 and 4 are independently functional.

---

## Phase 6: User Story 3 — Add many games at once (Priority: P2)

**Goal**: Admin pastes a list of titles, reviews proposed matches, and confirms the selected ones in one action.

**Independent Test**: Paste 40 titles, review matches/no-matches/already-present, confirm, and check the summary.

### Tests for User Story 3

- [ ] T041 [P] [US3] Unit test for `SwitchGameService.bulkPreview` (match / no-match / already-present / duplicate line
  collapse) in `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/service/SwitchGameServiceTest.java`
- [ ] T042 [US3] `@WebMvcTest` for `POST /switch/bulk/preview` and `POST /switch/bulk/confirm` in
  `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/rest/controller/SwitchGameControllerTest.java`

### Implementation for User Story 3

- [ ] T043 [US3] Implement bulk preview logic (line normalisation, per-line IGDB search, duplicate detection, status
  classification) in `SwitchGameService`
- [ ] T044 [US3] Implement bulk confirm logic (add selected lines via US1/US2 flows, produce added/skipped/already-present
  summary) in `SwitchGameService`
- [ ] T045 [US3] Add `POST /switch/bulk/preview` and `POST /switch/bulk/confirm` endpoints in
  `SwitchGameController`
- [ ] T046 [US3] Create `switch-bulk-dialog` container + view components (paste textarea, review table with
  match/no-match/already-present, per-line/global format toggle, confirm) in
  `jordylab-fe/libs/gamecatalog/ui/src/lib/switch-bulk-dialog/`
- [ ] T047 [US3] Wire the bulk dialog into the game grid (admin-only button) in
  `jordylab-fe/libs/gamecatalog/ui/src/lib/game-grid/game-grid-view.component.*`

**Checkpoint**: All user stories 1–4 and bulk add are independently functional.

---

## Phase 7: User Story 5 — Edit and remove (Priority: P2)

**Goal**: Admin can change format, relink, or remove a Switch game; guests cannot.

**Independent Test**: Edit format, relink to a different match, remove a game, and confirm guests see no controls and
get 403 on API writes.

### Tests for User Story 5

- [ ] T048 [P] [US5] Unit test for `SwitchGameService` edit: format change does not regenerate AI description; relink
  refreshes metadata but not description in
  `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/service/SwitchGameServiceTest.java`
- [ ] T049 [US5] `@WebMvcTest` for `PATCH /switch/games/{id}`, `DELETE /switch/games/{id}`, and guest `403` in
  `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/rest/controller/SwitchGameControllerTest.java`

### Implementation for User Story 5

- [ ] T050 [US5] Implement format edit + relink in `SwitchGameService` (preserve `EnrichmentStatus`/`description`) in
  `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/service/SwitchGameService.java`
- [ ] T051 [US5] Implement `DELETE /switch/games/{id}` (delete Switch installation; conditionally delete library entry
  and game) in `SwitchGameController` + `SwitchGameService`
- [ ] T052 [US5] Add edit/remove controls on the detail page (admin-only; hidden for guests) in
  `jordylab-fe/libs/gamecatalog/ui/src/lib/game-detail/game-detail-view.component.*`
- [ ] T053 [US5] Add guest-403 assertions for Switch write endpoints in the role-matrix tests or
  `SwitchGameControllerTest`

**Checkpoint**: All five user stories are independently functional.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Quality gates, docs, and validation

- [x] T054 [P] Run backend gamecatalog tests and fix any regressions (`./gradlew :jordylab-be:test --tests 'dev.jordy.jordylab.gamecatalog.*'`)
- [x] T055 [P] Run frontend gamecatalog tests and fix any regressions (`bunx nx test gamecatalog-api`, `bunx nx test gamecatalog-ui`)
- [x] T056 Run `/modularity-check` — Spring Modulith boundary tests green (`./gradlew :jordylab-be:test --tests '*ModularityTests*'`)
- [ ] T057 Verify 80% backend/frontend coverage gates pass — blocked by pre-existing `mobile.service` JaCoCo gap (0.4 vs 0.8)
- [x] T058 Run quickstart.md validation scenarios against the local stack (documented in `specs/009-switch-games/validation-results.md`)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion — **BLOCKS all user stories**
- **User Stories (Phase 3+)**: All depend on Foundational phase completion
  - US1 (P1) must complete before US2/US3/US5 (they build on the add flow)
  - US4 (P1) can run in parallel with US1 once Foundational is done, but is shown after US1 here for clarity
  - US2, US3, US5 (P2) can run in parallel after US1
- **Polish (Phase 8)**: Depends on all desired user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: Foundational → no other story dependency
- **User Story 4 (P1)**: Foundational → no other story dependency
- **User Story 2 (P2)**: Foundational + US1 (shares add flow and dialog)
- **User Story 3 (P2)**: Foundational + US1 (bulk reuses single-add logic)
- **User Story 5 (P2)**: Foundational + US1 (edit/remove targets games added by US1)

### Within Each User Story

- Tests (if included) are written first and must fail before implementation is complete
- DTOs/client interfaces before tests that depend on them
- Models/services before endpoints
- Backend endpoints before frontend API integration
- Components before wiring them into existing views

### Parallel Opportunities

- All Setup tasks (T001–T003) can run in parallel
- All Foundational entity/repository tasks (T004–T012) can run in parallel once the migration design is agreed
- US1 test tasks (T013–T016) can run in parallel after T017/T018 interfaces exist
- US1 implementation tasks T017/T018/T022/T023 are parallel; T019–T021/T024–T025 depend on them
- US4 tests (T027–T029) run in parallel
- US2, US3 and US5 can be developed in parallel once US1 is complete

---

## Parallel Example: User Story 1

```bash
# Tests (after client DTOs/interfaces exist):
T013 IgdbClient WireMock Switch search
T014 IgdbClient image URL + artwork fetch
T015 SwitchGameService add-by-IGDB unit test
T016 SwitchGameController @WebMvcTest

# Models/DTOs/interfaces (first):
T017 IgdbClient Switch search + image URL builder
T018 Switch DTO records
T022 switch-game.store.ts
T023 Switch models + mocks

# Implementation:
T019 SwitchGameService add flow
T020 SwitchGameController
T021 GameCatalogApiService methods
T024 switch-add-dialog components
T025 Wire button into grid
T026 Spectator tests
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational
3. Complete Phase 3: User Story 1
4. **STOP and VALIDATE**: Test search + add independently
5. Deploy/demo if ready

### Incremental Delivery

1. Setup + Foundational → Foundation ready
2. US1 → Test independently → Deploy/Demo (MVP!)
3. US4 → Test independently (visibility + purge safety)
4. US2 → Test independently (manual fallback)
5. US3 → Test independently (bulk paste)
6. US5 → Test independently (edit/remove + guest 403)
7. Polish → quickstart validation

### Parallel Team Strategy

With multiple developers:

1. Team completes Setup + Foundational together
2. Once Foundational is done:
   - Developer A: US1 + US4
   - Developer B: US2 + US5
   - Developer C: US3
3. Stories complete and integrate independently

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Each user story should be independently completable and testable
- Verify tests fail before implementing
- Stop at any checkpoint to validate a story independently
- Avoid vague tasks, same-file conflicts, and cross-story dependencies that break independence
