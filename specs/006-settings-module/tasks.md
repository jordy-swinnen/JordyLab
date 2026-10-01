# Tasks: Settings Module (Users & AI Models)

**Input**: Design documents from `/specs/006-settings-module/` (plan.md, spec.md, research.md D1–D12, data-model.md,
contracts/, quickstart.md)

**Prerequisites**: plan.md (required), spec.md (required — 7 user stories), research.md, data-model.md, contracts/

**Tests**: Included — the spec's success criteria demand automated proof (SC-001 role matrix, SC-004 WireMock fallback),
the constitution requires entity tests + TestBuilders as definition of done, and repo test discipline applies (JUnit
5/AssertJ/Mockito/Testcontainers/WireMock/MockMvc; Vitest/Spectator). Test tasks precede their implementation tasks
within each story where practical (red → green).

**Organization**: Tasks grouped by user story (spec.md US1–US7, priority order) so each story is independently
implementable and testable. Repo skills are referenced where they apply (`/new-module`, `/flyway-migration`, `/entity`,
`/test-builder`, `/angular-signal-store`, `/angular-test`, `/modularity-check`).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Which user story this task belongs to (US1–US7); Setup/Foundational/Polish have no story label
- Include exact file paths in descriptions

## Path Conventions

Web app monorepo: backend = `jordylab-be/src/main/java/dev/jordy/jordylab/…` (+ `src/test/…`, `src/main/resources/…`),
frontend = `jordylab-fe/…` (libs, apps). Full layout in [plan.md](plan.md) → Project Structure.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Module + libs skeleton, schema, config keys

- [X] T001 Create the `settings` module skeleton per repo layout (root package `dev.jordy.jordylab.settings` +
  `SettingsProperties` for `jordylab.settings.*` incl. `guest-chat.daily-limit`, `model-catalog.cache-ttl`,
  `notifications.ntfy.*`) in `jordylab-be/src/main/java/dev/jordy/jordylab/settings/` — follow `/new-module` (package
  layout from `jordylab-be/AGENTS.md`, no new sub-packages); run `ModularityTests` green
- [X] T002 [P] Scaffold frontend domain libs: `jordylab-fe/libs/settings/api` + `jordylab-fe/libs/settings/ui` via
  `bunx nx g @nx/angular:library` (tags `scope:settings,type:api|ui`), add `@jordylab-fe/settings/api|ui` paths to
  `jordylab-fe/tsconfig.base.json`, add `scope:settings` to the `type:app` constraint in
  `jordylab-fe/eslint.config.mjs`, create barrels + empty `settingsRoutes` (`settings.routes.ts`) per
  `jordylab-fe/AGENTS.md` "Adding a new domain"
- [X] T003 [P] Create Flyway migration
  `jordylab-be/src/main/resources/db/migration/V<yyyyMMdd>__settings_create_tables.sql` with the three tables + uniques
  per [data-model.md](data-model.md) (`settings` schema: `ai_feature_model_setting`, `ai_feature_last_run` unique on
  `feature_key`, `guest_chat_usage` unique on `(user_subject, usage_date)`) — follow `/flyway-migration`
- [X] T004 [P] Add config keys with defaults in `jordylab-be/src/main/resources/application.yaml`:
  `jordylab.ai.gateway.base-url` (default `https://openrouter.ai/api/v1`), `jordylab.ai.features.<key>.model` defaults
  for the four feature keys, `jordylab.ai.fallback.provider=anthropic` + `jordylab.ai.fallback.model=claude-sonnet-5`,
  `jordylab.settings.*` block; add env names `OPENROUTER_API_KEY`, `KEYCLOAK_BACKEND_SECRET`, `NTFY_*` to the `.env`
  template documentation (names only — never values)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Realm, security matrix, Keycloak test base, shared auth primitives — blocks all user stories

- [X] T005 ⚠️ **STOP-AND-REPORT GATE** (halt and report to the user before executing): rewrite
  `jordylab-be/compose/keycloak-realm-export.json` per [research.md](research.md) D10 — `registrationAllowed: true`,
  `registrationEmailAsUsername: true`, password policy (`length(12) and notUsername and notEmail`), new realm roles
  `admin` + `guest`, move user `jordy` from `jordylab-user` to `admin` (keeps `gamecatalog-scanner`, `offline_access`),
  **remove the `jordylab-user` role**, new confidential client `jordylab-backend` (service account, secret from
  `KEYCLOAK_BACKEND_SECRET`, service account granted realm-management `view-users`, `manage-users`, `view-roles`);
  `jordylab-host` and `gamecatalog-script` unchanged
- [X] T006 Style the Keycloak registration page in the custom theme `jordylab-be/compose/keycloak-theme/jordylab/` (
  CSS-only over `keycloak.v2`, Night Lab tokens — register.ftl renders via the base theme; no new ftl templates unless
  required)
- [X] T007 Rewrite the security filter chain in
  `jordylab-be/src/main/java/dev/jordy/jordylab/shared/config/SecurityConfig.java` to the deny-by-default matrix
  in [contracts/access-matrix.md](contracts/access-matrix.md) (`/api/fna/**` + `/api/settings/**` → `admin`; Game
  Catalog reads + chat → `admin|guest`; gamecatalog writes/sources/library → `admin`; `ingest/client` → `admin` (was
  `jordylab-user`); scan/check → `gamecatalog-scanner` unchanged; `anyRequest().denyAll()`); update
  `jordylab-be/src/test/java/dev/jordy/jordylab/shared/config/SecurityConfigTest.java` for the new role mapping
- [X] T008 Add Keycloak integration-test base: `com.github.dasniko:testcontainers-keycloak:4.3.1` (test scope) in
  `jordylab-be/build.gradle.kts` + `KeycloakTestContainer` fixture in
  `jordylab-be/src/test/java/dev/jordy/jordylab/settings/` (image `quay.io/keycloak/keycloak:26.3.2`, test realm import
  with `admin`/`guest`/pending/scanner users, `issuer-uri` override) — verify Testcontainers version alignment with the
  Boot BOM per research §1.3; write the role-matrix tests (guest 403 on `/api/fna/**` + `/api/settings/**` + gamecatalog
  writes; pending user 403 everywhere; scanner scan/check flow unchanged; admin passes) — proves SC-001
- [X] T009 [P] Extend `jordylab-fe/libs/shared/auth/src/lib/auth.service.ts` with a `roles` signal (parsed from
  `tokenParsed.realm_access.roles`), add `roleGuard(role)` in `jordylab-fe/libs/shared/auth/src/lib/role.guard.ts` (
  unauthenticated → `/login`; wrong role → `/awaiting-approval`), export both from
  `jordylab-fe/libs/shared/auth/src/index.ts`; specs per `/angular-test` in `jordylab-fe/libs/shared/auth/src/lib/` (
  guard via `TestBed.runInInjectionContext` with real `signal` mocks)

**Checkpoint**: Foundation ready — registration exists, the matrix is enforced (proven by T008), the frontend knows
roles. User stories can now run in parallel tracks.

---

## Phase 3: User Story 1 — Only approved people get in (Priority: P1) 🎯 MVP

**Goal**: A self-registered account is pending, sees only the "awaiting approval" page, and is denied on every API (spec
US1, FR-001–FR-005)

**Independent Test**: Register on the live stack, log in → only the awaiting-approval page renders and every backend
call is denied (quickstart scenario 1)

- [X] T010 [P] [US1] Write the pending-isolation integration test (red first) in
  `jordylab-be/src/test/java/dev/jordy/jordylab/settings/SettingsModuleTest.java`: register a user via the test realm →
  password-grant login → token carries no app role → every endpoint group (fna, settings, gamecatalog read + write +
  chat) returns 403; run against the T008 Keycloak container
- [X] T011 [P] [US1] Create the awaiting-approval page (standalone route outside the nav shell so no tabs render) in
  `jordylab-fe/apps/jordylab/src/app/awaiting-approval/` + route `{ path: 'awaiting-approval', … }` in
  `jordylab-fe/apps/jordylab/src/app/app.routes.ts`, with `roleGuard` redirecting authenticated no-role users there;
  spec in `awaiting-approval.component.spec.ts` (guest/admin tokens → NOT redirected; no-role token → redirected)

**Checkpoint**: US1 independently functional — the gate exists end to end. This is the MVP: safe to deploy for
friends-only sign-up (nobody gets in until US2 exists).

---

## Phase 4: User Story 2 — Admin approves, rejects and revokes (Priority: P1)

**Goal**: The in-app Users administration page (approve/reject/revoke/reset + pending badge) over the Keycloak Admin
REST API (spec US2, FR-006/FR-007)

**Independent Test**: With one pending user (test fixture): approve → their next login shows the Game Catalog; revoke →
access gone within one token lifetime; last-admin reject blocked (quickstart scenarios 2–5)

- [X] T012 [US2] Create `KeycloakAdminClient` in
  `jordylab-be/src/main/java/dev/jordy/jordylab/settings/rest/client/KeycloakAdminClient.java` (Spring `RestClient` +
  client-credentials token fetch/refresh for `jordylab-backend`; list/search users, get user, role-mapping add/remove,
  `reset-password` with `temporary: true`, `logout` sessions, enable/disable, get role) per [research.md](research.md)
  D3; WireMock tests in
  `jordylab-be/src/test/java/dev/jordy/jordylab/settings/rest/client/KeycloakAdminClientTest.java` (token refresh, each
  Admin REST call, failure → explicit `KEYCLOAK_UNAVAILABLE`)
- [X] T013 [US2] Implement `KeycloakUserAdministrationService` in
  `jordylab-be/src/main/java/dev/jordy/jordylab/settings/service/KeycloakUserAdministrationService.java` — derived
  status per [data-model.md](data-model.md) (PENDING/APPROVED/REJECTED), approve/reject/revoke/reset-password
  orchestration, `LAST_ADMIN_PROTECTED` guard (FR-007), generated temporary password (returned once, never logged); unit
  tests with the client mocked (explicit values, assigned `ArgumentCaptor`) in
  `jordylab-be/src/test/java/dev/jordy/jordylab/settings/service/KeycloakUserAdministrationServiceTest.java`
- [X] T014 [US2] Implement `SettingsUsersController` + request/response records in
  `jordylab-be/src/main/java/dev/jordy/jordylab/settings/rest/controller/`
  per [contracts/settings-users-api.md](contracts/settings-users-api.md) (`GET /api/settings/users?status=`,
  `GET …/pending-count`, `POST …/{id}/approve|reject|revoke|reset-password`, error shapes `USER_NOT_FOUND`/
  `LAST_ADMIN_PROTECTED`/`USER_NOT_APPROVED`/`KEYCLOAK_UNAVAILABLE`); MockMvc tests (`@WebMvcTest` + `@MockitoBean` +
  `@Language("JSON")`) in
  `jordylab-be/src/test/java/dev/jordy/jordylab/settings/rest/controller/SettingsUsersControllerTest.java`
- [X] T015 [P] [US2] Create the settings API lib surface in `jordylab-fe/libs/settings/api/src/lib/` —
  `SettingsApiService` (HttpClient wrapper for the users contract), `users.store.ts` signal store (
  pending/approved/rejected lists, actions with loading/error state) per `/angular-signal-store`, user models + mock
  factories in `mocks/`; specs per `/angular-test` (real `signal(...)` instances + `vi.fn` via `useValue`)
- [X] T016 [US2] Build the Users page in `jordylab-fe/libs/settings/ui/src/lib/users-page/` — container (
  `users-page.component.ts` injects the store) + presentation (`users-page-view.component.ts`, `input.required`/
  `output`, zero DI) with status sections, per-user Approve/Reject/Revoke/Reset actions, one-time temporary-password
  reveal; specs with store mock per repo conventions
- [X] T017 [US2] Wire the app shell: settings route (`{ path: 'settings', canActivate: [authGuard, roleGuard]… }` via
  `loadChildren` of `settingsRoutes`) + "Settings" nav group with pending-count badge (from `pending-count`) +
  username-aware visibility in `jordylab-fe/apps/jordylab/src/app/app.ts` + `app.html`; update the pinned nav assertions
  in `jordylab-fe/apps/jordylab/src/app/app.spec.ts`

**Checkpoint**: US1 + US2 together: sign-up → approval → guest works end to end.

---

## Phase 5: User Story 3 — Guests see only the Game Catalog (Priority: P1)

**Goal**: Guest scope enforced in backend (done in T007/T008) and mirrored in the frontend nav/guards, plus the
persisted daily chat limit (spec US3, FR-002/FR-003/FR-009)

**Independent Test**: Guest login → only the Game Catalog tab (Sources hidden); direct `/fna`/`/settings` routes denied
by the guard; after the limit (set 3 via env) the 4th chat shows the friendly message and no AI call is made (quickstart
scenarios 2, 10)

- [X] T018 [US3] Create the `GuestChatUsage` entity + repository per [data-model.md](data-model.md) in
  `jordylab-be/src/main/java/dev/jordy/jordylab/settings/domain/` — canonical entity structure via `/entity` (UUID id,
  `(user_subject, usage_date)` unique, builder guards) + race-safe native upsert `incrementUsage(subject, date)` in the
  repository; entity test + TestBuilder via `/test-builder` in
  `jordylab-be/src/test/java/dev/jordy/jordylab/settings/domain/`
- [X] T019 [US3] Implement `GuestChatLimitFilter` (OncePerRequestFilter in
  `jordylab-be/src/main/java/dev/jordy/jordylab/settings/rest/controller/GuestChatLimitFilter.java`, registered after
  security, matches `POST /api/gamecatalog/chat`) — exempt admins; pre-call check against the persisted count → `429`
  `{"reason":"CHAT_LIMIT_REACHED","resetsAt":…}` when ≥ `jordylab.settings.guest-chat.daily-limit` (default 20);
  increment only on 2xx after `chain.doFilter`; tests (limit boundary, admin exempt, failed-call-not-counted, concurrent
  upsert) in `jordylab-be/src/test/java/dev/jordy/jordylab/settings/rest/controller/GuestChatLimitFilterTest.java`
- [X] T020 [US3] Add the guest-at-limit integration test to
  `jordylab-be/src/test/java/dev/jordy/jordylab/settings/SettingsModuleTest.java`: guest token at the limit → 429 with
  `resetsAt`, `ResilientAiService` never invoked (`@MockitoBean` verify with explicit values); count survives a
  simulated restart (new EntityManager session)
- [X] T021 [P] [US3] Make the nav role-aware in `jordylab-fe/apps/jordylab/src/app/app.ts`: per-`NavItem`/group required
  role computed from `AuthService.roles()` (guest sees only Game Catalog group with Library + Chat; Sources item
  admin-only; Settings group admin-only); update `jordylab-fe/apps/jordylab/src/app/app.spec.ts` for guest/admin nav
  renders
- [X] T022 [P] [US3] Apply guards inside the domain route libs (so all three apps inherit):
  `jordylab-fe/libs/gamecatalog/ui/src/lib/gamecatalog.routes.ts` (shell → `admin|guest`; sources + management
  children → `admin`; grid/detail/chat open to both) and `jordylab-fe/libs/fna/ui/src/lib/fna.routes.ts` (→ `admin`);
  verify `apps/fna` + `apps/gamecatalog` harnesses pick them up; specs for both route files
- [X] T023 [US3] Handle `429 CHAT_LIMIT_REACHED` in the frontend chat flow (
  `jordylab-fe/libs/gamecatalog/api/src/lib/chat.store.ts` or equivalent): friendly "limit reached, resets at {time}"
  message, no retry; spec with a `useValue` HTTP mock returning the 429 body

**Checkpoint**: US1 + US2 + US3: the full guest experience is bounded and role-correct.

---

## Phase 6: User Story 4 — Resilient AI calls (Priority: P1)

**Goal**: OpenRouter primary → Anthropic `claude-sonnet-5` fallback per AI call, with per-feature registry, call
recording (event + metrics) and the Spring AI GA bump; Ollama removed (spec US4,
FR-011/FR-012/FR-013/FR-014/FR-016/FR-017)

**Independent Test**: With the gateway failing for each failure reason in turn, every AI feature still answers through
the fallback with `fallbackUsed` recorded; both providers down → explicit failure (quickstart scenario 8)

- [x] T024 [US4] (done; `spring.ai.model.chat` deliberately left **unset** — each starter only matches its own value, so `openai` would switch the Anthropic fallback off; proven by `AiGatewayWiringTest`) Bump Spring AI 2.0.0-M2 → **2.0.1 GA** in `jordylab-be/build.gradle.kts` (`springAiVersion`), add
  `spring-ai-starter-model-openai`, set `spring.ai.model.chat: openai` + map gateway config `jordylab.ai.gateway.*` →
  `spring.ai.openai.base-url/api-key` (via yaml placeholders, research D2) in
  `jordylab-be/src/main/resources/application.yaml`; follow the AGENTS.md GA-move procedure (check 2.0.1 migration notes
  incl. the Anthropic-official-SDK change, re-run `ResilientAiServiceTest` + module tests, `./gradlew test` green)
- [x] T025 ⚠️ **STOP-AND-REPORT GATE** (halt and report to the user before executing): remove all Ollama support —
  `spring-ai-starter-model-ollama` + `org.testcontainers:testcontainers-ollama` from `jordylab-be/build.gradle.kts`, the
  `OllamaContainer` bean from `jordylab-be/src/test/java/dev/jordy/jordylab/TestcontainersConfiguration.java`, the
  commented Ollama service from `jordylab-be/compose.yaml` (keep pgvector + advisors deps); verify `./gradlew build`
  green and `SC-005` grep clean (docs history allowed) — pre-approved by Jordy 2026-09-30; done in spec 011 BUG-009
- [x] T026 [US4] Refactor `jordylab-be/src/main/java/dev/jordy/jordylab/shared/ai/`: `AiFeature` enum (four keys with
  display name, module, description per [research.md](research.md) §2.1), `AiModelResolver` port (
  `resolveModel(AiFeature)` + `isModelKnown`), `AiCallCompleted` event record, replace `AiModuleConfig`/
  `jordylab.ai.modules` with feature/fallback config records (`jordylab.ai.features`, `jordylab.ai.fallback`), add
  `MODEL_NOT_FOUND` to `ProviderFailureReason`; keep the `@NamedInterface("ai")` exports; unit tests for registry +
  config binding
- [x] T027 [US4] Rewrite `ResilientAiServiceTest` (red first) in
  `jordylab-be/src/test/java/dev/jordy/jordylab/shared/ai/ResilientAiServiceTest.java`: WireMock-backed primary (
  OpenAI-compatible endpoint) + mocked `AnthropicChatModel` — for **each** failure reason (unreachable, timeout, 429,
  401, model-not-found) the call retried **once** on the fallback with `AiCallResult` recording actual provider/model/
  `fallbackUsed`; both-fail → explicit failure (no silent empty result); per-feature model resolution via the port;
  metrics + event publication asserted (assigned captors, explicit values)
- [x] T028 [US4] Rewrite `ResilientAiService` in
  `jordylab-be/src/main/java/dev/jordy/jordylab/shared/ai/ResilientAiService.java` until T027 is green:
  `call(AiFeature, systemPrompt, userPrompt)` resolves the model via `AiModelResolver`, builds `OpenAiChatOptions`/
  `AnthropicChatOptions` per provider, tries primary then falls back on the mapped reasons, keeps the executor timeout +
  multi-generation `extractText`, publishes `AiCallCompleted` (`ApplicationEventPublisher`) + Micrometer counters (
  `jordylab.ai.calls`: feature/provider/outcome/fallback tags — expose `metrics` actuator endpoint for `admin`)
- [ ] T029 [P] [US4] Update the four AI call sites to `AiFeature`:
  `jordylab-be/src/main/java/dev/jordy/jordylab/fna/service/BriefingGeneratorService.java` (`FNA_BRIEFING`),
  `gamecatalog/service/EnrichmentService.java` (`GAMECATALOG_ENRICHMENT`), `gamecatalog/service/ChatService.java` (
  translate → `GAMECATALOG_CHAT_QUERY`, compose → `GAMECATALOG_CHAT_ANSWER`); keep prompts + parsing unchanged; module
  tests (`GameCatalogModuleTest`, fna module test) stay green
- [x] T030 [US4] Add the default config-backed `AiModelResolver` (reads `jordylab.ai.features.<key>.model`) in
  `jordylab-be/src/main/java/dev/jordy/jordylab/shared/ai/` + unit test; add a boot smoke test in
  `jordylab-be/src/test/java/dev/jordy/jordylab/shared/ai/` proving both `OpenAiChatModel` (gateway base URL) and
  `AnthropicChatModel` beans exist with `spring.ai.model.chat=openai` and one plain call routes through the primary (
  research §4.1)

**Checkpoint**: US4 done — the AI layer is gateway-first, resilient and observable, on GA. US1–US3 unaffected (different
modules).

---

## Phase 7: User Story 5 — Everyone manages their own login details (Priority: P2)

**Goal**: The "My account" user menu with AIA-driven password/profile changes (spec US5, FR-008)

**Independent Test**: As a guest, change password and profile (name/email) from the user menu and log back in with the
new details (quickstart scenario 6)

- [ ] T031 [P] [US5] Create `UserMenuComponent` in `jordylab-fe/libs/shared/auth/src/lib/user-menu.component.ts` —
  avatar/initial + dropdown (spartan menu helm added via `bunx @spartan-ng/cli@latest add menu` if no suitable local
  component exists) with Change password → `login({ action: 'UPDATE_PASSWORD' })`, Edit profile →
  `login({ action: 'UPDATE_PROFILE' })`, Sign out → existing logout; export from the barrel; spec with the AuthService
  mocked (`useValue` + `vi.fn`)
- [ ] T032 [US5] Replace the static sign-out button with the user menu in the shell header (desktop + mobile) in
  `jordylab-fe/apps/jordylab/src/app/app.html` + `app.ts`; update `jordylab-fe/apps/jordylab/src/app/app.spec.ts` (menu
  renders for any authenticated user — admin and guest)
- [ ] T033 [US5] Verify AIA live against the real Keycloak 26.3 (quickstart scenario 6): `UPDATE_PASSWORD`
  re-authentication flow, `UPDATE_PROFILE` name + email change, and the email-as-username username-sync caveat (keycloak
  #13988/#16679 — research §4.3); record the verified outcome (and any workaround) in
  `specs/006-settings-module/research.md` §4

**Checkpoint**: US5 done — routine account changes no longer need the admin.

---

## Phase 8: User Story 6 — Choose a model per AI feature (Priority: P2)

**Goal**: The AI Models page: per-feature saved models over the live gateway catalog, last-run display, unavailable
flagging (spec US6, FR-014/FR-015/FR-016)

**Independent Test**: Change the game-description feature's model, trigger one enrichment → the result records the new
model, no restart (quickstart scenario 7, SC-003)

- [ ] T034 [P] [US6] Create `AiFeatureModelSetting` + `AiFeatureLastRun` entities + repositories
  per [data-model.md](data-model.md) in `jordylab-be/src/main/java/dev/jordy/jordylab/settings/domain/` (canonical
  `/entity` structure, `updateModel` mutation registering `AiFeatureModelSettingUpdated`); entity tests + TestBuilders
  via `/test-builder` in `jordylab-be/src/test/java/dev/jordy/jordylab/settings/domain/`
- [ ] T035 [US6] Implement `AiModelSettingsService` in
  `jordylab-be/src/main/java/dev/jordy/jordylab/settings/service/AiModelSettingsService.java` —
  `@Primary AiModelResolver` (setting row → cached effective model, else config default; cache invalidated on
  `AiFeatureModelSettingUpdated`), `AiCallCompleted` listener upserting `AiFeatureLastRun` (injected `Clock`); unit
  tests (resolution precedence, invalidation on update, listener upsert incl. failure reasons)
- [ ] T036 [US6] Create `OpenRouterModelCatalogClient` in
  `jordylab-be/src/main/java/dev/jordy/jordylab/settings/rest/client/OpenRouterModelCatalogClient.java` — keyless
  `GET {jordylab.ai.gateway.base-url}/models`, TTL cache, trim to text-output chat models (exclude `~` aliases),
  per-MTok pricing (×1M, `"-1"` → null), `expiration_date` → expiring flag; WireMock tests (list shape, cache TTL,
  unreachable → stale-or-`GATEWAY_CATALOG_UNAVAILABLE`, never a silent empty list)
- [ ] T037 [US6] Implement `SettingsAiModelsController` + records in
  `jordylab-be/src/main/java/dev/jordy/jordylab/settings/rest/controller/`
  per [contracts/settings-ai-models-api.md](contracts/settings-ai-models-api.md) — `GET /api/settings/ai-models` (
  registry + effective/default/fallback models + availability flag + last run), `GET …/catalog?search=&vendor=` (trimmed
  list + `fetchedAt`/`fresh`), `PUT …/{featureKey}` (`UNKNOWN_FEATURE`/`BLANK_MODEL`/`MODEL_UNAVAILABLE`
  -only-when-fresh); MockMvc tests in
  `jordylab-be/src/test/java/dev/jordy/jordylab/settings/rest/controller/SettingsAiModelsControllerTest.java`
- [ ] T038 [P] [US6] Create the frontend AI-models surface in `jordylab-fe/libs/settings/api/src/lib/` — catalog +
  feature models, `ai-models.store.ts` per `/angular-signal-store` (features, catalog with search/vendor filter, save
  action, stale metadata); mock factories + specs per `/angular-test`
- [ ] T039 [US6] Build the AI Models page in `jordylab-fe/libs/settings/ui/src/lib/ai-models-page/` —
  container/presentation: one row per feature (current model, read-only fallback, last-run line, "model unavailable"
  flag), model picker dialog/panel (vendor-grouped, search, price/context per MTok), stale-catalog hint; specs with
  store mock
- [ ] T040 [US6] Add the "AI Models" child route + nav item under Settings (
  `jordylab-fe/libs/settings/ui/src/lib/settings.routes.ts` + `jordylab-fe/apps/jordylab/src/app/app.ts`/`app.spec.ts`);
  prove SC-003 in `SettingsModuleTest`: save a model via the API → next `ResilientAiService` call uses it without
  restart (mocked model beans, explicit captor)

**Checkpoint**: US6 done — per-feature cost/quality control live; new AI features appear in the list automatically (
registry).

---

## Phase 9: User Story 7 — Know when someone signs up (Priority: P3)

**Goal**: Ntfy push on new pending sign-ups (spec US7, FR-018)

**Independent Test**: Register a new account → the admin's Ntfy push arrives within the poll interval with name +
email (quickstart scenario 11)

- [ ] T041 [US7] Create `NtfyClient` in
  `jordylab-be/src/main/java/dev/jordy/jordylab/settings/rest/client/NtfyClient.java` — `POST {base-url}/{topic}` with
  title/body (+ optional `Authorization` token header; never logged); WireMock test incl. graceful failure (notification
  failure must not break anything, but is logged — no silent failure)
- [ ] T042 [US7] Implement `PendingSignupNotifierService` in
  `jordylab-be/src/main/java/dev/jordy/jordylab/settings/service/PendingSignupNotifierService.java` —
  `@Scheduled(fixedDelay)` poll (configurable, default 5 min) of the pending list via
  `KeycloakUserAdministrationService`, diff against the last-seen pending set, push on new arrivals; complete no-op when
  Ntfy unconfigured (research D8); tests with injected `Clock` + mocked client/deps

---

## Phase 10: Polish & Cross-Cutting Concerns

**Purpose**: Docs, generated artifacts, and full validation across all stories

- [ ] T043 [P] Update docs: root `AGENTS.md` (remove Ollama/WireGuard guidance, rewrite the AI-routing table —
  OpenRouter primary per feature + Anthropic `claude-sonnet-5` fallback, MVP1 status; flag/update "Hetzner VPS" →
  OVHcloud), `jordylab-be/AGENTS.md` (new "Settings model (feature 006 — settings module)" section — note the label
  collision with the historical local-multiplayer 006 section per research §4.6, add `settings` to schema ownership,
  update the endpoint gates incl. `ingest/client` → `admin`, remove the Spring AI M2 accepted-risk note in favor of the
  GA bump), `jordylab-fe/AGENTS.md` (settings domain entry + `scope:settings` eslint/tsconfig gotchas + role-aware
  guards)
- [ ] T044 [P] Regenerate the scan client template (`python tools/build_client.py` →
  `jordylab-be/src/main/resources/scripts/jordylab-scan-template.py`) so the client-download gate/error text says
  `admin` instead of `jordylab-user`; verify `GET /api/gamecatalog/ingest/client` as admin still serves it
- [x] T045 Verify SC-005: `grep -ri ollama jordylab-be/ jordylab-fe/` returns nothing (code, build files, compose and
  configuration); only docs/specs keep history notes
- [ ] T046 Run the full [quickstart.md](quickstart.md) validation (13 scenarios, live stack incl. realm re-import, one
  real scanner run, fallback via dead-gateway override, guest limit with a lowered env limit); record outcomes
- [ ] T047 Final green run: `./gradlew build` (full backend suite, `ModularityTests`, JaCoCo ≥ 0.80) +
  `bunx nx run-many -t test lint` (all affected FE projects: settings libs, shared/auth, app shell, gamecatalog
  chat/route updates); run `/modularity-check` once more

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: no dependencies — start immediately
- **Phase 2 (Foundational)**: after Setup — **blocks all user stories** (realm + matrix + auth primitives)
- **Phase 3+ (User Stories)**: each starts after Foundational
    - Track A (access): US1 → US2 → US3 (natural runtime order: gate → admin → guest scope)
    - Track B (AI): US4 → US6 (US4 builds the registry/port; US6 builds the settings store + page on top)
    - US5 and US7 are small independents: US5 needs only T009; US7 needs US2's pending list (T012/T013)
- **Phase 10 (Polish)**: after all desired stories complete

### User Story Dependencies

- **US1 (P1)**: after Foundational — no story dependencies (MVP)
- **US2 (P1)**: after Foundational — independent (test fixtures create pending users; runtime demo integrates with US1)
- **US3 (P1)**: after Foundational — FE nav/guards are independent; runtime guest demo reuses US2's approve
- **US4 (P1)**: after **Setup only** (T001/T004) — fully parallel with Track A; touches `shared/ai` + callers, no auth
  code
- **US5 (P2)**: after Foundational (T009)
- **US6 (P2)**: after **US4** (registry, event, resolver port) + **T017** (settings route/nav in US2's shell wiring)
- **US7 (P3)**: after US2 (pending list read)

### Within Each User Story

- Tests written first (red) where included → entities → services → controllers/clients → frontend
- Stop-and-report gates (T005, T025) halt for the user's confirmation before executing
- Story checkpoint validated independently before moving on

### Parallel Opportunities

- T002/T003/T004 (Setup, different files)
- T009 with T005–T008 (auth lib vs backend/realm files)
- US1 (T010/T011) fully parallel with US4's start (different stacks)
- T015/T016 vs T013/T014 (FE api+page vs backend service+controller) once the contract is fixed
- T021/T022/T023 (three FE files), T029 call-site updates, T034/T038 (US6 entity vs FE store)

---

## Parallel Example: User Story 2

```bash
# Once the users contract is fixed (T014 defines the shapes), launch:
Task: T015 "settings API lib surface + users.store" (jordylab-fe/libs/settings/api)
Task: T016 "Users page container/presentation" (jordylab-fe/libs/settings/ui)  # needs T015's store API
Task: T012 "KeycloakAdminClient + WireMock tests" (jordylab-be …/settings/rest/client/)
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1 + Phase 2 (T001–T009), including the T005 stop-and-report gate
2. Phase 3 (US1) → **STOP and VALIDATE**: quickstart scenario 1 (register → pending → denied everywhere)
3. The gate is live: sign-up is open and safe (nobody gets in until US2 exists)

### Incremental Delivery

1. Setup + Foundational → foundation ready
2. US1 → gate works (MVP)
3. US2 → approve/reject/revoke + badge (friends can get in)
4. US3 → guest scope + chat limit (friends can use the catalog, bounded)
5. US4 → resilient per-feature AI (gateway + fallback, GA bump)
6. US5 → self-service account
7. US6 → per-feature model picker
8. US7 → sign-up push notifications
9. Polish → docs + generated client + full quickstart + all suites green

### Parallel Team Strategy

- Developer A: Track A (US1 → US2 → US3 → US7)
- Developer B: Track B (US4 → US6), then US5
- Both converge for Phase 10 (polish + full validation)

---

## Notes

- [P] tasks = different files, no dependencies on incomplete tasks
- [Story] labels map to spec.md user stories for traceability
- **Stop-and-report gates**: T005 (realm export + `jordylab-user` removal) and T025 (Ollama deletion) must halt and
  report to the user before executing — explicit instruction carried from the plan prompt
- Skills referenced in tasks are repo skills — the implementing agent loads them (`/entity`, `/flyway-migration`,
  `/angular-signal-store`, `/angular-test`, `/modularity-check`, …)
- Validation data rules apply: no hand-seeded users/catalog rows — pending users come from real registration (or
  Testcontainers fixtures in tests); catalog data from the real scanner
- Commit cadence per repo practice: only when the user asks
- Each user story is independently completable and testable — stop at any checkpoint to validate
