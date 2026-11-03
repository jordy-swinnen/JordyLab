# Implementation Plan: JordyLab MCP Server (read-only)

**Branch**: `021-jordylab-mcp-server` (git branch `jordyswinnen/jor-79-add-a-jordylab-mcp-server-so-admins-can-ask-claude-about`) | **Date**: 2026-11-03 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/021-jordylab-mcp-server/spec.md`

**Note**: This plan was produced by `/speckit-plan`. Its clarifications were answered in the spec (five answers, Q1 to
Q5). The decisions are in [research.md](research.md) (R1–R12).

## Summary

An admin connects Claude Desktop and Claude mobile to JordyLab through `https://jordylab.be/mcp`. Claude signs in with a
public Keycloak client, and then calls nine read-only tools: five for the game catalog and four for finance. Every call
is admin-only, limited to 60 a minute, returns at most 50 items, and leaves one audit line in the backend log. The
connector never writes, and it never calls an AI model.

Technical approach, in one line each:
- **Transport**: Spring AI Streamable-HTTP in stateless mode, so a deploy's restart does not drop a connected client (R1).
- **Auth**: a dedicated security chain for `/mcp`; the token must carry the audience `jordylab-mcp` and the admin role (R2, R3).
- **Discovery**: protected resource metadata (RFC 9728) served by the backend, reached through two gateway routes (R4, R10).
- **Keycloak**: one public client, PKCE S256, refresh rotation, no `offline_access` (R5, R6).
- **Guardrails**: an AOP advice on each tool gives the rate limit, the audit line and the error mapping (R7).
- **Placement**: a new `mcp` module, with tool classes in the domain modules beside their controllers (R8). No schema.

## Technical Context

**Language/Version**: Java 25 (backend); YAML (gateway); JSON (realm files).

**Primary Dependencies**: Spring Boot 4.1.1 and the existing OAuth2 resource server; Spring AI 2.0.1 via the BOM, with
the new `spring-ai-starter-mcp-server-webmvc`; a new AOP runtime for the tool advice (starter name confirmed in the
spike); Spring Modulith 2.1.1 (existing); Keycloak 26.7.4 (existing, `deploy/containers/keycloak/Containerfile`).

**Storage**: none. No table, no migration, no schema. The rate limit is in process memory (R7). The connector client is
realm configuration.

**Testing**: JUnit 5, AssertJ (`assertSoftly` for multi-assertion tests), Mockito without `any()`, MockMvc with
`@Language("JSON")`, the shared `TestSecurityConfig` for slice tests, Testcontainers Postgres for query tests,
`ModularityTests`. Live proof: `quickstart.md` steps 1–10, with Claude Desktop and Claude mobile.

**Target Platform**: single host on OVH VPS-2, public at `https://jordylab.be`. It has run Docker Compose behind Caddy since the
cutover (`specs/020-compose-deployment/`, live 2026-11-03). The k3s stack is retired, and this feature is released on the Compose stack. Claude
calls it from Anthropic's cloud, so it must be public HTTPS (Claude's docs).

**Project Type**: backend feature (a new module and tool adapters), plus a realm client, gateway routes and docs. No
frontend change.

**Performance Goals**: 95% of questions answered within 10 s (SC-002). A tool call adds one database read and no model call.

**Constraints**:
- Read-only: no mutating route, no AI call, no notification (FR-011, FR-012).
- Admin only, on the audience `jordylab-mcp` (FR-003, FR-021).
- At most 50 items per call; at most 60 calls per admin per minute (FR-009, FR-020).
- No secret, no hidden personal field, no argument or result in logs (FR-014, FR-015, FR-016).
- 60-second access tokens (spec 014); SSO session 30 days idle, 90 days maximum (realm).

**Scale/Scope**: one admin, a few hundred tool calls a month, nine tools, one instance.

## Constitution Check

*Checked against `.specify/memory/constitution.md` v1.1.2 after Phase 1 design.*

| Principle | Applies to | Result |
|-----------|-----------|--------|
| I. Clean Code | Tool methods, advice, limiter | Pass. Full names (`McpRateLimiter`, `GameCatalogMcpTools`). Each tool method is one service call and one mapping. |
| II. Fail Fast, No Silent Failures | Rate limit, auth, mapping | Pass. Over the limit is refused and visible. A failing tool returns a sanitized error and logs its class. An invalid token is a 401, never a partial answer. |
| III. Immutable, Builder-First | Records | Pass. Response shapes are records (AGENTS prefers records for DTOs). `McpProperties` is a `@ConfigurationProperties` value, not a hand-written constructor. |
| IV. Testing Discipline | All tests | Pass. AssertJ, `assertSoftly`, no `any()`, TestBuilders for JWTs and fixtures, descriptive test names. Synthetic catalog data only inside Testcontainers. |
| V. Language & Tooling Currency | Java | Pass. Java 25, Boot 4.1.1, Spring AI 2.0.1 (the current BOM pin). |
| Architecture (Additional constraints) | Module boundaries | Pass. `mcp` depends on no domain module. Domain modules depend on `mcp` only for `McpToolPage`. No internal import crosses a module. |
| AI routing (AGENTS) | Any model call | Pass. The feature makes no model call. `get_latest_briefing` reads the stored briefing and never triggers one. |
| Flyway (AGENTS) | Schema | Not applicable: no schema, no migration. |
| Secrets (AGENTS) | Responses and logs | Pass. Records list fields explicitly; the audit line holds `sub`, tool, outcome and duration only. |
| Governance | Docs | Pass. Module table and the connector runbook section are updated in the same change (tasks). |

**Gate result**: pass. The deviations are recorded under Complexity Tracking.

## Project Structure

### Documentation (this feature)

```text
specs/021-jordylab-mcp-server/
├── spec.md                       # /speckit-specify + /speckit-clarify (+ planning edits, see "Spec changes from planning")
├── checklists/requirements.md
├── plan.md                       # this file
├── research.md                   # Phase 0: R1–R12
├── data-model.md                 # Phase 1: envelope, tool records, in-memory window, audit line
├── quickstart.md                 # Phase 1: validation steps 1–10 and the automated test list
├── contracts/
│   ├── mcp-endpoint.md           # discovery, 401 pointer, auth outcomes, bounds
│   ├── tools.md                  # the nine read-only tools
│   └── keycloak-connector-client.md
└── tasks.md                      # /speckit-tasks (not created here)
```

### Source Code (repository root): what changes

```text
jordylab-be/
├── build.gradle.kts                                    # + spring-ai-starter-mcp-server-webmvc; + AOP runtime
├── src/main/resources/application.yaml                 # spring.ai.mcp.server.* (protocol STATELESS, tools only)
├── src/main/java/dev/jordy/jordylab/
│   ├── mcp/                                            # NEW module
│   │   ├── McpConfiguration.java                       # SecurityFilterChain for /mcp and the metadata path (@Order highest)
│   │   ├── McpProperties.java                          # publicBaseUrl (JORDYLAB_APP_BASE_URL), audience, limits
│   │   ├── McpToolPage.java                            # public envelope (items, returned, totalMatching, hasMore)
│   │   ├── rest/controller/
│   │   │   ├── ProtectedResourceMetadataController.java
│   │   │   └── model/ProtectedResourceMetadata.java
│   │   └── service/
│   │       ├── McpRateLimiter.java                     # fixed window per sub, 60/min, Clock-driven
│   │       ├── McpToolCallAdvice.java                  # @Around @McpTool: limit, run, audit line, error mapping
│   │       ├── McpJwtDecoderFactory.java               # lazy decoder with issuer + audience validators
│   │       └── McpAuthenticationEntryPoint.java        # 401 with resource_metadata
│   ├── gamecatalog/rest/controller/
│   │   ├── GameCatalogMcpTools.java                    # NEW: search_games, get_game, list_places, list_sources, get_library_status
│   │   └── model/                                      # NEW result records (GameSearchItem, GameDetail, PlaceSummary, …)
│   └── fna/
│       ├── domain/repository/ArticleRepository.java    # + findByTitleContainingIgnoreCaseOrderByPublishedAtDesc (Pageable, so offset works)
│       ├── rest/controller/
│       │   ├── FnaMcpTools.java                        # NEW: search_articles, get_portfolio, get_wishlist, get_latest_briefing
│       │   └── model/                                  # NEW result records (ArticleItem, PortfolioItem, BriefingView, …)
│       └── service/FnaService.java                     # + article title search
└── src/test/java/dev/jordy/jordylab/
    ├── mcp/                                            # NEW: McpSecurityTest, McpToolCallAdviceTest, McpRateLimiterTest,
    │                                                   #      ProtectedResourceMetadataControllerTest, McpToolSurfaceTest
    ├── gamecatalog/rest/controller/GameCatalogMcpToolsTest.java   # NEW (Testcontainers)
    └── fna/rest/controller/FnaMcpToolsTest.java                   # NEW (Testcontainers)

deploy/
├── compose/Caddyfile                                   # after the cutover (019): + /mcp and /.well-known/oauth-protected-resource;
│                                                       #   the k3s k8s/base/gateway.yaml is not edited
├── keycloak/realm-prod.json                            # + jordylab-mcp-connector client (fresh imports)
└── keycloak/README.md                                  # + dated row for the live kcadm apply

jordylab-be/compose/keycloak-realm-export.json          # + jordylab-mcp-connector client (local dev imports)

AGENTS.md                                               # module table: + mcp; Infrastructure: connector one paragraph
jordylab-be/AGENTS.md                                   # module section for mcp (routes, guardrails, tool rule)
docs/runbook.md                                         # connector setup, rotation, revocation, what to check on failure
```

**Structure Decision**: a new `mcp` module holds the transport, the security chain, discovery and the guardrails. It has
no dependency on a domain module. The tools sit in each domain module's `rest/controller/` as inbound adapters, the same
role as the controllers beside them, so no facade is needed and no internal service is exposed (R8). The package layout
follows AGENTS.md: `rest/controller/model/` for records, `service/` for logic, and no new sub-package beyond what the
layout already names.

## Delivery slices (input to `/speckit-tasks`)

1. **Foundation**: the `mcp` module skeleton, the starter dependency, the STATELESS property, the security chain (401
   with the pointer, 403 for non-admins, 401 for a missing audience), the metadata controller, `ModularityTests` green.
   Spike gate: an MCP client (MCP Inspector, run locally) completes `initialize` and `tools/list` against the stateless
   server (R1). Claude connects from Anthropic's cloud and cannot reach localhost, so Claude's own check runs after the deploy.
2. **Guardrails**: the rate limiter, the advice (limit, audit, error mapping), with the 61st-call test and the audit-line
   test. Spike gate: the advice runs on tool beans invoked by the framework (R7).
3. **Catalog tools**: `GameCatalogMcpTools` (five tools) with Testcontainers tests for filters, unknown facts, hidden
   fields and the 50-item bound.
4. **Finance tools**: `FnaMcpTools` (four tools), the title-search query, and the briefing "no briefing yet" case.
5. **Keycloak and gateway** (after the Compose cutover, I1): the client in both realm files; the `kcadm` apply recorded in the README; the two routes in the Caddyfile;
   the stale rule removed. Spike gate: token has `aud`, `realm_access`, a refresh token and no offline token; rotation
   on (R5).
6. **Surface test and docs**: the exact-tool-list test, the read-only hint test, `AGENTS.md`, `jordylab-be/AGENTS.md`,
   the runbook section.
7. **Operations** (after the merge, not code): the Keycloak apply before the first connection, quickstart steps 1–10
   (steps 4–8 need Claude), the rotation check, and the revocation check with a throwaway admin.

Slices 1–6 are one PR (one spec, one branch). Slice 7 runs after the merge and is recorded in the runbook. Slice 5's
Keycloak apply should happen before the merge, because each merge to `main` deploys (spec 019) and the connector client
must exist before Jordy first connects.

## Spec changes from planning

These follow from the research. They are applied to `spec.md` in the same change.

| # | Where in spec | Change | Why (research) |
|---|---------------|--------|----------------|
| S1 | New FR-021 | The MCP endpoint accepts only tokens issued for the connector (audience `jordylab-mcp`); web-app tokens get 401 | R3: the MCP authorization spec requires servers to validate the audience |
| S2 | User Story 2, acceptance scenario 1 | "articles about it" becomes "recent articles whose title mentions the ticker or company name" | R9: FNA stores no ticker tags, so the match is on the title |
| S3 | Assumptions | A connector sign-in lasts as long as the SSO session (30 days idle, 90 days at most), then Jordy signs in again | R6: refresh tokens are bound to the SSO session (no `offline_access`) |
| S4 | Assumptions | The rate-limit count is kept per running instance; a restart resets it | R7: one instance, in-memory window |

## Risks and mitigations

| # | Risk | Likelihood / impact | Mitigation |
|---|------|---------------------|------------|
| K1 | Claude's handshake with a STATELESS server fails | Low / high | Spike gate in slice 1; fallback is `STREAMABLE` (a property change) |
| K2 | The AOP advice is not applied to tools the framework invokes, so the limit and audit do not run | Medium / high | Spike gate in slice 2 asserts the 61st call is refused and not run; fallback is a wrapper per tool |
| K3 | Refresh rotation fails under parallel calls in one chat | Medium / low | Quickstart step 8; raise "Refresh Token Max Reuse" (a Keycloak setting) |
| K4 | Claude's proactive refresh (60-second tokens) adds a Keycloak call before almost every tool call | Certain / low | Accepted: one user, far below any limit, and the latency budget is 30 s |
| K5 | A connector token also works on `/api` (OI-1) | Certain / medium | Open decision for Jordy (below); the read-only promise rests on the tool list until then |
| K6 | The `WWW-Authenticate` pointer is lost in the gateway | Low / high | Quickstart step 2 is the first check after the deploy; the route keeps the header by default |
| K7 | A deploy restarts the backend mid-conversation | Certain / low | Stateless transport: no session to lose; Claude retries the call |
| K8 | The connector client is missing when the backend deploys | Medium / low | The endpoint is admin-only and returns 401 without the client; apply the Keycloak client first (slice 5) |
| K9 | Title search misses articles that only mention a company in the body | Medium / low | Accepted and stated in the tool description and spec S2 |
| K10 | The 90-day SSO cap forces a fresh sign-in | Certain / low | Stated in the spec (S3) and the runbook |
| K11 | Claude mobile needs a separate connector setup | Unknown / low | Quickstart step 6 verifies it; a documentation change if it does |
| K12 | The stale assetlinks rule is removed while something still needs it | Very low / low | Spec 018 removed the endpoint; the SecurityConfig comment confirms it |

## Open items (decided 2026-11-03: OI-1 option A, OI-2 merge straight to prod, OI-3 title only)

1. **OI-1: connector tokens and `/api`. Decided 2026-11-03: option A.** A connector token carries the admin's rights on `/api` as well, so a leaked
   token could call a write endpoint. Options: (A) accept it for v1, as the browser session already carries the same
   rights, and revisit in a later spec (recommended); (B) require the audience on the `/api` chain too, which touches
   every login path and needs the web client to send the audience.
2. **Keycloak apply before release.** The release follows the Compose cutover (I1), so the `kcadm` step for
   `jordylab-mcp-connector` runs before it. Confirm that, and confirm whether a merge still deploys: Compose decision 4
   says deploys run on release tags only (T2 in the analysis).
3. **Article matching.** Confirm S2: `search_articles` matches titles only, so a company mentioned only in the body is
   not found.

## Complexity Tracking

> Fill ONLY if Constitution Check has violations that must be justified

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| New module `mcp` | Transport, security chain, discovery and guardrails are cross-cutting; they belong to no domain | Putting them in `shared` mixes MCP into the AI/config/utilities module; putting them in one domain makes the others depend on it |
| Two new dependencies (Spring AI MCP starter, AOP runtime) | The MCP server and the per-call advice (limit, audit, outcome) need them | A hand-written JSON-RPC handler drifts from the protocol; a servlet filter cannot see call outcomes |
| A second `SecurityFilterChain` | `/mcp` needs an audience check and a different 401 that the existing deny-by-default chain must not get | Adding the audience to the global decoder would break every web login |
