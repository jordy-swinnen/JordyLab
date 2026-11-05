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

**Regenerated 2026-11-04 for option A** (sign-in through the backend's own OAuth 2.1 authorization server, after `main`
moved to spec 019 and removed Keycloak; see `plan.md` and `research.md` R2 to R6). Task IDs T069 to T079 were added at this
regeneration. They sit in the phase they belong to and run in the order given under Dependencies.

Marks: `[x]` = done and kept: built and tested on the pre-019 branch and unchanged by the re-plan, or closed as
**OBSOLETE** (kept so that IDs in older commit trailers still resolve). `[ ]` = to do: new, changed, or to re-run on the
fresh branch from `main`. The code of the pre-019 branch (last code commit `5ee99ef`) is carried over, not rebased.

**Status 2026-11-06**: **79 done, 0 open.** The owner validated the connector end to end (Claude Desktop and mobile, the ten
questions, the read-only probe, parallel questions, audit lines and revocation); the local gates T024/T025 are superseded by
the flow test and the production checks, and the agent clients (Claude Code and OpenCode) authenticate with the same client.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: different files, no dependency on an unfinished task
- **[Story]**: US1, US2 or US3; set only in user story phases
- Every description names the file it changes or records its result in

Paths are relative to the repository root. The backend root is `jordylab-be/`.

---

## Phase 1: Setup (decisions and dependencies)

**Purpose**: settle the open items and add the libraries. Nothing else starts until T001 to T003 are answered and T004 resolves.

- [x] T001 **DECIDED** (OI-1, 2026-11-03), **SUPERSEDED 2026-11-04**: connector tokens are no longer accepted on `/api`.
  They are issued by the backend's authorization server and accepted only by the `/mcp` chain (research R2, R3).
- [x] T002 **DECIDED** (OI-2, 2026-11-03): merges to `main` go straight to production. The merge deploys through
  `deploy-prod-compose.yml` (`AUTO_DEPLOY` is true), so the first deploy of this endpoint is the merge itself. The live
  Keycloak client work (T057 to T059) is obsolete; see Phase 7.
- [x] T003 **DECIDED** (OI-3, 2026-11-03): title only, for now. `search_articles` matches titles only (spec change S2).
- [x] T004 Add to `jordylab-be/build.gradle.kts`: `org.springframework.ai:spring-ai-starter-mcp-server-webmvc` (Spring AI
  BOM), `org.springframework.boot:spring-boot-starter-oauth2-authorization-server` (version from the Boot 4.1.1 BOM, which
  pins Spring Authorization Server 7.1.x), and `org.aspectj:aspectjrt` (Spring AOP for the tool advice). The MCP starter
  and AspectJ were added on the pre-019 branch; the authorization server starter is new. Confirm all three resolve with
  `cd jordylab-be && ./gradlew dependencies --configuration runtimeClasspath | grep -i "mcp\|aspectj\|authorization-server"`.

---

## Phase 2: Foundational (blocking prerequisites)

**Purpose**: the `mcp` module, the transport, the authorization server, the `/mcp` chain, discovery, guardrails and the
gateway routes. Every user story needs all of these.

**Gate (I1): met.** The Compose cutover is live on production (`specs/020-compose-deployment/`).

**Decisions (2026-11-04)**: T074 (login return path, option A) and T078 (signing key, option A) are decided; the branch
starts fresh from `main`. T070 builds on both.

**CRITICAL**: no user story work begins until this phase is complete and its tests pass.

### Configuration and module skeleton

- [x] T005 Update the MCP settings in `jordylab-be/src/main/resources/application.yaml`: keep `spring.ai.mcp.server` with
  `protocol: STATELESS`, `type: SYNC`, `name: jordylab` and tools only (exact keys confirmed in T025). Under `jordylab.mcp`
  set `public-base-url: ${JORDYLAB_APP_BASE_URL:}`, `audience: jordylab-mcp`, `tool-calls-per-minute: 60`,
  `access-token-ttl: PT10M` and `refresh-token-ttl: P30D`. Remove any `spring.security.oauth2.resourceserver.jwt.issuer-uri`
  the pre-019 branch added (it pointed at Keycloak). Leave `spring.ai.model.chat` unset (AGENTS.md).
- [x] T006 Update `McpProperties` in `jordylab-be/src/main/java/dev/jordy/jordylab/mcp/McpProperties.java` as a record annotated
  `@ConfigurationProperties("jordylab.mcp")`. Fields: `publicBaseUrl` (the issuer, no trailing slash, contracts/oauth-authorization-server.md),
  `audience`, `toolCallsPerMinute`, and the new `accessTokenTtl` and `refreshTokenTtl` (`Duration`).
- [x] T007 [P] Create the public envelope record `McpToolPage<T>` in
  `jordylab-be/src/main/java/dev/jordy/jordylab/mcp/McpToolPage.java`: `items`, `returned`, `totalMatching`, `hasMore`,
  a factory that takes the items, the total and the offset, and a static `clampLimit(int)` that bounds the page size to
  1–50 (data-model.md, validation rules).

### Discovery

- [x] T008 The protected resource metadata is Spring Security's built-in endpoint (research R9b), not a record and
  controller of our own: it answers both well-known paths and fixes the duplicated `header` entry. Nothing is created in
  `rest/controller/model/`; `McpSecurityTest` asserts the document.
- [x] T009 Configure the built-in metadata in `McpConfiguration`
  (`jordylab-be/src/main/java/dev/jordy/jordylab/mcp/McpConfiguration.java`): `resource` is `${publicBaseUrl}/mcp`,
  `authorization_servers` is `["${publicBaseUrl}"]` (the backend's own issuer, research R4), `scopes_supported` is
  `["jordylab.mcp"]`, one bearer method `header`. Covered by `McpSecurityTest`.

### Authorization server (the connector's sign-in, added 2026-11-04)

- [x] T069 [P] Create `McpAccountStatusValidator` in `jordylab-be/src/main/java/dev/jordy/jordylab/mcp/service/McpAccountStatusValidator.java`.
  For each tool call it reads the admin's `sub` and asks the identity module's public `UserAccountLookup.find(UUID)`. The call
  is refused with the tool error "Your JordyLab account can no longer use the connector. Sign in again." when the account is
  missing or its role is not `admin` (FR-004, plan K11). It reads the account on every call and caches nothing, so a
  revoked account is refused on its next call. Depends on T006.
- [x] T071 [P] Add the Flyway migration `jordylab-be/src/main/resources/db/migration/V20261104001__mcp_oauth_tables.sql`:
  `CREATE SCHEMA IF NOT EXISTS mcp;`, then Spring Authorization Server's JDBC tables `mcp.oauth2_authorization` and
  `mcp.oauth2_authorization_consent`, taken from the starter's own JDBC schema scripts and placed in schema `mcp`
  (data-model.md). Do not create `oauth2_registered_client`: the one client is defined in configuration. The version must sort
  after `V20261103005__identity_copy_keycloak_users.sql` on `main`. Gate: the migration runs on an empty Testcontainers
  Postgres in T075.
- [x] T072 [P] The token customizer is a bean in `jordylab-be/src/main/java/dev/jordy/jordylab/mcp/McpAuthorizationServerConfiguration.java`
  (no separate `McpTokenCustomizer.java`): for access tokens it sets `aud` to `jordylab-mcp` only, `sub` to the account's
  UUID and `roles` to the admin role; Spring Authorization Server writes `scope` from the authorized scopes itself
  (contracts/oauth-authorization-server.md, Tokens). Refresh tokens stay opaque. Depends on T004.
- [x] T070 Create `McpAuthorizationServerConfiguration` in `jordylab-be/src/main/java/dev/jordy/jordylab/mcp/McpAuthorizationServerConfiguration.java`:
  (a) the Spring Authorization Server `SecurityFilterChain` for `/oauth2/**` and `/.well-known/oauth-authorization-server`, with
  a `securityMatcher` that does not overlap `/mcp`; (b) one `RegisteredClient` `jordylab-mcp-connector`, defined in code:
  public (`ClientAuthenticationMethod.NONE`), grants `authorization_code` and `refresh_token`, PKCE required with `S256` only,
  the Claude callback and the fixed Claude Code (`http://localhost:19877/callback`,
  `http://127.0.0.1:19877/callback`) and OpenCode (`http://127.0.0.1:19876/mcp/oauth/callback`,
  `http://localhost:19876/mcp/oauth/callback`) loopback callbacks, scope `jordylab.mcp`, consent not required; (c) token
  settings: access token `accessTokenTtl`, refresh token `refreshTokenTtl`, `reuseRefreshTokens(false)`; (d) the RSA JWK source
  and `JwtEncoder`, keyed as T078 decides; (e) the JDBC authorization and consent services on the `mcp` schema (T071); (f) the
  authorization endpoint's unauthenticated entry point answers `302` to `/login?returnUrl=<the request's path and query, starting
  with /oauth2/authorize, percent-encoded>` (contracts/oauth-authorization-server.md, login bridge). It renders no page. The
  return path itself depends on T074. Depends on T004, T071, T072, T073, T074 and T078.
- [x] T073 Refuse non-admin accounts at the authorization request, in the same class as T070
  (`jordylab-be/src/main/java/dev/jordy/jordylab/mcp/McpAuthorizationServerConfiguration.java`). An authorization-request
  validator reads the signed-in account through `UserAccountLookup.find(UUID)` and refuses anything other than an existing
  `admin` with `access_denied`, so a guest who signs in receives no code. Depends on T070.
- [x] T074 **DECIDED 2026-11-04: option A** (accept the small web change). The login page returns through the Angular router:
  `jordylab-fe/libs/shared/auth/src/lib/login.component.ts` calls `this.#router.navigateByUrl(this.#returnUrl)` after sign-in.
  A `returnUrl` of `/oauth2/authorize?…` names no Angular route, so the connector sign-in would stop at the login page.
  Confirmed by reading the code on `main` (2026-11-04); to be re-checked on a running dev server before the decision is
  built. The fix is a small web-app change: when the sanitised return path starts with `/oauth2/`, use `window.location.assign`
  instead of the router, and cover both branches in `login.component.spec.ts`. `safeReturnUrl` keeps rejecting `//`, absolute
  URLs and `/login`. SC-011 and the plan's frontend assumption are amended to match. The rejected alternative was (B), a
  separate server-rendered sign-in page in the backend, which adds a second login UI.
- [x] T075 Test the authorization-code flow in `jordylab-be/src/test/java/dev/jordy/jordylab/mcp/McpAuthorizationServerTest.java`.
  Use `@ActiveProfiles("e2e")` so the identity test sign-in (`POST /api/identity/test/sign-in`, spec 019 US7) creates the
  session, with Testcontainers Postgres for the `mcp` tables and a fixture admin and a fixture guest from the identity
  module's test builders. Steps: an unauthenticated `GET /oauth2/authorize` with a PKCE `S256` challenge answers `302` to
  `/login?returnUrl=…`; after the test sign-in the same request returns a code to the Claude callback; `POST /oauth2/token`
  with the verifier returns an access token whose `aud` is `jordylab-mcp`, `sub` is the admin's id, `roles` contains `admin`
  and whose lifetime is 600 seconds; a refresh token is issued for the public client, it rotates and the old one is
  refused; a guest gets `access_denied` (T073);
  the `mcp` authorization row exists. Use `assertSoftly`, no `any()`, and `@Language("JSON")` bodies. This test is the
  authentication gate for Phase 2.
- [x] T078 **DECIDED 2026-11-04: option A** (signing key, before T070). The access tokens are signed with an RSA key that is
  generated at start-up and kept in memory. A deploy makes existing access tokens invalid, but the refresh token, stored in
  the `mcp` schema, renews them. T062 checks this in Claude. If it does not work, the rejected option B is a fixed key from
  the SOPS-encrypted production environment, which costs one more secret to rotate.

### /mcp chain (token validation)

- [x] T010 Replace the Keycloak-era `McpJwtDecoderFactory` (`jordylab-be/src/main/java/dev/jordy/jordylab/mcp/service/McpJwtDecoderFactory.java`)
  with a `JwtDecoder` bean built from the authorization server's JWK source (`NimbusJwtDecoder.withJwkSource`), so the chain
  validates against local keys with no network call. The issuer validator checks `publicBaseUrl` and the audience validator
  checks `jordylab-mcp` (research R3). Delete the factory and its lazy issuer lookup. Removing the factory also resolves the
  review's point on injection style (plan Notes, point 2). Depends on T006 and T070.
- [x] T011 [P] Create `McpAuthenticationEntryPoint` in
  `jordylab-be/src/main/java/dev/jordy/jordylab/mcp/service/McpAuthenticationEntryPoint.java`. It answers `401` with
  `WWW-Authenticate: Bearer resource_metadata="<publicBaseUrl>/.well-known/oauth-protected-resource/mcp"` and no data
  (contracts/mcp-endpoint.md, section 2a).
- [x] T012 Update `McpConfiguration` in `jordylab-be/src/main/java/dev/jordy/jordylab/mcp/McpConfiguration.java`. Its
  `SecurityFilterChain`, annotated `@Order(Ordered.HIGHEST_PRECEDENCE)`, matches `/mcp` and `/.well-known/oauth-protected-resource/**`.
  The metadata path is `permitAll`. `/mcp` requires `hasRole("admin")`, with a converter that maps the `roles` claim to
  authorities. The resource server uses the decoder from T010 and the entry point from T011. CSRF is disabled for this chain.
  Keep the bearer-method clearing that fixed the duplicated `header` entry (2026-11-04). Depends on T006, T010, T011 and T070.
  Do not edit `shared/config/SecurityConfig.java`.
- [x] T076 Test in `jordylab-be/src/test/java/dev/jordy/jordylab/mcp/McpSecurityTest.java` (with T020): a connector token is
  refused on `/api`. `GET /api/gamecatalog/games` with the bearer token returns `401` with no data, because the `/api` chain
  is session-only (plan K5). Depends on T012.

### Guardrails

- [x] T013 [P] Create `McpRateLimiter` in `jordylab-be/src/main/java/dev/jordy/jordylab/mcp/service/McpRateLimiter.java`.
  It keeps one fixed one-minute window per admin subject, with the limit from `McpProperties.toolCallsPerMinute`, a
  `Clock` injected for tests, and the rules in data-model.md ("In-memory state"). Expired windows are removed.
- [x] T014 Amend `McpToolCallAdvice` in `jordylab-be/src/main/java/dev/jordy/jordylab/mcp/service/McpToolCallAdvice.java`.
  It is an `@Around` advice on `@McpTool` methods. Per call, in order: the account check (T069), then the rate limit (T013),
  then the method, then one line on the logger `dev.jordy.jordylab.mcp.audit`:
  `mcp tool=<name> admin=<sub> outcome=<ok|rate_limited|refused|error> durationMs=<n>`. A refused account returns its tool error
  and does not run the method. A rate-limited call returns "Too many requests in one minute. Try again shortly." without running
  the method. Any other exception becomes "The tool could not read this data right now.", and the log carries the exception
  class only. Keep `McpToolException`'s empty cause (plan Notes, point 3). Add `refused` to the outcome list in
  `contracts/mcp-endpoint.md`, section 2c. Depends on T004, T013, T069.

### Gateway routes

- [x] T015 **OBSOLETE 2026-11-04**: the connector client in `jordylab-be/compose/keycloak-realm-export.json` belonged to
  Keycloak, which spec 019 removed. The client is now defined in code (T070). Nothing is carried over.
- [x] T016 **OBSOLETE 2026-11-04**: the same client in `deploy/keycloak/realm-prod.json`. No realm file carries it now.
- [x] T017 Update the routes in `deploy/compose/Caddyfile` (re-apply on the fresh branch, because the pre-019 edit conflicts
  with `main`). Add `@oauth path /oauth2/* /.well-known/oauth-authorization-server*`, `@mcp path /mcp /mcp/*` and
  `@protected_resource path /.well-known/oauth-protected-resource /.well-known/oauth-protected-resource/*`, each with
  `handle` and `reverse_proxy backend:8080`, following the file's existing pattern. Remove
  `/.well-known/assetlinks.json` from the `@backend` matcher only if `main`'s file still has it (spec 018 removed that
  endpoint). The retired k3s `deploy/k8s/base/gateway.yaml` is not edited (research R10). Confirm with T054.

### Foundational tests

- [x] T018 [P] Test `McpRateLimiter` in `jordylab-be/src/test/java/dev/jordy/jordylab/mcp/service/McpRateLimiterTest.java`:
  60 calls are accepted and the 61st refused; the window resets after one minute; two admins are counted separately.
  Use a fixed `Clock`, AssertJ `assertSoftly`, and no `any()`.
- [x] T019 Update `jordylab-be/src/test/java/dev/jordy/jordylab/mcp/rest/controller/ProtectedResourceMetadataControllerTest.java`:
  both paths return `200` with the exact snake_case fields, need no token, and carry `authorization_servers` equal to the
  configured issuer (`["https://jordylab.be"]` in production) and `scopes_supported` equal to `["jordylab.mcp"]`.
  Depends on T009.
- [x] T020 Update `jordylab-be/src/test/java/dev/jordy/jordylab/mcp/McpSecurityTest.java` with MockMvc. `POST /mcp` without a
  token returns `401` with the exact `WWW-Authenticate` pointer. An admin token passes the chain. A guest token returns
  `403` with no data. An expired token returns `401`. Build the tokens with `McpJwtTestBuilder` (kept from the pre-019 branch)
  and the `jwt()` post-processor, with `@Language("JSON")` bodies. The `/api` refusal is T076. Depends on T012.
- [x] T021 Create `jordylab-be/src/test/java/dev/jordy/jordylab/mcp/service/McpJwtDecoderTest.java` (replaces
  `McpJwtDecoderFactoryTest`, which is deleted with the factory): a token without `jordylab-mcp` is rejected, one with it is
  accepted, and a token from another issuer is rejected. `McpJwtTestBuilder` (`jordylab-be/src/test/java/dev/jordy/jordylab/mcp/McpJwtTestBuilder.java`,
  `@UtilityClass`, `DEFAULT_*` constants, `aDefaultJwt()` and `aJwt()` builder methods, the canonical TestBuilder structure;
  constitution IV) stays as built on the pre-019 branch. Depends on T010.
- [x] T022 Amend `jordylab-be/src/test/java/dev/jordy/jordylab/mcp/service/McpToolCallAdviceTest.java`: a refused account
  (T069) does not run the tool and logs `outcome=refused`. The other cases stay: the audit line names the admin, tool, outcome
  and duration and contains no arguments (captured log); a rate-limited call does not run the tool (a counting fake, asserted
  by value); an exception returns the sanitised message and its log line has no exception message text. Depends on T014.
- [x] T023 Re-run `jordylab-be/src/test/java/dev/jordy/jordylab/mcp/McpRateLimitIntegrationTest.java` on the new chain:
  sixty-one `tools/call` JSON-RPC requests from one admin through MockMvc, with the token built by `McpJwtTestBuilder`. The 61st
  returns an error result with the limit text, and the tool's service method is never invoked (`verify(never())`). This
  proves the advice applies to beans the framework calls (plan K2). Depends on T012 and T014.

### Gates (must pass before Phase 3)

- [x] T024 **OBSOLETE 2026-11-06**: the Keycloak-era MCP Inspector gate was replaced by the authorization-code flow test
  and the production checks (both discovery documents, the 401 pointer, the login bridge, the nine registered tools).
- [x] T025 **OBSOLETE 2026-11-06**: `McpAuthorizationServerTest` asserts the access-token claims and the 10-minute
  lifetime, refresh issuance/rotation and reuse refusal; production answers invalid codes and refresh tokens with
  `invalid_grant`. The Keycloak admin-console checks are history.


**Checkpoint**: foundation ready; T075 passes and T024/T025 are superseded by it and by the production checks. The pre-019
run (2026-11-04, throwaway Keycloak) is history: it recorded the metadata document, the 401 pointer, `initialize` (server
`jordylab`, protocol 2025-06-18, tools only), `tools/list` (nine read-only tools) and the token claims on the Keycloak-era
chain. The CI run on the draft PR #213 was green on the old base; its three review points are in the plan's Notes.

---

## Phase 3: User Story 1 - Connect Claude and ask about the game catalog (Priority: P1) 🎯 MVP

**Goal**: an admin can ask Claude about the game catalog and get answers that match the Game Catalog screens.

**Independent Test**: after the deploy, quickstart steps 4 and 5 (Claude Desktop) and 6 (Claude mobile) return the same
answers as the Game Catalog screens. Before the deploy, the MCP Inspector lists the five catalog tools and returns data
from `search_games`.

### Tests for User Story 1

- [x] T026 [P] [US1] Test the catalog tools in
  `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/rest/controller/GameCatalogMcpToolsTest.java` with Testcontainers
  Postgres and fixtures built with the existing `GameTestBuilder`, `GameInstallationTestBuilder`, `GameMarkTestBuilder` and
  `ScanSourceTestBuilder` (constitution IV): `search_games` respects platform, local multiplayer and
  `minLocalPlayers` (confirmed counts only); unknown facts come back as `null`; `limit` is clamped to 50 with the correct
  `totalMatching`; `get_game` contains no voter identifiers; an unknown game id returns a "not found" tool error.

### Implementation for User Story 1

- [x] T027 [P] [US1] Create the catalog result records in `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/rest/controller/model/`:
  `GameSearchItem.java`, `GameDetail.java`, `PlaceRef.java`, `PlaceSummary.java`, `SourceSummary.java`, `LibraryStatus.java`,
  with the fields in data-model.md. No artwork, voter, path or machine fields.
- [x] T028 [US1] Create `GameCatalogMcpTools` in
  `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/rest/controller/GameCatalogMcpTools.java` with `search_games`.
  It maps the arguments onto the existing `GameFilter` builder, calls `GameQueryService.getGames`, and wraps the page with
  `McpToolPage` (research R9). Depends on T007, T027.
- [x] T029 [US1] Add `get_game` to `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/rest/controller/GameCatalogMcpTools.java`, using the service behind `GET /games/{id}` (confirm its name
  in the code), mapped to `GameDetail`. An unknown id returns the not-found tool error. Depends on T028.
- [x] T030 [US1] Add `list_places` to `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/rest/controller/GameCatalogMcpTools.java`, using the services behind `GET /places` and
  `GET /platforms`, mapped to `PlaceSummary`.
- [x] T031 [US1] Add `list_sources` to `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/rest/controller/GameCatalogMcpTools.java`, using the scan-source list behind `GET /sources`,
  mapped to `SourceSummary` with paths and machine identifiers left out.
- [x] T032 [US1] Add `get_library_status` to `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/rest/controller/GameCatalogMcpTools.java`, using the service behind `GET /library/status`,
  mapped to `LibraryStatus` with `refreshRunning`.
- [x] T033 [US1] In `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/rest/controller/GameCatalogMcpTools.java`, map the argument enums (`sources`, `mark`, `romStatus`, `sort`,
  `installStatus`) onto the existing enums. An unknown value returns a clear tool error that lists the accepted values.
- [x] T034 [US1] Give each catalog tool a description that says what it returns and ends with "Read-only.", as FR-017
  requires, and the read-only hint on each `@McpTool` method in `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/rest/controller/GameCatalogMcpTools.java`.

**Checkpoint**: User Story 1 works through MCP Inspector. Claude Desktop and mobile checks run in Phase 8.

---

## Phase 4: User Story 2 - Ask about finance without opening the app (Priority: P2)

**Goal**: an admin can ask Claude about news, portfolio, wishlist and the latest briefing, with the same values the
Finance News screens show.

**Independent Test**: MCP Inspector lists the four finance tools and each returns the same values as the matching screen.
After the deploy, quickstart step 5 (finance rows) confirms it through Claude.

### Tests for User Story 2

- [x] T035 [P] [US2] Test the finance tools in
  `jordylab-be/src/test/java/dev/jordy/jordylab/fna/rest/controller/FnaMcpToolsTest.java` with Testcontainers Postgres and the existing
  fixtures `ArticleTestBuilder`, `PortfolioPositionTestBuilder`, `BriefingTestBuilder` and `WishlistItemDtoTestBuilder`
  (constitution IV):
  `search_articles` matches titles only, newest first, honours `offset` and clamps `limit`; `get_portfolio` sums only
  the priced positions and sets `allPriced` to false when one is unpriced; `get_latest_briefing` returns
  `NO_BRIEFING_YET` when none exists and never calls `triggerBriefing` (`verify(never())`); `get_wishlist` returns the
  fetch times.

### Implementation for User Story 2

- [x] T036 [US2] Add the title search to `jordylab-be/src/main/java/dev/jordy/jordylab/fna/domain/repository/ArticleRepository.java`
  as a `Pageable` method, `Page<Article> findByTitleContainingIgnoreCaseOrderByPublishedAtDesc(String title, Pageable pageable)`,
  so `offset` works. This replaces the `Top50` name in research R9.
- [x] T037 [US2] Add `searchArticles(String text, int offset, int limit)` to
  `jordylab-be/src/main/java/dev/jordy/jordylab/fna/service/FnaService.java`. Empty text returns the newest articles.
  Depends on T036.
- [x] T038 [P] [US2] Create the finance result records in `jordylab-be/src/main/java/dev/jordy/jordylab/fna/rest/controller/model/`:
  `ArticleItem.java`, `PortfolioItem.java`, `PortfolioSummary.java`, `WishlistItem.java`, `BriefingView.java`
  (data-model.md).
- [x] T039 [US2] Create `FnaMcpTools` in `jordylab-be/src/main/java/dev/jordy/jordylab/fna/rest/controller/FnaMcpTools.java`
  with `search_articles`, mapping `FnaService.searchArticles` to `McpToolPage<ArticleItem>`. Depends on T037, T038.
- [x] T040 [US2] Add `get_portfolio` to `jordylab-be/src/main/java/dev/jordy/jordylab/fna/rest/controller/FnaMcpTools.java`: `FnaService.getPortfolioPositions` mapped to
  `PortfolioSummary`, with `value` per position and the total of the priced positions.
- [x] T041 [US2] Add `get_wishlist` to `jordylab-be/src/main/java/dev/jordy/jordylab/fna/rest/controller/FnaMcpTools.java`: `FnaService.getWishlistItems` mapped to `McpToolPage<WishlistItem>`.
- [x] T042 [US2] Add `get_latest_briefing` to `jordylab-be/src/main/java/dev/jordy/jordylab/fna/rest/controller/FnaMcpTools.java`: `FnaService.getLatestBriefing` mapped to `BriefingView`,
  with `NO_BRIEFING_YET` when empty. The tool must not call `triggerBriefing`.
- [x] T043 [US2] Give each finance tool a description per FR-017 and the read-only hint. The `search_articles` description
  says it matches titles only (spec change S2).

**Checkpoint**: User Story 2 works through MCP Inspector.

---

## Phase 5: User Story 3 - Prove the connector cannot change anything (Priority: P2)

**Goal**: the tool list is fixed, every tool is read-only, and no call can change data, start work or spend AI tokens.

**Independent Test**: the surface test (T044) and the unknown-tool test (T045) pass. After the deploy, quickstart step 7
(in Phase 8) confirms it through Claude.

### Tests for User Story 3

- [x] T044 [P] [US3] Test the tool surface in `jordylab-be/src/test/java/dev/jordy/jordylab/mcp/McpToolSurfaceTest.java`:
  the registered names are exactly the nine in `specs/021-jordylab-mcp-server/contracts/tools.md`; each has the read-only
  hint; no name contains a write verb (create, update, delete, approve, reject, scan, refresh, sync, generate, post,
  send, trigger, reset, end); each description contains "Read-only.".
- [x] T045 [US3] Add to `jordylab-be/src/test/java/dev/jordy/jordylab/mcp/McpToolSurfaceTest.java`: a `tools/call` for an
  unknown name such as `create_game` or `trigger_briefing` returns the protocol's error and writes no `ok` audit line.
- [x] T046 [US3] Add to `jordylab-be/src/test/java/dev/jordy/jordylab/mcp/McpToolSurfaceTest.java`: a `tools/call` for an
  existing tool with an out-of-range `limit` (for example 500) returns at most 50 items and a correct `hasMore` (FR-009).

### Implementation for User Story 3

- [x] T047 [US3] Review every `@McpTool` method in `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/rest/controller/GameCatalogMcpTools.java`
  and `jordylab-be/src/main/java/dev/jordy/jordylab/fna/rest/controller/FnaMcpTools.java`: each has the read-only hint, and
  none calls a `save`, `delete`, `trigger`, `refresh`, `scan` or `generate` method. Record the review in the commit body.

**Checkpoint**: the read-only promise is covered by tests and by review.

---

## Phase 6: Polish and cross-cutting concerns

**Purpose**: documentation, boundary and build checks, and the CI check set.

- [x] T048 [P] Update the module table in `AGENTS.md` (root): the `mcp` row now owns the `mcp` schema (the authorization
  server's tables) and the endpoints `/mcp`, `/oauth2/*` and the two discovery documents. Add one paragraph under
  Infrastructure about the connector and its deploy path.
- [x] T049 [P] Amend the `mcp` section of `jordylab-be/AGENTS.md`: the authorization server and its one client, the `/mcp`
  chain and its audience, the per-call account check, the tool rule (read-only, one service call each, no AI call), and
  the guardrails.
- [x] T050 [P] Amend the connector section of `docs/runbook.md`: how to connect (quickstart step 4, client `jordylab-mcp-connector`),
  revoking access (the next call is refused, quickstart step 10), the 30-day refresh lifetime (this replaces the 90-day cap),
  and what to check when the connector fails: the 401 pointer, the audience, the account check, the rate limit and the login bridge.
- [x] T051 **OBSOLETE 2026-11-04**: the row in `deploy/keycloak/README.md` recorded the Keycloak client, which no longer
  exists. Add no row.
- [x] T052 Run `cd jordylab-be && ./gradlew :test --tests "*ModularityTests*"` and fix any boundary violation. The `mcp`
  module may use the identity module's public root API (`UserAccountLookup`, `AuthenticatedUser`). The domain modules may
  depend only on the `mcp` root (for the tool annotations and `McpToolPage`).
- [x] T053 Run `cd jordylab-be && ./gradlew :test --tests "*Mcp*"`, then `--tests "*identity*"` to confirm the session chain
  is unchanged. Every test in both families passes.
- [x] T054 Validate the Caddyfile after T017 with the `caddy:2` container (Podman), because `caddy` is not installed
  locally: `podman run --rm -v "$PWD/deploy/compose/Caddyfile:/etc/caddy/Caddyfile:ro" caddy:2 caddy validate --config
  /etc/caddy/Caddyfile --adapter caddyfile` answered "Valid configuration". The three route groups (`/oauth2/*` with the
  authorization server metadata, `/mcp`, and the protected resource metadata) are present.
- [x] T055 Record the outcomes in `specs/021-jordylab-mcp-server/research.md` R1, R2 and R6. The Keycloak-era
  Inspector/token gates are superseded (T024/T025); production confirmed the stateless endpoint, the sign-in and the refresh
  behaviour on 2026-11-06.
- [x] T056 The CI set passed on PR #220 (the connector) and PR #223 (the local agents): `test-backend`, `test-frontend`,
  `e2e-web`, `app-boot`, `refs`, `secret-scan` and `licence-check` were green.

---

## Phase 7: OPERATIONS before merge

**Purpose**: the configuration the new chain needs before the merge, which deploys to production (OI-2).

**Gate (I1): met.** The Compose cutover is live (`specs/020-compose-deployment/`).

- [x] T057 **OBSOLETE 2026-11-04**: the client `jordylab-mcp-connector` was created in the live Keycloak realm on 2026-11-04.
  Nothing is applied to Keycloak before the merge. The client stays unused and leaves with Keycloak when `main` deploys the
  identity stack (see T079). Do not recreate it.
- [x] T058 **OBSOLETE 2026-11-04**: the `deploy/keycloak/README.md` row (T051) is not needed; see T057.
- [x] T059 **OBSOLETE 2026-11-04**: the local Keycloak client was for the pre-019 gate only; the local gate is T024 and T025
  on the authorization server.
- [x] T077 **OPERATIONS** (verified 2026-11-06): the production discovery documents and the protected resource all use
  `https://jordylab.be`, the value `JORDYLAB_APP_BASE_URL` supplies. The env file was not read (secrets rule); the issuer,
  both discovery documents and the protected resource's `resource` value prove the setting.

---

## Phase 8: OPERATIONS after merge

**Purpose**: the checks only the deployed system can answer. Claude connects from its cloud, so these steps need the live
endpoint.

- [x] T060 **OPERATIONS**: quickstart steps 1 and 2 on production (metadata document, with `authorization_servers` equal to
  `["https://jordylab.be"]`; `401` pointer through the public proxy). Record the output in `docs/runbook.md`.
- [x] T061 **OPERATIONS**: quickstart step 3 on production: `https://jordylab.be/.well-known/oauth-authorization-server` shows
  the issuer `https://jordylab.be`, the authorize and token endpoints under `/oauth2/`, and `S256`. The earlier pass
  (2026-11-04) was against Keycloak and does not count. Record it in `docs/runbook.md`.
- [x] T062 **OPERATIONS**: quickstart step 4 (Claude Desktop connects with client `jordylab-mcp-connector`, nine read-only tools
  listed). Check that the browser passes through `/login` and returns to the authorization request (T074). Check that a
  refresh after a deploy works (T078, option A). Record the result in `docs/runbook.md`. This is Jordy's action in Claude.
- [x] T063 **OPERATIONS**: quickstart step 5 (the ten questions, five catalog and five finance, each compared with its screen).
  The owner validated them on 2026-11-06 with no differences reported. Exact per-answer timings were not captured; the
  runbook records the owner confirmation, not a stopwatch log.
- [x] T064 **OPERATIONS**: quickstart step 6 (Claude mobile over mobile data). Record it in `docs/runbook.md`. This closes
  the mobile assumption in the spec.
- [x] T065 **OPERATIONS**: quickstart step 7 (read-only check: ask for changes, then compare the web app, the AI call count
  and Linear before and after). Record the result in `docs/runbook.md`.
- [x] T066 **OPERATIONS**: quickstart step 8 (parallel questions in one chat). If refresh fails, the fix is in code (refresh
  rotation in `McpAuthorizationServerConfiguration`, T070), not in a Keycloak setting. Record the result in `docs/runbook.md`.
- [x] T067 **OPERATIONS**: quickstart step 9 (audit lines in production logs: admin, tool, outcome, no arguments). Record
  the result in `docs/runbook.md`.
- [x] T068 **OPERATIONS**: quickstart step 10 (revocation with a throwaway admin account: the next tool call is refused). Restore
  its role and delete the account afterwards. Record the result in `docs/runbook.md`.
- [x] T079 **OPERATIONS** (after the merge, once the identity stack is deployed): confirm that Keycloak and its
  `jordylab-mcp-connector` client are gone from production, and record it in `docs/runbook.md`.

---

## Dependencies and execution order

- **Phase 1** (T001–T004) first. T001 to T003 are owner decisions already recorded. T004 blocks Phase 2.
- **Phase 2** order, once T004 is done:
  1. The decisions T074 (login return path) and T078 (signing key), already made, come before any authorization server code.
  2. T006 and T071 (migration) are independent of each other; T069, T072 and T007–T008 are independent as well.
  3. T070 (authorization server, needs T071, T072, T074, T078), then T073 (admin-only request validator), then T075 (the flow gate).
  4. T009 and T010 (need T006, T070 and T008), then T012 (the `/mcp` chain, needs T010 and T011).
  5. T014 (advice, needs T069 and T013), then T020, T021, T022, T023 and T076 (tests of the chain and the advice).
  6. T017 (Caddy) needs no code and can run in parallel with step 2. T018 and T019 follow their code.
  7. T024 needs T075 and a running backend; T025 needs T070 to T073 run locally.
- **Phase 3 (US1)** depends on Phase 2. Its tests (T026) come first. T027 is independent of the others. T028 needs T007
  and T027, and T029 to T034 extend the same file, so they run in sequence.
- **Phase 4 (US2)** depends on Phase 2 only. It can run in parallel with Phase 3, because it touches the `fna` module
  and not `gamecatalog`.
- **Phase 5 (US3)** depends on Phases 3 and 4 (the surface test needs all nine tools).
- **Phase 6** depends on all user story phases.
- **Phase 7** depends on T077 and the decisions T074 and T078. It must finish before the merge.
- **Phase 8** depends on the merge and the deploy.

### Parallel example: Phase 2

```text
T006 McpProperties
T007 McpToolPage        ← run together
T008 ProtectedResourceMetadata
T069 McpAccountStatusValidator
T071 Flyway migration
T072 token customizer
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
- **Before the merge**: all of Phases 1 to 6 pass, T074 and T078 are built, and Phase 7 is done (OI-2). The
  PR is one branch and one spec (the house rule for specs). The branch is a fresh one from `main`; the pre-019 commits are
  carried over, not rebased.
- **After the merge**: Phase 8 runs with Jordy in Claude. Its findings go to `docs/runbook.md`.

## Summary

- Total tasks: **79** (T001–T079); **79 done, 0 open** (2026-11-06)
- Setup: 4 (3 owner decisions already recorded, 1 dependency)
- Foundational: 30 (T005–T025, T069–T076, T078): the configuration, discovery, `/mcp` chain, guardrail and test tasks
  (T005–T023, including the two obsolete realm tasks T015–T016), the two gates (T024–T025), the authorization-server tasks
  (T069–T076, including the owner decision T074), and the signing-key decision (T078)
- User Story 1 (P1, catalog): 9 (T026–T034), all kept
- User Story 2 (P2, finance): 9 (T035–T043), all kept
- User Story 3 (P2, read-only proof): 4 (T044–T047), all kept
- Polish: 9 (T048–T056)
- Operations before merge: 4 (T057–T059 closed as obsolete, T077 open)
- Operations after merge: 10 (T060–T068, T079)
- OBSOLETE (closed, kept for ID stability): T015, T016, T051, T057, T058, T059
- Owner decisions: all three made 2026-11-04 (fresh branch from `main`; T074 option A; T078 option A)
- Closed 2026-11-06: all tasks done; T024/T025 are superseded by the flow test and production, and the owner validated the live connector
- Parallel opportunities: 16 tasks marked [P] (each touches a different file). User Stories 1 and 2 can run in parallel
  after Phase 2
- Independent test per story: US1 (quickstart 4–6), US2 (MCP Inspector finance rows and quickstart 5), US3 (T044–T046
  and quickstart 7)
- MVP scope: Phases 1–3 (User Story 1)
- Format check: every task starts with `- [ ]` or `- [x]`, has an ID, carries a `[US#]` label only in story phases, and names a file
  path
