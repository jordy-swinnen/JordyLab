# Quickstart: validating the JordyLab MCP server

Run these after the implementation is merged and deployed. Steps 1–3 need no Claude; steps 4–8 need Claude Desktop and
Claude mobile signed in as the admin. Nothing here writes to the database, and no step creates test data in the live
catalog (root AGENTS.md, Validation Data).

## Prerequisites

- The connector client exists in production (`contracts/keycloak-connector-client.md`, applied with `kcadm`, recorded in
  `deploy/keycloak/README.md`).
- The merge is live: the deploy log names the commit (spec 019).
- `curl` and `jq` on the Mac; a browser for the admin sign-in.

## 1. Discovery document (no authentication)

```bash
curl -s https://jordylab.be/.well-known/oauth-protected-resource/mcp | jq .
```

Expected: `resource` is `https://jordylab.be/mcp`; `authorization_servers` is
`["https://jordylab.be/auth/realms/jordylab"]`; `scopes_supported` is `["openid"]`.

## 2. The 401 pointer (no authentication)

```bash
curl -si -X POST https://jordylab.be/mcp -H 'Content-Type: application/json' -d '{}' | grep -i '^HTTP\|www-authenticate'
```

Expected: `HTTP/2 401` and `www-authenticate: Bearer resource_metadata="https://jordylab.be/.well-known/oauth-protected-resource/mcp"`.
If the header is missing, the public proxy (Caddy, `deploy/compose/Caddyfile`) or the backend strips it: stop and fix the
route (research R10) before anything else.

## 3. Authorization server metadata

```bash
curl -s https://jordylab.be/auth/realms/jordylab/.well-known/openid-configuration | jq '{issuer, code_challenge_methods_supported, token_endpoint}'
```

Expected: `issuer` is `https://jordylab.be/auth/realms/jordylab` (exactly the value in the resource metadata);
`code_challenge_methods_supported` contains `S256`.

## 4. Connect Claude Desktop

1. Claude Desktop → Settings → Connectors → Add custom connector.
2. URL: `https://jordylab.be/mcp`. Advanced settings → OAuth client → "Use your own OAuth client", client ID
   `jordylab-mcp-connector`, secret blank.
3. Sign in with the admin account in the browser window Claude opens.
4. Expected: the connector shows nine tools, each described as read-only. Record any difference in the runbook.

Also check the token shape (optional, with the browser's developer tools on the Keycloak token response): the access
token decodes to `aud` containing `jordylab-mcp` and `realm_access.roles` containing `admin`, and there is a
`refresh_token` but no offline token.

## 5. Answer questions from live data

Ask Claude, in one chat, these ten questions (five catalog, five finance) and compare each with the web app screen named
(SC-005):

| Question | Compare with |
|---|---|
| Which local co-op games can four people play on one couch? | Game Catalog grid with the Local multiplayer filter and minimum 4 |
| Which games do I own through Steam Family only? | Game Catalog grid, source filter "Steam (Family)" |
| Which games have I marked as played and liked? | Game Catalog grid, mark filter |
| Which consoles do I have? | Places / Consoles |
| Tell me about one game I own, and where it lives. | Game detail page for the same game |
| What news is there about a ticker I hold? | Finance News articles, title match |
| What is my portfolio worth today? | Finance → Portfolio (same prices and fetch times) |
| Which portfolio positions have no price yet? | Finance → Portfolio (unpriced rows) |
| What is on my wishlist? | Finance → Wishlist |
| What did the latest briefing conclude? | Finance → Briefing (same generation time) |

Expected: the same titles, values and times as the screens. Any unknown value is reported as unknown, not guessed.

## 6. Connect Claude mobile

Open the Claude app on the phone (mobile data, not the home network). Check that the connector appears and that one
question from step 5 returns the same answer as the desktop. If the connector does not appear on the phone, record
how it was added there. This closes the untested assumption in the spec.

## 7. Read-only check

1. Ask Claude to add a game to the wishlist, mark one as played, refresh the briefing and start a library scan.
2. Expected: Claude reports that the connector is read-only (or that the action is not available through it).
3. Check the web app: the wishlist, marks, briefings and scan state are unchanged. No Linear issue is created. The
   AI call count is unchanged.

## 8. Refresh under parallel questions

In one Claude chat, ask two catalog questions at once, then two wishlist questions. Expected: all four answer. A
failure saying authorization failed means the rotation race in research R6 happened: raise "Refresh Token Max Reuse"
to a small number (a Keycloak setting), re-run, and record the change in the README table.

## 9. Audit lines

```bash
docker compose -f /opt/jordylab/compose/compose.yaml --env-file /opt/jordylab/env/prod.env logs backend --since 30m | grep 'dev.jordy.jordylab.mcp.audit' | tail -20
```

Expected: one line per tool call with `admin=<sub>`, `tool=<name>`, `outcome=` and `durationMs=`. No query text, no
titles, no prices, no e-mail address, no token.

## 10. Revocation (owner-run, with a throwaway admin account)

With a second admin account that exists only for this check, sign in through the connector, then remove its admin role
in Settings → Users. Expected: its next tool call within about a minute is refused, and no data comes back. Restore the
role and delete the account afterwards.

## Automated checks (run before merge, not in production)

```bash
cd jordylab-be && ./gradlew :test --tests "*ModularityTests*"
cd jordylab-be && ./gradlew :test --tests "*Mcp*"
```

The `Mcp*` tests cover: 401 with the pointer; 403 for a guest; 401 for a token without the audience; the tool list is
exactly the nine read-only names; every tool has the read-only hint; the 61st call is refused and not run; the audit
line has the admin, tool and outcome and no arguments; list results never exceed 50; the metadata document has the exact
fields. Testcontainers Postgres provides any catalog or finance data the tests need, as synthetic test fixtures.
