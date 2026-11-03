# Contract: Public routes

The public site must keep the same addresses it has today. Clients depend on them: the Keycloak redirect URIs, the Android app-links file and the sign-in pages. Caddy in `deploy/compose/Caddyfile` must match this table. Source: the Gateway routes in `deploy/k8s/base/gateway.yaml` (research R2).

The Gateway's `PathPrefix` matches whole path segments: `/api` matches `/api` and `/api/...`, but not `/apis`. Caddy's `path` matcher is exact unless it ends in `*`, so each prefix row needs two Caddy patterns: the bare path and the path with `/*`.

| Path | Caddy matcher | Target service | Rewrite | Note |
|------|---------------|----------------|---------|------|
| `/api` | `path /api /api/*` | `backend:8080` | none | the application API |
| `/.well-known/assetlinks.json` | `path /.well-known/assetlinks.json` | `backend:8080` | none | Android app-links file (the backend serves it) |
| `/auth/realms/jordylab` | `path /auth/realms/jordylab /auth/realms/jordylab/*` | `keycloak:8080` | none | sign-in, token and discovery endpoints |
| `/auth/resources` | `path /auth/resources /auth/resources/*` | `keycloak:8080` | none | Keycloak static resources |
| `/auth/.well-known` | `path /auth/.well-known /auth/.well-known/*` | `keycloak:8080` | none | well-known discovery |
| `/ntfy` | `handle_path /ntfy` and `handle_path /ntfy/*` | `ntfy:80` | prefix removed (`/ntfy/x` → `/x`, `/ntfy` → `/`) | notification endpoint |
| everything else | no matcher (the default route) | `frontend:8080` | none | the Angular application |

**Rules**
- `/auth/admin` is not routed to Keycloak. It falls through to the frontend, as it does today (the Gateway's `/` rule). Do not add a rule for it; the admin console stays reachable only through a port-forward.
- Plain HTTP (port 80) redirects to HTTPS for every path. The certificate challenge path stays reachable on port 80 for renewal.
- `/apis`, `/auth/realms/jordylabX` and similar near-misses go to the frontend, not to the target service. The quickstart checks this.

**Checks** (quickstart step 4)
- Each row answers with the target service's response, not with the frontend's `index.html`, except the frontend row.
- `https://jordylab.be/apis` and `https://jordylab.be/auth/realms/jordylabX` answer with the frontend, not the backend or Keycloak.
- `https://jordylab.be/.well-known/assetlinks.json` returns the same body as before the move.
