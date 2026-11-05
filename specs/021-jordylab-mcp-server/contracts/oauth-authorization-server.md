# Contract: the connector's OAuth 2.1 authorization server

The backend runs Spring Authorization Server so Claude can sign in with the admin's JordyLab account. This replaces the
Keycloak client the feature first planned (spec 019 removed Keycloak). The server is part of the `mcp` module and stores
its authorizations in the `mcp` schema.

## Endpoints (all on the public origin)

| Path | Purpose |
|---|---|
| `/oauth2/authorize` | Authorization code + PKCE. Unauthenticated users are sent to `/login?returnUrl=<the exact request>` |
| `/oauth2/token` | Form-encoded code exchange and refresh |
| `/oauth2/jwks` | The signing keys the `/mcp` chain validates against |
| `/.well-known/oauth-authorization-server` | RFC 8414 metadata (issuer, endpoints, `S256`, grant types, scopes) |

The issuer is `jordylab.mcp.public-base-url` (`https://jordylab.be`), the same value the protected resource metadata uses.

## Registered client

| Setting | Value |
|---|---|
| `clientId` | `jordylab-mcp-connector` (the identifier Jordy enters in Claude) |
| Authentication | public, no secret (`ClientAuthenticationMethod.NONE`) |
| Grants | `authorization_code`, `refresh_token` |
| PKCE | required, `S256` only |
| Redirect URI | `https://claude.ai/api/mcp/auth_callback` (the only one) |
| Consent | not required (one client, one admin) |
| Scopes | `jordylab.mcp` |
| Refresh | rotation on (`reuseRefreshTokens(false)`), 30 days; issued by the connector's own generator, because the library refuses refresh tokens to public clients on the code grant |

The client is defined in configuration, not in a table, because there is exactly one.

## Tokens

Access token: a JWT signed by the server, `10` minutes.

| Claim | Value |
|---|---|
| `iss` | the public base URL |
| `sub` | the account's UUID (the same id the owner columns hold) |
| `aud` | `jordylab-mcp` |
| `roles` | the account's role key (`admin`); guests are refused at the `/mcp` chain |
| `scope` | `jordylab.mcp` |

The `/mcp` chain validates the signature (JWKS), the issuer, the audience `jordylab-mcp`, and the role; then every tool call
looks the `sub` up through `UserAccountLookup` and requires the account to exist with role `admin` (FR-004).

Refresh token: 30 days, rotated on every use, stored with the authorization in the `mcp` schema so a deploy does not sign
Claude out.

The refresh request authenticates with `client_id` only: the refresh token is the credential, and the library's public-client
converter only covers the PKCE code request. The connector adds a converter and provider for that grant
(`McpRefreshClientAuthenticationConverter`, `McpRefreshClientAuthenticationProvider`); the standard client registration
check still applies.

## Login bridge

1. Claude opens `/oauth2/authorize?…` without a session.
2. The entry point answers `302` to `/login?returnUrl=<the exact authorization request>`.
3. The SPA logs in (`POST /api/identity/auth/login`) and navigates back to the `returnUrl`.
4. The session cookie authenticates the authorization request; the server issues the code and redirects to Claude with
   `?code=…&state=…`.
5. Claude exchanges the code at `/oauth2/token` with its PKCE verifier.

A user without the `admin` role may sign in, but the authorization request is refused with `access_denied`, and the `/mcp`
chain refuses any token without the role.

## Revocation

There is nothing to disconnect in Claude. Disabling the account or removing the admin role makes the next tool call fail,
whatever the token's remaining lifetime (the per-call account check).

## Verification

The integration test drives the whole flow (session sign-in → authorize → code → token) and asserts the audience, `sub`,
`roles`, the token lifetime and that a guest is refused. The live checks are quickstart steps 3 and 4.
