# Data Model: Production Deployment on Self-Managed k3s (OVH VPS)

This feature has no business/domain data model — it's infrastructure. The "entities" below are the deployment and
operational artifacts introduced by the spec's Functional Requirements and Key Entities, with the fields and state
transitions that matter for the acceptance scenarios (rollout health, rollback, backup retention, certificate
renewal). They map onto Kubernetes resources and CI artifacts, not application database tables.

## Environment Profile

Represents one of the exactly-two runtime environments (FR-001, FR-002).

| Field | Values | Notes |
|---|---|---|
| `name` | `local` \| `prod` | The only two valid values; anything else must fail fast at startup |
| `springProfile` | `local` \| `prod` | Backend's active Spring profile |
| `angularConfiguration` | `development` \| `production` | Frontend build configuration |
| `keycloakMode` | `start-dev` \| `start --optimized` | Keycloak startup mode |
| `settingsSource` | `.env` (gitignored) \| SOPS-decrypted env vars | Where credentials come from |

**Invariant**: shared configuration (`application.yaml`, the base Kustomize layer) holds no member of any
`Environment Profile`'s environment-specific fields (hosts, origins, credentials) — those live only in the
profile-specific files/overlays (FR-002).

## Container Image

One per deployable component (FR-004, FR-005).

| Field | Example | Notes |
|---|---|---|
| `component` | `backend` \| `frontend` \| `keycloak` | The three CI-built images |
| `registryPath` | `ghcr.io/jordy-swinnen/jordylab-backend` | Public GHCR repository |
| `tag` | commit SHA (e.g. `a1b2c3d`) | Never `latest` in prod (FR-005) |
| `platform` | `linux/amd64` | Only platform prod runs; arm64 (MacBook) builds are never deployed |
| `licenceLabel` | `org.opencontainers.image.licenses` OCI label | Must match the repo's all-rights-reserved licence (FR-020, US7) |

`ntfy`'s container image is a Key Entity exception: it runs from ntfy's own upstream image, not one of these three
CI-built images (per `/speckit-clarify`).

## Deployment Rollout

One per `deploy-prod.yml` run (FR-006, US4).

**States**: `Pending` (images built, awaiting manual approval) → `Approved` → `Progressing` (rollout applied,
waiting on readiness probes) → `Healthy` (all replicas ready) | `Failed` (probe never passes / apply error) →
`RolledBack` (previous image SHAs re-applied).

| Field | Notes |
|---|---|
| `imageShas` | One SHA per component being deployed |
| `approver` | Must be Jordy (GitHub Environment required reviewer) |
| `previousImageShas` | Recorded before apply, used for rollback |
| `rolloutStatus` | Output of `kubectl rollout status`; pipeline fails loudly on anything but success |
| `namespaceScope` | Always `jordylab` — the deploy ServiceAccount token cannot touch other namespaces (FR-007) |

**Transition rule**: `Progressing → Failed` must never silently leave the old version down — with `Recreate`
strategy (single replica, per the drafts' plan), the runbook's edge-case procedure applies (a failed migration
keeps the old pod down until rolled back); this is a documented, not automatic, recovery step.

## Secret Entry

One row per credential the deployment needs (FR-011, US3).

| Field | Example | Notes |
|---|---|---|
| `key` | `POSTGRES_APP_PASSWORD`, `ANTHROPIC_API_KEY`, `OPENROUTER_API_KEY` (006), Keycloak service-account secret (006), S3 keys, APK signing key (007, stays in GH Actions secrets not SOPS) | |
| `consumer` | which Deployment/StatefulSet reads it | |
| `source` | `deploy/k8s/overlays/prod/secrets.sops.yaml` (ciphertext) | Only ciphertext is committed |
| `decryptedBy` | CI, using the age private key (GitHub `production` environment secret) | Never written to disk outside the CI job |
| `rotationTrigger` | manual `sops` edit → commit → deploy → pod restart | Documented in the runbook |

**Invariant**: no `Secret Entry`'s plaintext value ever appears in git history, a container image, or a ConfigMap
(FR-011, FR-012, SC-003).

## Backup

One per CloudNativePG Barman Cloud Plugin backup run (FR-014, US5).

**States**: `Base` (full backup) or `WAL` (continuous archive segment) → retained under one of two rotation classes
→ `Pruned` once outside the retention window.

| Field | Notes |
|---|---|
| `type` | `base` \| `wal` |
| `retentionClass` | `daily` (7 kept) \| `weekly` (4 kept) — per `/speckit-clarify` |
| `target` | OVH Object Storage bucket (S3-compatible), via the Barman Cloud Plugin's `ObjectStore` |
| `restoredInDrill` | boolean — at least one `true` value MUST exist before go-live (FR-015), and again each quarter thereafter |

## Certificate

One per domain served (FR-008, edge case: renewal failure).

**States**: `Pending` (ACME challenge in progress) → `Ready` (valid, in use) → `Renewing` (auto-renewal near
expiry) → `Ready` | `Failed` (renewal error — runbook diagnostic procedure applies).

| Field | Notes |
|---|---|
| `domain` | The one public HTTPS domain |
| `issuer` | Let's Encrypt, via cert-manager's `ClusterIssuer` |
| `challengeType` | HTTP-01 via `gatewayHTTPRoute` (not the legacy `ingress` solver) |

## HTTP Route

One per publicly or internally exposed path (FR-008, FR-009). Full table in `contracts/http-routing.md`.

| Field | Notes |
|---|---|
| `pathPrefix` | e.g. `/`, `/api`, `/auth`, `/.well-known/assetlinks.json` |
| `backendService` | frontend, backend, or Keycloak |
| `exposure` | `public` \| `never-public` (admin console, metrics, health, management ports, port 9000) |

## CI Pipeline Run

Two kinds, corresponding to the two workflows (FR-004–FR-007, FR-012).

| Field | `build.yml` | `deploy-prod.yml` |
|---|---|---|
| `trigger` | push to `main` | manual approval on a `Pending` Deployment Rollout |
| `stages` | test → gitleaks scan → build 3 images → push to GHCR | join Tailscale → SOPS decrypt → `kustomize edit set image` → `kubectl apply -k` → rollout status |
| `credentialScope` | GHCR push token | namespace-scoped ServiceAccount token over Tailscale (never the k3s admin kubeconfig) |
| `failureMode` | secret found → fail the push | rollout never healthy → fail loudly, no silent partial deploy |
