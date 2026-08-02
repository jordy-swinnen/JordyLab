# Tasks: Complete FNA MVP1

**Input**: Design documents from `/specs/001-fna-mvp1-completion/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: Tests are included — the spec mandates tests for every view/component/service plus 80% coverage on both sides (FR-014 through FR-016c). Tests are written alongside implementation per the TestBuilder canonical structure.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Add coverage tooling to both projects and prepare configuration scaffolding before any story work begins.

- [x] T001 Add JaCoCo Gradle plugin and configure 80% coverage verification in jordylab-be/build.gradle.kts
- [x] T002 [P] Enable Vitest coverage with 80% line threshold in jordylab-fe/nx.json targetDefaults for @nx/vitest:test
- [x] T003 [P] Add per-lib coverage threshold config to jordylab-fe/libs/fna/api/project.json and jordylab-fe/libs/fna/ui/project.json (reportsDirectory already set)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core value objects and configuration that ALL user stories depend on. The `AiCallResult` record, `ProviderFailureReason` enum, `AiModuleConfig` properties, and `ProviderHealthCache` must exist before US1 implementation and before US3 tests can cover them.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [x] T004 [P] Create ProviderFailureReason enum in jordylab-be/src/main/java/dev/jordy/jordylab/shared/ai/ProviderFailureReason.java with values UNREACHABLE, TIMEOUT, RATE_LIMITED, AUTH_FAILED, UNKNOWN
- [x] T005 [P] Create AiCallResult record in jordylab-be/src/main/java/dev/jordy/jordylab/shared/ai/AiCallResult.java with success/failure factory methods per data-model.md
- [x] T006 [P] Create AiModuleConfig @ConfigurationProperties record in jordylab-be/src/main/java/dev/jordy/jordylab/shared/ai/AiModuleConfig.java binding jordylab.ai.modules.* properties
- [x] T007 [P] Create ProviderHealth value object in jordylab-be/src/main/java/dev/jordy/jordylab/shared/ai/ProviderHealth.java (provider, healthy, lastCheckedAt, ttlSeconds)
- [x] T008 Create ProviderHealthCache @Component in jordylab-be/src/main/java/dev/jordy/jordylab/shared/ai/ProviderHealthCache.java with ConcurrentHashMap, TTL-based isHealthy(), recordFailure(), recordSuccess() (depends on T007)
- [x] T009 Add per-module provider config to jordylab-be/src/main/resources/application.yaml under jordylab.ai.modules.fna with provider=anthropic and model=claude-sonnet-5 (depends on T006)

**Checkpoint**: Foundation ready — value objects, config, and health cache exist. User story implementation can now begin.

---

## Phase 3: User Story 1 — AI Provider Resilience (Priority: P1) 🎯 MVP

**Goal**: Rebuild ResilientAiService for per-module routing, bounded health-check-and-cache, normalized failure recording, and zero-throw AiCallResult returns. Update BriefingGeneratorService to handle failure explicitly without retry.

**Independent Test**: Run `./gradlew test --tests "dev.jordy.jordylab.shared.ai.*"` and `./gradlew test --tests "dev.jordy.jordylab.fna.service.BriefingGeneratorServiceTest"`. All pass. On simulated provider failure, no Briefing is saved and the normalized reason is logged.

### Tests for User Story 1

> **NOTE**: Write these tests FIRST, ensure they FAIL before implementation

- [x] T010 [P] [US1] Write ProviderHealthCacheTest in jordylab-be/src/test/java/dev/jordy/jordylab/shared/ai/ProviderHealthCacheTest.java — TTL expiry, invalidation on failure, refresh on success
- [x] T011 [P] [US1] Write AiModuleConfigTest in jordylab-be/src/test/java/dev/jordy/jordylab/shared/ai/AiModuleConfigTest.java — binds yaml, unknown module returns failure result
- [x] T012 [P] [US1] Rewrite ResilientAiServiceTest in jordylab-be/src/test/java/dev/jordy/jordylab/shared/ai/ResilientAiServiceTest.java — success returns AiCallResult with content; failure returns AiCallResult with enum reason; never throws; health cache consulted; per-module routing
- [x] T013 [P] [US1] Update BriefingGeneratorServiceTest in jordylab-be/src/test/java/dev/jordy/jordylab/fna/service/BriefingGeneratorServiceTest.java — on AiCallResult.failure, no Briefing saved, reason logged, no retry; on success, Briefing saved with content and model

### Implementation for User Story 1

- [x] T014 [US1] Rewrite ResilientAiService in jordylab-be/src/main/java/dev/jordy/jordylab/shared/ai/ResilientAiService.java — inject AiModuleConfig + ProviderHealthCache + AnthropicChatModel; call signature returns AiCallResult; route by moduleName; consult health cache before call; map exceptions to ProviderFailureReason; record attribution per call; never throw (depends on T004, T005, T006, T008)
- [x] T015 [US1] Update BriefingGeneratorService in jordylab-be/src/main/java/dev/jordy/jordylab/fna/service/BriefingGeneratorService.java — call aiService.call("fna", ...); inspect AiCallResult.success(); on failure log reason and throw BriefingGenerationException(failureReason) without saving Briefing (superseded T015 as originally written: returning `null` produced a live NullPointerException in FnaService.triggerBriefing() — see Phase 8); on success save Briefing with content and modelUsed from result (depends on T014)
- [x] T016 [US1] Run `./gradlew test --tests "dev.jordy.jordylab.shared.ai.*" --tests "dev.jordy.jordylab.fna.service.BriefingGeneratorServiceTest"` and verify all pass

**Checkpoint**: User Story 1 is fully functional — AI calls route per-module, health is cached, failures are normalized and explicit, briefing job does not retry.

---

## Phase 4: User Story 2 — Frontend Boundary Enforcement (Priority: P2)

**Goal**: Replace the wildcard Nx depConstraints in eslint.config.mjs with concrete scope and type boundary rules that fail lint on violations, enforced in CI.

**Independent Test**: Run `bunx nx run-many -t lint` — passes. Temporarily add an import from fna-ui inside fna-api and re-run — lint fails naming the violated constraint. Revert.

### Tests for User Story 2

- [x] T017 [US2] Create eslint boundary violation test fixture — a temporary import from libs/fna/ui inside libs/fna/api/src that should trigger @nx/enforce-module-boundaries; verify failure, then remove fixture

### Implementation for User Story 2

- [x] T018 [US2] Replace wildcard depConstraints in jordylab-fe/eslint.config.mjs with three concrete rules: type:api → onlyDependOnLibsWithTags [type:api, type:shared]; type:ui → [type:api, type:ui, type:shared]; scope:fna → [scope:fna, scope:shared]
- [x] T019 [US2] Verify jordylab-fe/apps/fna/project.json has tag scope:fna; add scope:shared tag convention documentation if no shared lib exists yet
- [x] T020 [US2] Run `bunx nx run-many -t lint` and verify lint passes with the new rules
- [x] T021 [US2] Run the boundary violation fixture from T017 to confirm lint fails, then remove the fixture

**Checkpoint**: User Story 2 is functional — boundary rules are concrete, enforced by lint, and run in CI via the existing lint target.

---

## Phase 5: User Story 3 — Frontend & Backend Test Parity + Coverage (Priority: P3)

**Goal**: Add tests for every fna-ui view (render, populated, empty, error), the fna-api service (success + HTTP error), and ensure both frontend and backend meet 80% coverage enforced in CI.

**Independent Test**: Run `bunx nx run-many -t test --coverage` — all views and API service covered, coverage ≥80% per lib. Run `./gradlew check` — JaCoCo verification passes ≥80% per package.

### Tests for User Story 3

- [x] T022 [P] [US3] Create fna-api coverage for API-backed state in jordylab-fe/libs/fna/api/src/lib/ — superseded from a single fna-api.service.spec.ts to one spec per signal store (article.store.spec.ts, briefing.store.spec.ts, portfolio.store.spec.ts) once the PR #4 review moved state out of components into stores (`/angular-signal-store`); success path with mocked HTTP response and HTTP error path per store, using @ngneat/spectator/vitest + HttpTestingController
- [x] T023 [P] [US3] Create fna-ui view specs in jordylab-fe/libs/fna/ui/src/lib/ — one spec per routed container covering render, populated state, empty state, and error state using @ngneat/spectator/vitest; mocks provided via useValue + vi.fn() per jordylab-fe/AGENTS.md (see Phase 8)

### Implementation for User Story 3

- [x] T024 [US3] Run `bunx nx test fna-api` and verify the API service tests pass and coverage ≥80% for the lib — 13/13 tests pass, 100% line coverage
- [x] T025 [US3] Run `bunx nx test fna-ui` and verify all view specs pass and coverage ≥80% for the lib — 17/17 tests pass, 84.78% line coverage
- [x] T026 [US3] Run `./gradlew check` and verify JaCoCo coverage verification passes ≥80% on the backend (depends on US1 tests being in place) — verification now runs at PACKAGE granularity, not BUNDLE (see Phase 8, FR-016c)

**Checkpoint**: All views and the API service have specs. Both frontend and backend enforce 80% coverage in CI.

---

## Phase 6: User Story 4 — Housekeeping & Documentation Reconciliation (Priority: P4)

**Goal**: Remove unused test infrastructure, record a decision on pre-release dependencies, and ensure AGENTS.md and the infrastructure guide describe no behaviour the code does not implement (SC-005).

**Independent Test**: Verify `AGENTS.md` AI Routing section matches the cloud-primary decision. Verify no references to local-primary routing or fallback for fna remain.

### Implementation for User Story 4

- [ ] T027 [P] [US4] Audit and remove unused test infrastructure in jordylab-fe/ — identify spec files/configs that test nothing and remove them so test config reflects actual usage (FR-017)
- [ ] T028 [P] [US4] Document the pre-release dependency decision in jordylab-be/AGENTS.md or a DECISIONS.md — Spring AI 2.0.0-M2 is a milestone release; record explicit accepted risk with pinned version or a plan to move to stable (FR-018)
- [x] T029 [US4] Final documentation reconciliation — verified AGENTS.md AI Routing section already matches the cloud-primary, per-module-config decision with no local-primary/fallback references for fna; fixed the Reference Docs table, which cited three docs (jordylab-infrastructure-guide.md, jordylab-project-setup.md, jordylab-project-overview.md) that do not exist in the repo — trimmed to only coding-master-prompt.md, which does (SC-005, see Phase 8)

**Checkpoint**: Documentation matches implementation. No behaviour is described that the code does not implement.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Final validation across all stories

- [x] T030 Run `./gradlew build` in jordylab-be/ — full build + tests + JaCoCo verification pass
- [x] T031 Run `bunx nx run-many -t lint` and `bunx nx run-many -t test --coverage` in jordylab-fe/ — lint passes, all tests pass, coverage ≥80% per lib
- [x] T032 [P] Run `./gradlew :test --tests "*ModularityTests*"` and verify Spring Modulith boundary tests pass with the shared/ai changes — 1/1 passes
- [x] T033 Run the quickstart.md validation guide end-to-end and confirm all scenarios pass — corrected several stale statements found in the process (wrong `.yml` extension, wrong trigger/latest-briefing endpoint paths, a nonexistent `ProviderFailureReasonTest` reference, the null-return failure description); see Phase 8

---

## Phase 8: PR #4 Review Remediation

**Purpose**: [PR #4](https://github.com/jordy-swinnen/JordyLab/pull/4) — which implemented Phases 1-7 above — collected 51 review threads (two automated reviews plus human comments) identifying real defects, convention violations, and gaps in conventions that didn't exist yet. This phase closes them out and amends the spec artifacts that either caused or permitted the defects.

**Independent Test**: `./gradlew check` (backend) and `bunx nx run-many -t test lint` (frontend) both pass. See Verification section of the remediation plan for the full checklist.

- [x] T034 Add `jordylab.ai.call-timeout-seconds` to AiModuleConfig and application.yaml, distinct from health-check-timeout-seconds; ResilientAiService.callWithTimeout uses it for the real inference call (FR-008a, fixes the bug where every real briefing call timed out against a 2s health-probe budget)
- [x] T035 Cancel the in-flight Future on timeout (`future.cancel(true)`) and add `@PreDestroy` executor shutdown in ResilientAiService — the executor was never shut down and timed-out calls kept running on leaked threads
- [x] T036 Add BriefingGenerationException (fna.service) carrying ProviderFailureReason; BriefingGeneratorService throws it instead of returning null; add a @RestControllerAdvice mapping it to 503 — fixes a live NullPointerException in FnaService.triggerBriefing() → toBriefingDto(null), reachable from POST /api/fna/briefing/trigger
- [x] T037 [P] Remove all `any()`/inline-throwaway-ArgumentCaptor usage from ResilientAiServiceTest and BriefingGeneratorServiceTest (the only two files in the backend test tree that violated the rule); restore the fallback-prompt-content assertions BriefingGeneratorServiceTest had lost; fix consultsHealthCacheBeforeCall to actually verify the mock interaction instead of re-reading its own stub
- [x] T038 [P] Add assertSoftly to every multi-assertion test in ResilientAiServiceTest, BriefingGeneratorServiceTest, AiModuleConfigTest; extract magic values to named constants
- [x] T039 [P] Move the fna system prompt out of a Java text block into `prompts/fna/briefing-system.st`, loaded as a Resource and rendered via SystemPromptTemplate, per the new "Spring AI / Prompts" convention in jordylab-be/AGENTS.md
- [x] T040 [P] Drop hand-written constructors on ResilientAiService and ProviderHealthCache in favour of @RequiredArgsConstructor; add a Clock @Bean (shared/config/ClockConfiguration) so ProviderHealthCache no longer self-constructs Clock.systemUTC()
- [x] T041 [P] Rewrite all three fna-ui signal stores (ArticleStore, BriefingStore, PortfolioStore) per `/angular-signal-store`; strip API-calling/state logic out of the container components; delete FnaApiService (fully absorbed by the stores)
- [x] T042 [P] Rewrite all three fna-ui component specs and the new store specs to use useValue + vi.fn() instead of hand-written mock classes + useClass, eliminating every `as unknown as ...Mock` cast; create component once in beforeEach; add libs/fna/api/src/lib/mocks/*.model.mock.ts fixture factories; fix eslint.config.mjs depConstraints (dropped the dangling type:shared tag that matched no project)
- [x] T043 Change jacocoTestCoverageVerification from BUNDLE to PACKAGE-level enforcement (FR-016c requires per-module measurement, not a workspace aggregate); exclude the trivial Spring Boot bootstrap class; add the FnaController endpoint tests this surfaced as missing (getPortfolio, getLatestBriefing, removePosition)
- [x] T044 Amend spec.md (FR-008a), contracts/resilient-ai-service.md (Bounds, config table, caller-integration contract — remove the "return null" option), and quickstart.md (stale `.yml` path, wrong trigger/latest endpoints, nonexistent ProviderFailureReasonTest reference, null-return description) to match the corrected implementation
- [x] T045 Add angular-test skill (frontend testing conventions did not exist anywhere before this PR, which is why all three UI specs independently converged on the same wrong mocking pattern); fix ai-endpoint skill (wrong config path shape, `.yml` vs `.yaml`, stale "Ollama primary" claim); note the public-vs-package-private TestBuilder nuance in the test-builder skill
- [x] T046 Add `.claude/hooks/post-test-convention-check.sh` (PostToolUse, advisory) flagging any()/inline-captor/missing-assertSoftly in edited Java tests and useClass/`as unknown as` in edited spec.ts files — the violations in T037/T042 were already written down three times over (AGENTS.md, coding-master-prompt.md, constitution.md) and happened anyway; a hook catches it at edit time instead of at review time
- [x] T047 Add an SDD spec-conformance stage to .github/workflows/claude-pr-review.yml — read the active specs/<slug>/ directory and flag diff behaviour with no FR backing it, unimplemented in-scope FRs, contract docs describing behaviour the code doesn't have, and tasks.md checkboxes marked done that aren't; widen claude_args --allowedTools to include Glob/Grep so the review can find the active spec directory

**Checkpoint**: All 51 PR #4 review threads resolved. Backend and frontend both pass their full check/test/lint suites. Spec artifacts describe the corrected behaviour, not the one that shipped in PR #4.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately
- **Foundational (Phase 2)**: Depends on T001 (JaCoCo) for coverage verification tasks; value objects (T004-T008) are independent of coverage tooling
- **User Stories (Phase 3+)**:
  - US1 depends on Foundational (Phase 2) — needs AiCallResult, ProviderFailureReason, AiModuleConfig, ProviderHealthCache
  - US2 depends only on Setup (Phase 1) — frontend-only, no backend dependency
  - US3 depends on US1 completion (backend coverage needs US1 tests in place) and US2 (frontend coverage needs boundary rules stable)
  - US4 depends on US1 (documentation matches the implemented routing decision)
- **Polish (Phase 7)**: Depends on all user stories complete

### User Story Dependencies

- **US1 (P1)**: Can start after Foundational (Phase 2) — no dependencies on other stories
- **US2 (P2)**: Can start after Setup (Phase 1) — fully independent of US1 (frontend-only)
- **US3 (P3)**: Depends on US1 (backend coverage needs the rewritten ResilientAiService tests) and US2 (frontend tests should run under enforced boundaries)
- **US4 (P4)**: Depends on US1 (documentation must match the implemented AI routing)

### Within Each User Story

- Tests written FIRST and FAIL before implementation
- Value objects before services
- Services before callers
- Full pass before moving to next priority

### Parallel Opportunities

- T001, T002, T003 (Setup) can run in parallel — different projects/configs
- T004, T005, T006, T007 (Foundational value objects) can run in parallel — different files
- T010, T011, T012, T013 (US1 tests) can run in parallel — different test files
- T022, T023 (US3 frontend tests) can run in parallel — different libs
- T027, T028 (US4 housekeeping) can run in parallel — different concerns
- T030, T031, T032, T033 (Polish) — T032 and T033 can partially overlap

---

## Parallel Example: Foundational Phase

```bash
# Launch all value object tasks together (different files, no dependencies):
Task: "Create ProviderFailureReason enum in jordylab-be/.../ProviderFailureReason.java"
Task: "Create AiCallResult record in jordylab-be/.../AiCallResult.java"
Task: "Create AiModuleConfig in jordylab-be/.../AiModuleConfig.java"
Task: "Create ProviderHealth in jordylab-be/.../ProviderHealth.java"
```

## Parallel Example: User Story 1 Tests

```bash
# Launch all US1 test tasks together (different test files):
Task: "Write ProviderHealthCacheTest in jordylab-be/.../ProviderHealthCacheTest.java"
Task: "Write AiModuleConfigTest in jordylab-be/.../AiModuleConfigTest.java"
Task: "Rewrite ResilientAiServiceTest in jordylab-be/.../ResilientAiServiceTest.java"
Task: "Update BriefingGeneratorServiceTest in jordylab-be/.../BriefingGeneratorServiceTest.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (JaCoCo + Vitest coverage config)
2. Complete Phase 2: Foundational (value objects, config, health cache)
3. Complete Phase 3: User Story 1 (ResilientAiService rewrite + BriefingGeneratorService update)
4. **STOP and VALIDATE**: Run US1 tests independently; verify briefing job fails explicitly on provider down
5. Deploy if ready — the core AI resilience gap is closed

### Incremental Delivery

1. Setup + Foundational → Foundation ready
2. Add User Story 1 → Test independently → AI resilience gap closed (MVP!)
3. Add User Story 2 → Test independently → Boundary enforcement closed
4. Add User Story 3 → Test independently → Test parity + coverage closed
5. Add User Story 4 → Verify documentation matches implementation
6. Polish → Full build validation → Feature complete

### Parallel Team Strategy

With multiple developers:

1. Team completes Setup + Foundational together
2. Once Foundational is done:
   - Developer A: User Story 1 (backend AI resilience)
   - Developer B: User Story 2 (frontend boundary enforcement — independent of US1)
3. After US1 + US2 complete:
   - Developer A + B: User Story 3 (test parity + coverage — needs both sides)
4. After US1 + US3:
   - Developer A: User Story 4 (documentation reconciliation)

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Each user story should be independently completable and testable (US3 depends on US1+US2 by nature)
- Verify tests fail before implementing
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
- Avoid: vague tasks, same file conflicts, cross-story dependencies that break independence