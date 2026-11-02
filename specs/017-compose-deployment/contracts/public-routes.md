# Contract: Public routes

The public site must keep the same addresses it has today. Clients depend on them: the mobile app, the Keycloak redirect URIs, the Android app-links file and the sign-in pages. Caddy in `deploy/compose/Caddyfile` must match this table. Source: the Gateway routes in `deploy/k8s/base/gateway.yaml` (research R2).

| Path | Match | Target service | Rewrite | Note |
|------|-------|----------------|---------|------|
| `/api` | prefix | `backend:8080` | none | the application API |
| `/.well-known/assetlinks.json` | exact | `backend:8080` | none | Android app-links file (the backend serves it) |
| `/auth/realms/jordylab` | prefix | `keycloak:8080` | none | sign-in, token and discovery endpoints |
| `/auth/resources` | prefix | `keycloak:8080` | none | Keycloak static resources |
| `/auth/.well-known` | prefix | `keycloak:8080` | none | well-known discovery |
| `/ntfy` | prefix | `ntfy:80` | prefix removed (`/ntfy/x` → `/x`) | notification endpoint |
| everything else | prefix `/` | `frontend:8080` | none | the Angular application |

**Rules**
- `/auth/admin` is not routed to Keycloak. It falls through to the frontend, as it does today (the Gateway's `/` rule). Do not add a rule for it; the admin console stays reachable only through a port-forward.
- Plain HTTP (port 80) redirects to HTTPS for every path. The certificate challenge path stays reachable on port 80 for renewal.
- The `/api` prefix is matched as a path segment, so `/apis` does not reach the backend (Caddy `path` matcher semantics).

**Checks** (quickstart)
- Each row answers with the target service's response, not with the frontend's `index.html`, except the frontend row.
- `https://jordylab.be/.well-known/assetlinks.json` returns the same body as before the move.
