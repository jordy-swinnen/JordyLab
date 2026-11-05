# Research: JordyLab MCP Server (read-only)

Phase 0 decisions for `plan.md`. Each entry gives the decision, why, and what was rejected. Sources: the Spring AI MCP
server reference (the 2.0.x docs, the same starter line as the pinned 2.0.1), Claude's connector authentication docs
(`claude.com/docs/connectors/building/authentication`), Spring Authorization Server (7.1.x) and Spring Security 7.1 reference, and the repository as
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
- **Confirmed 2026-11-04** on the stateless server (against a throwaway Keycloak test realm, before spec 019 landed): `initialize` returns the server name
  `jordylab`, and `tools/list` returns the nine tools. `McpConnectorIntegrationTest` holds both. MCP Inspector against the
  dev stack (T024) and Claude's own check after the deploy (Phase 8) are still open.

## R2. Sign-in: Spring Authorization Server inside the backend

- **Fact (2026-11-04)**: `main` moved to spec 019. The backend's `identity` module owns sessions (Spring Session JDBC),
  passwords and passkeys, the `admin`/`guest` roles and per-app grants. There is no Keycloak and no OAuth2 resource
  server, so the Keycloak design this feature first planned (a realm client, the Keycloak issuer and 60-second JWTs) no
  longer has a foundation.
- **Decision**: the backend runs its own OAuth 2.1 authorization server with Spring Authorization Server (the Boot
  `spring-boot-starter-oauth2-authorization-server` starter). One pre-registered **public** client
  `jordylab-mcp-connector`, authorization code with PKCE `S256`, the Claude callback plus the Claude Code and OpenCode
  loopback callbacks, consent off, refresh tokens on. The client identifier is what Jordy enters in Claude; no secret exists.
- **Why the backend and not a separate identity service**: one deployable, the existing session login is the human
  sign-in, and the account model the tools already use is the same one the connector authenticates against.
- **Rejected**: keeping Keycloak (removed by spec 019); a static request-header token (a long-lived shared credential,
  and Claude's static-credential support is limited); a hosted identity provider (a new dependency and a new secret).

## R3. Tokens: audience, roles and the per-call account check

- **Decision**: access tokens are JWTs signed by the backend's authorization server, with `aud` containing
  `jordylab-mcp`, `sub` equal to the account's UUID, and a `roles` claim (the role key, `admin`). The `/mcp` chain
  validates the issuer, the audience and the role.
- **Revocation**: a JWT cannot be recalled, so every tool call also looks the `sub` up through the identity module's
  `UserAccountLookup.find(UUID)` and requires the account to exist with role `admin`. That is what makes FR-004 true
  (the next call is refused after a role change or a disabled account) without introspection.
- **Why the role check on every call**: the alternative is a very short token plus trust in the refresh grant, which a
  JWT cannot enforce. One indexed lookup per call is cheaper than the risk.
- **Rejected**: opaque tokens with introspection (extra round trip and a second HTTP hop inside the server); a 60-second
  token with a 60-second revocation window (weaker than the spec's "next call").

## R4. Discovery: protected resource metadata and the authorization server metadata

- **Claude's requirements** (connector authentication docs): a `401` whose `WWW-Authenticate` header carries
  `resource_metadata`; the metadata's `resource` equal to the URL the user entered; `authorization_servers` with the
  issuer first; the authorization server's own metadata reachable; PKCE `S256`; a form-encoded token endpoint.
- **Decision**: keep the protected resource metadata document (RFC 9728) that is already built, and point its
  `authorization_servers` at the backend's own issuer, `${publicBaseUrl}`. Spring Authorization Server serves
  `/.well-known/oauth-authorization-server` (RFC 8414) and the JWKS at `/oauth2/jwks`; the token endpoint is
  `/oauth2/token`, the authorization endpoint `/oauth2/authorize`.
- **Routes**: Caddy forwards `/oauth2/*`, `/.well-known/oauth-authorization-server*`, `/mcp` and
  `/.well-known/oauth-protected-resource*` to the backend. Everything else keeps its current route.
- **Rejected**: Keycloak's `/.well-known` (removed); an external authorization server (new hosting, new trust setup).

## R5. The login bridge: the existing session login

- **Fact**: the SPA login is `/login`; it posts to `POST /api/identity/auth/login` and the login component honours a
  `returnUrl` query parameter (`safeReturnUrl`, same-origin only). But it returns with `router.navigateByUrl`, so a backend
  path such as `/oauth2/authorize` names no Angular route and never reaches the server (found 2026-11-04).
- **Decision (2026-11-04, owner)**: the login component loads a return path that starts with `/oauth2/` with a full page load
  (`window.location.assign`), and keeps the router for every other path. The authorization endpoint's authentication entry
  point sends an unauthenticated user to `/login?returnUrl=<the request's path and query>`; after login the browser loads the
  authorization request, the session cookie authenticates it, and Spring Authorization Server issues the code. Consent is off
  for the single client, so there is no second screen.
- **Tests**: an authorization-code flow test signs in against the identity test controller, follows the redirect, and
  exchanges the code for a token.
- **Rejected**: a separate login page inside the backend (a second login UI); the device grant (Claude's remote connectors
  use the authorization code flow).

## R6. Token lifetimes, refresh and storage

- **Decision**: access token 10 minutes, refresh token 30 days with rotation (`reuseRefreshTokens(false)`), authorizations
  and consents in the `mcp` schema through Spring Authorization Server's JDBC services, so a deploy does not sign Claude
  out. The registered client is defined in configuration (one client), not in a table.
- **Revocation** is covered by R3's per-call account check; the refresh token adds no extra window because the account
  check happens on every call, not only at refresh.
- **Library gaps found while building (2026-11-05)**: Spring Authorization Server's refresh generator returns no token
  when a public client uses the authorization-code grant, and its public-client authentication only matches the PKCE code
  request, so the refresh request gets no client authentication. The module fills both narrowly:
  `PublicClientRefreshTokenGenerator` issues the connector's rotating refresh token, and
  `McpRefreshClientAuthenticationConverter` plus `McpRefreshClientAuthenticationProvider` authenticate the
  `refresh_token` grant with `client_id` only. `McpAuthorizationServerTest` proves issuance, rotation and the refusal of a
  reused token. This is a deliberate exception to "use the library default"; a library upgrade must keep the flow test
  green.
- **Rejected**: in-memory authorizations (lost at every deploy); a 1-hour access token (a longer brute-force window if a
  token leaks, for no benefit since the account is checked per call anyway).

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
- **Correction (2026-11-04)**: the annotation layer shows an exception's message and then its root cause's message. With no
  cause the safe text appears twice, so `McpToolException` carries an empty cause and the text appears once, followed by
  one line separator.
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

## R9b. Spring Security's own metadata document (found while building)

- **Decision**: the protected resource metadata is Spring Security's built-in document, configured in `McpConfiguration`
  (resource, authorization server, `openid`, `header`, `JordyLab`, and
  `tlsClientCertificateBoundAccessTokens(false)`), not a controller of our own. It answers both well-known paths, and a
  second handler is shadowed by it, so an own controller would never be reached. The integration test asserts the exact
  document. The decoder is built on first use: `NimbusJwtDecoder.withIssuerLocation(...).build()` reads the issuer's
  configuration, and an eager build stopped the application from starting while the issuer was unreachable. With the
  authorization server inside the backend the decoder reads the local JWKS (`/oauth2/jwks`), so the first use is local.

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
- Proven by the tests rather than by the gates: the stateless handshake (R1), the advice on framework-invoked tools (R7),
  the token's audience and roles (R5). Still open: the dev-stack gates T024 and T025, the live client (T057), Caddy
  validation on the host (the file validates with `caddy validate`), and Claude's own connection after the deploy.
- Confirmed by the spike rather than assumed: the STATELESS handshake with Claude (R1), AOP on MCP-invoked tools (R7),
  the authorization server version and starter (R2, R11), the session login bridge (R5), token lifetimes and storage (R6),
  and the `WWW-Authenticate` header through Caddy (R10).
