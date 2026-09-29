# Implementation Plan: Production Deployment on Self-Managed k3s (OVH VPS)

**Branch**: `008-ovh-k8s-deployment` | **Date**: 2026-09-28 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/008-ovh-k8s-deployment/spec.md`

## Summary

Make JordyLab run in production on a self-managed, single-node k3s cluster on one OVH VPS-2, reachable at one
public HTTPS domain, with exactly two environments (`local`, `prod`), SOPS+age secrets decrypted only in CI, and
CI/CD via GitHub Actions with manual approval and rollback. Technical approach (see `research.md` for verified
versions and rationale): k3s's bundled Traefik v3 + Gateway API for ingress, k3s ServiceLB for exposure, k3s
local-path for storage, CloudNativePG + the Barman Cloud Plugin for the database and its backups to OVH Object
Storage, cert-manager for TLS, and a GitHub Actions pipeline that reaches the firewalled k3s API over Tailscale.
The four existing MIT licence files are replaced with a single all-rights-reserved `LICENSE` before go-live, and a
learning guide + runbook document every concept and procedure this introduces.

## Technical Context

**Language/Version**: Java 25 (backend, existing), TypeScript/Angular 21 via Nx 22 + Bun (frontend, existing);
new artifacts are Kubernetes YAML (Kustomize base + prod overlay), Helm values (for cluster add-ons), Containerfiles,
and GitHub Actions YAML workflows. Runbook commands are written to be Fish-shell compatible (no bash heredocs),
per Jordy's own shell.

**Primary Dependencies**: k3s `v1.37.0+k3s1` (bundled Traefik `3.7.13`, Gateway API v1.6.1 support); cert-manager
`v1.21.2`; CloudNativePG operator `v1.30.1` + the Barman Cloud Plugin (`cloudnative-pg/plugin-barman-cloud`);
Keycloak `26.7.4`; `nginxinc/nginx-unprivileged:1.30-alpine`; SOPS `v3.13.2` + age `v1.3.2`; a self-hosted
`ntfy` instance (upstream image, not built by this repo's CI); GitHub Actions with `docker/login-action@v4`,
`docker/build-push-action@v7`, `docker/metadata-action@v6`, `gitleaks/gitleaks-action@v3`,
`tailscale/github-action@v4`. Full rationale and source links in `research.md`.

**Storage**: PostgreSQL 16 with pgvector (CNPG operand `ghcr.io/cloudnative-pg/postgresql:16.15-standard-trixie`,
which ships pgvector), a single instance on the k3s `local-path` StorageClass; game
artwork and mobile APK files on a `local-path` PVC; database backups (base backups + continuous WAL) to OVH
Object Storage (S3-compatible) via the Barman Cloud Plugin, with a 30-day point-in-time recovery window (daily + weekly base backups; see spec.md FR-014).

**Testing**: Existing JUnit 5 / AssertJ / Testcontainers for the backend's new fail-fast-without-a-profile test;
existing Vitest / `@ngneat/spectator/vitest` for any frontend environment-config tests; CI-level validation via
`gitleaks` (secret scanning) and a build+push dry run; infrastructure validation is manual/runbook-driven per
`quickstart.md` (port scan, TLS check, restore drill, rollback drill) since there is no staging environment to
run automated infra tests against.

**Target Platform**: Container images built for `linux/amd64` only (the VPS is x86_64; MacBook-built arm64 images
are never deployed — CI is the only path to prod). Cluster: single-node k3s on one OVH VPS-2 (Ubuntu/Debian LTS).

**Project Type**: Infrastructure/deployment feature for an existing modular-monolith web app (not a new
application feature) — new deployment manifests, CI workflows, container images, and documentation; minimal
application-code changes (Spring profile enforcement, Angular prod config, licence metadata).

**Performance Goals**: Approved-deploy-to-healthy-rollout under 10 minutes; rollback under 5 minutes (SC-002).
Point-in-time database restore under 30 minutes (SC-004).

**Constraints**: Monthly infrastructure cost ≤ ~€13 excl. VAT and AI usage (SC-005), on a monthly/no-commitment
VPS-2 term (per `/speckit-clarify`). Single node, no HA, no autoscaling. k3s API (6443) never publicly reachable —
CI reaches it only over Tailscale. No `latest` image tags in prod. VPS-2's 8 GB RAM is real headroom, not slack —
sizing rationale in `research.md` / the drafts' research.

**Scale/Scope**: A handful of users (Jordy + approved guests from spec 006), one mobile app (spec 007) and one
scanner client (JordyBox) as consumers — not an internet-scale service. Single Kubernetes namespace (`jordylab`).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

The constitution (`​.specify/memory/constitution.md`) governs backend/frontend coding style (Clean Code, fail-fast,
builders, testing discipline, language currency) and Angular/Java architecture patterns. This feature is primarily
infrastructure (Kubernetes manifests, Helm values, CI workflows, Containerfiles, docs) with only a thin slice of
actual application code, so most principles apply narrowly:

| Principle | Applicability | Gate |
|---|---|---|
| I. Clean Code Discipline | Applies to the small code changes (profile-fail-fast logic, Angular prod environment file) | PASS — no new abstractions beyond what's needed |
| II. Fail Fast, No Silent Failures | Directly required by FR-002 (refuse to start without a valid profile) | PASS — this is the feature's own requirement |
| III. Immutable, Builder-First Design | N/A to Kubernetes YAML/Helm; applies if any new Java class is introduced (none currently planned — profile check is a startup listener, not a new domain object) | N/A / PASS |
| IV. Testing Discipline | Applies to the one new backend test (fail-fast-without-profile) — JUnit 5 + AssertJ | PASS |
| V. Language & Tooling Currency | No new Java/Angular language surface introduced by this feature | N/A |

No violations. **Complexity Tracking is empty** — no gate requires justification.

**Post-Phase 1 re-check**: `data-model.md`, `contracts/`, and `quickstart.md` introduce no new Java/Angular code
surface beyond what Phase 0 already accounted for (the fail-fast profile check and prod environment config) — all
new artifacts are Kubernetes manifests, Helm values, CI workflows, and docs. Gate still **PASS**, no new violations.

## Project Structure

### Documentation (this feature)

```text
specs/008-ovh-k8s-deployment/
├── plan.md              # This file
├── research.md          # Phase 0 output (versions, decisions, rationale)
├── data-model.md         # Phase 1 output (deployment/config entities)
├── quickstart.md        # Phase 1 output (end-to-end validation guide)
├── contracts/
│   ├── http-routing.md      # Public domain → backing service routing contract
│   └── secrets-schema.md    # Required secret keys, consumers, rotation notes
└── tasks.md             # Phase 2 output (/speckit-tasks — not created by /speckit-plan)
```

### Source Code (repository root)

```text
deploy/
├── containers/
│   ├── backend/Containerfile        # Spring Boot 4 multi-stage build
│   ├── frontend/Containerfile       # Bun + nx build --configuration=production → nginx-unprivileged
│   └── keycloak/Containerfile       # kc.sh build with the jordylab theme baked in
├── host/
│   ├── firewall.md / firewall setup notes (ufw/nftables rules: 22 restricted, 80, 443)
│   └── traefik-helmchartconfig.yaml  # HelmChartConfig dropped into /var/lib/rancher/k3s/server/manifests/
├── k8s/
│   ├── base/                         # Deployments, Services, HTTPRoutes, ConfigMaps (env-agnostic)
│   ├── overlays/prod/
│   │   ├── kustomization.yaml        # domain, image SHA tags, config.env → ConfigMap
│   │   └── secrets.sops.yaml         # SOPS-encrypted Secret manifests (committed ciphertext)
│   └── cluster/                      # cert-manager ClusterIssuer, CNPG Cluster + Barman plugin ObjectStore, ntfy
├── keycloak/
│   └── realm-prod.json               # Prod realm, no dev users, secrets via ${ENV_VAR} placeholders
└── .sops.yaml                        # age public key + encrypted_regex config

docs/
├── learn/                            # Learning guide chapters (one per concept area, linking real files)
├── runbook.md                        # Bootstrap, deploy, rollback, backup/restore, rotation, upgrades, rebuild
└── environments.md                   # The local/prod settings table (FR-003)

.github/workflows/
├── build.yml                         # test + gitleaks + build/push 3 images to GHCR, tagged by SHA
└── deploy-prod.yml                   # manual approval env, Tailscale join, SOPS decrypt, kustomize apply, rollout status

jordylab-be/src/main/resources/
├── application.yaml                  # shared config only — no hosts/origins/credentials
├── application-local.yaml            # existing local defaults, moved out of shared config
└── application-prod.yaml             # env-var-driven prod config
```

**Structure Decision**: A new top-level `deploy/` directory holds everything Kubernetes/container/host-specific,
separate from the three existing sub-projects (`jordylab-be`, `jordylab-fe`, `garmin-sync-service`), since this
infrastructure spans all of them rather than belonging to any one. `docs/` gets the learning guide and runbook
(new), alongside any existing docs. Application code changes are minimal and land in their existing sub-project
locations (`jordylab-be/src/main/resources/application-*.yaml`, the Angular prod environment file).

## Complexity Tracking

*No entries — the Constitution Check above found no violations requiring justification.*
