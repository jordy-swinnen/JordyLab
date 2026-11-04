# Contract: Keycloak connector client

The client Jordy enters in Claude (under "Use your own OAuth client") so that Claude can sign in to JordyLab. It is
created once in the `jordylab` realm (FR-002, Q2 answer B). It is public: there is no secret to copy, and the realm does
not accept anonymous client registration for this purpose.

## Client definition

| Setting | Value | Why |
|---|---|---|
| `clientId` | `jordylab-mcp-connector` | The identifier Jordy enters in Claude |
| `publicClient` | `true` | No secret (Q2) |
| `standardFlowEnabled` | `true` | Authorization code flow only |
| `implicitFlowEnabled` | `false` | Not used |
| `directAccessGrantsEnabled` | `false` | No password grant |
| `serviceAccountsEnabled` | `false` | No machine identity |
| `redirectUris` | `["https://claude.ai/api/mcp/auth_callback"]` | The callback Claude's docs give for the hosted apps (web, Desktop, mobile, Cowork) |
| `webOrigins` | `[]` | No browser origin needs CORS |
| PKCE | `pkce.code.challenge.method = S256` | Claude sends S256 on every request (its docs) |
| Refresh token rotation | Revoke Refresh Token: on; Refresh Token Max Reuse: 0 | Claude's docs ask public clients to rotate. Raised only if the spike shows parallel refreshes failing (research R6) |
| Default scopes | `openid`, `profile`, `email`, `roles` | `roles` puts `realm_access.roles` in the token that `SecurityConfig` maps |
| Optional scopes | **not** `offline_access` | Keeps refresh tokens bound to the SSO session, so FR-004 and the 90-day cap apply |

## Protocol mapper

| Name | Type | Setting | Result |
|---|---|---|---|
| `mcp-audience` | Audience | Included Custom Audience = `jordylab-mcp` | Adds `jordylab-mcp` to the access token's `aud`. The `/mcp` chain requires it (FR-021, research R3) |

## Token lifetime

Inherited from the realm: `accessTokenLifespan` = 60 seconds (spec 014). Refresh tokens follow the realm's SSO session
(30 days idle, 90 days at most).

## Applying it

- **Fresh imports**: the definition is added to `deploy/keycloak/realm-prod.json` and to
  `jordylab-be/compose/keycloak-realm-export.json`, so a new realm contains it.
- **Live production** (realm already imported; imports run once): **applied 2026-11-04.** The client and its audience
  mapper were created with `kcadm create clients -f` inside the Compose Keycloak container. `kcadm update` drops client
  attributes, so the PKCE and rotation attributes were written with a full client representation through the admin REST
  API (a short-lived `python:3.12-slim` container on the `jordylab_default` network, then removed). Keycloak added two
  defaults of its own (`backchannel.logout.session.required`, `backchannel.logout.revoke.offline.tokens`); both are now
  in the realm files. The change is recorded in `deploy/keycloak/README.md`.
  Run it before the first Claude connection. It can be run before or after the backend deploy, since the endpoint is
  admin-only either way.
- **Local dev**: the same `kcadm` commands against the Podman Keycloak (`--server http://localhost:8080`, no `/auth`).

## What Jordy enters in Claude

1. Custom connector URL: `https://jordylab.be/mcp`
2. Advanced settings, OAuth client: "Use your own OAuth client", client ID `jordylab-mcp-connector`, secret left blank
3. Sign in with the admin account when Claude asks

## Verification (quickstart)

- The client exists, is public, and has the redirect URI, S256 and the audience mapper.
- A token issued to this client contains `aud` with `jordylab-mcp` and `realm_access.roles` with `admin`, and the token
  response contains a refresh token but no offline token.
- Refreshing twice in a row returns a new refresh token each time (rotation on).
