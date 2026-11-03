# Tasks: JordyLab MCP Server (read-only)

**Input**: Design documents from `specs/021-jordylab-mcp-server/` (plan.md, spec.md, research.md, data-model.md,
contracts/, quickstart.md)

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/,
quickstart.md

**Tests**: included. The constitution's Testing Discipline (IV), AGENTS.md ("run relevant tests after each change") and
the plan's Testing section make them part of this feature's scope, not optional extras. Each story's tests come before
its implementation.

**Organization**: grouped by user story (spec.md: US1 P1 catalog, US2 P2 finance, US3 P2 read-only proof). User story 4
was removed by clarification Q1. Owner decisions are marked **OWNER DECISION**; operations steps are marked
**OPERATIONS**.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: different files, no dependency on an unfinished task
- **[Story]**: US1, US2 or US3; set only in user story phases
- Every description names the file it changes or records its result in

Paths are relative to the repository root. The backend root is `jordylab-be/`.

---

## Phase 1: Setup (decisions and dependencies)

**Purpose**: settle the open items and add the libraries. Nothing else starts until T001 to T003 are answered.

- [x] T001 **DECIDED** (OI-1, 2026-11-03): option A. Connector tokens are accepted on `/api` in v1. Recorded in
  `specs/021-jordylab-mcp-server/research.md` R3 and `spec.md` Assumptions. Option B is not planned.
- [ ] T002 **OWNER DECISION** (OI-2, premise changed by I1): confirm in `specs/021-jordylab-mcp-server/plan.md` (Open items)
  that the production Keycloak apply in T057 runs before the release. Also confirm whether a merge still deploys: Compose
  decision 4 says deploys run on release tags only (T2).
- [ ] T003 **OWNER DECISION** (OI-3): confirm in `specs/021-jordylab-mcp-server/spec.md` (User Story 2, scenario 1) that
  `search_articles` matches titles only (spec change S2).
- [ ] T004 Add `org.springframework.ai:spring-ai-starter-mcp-server-webmvc` (version from the Spring AI BOM) and the
  AspectJ runtime for Spring AOP to `jordylab-be/build.gradle.kts`, then confirm both resolve with
  `cd jordylab-be && ./gradlew dependencies --configuration runtimeClasspath | grep -i "mcp\|aspectj"`.

---

## Phase 2: Foundational (blocking prerequisites)

**Purpose**: the `mcp` module, transport, authentication, discovery, guardrails and the Keycloak client. Every user story
needs all of these.

**Gate (I1): met.** The Compose cutover is live on production (`specs/020-compose-deployment/`). T017 and T057 can run; the
rest of this phase does not depend on the host.

**CRITICAL**: no user story work begins until this phase is complete and its tests pass.

### Configuration and module skeleton

- [ ] T005 Add MCP settings to `jordylab-be/src/main/resources/application.yaml`: `spring.ai.mcp.server` with
  `protocol: STATELESS`, `type: SYNC`, `name: jordylab`, and capabilities limited to tools (exact keys confirmed in T025);
  and `jordylab.mcp` with `public-base-url: ${JORDYLAB_APP_BASE_URL:}`, `audience: jordylab-mcp`,
  `tool-calls-per-minute: 60`. Leave `spring.ai.model.chat` unset (AGENTS.md).
- [ ] T006 [P] Create `McpProperties` as a record annotated `@ConfigurationProperties("jordylab.mcp")` in
  `jordylab-be/src/main/java/dev/jordy/jordylab/mcp/McpProperties.java` (fields: `publicBaseUrl`, `audience`,
  `toolCallsPerMinute`).
- [ ] T007 [P] Create the public envelope record `McpToolPage<T>` in
  `jordylab-be/src/main/java/dev/jordy/jordylab/mcp/McpToolPage.java`: `items`, `returned`, `totalMatching`, `hasMore`,
  a factory that takes the items, the total and the offset, and a static `clampLimit(int)` that bounds the page size to
  1–50 (data-model.md, validation rules).

### Discovery

- [ ] T008 [P] Create the record `ProtectedResourceMetadata` with snake_case JSON names in
  `jordylab-be/src/main/java/dev/jordy/jordylab/mcp/rest/controller/model/ProtectedResourceMetadata.java` (contracts/mcp-endpoint.md, section 1a).
- [ ] T009 Create `ProtectedResourceMetadataController` in
  `jordylab-be/src/main/java/dev/jordy/jordylab/mcp/rest/controller/ProtectedResourceMetadataController.java`. It serves
  `/.well-known/oauth-protected-resource/mcp` and `/.well-known/oauth-protected-resource`. `resource` is
  `${publicBaseUrl}/mcp`; `authorization_servers` holds the configured issuer
  (`spring.security.oauth2.resourceserver.jwt.issuer-uri`, which is `https://jordylab.be/auth/realms/jordylab` in prod).
  Depends on T006 and T008.

### Authentication

- [ ] T010 [P] Create `McpJwtDecoderFactory` in
  `jordylab-be/src/main/java/dev/jordy/jordylab/mcp/service/McpJwtDecoderFactory.java`. It builds a lazy
  `NimbusJwtDecoder.withIssuerLocation(issuer)` with the default issuer validator and an audience validator for
  `McpProperties.audience` (research R2, R3). Depends on T006.
- [ ] T011 [P] Create `McpAuthenticationEntryPoint` in
  `jordylab-be/src/main/java/dev/jordy/jordylab/mcp/service/McpAuthenticationEntryPoint.java`. It answers `401` with
  `WWW-Authenticate: Bearer resource_metadata="<publicBaseUrl>/.well-known/oauth-protected-resource/mcp"` and no data
  (contracts/mcp-endpoint.md, section 2a). Depends on T006.
- [ ] T012 Create `McpConfiguration` in `jordylab-be/src/main/java/dev/jordy/jordylab/mcp/McpConfiguration.java`. It
  defines a `SecurityFilterChain` annotated `@Order(Ordered.HIGHEST_PRECEDENCE)` with a `securityMatcher` for `/mcp` and
  `/.well-known/oauth-protected-resource/**`: the metadata path is `permitAll`, `/mcp` requires `hasRole("admin")`, the
  resource server uses the decoder from T010 and the entry point from T011, and CSRF is disabled for this chain. If
  `shared/config` has no `Clock` bean, add one here. Depends on T006, T010, T011. Do not edit `shared/config/SecurityConfig.java`.

### Guardrails

- [ ] T013 [P] Create `McpRateLimiter` in `jordylab-be/src/main/java/dev/jordy/jordylab/mcp/service/McpRateLimiter.java`.
  It keeps one fixed one-minute window per admin subject, with the limit from `McpProperties.toolCallsPerMinute`, a
  `Clock` injected for tests, and the rules in data-model.md ("In-memory state"). Expired windows are removed. Depends on T006.
- [ ] T014 Create `McpToolCallAdvice` in `jordylab-be/src/main/java/dev/jordy/jordylab/mcp/service/McpToolCallAdvice.java`.
  It is an `@Around` advice on `@McpTool` methods. For each call it reads the admin's `sub` from the security context and
  asks T013. Over the limit, it returns the tool error "Too many requests in one minute. Try again shortly." and does
  not run the method. Otherwise it runs the method and writes one line on the logger `dev.jordy.jordylab.mcp.audit`:
  `mcp tool=<name> admin=<sub> outcome=<ok|rate_limited|error> durationMs=<n>`. Any exception becomes the tool error
  "The tool could not read this data right now.", and the log carries the exception class only (research R7, contracts
  section 2c). Depends on T004, T013.

### Keycloak and gateway

- [ ] T015 [P] Add the connector client `jordylab-mcp-connector` to `jordylab-be/compose/keycloak-realm-export.json`
  exactly as in `specs/021-jordylab-mcp-server/contracts/keycloak-connector-client.md`: public, standard flow only, PKCE
  `S256`, redirect `https://claude.ai/api/mcp/auth_callback`, the Audience mapper `mcp-audience` for `jordylab-mcp`,
  default scopes including `roles`, no `offline_access`, refresh rotation on. Validate with
  `python3 -m json.tool jordylab-be/compose/keycloak-realm-export.json > /dev/null`.
- [ ] T016 [P] Add the same client to `deploy/keycloak/realm-prod.json` for fresh production imports. The redirect has no
  `${PRODUCTION_DOMAIN}` placeholder because the callback is Claude's fixed URL. Validate with
  `python3 -m json.tool deploy/keycloak/realm-prod.json > /dev/null`.
- [ ] T017 Add the two routes to the live public Caddyfile `deploy/compose/Caddyfile`. Add `@mcp path /mcp /mcp/*` and
  `@protected_resource path /.well-known/oauth-protected-resource /.well-known/oauth-protected-resource/*`, each with
  `handle` and `reverse_proxy backend:8080`, following the file's existing pattern. Remove `/.well-known/assetlinks.json` from
  the `@backend` matcher (spec 018 removed that endpoint). The retired k3s `deploy/k8s/base/gateway.yaml` is not edited
  (research R10). Confirm with T054.

### Foundational tests

- [ ] T018 [P] Test `McpRateLimiter` in `jordylab-be/src/test/java/dev/jordy/jordylab/mcp/service/McpRateLimiterTest.java`:
  60 calls are accepted and the 61st refused; the window resets after one minute; two admins are counted separately.
  Use a fixed `Clock`, AssertJ `assertSoftly`, and no `any()`.
- [ ] T019 [P] Test the metadata endpoint in
  `jordylab-be/src/test/java/dev/jordy/jordylab/mcp/rest/controller/ProtectedResourceMetadataControllerTest.java`: both
  paths return `200` with the exact snake_case fields and need no token.
- [ ] T020 Test the chain in `jordylab-be/src/test/java/dev/jordy/jordylab/mcp/McpSecurityTest.java` with MockMvc:
  `POST /mcp` without a token returns `401` with the exact `WWW-Authenticate` pointer; an admin token passes the chain;
  a guest-only token returns `403` with no data. Build the tokens with `McpJwtTestBuilder` (created in T021) and the `jwt()`
  post-processor, with `@Language("JSON")` bodies. The audience check is covered by T021.
- [ ] T021 [P] Test the audience validator in
  `jordylab-be/src/test/java/dev/jordy/jordylab/mcp/service/McpJwtDecoderFactoryTest.java`: a token without `jordylab-mcp`
  is rejected and one with it is accepted. Create
  `jordylab-be/src/test/java/dev/jordy/jordylab/mcp/McpJwtTestBuilder.java` (`@UtilityClass`, `DEFAULT_*` constants,
  `aDefaultJwt()` and `aJwt()` builder methods, the canonical TestBuilder structure; constitution IV). Build every `Jwt`
  in the MCP tests with it.
- [ ] T022 [P] Test the advice in `jordylab-be/src/test/java/dev/jordy/jordylab/mcp/service/McpToolCallAdviceTest.java`:
  the audit line names the admin, tool, outcome and duration and contains no arguments (captured log);
  a rate-limited call does not run the tool (a counting fake, asserted by value); an exception returns the sanitised
  message and its log line has no exception message text.
- [ ] T023 [P] Test the protocol path in `jordylab-be/src/test/java/dev/jordy/jordylab/mcp/McpRateLimitIntegrationTest.java`:
  sixty-one `tools/call` JSON-RPC requests from one admin through MockMvc. The 61st returns an error result with the limit
  text, and the tool's service method is never invoked (`verify(never())`). This proves the advice applies to beans the
  framework calls (plan K2). Use a stub tool bean that counts calls.

### Gates (must pass before Phase 3)

- [ ] T024 Protocol gate, run after T059 (I2) with MCP Inspector against `./gradlew bootRun`. Obtain an admin token through
  Inspector's OAuth flow for `jordylab-mcp-connector`. `initialize` and `tools/list` complete
  over Streamable HTTP in stateless mode, with no session header required. Claude connects from Anthropic's cloud and
  cannot reach localhost, so Claude's own check is in Phase 8. If the exchange fails, change the protocol in T005 to
  `STREAMABLE` and repeat. Record the result in `specs/021-jordylab-mcp-server/research.md` R1.
- [ ] T025 Token gate, against the local Keycloak after T059 (I2) created the client live (the realm import runs only once):
  a token for `jordylab-mcp-connector` carries `aud`
  containing `jordylab-mcp`, `realm_access.roles` with `admin`, a refresh token, and no offline token. Confirm the exact
  names of "Revoke Refresh Token" and "Refresh Token Max Reuse" in the admin console, and record them in
  `specs/021-jordylab-mcp-server/contracts/keycloak-connector-client.md` (Verification). Also confirm the
  `tool-calls-per-minute` and capability property keys used in T005. Confirm that the realm refuses anonymous client
  registration (anonymous access to Client Registration off) and record the setting (FR-002, G1).

**Checkpoint**: foundation ready. Authentication, discovery, rate limit, audit and the Keycloak client pass their tests,
and T024 and T025 are recorded.

---

## Phase 3: User Story 1 - Connect Claude and ask about the game catalog (Priority: P1) 🎯 MVP

**Goal**: an admin can ask Claude about the game catalog and get answers that match the Game Catalog screens.

**Independent Test**: after the deploy, quickstart steps 4 and 5 (Claude Desktop) and 6 (Claude mobile) return the same
answers as the Game Catalog screens. Before the deploy, the MCP Inspector lists the five catalog tools and returns data
from `search_games`.

### Tests for User Story 1

- [ ] T026 [P] [US1] Test the catalog tools in
  `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/rest/controller/GameCatalogMcpToolsTest.java` with Testcontainers
  Postgres and fixtures built with the existing `GameTestBuilder`, `GameInstallationTestBuilder`, `GameMarkTestBuilder` and
  `ScanSourceTestBuilder` (constitution IV): `search_games` respects platform, local multiplayer and
  `minLocalPlayers` (confirmed counts only); unknown facts come back as `null`; `limit` is clamped to 50 with the correct
  `totalMatching`; `get_game` contains no voter identifiers; an unknown game id returns a "not found" tool error.

### Implementation for User Story 1

- [ ] T027 [P] [US1] Create the catalog result records in `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/rest/controller/model/`:
  `GameSearchItem.java`, `GameDetail.java`, `PlaceRef.java`, `PlaceSummary.java`, `SourceSummary.java`, `LibraryStatus.java`,
  with the fields in data-model.md. No artwork, voter, path or machine fields.
- [ ] T028 [US1] Create `GameCatalogMcpTools` in
  `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/rest/controller/GameCatalogMcpTools.java` with `search_games`.
  It maps the arguments onto the existing `GameFilter` builder, calls `GameQueryService.getGames`, and wraps the page with
  `McpToolPage` (research R9). Depends on T007, T027.
- [ ] T029 [US1] Add `get_game` to `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/rest/controller/GameCatalogMcpTools.java`, using the service behind `GET /games/{id}` (confirm its name
  in the code), mapped to `GameDetail`. An unknown id returns the not-found tool error. Depends on T028.
- [ ] T030 [US1] Add `list_places` to `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/rest/controller/GameCatalogMcpTools.java`, using the services behind `GET /places` and
  `GET /platforms`, mapped to `PlaceSummary`.
- [ ] T031 [US1] Add `list_sources` to `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/rest/controller/GameCatalogMcpTools.java`, using the scan-source list behind `GET /sources`,
  mapped to `SourceSummary` with paths and machine identifiers left out.
- [ ] T032 [US1] Add `get_library_status` to `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/rest/controller/GameCatalogMcpTools.java`, using the service behind `GET /library/status`,
  mapped to `LibraryStatus` with `refreshRunning`.
- [ ] T033 [US1] In `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/rest/controller/GameCatalogMcpTools.java`, map the argument enums (`sources`, `mark`, `romStatus`, `sort`,
  `installStatus`) onto the existing enums. An unknown value returns a clear tool error that lists the accepted values.
- [ ] T034 [US1] Give each catalog tool a description that says what it returns and ends with "Read-only.", as FR-017
  requires, and the read-only hint on each `@McpTool` method in `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/rest/controller/GameCatalogMcpTools.java`.

**Checkpoint**: User Story 1 works through MCP Inspector. Claude Desktop and mobile checks run in Phase 8.

---

## Phase 4: User Story 2 - Ask about finance without opening the app (Priority: P2)

**Goal**: an admin can ask Claude about news, portfolio, wishlist and the latest briefing, with the same values the
Finance News screens show.

**Independent Test**: MCP Inspector lists the four finance tools and each returns the same values as the matching screen.
After the deploy, quickstart step 5 (finance rows) confirms it through Claude.

### Tests for User Story 2

- [ ] T035 [P] [US2] Test the finance tools in
  `jordylab-be/src/test/java/dev/jordy/jordylab/fna/rest/controller/FnaMcpToolsTest.java` with Testcontainers Postgres and the existing
  fixtures `ArticleTestBuilder`, `PortfolioPositionTestBuilder`, `BriefingTestBuilder` and `WishlistItemDtoTestBuilder`
  (constitution IV):
  `search_articles` matches titles only, newest first, honours `offset` and clamps `limit`; `get_portfolio` sums only
  the priced positions and sets `allPriced` to false when one is unpriced; `get_latest_briefing` returns
  `NO_BRIEFING_YET` when none exists and never calls `triggerBriefing` (`verify(never())`); `get_wishlist` returns the
  fetch times.

### Implementation for User Story 2

- [ ] T036 [US2] Add the title search to `jordylab-be/src/main/java/dev/jordy/jordylab/fna/domain/repository/ArticleRepository.java`
  as a `Pageable` method, `Page<Article> findByTitleContainingIgnoreCaseOrderByPublishedAtDesc(String title, Pageable pageable)`,
  so `offset` works. This replaces the `Top50` name in research R9.
- [ ] T037 [US2] Add `searchArticles(String text, int offset, int limit)` to
  `jordylab-be/src/main/java/dev/jordy/jordylab/fna/service/FnaService.java`. Empty text returns the newest articles.
  Depends on T036.
- [ ] T038 [P] [US2] Create the finance result records in `jordylab-be/src/main/java/dev/jordy/jordylab/fna/rest/controller/model/`:
  `ArticleItem.java`, `PortfolioItem.java`, `PortfolioSummary.java`, `WishlistItem.java`, `BriefingView.java`
  (data-model.md).
- [ ] T039 [US2] Create `FnaMcpTools` in `jordylab-be/src/main/java/dev/jordy/jordylab/fna/rest/controller/FnaMcpTools.java`
  with `search_articles`, mapping `FnaService.searchArticles` to `McpToolPage<ArticleItem>`. Depends on T037, T038.
- [ ] T040 [US2] Add `get_portfolio` to `jordylab-be/src/main/java/dev/jordy/jordylab/fna/rest/controller/FnaMcpTools.java`: `FnaService.getPortfolioPositions` mapped to
  `PortfolioSummary`, with `value` per position and the total of the priced positions.
- [ ] T041 [US2] Add `get_wishlist` to `jordylab-be/src/main/java/dev/jordy/jordylab/fna/rest/controller/FnaMcpTools.java`: `FnaService.getWishlistItems` mapped to `McpToolPage<WishlistItem>`.
- [ ] T042 [US2] Add `get_latest_briefing` to `jordylab-be/src/main/java/dev/jordy/jordylab/fna/rest/controller/FnaMcpTools.java`: `FnaService.getLatestBriefing` mapped to `BriefingView`,
  with `NO_BRIEFING_YET` when empty. The tool must not call `triggerBriefing`.
- [ ] T043 [US2] Give each finance tool a description per FR-017 and the read-only hint. The `search_articles` description
  says it matches titles only (spec change S2).

**Checkpoint**: User Story 2 works through MCP Inspector.

---

## Phase 5: User Story 3 - Prove the connector cannot change anything (Priority: P2)

**Goal**: the tool list is fixed, every tool is read-only, and no call can change data, start work or spend AI tokens.

**Independent Test**: the surface test (T044) and the unknown-tool test (T045) pass. After the deploy, quickstart step 7
(in Phase 8) confirms it through Claude.

### Tests for User Story 3

- [ ] T044 [P] [US3] Test the tool surface in `jordylab-be/src/test/java/dev/jordy/jordylab/mcp/McpToolSurfaceTest.java`:
  the registered names are exactly the nine in `specs/021-jordylab-mcp-server/contracts/tools.md`; each has the read-only
  hint; no name contains a write verb (create, update, delete, approve, reject, scan, refresh, sync, generate, post,
  send, trigger, reset, end); each description contains "Read-only.".
- [ ] T045 [US3] Add to `jordylab-be/src/test/java/dev/jordy/jordylab/mcp/McpToolSurfaceTest.java`: a `tools/call` for an
  unknown name such as `create_game` or `trigger_briefing` returns the protocol's error and writes no `ok` audit line.
- [ ] T046 [US3] Add to `jordylab-be/src/test/java/dev/jordy/jordylab/mcp/McpToolSurfaceTest.java`: a `tools/call` for an
  existing tool with an out-of-range `limit` (for example 500) returns at most 50 items and a correct `hasMore` (FR-009).

### Implementation for User Story 3

- [ ] T047 [US3] Review every `@McpTool` method in `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/rest/controller/GameCatalogMcpTools.java`
  and `jordylab-be/src/main/java/dev/jordy/jordylab/fna/rest/controller/FnaMcpTools.java`: each has the read-only hint, and
  none calls a `save`, `delete`, `trigger`, `refresh`, `scan` or `generate` method. Record the review in the commit body.

**Checkpoint**: the read-only promise is covered by tests and by review.

---

## Phase 6: Polish and cross-cutting concerns

**Purpose**: documentation, boundary and build checks, and the CI check set.

- [ ] T048 [P] Update the module table in `AGENTS.md` (root): add the `mcp` row (no schema, endpoint `/mcp`), and add one
  paragraph under Infrastructure about the connector and the deploy path.
- [ ] T049 [P] Add an `mcp` section to `jordylab-be/AGENTS.md`: the routes, the audience rule, the tool rule (read-only, one
  service call each, no AI call), and the guardrails.
- [ ] T050 [P] Add a connector section to `docs/runbook.md`: how to connect (quickstart step 4), revoking access, the
  90-day sign-in cap, and what to check when the connector fails (the 401 pointer, the audience, the rate limit).
- [ ] T051 [P] Add a row for `jordylab-mcp-connector` to the change table in `deploy/keycloak/README.md`, marked pending
  until T058 runs.
- [ ] T052 Run `cd jordylab-be && ./gradlew :test --tests "*ModularityTests*"` and fix any boundary violation. The `mcp`
  module must not depend on a domain module; the domain modules may depend only on `mcp` root.
- [ ] T053 Run `cd jordylab-be && ./gradlew :test --tests "*Mcp*"`. Every test in the `Mcp` family passes.
- [ ] T054 Validate the Caddyfile after T017:
  `caddy validate --config deploy/compose/Caddyfile --adapter caddyfile`. Confirm the two new routes are present and the stale
  assetlinks route is absent.
- [ ] T055 Record the spike outcomes (T024, T025) and the Keycloak setting names in
  `specs/021-jordylab-mcp-server/research.md` R1 and R5.
- [ ] T056 Confirm the CI set passes on the PR: `test-backend`, `test-frontend`, `e2e-web` (SC-011: the web app is unchanged),
  `app-boot`, `refs`, `secret-scan`.

---

## Phase 7: OPERATIONS before merge

**Purpose**: the live Keycloak client must exist before the release, which follows the Compose cutover (I1, OI-2).

**Gate (I1): met.** The Compose cutover is live (`specs/020-compose-deployment/`). The release path is open in T002 and T2.

- [ ] T057 **OPERATIONS** (after the Compose cutover, I1): create `jordylab-mcp-connector` in the live production realm with
  `kcadm`, inside the Keycloak container of the Compose stack. Verify with `kcadm get clients -r jordylab -q clientId=jordylab-mcp-connector`
  that it is public, has the redirect, PKCE `S256` and the audience mapper. Confirm that anonymous client registration is off (FR-002, G1). Print no secret. Requires T002 confirmed.
- [ ] T058 **OPERATIONS**: change the pending row in `deploy/keycloak/README.md` (T051) to applied, with the date and a
  command summary and no secret values.
- [ ] T059 **OPERATIONS** (runs before T024 and T025, I2): create the same client in the local Podman Keycloak (`--server http://localhost:8080`, no `/auth`),
  so local token checks match production.

---

## Phase 8: OPERATIONS after merge

**Purpose**: the checks only the deployed system can answer. Claude connects from its cloud, so these steps need the live
endpoint.

- [ ] T060 **OPERATIONS**: quickstart steps 1 and 2 on production (metadata document; `401` pointer through the public proxy).
  Record the output in `docs/runbook.md`.
- [ ] T061 **OPERATIONS**: quickstart step 3 on production (`code_challenge_methods_supported` contains `S256`). Record it
  in `docs/runbook.md`.
- [ ] T062 **OPERATIONS**: quickstart step 4 (Claude Desktop connects, nine read-only tools listed). Record the result in
  `docs/runbook.md`. This is Jordy's action in Claude.
- [ ] T063 **OPERATIONS**: quickstart step 5 (the ten questions, five catalog and five finance, each compared with its screen). Record
  any difference and the time each answer took (SC-002) in `docs/runbook.md`.
- [ ] T064 **OPERATIONS**: quickstart step 6 (Claude mobile over mobile data). Record it in `docs/runbook.md`. This closes
  the mobile assumption in the spec.
- [ ] T065 **OPERATIONS**: quickstart step 7 (read-only check: ask for changes, then compare the web app, the AI call count
  and Linear before and after). Record the result in `docs/runbook.md`.
- [ ] T066 **OPERATIONS**: quickstart step 8 (parallel questions in one chat). If refresh fails, raise "Refresh Token Max
  Reuse" in the production realm, and record the change in `deploy/keycloak/README.md`.
- [ ] T067 **OPERATIONS**: quickstart step 9 (audit lines in production logs: admin, tool, outcome, no arguments). Record
  the result in `docs/runbook.md`.
- [ ] T068 **OPERATIONS**: quickstart step 10 (revocation with a throwaway admin account). Restore its role and delete the
  account afterwards. Record the result in `docs/runbook.md`.

---

## Dependencies and execution order

- **Phase 1** (T001–T004) first. T001 to T003 are owner decisions and may run in parallel with T004.
- **Phase 2** depends on Phase 1. T006, T007, T008, T010, T011, T013, T015 and T016 are independent of each other.
  T009 needs T006 and T008. T012 needs T006, T010 and T011. T014 needs T004 and T013. T017 is independent. T018 to T023
  need the code they test. T024 and T025 need the running backend and the local realm.
- **Phase 3 (US1)** depends on Phase 2. Its tests (T026) come first. T027 is independent of the others. T028 needs T007
  and T027, and T029 to T034 extend the same file, so they run in sequence.
- **Phase 4 (US2)** depends on Phase 2 only. It can run in parallel with Phase 3, because it touches the `fna` module
  and not `gamecatalog`.
- **Phase 5 (US3)** depends on Phases 3 and 4 (the surface test needs all nine tools).
- **Phase 6** depends on all user story phases.
- **Phase 7** depends on T002 (owner), T016 and the Compose cutover (I1), and it must finish before the release.
- **Phase 8** depends on the merge and the deploy.

### Parallel example: Phase 2

```text
T006 McpProperties
T007 McpToolPage        ← run together
T008 ProtectedResourceMetadata
T015 local realm export ← run together
T016 prod realm file
```

### Parallel example: User Stories 1 and 2 after Phase 2

```text
Developer A: T026 → T027 → T028 → T029 → T034 (gamecatalog)
Developer B: T035 → T036 → T037 → T038 → T039 → T043 (fna)
```

## Implementation strategy

- **MVP = Phase 1, Phase 2 and Phase 3 (User Story 1)**. Its checkpoint is a working catalog connection through MCP
  Inspector, and after the deploy, through Claude (Phase 8, steps 4 to 6).
- **Then** User Story 2 (finance), then User Story 3 (the surface and read-only tests). Each adds tools without changing
  the earlier ones.
- **Before the release**: all of Phases 1 to 6 pass, the Compose cutover has happened (I1), and Phase 7 is done (OI-2).
  The PR is one branch and one spec (the house rule for specs). Compose decision 4 makes the release a tag.
- **After the merge**: Phase 8 runs with Jordy in Claude. Its findings go to `docs/runbook.md`.

## Summary

- Total tasks: **68** (T001–T068)
- Setup: 4 (3 owner decisions, 1 dependency)
- Foundational: 21 (T005–T025): 15 build and configuration tasks, 6 test tasks (T018–T023) and 2 gates (T024–T025)
- User Story 1 (P1, catalog): 9 (T026–T034)
- User Story 2 (P2, finance): 9 (T035–T043)
- User Story 3 (P2, read-only proof): 4 (T044–T047)
- Polish: 9 (T048–T056)
- Operations before merge: 3 (T057–T059)
- Operations after merge: 9 (T060–T068)
- Parallel opportunities: 23 tasks marked [P] (each touches a different file). User Stories 1 and 2 can run in parallel
  after Phase 2
- Independent test per story: US1 (quickstart 4–6), US2 (MCP Inspector finance rows and quickstart 5), US3 (T044–T046
  and quickstart 7)
- MVP scope: Phases 1–3 (User Story 1)
- Format check: every task starts with `- [ ]`, has an ID, carries a `[US#]` label only in story phases, and names a file
  path
