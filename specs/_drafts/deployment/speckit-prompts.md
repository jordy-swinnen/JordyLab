# Deployment (self-managed k3s on an OVH VPS-2): SpecKit Prompts

Background is in `research.md`. `spec-draft.md` shows the expected spec, and `plan-draft.md` the expected plan shape.

> **Numbering:** none. SpecKit assigns the next free number when this draft is specified, so drafts can be taken in any order.
> **Order:** deployment can be specified any time. Implementation phase 1 (the environment split) and phase 2 (Containerfiles) don't need a server. Go-live should come after Settings (roles/secrets). The mobile app needs deployment live to be tested on a real phone.

---

## 1. `/speckit-specify`

```
Short name: ovh-k8s-deployment.

Make JordyLab production-ready and deployed on a self-managed single-node k3s Kubernetes cluster running on one OVH VPS-2 (4 vCores / 8 GB RAM / 75 GB NVMe), reachable on one public HTTPS domain (bought at OVHcloud) for the web app, the mobile app mobile app and the JordyBox game-catalog scanner. OVH Managed Kubernetes was considered and rejected on cost (MKS nodes + a separately billed load balancer ≈ €40–55/month vs ≈ €8.50–13 for k3s on a VPS-2); keep that reasoning in research.md.

WHY: friends (the Settings spec guests) and my phone (the mobile app spec) need JordyLab on the internet. Today it only runs locally (Podman compose for Postgres + Keycloak, backend from the IDE, frontend via nx serve), there are no container images, and local defaults (localhost URLs, dev Keycloak) are mixed into the shared config.

ENVIRONMENTS: exactly two — local and prod. Nothing else. Shared config holds no hosts, origins or credentials; the app refuses to start without one of the two profiles. One document lists every setting that differs and where its value comes from. Local keeps Podman + compose + a gitignored .env.

PROD SHAPE: one domain with automatic TLS; / serves the web app, /api the backend, /auth only Keycloak's public login endpoints (admin console, metrics, health and management ports never public). Mobile app support: CORS for the app origin, /.well-known/assetlinks.json, APK file storage. Traffic reaches the cluster through k3s's built-in ServiceLB on the VPS's own public IP (no cloud load balancer) and k3s's bundled Traefik; storage is k3s's default local-path provisioner on the VPS disk. PostgreSQL with pgvector runs inside the cluster with continuous backups to OVH Object Storage and point-in-time restore; a restore drill must pass before go-live. Game artwork survives restarts. Every workload has health probes and resource limits.

SECRETS: prod secrets are encrypted with SOPS + age; only the encrypted file is committed to git, and the deploy pipeline decrypts it just before applying to the cluster. No plaintext secret in git, container images or config maps. The age private key lives only in the GitHub production environment and my password manager; losing it must be covered in the runbook. CI scans for committed secrets. The repo and container images are public, so images must contain nothing secret.

DELIVERY: CI builds and tests backend, frontend and Keycloak (with my login theme) images for linux/amd64, tagged by commit SHA, published to GitHub Container Registry. Deploying to prod needs my manual approval in GitHub, waits for a healthy rollout, and supports rollback to the previous version. CI's cluster credentials are limited to the JordyLab namespace.

EDUCATION (important): I know Kubernetes basics and I'm new to Podman. Deliver a learning guide that explains each concept used (images/Containerfiles, rootless Podman, podman machine on macOS vs native Linux, podman compose, podman kube play, Pod, Deployment, Service, Gateway/HTTPRoute, TLS/cert-manager, ConfigMap, Secret, SOPS + age, PVC/StorageClass/local-path, operators/CRDs, probes, resources, RBAC, CI/CD, and what k3s bundles — containerd, Traefik, CoreDNS, ServiceLB, local-path) by pointing at JordyLab's own files, plus hands-on exercises. Be precise about Podman's role: it is my local dev container engine (replacing Docker for build, test and compose) and a learning bridge (podman generate kube / podman kube play to sandbox manifests before kubectl apply) — not a cluster manager; the cluster is managed with kubectl (+ Helm) and k3s runs containers with containerd. Also a runbook with exact commands for: first-time bootstrap from an empty OVH account (order VPS, harden the OS, install k3s, customise bundled Traefik, install add-ons, DNS), deploy, rollback, logs, DB backup/restore, secret rotation, certificate troubleshooting, k3s/OS upgrades, full VPS rebuild.

LICENSING: the repo and images are public, but I don't want anyone using my work for now. Replace the four existing MIT licence files (.claude/LICENSE, jordylab-be/LICENSE, jordylab-fe/LICENSE, garmin-sync-service/LICENSE) with one root LICENSE: source published for viewing only, all rights reserved, copyright Jordy Swinnen. Align package manifests, the README (short plain-language licence section) and container image licence labels; keep any third-party licences intact; add a CI check that no MIT licence reappears. Must land before go-live. I may loosen this later.

HOST: I operate the VPS myself — SSH key-only access, automatic security updates, a firewall exposing only SSH (restricted), 80 and 443 (never the kubelet or flannel ports); how CI reaches the Kubernetes API is an open question for clarify.

CONSTRAINTS: cheapest sensible setup (one OVH VPS-2 from €7.21/month excl. VAT on a 12-month term — confirm the renewal price; target ≤ €13/month excluding AI usage); short downtime during reboots/upgrades is acceptable; VPS-3 is the upgrade path if memory runs short. Update AGENTS.md so it stops describing the old Hetzner/Compose/Watchtower/Ollama production setup.

Out of scope: staging/other environments, multi-node high availability, autoscaling, managed Kubernetes (MKS), managed secret stores, Terraform for the VPS, central logging/metrics dashboards, deploying the garmin-sync-service (no code yet).
```

---

## 2. `/speckit-clarify`: expected questions and suggested answers
1. Ntfy for mobile app notifications → deploy ntfy in the cluster (tiny).
2. Backup retention → 7 daily + 4 weekly, plus a restore drill before go-live and then quarterly.
3. Deploy trigger → build on every push to `main`; deploy only on manual approval.
4. VPS datacenter → the closest EU location offered at checkout, ideally the same region as the Object Storage bucket.
5. How CI reaches the k3s API (6443) → decide between: open 6443 with only a namespace-scoped token; firewalled 6443 + GitHub Actions joining WireGuard/Tailscale for the deploy step; or deploy over SSH. (Suggestion: firewalled + WireGuard/Tailscale, since you already know WireGuard.)
6. VPS term → 12-month upfront (cheapest) vs monthly; confirm the renewal price.
7. Keycloak admin console access → `kubectl port-forward` only.

---

## 3. `/speckit-plan`

```
Tech context for deployment (read AGENTS.md, the constitution, and specs/_drafts/deployment/research.md + plan-draft.md first; carry their findings into this feature's research.md; verify every version, Helm value and YAML field against live docs before committing — report instead of guessing on mismatches):

- Follow the repo layout and phase order in plan-draft.md (deploy/containers, deploy/host, deploy/k8s/{base,overlays/prod,cluster}, deploy/keycloak/realm-prod.json, .sops.yaml, docs/learn, docs/runbook.md, docs/environments.md, .github/workflows/{build,deploy-prod}.yml).
- Backend: Spring profiles `local` and `prod`; move all localhost/dev values out of application.yaml into application-local.yaml; application-prod.yaml driven by env vars; enable actuator readiness/liveness probe groups; forward-headers behind Traefik; fail fast when no valid profile is active (test it). Artwork dir on a PVC (storageClassName local-path); Deployment replicas 1 with Recreate.
- Frontend: production config with the real domain (placeholder until bought); Containerfile multi-stage Bun + `nx build jordylab --configuration=production` → nginx-unprivileged with SPA fallback and security headers.
- Keycloak: custom image (theme baked in, `kc.sh build` for postgres + health), `start --optimized`, hostname https://<domain>/auth, http-relative-path /auth, http-enabled, proxy-headers xforwarded; prod realm file without dev users or secrets (secrets via env placeholders if Keycloak 26 supports it — verify). Route only /auth/realms, /auth/resources, /auth/.well-known; never /auth/admin or port 9000.
- Host + k3s bring-up (runbook, I execute it): order the VPS-2; harden the OS (non-root sudo user, SSH keys only, unattended-upgrades, firewall: 22 restricted, 80/443 open, 10250 and 8472/udp closed, 6443 per clarify); install k3s at a pinned version via the official install script; copy the kubeconfig to my laptop; verify the bundled components (Traefik v3, CoreDNS, ServiceLB svc-* pods, local-path-provisioner); customise the bundled Traefik with a HelmChartConfig in /var/lib/rancher/k3s/server/manifests (Gateway API provider on, HTTP→HTTPS redirect) — do not install a second Traefik; exposure is via ServiceLB on the VPS public IP, no cloud load balancer. Include k3s + OS upgrade and full-VPS-rebuild procedures, and optional k3s datastore backup (server/db + server/token).
- Cluster add-ons via Helm, pinned versions: cert-manager with a Let's Encrypt ClusterIssuer (Gateway HTTP-01 against the bundled Traefik), CloudNativePG + Barman Cloud plugin → OVH Object Storage (S3). Do NOT use ingress-nginx (retired March 2026). No External Secrets Operator.
- Secrets: SOPS + age. age-keygen; public key in .sops.yaml (encrypted_regex for data/stringData); deploy/k8s/overlays/prod/secrets.sops.yaml committed encrypted; private key as a GitHub `production` environment secret + my offline copy; CI decrypts without writing plaintext to disk (sops -d | kubectl apply, or ksops — pick the simplest, justify). Document rotation and re-keying.
- Postgres: CNPG Cluster, 1 instance on StorageClass local-path, PG16 image with pgvector (verify), keycloak schema, backups + WAL archiving, documented and tested point-in-time restore.
- Kustomize: base + one prod overlay (domain, image SHA tags, config.env → ConfigMap). No `latest` tags.
- CI: build.yml (tests, gitleaks secret scan, build 3 images linux/amd64, push to ghcr.io/jordy-swinnen/jordylab-* tagged with SHA). deploy-prod.yml (GitHub Environment `production` with me as required reviewer; namespace-scoped ServiceAccount token as the only cluster credential — never the k3s admin kubeconfig; API access method per clarify; SOPS decrypt step; kustomize edit set image; kubectl apply -k; rollout status; documented rollback).
- Docs: the learning guide chapters and exercises listed in plan-draft.md, written for someone with basic k8s and no Podman experience, each concept linked to the real file; the runbook with exact commands (Fish-shell compatible — no bash heredocs); AGENTS.md infra section rewritten.
- Licensing (do this first — it's a small standalone commit): root LICENSE "all rights reserved, viewing only" replacing the 4 MIT files; package.json `"license": "UNLICENSED"` and matching Gradle/pyproject metadata; README licence section; OCI label org.opencontainers.image.licenses on all images; CI grep check against MIT. Don't touch third-party licence files. Show me the LICENSE text for approval before committing.
- Include Settings/mobile app hooks: secrets for OPENROUTER_API_KEY and the Keycloak service-account client secret; CORS for https://localhost; assetlinks.json route; APK storage.

Stop and report before: anything that creates billable OVH resources, running commands on the VPS, changing DNS, generating/storing any key or credential. I run those steps myself from the runbook.
```

---

## 4. Then `/speckit-tasks` → `/speckit-analyze` → `/speckit-implement`
