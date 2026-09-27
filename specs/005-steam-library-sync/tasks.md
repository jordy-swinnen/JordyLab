# Tasks: Steam Library & Family Library Sync

**Feature**: `005-steam-library-sync` | **Plan**: [plan.md](./plan.md) | **Spec**: [spec.md](./spec.md)

Legend: `[P]` = can run in parallel with adjacent `[P]` tasks; `[USn]` = user story.

## Phase 1 — Setup

- [X] T001 Add `library()` properties to `GameCatalogProperties`; switch `jordylab.ai.modules.gamecatalog.model` to Haiku in `application.yaml`
- [X] T002 [P] Migration `V20260928001__gamecatalog_library.sql`: duplicate-check + unique partial index `uq_game_steam_app_id`, `game.title_source`, `game_library_entry`, `library_sync_run`, tool cleanup

## Phase 2 — Foundational (domain + identity)

- [X] T003 [P] New enums `LibrarySource`, `LibrarySyncOutcome`, `TitleSource` (+ `InstallStatus`)
- [X] T004 [P] New entities `GameLibraryEntry`, `LibrarySyncRun` (+ TestBuilders, entity tests)
- [X] T005 `Game`: `titleSource`; `applyDeterministicMetadata` fill-only; `updateCatalogInfo(title, platform, source)` with authority; deterministic multiplayer flags + description; `markMetadataFetched`
- [X] T006 [P] New repositories `GameLibraryEntryRepository`, `LibrarySyncRunRepository`; `findBySteamAppId` + `insertSteamGameIfAbsent`
- [X] T007 `ReconciliationService`: resolve-or-create by app ID (native insert-or-fetch), title authority; purge condition extended
- [X] T008 [P] `ToolExclusion` + applied in `SteamLibraryParser` [US1/US2]

## Phase 3 — US1: link-not-rebuild (P1)

- [X] T009 Library entry upsert goes through the same resolve-or-create path; adoption touches nothing else [US1]
- [X] T010 Test: SC-001 cost — enriched game reported by the library ⇒ `verifyNoInteractions` on `SteamAppDetailsClient` + `ResilientAiService` [US1]
- [X] T011 Test: SC-004 — duplicate `steam_app_id` rejected by unique index / resolve-or-create yields one game [US1]
- [X] T012 Test: title authority — a scan does not overwrite a library title [US1]

## Phase 4 — US2: owned library (P1)

- [X] T013 `SteamOwnedGamesClient` + WireMock-able shape; no full-URI logging [US2]
- [X] T014 `SteamLibrarySyncService`: normalise → SHA-256 → `NO_CHANGE`; empty/shrink guards; upsert entries; soft-remove missing; bounded passes; record `LibrarySyncRun` with call counts [US2]
- [X] T015 Library passes: metadata (paced) → enrichment (installed-first, no AI for not-installed) [US2/US6]
- [X] T016 Owned sync piggybacks on an applied Steam scan via `syncOwnedIfDue()` gated by min-interval (inline, no scheduler) [US2]
- [X] T017 Test: SC-002 same response twice ⇒ `NO_CHANGE`, no per-game work; SC-009 no AI for not-installed [US2]
- [X] T018 Test: purge — owned game with an expired installation survives; removed entry + no installation purges [US2]

## Phase 5 — US3: filtering (P1)

- [X] T019 `GameRepository`: visibility = installed OR active library entry; `installStatus` + `librarySource` params on list + chat queries
- [X] T020 `GameQueryService`/`GameCatalogController`: `installStatus` (default INSTALLED) + `librarySource`; responses carry `installStatus`/`librarySource`/`familyOwners`
- [X] T021 `ChatService`: `installStatus`/`librarySources` in the filter schema, parser and query [US3]
- [X] T022 FE models + api service + mocks for status/source/library [US3]
- [X] T023 FE `game-library.store.ts`: `selectedInstallStatus`/`selectedLibrarySource` + `hostFilterAvailable` [US3]
- [X] T024 FE grid status/source filter chips + card badges; detail rows + family owners [US3]
- [X] T025 Tests: repository filter matrix, chat filter, Spectator specs [US3]

## Phase 6 — US4/US5: family (P2)

- [ ] T026 **Deferred**: freeze a real, sanitised `GetSharedLibraryApps` capture as the WireMock fixture. The client is implemented against the provisional shape and fails safe (`UNKNOWN_RESPONSE`/`TOKEN_EXPIRED`) until a real token confirms field names.
- [X] T027 `SteamFamilyClient` + `SteamFamilySyncService` (in-memory token, omit excluded, owner ids) [US4/US5]
- [X] T028 `LibraryController`: `/library/steam/sync`, `/library/steam-family/sync`, `/library/status`; error codes [US4/US5]
- [X] T029 FE source-manager library section: sync buttons, last-run (metadata/AI call counts), family token field, stale hint [US5]

## Phase 7 — US6: cost + polish

- [X] T030 `SteamAppDetailsClient`: fixed `filters`, `type` check, `short_description` + categories, 429-aware, paced from the library path [US6]
- [X] T031 `EnrichmentService`: fill-only deterministic apply; not-installed excluded from the backlog; installed-first ordering [US6]
- [X] T032 Tests: 429 handling, tool exclusion, appdetails fixtures, type/short_description [US6]
- [X] T033 Docs/config: `application.yaml` keys, root + backend AGENTS.md AI routing/notes

## Phase 8 — Validation

- [X] T034 Backend full suite (367 tests) + `ModularityTests`; frontend affected lint + test (135) + host build
- [ ] T035 **Deferred to manual run**: quickstart end-to-end with the real scanner + a real owned/family sync (requires `STEAM_WEB_API_KEY`/`STEAM_ID` and a live family token). Automated coverage stands in for SC-001/002/004/009.

## Notes

- The owned sync is triggered inline after an applied Steam scan (`SteamLibrarySyncService.syncOwnedIfDue()`), not via a Modulith event — KISS, and consistent with 004's inline philosophy. The `event_publication` table remains unused.
- Family owner display names are not available from the shared-library response; the owning Steam IDs are stored (non-secret) and shown. Resolving them to display names is a follow-up.
