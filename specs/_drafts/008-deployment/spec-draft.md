# Feature Specification: Production Deployment on Self-Managed k3s (OVH VPS)

**Feature Branch**: `008-ovh-k8s-deployment`
**Created**: 2026-09-27
**Status**: Draft (reference for `/speckit-specify`; see `research.md` and `plan-draft.md`)
**Related**: 006 Settings (new secrets, prod realm), 007 Mobile app (public domain, APK download, App Links)
**Infrastructure decision (2026-09-27)**: self-managed k3s on an OVH VPS-2 instead of OVH MKS; see research.md §2

---

## Overview

Make JordyLab deployable and running in production on a self-managed single-node **k3s** Kubernetes cluster on one **OVH VPS-2** (4 vCores / 8 GB / 75 GB NVMe), reachable at one public HTTPS domain for the web app, the 007 mobile app and the JordyBox scanner. There are exactly two environments, `local` and `prod`, with a clear boundary between them. Secrets are encrypted with SOPS + age. Only ciphertext is committed, and CI decrypts it at deploy time. No plaintext secret touches git or images. (OVH Managed Kubernetes was evaluated and rejected on cost; see research.md §2.) Jordy has basic Kubernetes knowledge and is new to Podman, so the feature also ships a learning guide and a runbook tied to JordyLab's own files.

---

## User Scenarios & Testing

### User Story 1: JordyLab is live on a public HTTPS domain (Priority: P1)

As Jordy, I want the web app, API and login served from one domain with a valid certificate, so that my friends and my phone can use JordyLab from anywhere.

**Independent Test**: Open `https://<domain>` from a phone on mobile data, log in, open the Game Catalog, and check the certificate is valid.

**Acceptance Scenarios**:
1. **Given** the cluster is deployed, **When** a browser opens `https://<domain>`, **Then** the Angular app loads over a valid, auto-renewing TLS certificate, and HTTP redirects to HTTPS.
2. **Given** the app, **When** it calls `/api/**`, **Then** the backend answers on the same origin, with no CORS needed for the web app.
3. **Given** the login flow, **When** a user logs in, **Then** Keycloak is served under `https://<domain>/auth` and redirects back correctly.
4. **Given** anyone on the internet, **When** they request `/auth/admin`, Keycloak metrics/health, or any internal port, **Then** it isn't reachable.
5. **Given** the JordyBox scanner, **When** it pushes a scan to the prod URL, **Then** ingestion works exactly as it does locally.
6. **Given** the 007 mobile app, **When** it calls the API, logs in, verifies App Links or downloads the APK, **Then** the prod setup supports it (CORS for the app origin, `/.well-known/assetlinks.json`, APK storage).

### User Story 2: Two clearly separated environments (Priority: P1)

As Jordy, I want exactly two environments, local and prod, so that I always know which config is in effect and local defaults can never leak into prod.

**Acceptance Scenarios**:
1. **Given** the backend, **When** it starts without an active profile, **Then** it refuses to start with a clear message (fail fast). `local` and `prod` are the only valid profiles.
2. **Given** the shared config, **When** I look at it, **Then** it holds no localhost URLs, dev CORS origins or credentials. Those live only in the `local` profile.
3. **Given** local development, **When** I run the documented commands, **Then** Postgres and Keycloak run in Podman, and the backend and frontend run the same way as today.
4. **Given** prod, **When** the app runs, **Then** Keycloak runs in production mode with a prod realm (no dev user, no localhost redirects).
5. **Given** the repo, **When** anyone reads the environment docs, **Then** one table lists every setting that differs between local and prod, and where each value comes from.

### User Story 3: Secrets are injected, never committed (Priority: P1)

**Acceptance Scenarios**:
1. **Given** a secret (API keys, DB and Keycloak credentials, client secrets, backup keys), **When** prod runs, **Then** the value comes from a SOPS-encrypted file in git, decrypted by the deploy pipeline with an age key that only CI and Jordy hold.
2. **Given** a secret is rotated (edited with `sops`, committed, deployed), **When** the pod restarts, **Then** the new value is used.
3. **Given** the encrypted secrets file in the public repo, **When** someone without the age private key reads it, **Then** they only see key names and ciphertext.
4. **Given** git history, the published container images and the ConfigMaps, **When** they're scanned, **Then** no plaintext secret values are found. CI runs a secret scan on every push.
5. **Given** local development, **When** I run the app, **Then** secrets come from the gitignored `.env`, as today.

### User Story 4: One-click, approved deployments with rollback (Priority: P1)

**Acceptance Scenarios**:
1. **Given** a push to `main`, **When** CI runs, **Then** it builds and tests the backend, frontend and Keycloak images and publishes them tagged with the commit SHA.
2. **Given** built images, **When** I approve the production deployment in GitHub, **Then** the cluster is updated to exactly those images, and the pipeline waits until the rollout is healthy, or fails loudly.
3. **Given** a bad release, **When** I trigger a rollback, **Then** the previous version is running again within 5 minutes.
4. **Given** the deploy credentials, **When** they're used, **Then** they can only change resources in the JordyLab namespace, not the whole cluster.

### User Story 5: Data is durable and restorable (Priority: P1)

**Acceptance Scenarios**:
1. **Given** the production database, **When** a day passes, **Then** a backup exists in OVH Object Storage, including continuous write-ahead logs.
2. **Given** a backup, **When** I follow the restore runbook, **Then** I can restore the database to a point in time into a fresh database, and the app works against it. This is tested at least once before go-live.
3. **Given** a pod restart or a VPS reboot, **When** it comes back, **Then** uploaded game artwork and database data are still there (node-local storage on the VPS disk).
4. **Given** the VPS is lost entirely, **When** I follow the runbook, **Then** a fresh VPS can be rebuilt from git + the SOPS files + the Object Storage backups.

### User Story 6: Learn Kubernetes and Podman on my own project (Priority: P2)

As someone who knows Kubernetes basics and is new to Podman, I want a learning guide built on JordyLab's own files, so that I understand and can operate what's running, not just copy it.

**Acceptance Scenarios**:
1. **Given** the learning guide, **When** I read it, **Then** each concept (container image, Pod, Deployment, Service, Gateway/HTTPRoute, ConfigMap, Secret, PVC/StorageClass, Namespace, probes, resources, operators/CRDs, RBAC) is explained with a link to the exact JordyLab file that uses it.
2. **Given** the k3s chapter, **When** I read it, **Then** I understand what k3s bundles (containerd, Traefik, CoreDNS, ServiceLB, local-path provisioner), how ServiceLB exposes ports on the VPS's own IP, how the bundled Traefik is customised, and what I now own as host operator (OS patching, firewall, k3s upgrades).
3. **Given** the Podman chapter, **When** I follow it, **Then** I understand Podman's actual role: my local dev container engine (replacing Docker for build, test and compose) and a learning bridge (`podman generate kube` / `podman kube play` to sandbox manifests before `kubectl apply`). It is **not** a cluster manager: the cluster is managed with kubectl (+ Helm), and k3s runs containers with containerd. The chapter also covers rootless containers, `podman machine` on macOS vs native on CachyOS, Containerfiles, and why prod images are built in CI (arm64 vs amd64).
4. **Given** the secrets chapter, **When** I follow it, **Then** I can create an age key, encrypt and edit a secret with SOPS, and explain why the private key must never be committed.
5. **Given** hands-on exercises, **When** I do them, **Then** I have built an image locally, sandboxed a manifest with `podman kube play`, port-forwarded to the Keycloak admin console, read pod logs, rotated a SOPS secret, and done a rollback on prod.
6. **Given** the runbook, **When** something happens (deploy, rollback, logs, DB restore, secret rotation, certificate issue, k3s/OS upgrade, VPS rebuild), **Then** there's a step-by-step procedure with the exact commands.

### User Story 8: The public code is readable but not reusable (Priority: P1)

As Jordy, I want the public repo and public images licensed "all rights reserved, viewing only", so that people can read my code but not use, copy, modify or redistribute it until I decide otherwise.

**Context**: The repo is already public, and four MIT licence files exist (`.claude/LICENSE`, `jordylab-be/LICENSE`, `jordylab-fe/LICENSE`, `garmin-sync-service/LICENSE`, since 2026-08-01). MIT permits reuse. As far as we know, versions already published under MIT stay MIT for whoever copied them. The change applies from the relicensing commit onward.

**Acceptance Scenarios**:
1. **Given** the repo, **When** anyone looks for a licence, **Then** there is exactly one root `LICENSE` stating the source is published for viewing only, all rights reserved, with the copyright holder (Jordy Swinnen) and year. All four MIT files are removed.
2. **Given** package manifests (`package.json`, Gradle/`pyproject.toml` metadata, etc.), **When** they declare a licence, **Then** it matches (e.g. `"license": "UNLICENSED"` / "All rights reserved"), with no leftover MIT declarations.
3. **Given** the README, **When** someone reads it, **Then** a short "Licence" section explains the terms in plain words and that permission requests go to Jordy.
4. **Given** the published container images, **When** they're inspected, **Then** their `org.opencontainers.image.licenses` label matches the repo licence (no MIT).
5. **Given** third-party code in the repo (e.g. copied snippets or vendored files), **When** it keeps its own licence, **Then** that licence is preserved and not overwritten.

### User Story 7: First-time setup from zero (Priority: P2)

**Acceptance Scenarios**:
1. **Given** an empty OVH account, **When** I follow the bootstrap guide, **Then** I end with: a domain + DNS, a hardened VPS-2 (SSH keys only, firewall, automatic security updates), k3s installed at a pinned version with its bundled Traefik customised, cert-manager + CloudNativePG installed, an Object Storage bucket, an age key + SOPS-encrypted secrets, and the first successful deploy.
2. **Given** the bootstrap, **When** it creates the age key, **Then** the guide explains where the private key lives (password manager + GitHub environment secret) and that losing it makes every prod secret unrecoverable.

### Edge Cases
- The VPS reboots (k3s or OS upgrade): there's short downtime, and everything comes back without manual steps. This is documented as accepted.
- The VPS disk or host is lost: the node-local volumes are gone, and recovery uses the rebuild runbook (git + SOPS + Object Storage backups).
- The certificate fails to renew: the runbook covers how to diagnose it. Monitoring is out of scope, but cert-manager status is checked in the runbook.
- A Flyway migration fails during rollout: the new pod never becomes ready, the old version keeps serving (or, with Recreate, the runbook explains recovery), and the pipeline fails.
- The age private key is lost: prod secrets can't be decrypted. The offline copy in the password manager is required, and the runbook covers re-keying.
- The disk fills up (images + volumes on 75 GB): the runbook has check and cleanup commands (`k3s crictl rmi --prune`, volume sizes).
- An image built on the MacBook (arm64) is pushed by mistake: CI is the only path to prod, and the manifests reference CI-built SHA tags only.
- The domain's DNS isn't propagated yet: the certificate isn't issued, and the bootstrap guide covers waiting and verifying.
- Someone pulls the public images: they must contain no secrets or prod config values that are secret.

---

## Requirements

### Functional: Environments
- **FR-001**: There MUST be exactly two runtime environments, `local` and `prod`, expressed as Spring profiles, Angular build configurations and Keycloak modes.
- **FR-002**: Shared configuration MUST contain no environment-specific hosts, origins or credentials. Starting without a valid profile MUST fail fast.
- **FR-003**: A single document MUST list every setting that differs between the environments and where its value comes from.

### Functional: Packaging & delivery
- **FR-004**: The backend, frontend and Keycloak (including the jordylab theme) MUST each be packaged as a container image, built reproducibly in CI for linux/amd64.
- **FR-005**: Images MUST be tagged with the commit SHA. Prod MUST only run SHA-tagged images, never `latest`.
- **FR-006**: Deployment to prod MUST require manual approval, MUST wait for a healthy rollout, and MUST support rollback to the previous version.
- **FR-007**: CI deploy credentials MUST be limited to the JordyLab namespace.

### Functional: Exposure & security
- **FR-008**: One public domain with automatic TLS MUST serve `/` (web), `/api` (backend), `/auth` (Keycloak public endpoints only) and the mobile App Links file.
- **FR-009**: The Keycloak admin console, metrics, health and management ports MUST NOT be publicly reachable.
- **FR-010**: Every workload MUST declare readiness/liveness probes and resource requests/limits.
- **FR-011**: Prod secrets MUST be stored in git only as SOPS + age ciphertext and decrypted by the deploy pipeline. The age private key MUST exist only in the GitHub `production` environment and Jordy's offline store.
- **FR-012**: CI MUST scan for committed secrets and fail on findings.

### Functional: Data
- **FR-013**: PostgreSQL with pgvector MUST run in the cluster on node-local storage, with continuous backups to OVH Object Storage and point-in-time restore.
- **FR-014**: A restore drill MUST be performed and documented before go-live.
- **FR-015**: Game artwork (and 007 APK files) MUST be stored on persistent storage (k3s local-path) that survives pod restarts and VPS reboots.

### Functional: Education & operations
- **FR-016**: A learning guide MUST explain Kubernetes and Podman concepts using JordyLab's own files, with hands-on exercises.
- **FR-017**: A runbook MUST cover bootstrap (VPS hardening + k3s install), deploy, rollback, logs, DB backup/restore, secret rotation, certificate troubleshooting, k3s/OS upgrades and full VPS rebuild.
- **FR-020**: The VPS MUST be hardened: SSH key-only access, no root password login, automatic security updates, and a firewall exposing only SSH (restricted), 80 and 443. Kubelet and flannel ports MUST NOT be public. Kubernetes API (6443) exposure follows the clarify decision on how CI reaches the cluster.
- **FR-019**: The repository MUST carry a single root `LICENSE` with "all rights reserved, source available for viewing only" terms, replacing all four MIT licence files; package manifests, the README and image labels MUST match, and third-party licences MUST be preserved. This MUST land before go-live.
- **FR-018**: AGENTS.md MUST be updated: Hetzner/Compose/Watchtower/Ollama prod references replaced with the k3s-on-OVH-VPS setup.

### Key Entities (deployment artifacts, not domain data)
- Container images (backend, frontend, keycloak), K8s manifests (base + prod overlay), host + k3s configuration (Traefik HelmChartConfig, firewall), cluster add-on configuration, SOPS-encrypted secret files + `.sops.yaml`, database cluster definition, CI workflows, docs (learning guide, runbook, environments table).

---

## Success Criteria
- **SC-001**: A friend on mobile data can open `https://<domain>`, log in and use the Game Catalog.
- **SC-002**: From an approved deploy to a healthy new version takes under 10 minutes; a rollback takes under 5 minutes.
- **SC-003**: Zero secret values in git, images or ConfigMaps (CI secret scan green; manual image inspection in the runbook).
- **SC-004**: A database restore to a point in time succeeds in a drill, in under 30 minutes by following the runbook.
- **SC-005**: Monthly infrastructure cost stays at or under about €13 excl. VAT (VPS-2 + Object Storage + domain), excluding AI usage.
- **SC-006**: Jordy can explain and do every runbook procedure alone after working through the learning guide.
- **SC-008**: No MIT licence file or MIT licence declaration remains in the repo (excluding third-party files), and a CI check (e.g. a grep in build.yml) keeps it that way.
- **SC-007**: A public port scan of the VPS shows only 80, 443 and (restricted) SSH, plus 6443 only if clarify chose that. `/auth/admin` is not reachable.

---

## Assumptions
- One OVH **VPS-2** (4 vCores / 8 GB / 75 GB NVMe, from €7.21/mo excl. VAT on a 12-month term; confirm the renewal price) running single-node **k3s**. Short downtime during maintenance is accepted. VPS-3 is the upgrade path if memory runs short.
- Licensing is "all rights reserved, viewing only" for now; Jordy may loosen it later (e.g. PolyForm Noncommercial, Business Source License or MIT). This isn't legal advice; check with a lawyer if it becomes commercially relevant.
- The domain is bought at OVHcloud. The repo and GHCR images are public, so images contain nothing secret.
- Ingress uses k3s's **bundled Traefik v3** (Gateway API enabled via `HelmChartConfig`; ingress-nginx is retired). Exposure is through k3s **ServiceLB** on the VPS's own public IP, with no cloud load balancer. Certificates come from Let's Encrypt via cert-manager.
- Storage uses k3s's default **local-path** provisioner (single node; no network block storage).
- Local development keeps Podman + compose. There's no staging environment.
- Out of scope: multi-node HA, autoscaling, Terraform/IaC for the VPS (possible later), central logging/metrics dashboards, the Garmin sync service (no code yet), managed Kubernetes (MKS) and managed secret stores.
