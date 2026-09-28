# Tasks: Production Deployment on Self-Managed k3s (OVH VPS)

**Input**: Design documents from `/specs/008-ovh-k8s-deployment/` (spec.md, plan.md, research.md, data-model.md,
contracts/, quickstart.md)

**Tests**: Only one automated test is explicitly required by the spec (FR-002's fail-fast-without-profile check,
under US2). No other test tasks are added — this feature is validated primarily through `quickstart.md` and the
runbook, since there is no staging environment.

**Organization**: Tasks are grouped by user story (spec.md's US1–US8) to enable independent implementation and
testing of each story.

## ⚠️ Operator-executed steps — read before starting

Per the feature's own constraints (`specs/_drafts/008-deployment/speckit-prompts.md` §3): **anything that creates a
billable OVH resource, runs a command on the live VPS, changes DNS, or generates/stores a real key or credential is
Jordy's to execute himself from the runbook — never something `/speckit-implement` or an agent does.** Tasks below
that touch this boundary are marked **(operator step)** — their deliverable is the *written, exact-commands
runbook section*, not running those commands. Do not provision the VPS, install k3s, create the Object Storage
bucket, generate the age keypair, or create GitHub environment secrets as part of implementing these tasks.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: Which user story this task belongs to (omitted for Setup/Foundational/Polish)

## Path Conventions

Per `plan.md`'s Project Structure: `deploy/` (new, root-level: containers/, host/, k8s/base/,
k8s/overlays/prod/, k8s/cluster/), `docs/` (new: learn/, runbook.md, environments.md),
`.github/workflows/` (new: build.yml, deploy-prod.yml), plus existing `jordylab-be/`, `jordylab-fe/`,
`garmin-sync-service/` for the small application-code and licence-metadata changes.

---

## Phase 1: Setup

**Purpose**: Scaffold the new directories and placeholder files this feature adds. Nothing here touches a live
server.

- [ ] T001 Create the `deploy/` directory skeleton: `deploy/containers/{backend,frontend,keycloak}/`,
  `deploy/host/`, `deploy/k8s/base/`, `deploy/k8s/overlays/prod/`, `deploy/k8s/cluster/`
- [ ] T002 [P] Create the `docs/` skeleton: `docs/learn/`, a stub `docs/runbook.md` with section headings matching
  FR-018's list (bootstrap, deploy, rollback, logs, backup/restore, secret rotation, certificate troubleshooting,
  k3s/OS upgrades, full VPS rebuild), and a stub `docs/environments.md`
- [ ] T003 [P] Create stub `.github/workflows/build.yml` and `.github/workflows/deploy-prod.yml` (name + trigger
  only, no jobs yet)
- [ ] T004 [P] Create `.sops.yaml` at the repo root with the `creation_rules` shape from `research.md` §7
  (`encrypted_regex: ^(data|stringData)$`), age public key left as a `# TODO: Jordy's age public key` placeholder —
  do not generate a real keypair
- [ ] T005 [P] Create `deploy/k8s/base/kustomization.yaml` declaring the `jordylab` Namespace, with an empty
  resource list to be filled in by later phases

**Checkpoint**: Directory structure exists; no live infrastructure touched yet.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The environment split and container images that every later phase assumes already exist. Per the
feature's own phase-order note, this and Setup are the only phases that need no live server.

**⚠️ CRITICAL**: No user story phase below can be meaningfully completed until this phase is done.

- [ ] T006 [P] Split `jordylab-be/src/main/resources/application.yaml`: move every `localhost`, dev-Keycloak, and
  dev-CORS value into a new `jordylab-be/src/main/resources/application-local.yaml`, leaving `application.yaml`
  free of environment-specific values (FR-002)
- [ ] T007 [P] Create `jordylab-be/src/main/resources/application-prod.yaml` with every value sourced from an
  environment variable — no literal hosts, origins, or credentials (FR-002)
- [ ] T008 Implement a backend startup check that fails fast with a clear error message when neither `local` nor
  `prod` is the active Spring profile (FR-002; depends on T006/T007 defining what "valid profile" means)
- [ ] T009 [P] Create `jordylab-fe`'s production environment file pointing at the real domain (placeholder value
  until the domain is bought) and confirm the existing local environment file is untouched (FR-001)
- [ ] T010 [P] Create `deploy/containers/backend/Containerfile`: multi-stage Java 25/Gradle build producing a
  `linux/amd64` runtime image
- [ ] T011 [P] Create `deploy/containers/frontend/Containerfile`: multi-stage Bun build running
  `nx build jordylab --configuration=production`, final stage on `nginx/docker-nginx-unprivileged:1.29-alpine`
  (`research.md` §10) with SPA fallback and security headers
- [ ] T012 [P] Create `deploy/containers/keycloak/Containerfile`: based on `quay.io/keycloak/keycloak:26.7.4`
  (`research.md` §9), running `kc.sh build` with the `jordylab` login theme baked in
- [ ] T013 Add base `Deployment`/`Service` manifests for backend, frontend, and Keycloak in `deploy/k8s/base/`,
  each with readiness/liveness probes and resource requests/limits (FR-010), referencing the three images above

**Checkpoint**: Environment split, images, and base workload manifests exist. User story phases can begin.

---

## Phase 3: User Story 1 - JordyLab is live on a public HTTPS domain (Priority: P1) 🎯

**Goal**: One public domain, with automatic TLS, serves `/` (web), `/api` (backend), `/auth` (Keycloak public
endpoints), and supports the mobile app and JordyBox scanner (spec.md US1).

**Independent Test**: Open `https://<domain>` from a phone on mobile data, log in, open the Game Catalog, and
confirm the certificate is valid (once the cluster from Phase 10/US8 is bootstrapped at least once).

- [ ] T014 [P] [US1] Create `Gateway` + `HTTPRoute` manifests in `deploy/k8s/base/` for the public routes table in
  `contracts/http-routing.md` (`/`, `/api/**`, `/auth/realms|resources|.well-known`,
  `/.well-known/assetlinks.json`)
- [ ] T015 [US1] Create `deploy/host/traefik-helmchartconfig.yaml` (a `HelmChartConfig` enabling
  `providers.kubernetesGateway.enabled` and `gateway.enabled`, per `research.md` §2) — this file is authored here;
  dropping it onto the live VPS is the **(operator step)** documented in T063
- [ ] T016 [US1] Create `deploy/k8s/cluster/cert-manager-clusterissuer.yaml`: a Let's Encrypt `ClusterIssuer` using
  the `gatewayHTTPRoute` HTTP-01 solver (`research.md` §3)
- [ ] T017 [US1] Configure the Keycloak container's start command (in the Containerfile or an entrypoint script)
  with `--hostname=https://<domain>/auth`, `--http-relative-path=/auth`, `--proxy-headers=xforwarded`
  (`research.md` §9); confirm no `HTTPRoute` ever references `/auth/admin` or port `9000`
  (`contracts/http-routing.md`)
- [ ] T018 [P] [US1] Add CORS configuration for the mobile app origin and implement the
  `/.well-known/assetlinks.json` route in the backend (spec 007 dependency, US1 acceptance scenario 6)
- [ ] T019 [P] [US1] Create Deployment/Service/HTTPRoute manifests for the self-hosted `ntfy` instance in
  `deploy/k8s/base/` and `deploy/k8s/overlays/prod/` (FR-013, resolved in `/speckit-clarify`)
- [ ] T020 [US1] Create `deploy/k8s/overlays/prod/kustomization.yaml` wiring the real domain, image SHA tag
  placeholders, and prod `ConfigMap` values (FR-008; depends on T014/T016/T019's resources existing to reference)
- [ ] T021 [P] [US1] Write `docs/runbook.md`'s "DNS & first TLS issuance" section with exact steps and
  verification commands — **(operator step)**: Jordy points DNS at the VPS and verifies the certificate; do not
  change DNS yourself
- [ ] T022 [US1] Once the cluster is bootstrapped and a first deploy has run, execute the US1 checks from
  `quickstart.md` (curl checks, `/auth/admin` unreachability, the mobile-data browser test) and record the results
  in the runbook

**Checkpoint**: US1's manifests and code are complete; full verification needs Phase 10 (bootstrap) and Phase 6
(deploy pipeline) to have run at least once.

---

## Phase 4: User Story 2 - Two clearly separated environments (Priority: P1)

**Goal**: Exactly two environments, with a shared config that can never leak local defaults into prod (spec.md
US2).

**Independent Test**: Start the backend with no active profile and confirm it refuses to start; read
`docs/environments.md` and confirm it lists every differing setting. No live cluster needed.

- [ ] T023 [P] [US2] Add a backend test asserting startup fails fast with a clear error when no Spring profile is
  active (JUnit 5 + AssertJ, per the constitution's Testing Discipline; verifies T008)
- [ ] T024 [P] [US2] Write `docs/environments.md`: one row per setting that differs between `local` and `prod`,
  and where its value comes from (FR-003)
- [ ] T025 [US2] Audit `application.yaml` (backend) and the Angular environment files (frontend) to confirm zero
  localhost URLs, dev CORS origins, or credentials remain outside the `local`-only files (FR-002)

**Checkpoint**: US2 is fully testable independently, with no server required.

---

## Phase 5: User Story 3 - Secrets are injected, never committed (Priority: P1)

**Goal**: Every prod secret comes from a SOPS+age-encrypted file, never plaintext, in git, images, or ConfigMaps
(spec.md US3).

**Independent Test**: Inspect `secrets.sops.yaml` in the repo without the age private key and confirm only key
names and ciphertext are visible; confirm CI's secret scan runs on every push.

- [ ] T026 [P] [US3] Create `deploy/k8s/overlays/prod/secrets.sops.yaml` with the key structure from
  `contracts/secrets-schema.md`, values left as empty placeholders — Jordy fills in and encrypts the real values
  himself; never commit a real secret from this task
- [ ] T027 [P] [US3] Add a `gitleaks/gitleaks-action@v3` secret-scan step to `.github/workflows/build.yml`
  (`research.md` §11), failing the build on any finding (FR-012)
- [ ] T028 [P] [US3] Write `docs/runbook.md`'s "Generate the age key" section: the exact `age-keygen` command,
  where the public key goes (`.sops.yaml`), and where the private key must live (password manager + the GitHub
  `production` environment secret) — **(operator step)**: do not generate or store a real key yourself
- [ ] T029 [P] [US3] Write `docs/runbook.md`'s "Rotate a secret" section: the exact `sops` edit → commit → deploy →
  pod-restart sequence
- [ ] T030 [US3] Write `docs/runbook.md`'s "Age key lost" recovery section (re-keying procedure, per spec.md's
  edge case)

**Checkpoint**: Secrets structure and CI scanning exist; the runbook has every documented procedure Jordy needs to
actually create and manage the real key.

---

## Phase 6: User Story 4 - One-click, approved deployments with rollback (Priority: P1)

**Goal**: Every prod deployment is CI-built, gated behind manual approval, health-checked, and reversible (spec.md
US4).

**Independent Test**: Push to `main`, approve the deploy, confirm the rollout is healthy, then roll back and
confirm the previous version returns.

- [ ] T031 [US4] Implement `.github/workflows/build.yml`: run tests, then (after T027's gitleaks step passes)
  build and push the three images to `ghcr.io/jordy-swinnen/jordylab-*` tagged by commit SHA, using
  `docker/login-action@v4`, `docker/build-push-action@v7`, `docker/metadata-action@v6` (`research.md` §11)
- [ ] T032 [P] [US4] Create `deploy/k8s/cluster/ci-deploy-rbac.yaml`: a namespace-scoped `ServiceAccount` + `Role`
  + `RoleBinding` limited to the `jordylab` namespace (FR-007) — manifest only; minting and storing the resulting
  token is the **(operator step)** in T034
- [ ] T033 [US4] Implement `.github/workflows/deploy-prod.yml`: require the `production` GitHub Environment's
  approval, join Tailscale (`tailscale/github-action@v4`, `research.md` §8), decrypt `secrets.sops.yaml`,
  `kustomize edit set image`, `kubectl apply -k deploy/k8s/overlays/prod`, then `kubectl rollout status` with a
  hard failure on timeout (FR-006)
- [ ] T034 [P] [US4] Write `docs/runbook.md`'s "First-time CI access setup" section: creating the GitHub
  `production` environment with Jordy as the required reviewer, minting the namespace-scoped ServiceAccount token
  from T032, and adding it plus a Tailscale auth key as GitHub environment secrets — **(operator step)**: do not
  create these yourself
- [ ] T035 [US4] Write `docs/runbook.md`'s "Deploy" and "Rollback" sections with the exact commands (push → approve
  → verify rollout; `kubectl rollout undo` or re-run with the previous SHA)

**Checkpoint**: The deploy pipeline exists; its first real run and RBAC/environment setup are Jordy's per T034.

---

## Phase 7: User Story 5 - Data is durable and restorable (Priority: P1)

**Goal**: The database and game artwork survive restarts and are restorable from off-site backups (spec.md US5).

**Independent Test**: Follow the restore runbook to restore the prod database to a point in time into a fresh
database, and confirm the app works against it.

- [ ] T036 [P] [US5] Create `deploy/k8s/cluster/cnpg-cluster.yaml`: a single-instance PostgreSQL 16 `Cluster` on
  the `local-path` `StorageClass`, with the pgvector `ImageVolume` extension (`research.md` §6), including the
  `keycloak` schema
- [ ] T037 [P] [US5] Create `deploy/k8s/cluster/barman-cloud-plugin-values.yaml` (Helm values for the Barman Cloud
  Plugin, `research.md` §6) and `deploy/k8s/cluster/ovh-object-storage.yaml` (an `ObjectStore` CRD referencing the
  S3 keys from `contracts/secrets-schema.md`)
- [ ] T038 [US5] Configure the backup/`ScheduledBackup` retention policy for 7 daily + 4 weekly snapshots (per
  `/speckit-clarify`, FR-014; depends on T037's `ObjectStore` existing)
- [ ] T039 [P] [US5] Create PVC manifests for game artwork and 007's APK storage on the `local-path`
  `StorageClass` in `deploy/k8s/base/` (FR-016)
- [ ] T040 [P] [US5] Write `docs/runbook.md`'s "OVH Object Storage bucket" section — **(operator step)**: Jordy
  creates the bucket and generates the S3 keys himself; this is a billable resource, do not create it yourself
- [ ] T041 [US5] Write `docs/runbook.md`'s "Restore drill" section with the exact point-in-time-restore commands
  into a fresh `Cluster`, to run once before go-live and quarterly after (FR-015, `/speckit-clarify`)
- [ ] T042 [P] [US5] Write `docs/runbook.md`'s "Full VPS rebuild" section (git + SOPS files + Object Storage
  backups → a fresh cluster)

**Checkpoint**: Database and storage manifests exist; the bucket, first backup, and restore drill are Jordy's to
run per T040/T041.

---

## Phase 8: User Story 6 - Learn Kubernetes and Podman (Priority: P2)

**Goal**: A learning guide explains every concept used, pointing at JordyLab's own files, with hands-on exercises
(spec.md US6).

**Independent Test**: Read each chapter and confirm it links to the real file it describes.

- [ ] T043 [P] [US6] Write `docs/learn/01-containers-and-images.md` (images, Containerfiles — links to
  `deploy/containers/*` from T010–T012)
- [ ] T044 [P] [US6] Write `docs/learn/02-podman.md` (rootless Podman, `podman machine` on macOS vs native Linux,
  `podman compose`, `podman generate kube`/`podman kube play` as a learning bridge — never a cluster manager, per
  `research.md`'s carried-forward Podman decision)
- [ ] T045 [P] [US6] Write `docs/learn/03-k3s-and-bundled-components.md` (containerd, Traefik, CoreDNS, ServiceLB,
  local-path — what k3s bundles and what Jordy now owns as host operator, `research.md` §1–5)
- [ ] T046 [P] [US6] Write `docs/learn/04-workloads.md` (Pod, Deployment, Service — links to `deploy/k8s/base/`
  from T013)
- [ ] T047 [P] [US6] Write `docs/learn/05-gateway-api-and-tls.md` (Gateway/HTTPRoute, cert-manager — links to the
  T014/T016 manifests)
- [ ] T048 [P] [US6] Write `docs/learn/06-config-and-secrets.md` (ConfigMap, Secret, SOPS+age — links to
  `.sops.yaml` and `secrets.sops.yaml` from T004/T026)
- [ ] T049 [P] [US6] Write `docs/learn/07-storage.md` (PVC/StorageClass/local-path — links to T039's PVCs)
- [ ] T050 [P] [US6] Write `docs/learn/08-operators-and-crds.md` (the CloudNativePG operator, the Barman Cloud
  Plugin, ImageVolume extensions — links to T036/T037)
- [ ] T051 [P] [US6] Write `docs/learn/09-probes-resources-rbac.md` (readiness/liveness probes, resource limits,
  the `ci-deploy-rbac.yaml` from T032)
- [ ] T052 [P] [US6] Write `docs/learn/10-cicd.md` (the `build.yml`/`deploy-prod.yml` workflows from T031/T033)
- [ ] T053 [US6] Write `docs/learn/README.md` indexing all chapters and listing the hands-on exercises (build an
  image locally, sandbox a manifest with `podman kube play`, port-forward to the Keycloak admin console, read pod
  logs, rotate a SOPS secret, perform a rollback on prod)

**Checkpoint**: The learning guide is complete and cross-links every other phase's files.

---

## Phase 9: User Story 7 - The public code is readable but not reusable (Priority: P1)

**Goal**: The repo and its public images carry one all-rights-reserved licence, replacing the four MIT files
(spec.md US7).

**Independent Test**: Search the repo for a licence and confirm exactly one root `LICENSE` exists with the correct
terms; inspect a published image's OCI licence label.

- [ ] T054 [US7] Draft the root `LICENSE` text (all rights reserved, source available for viewing only, copyright
  Jordy Swinnen) and present it for approval **before committing** — do not finalize without Jordy's sign-off
- [ ] T055 [US7] Remove the four MIT `LICENSE` files (`.claude/LICENSE`, `jordylab-be/LICENSE`,
  `jordylab-fe/LICENSE`, `garmin-sync-service/LICENSE`) once T054 is approved
- [ ] T056 [P] [US7] Update `jordylab-fe/package.json` (and any other `package.json`) `license` field to
  `"UNLICENSED"`
- [ ] T057 [P] [US7] Update `jordylab-be`'s Gradle build metadata and `garmin-sync-service`'s `pyproject.toml`
  licence declarations to match
- [ ] T058 [P] [US7] Add a "Licence" section to the root README explaining the terms in plain language and that
  permission requests go to Jordy
- [ ] T059 [P] [US7] Add the `org.opencontainers.image.licenses` OCI label matching the new licence to all three
  Containerfiles (T010–T012)
- [ ] T060 [US7] Add a CI check to `.github/workflows/build.yml` that greps for MIT licence text/declarations and
  fails the build if any is found (SC-007)

**Checkpoint**: No MIT reference remains anywhere in the repo, manifests, or images; CI enforces it going forward.
This MUST land before go-live (FR-020).

---

## Phase 10: User Story 8 - First-time setup from zero (Priority: P2)

**Goal**: A single bootstrap guide takes an empty OVH account to a working, deployed cluster (spec.md US8). Every
task in this phase is a **(operator step)** — the deliverable is the written procedure, never its execution.

**Independent Test**: Follow the bootstrap guide start to finish and confirm each listed end-state item is
reached.

- [ ] T061 [US8] Write `docs/runbook.md`'s "Order the VPS" section (OVH VPS-2, monthly/no-commitment term per
  `/speckit-clarify`; the datacenter/region choice is left open per spec.md's Assumptions) — **(operator step)**:
  billable resource, do not order it yourself
- [ ] T062 [US8] Write `docs/runbook.md`'s "Harden the OS" section (SSH key-only access, disable root/password
  login, unattended security upgrades, firewall: 22 restricted, 80/443 open, 10250 and 8472/udp closed) —
  **(operator step)**: do not run these commands yourself
- [ ] T063 [US8] Write `docs/runbook.md`'s "Install k3s" section (pinned `INSTALL_K3S_VERSION=v1.37.0+k3s1` per
  `research.md` §1, copying the kubeconfig, verifying the bundled components, applying T015's
  `HelmChartConfig`) — **(operator step)**: do not run these commands yourself
- [ ] T064 [US8] Write `docs/runbook.md`'s "Install cluster add-ons" section (cert-manager, the CloudNativePG
  operator + Barman Cloud Plugin, at the pinned versions in `research.md`) — **(operator step)**: do not run these
  commands yourself
- [ ] T065 [P] [US8] Write `docs/runbook.md`'s "k3s / OS upgrades" section (FR-018)
- [ ] T066 [P] [US8] Update `AGENTS.md`'s Infrastructure section, replacing the Hetzner/Compose/Watchtower/Ollama
  production description with the k3s-on-OVH-VPS setup (FR-021)

**Checkpoint**: The bootstrap runbook is complete; Jordy can execute it once, start to finish, to reach a live
cluster — which is the prerequisite every earlier phase's "once bootstrapped" checkpoint refers to.

---

## Phase 11: Polish & Cross-Cutting Concerns

**Purpose**: Remaining runbook sections and a final end-to-end validation pass.

- [ ] T067 [P] Write `docs/runbook.md`'s "Certificate troubleshooting" section (diagnosing a failed renewal, per
  spec.md's edge case)
- [ ] T068 [P] Write `docs/runbook.md`'s "Disk cleanup" section (`k3s crictl rmi --prune`, checking `local-path`
  volume sizes, per spec.md's edge case)
- [ ] T069 [P] Write `docs/runbook.md`'s "Logs" section (exact `kubectl logs` commands for each workload)
- [ ] T070 Cross-check every runbook procedure against `docs/learn/`'s chapters for consistent terminology
- [ ] T071 Run the full `quickstart.md` validation pass end-to-end once bootstrap and a first deploy are complete;
  record the results
- [ ] T072 Final security review: confirm a public port scan matches FR-019/SC-008 exactly (only 22/80/443 public,
  6443 never public)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately, no server needed.
- **Foundational (Phase 2)**: Depends on Setup. Still no server needed (matches the feature's own note that the
  environment split and Containerfiles come first). **Blocks all user story phases.**
- **User Story phases (3–10)**: All depend on Foundational. Unlike a typical app feature, full end-to-end
  verification of US1, US4, and US5 genuinely needs a live cluster — that dependency is Phase 10 (US8's bootstrap),
  not an earlier-numbered phase, since US8 documents but doesn't gate authoring the manifests those phases add.
  Concretely:
  - **US2, US3, US7** are fully testable with no live cluster — their manifests/docs/code can be finished, and
    their Independent Tests run, right after Foundational.
  - **US1, US5** can have all manifests authored right after Foundational, but their Independent Test needs Phase
    10's bootstrap to have happened at least once.
  - **US4**'s pipeline can be written right after Foundational, but needs US1's and US5's manifests (something to
    deploy) plus US3's secrets structure to actually run end-to-end, and needs Phase 10's bootstrap for a real
    cluster to deploy to.
  - **US6** (the learning guide) links to files from every other phase, so its chapters are best finished last,
    even though nothing stops drafting them earlier.
  - **US8** (bootstrap) is written independently of the other phases' content, but its "Install cluster add-ons"
    and "Install k3s" sections reference artifacts from US1 (T015's HelmChartConfig) and US5 (the CNPG/Barman
    versions) — so finish those phases' manifest-authoring tasks first, even though executing the runbook is
    entirely Jordy's separate action.
- **Polish (Phase 11)**: Depends on all P1 stories (US1–US5, US7) and US8 being complete.

### Parallel Opportunities

- All Setup tasks except T001 can run in parallel (T002–T005).
- Within Foundational, T006/T007/T009/T010/T011/T012 touch independent files and can run in parallel; T008 and
  T013 each depend on earlier tasks in the same phase.
- Once Foundational is done, **US2, US3, and US7 can be worked entirely in parallel** with each other and with the
  manifest-authoring parts of US1/US5 — none of them share files.
- Within US6, every chapter (T043–T052) is an independent file and can be written in parallel.
- Within US7, T056–T059 are independent files and can run in parallel once T054/T055 (the licence text itself and
  removing the old files) are done.

---

## Parallel Example: User Story 2 (fully parallel, no server needed)

```bash
Task: "Add a backend test asserting startup fails fast with no active profile"
Task: "Write docs/environments.md listing every differing setting"
```

---

## Implementation Strategy

### Not a single-story MVP — this feature's P1 stories are interdependent infrastructure

Unlike a typical application feature, no single P1 user story here is a meaningful "ship it" MVP on its own: US1
(live domain) has nothing to serve without US4 (a deploy pipeline) and US5 (a database); US4 has nothing to deploy
without US1's manifests; and none of it survives a restart without US5's backups. The closest thing to an MVP
sequence is:

1. **Setup + Foundational** — no server needed, do this first.
2. **US2, US3, US7 in parallel** — also no server needed; get the environment split, secrets structure, and
   licensing gate in place before anything goes near a live cluster.
3. **US8's bootstrap, run once by Jordy** (all of Phase 10's runbook sections must be written first) — this is the
   point where a live cluster first exists.
4. **US1 + US5 manifests, then US4's pipeline, deployed together** — the actual go-live moment; US1's Independent
   Test and US5's restore drill are verified here.
5. **US6 (learning guide)** — write throughout, but finish last since it links to every other phase's files.
6. **Polish** — remaining runbook sections and the final `quickstart.md` pass.

### Incremental Delivery within that constraint

- Everything serverless (Foundational, US2, US3, US7) can be built, reviewed, and merged well before Jordy spends
  any money on OVH.
- The manifest-authoring halves of US1, US4, US5 can also be built and reviewed before a VPS exists — only their
  *validation* waits on Jordy's bootstrap.
- This keeps the actual "run a command on a billable VPS" moment as late and as small as possible: by the time
  Jordy opens the runbook, almost everything it tells him to apply already exists, reviewed, in git.
