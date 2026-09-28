# Research: Production Deployment on Self-Managed k3s (OVH VPS)

Date: 2026-09-28. Builds on `specs/_drafts/008-deployment/research.md` (the cost/architecture case for k3s over
OVH MKS, sizing rationale, and the open questions later resolved in `/speckit-clarify`) and
`specs/_drafts/008-deployment/plan-draft.md` (the intended repo layout and phase order). Every version and field
below was checked against live sources on 2026-09-28 by two research passes; items sourced from a search-cache
rather than a direct fetch (because `docs.k3s.io`, `doc.traefik.io`, `cert-manager.io`, `keycloak.org`, `nx.dev` and
`quay.io` were blocked by this session's egress proxy) are marked **(cache)** and should get one direct doc check
before the corresponding manifests are written in `/speckit-implement`.

## 1. Cluster runtime: k3s

**Decision**: Pin k3s to the current stable channel release, `v1.37.0+k3s1` (bundles Kubernetes v1.37.0), installed
via the official install script with `INSTALL_K3S_VERSION` pinned (never track `stable` unpinned, so upgrades are a
deliberate runbook step).

**Rationale**: Matches `specs/_drafts/008-deployment/research.md`'s decision to self-host k3s instead of OVH MKS
(≈€8.50–13/mo vs ≈€40–55/mo — see that file for the full cost breakdown, unchanged by this plan). Pinning avoids an
unplanned control-plane upgrade during a routine `k3s` restart.

**Alternatives considered**: Tracking the `latest` channel — rejected, too volatile for a single-node cluster with
no staging to catch a bad release first.

**Source**: https://github.com/k3s-io/k3s/releases (checked 2026-09-28).

## 2. Ingress: k3s's bundled Traefik v3 + Gateway API

**Decision**: Use k3s's bundled Traefik (image `3.7.13`, k3s-pinned chart `41.4.2+up41.4.0`) rather than installing
a second ingress controller. Customize it with a `HelmChartConfig` (`apiVersion: helm.cattle.io/v1`,
`kind: HelmChartConfig`, `metadata.name: traefik`, `metadata.namespace: kube-system`) dropped in
`/var/lib/rancher/k3s/server/manifests/`, setting `providers.kubernetesGateway.enabled: true` and
`gateway.enabled: true` to turn on the Gateway API provider. **(cache)** for the exact `HelmChartConfig` shape —
corroborated by k3s's own bundled manifest structure, not a direct docs.k3s.io fetch; confirm before writing the
file.

**Rationale**: `ingress-nginx` is in retirement (Kubernetes SIG-network's official notice: best-effort maintenance
only until March 2026, no releases or security patches after — https://www.kubernetes.io/blog/2025/11/11/ingress-nginx-retirement/,
follow-up https://www.kubernetes.io/blog/2026/01/29/ingress-nginx-statement/). Installing a *second* Traefik
alongside k3s's bundled one would double the ingress surface for no benefit. The bundled Traefik at 3.7.10+ (which
k3s ships) supports Gateway API v1.6.1, current enough for cert-manager's Gateway-based HTTP-01 solver (below).

**Alternatives considered**: A separately Helm-installed Traefik (rejected — redundant with the bundled one, adds
an upgrade to track independently); classic `Ingress` resources instead of Gateway API (rejected — Gateway API is
the forward-looking standard now that ingress-nginx is retiring, and the spec explicitly asks for
Gateway/HTTPRoute as a learning-guide topic).

**Source**: https://raw.githubusercontent.com/k3s-io/k3s/master/manifests/traefik.yaml,
https://github.com/traefik/traefik-helm-chart (checked 2026-09-28).

## 3. TLS: cert-manager + Let's Encrypt

**Decision**: Install cert-manager v1.21.2 via Helm with `config.enableGatewayAPI: true`, and a `ClusterIssuer`
using an ACME HTTP-01 solver with `solvers[].http01.gatewayHTTPRoute` (not the legacy `ingress` solver type),
`parentRefs` pointing at the k3s/Traefik `Gateway`.

**Rationale**: Gateway API support in cert-manager is first-class and out of feature-gate as of v1.15+, matching
the Gateway-based Traefik setup above. **(cache)** for the exact `gatewayHTTPRoute` field/flag — corroborated via
search, not a direct cert-manager.io fetch; confirm before writing the `ClusterIssuer` manifest.

**Alternatives considered**: The `ingress` HTTP-01 solver type — rejected, since exposure goes through a Gateway,
not an Ingress.

**Source**: https://github.com/cert-manager/cert-manager/releases (checked 2026-09-28).

## 4. Exposure: k3s ServiceLB (no cloud load balancer)

**Decision**: Unchanged from `specs/_drafts/008-deployment/research.md` — k3s's built-in ServiceLB (Klipper) binds
the Traefik Service's ports 80/443 as hostPorts on the VPS's own public IPv4. No OVH Load Balancer product.

**Rationale**: Confirmed by the k3s architecture research above; this is what makes the ≈€8.50–13/mo cost work
instead of MKS's separately billed Load Balancer (≈€6/mo alone).

## 5. Storage: k3s local-path provisioner

**Decision**: Unchanged — k3s's bundled `local-path` StorageClass, backing PVCs with directories under
`/var/lib/rancher/k3s/storage` on the VPS's NVMe. No network block storage.

**Rationale**: Single node, no HA — network storage (e.g. OVH Block Storage/Cinder) would add cost and complexity
with no availability benefit on one node. This is why off-site database backups (below) are a hard requirement,
not a nice-to-have.

## 6. Database: CloudNativePG + Barman Cloud Plugin

**Decision**: CloudNativePG (CNPG) operator v1.30.1, a single-instance `Cluster` on `local-path` storage, PostgreSQL
16 via the official operand image `ghcr.io/cloudnative-pg/postgresql:16.10-system-trixie`, with the **pgvector**
extension added through CNPG's **ImageVolume extensions** mechanism (a separate official extension image from
`cloudnative-pg/postgres-extensions-containers`, referenced in the `Cluster`'s `extensions` list) rather than a
custom-built Postgres image. Backups to OVH Object Storage (S3-compatible) go through the separately installed
**Barman Cloud Plugin** (`cloudnative-pg/plugin-barman-cloud`, a CNPG-I plugin), configured via an `ObjectStore`
CRD, with base backups plus continuous WAL archiving and a retention policy of 7 daily + 4 weekly backups
(per `/speckit-clarify`).

**Rationale**: CNPG's in-tree `spec.backup.barmanObjectStore` field is deprecated (since CNPG 1.26); the plugin is
CNPG's current recommended path for all new deployments, so building on it now avoids a forced migration later. The
ImageVolume extensions mechanism avoids maintaining a custom Postgres+pgvector image and its own rebuild/patch
pipeline — pgvector version bumps come from CNPG's own maintained extension image instead.

**Alternatives considered**: A hand-built `Dockerfile` layering pgvector onto the CNPG Postgres image (rejected —
extra image to build, tag and patch in CI, when an official extension image already exists); keeping the in-tree
`barmanObjectStore` field (rejected — deprecated, and CNPG's own release notes are inconsistent about exactly when
it's removed, so building on the deprecated path risks a forced rewrite).

**Open item carried to `/speckit-implement`**: CNPG's release notes disagree on the exact version that removes
in-tree Barman support (1.28 vs 1.30 vs 1.31 have all appeared across releases). This doesn't change the decision
(use the plugin either way) but the exact CNPG version pinned in the Helm install should get a final release-notes
check at implementation time.

**Source**: https://github.com/cloudnative-pg/cloudnative-pg/releases,
https://github.com/cloudnative-pg/plugin-barman-cloud, https://cloudnative-pg.io/plugin-barman-cloud/docs/intro/,
https://cloudnative-pg.io/docs/1.29/imagevolume_extensions/,
https://github.com/cloudnative-pg/postgres-extensions-containers/tree/main/pgvector (checked 2026-09-28).

## 7. Secrets: SOPS + age

**Decision**: `getsops/sops` v3.13.2 and `FiloSottile/age` v1.3.2. `.sops.yaml` at the repo root:

```yaml
creation_rules:
  - path_regex: .*secret.*\.ya?ml$
    encrypted_regex: ^(data|stringData)$
    age: <age public key>
```

In CI, decrypt directly with `sops -d deploy/k8s/overlays/prod/secrets.sops.yaml | kubectl apply -f -` just before
`kustomize edit set image` + `kubectl apply -k`, rather than adding the `ksops` Kustomize plugin.

**Rationale**: `ksops` (`viaduct-ai/kustomize-sops`) is the more commonly promoted pattern now, but mainly for
GitOps setups (e.g. ArgoCD, which can't shell out to `sops` mid-sync). This deploy is a plain GitHub Actions job
that already runs arbitrary shell steps, so the extra plugin adds a dependency without solving a problem this
pipeline actually has.

**Alternatives considered**: `ksops` — rejected for now as unnecessary complexity for a single, non-GitOps
pipeline; can be revisited if the deploy flow ever moves to ArgoCD/Flux.

**Source**: https://newreleases.io/project/github/getsops/sops/release/v3.13.2,
https://newreleases.io/project/github/FiloSottile/age/release/v1.3.2,
https://github.com/viaduct-ai/kustomize-sops (checked 2026-09-28).

## 8. CI → k3s API access: Tailscale

**Decision**: Keep k3s's API server (6443) firewalled from the public internet. The `deploy-prod.yml` job joins the
same Tailscale network as the VPS using `tailscale/github-action@v4` (an ephemeral, single-use tailnet node for the
duration of the job), then talks to the API server over the tailnet using a namespace-scoped ServiceAccount token
(never the k3s admin kubeconfig).

**Rationale**: Resolves the `/speckit-clarify` decision for FR-019. `tailscale/github-action` is actively
maintained (latest v4.2.0, 2026-09-22); no comparably maintained first-party WireGuard GitHub Action exists, so
Tailscale (which is WireGuard-based under the hood) is the better-supported choice for this exact job.

**Alternatives considered**: Raw `wg-quick` shell steps with a community action (rejected — more moving parts to
maintain for the same outcome); exposing 6443 publicly behind only a token (rejected in `/speckit-clarify` — no
defense in depth); deploying over SSH (rejected in `/speckit-clarify` — Tailscale keeps kubectl as the deploy
tool, matching the learning-guide goal of operating the cluster with kubectl, not through an SSH tunnel).

**Source**: https://github.com/tailscale/github-action/releases,
https://github.com/marketplace/actions/connect-tailscale (checked 2026-09-28).

## 9. Keycloak: version and production flags

**Decision**: `quay.io/keycloak/keycloak:26.7.4`. Custom image with the `jordylab` theme baked in via `kc.sh build`,
started with `kc.sh start --optimized --hostname=https://<domain>/auth --http-relative-path=/auth
--proxy-headers=xforwarded --proxy-trusted-addresses=<traefik-service-cidr>`.

**Rationale (cache)**: All four flags are confirmed unchanged in current Keycloak (26.x uses "hostname v2",
`--http-relative-path` is still the current flag name, `--proxy-headers` still accepts `xforwarded`), but this was
search-corroborated rather than a direct `keycloak.org` fetch (proxy-blocked) — worth one direct doc check before
finalizing the container's start command.

**Realm secrets**: Keycloak's realm-import JSON supports `${ENV_VAR_NAME}` placeholders (with an optional
`:fallback`) for values including secrets, **but** this now requires the referenced variable name to be explicitly
allowlisted via `spi-admin-allowed-system-variables` (a security hardening in current Keycloak) — the prod realm
file's secret fields (e.g. the 006 service-account client secret) will need that option set alongside the
placeholder, or the realm import silently won't resolve the value.

**Source**: https://github.com/keycloak/keycloak/releases (direct fetch); flag/placeholder details via
keycloak.org search-cache (checked 2026-09-28).

## 10. Frontend image: nginx-unprivileged

**Decision**: `nginx/docker-nginx-unprivileged` (Docker Hub image `nginxinc/nginx-unprivileged`), tag family
`1.29-alpine`, as the final stage after a multi-stage Bun + `nx build jordylab --configuration=production` build.

**Rationale**: Actively maintained, runs as a non-root UID by default (needed since the container never runs as
root anywhere in this deployment), small Alpine base.

**Source**: https://hub.docker.com/r/nginxinc/nginx-unprivileged,
https://github.com/nginx/docker-nginx-unprivileged (checked 2026-09-28).

## 11. CI tooling versions

**Decision**:
- `gitleaks/gitleaks-action@v3` for the secret scan in `build.yml`.
- `docker/login-action@v4`, `docker/build-push-action@v7`, `docker/metadata-action@v6` for building and pushing
  the three images to `ghcr.io/jordy-swinnen/jordylab-*`, tagged with the commit SHA.
- GitHub Environments with a required reviewer (Jordy) on the `production` environment remains the manual-approval
  gate for `deploy-prod.yml`. Note: required-reviewer protection rules apply to public repos on the free plan,
  which fits — this repo is public.

**Source**: each action's GitHub releases page; https://docs.github.com/en/actions/reference/workflows-and-actions/deployments-and-environments
(checked 2026-09-28).

## 12. Frontend build tooling

**Decision (cache)**: `nx build jordylab --configuration=production` remains the current build invocation for an
Nx-managed Angular 21 app via the `@nx/angular:application` executor. Search-corroborated, not a direct `nx.dev`
fetch (proxy-blocked) — low risk, since this is an existing, already-working command in the repo today, not a new
one being introduced.

## Carried-forward decisions (unchanged from `specs/_drafts/008-deployment/research.md`)

These were already researched and are not re-litigated here; see that file for full detail:
- **k3s on a VPS-2 instead of OVH MKS** — cost (§2 of that file: ≈€8.50–13/mo vs ≈€40–55/mo) and sizing (§3: why
  8 GB, not 4 GB) rationale stands unchanged.
- **Podman's role** — local dev engine + `podman generate kube`/`podman kube play` learning bridge, never a
  cluster manager (§7 of that file).
- **Licensing decision** — all-rights-reserved, replacing the four MIT files (§11 of that file).

## Open item not blocking this plan

The OVH datacenter/region for the VPS and Object Storage bucket remains an explicit, deliberately deferred
`[NEEDS CLARIFICATION]` in `spec.md`'s Assumptions — it has no architectural impact (any EU region works
identically) and is a checkout-time choice for the runbook, not a design decision this plan needs to make.
