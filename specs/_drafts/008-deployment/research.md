# 008 Production Deployment: Self-Managed k3s on an OVH VPS: Research

Date: 2026-09-27, revised the same day. Checked against the repo and the live vendor docs. Prices come from ovhcloud.com (EUR, excl. VAT unless stated). Confirm them at checkout.

## Decisions so far (from Jordy)
- **Infrastructure:** **self-managed k3s on one OVH VPS-2**. This replaces OVH Managed Kubernetes (MKS); see §2 for why.
- **Domain:** bought at OVHcloud (e.g. `.be`).
- **Database:** PostgreSQL + pgvector **in the cluster** (CloudNativePG), with backups to **OVH Object Storage** (pay-per-use).
- **Images:** **GHCR**. The repo `jordy-swinnen/JordyLab` is public, so public images are free.
- **Secrets:** **SOPS + age**. Encrypted files are committed to git and decrypted in CI just before `kubectl apply`.
- **Deploy flow:** GitHub Actions builds, then deploys to prod after a manual **Approve**.
- Exactly **two environments: `local`** (your machines, Podman) **and `prod`** (k3s on the VPS).
- Education on Kubernetes, k3s and Podman is part of the deliverable.

## TL;DR: the target setup

```
Internet ─► VPS-2 public IPv4 :80/:443
             └─ k3s ServiceLB (hostPort) ─► bundled Traefik v3 ─┬─ /      → frontend (nginx, Angular build)
                TLS: cert-manager + Let's Encrypt               ├─ /api   → backend (Spring Boot)
                                                                ├─ /auth  → Keycloak (/admin not routed)
                                                                └─ /.well-known/assetlinks.json → backend (007)
Namespace "jordylab": backend ─► CloudNativePG (pgvector) on local-path storage ─► backups ─► OVH Object Storage (S3)
Secrets: *.sops.yaml in git ──(age key in GitHub Actions)──► decrypted in CI ─► kubectl apply ─► K8s Secrets
JordyBox scanner / phones (007) ──HTTPS──► same public domain
```

**Estimated cost: about €8.50–13 a month (excl. VAT)**

| Item | € / month |
|---|---|
| VPS-2: 4 vCores, 8 GB RAM, 75 GB NVMe, 1 Gbps, IPv4, anti-DDoS, daily backup of the last 24 h included | **from 7.21** (listed as "from"; the configurator uses a **12-month upfront** term). The price without commitment and the renewal price weren't shown, so **confirm at checkout** |
| OVH Object Storage (database backups, a few GB) | < 0.10 |
| Domain (.be, yearly cost spread over 12 months) | ≈ 1 |
| **Total** | **≈ 8.50** on the 12-month rate; budget **up to ≈ 13** in case the no-commitment or renewal rate is higher |

With 21% Belgian VAT, VPS-2 comes to about €8.72.

## 1. What's in the repo today (the gaps)

| Area | Today | Needed for prod |
|---|---|---|
| Container images | **None.** No Containerfile/Dockerfile for backend or frontend | 3 Containerfiles: backend, frontend (nginx), Keycloak (theme baked in) |
| Environments | `application.yaml` mixes local defaults (`localhost:5432`, `localhost:8180`, localhost CORS). Angular `environment.prod.ts` still points at `jordylab.example.com` | Spring profiles `local`/`prod`; shared config free of localhost; Angular prod on the real domain |
| Keycloak | `start-dev`, `KC_HOSTNAME=localhost`, realm import with a dev user and localhost redirects | `start --optimized` under `https://<domain>/auth`, proxy headers, prod realm without dev users or secrets, `/admin` not public |
| Secrets | `jordylab-be/.env` (gitignored, good) | SOPS-encrypted secret files in git, decrypted by CI |
| Game artwork | Local dir via `GAMECATALOG_ARTWORK_DIR` | PVC on k3s local-path storage |
| CI/CD | Only PR-review and hook-test workflows | Build + push images to GHCR; deploy with approval |
| Docs | AGENTS.md still says "Hetzner VPS + Compose + Watchtower" | Rewrite the infra section for k3s on OVH VPS; learning guide |
| `garmin-sync-service` | No code | **Out of scope** |
| Ntfy (007 notifications) | Not in the repo | Clarify: run ntfy in the cluster or use ntfy.sh |

## 2. Why MKS was rejected (kept for the record)
- **Cost.** MKS needs OVH Public Cloud instances as nodes, plus a separately billed Public Cloud Load Balancer:
  - Control plane: €0 on the Free plan.
  - Load Balancer S: €6.06/mo.
  - One 8 GB node (B3-8): about €32 on a 12-month plan, about €37 billed hourly.
  - Storage: a few euros.
  - Total: **about €42–47 a month**, versus about €8.50–13 for k3s on a VPS-2.
- **The node minimum isn't €24.** Discovery flavours D2-4 (4 GB, €11.44/mo) and D2-8 *are* supported as MKS nodes, so the cheapest node is lower than that. But 4 GB is too small for this stack (see §3), and with 8 GB the total still lands at about €40 or more once the LB is included.
- **The Load Balancer.** The old "Load Balancer for MKS" (IOLB) is **deprecated**. OVH's migration guide says it is deprecated for clusters on Kubernetes versions above 1.31, and that upgrades are blocked while IOLB services remain. New clusters use the separately billed Public Cloud Load Balancer (Octavia). I found no exact end-of-life date in OVH's docs, so the "EOL July 2026" figure is unverified.
- **Learning value** is the same with k3s: the same `kubectl`, Deployments, Services, Ingress/Gateway, Helm and operators. It's also **higher**: you learn the node/OS layer too, which MKS hides.
- **What you give up:** OVH's managed control plane (99.5% SLA), automatic Kubernetes upgrades, and managed block storage. You now own OS patching, k3s upgrades and host security (§5).

## 3. Sizing: why VPS-2 (8 GB), not VPS-1 (4 GB)
- k3s server requirements are at least 2 cores and 2 GB RAM. On one node that's the control plane plus kubelet plus containerd, roughly 0.5–0.75 GB.
- Bundled system pods add a few hundred MB: Traefik, CoreDNS, ServiceLB, local-path-provisioner and metrics-server.
- On top of that come cert-manager and the CloudNativePG operator. Then the workloads:
  - Postgres + pgvector: about 1 GB.
  - The Spring Boot monolith (Modulith + Spring AI + Embabel, JVM): 1–1.5 GB.
  - Keycloak (JVM): 0.75–1 GB.
  - The nginx frontend: tiny.
- That's **well over 3–4 GB** before traffic, and deploys with a rolling strategy briefly run the old and new pod side by side. 4 GB would mean constant memory pressure and random evictions, which are miserable to debug while learning. **8 GB gives real headroom.** VPS-3 (6 vCores, 12 GB, from €10.40) is the next step up if the Game Catalog's RAG workload grows.

## 4. k3s specifics (what replaces the MKS pieces)

| Concern | MKS plan (old) | k3s on VPS (new) |
|---|---|---|
| Control plane | OVH-managed | k3s server on the VPS. The datastore is **SQLite** by default for a single server |
| Load balancer | OVH Public Cloud LB (€6/mo) | **k3s ServiceLB (Klipper):** a DaemonSet that binds the Service ports as **hostPorts on the VPS's own public IP** (80/443). No cloud LB product |
| Ingress/Gateway | Traefik via Helm | **k3s's bundled Traefik v3.** Customise it with a `HelmChartConfig` in `/var/lib/rancher/k3s/server/manifests/`. Gateway API support is enabled there (`providers.kubernetesGateway.enabled: true`; Traefik v3 supports Gateway API v1.4) |
| TLS | cert-manager + Let's Encrypt | Unchanged: cert-manager + Let's Encrypt, working with the bundled Traefik |
| Storage | OVH Block Storage (Cinder CSI) | **local-path provisioner** (bundled default StorageClass `local-path`). Volumes live on the VPS's NVMe under `/var/lib/rancher/k3s/storage` |
| Secrets | OVH Secret Manager + External Secrets Operator | **SOPS + age** (§6) |
| DB | CloudNativePG + Barman to OVH Object Storage | Unchanged. CNPG runs on local-path volumes, so off-site backups matter even more |
| Node upgrades | OVH-managed | You do them: k3s via the install script with a pinned `INSTALL_K3S_VERSION`, or Rancher's system-upgrade-controller; OS via unattended-upgrades + reboots |

- **local-path consequence:** data exists on one disk only. If the VPS dies, the only copies are the Object Storage backups. That's why the restore drill stays a hard requirement. The VPS's included "daily backup of the previous 24 h" is a bonus, not a substitute for point-in-time recovery.
- **Cluster-state backup:** everything in the cluster is re-creatable from git plus the SOPS files, except the database, which has its own backups. Backing up the k3s SQLite datastore is optional: copy `/var/lib/rancher/k3s/server/db/` **and** `/var/lib/rancher/k3s/server/token`, because the token is needed to decrypt the datastore.

## 5. You now own the host (a direct consequence of self-managing)
- **OS:** a current Ubuntu LTS or Debian stable image from OVH. SSH keys only, root login and password auth disabled, unattended security upgrades.
- **Firewall:** allow inbound **22** (SSH, ideally restricted), **80** and **443**.
  - **Never expose** 10250 (kubelet) or 8472/UDP (flannel VXLAN; k3s docs: *"should not be exposed to the world"*).
  - **6443** (Kubernetes API): how CI reaches it is an **open question** (see the end of this file).
  - Use OVH's network firewall and/or `ufw`/nftables on the host. Note that ServiceLB/hostPort rules interact with host firewalls, so verify in plan.
- **Upgrades:** a runbook procedure for k3s and OS upgrades, with an expected short downtime on a single node.
- **Monitoring the basics:** disk usage (local-path volumes plus images on 75 GB) and memory. There are no dashboards (out of scope), but the runbook has check commands.

## 6. Secrets: SOPS + age
- **age** creates a key pair. **SOPS** encrypts only the *values* in YAML files (keys stay readable, so diffs make sense). Encrypted files such as `deploy/k8s/overlays/prod/secrets.sops.yaml` are committed to git. The `.sops.yaml` config names the age public key.
- **Decryption happens in CI:** the age **private** key is a GitHub Actions secret in the `production` environment. The deploy job runs `sops -d … | kubectl apply -f -` (or a kustomize/ksops plugin, chosen in plan) just before `apply -k`.
- **Key custody:** keep a second copy of the age private key offline, in your password manager. **Losing it means you can't decrypt any prod secret.** Keep the public key in the repo so anyone (you, CI) can *encrypt*.
- **Rotation:** edit with `sops secrets.sops.yaml`, commit, deploy. Pods need a restart to pick up new env values (a runbook step).
- Secrets covered: Postgres app and Keycloak DB credentials (or CNPG-generated), Keycloak admin bootstrap, `ANTHROPIC_API_KEY`, `OPENROUTER_API_KEY` (006), Keycloak service-account client secret (006), S3 keys for backups, ntfy token (if used). The APK signing key (007) stays in GitHub Actions secrets.
- **Local stays `.env`** (gitignored). The rule: no plaintext secret in git, images or ConfigMaps.

## 7. Podman: its actual role
- **Local development container engine** replacing Docker: it runs Postgres + Keycloak through `podman compose`, runs Testcontainers (via `DOCKER_HOST` pointed at the Podman socket, already documented), and builds images locally from the Containerfiles.
- **A learning bridge to Kubernetes:** `podman generate kube` turns a running container or pod into Kubernetes YAML, and `podman kube play` runs Pod/Deployment YAML locally. That lets you sandbox a manifest before you `kubectl apply` it to the cluster. Services/Gateway networking don't behave like a real cluster there.
- **Not a cluster manager.** The cluster is managed with `kubectl` (+ Helm for add-ons). k3s runs containers with **containerd**; Podman plays no part once something runs in the cluster.
- On **macOS** Podman runs in a Linux VM (`podman machine`); on CachyOS it runs natively and rootless.
- **Apple Silicon catch (unchanged):** the MacBook builds arm64 images, but the VPS is x86_64. Prod images are built only by GitHub Actions on amd64 runners (`--platform linux/amd64` if ever built locally).

## 8. Keycloak in production (unchanged by the switch)
- A custom image (theme baked in, `kc.sh build`), started with `start --optimized`, `--hostname=https://<domain>/auth`, `--http-relative-path=/auth`, `--http-enabled=true`, `--proxy-headers=xforwarded`.
- Route only `/auth/realms`, `/auth/resources` and `/auth/.well-known`. **Never** route `/auth/admin` or port 9000; reach the admin console with `kubectl port-forward`. Prod realm file without dev users or secrets (env placeholders: verify for Keycloak 26).

## 9. Database: CloudNativePG (unchanged, except storage)
- `Cluster` resource with 1 instance on StorageClass `local-path`, **PG16** image with pgvector (verify the image), `keycloak` schema.
- Barman Cloud plugin: base backups + WAL to **OVH Object Storage** (S3-compatible, pay-per-use). **Restore drill before go-live.**

## 10. CI/CD with GitHub Actions
- **Build:** on push to `main`, run tests + gitleaks secret scan, build backend, frontend and Keycloak images for linux/amd64, push to `ghcr.io/jordy-swinnen/jordylab-*:<sha>`. Images are public, so nothing secret goes in them.
- **Deploy:** GitHub Environment `production` with you as required reviewer, then:
  1. Decrypt the SOPS files with the age key.
  2. `kustomize edit set image … :<sha>`
  3. `kubectl apply -k deploy/k8s/overlays/prod`
  4. `kubectl rollout status`
  - Cluster credentials: a **namespace-scoped ServiceAccount token**, not the k3s admin kubeconfig (`/etc/rancher/k3s/k3s.yaml`).
- **How CI reaches the API server (6443) is open.** See the questions at the end.
- **Rollback:** re-run the deploy with the previous SHA, or `kubectl rollout undo`.

## 11. Licensing (the repo is already public)
- `jordy-swinnen/JordyLab` is **public**, and MIT licence files exist in `.claude/`, `jordylab-be/`, `jordylab-fe/` and `garmin-sync-service/` (since 2026-08-01). MIT allows anyone to use, modify and sell the code.
- Jordy wants "readable but not usable for now". Decision: **all rights reserved, source available for viewing only**, as one root `LICENSE` replacing the MIT files. With no open-source licence, copyright law already forbids reuse. GitHub's Terms of Service only let others view and fork within GitHub.
- Versions already published under MIT can't be taken back (as far as we know; not legal advice). The new terms apply from the relicensing commit on.
- Not chosen: PolyForm Strict 1.0.0 (still allows non-commercial *use*). Later options for loosening: PolyForm Noncommercial, Business Source License 1.1, or MIT/AGPL.
- Public GHCR images carry compiled code, so they get the same licence label.

## 12. Dependencies with 006 and 007
- 006: new secrets (OpenRouter key, Keycloak service-account secret) and prod realm changes. They go into the SOPS files.
- 007: needs the public domain, `/.well-known/assetlinks.json`, the APK download path, APK storage (a PVC on local-path, or an Object Storage bucket), and CORS for `https://localhost`.
- The **gamecatalog-scanner** on JordyBox points at the prod URL.

## Open questions for `/speckit-clarify`
1. **How does CI reach the k3s API (6443)?**
   - (a) Open 6443 publicly, protected only by the namespace-scoped token.
   - (b) Keep 6443 firewalled, with GitHub Actions joining a WireGuard/Tailscale network for the deploy step.
   - (c) Deploy over SSH (the runner SSHes to the VPS and runs `kubectl` there).
   - This is a direct consequence of self-hosting; MKS exposed the API for you.
2. Ntfy for 007 notifications: in the cluster, or ntfy.sh?
3. Backup retention: 7 daily + 4 weekly? Restore drill before go-live, then quarterly?
4. Deploy trigger: build on every push to `main`, deploy only on manual approval?
5. VPS datacenter: pick the closest EU location offered at checkout (e.g. Gravelines/Strasbourg), in the same region as the Object Storage bucket if possible.
6. VPS term: 12-month upfront (cheapest) or monthly? Confirm the renewal price at checkout.

## Sources
- OVHcloud VPS: [VPS range (VPS 2027: VPS-1 to VPS-4, "from" prices, 12-month upfront configurator)](https://www.ovhcloud.com/en-ie/vps/)
- OVHcloud Managed Kubernetes (rejected option): [MKS](https://www.ovhcloud.com/en/public-cloud/kubernetes/) · [Public Cloud prices](https://www.ovhcloud.com/en-ie/public-cloud/prices/) · [MKS node flavours (D2-4/D2-8 supported)](https://docs.ovhcloud.com/en/guides/public-cloud/containers-orchestration/managed-kubernetes/datacenters-nodes-storage-flavors) · [IOLB → Octavia migration (IOLB deprecated for >1.31)](https://docs.ovhcloud.com/en/guides/public-cloud/containers-orchestration/managed-kubernetes/migrate-iolb-to-public-cloud-loadbalancer)
- k3s: [Networking services (bundled Traefik v3, HelmChartConfig, Gateway API, ServiceLB)](https://docs.k3s.io/networking/networking-services) · [Requirements (2 cores/2 GB, ports, VXLAN warning)](https://docs.k3s.io/installation/requirements) · [Backup/restore (SQLite db + token)](https://docs.k3s.io/datastore/backup-restore)
- [Kubernetes: Ingress NGINX retirement](https://www.kubernetes.io/blog/2025/11/11/ingress-nginx-retirement/) · [Traefik + cert-manager](https://doc.traefik.io/traefik/v3.4/user-guides/cert-manager/)
- [Keycloak behind a reverse proxy](https://www.keycloak.org/server/reverseproxy)
- [CloudNativePG Barman Cloud plugin](https://cloudnative-pg.io/plugin-barman-cloud/docs/intro/)
- [podman kube play](https://docs.podman.io/en/latest/markdown/podman-kube-play.1.html)
- SOPS: [getsops/sops](https://github.com/getsops/sops) · age: [FiloSottile/age](https://github.com/FiloSottile/age)
