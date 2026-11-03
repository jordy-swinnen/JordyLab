# Research: JordyLab MCP Server (read-only)

Phase 0 decisions for `plan.md`. Each entry gives the decision, why, and what was rejected. Sources: the Spring AI MCP
server reference (the 2.0.x docs, the same starter line as the pinned 2.0.1), Claude's connector authentication docs
(`claude.com/docs/connectors/building/authentication`), the Keycloak 26.7 MCP authorization guide, and the repository as
it stands on 2026-11-03.

## R1. Transport: Spring AI Streamable-HTTP, stateless

- **Decision**: `org.springframework.ai:spring-ai-starter-mcp-server-webmvc` at the version the BOM pins (2.0.1, the
  version `gradle` already resolves for `spring-ai-bom`). `spring.ai.mcp.server.protocol=STATELESS`, the server type
  stays `SYNC`, tools are `@McpTool` methods found by annotation scanning. The endpoint is the starter's default
  `POST /mcp`.
- **Why STATELESS**: no session is kept on the server. Spec 019 restarts the backend on every merge to `main`, and a
  stateful Streamable session would be lost at each restart. A stateless server keeps no session state, so a connected
  Claude client survives a deploy without signing in again.
- **Why Spring AI**: it is already the backend's AI framework (constitution V), and its starter is the supported MCP
  server for Boot 4.
- **Rejected**: `STREAMABLE` with sessions (lost at each deploy); `SSE` (deprecated since 2.0.0); a hand-written
  JSON-RPC handler (protocol drift); the community MCP Security library (its own docs mark it work in progress).
- **Risk**: Claude's handshake against a STATELESS server is not yet tested. Task T024 confirms `initialize` and `tools/list`
  locally with MCP Inspector. Claude's own check runs after the deploy (Phase 8), because Claude connects from Anthropic's
  cloud and cannot reach localhost. If either check fails, switch to `STREAMABLE` (a property change only).

## R2. Authentication and authorization on `/mcp`

- **Fact**: the starter applies no authentication. Its docs say the endpoint accepts every request unless a security
  layer sits in front of it.
- **Fact**: `SecurityConfig` is deny-by-default with `anyRequest().denyAll()`, and every `/api` rule is listed in it.
- **Decision**: a dedicated `SecurityFilterChain` in the `mcp` module with `@Order(Ordered.HIGHEST_PRECEDENCE)` and a
  `securityMatcher` for `/mcp` and `/.well-known/oauth-protected-resource/**`. It requires `hasRole("admin")` for `/mcp`
  and `permitAll` for the metadata document. Its JWT decoder is a `NimbusJwtDecoder.withIssuerLocation(issuer)` (lazy:
  startup does not wait for Keycloak) with two validators: the default issuer validator and an audience validator for
  `jordylab-mcp` (R3). The 401 response uses a custom entry point that adds `resource_metadata` (R4).
- **Existing chain**: unchanged. It still denies anything it does not list.
- **Rejected**: adding the audience check to the global decoder (web-app tokens carry no connector audience, so the login
  path would break); method security on the tools (the spec and `SecurityConfig` put access in one deny-by-default
  place).

## R3. Audience binding

- **Fact**: the MCP authorization spec says a server MUST validate that a token was issued for it. The mechanism is
  RFC 8707 resource indicators.
- **Fact**: Keycloak 26.7 accepts the `resource` parameter only with the experimental `resource-indicators` feature.
- **Decision**: bind the token with Keycloak's standard **Audience** protocol mapper on the connector client
  (R5), which adds `jordylab-mcp` to the access token's `aud`. The `/mcp` chain rejects any token without that audience
  (401). This needs no experimental feature.
- **Residual risk (OI-1, decided 2026-11-03: accepted for v1)**: other chains do not check `aud`, so a connector token also works on `/api`. It
  carries the admin's own rights there, which is the same as the browser session today. The read-only promise rests on
  the connector's tool list, not on the token.

## R4. Discovery: protected resource metadata and the 401 pointer

- **Claude's requirements** (docs, "Serve discovery metadata"): a `401` with
  `WWW-Authenticate: Bearer resource_metadata="<url>"`; the metadata's `resource` equals the URL the user enters;
  `authorization_servers` lists the issuer first (Claude uses only the first entry); the authorization server's own
  metadata is reachable; PKCE with `S256`; a form-encoded token endpoint; responses within 10 s (discovery) and 30 s
  (refresh).
- **Decision**: a `ProtectedResourceMetadataController` in the `mcp` module serves the RFC 9728 document at
  `/.well-known/oauth-protected-resource/mcp` (the path-suffixed form Claude probes first) and at
  `/.well-known/oauth-protected-resource`. Values:
  - `resource`: `${publicBaseUrl}/mcp` = `https://jordylab.be/mcp`
  - `authorization_servers`: `["https://jordylab.be/auth/realms/jordylab"]`, which is the issuer Keycloak advertises
    behind `KC_HOSTNAME=https://jordylab.be/auth`
  - `scopes_supported`: `["openid"]`
  - `bearer_methods_supported`: `["header"]`
- **Base URL**: the existing `JORDYLAB_APP_BASE_URL` (set to `https://jordylab.be` in the prod overlay). No new variable.
- **Authorization server metadata**: already reachable. The gateway routes `/auth/realms/jordylab`.
- **Gateway**: two routes to the backend, one for `/mcp` and one for `/.well-known/oauth-protected-resource`, see R10.

## R5. The Keycloak connector client

- **Decision**: a client `jordylab-mcp-connector` in the `jordylab` realm:
  - public (no secret), standard flow only; no direct access grants, implicit flow, or service account
  - PKCE required with `S256`
  - redirect URI `https://claude.ai/api/mcp/auth_callback`. Claude's docs say the hosted apps (web, Desktop, mobile,
    Cowork) use exactly this callback
  - no web origins
  - an **Audience** mapper adding `jordylab-mcp` to the access token (R3)
  - the `roles` default scope, so `realm_access.roles` is in the token that `SecurityConfig` maps
  - `offline_access` **not** assigned. Keycloak then issues ordinary refresh tokens bound to the SSO session, so the
    revocation rule (FR-004) and the 90-day SSO cap apply. Claude appends `offline_access` only when the authorization
    server metadata lists it; it is ignored when the client lacks it (verified in the spike)
  - refresh rotation on: "Revoke Refresh Token" on, "Refresh Token Max Reuse" 0 (Claude's docs ask public clients to
    rotate). The exact setting names are confirmed in the admin console during the spike
- **Claude side**: Jordy enters only the client identifier under "Use your own OAuth client" and leaves the secret blank
  (Claude's docs allow this for a public client). This is the choice recorded for Q2.
- **Applying it**: the prod realm is imported once (`deploy/keycloak/README.md`), so the client is created live with
  `kcadm` inside the Compose Keycloak container (`docker compose exec keycloak`), and recorded in that README's change table.
  The README's `kubectl exec` form is for the retired k3s host. The same definition
  goes into `deploy/keycloak/realm-prod.json` and the dev export `jordylab-be/compose/keycloak-realm-export.json`, so a
  fresh import matches.
- **Token lifetime**: the realm's 60-second access token (spec 014) applies unchanged.

## R6. Refresh frequency, rotation and revocation

- **Fact**: access tokens last 60 seconds. Claude refreshes proactively up to five minutes before expiry, so in practice
  it refreshes before almost every tool call. Each refresh is one Keycloak call, which is far below any limit for one
  user.
- **Fact**: each connection (desktop, mobile) signs in separately and holds its own refresh token, so one client's
  rotation does not invalidate the other's.
- **Risk**: two parallel tool calls inside one client may refresh at the same time, and with rotation and reuse 0 the
  second one fails with `invalid_grant`. Mitigation: the spike runs parallel questions in one chat. If it fails, raise
  "Refresh Token Max Reuse" to a small number. This is a Keycloak setting, not a code change.
- **Revocation (FR-004)**: a disabled account cannot refresh; a removed admin role appears in the next refreshed token.
  Either way access stops within about one token lifetime, with no disconnect step in Claude.
- **Cap**: the SSO session lasts 30 days idle and 90 days at most (realm setting). After that Jordy signs in again. This
  becomes an assumption in the spec (S3).

## R7. Tool-call guardrails: rate limit, audit and error mapping

- **Decision**: one Spring AOP `@Around` advice in the `mcp` module matches the `@McpTool` methods. For each call it:
  1. reads the admin's JWT `sub` from the security context (set for the request; the spike confirms the stateless
     transport runs the tool on the request thread)
  2. checks a fixed-window counter per `sub` (60 calls per minute, FR-020). Over the limit it returns a tool error
     ("Too many requests in one minute. Try again shortly.") and does **not** run the tool (SC-012)
  3. runs the tool
  4. writes one INFO line on `dev.jordy.jordylab.mcp.audit`: tool name, `sub`, outcome (`ok`, `rate_limited`, `error`),
     duration. No arguments and no result (FR-016)
  5. maps any exception to the sanitized message "The tool could not read this data right now." and logs the exception
     class only (FR-018)
- **Why AOP**: it sees the real outcome of each call, and it runs before the tool body, so a refused call never runs.
  This needs the AspectJ runtime, a new dependency whose exact starter name is confirmed when the spike resolves
  dependencies.
- **Rejected**: a servlet filter that parses JSON-RPC bodies (it must read and cache request bodies, handle batches, and
  cannot see whether a call succeeded).
- **State**: the counter lives in memory per running instance. There is one instance, and a restart (also a deploy)
  resets it. Accepted; it is noted in the spec's assumptions (S4).
- **Risk**: the advice must apply to beans the MCP framework invokes through the annotation scanner. The spike asserts
  that the 61st call is refused and does not run.

## R8. Module placement and Spring Modulith

- **Decision**: a new module `dev.jordy.jordylab.mcp`:
  - root: `McpConfiguration`, `McpProperties`, `McpToolPage<T>` (the public page envelope, see data-model.md)
  - `rest/controller/`: `ProtectedResourceMetadataController`, plus `model/ProtectedResourceMetadata`
  - `service/`: `McpRateLimiter`, `McpToolCallAdvice`, `McpJwtDecoderFactory`, `McpAuthenticationEntryPoint`
  - the `SecurityFilterChain` bean lives in `McpConfiguration`
- **Tools live in the domain modules** as inbound adapters, the same role as controllers:
  `gamecatalog/rest/controller/GameCatalogMcpTools.java` and `fna/rest/controller/FnaMcpTools.java`, with their result
  records under each `rest/controller/model/`. The package layout does not grow: `rest/controller` is an existing
  sub-package, and tool classes sit beside controllers there.
- **Dependency direction**: domain modules depend on `mcp` (for `McpToolPage` only). `mcp` depends on no domain module.
  No new facade is needed, because the tools call services inside their own module.
- **Rejected**: one `mcp` module that calls the domain modules (needs new facades and cross-module service calls);
  putting MCP in `shared` (`shared` is for cross-module AI, config and utilities).
- **Gate**: `ModularityTests` stays green. The `mcp` root is public API by Modulith convention.

## R9. Data sources and the one new query

- **Reused, no new business logic**:
  - catalog: `GameQueryService.getGames(filter, page, size)` (the same filter as `GET /api/gamecatalog/games`, page size
    capped at 50), the game-detail service behind `GET /games/{id}`, the places and platforms services behind
    `GET /places` and `GET /platforms`, the sources list, and the library-status service behind `GET /library/status`.
    Exact method names are confirmed when each tool is written.
  - finance: `FnaService.getRecentArticles()` (already the newest 50), `getPortfolioPositions()`,
    `getWishlistItems()`, `getLatestBriefing()`.
- **One new query**: a Spring Data derived query on `ArticleRepository`,
  `Page<Article> findByTitleContainingIgnoreCaseOrderByPublishedAtDesc(String title, Pageable pageable)`. It takes a
  `Pageable` so the tool's `offset` works (task T036). FNA stores no ticker tags on articles (`Article` has feed, title, url, hash, full content and
  dates). So `search_articles` matches the title. Full-text search over `fullContent` was rejected: it is large and not
  needed for a 50-item answer. This narrows User Story 2 (spec change S2).
- **Output records are tool-specific**, not the REST DTOs. Each record lists its fields explicitly, so fields the UI
  hides (voter identities, e-mail addresses, grants, source file paths, machine identifiers) never reach a tool. This
  is how FR-014 and FR-015 hold by construction.

## R10. Gateway routes and a stale rule

- **Add** to the public Caddyfile `deploy/compose/Caddyfile`, which the Compose cutover (`specs/020-compose-deployment/`) created and which is live on
  production. The retired k3s `gateway.yaml` is not edited. The `@backend` matcher also lists the stale
  `/.well-known/assetlinks.json`, which is removed. Both routes go to the backend:
  - `PathPrefix /mcp`
  - `PathPrefix /.well-known/oauth-protected-resource` (matches the path-suffixed form too, since PathPrefix is
    segment-based)
- **Verify**: Caddy passes the `WWW-Authenticate` header through unchanged (quickstart step 2).
- **Remove** the stale `Exact /.well-known/assetlinks.json` rule. Spec 018 removed that endpoint, and the
  `SecurityConfig` comment says so. The rule forwards to a path that nothing serves.

## R11. Version pin

- **Decision**: stay on 2.0.1, the version the BOM already pins. Spring AI 2.0.2 and 2.1.0-RC1 exist on Maven Central.
  Upgrading in the same feature would mix two changes. The docs read for R1 are the 2.0.2 reference, which uses the same
  starter names and properties.

## R12. Claude mobile

- **Fact**: Claude's docs say the same connector infrastructure backs claude.ai, Desktop and mobile, and that connectors
  are added in the Claude app's connector settings. Whether a connector added on Desktop with a user-entered client
  identifier is then usable on mobile is not stated.
- **Decision**: the spec's assumption stays marked untested. Quickstart step 6 verifies it on the phone. If mobile needs
  its own setup, it is a documentation change, not a design change (FR-005 requires remote connectors, which both
  surfaces use).

## Open items carried into the plan

- OI-1: decided 2026-11-03, accepted for v1 (R3).
- Confirmed by the spike rather than assumed: the STATELESS handshake with Claude (R1), AOP on MCP-invoked tools (R7),
  the Keycloak setting names and refresh behaviour (R5, R6), and the `WWW-Authenticate` header through Caddy (R10).
