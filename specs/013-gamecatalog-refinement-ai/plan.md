# Implementation Plan: Game Catalog Refinement and LibBot (AI) Rebuild

**Branch**: `013-gamecatalog-refinement-ai` | **Date**: 2026-10-07 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/013-gamecatalog-refinement-ai/spec.md`

**Builds on**: 002 (catalog), 004 (multi-host model, artwork), 005 (library sources, IGDB multiplayer), 006 (roles, guest limit), 007 (mobile app),
009 (Switch games, generalised to consoles), 011 (e2e hardening), 012 (platform currency) and the AI research of 2026-10-05
(`docs/research/spring-ai-architecture.md`, `spring-ai-gap-analysis.md`, `shared/ai/AGENTS.md`).

## Summary

Rebuild the catalog's AI as **LibBot**, a four-step workflow (interpret → derive → retrieve → compose) in which the model extracts facts and drafts
text while Java owns the rules, the retrieval and the validation of every reference. Retrieval is hybrid: SQL over the live relational model plus
pgvector similarity, with a tri-state treatment of unknown data and votes ranked after hard requirements. Conversation memory is server-side, bounded
and session-only. Around it: make a **game exist once with many places** (scan copies, Steam library, console entries) so hosts, sources and
platforms can multiply without duplicates; introduce **Host** and **Console** aggregates (display names, the Consoles tab); add **per-user marks**,
**per-machine ROM status**, a **auto-fill worker** that fills in covers, facts, descriptions and embeddings by itself (≥ 90 % covers), admin **bulk
refresh runs**, the **filter redesign** and platform/status chip palette from [ui-design.md](./ui-design.md), and three **mobile and detail-page fixes** (fingerprint
error flash, horizontal scroll, honest AI authorship line plus one cover/banner composition on every width).

Technical approach and every decision with its alternatives: [research.md](./research.md). Entities and migrations: [data-model.md](./data-model.md).
API: [contracts/](./contracts/). Design: [ui-design.md](./ui-design.md) and [ui-mockup.html](./ui-mockup.html).

## Technical Context

**Language/Version**: Java 25, Spring Boot 4.1.1, Spring Modulith 2.1.1, Spring AI 2.0.1 (GA); Angular 22 / Nx 23 / TypeScript, Bun

**Primary Dependencies**: existing: Spring Data JPA, Flyway, Lombok, Spring AI OpenAI + Anthropic starters, spartan/ui, Tailwind (Night Lab theme). **New**:
`com.github.ben-manes.caffeine:caffeine` (conversation store; version via the Boot BOM). `spring.ai.model.embedding` switches from `none` to `openai`
(embeddings through the existing OpenRouter gateway). No new frontend dependency.

**Storage**: PostgreSQL 16 + pgvector. Three migrations in the `gamecatalog` schema (one Java), see data-model.md. `vector` extension must exist in prod
before release (research Part D).

**Testing**: Backend: JUnit 5, AssertJ, Mockito (no `any()`), Testcontainers (`pgvector/pgvector:pg16`), WireMock, MockMvc, EqualsVerifier on entities,
TestBuilder fixtures, `ModularityTests`, `RoleMatrixTest`; JaCoCo ≥ 80 % per package. Frontend: Vitest + Spectator (`useValue`/`vi.fn()`, mock files per interface),
80 % gate. Browser: Playwright journeys (`e2e-web`) incl. axe and the new horizontal-overflow check. AI: golden set (replay tier in CI, live tier on demand).
Mobile: Android emulator suite for startup; fingerprint is a manual checklist (project rule).

**Target Platform**: web app + Android WebView (Capacitor) from the same build; single-node k3s on one OVH VPS (one backend replica, 8 GB RAM).

**Project Type**: web application: existing monolith module `gamecatalog` + `shared/ai` + a small `settings` change; Nx libs `gamecatalog/api|ui` and
`shared/auth`. No new projects or libs.

**Performance Goals**: LibBot: stage within 1 s, complete answer ≤ 10 s for 95 % of questions (two Haiku-class calls + one embedding ≈ 3 to 6 s typical);
library grid and filters stay under the 004/005 envelope (< 2 s first page, < 1 s search at 5,000 games); the vote/aggregate queries are index-backed.

**Constraints**:
- All model calls go through `ResilientAiService`; never a `ChatClient`, never a second advisor/tool loop.
- No model call inside a database transaction; a scan upload never waits on AI.
- No prompt or answer text in logs; token usage on every call.
- Flyway owns all DDL; migrations append-only; the title-key merge ships with a dry-run and a pre-release backup.
- Guest limit of 20/day unchanged; failed or cancelled messages never count.
- Real data only through the real scanner (root `AGENTS.md`, Validation Data); the dev containers are read-only for agents.
- WCAG AA: every chip pair ≥ 4.5:1 (unit-tested) and the existing axe journey stays green; no horizontal scroll from 360 px.

**Scale/Scope**: ~230 games today (a few thousand at most), a handful of users (admin plus friends), up to ~8 hosts and consoles. Conversation store ≤ 2,000 live
conversations. IGDB paced at 4 requests/s by the existing client, so a first fill takes minutes.

## Constitution Check

*GATE: passed before Phase 0; re-checked after Phase 1 design (below).* Source: `.specify/memory/constitution.md` v1.1.2.

| Principle | Check | Result |
|---|---|---|
| I. Clean code (SOLID, KISS, YAGNI, DRY, full names, small methods) | The retrieval fragments, the visibility predicate and `PlatformCatalog` each replace copy-pasted logic (five `@Query` copies of visibility, three platform maps, a frontend colour function). `LibBotAnswerValidator`, `GameIdentityService`, `PlaceRemovalService` are single-purpose. Token streaming, a tool-calling agent and a separate vector DB are deliberately **not** built (YAGNI). | Pass |
| II. Fail fast, no silent failures | Structured model output is validated and repaired once, then fails with a typed reason; ROM-status on a non-emulated copy and duplicate names fail with 409; the Flyway merge aborts on conflicting uniqueness instead of skipping; embedding failure is **logged and degrades visibly** (lexical search) rather than silent. | Pass |
| III. Immutable, builder-first | New entities (`Host`, `Console`, `ConsoleGameEntry`, `GameMark`, `RefreshRun`, `GameEmbedding`) use `@Builder` with `Preconditions` in `build()`; DTOs are records; `@UtilityClass` for `TitleKeys`, `GameSources`. No `@Data`, no hand constructors. | Pass |
| IV. Testing discipline | New entities ship builder tests + `EqualsVerifier`; TestBuilders for every fixture; `assertSoftly`; no `any()`; Testcontainers for retrieval SQL; Vitest + Spectator with `useValue`. Golden set added for the AI. | Pass |
| V. Language & tooling currency | Java 25 syntax (records, pattern matching, sealed `Place` view), no `var`; `inject()` and `#field` in Angular; signals + hand-rolled stores, no NgRx. | Pass |
| Architecture: container–presentation, signal stores in `api` lib, barrels | `LibBotStore`, extended `GameLibraryStore`, `ConsoleStore`, `RefreshRunStore` in `libs/gamecatalog/api`; views stay presentational. | Pass |

**Gate result**: no violation requires a waiver. Deviations from earlier *conventions* (not the constitution) are in Complexity Tracking.

**Post-design re-check**: unchanged. The design adds six small aggregates and one pipeline; every one is demanded by a spec requirement (traceability table below).

## Project Structure

### Documentation (this feature)

```text
specs/013-gamecatalog-refinement-ai/
├── plan.md              # this file
├── research.md          # decisions, alternatives, open items
├── data-model.md        # entities, migrations, validation map
├── ui-design.md         # filter bar, chips, card, LibBot page (frontend-design)
├── ui-mockup.html       # runnable design reference
├── quickstart.md        # local-then-prod validation guide
├── contracts/
│   ├── catalog-api.md
│   ├── consoles-api.md
│   └── libbot-api.md
├── checklists/requirements.md
└── tasks.md             # created by /speckit-tasks
```

### Source code (repository root)

```text
jordylab-be/src/main/java/dev/jordy/jordylab/
├── shared/ai/                         # extended, still the only model path
│   ├── ResilientAiService.java        # + call(messages), callStructured, embed, token usage
│   ├── AiFeature.java                 # + GAMECATALOG_EMBEDDING (not selectable); LibBot display names
│   ├── AiCallResult.java, AiCallCompleted.java   # + token counts
│   └── StructuredOutput.java          # BeanOutputConverter + validate + one repair (new)
├── settings/
│   ├── rest/controller/GuestChatLimitFilter.java # pre-check only, new path
│   ├── service/GuestChatUsageListener.java       # increments on LibBotMessageAnswered (new)
│   └── service/KeycloakUserAdministrationService.java  # publishes UserAccessRemoved
└── gamecatalog/
    ├── LibBotMessageAnswered.java, UserAccessRemoved.java  # root package events (public API)
    ├── domain/
    │   ├── Host, Console, ConsoleGameEntry, GameMark, GameEmbedding, RefreshRun   (new)
    │   ├── Game, GameInstallation, ScanSource                                      (changed)
    │   ├── PlatformCatalog, RomStatus, MarkType, GameSources                       (new)
    │   └── repository/…               # + Host, Console, ConsoleGameEntry, GameMark, GameEmbedding, RefreshRun
    ├── service/
    │   ├── libbot/                    # LibBotService, QuestionInterpreter, ConstraintDeriver, CandidateRetriever,
    │   │                              # AnswerComposer, LibBotAnswerValidator, ConversationStore, LibBotMessages
    │   ├── autofill/                  # CatalogAutoFillService, FactsStep, ArtworkStep, DescriptionStep, EmbeddingStep, RefreshRunService
    │   ├── GameIdentityService, PlaceRemovalService, PlaceNameService, MarkService, RomStatusService
    │   ├── ConsoleService, ConsoleGameService, ConsoleBulkService   # replace SwitchGameService/SwitchBulkService
    │   └── (changed) ScanService, ReconciliationService, GameQueryService, ScanSourceService, ArtworkService, EnrichmentService
    ├── rest/controller/               # LibBotController (SSE), ConsoleController, MarkController, HostController, RefreshRunController (+ changed)
    ├── rest/client/                   # IgdbClient (+ platform lookup, cover/artwork), ArtworkLookupClient (normalised variants)
    └── util/                          # TitleKeys (pure static, shared with the Flyway Java migration)
jordylab-be/src/main/resources/
├── db/migration/V2026100800{1,2,3}__…             # SQL, Java, SQL
├── prompts/gamecatalog/{libbot-interpret,libbot-answer,enrichment}.st
└── libbot/messages_{en,nl}.properties
jordylab-be/src/test/java/…                         # mirrors main; golden set under src/test/resources/libbot/golden/
deploy/k8s/cluster/                                 # + cnpg Database resource enabling the vector extension

jordylab-fe/libs/gamecatalog/api/src/lib/           # LibBotStore, ConsoleStore, RefreshRunStore, MarkStore, extended GameLibraryStore, models, mocks
jordylab-fe/libs/gamecatalog/ui/src/lib/
├── game-grid/ (filter bar, active chips, filters panel/sheet, card), game-detail/ (places, ROM per machine, marks, About heading from provenance, banner/cover composition)
├── libbot/ (replaces game-chat/), consoles/ (replaces switch-game/, switch-bulk/), source-manager/ (host names, refresh runs, health)
└── chips/ (platform, status, rom, mark chips)
jordylab-fe/libs/shared/auth/src/lib/               # auth.service.ts + biometric-unlock.service.ts fix
jordylab-fe/apps/jordylab/src/app/                  # nav labels/icons, route redirects
jordylab-fe/apps/jordylab-e2e/src/                  # journeys: libbot, consoles, marks, filters; overflow check in accessibility.spec.ts
docs/ + AGENTS.md files                             # "no scheduler" rule, chat section, model notes updated
```

**Structure decision**: extend the existing `gamecatalog` module and its Nx libs. Two sub-packages under `service/` (`libbot`, `autofill`) follow the
`service/scan` precedent; the layout rule says to stop and ask before inventing structure, so this is **flagged for the owner** (research, decisions list)
and trivially reversible (a package move).

## Requirement traceability (spec → where it is built)

| Spec | Built in |
|---|---|
| US1, US2, FR-001 to FR-010 | `service/libbot/*`, `ConstraintDeriver`, `CandidateRetriever` (tri-state), `AnswerComposer`, `LibBotAnswerValidator` |
| US3, FR-011, FR-012 | `ConversationStore` (10 exchanges, 2 h idle), `LibBotController` conversation key from the JWT subject |
| FR-013, FR-014 | provider fallback in `ResilientAiService`, SSE stages, `LibBotMessageAnswered` quota event |
| FR-015, FR-017 | delimited catalog rows in `libbot-answer.st`; prompts as resources; no transactions around AI; token counters; no content logging |
| FR-016, FR-029 | visibility predicate (one definition) in retrieval; embedding refresh by the auto-fill worker; hide-impact |
| FR-018 | golden set, replay and live tiers |
| US4, FR-019 to FR-022, FR-063 | `CatalogAutoFillService` + steps, IGDB covers and summaries, `DescriptionQualityValidator`, rewritten `enrichment.st`, daily sweep, Sources health counts |
| US5, FR-023 to FR-028 | `GameIdentityService`, `console_game_entry`, `PlaceRemovalService`, Flyway Java merge |
| US6, FR-032 to FR-036 | `ConsoleService`/`ConsoleGameService`/`ConsoleBulkService`, `PlatformCatalog` autocomplete, Switch migration, Consoles UI |
| US7, FR-037, FR-038 | filter bar, `GameLibraryStore` arrays + URL state |
| US8, FR-030, FR-031 | `Host` aggregate, `Host.label()`, `PlaceNameService`, host naming UI |
| US9, FR-039 to FR-041 | `GameSources`, `PlatformCatalog` colours, chip components, contrast unit test |
| US10, FR-043 to FR-046 | `GameMark`, `MarkService`, `UserAccessRemoved` listener, vote ranking in retrieval |
| US11, FR-047 to FR-050 | `RefreshRun`, `RefreshRunService`, Sources page buttons and dialog |
| US12, FR-042 | nav labels/icons, Sources lists scan sources only, hide-impact dialog |
| US13, FR-051 to FR-055 | `GameInstallation.romStatus` + `RomStatusService`, chips, filter, retrieval playable-places rule |
| FR-056 | `language` in interpretation, `LibBotMessages` en/nl, Dutch golden cases |
| US14, FR-057, FR-058 | `auth.service.ts` / `biometric-unlock.service.ts` fix with failing test first; overflow Playwright check and CSS fixes |
| US15, FR-059 to FR-062 | `AiCallResult.answeredModel`, `Game` description-provenance columns + `AiAuthorship`, `description`/`factSources` in the detail response, About heading and Spec sheet source note, banner/cover composition (research A13, C7) |

## Delivery phases (input for `/speckit-tasks`; local first, prod last, as the owner set)

Each phase leaves the app working locally. **Nothing is released until every phase has passed locally** (spec "Delivery and Validation").

| Phase | Content | Exit check (local) |
|---|---|---|
| 0. Foundations | Spike: embeddings through the gateway (**done**, test in repo), Flyway Java migration dry-run on a **copy** of dev data, IGDB platform ids (**done**, verified 2026-10-07), failing tests for the two mobile bugs and the overflow check | spike notes in research.md; red tests exist |
| 1. Model | Migrations 1 to 3, `Host`, `Console`, places, `PlatformCatalog`, `TitleKeys`, `GameIdentityService`, visibility predicate, visibility/purge/merge tests | `./gradlew test` for gamecatalog green; ModularityTests green |
| 2. Scan & sources | scan adoption through `Host`, display name, source labels, Sources page (scan sources only, hide-impact), remove `SWITCH` | real scanner run against local backend (macOS) reflects hosts and labels |
| 3. Auto-fill | worker, IGDB facts + covers, libretro variants, daily sweep, health counts, removal of inline enrichment from `ScanService`, `RefreshRun` + buttons | after a real scan, ≥ 90 % covers locally, no button pressed |
| 4. LibBot | `ResilientAiService` extensions, interpret/derive/retrieve/compose, store, SSE, quota event, prompts, i18n, golden replay tier | golden set (live tier, local) ≥ 90 %; owner-reported cases pass |
| 5. Consoles, marks, ROM status | `/consoles`, bulk, marks, rom-status, events | journeys pass |
| 6. Frontend | stores, filter bar, chips, card, detail, LibBot page, Consoles page, nav, redirects | Vitest green; mockup parity reviewed in the browser pane at desktop and 360 px |
| 7. Mobile + polish | auth fix, overflow fixes on every page, a11y journey | Playwright `e2e-web` green; Android startup suite; **fingerprint checklist on the phone (manual)** |
| 8. Local acceptance | walk every user story and success criterion in [quickstart.md](./quickstart.md) §A | all green; results recorded |
| 9. Release | PR, CI, tag, pre-release on-demand CNPG backup (runbook step added), apply the pgvector `Database` resource, deploy (owner's yes on cluster changes) | deploy healthy |
| 10. Prod acceptance | re-run quickstart §B on jordylab.be; measure SC-001 and SC-007 on the real library; phone checks | recorded in the validation results |

## Complexity Tracking

Constitution: no violations. These depart from **earlier conventions** and are listed so the owner can veto them:

| Departure | Why needed | Simpler alternative rejected because |
|---|---|---|
| SQL over a Flyway-owned pgvector table instead of Spring's `VectorStore` bean | visibility, host names, ROM status and votes change constantly and live in other tables and must be correct within a minute; and `VectorStore` embeds through its own `EmbeddingModel` (verified in the 2.0.1 bytecode), around `ResilientAiService` | `PgVectorStore` would mirror that state as metadata and go stale, bypass the one AI path, and own its own DDL against the Flyway rule. Search speed is equal at this size. |
| Own `ConversationStore` instead of Spring AI `ChatMemory` | needs cited game ids to resolve "those", exact 10-exchange window, session-only | `ChatMemory` stores bare messages; adding ids means wrapping it anyway |
| Scheduler in `gamecatalog` (daily sweep), **approved 2026-10-07** | the owner's Q5 answer; the 90 % cover target needs self-healing | "no scheduler" leaves missing covers missing until someone scans or presses a button |
| `Game.platform` removed (platform lives on places) | one game, several platforms | keeping a "primary platform" makes the data lie as soon as a game has two |
| A Flyway **Java** migration | `TitleKeys` must be the same code in the app and the backfill | SQL `unaccent` is not guaranteed on the CNPG image; two normalisers would diverge |
| Quota counting moved to an event | SSE answers are `200` before the answer exists | counting on HTTP status would charge failed answers (violates FR-013) |
| Sub-packages `service/libbot`, `service/autofill` | ~20 new classes in two cohesive groups | flat `service/` would hold 40+ files; flagged for the owner |

## Risks

| Risk | Mitigation |
|---|---|
| Prod `CREATE EXTENSION vector` not permitted for the app role | declarative `Database` extension resource applied **before** the release; migration uses `IF NOT EXISTS`; rehearsal on the restore-drill cluster **Owner approved this cluster change on 2026-10-07**; it is applied just before the release (Phase 9) |
| Title-key merge joins two different games | ids that differ never merge; dry-run output reviewed on a copy of dev data; relink repairs; pre-release backup |
| ~~OpenAI SDK module's embedding model ignores the gateway URL~~ | **Verified, risk closed**: `AiEmbeddingGatewayWiringTest` passes (model, dimensions, `/v1/embeddings`, token usage) |
| IGDB match quality for obscure ROMs misses the 90 % cover target | IGDB title+platform match, then title-only, then libretro variants; admin sees the exception list; measured on real data in Phase 3 before building more on it |
| LibBot quality is hard to judge | golden set with a pass threshold; the owner's six reported failures are fixtures |
| Scope: 15 user stories | phases above each leave a working app; the P1 stories (LibBot, data completeness, one-game-many-places) come first |
