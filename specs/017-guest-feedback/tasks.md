---

description: "Task list for Guest Feedback and Admin Triage"
---

# Tasks: Guest Feedback and Admin Triage

**Input**: Design documents from `/specs/017-guest-feedback/`

**Prerequisites**: [plan.md](plan.md) (required), [spec.md](spec.md) (required for user stories), [research.md](research.md), [data-model.md](data-model.md), [contracts/rest-api.md](contracts/rest-api.md), [contracts/linear-client.md](contracts/linear-client.md), [quickstart.md](quickstart.md)

**Tests**: Included per the JordyLab Constitution (IV. Testing Discipline) and `jordylab-be/AGENTS.md` / `jordylab-fe/AGENTS.md` (entity tests are part of the definition of done; stores and components carry specs; e2e journeys cover whole flows).

**Organization**: Tasks are grouped by user story (`US1`–`US5`) to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Which user story this task belongs to (`[US1]`, `[US2]`, `[US3]`, `[US4]`, `[US5]`)
- Exact file paths are included in every task description

## Rules that apply to every task

- **Java**: Never use `var`; blank line before every `return`; method order: public, package-private, private; Lombok `@Builder` / `@RequiredArgsConstructor`, records for DTOs, `Preconditions.checkArgument` only in `build()` (and named mutation methods); canonical entity structure with `BaseEntity`; module layout exactly per `jordylab-be/AGENTS.md`.
- **Java tests**: JUnit 5, AssertJ (`assertSoftly`), Mockito without `any()` (explicit values or an assigned, asserted `ArgumentCaptor`), `@Language("JSON")` on inline JSON, Testcontainers/WireMock/MockMvc per house patterns, canonical `@UtilityClass` `*TestBuilder` fixtures.
- **Angular**: `inject(Service)` over constructor injection, `#field` over `private`, signals for state, Container–Presentation separation, barrel imports, theme CSS variables only (never a hex), shared layout pieces (`PageHeader`, `EmptyState`, `ErrorState`, `SkeletonRows`), loading/empty/error states on every data screen.
- **Frontend tests**: Vitest + `@ngneat/spectator/vitest`, `useValue` + `vi.fn()` mocks, real `signal(...)` instances for store deps, `aXMock()` factories in `libs/feedback/api/src/lib/mocks/`, specs import via barrels.
- **Commits**: `JOR-33: …` subject and `Refs: 017` (or `Refs: 017/T###`) trailer on every commit.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Module skeletons on both stacks so every later task has a home

- [X] T001 Create the `feedback` backend module skeleton per [plan.md](plan.md): package `jordylab-be/src/main/java/dev/jordy/jordylab/feedback/` with `FeedbackConfiguration.java` and `FeedbackProperties.java` (`@ConfigurationProperties(prefix = "jordylab.feedback")`: `daily-ticket-limit: 5`, `title-max-length: 120`, `description-max-length: 5000`, `linear.base-url: https://api.linear.app/graphql`, `linear.api-key: ${LINEAR_API_KEY:}`, `linear.team-key: JOR`), and the `jordylab.feedback` block in `jordylab-be/src/main/resources/application.yaml`; verify `./gradlew :test --tests "*ModularityTests*"` still passes
- [X] T002 [P] Scaffold the frontend domain libs per `jordylab-fe/AGENTS.md` ("Adding a new domain"): `bunx nx g @nx/angular:library feedback-api --directory=libs/feedback/api --tags="scope:feedback,type:api"` and the same for `feedback-ui` (`type:ui`); add `scope:feedback` to the module-boundary rules in `jordylab-fe/eslint.config.mjs` (self + `scope:shared`, and allow `apps/jordylab` to depend on it); create empty `libs/feedback/ui/src/lib/feedback.routes.ts` and both barrels

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Schema, aggregate, enums and repository that every user story builds on

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T003 [P] Create the enums in `jordylab-be/src/main/java/dev/jordy/jordylab/feedback/domain/`: `FeedbackApp` (`GAME_CATALOG`, display "Game Catalog", Linear App label "Game Catalog"), `FeedbackType` (`BUG_REPORT`/`FEATURE_REQUEST`/`IMPROVEMENT`/`IDEA` with displays "Bug report"/"Feature request"/"Improvement"/"Idea" and Linear Type labels Bug/Feature/Improvement/Idea), `FeedbackTicketState` (`NEW`, `ACCEPTED`, `BACKLOG`, `REJECTED`) per [data-model.md](data-model.md)
- [X] T004 [P] Create Flyway migration `jordylab-be/src/main/resources/db/migration/V20261103001__feedback_create_tables.sql` (next free version on the implementation date — keep the `V<yyyyMMdd>NNN` convention): `CREATE SCHEMA IF NOT EXISTS feedback; SET search_path TO feedback;` then `feedback.feedback_ticket` with the columns and the `(sender_subject, created_at)` index per [data-model.md](data-model.md)
- [X] T005 Create `jordylab-be/src/main/java/dev/jordy/jordylab/feedback/domain/FeedbackTicket.java` in the canonical entity structure (`BaseEntity<FeedbackTicket>`, partial `FeedbackTicketBuilder` with all `Preconditions` guards, named mutation methods `moveToBacklog`/`reject`/`markAccepted`/`updateTexts`/`recordAcceptError`/`anonymiseSender` with the state-machine rules) and `jordylab-be/src/test/java/dev/jordy/jordylab/feedback/domain/FeedbackTicketTest.java` (happy-path build asserting every field, null/blank/oversize guards, every legal and illegal transition, `EqualsVerifier` with the three suppressions) — depends on T003
- [X] T006 [P] Create `jordylab-be/src/test/java/dev/jordy/jordylab/feedback/domain/FeedbackTicketTestBuilder.java` (canonical `@UtilityClass`, `public static final` defaults, `aDefaultFeedbackTicket()` / `aFeedbackTicket()`) — depends on T005
- [X] T007 Create `jordylab-be/src/main/java/dev/jordy/jordylab/feedback/domain/repository/FeedbackTicketRepository.java` with `findBySenderSubjectOrderByCreatedDateDesc`, `countBySenderSubjectAndCreatedDateAfter`, `findAllByStateOrderByCreatedDateDesc` + `findAllByOrderByCreatedDateDesc`, `findByIdForAccept` (`@Lock(PESSIMISTIC_WRITE)`) and `findBySenderSubject` per [data-model.md](data-model.md) — depends on T005
- [X] T008 Create `jordylab-be/src/test/java/dev/jordy/jordylab/feedback/domain/repository/FeedbackRepositoryTest.java` (Testcontainers pgvector, following the `FnaRepositoryTest` pattern): save + own-list query, daily-count query, inbox state filter, pessimistic finder — depends on T006, T007

**Checkpoint**: Foundation ready — `feedback.feedback_ticket` exists with the full aggregate, guards and repository coverage; `*ModularityTests*` and the new tests pass.

---

## Phase 3: User Story 1 - A guest sends feedback about an app (Priority: P1) 🎯

**Goal**: Guests see exactly one Settings tab (Feedback) with a form (app from their allowed set, type, title, description) and their own ticket list; submission validates, enforces the five-per-day limit and lands as `NEW`.

**Independent Test**: As a guest, open Settings → Feedback (no other tabs visible), submit a ticket for the Game Catalog, and see it in your own list as New within a minute; a sixth same-day submission is refused; the admin surface for these tickets arrives in US2 (US1 + US2 together form the MVP loop).

### Tests for User Story 1 ⚠️

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [X] T009 [P] [US1] Create `jordylab-be/src/test/java/dev/jordy/jordylab/feedback/service/FeedbackSubmissionServiceTest.java` (Mockito): happy-path submit (state `NEW`, `FeedbackTicketCreated` registered via the aggregate), blank/oversize title and description → `VALIDATION`, app not in the caller's set → `APP_NOT_ALLOWED`, sixth ticket of the day → limit refusal
- [X] T010 [P] [US1] Create `jordylab-be/src/test/java/dev/jordy/jordylab/feedback/rest/controller/FeedbackTicketControllerTest.java` (MockMvc, `@Language("JSON")`): `POST /api/feedback/tickets` → `201` with the guest view; `400 VALIDATION`; `400 APP_NOT_ALLOWED`; `429 FEEDBACK_LIMIT_REACHED`; `GET /api/feedback/tickets` returns only the caller's tickets; `GET /api/feedback/apps` returns the caller's apps; assert the guest JSON never contains `privateNote`/`linearIssueUrl`/`acceptError`/`sender` per [contracts/rest-api.md](contracts/rest-api.md)
- [X] T011 [P] [US1] Create `jordylab-be/src/test/java/dev/jordy/jordylab/feedback/FeedbackModuleTest.java` (`@ApplicationModuleTest` + pgvector, following `GameCatalogModuleTest`'s auth setup): real end-to-end submission incl. the persisted daily-limit count and two senders seeing only their own tickets
- [X] T012 [P] [US1] Extend `jordylab-be/src/test/java/dev/jordy/jordylab/settings/RoleMatrixTest.java` with the feedback rows: guest reaches `POST|GET /api/feedback/tickets` + `GET /api/feedback/apps` and gets 403 on `/api/feedback/inbox` and `/api/settings/users`; admin gets 403 on the guest endpoints and 200 on the inbox; no-role user 403 everywhere
- [X] T013 [P] [US1] Create `jordylab-fe/libs/feedback/api/src/lib/mocks/feedback-ticket.model.mock.ts` (`aFeedbackTicketMock`) and `feedback-app.model.mock.ts` (`aFeedbackAppMock`), re-exported from `jordylab-fe/libs/feedback/api/src/index.ts`
- [X] T014 [P] [US1] Create `jordylab-fe/libs/feedback/api/src/lib/feedback.store.spec.ts`: loading apps and own tickets, successful submit refreshing the list, surfacing the `429 FEEDBACK_LIMIT_REACHED` and validation messages
- [X] T015 [P] [US1] Create `jordylab-fe/libs/feedback/ui/src/lib/feedback-page/feedback-page.component.spec.ts` (+ `feedback-page-view.component.spec.ts`) (Spectator, `useValue` + `vi.fn()`, real `signal(...)` for the store): form renders apps/types, disabled submit until valid, ticket list shows loading/empty/error via the shared layout pieces

### Implementation for User Story 1

- [X] T016 [US1] Create `jordylab-be/src/main/java/dev/jordy/jordylab/feedback/service/FeedbackAppAccessService.java`: resolves the caller's `FeedbackApp` list from the authenticated authorities (`ROLE_guest` → `GAME_CATALOG` today) — the single seam both the apps endpoint and submit validation use — depends on T009–T012 failing first
- [X] T017 [US1] Create `jordylab-be/src/main/java/dev/jordy/jordylab/feedback/FeedbackTicketCreated.java` (module-root record: `senderName`, `appName`, `typeName` — never ticket text) and `jordylab-be/src/main/java/dev/jordy/jordylab/feedback/service/FeedbackSubmissionService.java` (validation, allow-list check, daily count from `FeedbackProperties`, sender subject + display name from the principal, `registerEvent`)
- [X] T018 [US1] Create `jordylab-be/src/main/java/dev/jordy/jordylab/feedback/rest/controller/FeedbackTicketController.java` (`POST|GET /api/feedback/tickets`, `GET /api/feedback/apps`), its `model/` records (guest view per the contract) and `FeedbackExceptionHandler.java` (`VALIDATION`, `APP_NOT_ALLOWED`, `FEEDBACK_LIMIT_REACHED`)
- [X] T019 [US1] Extend `jordylab-be/src/main/java/dev/jordy/jordylab/shared/config/SecurityConfig.java` with the feedback matchers per [contracts/rest-api.md](contracts/rest-api.md) (guest endpoints `hasRole("guest")`, `/api/feedback/inbox/**` `hasRole("admin")`; `/api/settings/**` untouched)
- [X] T020 [US1] Create `jordylab-fe/libs/feedback/api/src/lib/feedback.models.ts`, `feedback-api.service.ts` (`GET/POST /api/feedback/tickets`, `GET /api/feedback/apps`) and `feedback.store.ts` (Custom Signal Store: apps, tickets, submit with error messages)
- [X] T021 [US1] Create the guest tab in `jordylab-fe/libs/feedback/ui/src/lib/feedback-page/` (container `feedback-page.component.ts` + presentation `feedback-page-view.component.ts`: app/type selects, title/description inputs with the length caps, own-ticket list) and `guestFeedbackRoutes` in `feedback.routes.ts` guarded `roleGuard('guest')`
- [X] T022 [US1] Rework the `/settings` routing per [research.md](research.md): `jordylab-fe/apps/jordylab/src/app/app.routes.ts` mounts `settings` as a composed `children` array guarded `roleGuard('admin', 'guest')` with a role-aware default redirect (admin → `users`, guest → `feedback`); `jordylab-fe/libs/settings/ui/src/lib/settings.routes.ts` exports its per-tab routes (`users`, `ai-models`, `app`) with `roleGuard('admin')` on the admin tabs, re-exported from its barrel
- [X] T023 [US1] Extend `jordylab-fe/apps/jordylab/src/app/navigation.ts` with `requiredRole: 'guest'` (symmetric with `'admin'`: hides the item from admins) and a guest "Feedback" item (`/settings/feedback`); update `visibleNavigationGroups(isAdmin, isGuest)` and its caller in `jordylab-fe/apps/jordylab/src/app/app.ts`
- [X] T024 [US1] Create `jordylab-fe/apps/jordylab-e2e/src/feedback.spec.ts`: guest sees only the Feedback tab in Settings, submits a ticket, sees it as New in their own list; add `/settings/feedback` to the `PAGES` list in `accessibility.spec.ts` (and `layout.spec.ts` if it enumerates pages)

**Checkpoint**: Guest loop works end to end — a guest can submit and track feedback; the admin still cannot see anything yet (US2).

---

## Phase 4: User Story 2 - The admin triages tickets in the idea box (Priority: P2)

**Goal**: The admin sees a guest-free Feedback inbox tab listing every ticket (app, type, sender, date, text) with Accept-less triage first: Backlog, Reject, and editing the reply/private note.

**Independent Test**: As the admin, open Settings → Feedback inbox (never the guest tab), see a submitted ticket as New, Backlog it, Reject another (archived, no actions left), set a reply and a private note, and still re-decide the Backlog one; a guest calling any inbox endpoint gets 403.

### Tests for User Story 2 ⚠️

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [X] T025 [P] [US2] Create `jordylab-be/src/test/java/dev/jordy/jordylab/feedback/service/FeedbackTriageServiceTest.java` (Mockito + TestBuilder): inbox list (all + state filter), `NEW → BACKLOG`, `NEW|BACKLOG → REJECTED`, terminal-state transitions throw, `updateTexts` wholesale replacement + length caps, reply allowed in any state, `404` on unknown id
- [X] T026 [P] [US2] Create `jordylab-be/src/test/java/dev/jordy/jordylab/feedback/rest/controller/FeedbackInboxControllerTest.java` (MockMvc, `@Language("JSON")`): `GET /api/feedback/inbox` (+`?state=`), `POST …/{id}/backlog`, `POST …/{id}/reject`, `PUT …/{id}/texts`, with the admin view fields and the `NOT_FOUND`/`INVALID_STATE` error bodies per [contracts/rest-api.md](contracts/rest-api.md)
- [X] T027 [P] [US2] Extend `jordylab-be/src/test/java/dev/jordy/jordylab/settings/RoleMatrixTest.java`: guest 403 on every `/api/feedback/inbox/**` verb
- [X] T028 [P] [US2] Create `jordylab-fe/libs/feedback/api/src/lib/inbox.store.spec.ts`: loading tickets, state filtering, backlog/reject actions, `updateTexts`, friendly failure messages per `reason` code
- [X] T029 [P] [US2] Create `jordylab-fe/libs/feedback/ui/src/lib/inbox-page/inbox-page.component.spec.ts` (+ `inbox-page-view.component.spec.ts`) (Spectator, mocked `InboxStore` via `useValue` + real `signal(...)`): list renders sender/app/type/date/state, actions per state (no actions on terminal), reply/note editing

### Implementation for User Story 2

- [X] T030 [US2] Create `jordylab-be/src/main/java/dev/jordy/jordylab/feedback/service/FeedbackTriageService.java` (list, transitions via the entity's named methods, `updateTexts`)
- [X] T031 [US2] Create `jordylab-be/src/main/java/dev/jordy/jordylab/feedback/rest/controller/FeedbackInboxController.java` (`GET /api/feedback/inbox`, `POST …/{id}/backlog`, `POST …/{id}/reject`, `PUT …/{id}/texts`), its `model/` records (admin view incl. `sender`, `privateNote`, `linearIssueUrl`, `acceptError`) and the `NOT_FOUND`/`INVALID_STATE` rows in `FeedbackExceptionHandler.java`
- [X] T032 [US2] Create `jordylab-fe/libs/feedback/api/src/lib/inbox-api.service.ts` and `inbox.store.ts` (Custom Signal Store: tickets, state filter, backlog/reject, updateTexts, error signals)
- [X] T033 [US2] Create the admin tab in `jordylab-fe/libs/feedback/ui/src/lib/inbox-page/` (container + view: ticket list with state segments, per-state actions, reply/private-note editing) and `adminInboxRoutes` in `feedback.routes.ts` guarded `roleGuard('admin')`
- [X] T034 [US2] Mount the inbox in the `/settings` composition from T022 (`jordylab-fe/apps/jordylab/src/app/app.routes.ts`) and add the admin "Feedback" item (`/settings/inbox`, `requiredRole: 'admin'`) in `jordylab-fe/apps/jordylab/src/app/navigation.ts`
- [X] T035 [US2] Extend `jordylab-fe/apps/jordylab-e2e/src/feedback.spec.ts` with the admin journey: inbox lists the guest's ticket, Backlog keeps it actionable, Reject archives it, reply + private note save; add `/settings/inbox` to the `PAGES` list in `accessibility.spec.ts`

**Checkpoint**: The MVP loop is complete without Linear — guests submit, the admin triages (Backlog/Reject) and replies; guests see the status and reply.

---

## Phase 5: User Story 3 - Accepting a ticket creates a Linear issue (Priority: P3)

**Goal**: Accept (with the optional private note) creates exactly one issue in Linear team `JOR` (Triage, App + Type labels, guest-marked description, `Source: idea box`, no personal data), idempotently, storing the issue id/link admin-only; Linear failures leave the ticket retryable.

**Independent Test**: On the throwaway e2e stack (no `LINEAR_API_KEY`), Accept shows the graceful `LINEAR_UNAVAILABLE` state and the ticket stays New; with a real key (manual quickstart), one click plus a note produces exactly one correctly labelled Triage issue — a double click never a second.

### Tests for User Story 3 ⚠️

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [X] T036 [P] [US3] Create `jordylab-be/src/test/java/dev/jordy/jordylab/feedback/rest/client/LinearClientTest.java` (`@WireMockTest` on the next free `999x` port): team/Triage/label lookups cached, `issueCreate` request body (query + variables + label ids) and `Authorization` header asserted with `@Language("JSON")`, issue-description template exactly per [contracts/linear-client.md](contracts/linear-client.md) (guest marking, `Source: idea box` last), adopt-on-retry search, unconfigured key and HTTP failure → `LinearUnavailableException`
- [X] T037 [P] [US3] Create `jordylab-be/src/test/java/dev/jordy/jordylab/feedback/service/AcceptTicketServiceTest.java` (Mockito, `LinearClient` mocked, explicit values): happy accept stores id + url and marks `ACCEPTED`, second accept returns the stored result with no second client call, Linear failure records `accept_error` and leaves the state unchanged, retry after an error searches first and adopts an existing issue, terminal states throw
- [X] T038 [P] [US3] Extend `jordylab-be/src/test/java/dev/jordy/jordylab/feedback/rest/controller/FeedbackInboxControllerTest.java` with `POST …/{id}/accept` (optional `privateNote` body): `200` admin view with `linearIssueUrl`, `503 LINEAR_UNAVAILABLE` with the ticket unchanged

### Implementation for User Story 3

- [X] T039 [US3] Create `jordylab-be/src/main/java/dev/jordy/jordylab/feedback/rest/client/LinearClient.java` per [contracts/linear-client.md](contracts/linear-client.md): module-private `RestClient` with short timeouts (built `@PostConstruct`), raw-key `Authorization` header (verified with a `viewer` ping), cached team-by-key/state/label lookups, `issueCreate`, the adopt-on-retry search, `LinearUnavailableException`
- [X] T040 [US3] Create `jordylab-be/src/main/java/dev/jordy/jordylab/feedback/service/AcceptTicketService.java`: pessimistic-lock finder, idempotency (stored issue id short-circuits), search-first on `accept_error`, `markAccepted` on success, `recordAcceptError` on failure — depends on T037, T039
- [X] T041 [US3] Add `POST /api/feedback/inbox/{id}/accept` to `FeedbackInboxController.java` and the `503 LINEAR_UNAVAILABLE` row to `FeedbackExceptionHandler.java`
- [X] T042 [US3] Add `accept(id, privateNote?)` to `jordylab-fe/libs/feedback/api/src/lib/inbox.store.ts` (+ spec extension: success refresh, `LINEAR_UNAVAILABLE` message, in-flight guard against double clicks)
- [X] T043 [US3] Add the Accept action with its optional private-note dialog, the Linear link (admin only) and the `acceptError` display to `jordylab-fe/libs/feedback/ui/src/lib/inbox-page/inbox-page-view.component.ts` (+ `inbox-page-view.component.spec.ts` extension)
- [X] T044 [US3] Extend `jordylab-fe/apps/jordylab-e2e/src/feedback.spec.ts`: accept without a key shows the Linear-unavailable state, the ticket stays New, retry is offered

**Checkpoint**: The full accept path works — idempotent against double clicks and timeouts, graceful without the key, real with it.

---

## Phase 6: User Story 4 - The guest sees what happened to their feedback (Priority: P4)

**Goal**: The guest's own list shows each ticket's state (New, Accepted, Backlog, Rejected) and the admin's reply, expandable per ticket — and provably nothing else.

**Independent Test**: After the admin Backlogs/Rejects tickets and sets a reply (e2e, no Linear needed), the guest sees exactly those states and that reply; after a real accept (manual quickstart), the guest sees Accepted and the reply, never the Linear link or any private note.

### Tests for User Story 4 ⚠️

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [X] T045 [P] [US4] Extend `jordylab-fe/libs/feedback/ui/src/lib/feedback-page/feedback-page-view.component.spec.ts`: state badge per state, expandable ticket detail showing the admin reply, no rendering path that could show a link or note (view model only carries the guest fields)

### Implementation for User Story 4

- [X] T046 [US4] Add the state badge and expandable reply detail to `feedback-page-view.component.ts` in `jordylab-fe/libs/feedback/ui/src/lib/feedback-page/` (guest view model already excludes admin fields by contract)
- [X] T047 [US4] Extend `jordylab-be/src/test/java/dev/jordy/jordylab/feedback/rest/controller/FeedbackTicketControllerTest.java`: after a state change and reply, the guest response shows exactly `state` + `reply` and never `linearIssueUrl`/`privateNote`/`acceptError` (JSON field-absence assertions)
- [X] T048 [US4] Extend `jordylab-fe/apps/jordylab-e2e/src/feedback.spec.ts`: after the admin's Backlog/Reject/reply actions, the guest sees the new states and the reply, and nothing more

**Checkpoint**: The loop is closed both ways — guests know what happened to every ticket they sent.

---

## Phase 7: User Story 5 - The admin hears about new feedback (Priority: P5)

**Goal**: Every newly saved ticket sends the admin exactly one Ntfy notification without the ticket's text; a delivery failure never blocks the submission.

**Independent Test**: Submit a ticket against a stubbed Ntfy endpoint (WireMock) and assert exactly one publish with sender/type/app only; in the manual quickstart, the phone receives the notification.

### Tests for User Story 5 ⚠️

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [X] T049 [P] [US5] Extend `jordylab-be/src/test/java/dev/jordy/jordylab/mobile/service/MobileNotificationListenerTest.java` (or create it beside the listener): a `FeedbackTicketCreated` event produces exactly one `NtfyClient.publish` call whose body contains the sender, type and app and neither the title nor the description (assigned `ArgumentCaptor` asserted)

### Implementation for User Story 5

- [X] T050 [US5] Add the `@ApplicationModuleListener` for `FeedbackTicketCreated` to `jordylab-be/src/main/java/dev/jordy/jordylab/mobile/service/MobileNotificationListener.java` ("New feedback ticket", "{sender} sent a {type} for the {app} app.", `appLinkUrl("feedback-inbox")`), reusing the swallow-and-log failure behaviour
- [X] T051 [US5] Add `'feedback-inbox': '/settings/inbox'` to the screen map in `jordylab-fe/libs/shared/platform/api/src/lib/app-link.service.ts` (+ its spec, following the `settings-users` case)

**Checkpoint**: New feedback announces itself; nothing about the notification can fail a submission.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Edge cases, operations and documentation that span stories

- [X] T052 [P] Create `jordylab-be/src/main/java/dev/jordy/jordylab/feedback/service/FeedbackAnonymisationListener.java` consuming `shared.event.UserAccessRemoved` (anonymise that sender's `sender_display_name`, tickets stay) + its test — the spec's deleted-account edge case
- [X] T053 [P] **Owner task**: add `LINEAR_API_KEY` to `deploy/k8s/overlays/prod/secrets.sops.yaml` (SOPS + age, edited by Jordy — never let an agent print the value); no manifest change is needed, `deploy/k8s/base/backend.yaml` already `envFrom`s `jordylab-secrets` — the owner added it locally (`jordylab-be/.env`) and to the SOPS file on 2026-11-03 — confirmed saved (the key name is present in `stringData`); the change lives in the main checkout and was uncommitted at check time, so commit it there (or merge it here) before the next prod deploy
- [X] T054 [P] Update docs: add the `feedback` row (module, schema `feedback`, purpose) to the modules table in `AGENTS.md`, add `feedback` → jordylab-be to the Schema Ownership list and a short "Feedback module" section (event-only Ntfy wiring, Linear client, `LINEAR_API_KEY`) in `jordylab-be/AGENTS.md`
- [X] T055 Run the full validation per [quickstart.md](quickstart.md): backend suite incl. `*ModularityTests*`; `bunx nx run-many -t test` and `-t lint` (ESLint + Oxlint) plus `bun tools/check-theme-tokens.mjs` in `jordylab-fe`; `e2e/run.sh web`; then the manual scenario table with a real `LINEAR_API_KEY` (scenarios 4 and 6–7 create real, deletable Linear issues) — automated parts all green (backend feedback suite + JaCoCo gate, 17/17 frontend projects, ESLint 20 + Oxlint 21, 218/218 Playwright); the manual real-key scenarios remain with the owner

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: T001 and T002 are independent of each other (backend vs frontend scaffold) — run in parallel
- **Foundational (Phase 2)**: depends on T001; blocks all user stories
- **User Stories (Phases 3–7)**: each depends on Phase 2; sequential in priority order is the default
- **Polish (Phase 8)**: T052–T054 after the stories they touch; T055 last

### User Story Dependencies

- **US1 (P1)**: after Phase 2 — no story dependencies. Delivers the guest loop
- **US2 (P2)**: after Phase 2; integrates with US1's tickets (same aggregate). **US1 + US2 = the MVP** (submit + triage without Linear)
- **US3 (P3)**: after US2 (accept acts on inbox tickets); needs `LINEAR_API_KEY` only for the manual path — all automated paths run without it
- **US4 (P4)**: after US2 for the state/reply display; its accepted-state assertions (T047/T048) land after US3
- **US5 (P5)**: after US1 only (the event fires at submission) — can run in parallel with US2–US4

### Within Each User Story

- Tests first (watch them fail), then services, then controllers, then frontend store, then UI, then e2e
- Backend and frontend tasks of one story touch different stacks — the `[P]`-marked pairs can run in parallel

### Parallel Opportunities

- Phase 1: T001 ∥ T002
- Phase 2: T003 ∥ T004, then T005 → (T006 ∥ T007) → T008
- Every story: all `[P]` test tasks together; backend implementation ∥ frontend implementation
- US5 can run alongside US2–US4

---

## Implementation Strategy

### MVP First (User Stories 1 + 2)

1. Complete Phase 1 + Phase 2
2. Complete US1, validate the guest loop independently (e2e scenario 1)
3. Complete US2, validate the triage loop — **this is the deployable MVP**: feedback flows and is triaged without Linear
4. **STOP and VALIDATE** against [quickstart.md](quickstart.md) scenarios 1, 3 (without accept), 8, 9, 10

### Incremental Delivery

5. Add US3 → validate with the real key (scenarios 4, 6, 7)
6. Add US4 → validate the closed loop (scenario 5)
7. Add US5 → validate the notification (scenario 2)
8. Polish phase → docs, secret, full suites

### Notes

- Every task commits with `JOR-33: …` + `Refs: 017/T###`
- After the last task: run the `linear-sync-drafts` skill so JOR-33 tracks spec 017's state from this file
- Never hand-seed `feedback.feedback_ticket` — data comes through the app (guest submits) or test fixtures
