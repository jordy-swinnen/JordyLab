# Feature Specification: Production Deployment on Self-Managed k3s (OVH VPS)

**Feature Branch**: `008-ovh-k8s-deployment`

**Created**: 2026-09-28

**Status**: Draft

**Related**: 006 Settings (guest/admin roles feed the new secrets and the prod realm), 007 Mobile app (needs 008 live
to be tested on a real phone; 008 itself can be specified any time and does not depend on 007)

**Key decision**: self-managed **k3s** on one **OVH VPS-2** instead of OVH Managed Kubernetes (MKS) — MKS was
evaluated and rejected on cost (see Assumptions).

**Input**: User description: "Make JordyLab production-ready and deployed on a self-managed single-node k3s
Kubernetes cluster running on one OVH VPS-2 (4 vCores / 8 GB RAM / 75 GB NVMe), reachable on one public HTTPS domain
(bought at OVHcloud) for the web app, the 007 mobile app and the JordyBox game-catalog scanner. WHY: friends (spec
006 guests) and my phone (spec 007) need JordyLab on the internet; today it only runs locally with no container
images and local defaults mixed into shared config. Exactly two environments (local, prod); shared config holds no
hosts/origins/credentials and the app refuses to start without a valid profile. Prod serves `/` (web), `/api`
(backend) and `/auth` (Keycloak public endpoints only) from one domain with automatic TLS, through k3s's bundled
ServiceLB and Traefik on the VPS's own public IP — no cloud load balancer — with k3s's local-path storage. Postgres
with pgvector runs in-cluster with continuous backups to OVH Object Storage, point-in-time restore, and a mandatory
restore drill before go-live. Secrets are SOPS + age encrypted in git and decrypted by the deploy pipeline; the repo
and images are public and must carry no plaintext secrets. CI builds and tests backend/frontend/Keycloak images,
publishes them to GHCR tagged by commit SHA, and deploys to prod only after manual approval, with health-checked
rollout and rollback. A learning guide explains every Kubernetes/Podman concept used, pointing at JordyLab's own
files, plus a runbook for bootstrap, deploy, rollback, backup/restore, secret rotation, certificate troubleshooting
and upgrades. The four existing MIT licence files are replaced with one root all-rights-reserved LICENSE before
go-live. The VPS is self-hardened (SSH key-only, automatic updates, minimal firewall). Target cost ≤ ~€13/month
excl. AI usage. Out of scope: staging, multi-node HA, autoscaling, MKS, managed secret stores, Terraform, central
logging/metrics, and the garmin-sync-service." (full description in
`specs/_drafts/008-deployment/speckit-prompts.md` §1)

---

## Overview

JordyLab currently only runs locally: Podman Compose for Postgres and Keycloak, the backend from the IDE, the
frontend via `nx serve`. There are no container images, and local defaults (localhost URLs, dev Keycloak) are mixed
into shared configuration. This feature makes JordyLab reachable on the public internet, on a single domain with
valid TLS, running on a self-managed, single-node **k3s** cluster on one **OVH VPS-2**. There are exactly two
environments — `local` and `prod` — with a hard boundary between them. Secrets are encrypted with SOPS + age; only
ciphertext is committed, and CI decrypts it at deploy time. No plaintext secret ever touches git, images, or
ConfigMaps. Because the repo and its published images are public, the four existing MIT licence files are replaced
with a single all-rights-reserved licence before go-live. Because Jordy knows Kubernetes basics but is new to
Podman, the feature also ships a learning guide and an operational runbook built on JordyLab's own files.

---

## Clarifications

### Session 2026-09-28

- Q: How should CI reach the k3s API server to deploy? → A: Keep 6443 firewalled from the public internet; GitHub
  Actions joins a WireGuard/Tailscale network for the deploy step.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - JordyLab is live on a public HTTPS domain (Priority: P1)

As Jordy, I want the web app, API and login served from one domain with a valid certificate, so that my friends and
my phone can use JordyLab from anywhere.

**Why this priority**: Without a reachable public HTTPS domain, nothing else in this feature — or in 006 (guests) or
007 (mobile app) — has any value. This is the smallest slice that makes JordyLab usable off the local network.

**Independent Test**: Open `https://<domain>` from a phone on mobile data, log in, open the Game Catalog, and check
the certificate is valid.

**Acceptance Scenarios**:

1. **Given** the cluster is deployed, **When** a browser opens `https://<domain>`, **Then** the Angular app loads
   over a valid, auto-renewing TLS certificate, and HTTP redirects to HTTPS.
2. **Given** the app, **When** it calls `/api/**`, **Then** the backend answers on the same origin, with no CORS
   needed for the web app.
3. **Given** the login flow, **When** a user logs in, **Then** Keycloak is served under `https://<domain>/auth` and
   redirects back correctly.
4. **Given** anyone on the internet, **When** they request `/auth/admin`, Keycloak metrics/health, or any internal
   port, **Then** it isn't reachable.
5. **Given** the JordyBox scanner, **When** it pushes a scan to the prod URL, **Then** ingestion works exactly as it
   does locally.
6. **Given** the 007 mobile app, **When** it calls the API, logs in, verifies App Links, downloads the APK, or
   receives a push notification, **Then** the prod setup supports it (CORS for the app origin,
   `/.well-known/assetlinks.json`, APK storage, notification delivery).

---

### User Story 2 - Two clearly separated environments (Priority: P1)

As Jordy, I want exactly two environments, local and prod, so that I always know which config is in effect and
local defaults can never leak into prod.

**Why this priority**: Without an enforced boundary between local and prod, a stray local default (a localhost URL,
a dev credential) can silently break or compromise production. This has to be in place before anything runs for
real.

**Acceptance Scenarios**:

1. **Given** the backend, **When** it starts without an active profile, **Then** it refuses to start with a clear
   message (fail fast). `local` and `prod` are the only valid profiles.
2. **Given** the shared config, **When** I look at it, **Then** it holds no localhost URLs, dev CORS origins or
   credentials. Those live only in the `local` profile.
3. **Given** local development, **When** I run the documented commands, **Then** Postgres and Keycloak run in
   Podman, and the backend and frontend run the same way as today.
4. **Given** prod, **When** the app runs, **Then** Keycloak runs in production mode with a prod realm (no dev user,
   no localhost redirects).
5. **Given** the repo, **When** anyone reads the environment docs, **Then** one table lists every setting that
   differs between local and prod, and where each value comes from.

---

### User Story 3 - Secrets are injected, never committed (Priority: P1)

As Jordy, I want every prod secret to come from an encrypted file that only I and CI can decrypt, so that a public
repo never leaks a live credential.

**Why this priority**: Committing a real secret to a public repository is an unrecoverable, high-severity mistake.
The secrets pipeline must exist before any other prod workload is allowed to run.

**Independent Test**: Inspect the encrypted secrets file in the public repo without the age private key, confirm
only key names and ciphertext are visible, then rotate one value through `sops` and confirm the running pod picks
it up after a restart.

**Acceptance Scenarios**:

1. **Given** a secret (API keys, DB and Keycloak credentials, client secrets, backup keys), **When** prod runs,
   **Then** the value comes from a SOPS-encrypted file in git, decrypted by the deploy pipeline with an age key that
   only CI and Jordy hold.
2. **Given** a secret is rotated (edited with `sops`, committed, deployed), **When** the pod restarts, **Then** the
   new value is used.
3. **Given** the encrypted secrets file in the public repo, **When** someone without the age private key reads it,
   **Then** they only see key names and ciphertext.
4. **Given** git history, the published container images and the ConfigMaps, **When** they're scanned, **Then** no
   plaintext secret values are found. CI runs a secret scan on every push.
5. **Given** local development, **When** I run the app, **Then** secrets come from the gitignored `.env`, as today.

---

### User Story 4 - One-click, approved deployments with rollback (Priority: P1)

As Jordy, I want every prod deployment gated behind my approval, health-checked, and reversible, so that a bad
release can never turn into a stuck outage.

**Why this priority**: Without approval gates, health checks and rollback, every deploy risks an outage with no
safety net, on a system other people now depend on.

**Independent Test**: Push a change to `main`, approve the resulting deploy in GitHub, confirm the new version is
live and healthy, then trigger a rollback and confirm the previous version returns.

**Acceptance Scenarios**:

1. **Given** a push to `main`, **When** CI runs, **Then** it builds and tests the backend, frontend and Keycloak
   images and publishes them tagged with the commit SHA.
2. **Given** built images, **When** I approve the production deployment in GitHub, **Then** the cluster is updated
   to exactly those images, and the pipeline waits until the rollout is healthy, or fails loudly.
3. **Given** a bad release, **When** I trigger a rollback, **Then** the previous version is running again within 5
   minutes.
4. **Given** the deploy credentials, **When** they're used, **Then** they can only change resources in the JordyLab
   namespace, not the whole cluster.

---

### User Story 5 - Data is durable and restorable (Priority: P1)

As Jordy, I want the database and uploaded artwork to survive restarts and be restorable from backups, so that a
crash or a lost VPS never means losing real data.

**Why this priority**: Once other people (006 guests) depend on JordyLab daily, losing the database or game artwork
with no way back is unacceptable.

**Independent Test**: Follow the restore runbook to restore the production database to a point in time into a fresh
database, and confirm the app works against it.

**Acceptance Scenarios**:

1. **Given** the production database, **When** a day passes, **Then** a backup exists in OVH Object Storage,
   including continuous write-ahead logs.
2. **Given** a backup, **When** I follow the restore runbook, **Then** I can restore the database to a point in
   time into a fresh database, and the app works against it. This is tested at least once before go-live.
3. **Given** a pod restart or a VPS reboot, **When** it comes back, **Then** uploaded game artwork and database data
   are still there (node-local storage on the VPS disk).
4. **Given** the VPS is lost entirely, **When** I follow the runbook, **Then** a fresh VPS can be rebuilt from git +
   the SOPS files + the Object Storage backups.

---

### User Story 6 - Learn Kubernetes and Podman on my own project (Priority: P2)

As someone who knows Kubernetes basics and is new to Podman, I want a learning guide built on JordyLab's own files,
so that I understand and can operate what's running, not just copy it.

**Why this priority**: Valuable for long-term self-sufficiency and confidence operating the cluster, but the
platform can run in production before the guide is finished — the running system doesn't depend on it.

**Acceptance Scenarios**:

1. **Given** the learning guide, **When** I read it, **Then** each concept (container image, Pod, Deployment,
   Service, Gateway/HTTPRoute, ConfigMap, Secret, PVC/StorageClass, Namespace, probes, resources, operators/CRDs,
   RBAC) is explained with a link to the exact JordyLab file that uses it.
2. **Given** the k3s chapter, **When** I read it, **Then** I understand what k3s bundles (containerd, Traefik,
   CoreDNS, ServiceLB, local-path provisioner), how ServiceLB exposes ports on the VPS's own IP, how the bundled
   Traefik is customised, and what I now own as host operator (OS patching, firewall, k3s upgrades).
3. **Given** the Podman chapter, **When** I follow it, **Then** I understand Podman's actual role: my local dev
   container engine (replacing Docker for build, test and compose) and a learning bridge (`podman generate kube` /
   `podman kube play` to sandbox manifests before `kubectl apply`). It is **not** a cluster manager: the cluster is
   managed with kubectl (+ Helm), and k3s runs containers with containerd. The chapter also covers rootless
   containers, `podman machine` on macOS vs native on CachyOS, Containerfiles, and why prod images are built in CI
   (arm64 vs amd64).
4. **Given** the secrets chapter, **When** I follow it, **Then** I can create an age key, encrypt and edit a secret
   with SOPS, and explain why the private key must never be committed.
5. **Given** hands-on exercises, **When** I do them, **Then** I have built an image locally, sandboxed a manifest
   with `podman kube play`, port-forwarded to the Keycloak admin console, read pod logs, rotated a SOPS secret, and
   done a rollback on prod.
6. **Given** the runbook, **When** something happens (deploy, rollback, logs, DB restore, secret rotation,
   certificate issue, k3s/OS upgrade, VPS rebuild), **Then** there's a step-by-step procedure with the exact
   commands.

---

### User Story 7 - The public code is readable but not reusable (Priority: P1)

As Jordy, I want the public repo and public images licensed "all rights reserved, viewing only", so that people can
read my code but not use, copy, modify or redistribute it until I decide otherwise.

**Why this priority**: The repo and its images become genuinely public and production-facing with this feature; the
licence terms must be correct before that release, or the current MIT terms keep permitting reuse indefinitely.

**Context**: The repo is already public, and four MIT licence files exist (`.claude/LICENSE`, `jordylab-be/LICENSE`,
`jordylab-fe/LICENSE`, `garmin-sync-service/LICENSE`, since 2026-08-01). MIT permits reuse. As far as we know,
versions already published under MIT stay MIT for whoever copied them. The change applies from the relicensing
commit onward.

**Acceptance Scenarios**:

1. **Given** the repo, **When** anyone looks for a licence, **Then** there is exactly one root `LICENSE` stating the
   source is published for viewing only, all rights reserved, with the copyright holder (Jordy Swinnen) and year.
   All four MIT files are removed.
2. **Given** package manifests (`package.json`, Gradle/`pyproject.toml` metadata, etc.), **When** they declare a
   licence, **Then** it matches (e.g. `"license": "UNLICENSED"` / "All rights reserved"), with no leftover MIT
   declarations.
3. **Given** the README, **When** someone reads it, **Then** a short "Licence" section explains the terms in plain
   words and that permission requests go to Jordy.
4. **Given** the published container images, **When** they're inspected, **Then** their
   `org.opencontainers.image.licenses` label matches the repo licence (no MIT).
5. **Given** third-party code in the repo (e.g. copied snippets or vendored files), **When** it keeps its own
   licence, **Then** that licence is preserved and not overwritten.

---

### User Story 8 - First-time setup from zero (Priority: P2)

As Jordy, I want a single bootstrap guide that takes an empty OVH account to a working, deployed cluster, so that
the whole setup is repeatable and I'm never guessing at a missing step.

**Why this priority**: Bootstrap happens once, and can be reviewed and rehearsed against the runbook without
touching the live system; it doesn't gate ongoing operation once it has succeeded.

**Acceptance Scenarios**:

1. **Given** an empty OVH account, **When** I follow the bootstrap guide, **Then** I end with: a domain + DNS, a
   hardened VPS-2 (SSH keys only, firewall, automatic security updates), k3s installed at a pinned version with its
   bundled Traefik customised, cert-manager + CloudNativePG installed, an Object Storage bucket, an age key +
   SOPS-encrypted secrets, and the first successful deploy.
2. **Given** the bootstrap, **When** it creates the age key, **Then** the guide explains where the private key lives
   (password manager + GitHub environment secret) and that losing it makes every prod secret unrecoverable.

---

### Edge Cases

- The VPS reboots (k3s or OS upgrade): there's short downtime, and everything comes back without manual steps. This
  is documented as accepted.
- The VPS disk or host is lost: the node-local volumes are gone, and recovery uses the rebuild runbook (git + SOPS +
  Object Storage backups).
- The certificate fails to renew: the runbook covers how to diagnose it. Monitoring is out of scope, but
  cert-manager status is checked in the runbook.
- A database migration fails during rollout: the new pod never becomes ready, the old version keeps serving (or the
  runbook explains recovery), and the pipeline fails.
- The age private key is lost: prod secrets can't be decrypted. The offline copy in the password manager is
  required, and the runbook covers re-keying.
- The disk fills up (images + volumes on 75 GB): the runbook has check and cleanup commands.
- An image built on the MacBook (arm64) is pushed by mistake: CI is the only path to prod, and the manifests
  reference CI-built SHA tags only.
- The domain's DNS isn't propagated yet: the certificate isn't issued, and the bootstrap guide covers waiting and
  verifying.
- Someone pulls the public images: they must contain no secrets or prod config values that are secret.

## Requirements *(mandatory)*

### Functional: Environments

- **FR-001**: There MUST be exactly two runtime environments, `local` and `prod`, expressed as Spring profiles,
  Angular build configurations and Keycloak modes.
- **FR-002**: Shared configuration MUST contain no environment-specific hosts, origins or credentials. Starting
  without a valid profile MUST fail fast.
- **FR-003**: A single document MUST list every setting that differs between the environments and where its value
  comes from.

### Functional: Packaging & delivery

- **FR-004**: The backend, frontend and Keycloak (including the jordylab theme) MUST each be packaged as a container
  image, built reproducibly in CI for linux/amd64.
- **FR-005**: Images MUST be tagged with the commit SHA. Prod MUST only run SHA-tagged images, never `latest`.
- **FR-006**: Deployment to prod MUST require manual approval, MUST wait for a healthy rollout, and MUST support
  rollback to the previous version. [NEEDS CLARIFICATION: confirm the deploy trigger — build and test run on every
  push to `main`, and only the deploy-to-prod step is gated behind manual approval]
- **FR-007**: CI deploy credentials MUST be limited to the JordyLab namespace.

### Functional: Exposure & security

- **FR-008**: One public domain with automatic TLS MUST serve `/` (web), `/api` (backend), `/auth` (Keycloak public
  endpoints only) and the mobile App Links file.
- **FR-009**: The Keycloak admin console, metrics, health and management ports MUST NOT be publicly reachable.
- **FR-010**: Every workload MUST declare readiness/liveness probes and resource requests/limits.
- **FR-011**: Prod secrets MUST be stored in git only as SOPS + age ciphertext and decrypted by the deploy pipeline.
  The age private key MUST exist only in the GitHub `production` environment and Jordy's offline store.
- **FR-012**: CI MUST scan for committed secrets and fail on findings.
- **FR-013**: Push notifications used by the mobile app (spec 007) MUST be delivered through [NEEDS CLARIFICATION:
  a self-hosted ntfy instance running in this cluster, or the public ntfy.sh service].

### Functional: Data

- **FR-014**: PostgreSQL with pgvector MUST run in the cluster on node-local storage, with continuous backups
  (base backups + WAL) to OVH Object Storage and point-in-time restore. [NEEDS CLARIFICATION: backup retention
  schedule — a rotation such as 7 daily + 4 weekly snapshots has been proposed but not confirmed]
- **FR-015**: A restore drill MUST be performed and documented before go-live. [NEEDS CLARIFICATION: ongoing restore
  drill cadence after go-live — a quarterly cadence has been proposed but not confirmed]
- **FR-016**: Game artwork (and 007 APK files) MUST be stored on persistent storage (k3s local-path) that survives
  pod restarts and VPS reboots.

### Functional: Education & operations

- **FR-017**: A learning guide MUST explain Kubernetes and Podman concepts using JordyLab's own files, with
  hands-on exercises.
- **FR-018**: A runbook MUST cover bootstrap (VPS hardening + k3s install), deploy, rollback, logs, DB
  backup/restore, secret rotation, certificate troubleshooting, k3s/OS upgrades and full VPS rebuild.
- **FR-019**: The VPS MUST be hardened: SSH key-only access, no root password login, automatic security updates,
  and a firewall exposing only SSH (restricted), 80 and 443. Kubelet and flannel ports MUST NOT be public. The
  Kubernetes API (6443) MUST also stay firewalled from the public internet; CI reaches it for deploys by joining a
  WireGuard/Tailscale network.
- **FR-020**: The repository MUST carry a single root `LICENSE` with "all rights reserved, source available for
  viewing only" terms, replacing all four MIT licence files; package manifests, the README and image labels MUST
  match, and third-party licences MUST be preserved. This MUST land before go-live.
- **FR-021**: AGENTS.md MUST be updated: Hetzner/Compose/Watchtower/Ollama prod references replaced with the
  k3s-on-OVH-VPS setup.

### Key Entities *(include if feature involves data)*

- **Container images**: backend, frontend, keycloak — built in CI, tagged by commit SHA, published to GHCR.
- **K8s manifests**: base configuration plus one prod overlay (domain, image tags, ConfigMap values).
- **Host + k3s configuration**: VPS firewall rules, k3s install, Traefik customisation.
- **Cluster add-on configuration**: cert-manager (TLS issuance), CloudNativePG (database).
- **SOPS-encrypted secret files**: ciphertext committed to git, plus the age key configuration that names who can
  encrypt.
- **Database cluster definition**: the in-cluster Postgres instance, its storage, and its backup target.
- **CI workflows**: build-and-publish, deploy-to-prod-with-approval.
- **Docs**: the learning guide, the runbook, the environments table.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A friend on mobile data can open `https://<domain>`, log in and use the Game Catalog.
- **SC-002**: From an approved deploy to a healthy new version takes under 10 minutes; a rollback takes under 5
  minutes.
- **SC-003**: Zero secret values in git, images or ConfigMaps (CI secret scan green; manual image inspection in the
  runbook).
- **SC-004**: A database restore to a point in time succeeds in a drill, in under 30 minutes by following the
  runbook.
- **SC-005**: Monthly infrastructure cost stays at or under about €13 excl. VAT (VPS-2 + Object Storage + domain),
  excluding AI usage.
- **SC-006**: Jordy can explain and do every runbook procedure alone after working through the learning guide.
- **SC-007**: No MIT licence file or MIT licence declaration remains in the repo (excluding third-party files), and
  a CI check keeps it that way.
- **SC-008**: A public port scan of the VPS shows only 80, 443 and (restricted) SSH — 6443 is never publicly
  reachable, since CI reaches it over WireGuard/Tailscale instead. `/auth/admin` is not reachable.

## Assumptions

- One OVH **VPS-2** (4 vCores / 8 GB / 75 GB NVMe, from €7.21/mo excl. VAT) running single-node **k3s**, on
  [NEEDS CLARIFICATION: VPS commitment term — the 12-month upfront rate is the cheapest quoted price, but the
  no-commitment/monthly price and the renewal price were not shown and need confirming at checkout]. Short downtime
  during maintenance is accepted. VPS-3 is the upgrade path if memory runs short.
- [NEEDS CLARIFICATION: which OVH datacenter/region to provision the VPS and Object Storage bucket in — the closest
  EU location offered at checkout has been proposed, ideally the same region for both]
- OVH Managed Kubernetes (MKS) was evaluated and rejected: MKS needs Public Cloud nodes plus a separately billed
  Load Balancer, landing around €40–55/month, versus about €8.50–13/month for k3s on a VPS-2. The learning value is
  judged equal or higher with k3s, since it additionally covers the node/OS layer that MKS hides.
- Licensing is "all rights reserved, viewing only" for now; Jordy may loosen it later (e.g. PolyForm Noncommercial,
  Business Source License or MIT). This isn't legal advice; check with a lawyer if it becomes commercially
  relevant.
- The domain is bought at OVHcloud. The repo and GHCR images are public, so images contain nothing secret.
- Ingress uses k3s's bundled Traefik v3 (Gateway API enabled) rather than a separately installed ingress
  controller. Exposure is through k3s's built-in ServiceLB on the VPS's own public IP, with no cloud load balancer.
  Certificates come from Let's Encrypt via cert-manager.
- Storage uses k3s's default local-path provisioner (single node; no network block storage).
- Local development keeps Podman + compose. There's no staging environment.
- Out of scope: staging/other environments, multi-node high availability, autoscaling, managed Kubernetes (MKS),
  managed secret stores, Terraform/IaC for the VPS (possible later), central logging/metrics dashboards, and the
  garmin-sync-service (no code yet).
