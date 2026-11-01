---

description: "Task list for console player counts from a second source"
---

# Tasks: Console Player Counts from a Second Source

**Input**: Design documents from `specs/014-console-player-counts/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/), [quickstart.md](quickstart.md)

**Tests**: Included. The project constitution (IV. Testing Discipline) and `jordylab-be/AGENTS.md` make tests part of done for every changed class; each story phase lists its tests, written to fail before the code they cover.

**Organization**: Grouped by user story. US2 (the engine) comes before US1 (what LibBot gains) because US1 is only observable once US2 fills counts; both are P1. US5 (safety hardening) is P3 but **must ship together with US2**: do not release US2 without US5 (see Dependencies).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an unfinished task)
- **[Story]**: US1–US5 from [spec.md](spec.md); none for Setup, Foundational and Polish
- Paths are relative to the repository root; backend sources are under `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/` (shortened to `…/gamecatalog/` below), tests under `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/` (`…/gamecatalog-test/`)

## Rules that apply to every task

- Java: never `var`, a blank line before every `return`, Lombok (`@RequiredArgsConstructor`, `@Slf4j`), records for DTOs, no hand-written constructors, full names. Method order: public, package-private, private.
- Tests: `assertSoftly` for several assertions, never Mockito `any()`, `@Language("JSON")` on inline JSON, descriptive names, `GameTestBuilder` fixtures.
- Counts only from a source's own field; never hand-seed any database; synthetic data lives in test code only.
- The key (`THEGAMESDB_API_KEY`) and any request URI or response body from TheGamesDB never reach a log, an exception message that is rethrown, a response, a file or a commit (TheGamesDB echoes the key in its pagination URLs).
- Every commit carries `Refs: 014` (or `014/T###`) as a git trailer.

---

## Phase 1: Setup

**Purpose**: configuration and tooling the other phases build on

- [ ] T001 Add the `jordylab.gamecatalog.thegamesdb` block to `jordylab-be/src/main/resources/application.yaml` next to the existing `igdb` settings: `base-url: https://api.thegamesdb.net/v1`, `timeout-ms: 5000`, `min-interval-ms: 1000`, `allowance-reserve: 20` (the key itself is the `THEGAMESDB_API_KEY` environment variable, already named in `jordylab-be/.env.example`)
- [ ] T002 Add the opt-in `theGamesDbCheck` task to `jordylab-be/build.gradle.kts` (mirror `igdbCheck`: `includeTags("integration-thegamesdb")`) and add `"integration-thegamesdb"` to the `excludeTags(...)` list of the default `test` task (currently `"golden-live", "integration-igdb"`) so the live check never runs in CI
- [ ] T003 [P] Register the owner handoffs in `docs/testing/e2e-test-plan.md` after HANDOFF-35: **HANDOFF-36** (the local end-to-end of `specs/014-console-player-counts/quickstart.md` §2 and the `goldenLive` run; the owner decided on 2026-11-01 that the agent does these itself with the keys in the owner's `jordylab-be/.env`, so it is logged as done by the agent, not as an owner action) and **HANDOFF-37** (add `THEGAMESDB_API_KEY` to `deploy/k8s/overlays/prod/secrets.sops.yaml` with `SOPS_EDITOR="idea --wait" sops …`: **done by the owner on 2026-11-01**; record it as done)

---

## Phase 2: Foundational (blocks every user story)

**Purpose**: the data shape every story reads or writes. **No user story starts before this phase is done.**

- [ ] T004 Create `jordylab-be/src/main/resources/db/migration/V20261102001__gamecatalog_player_count_source.sql` exactly as in [data-model.md](data-model.md): `CREATE SCHEMA IF NOT EXISTS gamecatalog; SET search_path TO gamecatalog;`, add `max_local_players_source TEXT` and `player_count_gap TEXT` to `game`, backfill `'IGDB'` where `max_local_players IS NOT NULL`
- [ ] T005 [P] Create enum `PlayerCountSource { IGDB, THEGAMESDB }` in `…/gamecatalog/domain/PlayerCountSource.java`
- [ ] T006 [P] Create enum `PlayerCountGap { NO_MATCH, ENTRIES_DISAGREE, COUNT_BELOW_TWO, CONSOLE_NOT_LISTED }` in `…/gamecatalog/domain/PlayerCountGap.java`
- [ ] T007 Extend `…/gamecatalog/domain/Game.java` (after T005, T006): add `@Enumerated(EnumType.STRING)` fields `maxLocalPlayersSource` and `playerCountGap`; make `applyDeterministicMultiplayer` and `applyFillOnlyMultiplayer` set `maxLocalPlayersSource = IGDB` and clear `playerCountGap` whenever they store a non-null count; add `applyPlayerCount(int, PlayerCountSource)` (fill-only: no effect when a count exists; sets count and source, clears the gap, resets `multiplayerAttempts`) and `recordPlayerCountGap(PlayerCountGap)` (no effect when a count exists); wire the new fields through the `@AllArgsConstructor` and the partial builder's `build()` without changing the 1–64 guard
- [ ] T008 [P] Update `…/gamecatalog-test/domain/GameTestBuilder.java` with defaults for the two new fields (public constants at the top, `aDefaultGame()` unchanged in shape)
- [ ] T009 [P] Extend `…/gamecatalog-test/domain/GameTest.java` (after T007, T008): IGDB path sets source `IGDB`; `applyPlayerCount` fills a null count with `THEGAMESDB` and clears the gap; it never overwrites an existing count or its source; `recordPlayerCountGap` is ignored when a count exists; invariant "source null ⇔ count null" after each method; the existing `EqualsVerifier` test still passes
- [ ] T010 Verify the migration and backfill by running `cd jordylab-be && ./gradlew test --tests "*GameCatalogModuleTest*" --tests "*GameTest*"` (Testcontainers; see the Podman environment variables in `jordylab-be/AGENTS.md`); fix until green

**Checkpoint**: the schema, the enums and the entity methods exist and are tested.

---

## Phase 3: User Story 2 — Counts are filled automatically, correctly and only when sure (P1) 🎯 engine

**Goal**: console games flagged local multiplayer that IGDB left without a count get one from TheGamesDB, only when every matching entry agrees; everything doubtful stays unknown with a stored reason; nothing else changes.

**Independent test**: with the client stubbed by WireMock, the 15-title shapes from the measurement behave as in the spec (agreeing entries stored, Mario Party 7's 7/7/4 and All-Stars' 4/2 left unknown, a count of 1 ignored, a Steam game never asked).

### Tests for User Story 2 (write first, expect them to fail)

- [ ] T011 [P] [US2] Create `…/gamecatalog-test/rest/client/TheGamesDbClientTest.java` (`@WireMockTest(httpPort = 9993)`, free port): exact-key match with one agreed value → `FOUND`; the bracketed-variant case (7, 7 and a bracketed `[… Mic Included]` 4 → `ENTRIES_DISAGREE`); a differently named bundle is ignored; entries without `players` are ignored; only entries on the requested platform count; single value 1 → `COUNT_BELOW_TWO`; no matching title → `NO_MATCH`; console without a mapped id → `CONSOLE_NOT_LISTED` and no HTTP call; a second page is followed only when `pages.next` is set and at most three pages; request carries `fields=players`, `filter[platform]`, the key and the honest `User-Agent`; unconfigured → `DEFERRED` without a call
- [ ] T012 [P] [US2] Extend `…/gamecatalog-test/service/MultiplayerServiceTest.java`: order Steam categories → IGDB → TheGamesDB; the third step runs only for a game with no Steam app ID, `localMultiplayer == true` and a still-null count; a Steam game and a game with an unknown or false flag never call the client; `FOUND` stores count and source `THEGAMESDB`; a `GAP` stores the reason and counts an attempt; `DEFERRED` stores nothing and counts no attempt; a game on two consoles combines results (all equal → value, two different values → `ENTRIES_DISAGREE`); the lookup is cached per (title key, console) within one batch; an existing count is never overwritten; bulk `rederive` resets attempts and looks the game up again; per-game `refresh` uses the same path

### Implementation for User Story 2

- [ ] T013 [US2] Create `…/gamecatalog/rest/client/TheGamesDbClient.java` following the `IgdbClient` pattern (`@Component`, `@RequiredArgsConstructor`, package-private `@Value` fields for `THEGAMESDB_API_KEY` and the four `jordylab.gamecatalog.thegamesdb.*` settings, `@PostConstruct void init()` building the `RestClient` with timeouts, `isConfigured()`, call pacing). Implement the fixed console → TheGamesDB id map from [research.md](research.md) R4 keyed by `PlatformCatalog` names, `lookup(titleKey/title, console)` returning a `PlayerCountLookup` record (`outcome` FOUND/GAP/DEFERRED, `maxLocalPlayers`, `gap`) per [contracts/thegamesdb-lookup.md](contracts/thegamesdb-lookup.md) reading rules 1–6, matching with `TitleKeys.keyFor` only, and one INFO line at start-up when unconfigured. Add a read-only `state()` returning a nested `ClientState` record (`configured`, `pausedUntil`, `pauseReason`, `remainingAllowance`; the unconfigured/empty state until T038 fills it in). Log nothing but exception class names or HTTP status; never the URI, key or a body
- [ ] T014 [US2] Update `…/gamecatalog/service/MultiplayerService.java` (after T013): inject `TheGamesDbClient` and `GamePlatformService`; make `deriveOne` return an outcome (`RESOLVED`, `UNRESOLVED`, `DEFERRED`) instead of a boolean; add the third step from [research.md](research.md) R8 (eligibility, per-console combination, per-batch cache keyed by title key and console, `game.applyPlayerCount` / `game.recordPlayerCountGap`); count a failed attempt only for `UNRESOLVED`; keep `derivePending`, `refresh` and `rederive` signatures so `CatalogAutoFillService`, `RefreshRunService` and `ConsoleGameService` need no change
- [ ] T015 [US2] Run `cd jordylab-be && ./gradlew test --tests "*TheGamesDbClientTest" --tests "*MultiplayerServiceTest" --tests "*GameTest" --tests "*ModularityTests*"`; fix until green

**Checkpoint**: US2 works and is testable on its own with stubbed responses. **Not releasable without Phase 7 (US5).**

---

## Phase 4: User Story 1 — LibBot judges more console games for a party (P1) 🎯 MVP outcome

**Goal**: console games with a stored count are judged against the party size, so the "player count isn't known yet" number drops.

**Independent test**: with the library fixture of [spec.md](spec.md) US1, a 4-person question confirms or excludes the newly counted games and names fewer unknown ones; a still-unknown game keeps the note.

- [ ] T016 [P] [US1] Extend `…/gamecatalog-test/service/libbot/CandidateRetrieverTest.java` and `FactMatcherTest.java`: a game with `maxLocalPlayers` from `THEGAMESDB` is `CONFIRMED` for a party it fits and `EXCLUDED` for one it does not; the unknown-count number drops by one per game that gained a count; a game left without a count is still reported as unknown. Expect no production-code change (LibBot reads `maxLocalPlayers` regardless of source); if a test fails, fix the cause in `…/gamecatalog/service/libbot/`
- [ ] T017 [US1] Run `cd jordylab-be && ./gradlew test --tests "*libbot*" --tests "*GameFilterQueryTest*"`; fix until green
- [ ] T018 [US1] Run the local end-to-end of [quickstart.md](quickstart.md) §2 (steps 2–5) against the running dev stack and the owner's real library (HANDOFF-36, owner-approved for the agent). First back up the dev database with the `pg_dump` command of the quickstart prerequisites, never stop, remove or recreate the `jordylab-be-*` containers or the volume, and read keys from `jordylab-be/.env` without printing them. Starting the backend applies the Flyway migration to the dev database; that is the intended rehearsal. Report the numbers. Steps 6 and 7 (the LibBot question, the log check) wait until US3's fact line exists, see T045

**Checkpoint**: US1 + US2 together are the MVP: counts are filled and LibBot uses them.

---

## Phase 5: User Story 3 — The owner can see where each count came from (P2)

**Goal**: the detail page and LibBot's citations name the source of a count, separately from the source of the flags.

**Independent test**: a game filled by TheGamesDB and one filled by IGDB each show their own source for the count in the API and on the detail page.

### Tests for User Story 3 (write first)

- [ ] T019 [P] [US3] Extend `…/gamecatalog-test/service/GameQueryServiceTest.java` and `…/gamecatalog-test/rest/controller/GameCatalogControllerTest.java`: detail returns `maxLocalPlayersSource` and `factSources.playerCount`, both `null` when the count is `null`, `multiplayerSource` unchanged
- [ ] T020 [P] [US3] Extend `…/gamecatalog-test/service/libbot/AnswerComposerTest.java`: the fact line reads `up to 4 local players (TheGamesDB)` / `(IGDB)` and carries no source text when the count is unknown

### Implementation for User Story 3

- [ ] T021 [P] [US3] Add `maxLocalPlayersSource` to `…/gamecatalog/rest/controller/model/GameDetailResponse.java` and `playerCount` to `…/gamecatalog/rest/controller/model/FactSourcesResponse.java` (both `PlayerCountSource`, nullable); map them in `…/gamecatalog/service/GameQueryService.java` (the `FactSourcesResponse` construction near line 187)
- [ ] T022 [P] [US3] Carry the source into LibBot: add `maxLocalPlayersSource` to `…/gamecatalog/service/libbot/Candidate.java`, set it in `CandidateRetriever.java` (builder near line 230) and make `AnswerComposer.java` (near line 59) write `up to N local players (<source name>)`
- [ ] T023 [P] [US3] Frontend models: add `maxLocalPlayersSource` to the game detail and `playerCount` to `FactSources` in `jordylab-fe/libs/gamecatalog/api/src/lib/gamecatalog.models.ts` (and a `PlayerCountSource` type); update `jordylab-fe/libs/gamecatalog/api/src/lib/mocks/game-detail.model.mock.ts`
- [ ] T024 [US3] Show the count with its source on the detail page in `jordylab-fe/libs/gamecatalog/ui/src/lib/game-detail/game-detail-view.component.html` (e.g. `4 · TheGamesDB`), keep the "Multiplayer data" row naming the flags' source (it must stop being read as the count's source); extend `jordylab-fe/libs/gamecatalog/ui/src/lib/game-detail/game-detail.component.spec.ts` (Spectator, `useValue` + `vi.fn()`, real signals)
- [ ] T025 [US3] Run `cd jordylab-be && ./gradlew test --tests "*GameQueryServiceTest" --tests "*GameCatalogControllerTest" --tests "*libbot*"` (covers `AnswerComposerTest`, `CandidateRetrieverTest` and `GoldenReplayTest` after the `Candidate` change in T022) and `cd jordylab-fe && bunx nx test gamecatalog-ui && bunx nx test gamecatalog-api`; fix until green

**Checkpoint**: sources are visible everywhere a count is shown.

---

## Phase 6: User Story 4 — The Sources page shows how well each source does (P2)

**Goal**: per source, how many games were filled, how many are unknown and why, the state of the second source, and the games behind each number.

**Independent test**: after a bulk refresh the page shows the numbers of [contracts/catalog-api-changes.md](contracts/catalog-api-changes.md); "Entries disagree" lists the disagreeing games; the numbers and the lists come from the same predicates.

### Tests for User Story 4 (write first)

- [ ] T026 [P] [US4] Extend `…/gamecatalog-test/domain/repository/GameRepositoryTest.java` or add `LibraryHealthRepositoryTest.java` (Testcontainers, synthetic games built with `GameTestBuilder`): counts of visible local-multiplayer games by `max_local_players_source`; unknown by reason (`STEAM_NO_SOURCE`, the four gaps, `NOT_LOOKED_UP_YET`) add up to `unknown`; games with an unknown flag are excluded; each list uses the same predicate as its count
- [ ] T027 [P] [US4] Extend `…/gamecatalog-test/service/LibraryHealthServiceTest.java`: the `playerCounts` block includes the client's state (configured, `pausedUntil`, `pauseReason`, `remainingAllowance`); `PLAYER_COUNT` exceptions list with and without `reason`; the existing kinds return `reason: null`; an unknown `reason` is rejected
- [ ] T028 [P] [US4] Extend `…/gamecatalog-test/rest/controller/` (the controller test that covers `/sources/health/exceptions`, or a new `ScanSourceControllerTest`): `kind=PLAYER_COUNT&reason=ENTRIES_DISAGREE` returns the contract shape; an unknown `reason` → `400`; guests are refused (admin only)

### Implementation for User Story 4

- [ ] T029 [US4] Add the player-count counts and lists to `…/gamecatalog/domain/repository/LibraryHealthRepository.java` (JPQL fragments for the reasons built on `VISIBLE`, mirroring `NO_COVER`/`NO_DESCRIPTION`)
- [ ] T030 [P] [US4] Add the `PlayerCounts` record (nested, per the contract) to `…/gamecatalog/rest/controller/model/LibraryHealthResponse.java` and a nullable `reason` to `HealthException` in `…/gamecatalog/rest/controller/model/HealthExceptionsResponse.java`
- [ ] T031 [US4] After T030, update every existing constructor call and test fixture for the new record components (`LibraryHealthResponse.PlayerCounts`, `HealthException.reason`): search for `new LibraryHealthResponse(`, `new HealthExceptionsResponse(` and `new HealthException(` in `jordylab-be/src/main` and `jordylab-be/src/test` (for example `…/gamecatalog/service/ScanSourceService.java`) and make them compile
- [ ] T032 [US4] Extend `…/gamecatalog/service/LibraryHealthService.java` (`Kind.PLAYER_COUNT`, `health()` builds the block, `exceptions(kind, reason)`) and `…/gamecatalog/rest/controller/ScanSourceController.java` (optional `reason` request parameter on `/health/exceptions`; `400` for an unknown value)
- [ ] T033 [P] [US4] Frontend: add `PlayerCounts`, the new `HealthKind` value and `reason` to `jordylab-fe/libs/gamecatalog/api/src/lib/gamecatalog.models.ts`; extend `getHealthExceptions(kind, reason?)` in `jordylab-fe/libs/gamecatalog/api/src/lib/gamecatalog-api.service.ts`, `jordylab-fe/libs/gamecatalog/api/src/lib/scan-source.store.ts` and its spec, and `jordylab-fe/libs/gamecatalog/api/src/lib/mocks/library-health.model.mock.ts`
- [ ] T034 [US4] Add the "Player counts" section beside the health rows in `jordylab-fe/libs/gamecatalog/ui/src/lib/source-manager/source-manager-view.component.html` and `.ts` (filled per source, unknown per reason, TheGamesDB state with "paused until … because …" / "not configured", "show the games" reusing the exceptions pattern); extend `jordylab-fe/libs/gamecatalog/ui/src/lib/source-manager/source-manager.component.spec.ts`
- [ ] T035 [US4] Run `cd jordylab-be && ./gradlew test --tests "*LibraryHealth*" --tests "*GameRepositoryTest*" --tests "*Controller*"` and `cd jordylab-fe && bunx nx test gamecatalog-ui && bunx nx test gamecatalog-api`; fix until green

**Checkpoint**: gaps and paused sources are visible, never silent.

---

## Phase 7: User Story 5 — The new source is optional and safe to run (P3, ships with US2)

**Goal**: no key → today's behaviour; with a key → paced, allowance-aware, self-pausing, and the key never leaks.

**Independent test**: with the key unset, a refresh makes no call and logs one line; a 403, a low allowance or an outage pauses the source without charging any game an attempt; no log line contains the key, the request URI or a response body.

### Tests for User Story 5 (write first)

- [ ] T036 [P] [US5] Extend `…/gamecatalog-test/rest/client/TheGamesDbClientTest.java`: HTTP 403 (body of the contract) pauses for at least an hour or the reported timer and later calls make no HTTP request; `remaining_monthly_allowance` at or below the reserve pauses until `allowance_refresh_timer`; timeout, connection error, HTTP 5xx and other 4xx pause 15 minutes; unreadable body does not pause; the pause is WARN-logged once; calls are at least `min-interval-ms` apart; **no captured log line or thrown message contains the key, the request URI, the `apikey` text or any part of a response body** (assert on every failure row of the contract using a log appender)
- [ ] T037 [P] [US5] Extend `…/gamecatalog-test/service/MultiplayerServiceTest.java`: a paused or unconfigured client yields `DEFERRED` for every eligible game, none is charged an attempt, IGDB and Steam results for the same batch are unaffected, and no game is parked because of an outage

### Implementation for User Story 5

- [ ] T038 [US5] Add the pause state machine to `…/gamecatalog/rest/client/TheGamesDbClient.java` per [research.md](research.md) R3 and the failure table in [contracts/thegamesdb-lookup.md](contracts/thegamesdb-lookup.md): in-memory `pausedUntil`/`pauseReason`/`remainingAllowance`, `ALLOWANCE_LOW` against `allowance-reserve`, no call while paused, one WARN per pause, error handling that logs only the exception class or status, and `state()` filled in (completes T031)
- [ ] T039 [P] [US5] Create the opt-in live check `…/gamecatalog-test/rest/client/TheGamesDbPlatformCheck.java` (`@Tag("integration-thegamesdb")`): one `Platforms` call, assert every console in the client's map still carries its stored TheGamesDB name; skipped without `THEGAMESDB_API_KEY`
- [ ] T040 [US5] Run `cd jordylab-be && ./gradlew test --tests "*TheGamesDbClientTest" --tests "*MultiplayerServiceTest"`, then with the key in the environment (never printed) `./gradlew theGamesDbCheck`; fix until green

**Checkpoint**: the source is safe. US2 may now be released.

---

## Phase 8: Polish & Cross-Cutting

**Purpose**: documentation (FR-014, FR-015), the final boundary and regression checks, and hand-offs to the owner

- [ ] T041 [P] Document the setup in `docs/runbook.md` (new subsection under §23 "LibBot and the auto-fill: what to check", plus a line in §13 "Rotate a secret" pointing to it): the secret name `THEGAMESDB_API_KEY`, where it lives (local `jordylab-be/.env`, prod `jordylab-secrets` via SOPS), how to rotate it, how to check by behaviour without printing it (presence test and a successful `Platforms` call), what a paused source on the Sources page means, and that the service echoes the key in its pagination URLs so response bodies must never be pasted
- [ ] T042 [P] Add the `THEGAMESDB_API_KEY` row to `specs/008-ovh-k8s-deployment/contracts/secrets-schema.md` (backend `TheGamesDbClient`, `@Value`; unset → client skipped, IGDB only)
- [ ] T043 [P] Update the data-source notes in `jordylab-be/AGENTS.md` ("Game catalog model (005 extension — local multiplayer metadata)" and the "Infrastructure"/AI-adjacent notes if they list sources): the second source and its rules (only flagged local-multiplayer console games, agreement rule, count under 2 ignored, separate count-source column, pause on refusal or outage, never log the URI), and add `THEGAMESDB_API_KEY` to the Secrets notes if keys are listed there
- [ ] T044 [P] Update the admin explanation in `jordylab-fe/libs/gamecatalog/ui/src/lib/source-manager/refresh-panel.component.ts` (the "Game data" card): it also looks player counts up again for console games that have none, and says that doubtful ones stay unknown and are listed; adjust its spec if the text is asserted
- [ ] T045 Run the boundary and regression checks for everything touched: `cd jordylab-be && ./gradlew test --tests "*ModularityTests*" --tests "*gamecatalog*"` and `cd jordylab-fe && bunx nx test gamecatalog-ui gamecatalog-api && bunx nx lint gamecatalog-ui gamecatalog-api`; fix until green (no full-suite run unless asked). Then finish **HANDOFF-36** (quickstart §2 steps 6–7) and run `cd jordylab-be && ./gradlew goldenLive` with the keys from the owner's `jordylab-be/.env` (never printed) to confirm a real model still answers well now that the fact line names the source (T022)
- [ ] T046 Scan the diff for secrets (`gitleaks git --no-banner --redact .`) and confirm no `apikey=` text, 64-character hex string or response body appears in any changed file, test fixture or log assertion
- [ ] T047 **HANDOFF-37** is done (the owner added `THEGAMESDB_API_KEY` to the prod secrets on 2026-11-01). After the release, and once the owner confirms the deploy, run [quickstart.md](quickstart.md) §3 on production and record SC-001 to SC-007 in `specs/014-console-player-counts/validation-results.md` (the agent runs it once the owner confirms the release)
- [ ] T048 After the production check, update `docs/testing/bug-log.md` BUG-077 with the measured result (counts before and after, what remains unknown and why) and close it only if a party-size question names far fewer unknown games; mark every finished task above `[x]`

---

## Dependencies & Execution Order

### Phase dependencies

- **Setup (1)**: no dependencies; T001, T002, T003 can start together (T003 [P]).
- **Foundational (2)**: after Setup; **blocks all user stories**. Inside it: T004 → T010; T005/T006 → T007 → T008/T009 → T010.
- **US2 (3)**: after Phase 2. T011/T012 (tests, [P]) → T013 → T014 → T015.
- **US1 (4)**: after US2 (it needs counts to exist). T016 → T017; T018 is the owner's run.
- **US3 (5)**: after Phase 2 for the API/Detail (T019, T021, T023); T022 needs `Candidate` only, so it does not need US2. Can run in parallel with US2 once Phase 2 is done.
- **US4 (6)**: after Phase 2 **and T013** (the client class and its `state()`; T032 injects it). From there it can run in parallel with the rest of US2, US3 and US5 by a different pair of hands; T038 later fills in the real state.
- **US5 (7)**: T036/T037 can be written with US2's tests; T038 follows T013; **US5 must be complete before any release containing US2**.
- **Polish (8)**: after the stories it documents; T041–T044 [P]; T047 needs the release.

### User story dependencies

- US2 → US1 (outcome); US2 ← US5 (release gate); US3 and US4 are independent of each other and of US1.
- No story needs another's UI.

### Within a story

Tests first (they fail), then models/DTOs, services and clients, then controllers and UI, then the story's test run.

### Parallel opportunities

- Phase 2: T005 ∥ T006, then T008 ∥ T009.
- US2 tests: T011 ∥ T012.
- US3: T019 ∥ T020 ∥ T021 ∥ T022 ∥ T023 (different files).
- US4: T026 ∥ T027 ∥ T028, then T030 ∥ T031 ∥ T033.
- Docs: T041 ∥ T042 ∥ T043 ∥ T044.
- Whole stories: US3 can start right after Phase 2; US4 can start once T013 exists; both can then proceed while the rest of US2 and US5 are in progress.

## Implementation Strategy

### MVP first

1. Phase 1 + Phase 2 (foundation).
2. Phase 3 (US2) with Phase 7 (US5): the engine **and** its safety, so nothing unsafe can ship.
3. Phase 4 (US1): prove LibBot benefits (HANDOFF-36 locally).
4. **Stop and validate** against SC-001, SC-003, SC-005, SC-007 with the quickstart.

### Incremental delivery

1. + US3 (sources visible) → deploy-ready increment.
2. + US4 (Sources page) → the owner sees gaps and a paused source.
3. Polish, release, HANDOFF-37, production check (T047), bug-log update (T048).

### Task counts

| Phase | Tasks | Range |
|-------|-------|-------|
| 1 Setup | 3 | T001–T003 |
| 2 Foundational | 7 | T004–T010 |
| 3 US2 engine (P1) | 5 | T011–T015 |
| 4 US1 LibBot outcome (P1) | 3 | T016–T018 |
| 5 US3 source visible (P2) | 7 | T019–T025 |
| 6 US4 Sources page (P2) | 10 | T026–T035 |
| 7 US5 safety (P3) | 5 | T036–T040 |
| 8 Polish | 8 | T041–T048 |
| **Total** | **48** | |
