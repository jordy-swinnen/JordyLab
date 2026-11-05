# Implementation Plan: JordyLab MCP Server (read-only)

**Branch**: `021-jordylab-mcp-server` (git branch `jordyswinnen/jor-79-add-a-jordylab-mcp-server-so-admins-can-ask-claude-about`) | **Date**: 2026-11-03 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/021-jordylab-mcp-server/spec.md`

**Note**: This plan was produced by `/speckit-plan`. Its clarifications were answered in the spec (five answers, Q1 to
Q5). The decisions are in [research.md](research.md) (R1–R12).

## Summary
An admin connects Claude Desktop and Claude mobile to JordyLab through `https://jordylab.be/mcp`. Claude signs in with the
admin's JordyLab account through an OAuth 2.1 authorization server that the backend runs, and then calls nine read-only
tools: five for the game catalog and four for finance. Every call is admin-only, limited to 60 a minute, returns at most
50 items, and leaves one audit line in the backend log. The connector never writes, and it never calls an AI model.

Technical approach, in one line each:
- **Transport**: Spring AI Streamable-HTTP in stateless mode, so a deploy's restart does not drop a connected client (R1).
- **Sign-in**: Spring Authorization Server inside the backend, one pre-registered public client with PKCE S256, and the
  existing session login as the bridge (R2, R5).
- **Tokens**: a short-lived JWT for the connector (`aud` `jordylab-mcp`, the user's id and role), with the account
  re-checked against the identity module on every call (R3).
- **Discovery**: protected resource metadata (RFC 9728) plus the authorization server's own metadata (RFC 8414), both
  served by the backend (R4, R10).
- **Guardrails**: an AOP advice on each tool gives the rate limit, the audit line and the error mapping (R7).
- **Placement**: the `mcp` module owns the auth server, the `/mcp` chain and the guardrails; the tools stay in the domain
  modules (R8). One Flyway migration in a new `mcp` schema.

## Technical Context
**Language/Version**: Java 25 (backend); YAML (Caddy); SQL (Flyway).

**Primary Dependencies**: Spring Boot 4.1.1 and Spring Security 7.1.1 (existing), with
`spring-boot-starter-oauth2-authorization-server` (Spring Authorization Server 7.1.x, version from the Boot BOM); Spring AI
2.0.1 via the BOM with `spring-ai-starter-mcp-server-webmvc`; `aspectjrt` for the tool advice; Spring Modulith 2.1.1; the
`identity` module from spec 019 (`AuthenticatedUser`, `CurrentUserResolver`, `UserAccountLookup`).

**Storage**: one new schema, `mcp`, with one Flyway migration holding Spring Authorization Server's JDBC tables
(authorization, authorization consent, and the registered client if stored in JDBC). The rate limit stays in process
memory (R7).

**Testing**: JUnit 5, AssertJ (`assertSoftly` for multi-assertion tests), Mockito without `any()`, MockMvc with
`@Language("JSON")`, the identity module's test sign-in, Testcontainers Postgres for the query tests, `ModularityTests`.
A dedicated test drives the authorization-code flow (session login → authorize → code → token with the audience and
role). Live proof: `quickstart.md` steps 1–10, with Claude Desktop and Claude mobile.

**Target Platform**: single host on OVH VPS-2, public at `https://jordylab.be`, Docker Compose behind Caddy since
`specs/020-compose-deployment/`. Claude calls it from Anthropic's cloud, so it must be public HTTPS.

**Project Type**: backend feature (the `mcp` module with an authorization server and tool adapters), plus Caddy routes,
one Flyway migration and docs. One small frontend change: the login page returns to a `/oauth2/` path with a full page load
instead of the Angular router (T074, decided 2026-11-04). Everything else in the web app is unchanged.

**Performance Goals**: 95% of questions answered within 10 s (SC-002). A tool call adds one database read and one account
lookup, and no model call.

**Constraints**:
- Read-only: no mutating route, no AI call, no notification (FR-011, FR-012).
- Admin only, on the audience `jordylab-mcp`, re-checked on every call (FR-003, FR-004, FR-021).
- At most 50 items per call; at most 60 calls per admin per minute (FR-009, FR-020).
- No secret, no hidden personal field, no argument or result in logs (FR-014, FR-015, FR-016).
- Access token 10 minutes; refresh token 30 days with rotation; the session login is the only human sign-in.
- The authorization endpoint, the token endpoint, the JWKS and both well-known documents must be reachable from the
  internet.

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
├── build.gradle.kts                                    # + spring-ai-starter-mcp-server-webmvc, spring-boot-starter-oauth2-authorization-server, aspectjrt
├── src/main/resources/
│   ├── application.yaml                                # spring.ai.mcp.server.* (STATELESS, tools only); jordylab.mcp.*
│   └── db/migration/V20261104001__mcp_oauth_tables.sql # NEW: schema mcp + SAS authorization, consent and client tables
├── src/main/java/dev/jordy/jordylab/
│   ├── mcp/                                            # NEW module
│   │   ├── McpAuthorizationServerConfiguration.java    # SAS chains, client, JWK, token customizer, refresh helpers, schema wrapper
│   │   ├── McpConfiguration.java                       # /mcp chain: AS JWKS decoder, audience, admin, 401 pointer
│   │   ├── McpProperties.java                          # publicBaseUrl, issuer, audience, limits
│   │   ├── McpToolPage.java                            # public envelope (items, returned, totalMatching, hasMore)
│   │   └── service/
│   │       ├── McpAccountStatusValidator.java          # per-call admin re-check through UserAccountLookup
│   │       ├── McpRateLimiter.java                     # fixed window per sub, 60/min, Clock-driven
│   │       ├── McpToolCallAdvice.java                  # @Around @McpTool: limit, run, audit line, error mapping
│   │       └── McpAuthenticationEntryPoint.java        # 401 with resource_metadata
│   ├── gamecatalog/rest/controller/
│   │   ├── GameCatalogMcpTools.java                    # five tools (unchanged)
│   │   └── model/                                      # result records (unchanged)
│   └── fna/
│       ├── domain/repository/ArticleRepository.java    # + paged title search (unchanged)
│       ├── rest/controller/
│       │   ├── FnaMcpTools.java                        # four tools (unchanged)
│       │   └── model/                                  # result records (unchanged)
│       └── service/FnaService.java                     # + article title search (unchanged)
└── src/test/java/dev/jordy/jordylab/
    ├── mcp/                                            # McpAuthorizationServerTest, McpSecurityTest, McpToolCallAdviceTest,
    │                                                   # McpRateLimiterTest, McpToolSurfaceTest, McpJwtTestBuilder
    ├── gamecatalog/rest/controller/GameCatalogMcpToolsTest.java
    └── fna/rest/controller/FnaMcpToolsTest.java

deploy/compose/Caddyfile                                # + /oauth2/*, /.well-known/oauth-authorization-server*,
                                                        #   /mcp, /.well-known/oauth-protected-resource*

jordylab-fe/libs/shared/auth/src/lib/login.component.ts  # + full-page return for /oauth2/ paths (T074); spec beside it

AGENTS.md                                               # module table: + mcp
jordylab-be/AGENTS.md                                   # mcp section: auth server, /mcp chain, guardrails
docs/runbook.md                                         # connector setup and troubleshooting
```

**Structure Decision**: a new `mcp` module holds the transport, the security chain, discovery and the guardrails. It has
no dependency on a domain module. The tools sit in each domain module's `rest/controller/` as inbound adapters, the same
role as the controllers beside them, so no facade is needed and no internal service is exposed (R8). The package layout
follows AGENTS.md: `rest/controller/model/` for records, `service/` for logic, and no new sub-package beyond what the
layout already names.

## Delivery slices (input to `/speckit-tasks`)
1. **Authorization server** (new): `McpAuthorizationServerConfiguration` with Spring Authorization Server, one registered
   public client (`jordylab-mcp-connector`, authorization_code + refresh_token, PKCE S256, the Claude callback plus the
   Claude Code/OpenCode loopback callbacks, consent off), a JWK source, the token customizer (`aud` `jordylab-mcp`, `sub` =
   account id, `roles`), the authorization
   endpoint's entry point to `/login?returnUrl=…`, and the Flyway migration for the `mcp` schema. Gate: an
   authorization-code flow test reaches a token with the audience and `admin`.
2. **The `/mcp` chain on AS tokens**: replace the issuer-based decoder with the AS JWKS, keep the audience validator, map
   the `roles` claim to authorities, add the per-call account check through `UserAccountLookup`, keep the 401 pointer and
   the protected resource metadata. Tests: 401, wrong audience, guest, revoked account, the nine tools.
3. **Guardrails** (already built): rate limiter, advice, audit line and error mapping; tests stay.
4. **Tools** (already built): the five catalog and four finance tools, their records and tests; unchanged.
5. **Deployment and docs**: Caddy routes for `/oauth2/*`, `/.well-known/oauth-authorization-server` (and its path-suffixed
   form), `/mcp` and `/.well-known/oauth-protected-resource*`; remove the Keycloak client changes from the realm files and
   this spec; docs (`AGENTS.md`, `jordylab-be/AGENTS.md`, runbook, Keycloak README row reverted).
6. **Operations** (after the merge): the public discovery checks, then Claude Desktop and mobile, the ten questions, the
   read-only probe, parallel questions, the audit lines, revocation.

## Spec changes from planning

These follow from the research. They are applied to `spec.md` in the same change.

| # | Where in spec | Change | Why (research) |
|---|---------------|--------|----------------|
| S1 | New FR-021 | The MCP endpoint accepts only tokens issued for the connector (audience `jordylab-mcp`); web-app tokens get 401 | R3: the MCP authorization spec requires servers to validate the audience |
| S2 | User Story 2, acceptance scenario 1 | "articles about it" becomes "recent articles whose title mentions the ticker or company name" | R9: FNA stores no ticker tags, so the match is on the title |
| S3 | Assumptions | A connector sign-in lasts as long as the SSO session (30 days idle, 90 days at most), then Jordy signs in again | R6: refresh tokens are bound to the SSO session (no `offline_access`) |
| S4 | Assumptions | The rate-limit count is kept per running instance; a restart resets it | R7: one instance, in-memory window |
| S5 | FR-002, FR-004, assumptions, dependencies | Sign-in moves from a Keycloak client to the backend's own OAuth 2.1 authorization server; every call re-checks the account | Spec 019 removed Keycloak; R2, R13–R15 |
| S6 | Keycloak client contract and realm changes | Removed; replaced by `contracts/oauth-authorization-server.md` and `contracts/oauth-client.md` | Same |
| S7 | Quickstart steps 3–4 | The Keycloak checks become the AS discovery document and the login bridge | Same |

## Risks and mitigations
| # | Risk | Likelihood / impact | Mitigation |
|---|------|---------------------|------------|
| K1 | Claude's handshake with a STATELESS server fails | Low / high | Gate in slice 2 with a real MCP client; fallback is `STREAMABLE` (a property change) |
| K2 | The AOP advice is not applied to tools the framework invokes, so the limit and audit do not run | Medium / high | Gate in slice 2 asserts the 61st call is refused and not run; fallback is a wrapper per tool |
| K3 | The authorize request loses the login session and loops back to `/login` | Medium / high | Same origin; the login page loads `/oauth2/` return paths with a full page load (T074, its spec covers it); quickstart step 4 catches it; the entry point keeps the exact request |
| K4 | Spring Authorization Server's tables are missing on first boot | Low / high | Flyway owns them and runs before the AS serves a request; the migration is in the first slice |
| K5 | An AS token reaches more than `/mcp` | Low / medium | The `/api` chain is session-only; the AS token is accepted by the `/mcp` chain alone, and only there |
| K6 | The `WWW-Authenticate` pointer is lost in the proxy | Low / high | Quickstart step 2 is the first check after the deploy; Caddy keeps the header by default |
| K7 | A deploy restarts the backend mid-conversation | Certain / low | Stateless transport and a stateless access token; Claude retries the call |
| K8 | Spring Authorization Server 7.1.x is incompatible with the pinned Security 7.1.1 | Low / high | The Boot BOM manages both; the first slice compiles and runs the flow test before anything else |
| K9 | Refresh tokens are lost across a deploy | Low / medium | Authorizations live in the `mcp` schema, not in memory; the flow test crosses a context restart |
| K10 | The token issuer does not match the public URL Claude sees | Medium / medium | The issuer is `publicBaseUrl`; the discovery document and the decoder use the same property; quickstart step 3 |
| K11 | A removed admin keeps access until the token expires | Low / medium | Every call re-checks the account and role against the identity module (FR-004) |
| K12 | Claude mobile needs its own connector setup | Unknown / low | Quickstart step 6; a documentation change only |
| K13 | A Spring Authorization Server upgrade changes the public-client refresh behaviour the connector patches around | Low / high | `McpAuthorizationServerTest` covers issuance, rotation and reuse; the two helper classes are documented in R6 and `jordylab-be/AGENTS.md` |

## Open items (decided 2026-11-03: OI-1 option A, OI-2 merge straight to prod, OI-3 title only)
All earlier answers hold, with the authentication changed on 2026-11-04:

1. **OI-1 is closed by design**: connector tokens are issued by the backend's own authorization server and accepted only by
   the `/mcp` chain; `/api` remains session-only, so the residual risk from the Keycloak design is gone.
2. **OI-2 and OI-3** stay as recorded (merge to `main` deploys; article search matches titles).
3. **Resolved 2026-11-04**: the auth server is Spring Authorization Server inside the backend, not Keycloak, and every call
   re-checks the account (FR-004).

## Complexity Tracking
> Fill ONLY if Constitution Check has violations that must be justified

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| A new module `mcp` | The auth server, the `/mcp` chain, discovery and the guardrails are cross-cutting; the tools live in the domain modules | Putting them in `shared` mixes MCP into config and utilities; one domain module would make the others depend on it |
| An authorization server in the backend | Claude's remote connectors need OAuth 2.1; the identity module has sessions only | A static request-header token is a weaker, long-lived credential and depends on Claude's limited static-credential support |
| A new `mcp` schema with SAS tables | Refresh tokens and consents must survive a deploy | In-memory authorizations log Claude out on every deploy |
| Two new dependencies (Spring AI MCP starter, SAS starter) plus `aspectjrt` | The MCP server, the OAuth flow and the per-call advice | A hand-written JSON-RPC handler and a hand-written OAuth server both drift from their specifications |
| A second `SecurityFilterChain` | `/mcp` needs an audience check and a different 401 than the session chain | Adding token auth to the session chain would change the web app's login |

## Notes from the automated review (non-blocking, follow-ups)

The PR review found no blocking issue. Three minor points, deferred rather than widening this PR:

1. `McpRateLimiter` never evicts expired windows. Bounded by the number of admins and harmless today; evict on read.
2. `McpJwtDecoderFactory` mixes `@RequiredArgsConstructor` with a package-private `@Value` field. Taking the issuer through the
   constructor or through `McpProperties` would be more consistent.
3. `McpToolException` carries an empty cause to stop the MCP annotation layer repeating the message. Documented in the class,
   but check it again on a Spring AI upgrade (the annotation layer's error text is the reason).

Also from that review: a Linear key alone in the PR title may close JOR-79 on merge, while T060 and T062 to T068 are still
open. Verify the ticket after the merge, or link the PR as "Part of".

