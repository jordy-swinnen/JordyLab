# Contract: Public HTTP Routing

The single external interface this feature exposes to the outside world (browsers, the JordyBox scanner, the 007
mobile app) is the routing table on the one public domain. This is the contract those consumers depend on —
changing a path here is a breaking change for them.

**Source of truth for the implementation**: `deploy/k8s/base/` HTTPRoutes + `deploy/k8s/overlays/prod/`
(Gateway API `Gateway`/`HTTPRoute` resources, Traefik-served, TLS via cert-manager — see `research.md` §2–3).

## Public routes (reachable from the internet)

| Path prefix | Backend | Consumers | Notes |
|---|---|---|---|
| `/` | frontend (Angular SPA via nginx-unprivileged) | Browsers, 007 mobile app (bundled WebView) | HTTP → HTTPS redirect at the Gateway |
| `/api/**` | backend (Spring Boot) | Frontend, 007 mobile app, JordyBox scanner | Same origin as `/` — no CORS needed for the web app (US1 scenario 2) |
| `/auth/realms/jordylab/**`, `/auth/resources/**`, `/auth/.well-known/**` | Keycloak | Frontend login flow, 007 mobile app login, scan client device login | Public Keycloak endpoints of the `jordylab` realm only — `master` is not routed (spec 011 BUG-022, 2026-10-01) |
| `/.well-known/assetlinks.json` | backend | Android (App Links verification, spec 007) | Static/generated file |
| `/ntfy` (or subpath, exact prefix decided in `/speckit-implement`) | self-hosted ntfy | 007 mobile app | Resolves the `/speckit-clarify` ntfy decision (FR-013) |

## Never publicly routed (FR-009)

| Path / port | Reason |
|---|---|
| `/auth/admin/**` | Keycloak admin console — reachable only via `kubectl port-forward` (US6 acceptance scenario, drafts' research §8) |
| Keycloak management port `9000` (metrics, health) | Internal only |
| Backend actuator management endpoints beyond what `/api` needs | Health/readiness probes are cluster-internal, not routed through the Gateway |
| k3s API server, port `6443` | Firewalled; CI reaches it over Tailscale, never through this Gateway (FR-019) |

## Consumer expectations

- **JordyBox scanner**: POSTs to `/api/gamecatalog/ingest/*` exactly as it does against `local` — no URL scheme
  change beyond the base domain (US1 scenario 5).
- **007 mobile app**: needs CORS allowed for its app origin on `/api/**`, plus the `assetlinks.json` and ntfy
  routes above (US1 scenario 6). This contract is the dependency 007 has on 008 being live.
- **Browsers**: get a valid, auto-renewing certificate on every route under the domain (US1 scenario 1; see the
  Certificate entity in `data-model.md`).
