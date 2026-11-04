# Contract: MCP endpoint and discovery

Public address: `https://jordylab.be/mcp` (the value Jordy enters in Claude). Transport: MCP Streamable HTTP in
stateless mode (research R1). Protocol: MCP 2025-06-18 or later, as negotiated by the client.

## 1. Discovery (unauthenticated)

### 1a. Protected resource metadata

`GET https://jordylab.be/.well-known/oauth-protected-resource/mcp` (also served at
`/.well-known/oauth-protected-resource`)

```json
{
  "resource": "https://jordylab.be/mcp",
  "authorization_servers": ["https://jordylab.be"],
  "scopes_supported": ["openid"],
  "bearer_methods_supported": ["header"],
  "resource_name": "JordyLab"
}
```

Rules: `resource` equals the URL the user enters, character for character (Claude's requirement). The first and only
entry in `authorization_servers` is the backend's own issuer (`https://jordylab.be`), which must match the `iss` claim of
the tokens exactly. Status `200`, `Content-Type: application/json`, no authentication.

### 1b. Authorization server metadata

Served by the backend at `https://jordylab.be/.well-known/oauth-authorization-server` (Spring Authorization Server). It
advertises the issuer, the authorize and token endpoints, `code_challenge_methods_supported` containing `S256`, the
`authorization_code` and `refresh_token` grants, and the `jordylab.mcp` scope. Checked in quickstart step 3.

## 2. The MCP endpoint

### 2a. Unauthenticated or wrong audience

Any request to `/mcp` without a valid bearer token returns:

```http
HTTP/1.1 401 Unauthorized
WWW-Authenticate: Bearer resource_metadata="https://jordylab.be/.well-known/oauth-protected-resource/mcp"
```

The same `401` is returned for a valid token that lacks the audience `jordylab-mcp` (FR-021). The `401` is required
for Claude to start sign-in (Claude ignores the header on any other status).

### 2b. Authenticated but not admin

`403 Forbidden`, body without any data. Covers guests, a pending or revoked account, and the scan client's role (FR-003).

### 2c. Admin with the connector audience

`200` for JSON-RPC requests (`initialize`, `tools/list`, `tools/call`), as the stateless transport defines. Each
`tools/call` goes through the guardrails in `research.md` R7, in this order:

1. Rate limit: over 60 calls in the current minute returns a **tool error result** (`isError: true`) with the text
   `Too many requests in one minute. Try again shortly.` The tool does not run.
2. Tool runs.
3. Failure returns a tool error result with `The tool could not read this data right now.` No stack trace, SQL, host
   name or credential.

### 2d. Capabilities

Only `tools` is offered. Resources, prompts, completions and logging are switched off (`spring.ai.mcp.server.*`
capability properties; exact keys confirmed in the spike). Every tool is read-only (`contracts/tools.md`).

## 3. Bounds

- Maximum 50 items per list result (FR-009).
- Maximum 60 `tools/call` requests per admin per minute (FR-020).
- Discovery and token endpoints answer within Claude's 10-second and 30-second windows. Keycloak runs in the same
  cluster, so no extra budget is needed.

## 4. Not exposed

No route in this contract accepts a write. Paths that stay closed to the connector, even though the admin could call
them in the web app: every `POST`, `PUT`, `PATCH` and `DELETE`; `POST /api/gamecatalog/libbot/ask` and its
conversation endpoints; `POST /api/fna/briefing/trigger`; refresh runs; scans and the ingest endpoints; `/api/settings/**`;
`/api/feedback/inbox/**`; and anything under `/api/gamecatalog/games/*/mark`. The tool list is the only way in (FR-011,
FR-012).
