# 5. Gateway API and TLS

## Why Gateway API, not Ingress

Kubernetes' original `Ingress` resource is being superseded by the **Gateway API**, and separately,
`ingress-nginx` (the most common Ingress controller) is in retirement (best-effort only until
March 2026 — see `research.md` §2). JordyLab uses Gateway API from the start rather than building
on a resource type that's on its way out.

## Gateway

`deploy/k8s/base/gateway.yaml`'s `Gateway` (`jordylab-gateway`) declares two listeners — plain
HTTP on port 80, HTTPS on port 443 — and which implementation serves them: `gatewayClassName:
traefik`. Traefik is k3s's **bundled** ingress (Chapter 3), customised via
`deploy/host/traefik-helmchartconfig.yaml` (a `HelmChartConfig` — Chapter 8 covers what that
resource type actually is) to turn its Gateway API provider on in the first place; it isn't on by
default.

## HTTPRoute

Each `HTTPRoute` in `gateway.yaml` attaches to the Gateway (`parentRefs`) and matches on a path
prefix, forwarding to a backend Service (Chapter 4): `frontend-route` → `/` → the frontend Service,
`backend-route` → `/api` → the backend Service, `keycloak-route` → three narrow prefixes
(`/auth/realms/jordylab`, `/auth/resources`, `/auth/.well-known`) → the Keycloak Service — only the app's own realm, so the
`master` realm (where the Keycloak admin logs in) is not reachable from the internet. Notice there is
**no** `/auth` catch-all route — `/auth/admin` and Keycloak's management port are simply never
referenced by any HTTPRoute, so they're unreachable from outside the cluster no matter what
firewall rules exist (`contracts/http-routing.md`). A separate `http-to-https-redirect` HTTPRoute
handles the port-80 listener, redirecting everything to HTTPS.

## Certificates, the ACME way

`deploy/k8s/cluster/cert-manager-clusterissuer.yaml`'s `ClusterIssuer` tells cert-manager how to
get certificates from Let's Encrypt: via the ACME protocol's **HTTP-01 challenge**. Let's Encrypt
asks "prove you control this domain" by requesting a specific token at
`http://<domain>/.well-known/acme-challenge/<token>` — cert-manager temporarily wires that up
through the Gateway (`solvers[].http01.gatewayHTTPRoute`, pointing back at the same
`jordylab-gateway`'s HTTP listener) to prove it, then Let's Encrypt issues the cert. The `Certificate`
resource beneath the `ClusterIssuer` in that file requests the actual cert and stores it as a
Kubernetes `Secret` (`jordylab-tls`) that the Gateway's HTTPS listener references directly.

Renewal is automatic — cert-manager tracks expiry and re-runs the same challenge well before a
certificate lapses. `docs/runbook.md` §17 covers what to check if it doesn't.

## Hands-on

```
kubectl get gateway,httproute -n jordylab
kubectl get certificate -n jordylab jordylab-tls
kubectl describe certificate -n jordylab jordylab-tls
```
