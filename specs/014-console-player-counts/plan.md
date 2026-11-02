# Implementation Plan: Console Player Counts from a Second Source

**Branch**: `014-console-player-counts` | **Date**: 2026-11-01 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/014-console-player-counts/spec.md`

## Summary

Console games flagged local multiplayer that IGDB leaves without a player count get one from TheGamesDB's own `players`
field, but only when every entry for the same title (catalog title key) on the game's console agrees. The step runs after the
IGDB fill inside the existing multiplayer derivation (background backlog, bulk "Refresh game data", per-game refresh), never for
Steam games or games whose local flag is unknown. Each count records its own source in a new column (backfilled to IGDB), and
each game left without one records why, so the detail page, LibBot's fact lines and a new "Player counts" section on the
Sources page can say where a number came from or why it is missing. The source is optional, paced, allowance-aware and pauses
itself on refusals and outages without charging games a failed attempt; its key is never logged, including the copy the
service echoes back in every response. Design decisions: [research.md](research.md).

## Technical Context

**Language/Version**: Java 25 (backend), TypeScript / Angular 22 (frontend)

**Primary Dependencies**: Spring Boot 4.1.1, Spring `RestClient`, Spring Data JPA, Flyway, Lombok; Angular signals, spartan/ui

**Storage**: PostgreSQL 16 (`gamecatalog` schema): two new nullable columns on `game` (`max_local_players_source`,
`player_count_gap`), one Flyway migration with a backfill

**Testing**: JUnit 5, AssertJ (`assertSoftly`), Mockito (no `any()`), WireMock, Testcontainers (module test); Vitest +
Spectator for the frontend; an opt-in live check (`./gradlew theGamesDbCheck`, tag `integration-thegamesdb`)

**Target Platform**: the `jordylab-be` monolith (one replica on the single-node k3s cluster) and the `jordylab` Angular app

**Project Type**: web application (Spring Boot backend + Nx Angular frontend)

**Performance Goals**: a bulk refresh spends at most 100 TheGamesDB lookups (15 expected today); one call per second

**Constraints**: 1,000 lookups a month, stop at a reserve of 20; the key appears in no log line, response, page or stored
body; with no key the behaviour is byte-for-byte today's; counts only from the source's field

**Scale/Scope**: 56 local-multiplayer console games (15 without a count), 32 mappable consoles; one new outbound client,
changes in one service, one entity, two response records, one repository, two frontend views

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | How the design complies | Status |
|-----------|------------------------|--------|
| I. Clean Code | One responsibility per piece: the client talks to TheGamesDB and applies the agreement rule, `MultiplayerService` orders the sources, `Game` owns the fill-only rule. Full names (`PlayerCountSource`, `PlayerCountGap`), early returns. | Pass |
| II. Fail fast, no silent failures | A refusal, low allowance or outage pauses the source with one warning line and shows on the Sources page ("paused until …, why"); an unconfigured source logs once and shows "not configured". Games are never silently charged or silently skipped: every missing count carries a reason. | Pass |
| III. Immutable, builder-first | Response DTOs and the lookup result are records; the client uses `@RequiredArgsConstructor` with package-private `@Value` fields and `@PostConstruct init()` (no hand-written constructor); `Game` gains named mutation methods, no setters. | Pass |
| IV. Testing discipline | WireMock client tests, Mockito service tests with explicit values, `GameTestBuilder` fixtures, `assertSoftly`, descriptive names; frontend specs with `useValue` + `vi.fn()` and real signals. Synthetic data only inside tests. | Pass |
| V. Language & tooling currency | Java 25 without `var`; Angular 22 with `inject()` and `#field`. | Pass |
| Architecture constraints | Everything stays inside `gamecatalog` and its existing sub-packages (`domain`, `rest/client`, `rest/controller/model`, `service`); the Sources section follows container–presentation and the existing `scan-source.store`. No new module, no cross-module import. | Pass |
| Project rules (AGENTS.md) | Deterministic data only, never AI; never hand-seed; the secret goes in `.env.example` by name and in the SOPS file by the owner; migration `V<yyyyMMdd>…` with `CREATE SCHEMA IF NOT EXISTS`; `ModularityTests` re-run. | Pass |

**Post-design re-check (after Phase 1)**: still Pass. The design added a source-state block to an admin-only response and a
`reason` to the exceptions list; neither crosses a module boundary or needs a new sub-package. No Complexity Tracking entries.

## Project Structure

### Documentation (this feature)

```text
specs/014-console-player-counts/
├── spec.md
├── plan.md              # This file
├── research.md          # Phase 0
├── data-model.md        # Phase 1
├── quickstart.md        # Phase 1
├── contracts/
│   ├── thegamesdb-lookup.md     # outbound: what is called and how a response is read
│   └── catalog-api-changes.md   # inbound: game detail + Sources page health changes
├── checklists/requirements.md
└── tasks.md             # Phase 2 (/speckit-tasks, not created here)
```

### Source Code (repository root)

```text
jordylab-be/
├── .env.example                                         # THEGAMESDB_API_KEY (already added, name only)
├── build.gradle.kts                                     # theGamesDbCheck task (tag integration-thegamesdb)
└── src/
    ├── main/java/dev/jordy/jordylab/gamecatalog/
    │   ├── domain/
    │   │   ├── Game.java                                # maxLocalPlayersSource, playerCountGap, applyPlayerCount, recordPlayerCountGap
    │   │   ├── PlayerCountSource.java                   # new enum: IGDB, THEGAMESDB
    │   │   ├── PlayerCountGap.java                      # new enum: NO_MATCH, ENTRIES_DISAGREE, COUNT_BELOW_TWO, CONSOLE_NOT_LISTED
    │   │   └── repository/LibraryHealthRepository.java  # player-count counts and lists
    │   ├── rest/client/TheGamesDbClient.java            # new: lookup, agreement rule, pause state, platform map
    │   ├── rest/controller/model/
    │   │   ├── GameDetailResponse.java                  # + maxLocalPlayersSource
    │   │   ├── FactSourcesResponse.java                 # + playerCount
    │   │   ├── LibraryHealthResponse.java               # + playerCounts block
    │   │   └── HealthExceptionsResponse.java            # + reason per listed game
    │   ├── rest/controller/ScanSourceController.java    # exceptions endpoint: optional reason
    │   └── service/
    │       ├── MultiplayerService.java                  # third step, outcome enum, platform lookup
    │       ├── LibraryHealthService.java                # PLAYER_COUNT kind and block
    │       ├── GameQueryService.java                    # map the new fields
    │       └── libbot/AnswerComposer.java               # "up to N local players (source)"
    ├── main/resources/
    │   ├── application.yaml                             # jordylab.gamecatalog.thegamesdb.*
    │   └── db/migration/V20261102001__gamecatalog_player_count_source.sql
    └── test/java/dev/jordy/jordylab/gamecatalog/
        ├── rest/client/TheGamesDbClientTest.java        # WireMock
        ├── rest/client/TheGamesDbPlatformCheck.java     # opt-in live check
        ├── service/MultiplayerServiceTest.java          # extended
        ├── service/LibraryHealthServiceTest.java        # extended or new
        └── domain/GameTest.java                         # extended

jordylab-fe/libs/gamecatalog/
├── api/src/lib/gamecatalog.models.ts                    # new fields and kinds
├── api/src/lib/mocks/                                   # updated mocks
├── ui/src/lib/game-detail/game-detail-view.component.html   # count source next to the count
└── ui/src/lib/source-manager/                           # "Player counts" section

docs/runbook.md                                          # key setup, rotation, behaviour check, pause meaning
specs/008-ovh-k8s-deployment/contracts/secrets-schema.md # THEGAMESDB_API_KEY row
jordylab-be/AGENTS.md                                    # local-multiplayer section: second source, rules
```

**Structure Decision**: web application, existing layout. All backend changes stay in the `gamecatalog` module's existing
sub-packages (no new sub-package, per `jordylab-be/AGENTS.md`); the frontend changes stay in the `gamecatalog` `api` and `ui`
libs. The production secret is added by the owner to `deploy/k8s/overlays/prod/secrets.sops.yaml` (handoff in tasks); it
reaches the backend through the existing `envFrom`.

## Complexity Tracking

None. No constitution violation needs justifying.
