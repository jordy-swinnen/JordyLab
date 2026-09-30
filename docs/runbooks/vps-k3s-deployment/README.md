# Runbook: deploying a web app to a single VPS with k3s

This runbook describes, start to finish, how we took a typical full-stack application
(Spring Boot backend, Angular frontend, Keycloak for login, PostgreSQL, a small push-notification
service) and put it into production on **one rented Linux server**, with HTTPS, automatic
backups and a "merge → approve → deployed" pipeline.

It is written for developers who are comfortable with code and Git but new to operations.
Every step says **what** we do and **why** — the "why" is the part that transfers to your own project.

> Conventions used below
> - `<angle brackets>` are placeholders you replace with your own values
    > (`<domain>`, `<vps-ip>`, `<tailscale-ip>`, `<tailnet-name>`, `<app>`, `<github-user>/<repo>`).
> - Commands prefixed with nothing run **on your laptop**; commands shown inside `ssh …` or after
    > "on the server" run **on the VPS**.

---

## Contents

1. [The end result](#1-the-end-result)
2. [Key decisions and why](#2-key-decisions-and-why)
3. [Tools you need on your laptop](#3-tools-you-need-on-your-laptop)
4. [Phase 1 — Server and domain](#4-phase-1--server-and-domain)
5. [Phase 2 — Lock down the server](#5-phase-2--lock-down-the-server)
6. [Phase 3 — A private admin network (Tailscale)](#6-phase-3--a-private-admin-network-tailscale)
7. [Phase 4 — Install Kubernetes (k3s)](#7-phase-4--install-kubernetes-k3s)
8. [Phase 5 — Cluster add-ons](#8-phase-5--cluster-add-ons)
9. [Phase 6 — Off-site storage for database backups](#9-phase-6--off-site-storage-for-database-backups)
10. [Phase 7 — The Kubernetes manifests](#10-phase-7--the-kubernetes-manifests)
11. [Phase 8 — Secrets in Git with SOPS + age](#11-phase-8--secrets-in-git-with-sops--age)
12. [Phase 9 — CI/CD with an approval gate](#12-phase-9--cicd-with-an-approval-gate)
13. [Phase 10 — First deploy and verification](#13-phase-10--first-deploy-and-verification)
14. [Day-2 operations](#14-day-2-operations)
15. [Rules of thumb we learned](#15-rules-of-thumb-we-learned)
16. [Glossary](#16-glossary)

---

## 1. The end result

```
                         Internet
                            │  only ports 22, 80, 443 open
                            ▼
┌──────────────────────── VPS (Ubuntu LTS) ───────────────────────────┐
│  k3s (single-node Kubernetes)                                        │
│                                                                      │
│  Traefik (Gateway API)  ── TLS certificate from Let's Encrypt        │
│     /            → frontend  (nginx serving the Angular build)       │
│     /api         → backend   (Spring Boot)                           │
│     /auth/...    → Keycloak  (only the public login endpoints)       │
│     /ntfy        → ntfy      (push notifications)                    │
│                                                                      │
│  PostgreSQL (CloudNativePG) ── continuous backups ──┐                │
│                                                      │               │
│  k3s API :6443  ◄── only reachable over Tailscale    │               │
└──────────────────────────────────────────────────────┼──────────────┘
                                                       ▼
                                  S3-compatible object storage
                                  (different datacenter)

GitHub: push to main → Build (tests + images) → Deploy (waits for your approval)
        → joins Tailscale → applies manifests → waits until healthy
```

What you get:

- One public domain with HTTPS, certificates renewed automatically.
- The Kubernetes API is **not** on the internet at all.
- Secrets live in Git, encrypted; only you and the deploy pipeline can decrypt them.
- The database streams backups to another datacenter; you can restore to any point in the last 30 days.
- Deploying = merging a pull request and clicking "Approve" in GitHub.

Running cost in our case: a 4 vCPU / 8 GB VPS at about €8.50/month (ex. VAT) plus a few cents per month
for backup storage, plus the domain.

---

## 2. Key decisions and why

| Decision                                                | Why                                                                                                                                                                                 | What we didn't pick                                                                                                                                                                                     |
|---------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **One VPS, self-managed k3s**                           | Cheapest way to get "real" Kubernetes. k3s is a single binary that bundles an ingress (Traefik), a load balancer, local storage and DNS, so one server is enough.                   | Managed Kubernetes (costs more, overkill for one app); plain Docker Compose (no rolling updates, health checks, or a path to grow).                                                                     |
| **Tailscale for admin access**                          | The Kubernetes API (port 6443) is the keys to the kingdom. Tailscale puts it on a private network that only your devices and CI can join — no open port, no VPN server to maintain. | Opening 6443 to the internet (constantly scanned); a self-hosted WireGuard/OpenVPN (more work).                                                                                                         |
| **Gateway API** (via k3s's bundled Traefik)             | The newer, standard Kubernetes routing API; cert-manager supports it directly.                                                                                                      | The older `Ingress` API.                                                                                                                                                                                |
| **cert-manager + Let's Encrypt (HTTP-01)**              | Free certificates that renew themselves.                                                                                                                                            | Buying certificates; manual renewals.                                                                                                                                                                   |
| **CloudNativePG (CNPG) operator**                       | Runs PostgreSQL "the Kubernetes way": it creates the database, users, and handles backups and restores declaratively.                                                               | A hand-rolled Postgres Deployment (you'd rebuild backup/restore yourself).                                                                                                                              |
| **Barman Cloud plugin → S3 in another region**          | Backups must survive losing the server *and* the datacenter. The plugin is CNPG's current recommended backup path.                                                                  | Backups on the same disk/datacenter (useless in the scenarios that matter).                                                                                                                             |
| **SOPS + age for secrets**                              | Secrets are versioned next to the code, encrypted. One private key unlocks them. No extra service to run or pay for.                                                                | A cloud secret manager (Azure Key Vault, OVH Secret Manager + External Secrets Operator). Great for teams and rotation, but an extra dependency for one person and one server. Easy to switch to later. |
| **GitHub Actions + protected "production" environment** | Every deploy is a Git commit and needs a human click.                                                                                                                               | Deploying from your laptop (not reproducible, no audit trail).                                                                                                                                          |
| **Kustomize** (base + overlay)                          | Plain YAML plus small per-environment patches; no templating language to learn.                                                                                                     | Helm charts for our own app (more indirection than we needed).                                                                                                                                          |
| **Public container images**                             | The repo is public, the images contain nothing secret, so the cluster can pull without credentials.                                                                                 | Private images + pull secrets (fine too, one extra moving part).                                                                                                                                        |

---

## 3. Tools you need on your laptop

macOS with Homebrew shown; every tool exists for Linux/Windows too.

```bash
brew install kubectl helm sops age kustomize gitleaks gh
```

| Tool           | Used for                                                                                                           |
|----------------|--------------------------------------------------------------------------------------------------------------------|
| `kubectl`      | Talking to the cluster. Keep it within **one minor version** of the server (e.g. server 1.36 → kubectl 1.35–1.37). |
| `helm`         | Installing third-party components (cert-manager, CNPG).                                                            |
| `sops` + `age` | Encrypting/decrypting the secrets file.                                                                            |
| `kustomize`    | Building the final manifests (also built into `kubectl kustomize`).                                                |
| `gitleaks`     | Scanning for accidentally committed secrets before you push.                                                       |
| `gh`           | GitHub CLI — used to set secrets without ever printing them.                                                       |
| Tailscale app  | Joins your laptop to the private admin network.                                                                    |

> Tip: if `kubectl version` shows an unexpected old client after installing, another copy is earlier in
> your `PATH` or your shell cached the old location. `which -a kubectl` shows all copies; `hash -r`
> clears zsh's cache.

---

## 4. Phase 1 — Server and domain

**Server.** Rent a VPS with a current **Ubuntu LTS** image. We used 4 vCPU / 8 GB RAM / 75 GB NVMe,
which comfortably runs Postgres, a JVM backend, Keycloak and the rest. Add your **SSH public key**
when ordering (or when reinstalling the OS) so password login is never needed.

```bash
cat ~/.ssh/id_ed25519.pub          # the key to paste into the provider's form
ssh ubuntu@<vps-ip>                # verify you can log in
```

**Domain.** Buy a domain and, in the registrar's DNS zone, point an **A record** for the bare domain
at the server's IPv4 address. If the zone already contains a "parking" A record, **edit** it instead
of adding a second one — two A records would split visitors between your server and the parking page.

```bash
dig +short <domain> @1.1.1.1       # should print <vps-ip>
```

> Some country domains (e.g. `.be`) are only published after the **registry verifies the holder's
> contact details** by email. If `dig` returns nothing for a day, run `whois <domain>` — it will say so.

**Billing hygiene:** check whether the VPS renews automatically, and turn that on if you want it to.

---

## 5. Phase 2 — Lock down the server

Goal: only key-based SSH, and a firewall that allows web traffic but nothing else.

**SSH hardening** — a drop-in file overrides the defaults without editing the main config:

```bash
# on the server
sudo tee /etc/ssh/sshd_config.d/00-hardening.conf >/dev/null <<'EOF'
PasswordAuthentication no
KbdInteractiveAuthentication no
PermitRootLogin no
EOF
sudo sshd -t && sudo systemctl reload ssh
```

Keep your current SSH session open and test a **new** login from a second terminal before closing it.
That is your lock-out escape.

**Firewall (ufw)**:

```bash
# on the server
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow OpenSSH
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
# k3s internal networks (pods and services) must be able to talk to the host:
sudo ufw allow from 10.42.0.0/16
sudo ufw allow from 10.43.0.0/16
sudo ufw enable
```

Why the two `10.x` rules: k3s gives pods addresses in `10.42.0.0/16` and services in `10.43.0.0/16`.
Without these, ufw blocks pod traffic to the node (DNS, the API server) and things fail in confusing ways.

---

## 6. Phase 3 — A private admin network (Tailscale)

Tailscale creates an encrypted private network (a "tailnet") between your devices. We use it so the
Kubernetes API is reachable from **your laptop and CI only**.

**Join the server and your laptop:**

```bash
# on the server
curl -fsSL https://tailscale.com/install.sh | sh
sudo tailscale up
tailscale ip -4                    # note this: <tailscale-ip>

# on the server: allow the k3s API only on the Tailscale interface
sudo ufw allow in on tailscale0 to any port 6443 proto tcp
```

Install the Tailscale app on your laptop and log in with the same account.
Also note the server's MagicDNS name (`<server>.<tailnet-name>.ts.net`) from the admin console.

**Access policy.** The default Tailscale policy lets every device reach every other device on every port.
That's fine for your own devices, but CI machines should reach **only port 6443 on the server**.
In the admin console → *Access controls*, the policy we used:

```jsonc
{
  "tagOwners": {
    "tag:server": ["autogroup:admin"],
    "tag:ci":     ["autogroup:admin"],
  },
  "grants": [
    // Your own (untagged) devices can reach everything.
    {"src": ["autogroup:member"], "dst": ["*"], "ip": ["*"]},
    // CI may reach only the Kubernetes API on the server.
    {"src": ["tag:ci"], "dst": ["tag:server"], "ip": ["tcp:6443"]},
  ],
  "ssh": [
    {"action": "check", "src": ["autogroup:member"], "dst": ["autogroup:self"],
     "users": ["autogroup:nonroot", "root"]},
  ],
  // Tailscale refuses to save the policy if these don't hold.
  "tests": [
    {"src": "tag:ci", "accept": ["tag:server:6443"], "deny": ["tag:server:22", "tag:server:443"]},
  ],
}
```

Why tags: a tagged device is owned by the tag, not by a person, so it can't reach your laptop, and its
access is defined by policy instead of by who logged it in.

**Tag the server and disable key expiry.** In *Machines* → the server → *Edit ACL tags* → `tag:server`.
Then *Machine settings* → **Disable key expiry**. Otherwise the server silently drops off the tailnet
after ~180 days and you lose access to the cluster API.

---

## 7. Phase 4 — Install Kubernetes (k3s)

**Pick the version.** k3s publishes "channels". Use **stable** unless you have a reason not to:

```bash
curl -s https://update.k3s.io/v1-release/channels | grep -o '"name":"stable","latest":"[^"]*"'
```

**Install**, telling k3s which extra names/IPs its API certificate must be valid for (your Tailscale
IP and MagicDNS name — otherwise kubectl over Tailscale fails certificate checks):

```bash
# on the server
curl -sfL https://get.k3s.io | INSTALL_K3S_CHANNEL=stable sh -s - server \
  --tls-san <tailscale-ip> \
  --tls-san <server>.<tailnet-name>.ts.net
sudo kubectl get nodes             # STATUS should become Ready
sudo kubectl get pods -A           # coredns, traefik, local-path, metrics-server → Running
```

Give it a minute before judging — the system pods need time to pull images.

**Enable Traefik's Gateway API support.** k3s manages its bundled Traefik with a Helm chart; you
customize it by dropping a `HelmChartConfig` into k3s's manifests folder (k3s applies it automatically):

```yaml
# /var/lib/rancher/k3s/server/manifests/traefik-config.yaml  (keep a copy in your repo)
apiVersion: helm.cattle.io/v1
kind: HelmChartConfig
metadata:
  name: traefik
  namespace: kube-system
spec:
  valuesContent: |-
    providers:
      kubernetesGateway:
        enabled: true
    gateway:
      enabled: true
```

```bash
ssh ubuntu@<vps-ip> 'sudo tee /var/lib/rancher/k3s/server/manifests/traefik-config.yaml >/dev/null' \
  < deploy/host/traefik-helmchartconfig.yaml
```

Note what's **not** in there: an HTTP→HTTPS redirect. We do that redirect with a route instead
(see [Phase 7](#10-phase-7--the-kubernetes-manifests)); a redirect at Traefik's entry point would also
redirect Let's Encrypt's validation requests and break certificate issuance.

**Get the admin kubeconfig onto your laptop**, pointed at the Tailscale name instead of `127.0.0.1`:

```bash
mkdir -p ~/.kube
ssh ubuntu@<vps-ip> 'sudo cat /etc/rancher/k3s/k3s.yaml' \
  | sed 's#https://127.0.0.1:6443#https://<server>.<tailnet-name>.ts.net:6443#' > ~/.kube/<app>.yaml
chmod 600 ~/.kube/<app>.yaml
export KUBECONFIG=~/.kube/<app>.yaml     # add to ~/.zshrc to make it permanent
kubectl get nodes
kubectl get gatewayclass                  # traefik → ACCEPTED True
```

This file is full cluster-admin. Never commit it, never paste it anywhere.

---

## 8. Phase 5 — Cluster add-ons

Three components, installed once with Helm. **Look up current versions first** and pin them:

```bash
helm repo add jetstack https://charts.jetstack.io
helm repo add cnpg https://cloudnative-pg.github.io/charts
helm repo update
helm search repo jetstack/cert-manager
helm search repo cnpg/cloudnative-pg
helm search repo cnpg/plugin-barman-cloud
```

> **Chart version ≠ app version.** `helm install --version` wants the *chart* version.
> E.g. CloudNativePG operator 1.30.1 ships as chart 0.29.1. The search output shows both columns.

Install in this order (the backup plugin needs cert-manager for its internal certificates):

```bash
helm install cert-manager jetstack/cert-manager \
  --namespace cert-manager --create-namespace --version <chart-version> \
  --set crds.enabled=true \
  --set config.enableGatewayAPI=true \
  --wait

helm install cnpg cnpg/cloudnative-pg \
  --namespace cnpg-system --create-namespace --version <chart-version> --wait

helm install barman-cloud cnpg/plugin-barman-cloud \
  --namespace cnpg-system --version <chart-version> --wait
```

What each one is:

- **cert-manager** — watches `Certificate` resources and gets them signed by Let's Encrypt.
  `config.enableGatewayAPI=true` lets it answer Let's Encrypt's HTTP challenge through the Gateway.
- **CloudNativePG** — an *operator*: you declare "I want a Postgres cluster like this" and it builds and
  maintains it.
- **Barman Cloud plugin** — ships WAL (the database's change log) and periodic full backups to S3 storage.

Verify: `kubectl get pods -n cert-manager -n cnpg-system` all Running;
`kubectl -n cnpg-system get certificate` shows the plugin's certificates `READY True`.

---

## 9. Phase 6 — Off-site storage for database backups

Create an **S3-compatible bucket in a different datacenter/region than the server** (we used the same
cloud provider's object storage, other country). If the server's datacenter burns down, the backups
survive; that is the entire point.

1. In the provider console, create a project for object storage if needed (this may require a
   registered payment method — pay-as-you-go).
2. Create a **private** bucket, e.g. `<app>-db-backups`, standard storage class, no versioning.
3. Create a dedicated storage user with read/write on it and generate its **S3 access key + secret key**.
   Store both in your password manager; they go into the encrypted secrets file in Phase 8.
4. Note the S3 endpoint for the region, e.g. `https://s3.<region>.io.cloud.ovh.net` for OVH
   (other providers document theirs).

Cost for a small database: cents per month.

---

## 10. Phase 7 — The Kubernetes manifests

### 10.1 Layout

```
deploy/
  host/traefik-helmchartconfig.yaml   # copied to the server once (Phase 4)
  k8s/
    bootstrap/      # cluster-wide things, applied ONCE by you with the admin kubeconfig
      namespace.yaml
      cert-manager-clusterissuer.yaml   # Let's Encrypt prod + staging issuers
      ci-deploy-rbac.yaml               # CI's ServiceAccount, Role, RoleBinding, token
      kustomization.yaml
    base/           # the app: Deployments, Services, PVCs, Gateway, HTTPRoutes
    cluster/        # namespaced platform pieces: Postgres cluster, backup config, Certificate
    overlays/prod/  # production values: domain, config maps, image tags, encrypted secrets
      kustomization.yaml
      secrets.sops.yaml
```

**Why split `bootstrap/` out:** CI deploys with a token that can only touch *one namespace*. Anything
cluster-wide (the namespace itself, ClusterIssuers, RBAC) must be applied by an admin — you — once.
Least privilege: if the CI token leaks, the damage stays inside one namespace.

**Kustomize rules worth knowing:**

- The overlay references *directories* (`../../base`, `../../cluster`), each with its own
  `kustomization.yaml`. Referencing individual files outside the overlay folder is blocked by default.
- Set `namespace: <app>` in the overlay so every namespaced object lands in the right place.
- Check the result locally before anything else: `kubectl kustomize deploy/k8s/overlays/prod`.

### 10.2 Routing: Gateway + HTTPRoutes

```yaml
apiVersion: gateway.networking.k8s.io/v1
kind: Gateway
metadata: { name: app-gateway }
spec:
  gatewayClassName: traefik
  listeners:
    - name: http
      protocol: HTTP
      port: 8000            # Traefik's INTERNAL "web" port, not 80
    - name: https
      protocol: HTTPS
      port: 8443            # Traefik's INTERNAL "websecure" port, not 443
      tls:
        mode: Terminate
        certificateRefs: [{ name: app-tls }]
```

> **Why 8000/8443:** Traefik matches Gateway listeners to its entry points by the port the entry point
> *listens on inside the pod*. k3s's Traefik listens on 8000/8443; its Service maps the public 80/443
> onto them.

One `HTTPRoute` per service, attached to the `https` listener, by path prefix:
`/` → frontend, `/api` → backend, `/auth/realms`, `/auth/resources`, `/auth/.well-known` → Keycloak
(deliberately **not** `/auth/admin`), `/ntfy` → ntfy (with a prefix-strip rewrite).

Plus one route on the `http` listener that redirects everything to HTTPS:

```yaml
rules:
  - filters:
      - type: RequestRedirect
        requestRedirect: { scheme: https, statusCode: 301 }
```

Let's Encrypt's challenge route (created temporarily by cert-manager) has a more specific path, so it
wins over this catch-all and validation still works.

### 10.3 TLS

```yaml
# bootstrap/: one issuer for real certs, one for testing (untrusted, but generous rate limits)
apiVersion: cert-manager.io/v1
kind: ClusterIssuer
metadata: { name: letsencrypt-prod }
spec:
  acme:
    server: https://acme-v02.api.letsencrypt.org/directory
    email: <an address you read>
    privateKeySecretRef: { name: letsencrypt-prod-account-key }
    solvers:
      - http01:
          gatewayHTTPRoute:
            parentRefs: [{ name: app-gateway, namespace: <app>, sectionName: http }]
---
# cluster/: the certificate itself
apiVersion: cert-manager.io/v1
kind: Certificate
metadata: { name: app-tls }
spec:
  secretName: app-tls
  issuerRef: { name: letsencrypt-prod, kind: ClusterIssuer }
  dnsNames: [<domain>]
```

The certificate is issued as soon as the domain resolves to the server, and renewed automatically.

### 10.4 Database (CloudNativePG)

```yaml
apiVersion: postgresql.cnpg.io/v1
kind: Cluster
metadata: { name: pg }
spec:
  instances: 1
  imageName: ghcr.io/cloudnative-pg/postgresql:16.x-standard-trixie   # "standard" includes pgvector
  storage: { storageClass: local-path, size: 10Gi }
  bootstrap:
    initdb:
      database: app
      owner: app
      secret: { name: db-app }                  # username + password for the owner
      postInitSQL:                               # runs in the `postgres` database
        - CREATE ROLE keycloak LOGIN;
      postInitApplicationSQL:                    # runs inside the `app` database
        - CREATE SCHEMA IF NOT EXISTS keycloak AUTHORIZATION keycloak;
  managed:
    roles:                                       # CNPG keeps this role's password in sync
      - name: keycloak
        ensure: present
        login: true
        passwordSecret: { name: db-keycloak }
  plugins:
    - name: barman-cloud.cloudnative-pg.io
      isWALArchiver: true
      parameters: { barmanObjectName: offsite-backups }
```

**The credentials pattern** — each password lives in exactly one Secret, used by both sides:

```
Secret db-app       (type kubernetes.io/basic-auth: username, password)
   ├── CNPG creates the database owner with it
   └── backend reads POSTGRES_USER / POSTGRES_PASSWORD from it (secretKeyRef)
Secret db-keycloak  (same shape)
   ├── CNPG sets the keycloak role's password from it
   └── Keycloak reads KC_DB_USERNAME / KC_DB_PASSWORD from it
```

If the app kept its own copy of the password, the two would drift and logins would fail.

**Extensions:** use CNPG's **standard** image flavour — it already includes pgvector.
(CNPG's newer "ImageVolume extensions" mechanism only works on PostgreSQL 18+.)

### 10.5 Backups

```yaml
apiVersion: barmancloud.cnpg.io/v1
kind: ObjectStore
metadata: { name: offsite-backups }
spec:
  configuration:
    destinationPath: s3://<bucket>/pg
    endpointURL: https://s3.<region>.<provider-domain>
    s3Credentials:
      accessKeyId:     { name: app-secrets, key: S3_ACCESS_KEY }
      secretAccessKey: { name: app-secrets, key: S3_SECRET_KEY }
    wal: { compression: gzip }
  retentionPolicy: 30d          # restore to any point in the last 30 days
---
apiVersion: postgresql.cnpg.io/v1
kind: ScheduledBackup
metadata: { name: daily-backup }
spec:
  schedule: "0 0 3 * * *"       # 6 fields, SECONDS FIRST → 03:00 UTC daily
  backupOwnerReference: self
  cluster: { name: pg }
  method: plugin
  pluginConfiguration: { name: barman-cloud.cloudnative-pg.io }
```

> **Two things that are easy to get wrong**
> - CNPG schedules use a **6-field cron with seconds first**. The usual 5-field `"0 3 * * *"` would
    > mean "every hour at minute 3".
> - Retention is **time-based** (`30d`). CNPG has no "keep the last 7 daily + 4 weekly" setting;
    > the schedules only decide *how often* a full backup is taken. WAL archiving is continuous.

### 10.6 Keycloak

Keycloak has **build-time** options (baked into the image by `kc.sh build`) and **runtime** options
(environment variables). With `start --optimized`, a runtime value that differs from the built value
makes Keycloak refuse to start — so decide deliberately which is which.

```dockerfile
FROM quay.io/keycloak/keycloak:<version> AS builder
ENV KC_DB=postgres
ENV KC_HEALTH_ENABLED=true
ENV KC_METRICS_ENABLED=true
ENV KC_HTTP_RELATIVE_PATH=/auth             # build-time: Keycloak lives under /auth
ENV KC_HTTP_MANAGEMENT_RELATIVE_PATH=/      # build-time: health stays at :9000/health/*
RUN /opt/keycloak/bin/kc.sh build

FROM quay.io/keycloak/keycloak:<version>
COPY --from=builder /opt/keycloak/ /opt/keycloak/
COPY deploy/keycloak/realm-prod.json /opt/keycloak/data/import/realm-prod.json
ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
CMD ["start", "--optimized", "--import-realm"]
```

Runtime settings (ConfigMap in the overlay):

| Variable                     | Value                   | Why                                                                           |
|------------------------------|-------------------------|-------------------------------------------------------------------------------|
| `KC_HOSTNAME`                | `https://<domain>/auth` | The public URL Keycloak puts in tokens and redirects.                         |
| `KC_HTTP_RELATIVE_PATH`      | `/auth`                 | Must equal the built value.                                                   |
| `KC_HTTP_ENABLED`            | `true`                  | Traefik terminates HTTPS; inside the cluster Keycloak speaks plain HTTP.      |
| `KC_PROXY_HEADERS`           | `xforwarded`            | Trust `X-Forwarded-*` so Keycloak knows the original request was HTTPS.       |
| `KC_PROXY_TRUSTED_ADDRESSES` | `10.42.0.0/16`          | …but only from inside the cluster (the pod network), never from the internet. |

**Realm as code.** The realm (clients, roles, settings) is a JSON file baked into the image and imported
on first start. Values that differ per environment use `${VAR}` placeholders, filled from the container's
environment at import time (e.g. `${PRODUCTION_DOMAIN}`, `${BACKEND_CLIENT_SECRET}`).
Keycloak **skips the import if the realm already exists**, so later changes to the file must be applied
through the admin console or `kcadm.sh`.

**The admin console is not routed publicly** — only the login-related paths are. Reach it through a
tunnel when needed (see [Day-2](#14-day-2-operations)).

### 10.7 Backend and frontend

- **Health probes must be reachable without login.** Kubernetes calls `/actuator/health/liveness` and
  `/actuator/health/readiness`; in Spring Security permit `/actuator/health/**`, not just
  `/actuator/health`. (Safe: `/actuator` isn't routed through the Gateway.)
- **Frontend image:** multi-stage build — Bun/Node builds the Angular app, then
  `nginxinc/nginx-unprivileged` serves it on port 8080 as a non-root user. Angular's application
  builder writes the site into **`dist/<project>/browser/`** — copy *that* folder into nginx's html root.
- **Pin image tags** for anything third-party (e.g. ntfy `v2.28.0`), never `latest`.
- **ntfy** can't run under a sub-path with a `base-url` set; leave `NTFY_BASE_URL` off the ntfy
  container when you route it at `/ntfy` (publish/subscribe still work). Give it a subdomain later if
  you need attachments or web push.

---

## 11. Phase 8 — Secrets in Git with SOPS + age

**How it works.** `age` is a tiny encryption tool with a key pair. `sops` encrypts only the *values* in a
YAML file (keys stay readable, so diffs make sense). Anyone with the **public** key can encrypt; only the
**private** key decrypts. The private key lives in three places: your laptop, your password manager, and
the GitHub production environment.

**1. Create the key** (the location below is where sops looks by default on macOS):

```bash
mkdir -p "$HOME/Library/Application Support/sops/age"
age-keygen -o "$HOME/Library/Application Support/sops/age/keys.txt"
chmod 600 "$HOME/Library/Application Support/sops/age/keys.txt"
```

It prints `Public key: age1…`. Back up the whole file in your password manager — lose it and nobody
can decrypt the secrets again.

**2. Tell sops which files to encrypt and with which key** (`.sops.yaml` in the repo root):

```yaml
creation_rules:
  - path_regex: .*secrets\.sops\.ya?ml$
    encrypted_regex: ^(data|stringData)$     # only Secret values
    age: age1<your-public-key>
```

**3. Write the secrets file** as normal Kubernetes Secrets with empty values (one `Opaque` Secret for
app settings, and the two `basic-auth` Secrets for the database from 10.4), then encrypt and edit:

```bash
sops -e -i deploy/k8s/overlays/prod/secrets.sops.yaml
EDITOR="code --wait" sops deploy/k8s/overlays/prod/secrets.sops.yaml
```

sops decrypts into a temporary file, opens your editor, and re-encrypts when you **close the tab**.

- Use `openssl rand -base64 33 | tr -d '/+='` for generated passwords.
- Editors with a "local history" feature (VS Code, IntelliJ) may keep plaintext copies of the temp file.
  Use a separate VS Code profile with `"workbench.localHistory.enabled": false`, or clear IntelliJ's
  local history afterwards.
- Empty values stay unencrypted (there's nothing to hide); that's normal.
- Check it decrypts: `sops -d deploy/k8s/overlays/prod/secrets.sops.yaml >/dev/null && echo OK`.

**4. Before committing**, scan for leaks:

```bash
gitleaks git --redact .      # history
gitleaks dir --redact .      # working tree (findings in gitignored files like .env are expected)
```

---

## 12. Phase 9 — CI/CD with an approval gate

### 12.1 The flow

```
PR → Build workflow runs tests (no images)
merge to main → Build: tests + build & push images tagged sha-<commit>
             → Deploy workflow starts, pauses: "waiting for approval"
you click Approve → runner joins Tailscale as tag:ci
                  → decrypts secrets, sets image tags to sha-<commit>, kubectl apply
                  → waits until every Deployment is healthy (fails the run if not)
```

### 12.2 Build workflow essentials

- Push images to GHCR as `ghcr.io/<github-user>/<app>-<component>:sha-<full-commit-sha>`.
  A commit-SHA tag means "exactly this code", which makes rollbacks trivial.
- Only build/push on pushes to `main`, not on PRs.
- In the image matrix set `strategy.fail-fast: false`, so one failing image doesn't cancel the others.
- For a **public** repo, packages published from it are public too, and the cluster can pull them
  without credentials. (Check anonymously: request a token from `ghcr.io/token?scope=repository:…:pull`
  and list tags.)

### 12.3 One-time GitHub setup

**Production environment** with you as required reviewer, deployable only from `main`:

```bash
REPO=<github-user>/<repo>
gh api -X PUT repos/$REPO/environments/production --input - <<EOF
{"reviewers":[{"type":"User","id":$(gh api user --jq .id)}],
 "deployment_branch_policy":{"protected_branches":false,"custom_branch_policies":true}}
EOF
gh api -X POST repos/$REPO/environments/production/deployment-branch-policies -f name=main -f type=branch
```

(Required reviewers are free on public repos; private repos need a paid plan.)

**CI's cluster credentials.** In `bootstrap/ci-deploy-rbac.yaml`: a ServiceAccount, a `Role` limited to
the app namespace and the API groups the app uses (core, apps, gateway, cert-manager, CNPG, Barman,
batch), a `RoleBinding`, and a long-lived token:

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: ci-deploy-token
  namespace: <app>
  annotations: { kubernetes.io/service-account.name: ci-deploy }
type: kubernetes.io/service-account-token
```

```bash
kubectl apply -k deploy/k8s/bootstrap          # once, as admin
```

**A Tailscale credential for CI.** Admin console → *Settings → Trust credentials* → new **OAuth**
credential with **only** *Keys → Auth Keys → Write*, tag **`tag:ci`**. The secret is shown once.

**Store the six secrets in the environment — piped, never printed:**

```bash
kubectl -n <app> get secret ci-deploy-token -o jsonpath='{.data.token}' | base64 -d \
  | gh secret set KUBE_TOKEN --env production --repo $REPO
kubectl config view --raw --minify -o jsonpath='{.clusters[0].cluster.certificate-authority-data}' \
  | gh secret set KUBE_CA_CERT --env production --repo $REPO
printf 'https://<tailscale-ip>:6443' | gh secret set KUBE_SERVER --env production --repo $REPO
grep '^AGE-SECRET-KEY-' "$HOME/Library/Application Support/sops/age/keys.txt" \
  | gh secret set SOPS_AGE_KEY --env production --repo $REPO
gh secret set TS_OAUTH_CLIENT_ID     --env production --repo $REPO   # paste when prompted
gh secret set TS_OAUTH_CLIENT_SECRET --env production --repo $REPO   # starts with tskey-client-
gh secret list --env production --repo $REPO
```

### 12.4 Deploy workflow essentials

```yaml
on:
  workflow_run: { workflows: [Build], types: [completed] }
  workflow_dispatch: { inputs: { sha: { required: false } } }   # manual deploy / rollback
jobs:
  deploy:
    if: >-
      github.event_name == 'workflow_dispatch' ||
      (github.event.workflow_run.conclusion == 'success' &&
       github.event.workflow_run.event == 'push' &&
       github.event.workflow_run.head_branch == 'main')
    environment: production                       # ← the approval gate
    env: { SHA: "${{ github.event.inputs.sha || github.event.workflow_run.head_sha }}" }
    steps:
      - uses: actions/checkout@v4
        with: { ref: "${{ env.SHA }}" }           # manifests from the SAME commit as the images
      - uses: tailscale/github-action@v4
        with:
          oauth-client-id: ${{ secrets.TS_OAUTH_CLIENT_ID }}
          oauth-secret:    ${{ secrets.TS_OAUTH_CLIENT_SECRET }}
          tags: tag:ci
      # install pinned sops, age, kustomize, and a kubectl matching the cluster's minor version
      # write a kubeconfig from KUBE_SERVER / KUBE_CA_CERT / KUBE_TOKEN
      - run: |
          cd deploy/k8s/overlays/prod
          sops --output secrets.sops.yaml -d secrets.sops.yaml      # only in the runner
          kustomize edit set image <image>=<image>:sha-${SHA} ...   # for each component
          kustomize build . | kubectl apply -f -
      - run: |
          kubectl -n <app> rollout status deploy/backend  --timeout=8m
          kubectl -n <app> rollout status deploy/frontend --timeout=5m
          kubectl -n <app> rollout status deploy/keycloak --timeout=8m
```

Why the details matter:

- The `if:` stops PR builds from triggering deploys.
- `ref: ${{ env.SHA }}`: a `workflow_run` checkout otherwise uses the latest `main`, which may be newer
  than the images you're deploying.
- The rollout wait turns "applied" into "actually running"; a broken release fails the workflow.

---

## 13. Phase 10 — First deploy and verification

Merge, wait for Build, approve the deploy. Then watch:

```bash
export KUBECONFIG=~/.kube/<app>.yaml
watch -n 5 'kubectl -n <app> get pods,cluster,certificate,gateway'
```

Expect: the database first (a minute of initialization), then backend and Keycloak (a few restarts
while the database comes up are normal), certificate `READY True` once DNS resolves.

**Verification checklist:**

| Check                | How                                                                     | Expected                      |
|----------------------|-------------------------------------------------------------------------|-------------------------------|
| Site over HTTPS      | open `https://<domain>`                                                 | your app, valid certificate   |
| HTTP redirects       | `curl -I http://<domain>`                                               | `301` to `https://`           |
| Login endpoints      | `https://<domain>/auth/realms/<realm>/.well-known/openid-configuration` | JSON with the right `issuer`  |
| Admin console hidden | `https://<domain>/auth/admin/`                                          | **not** Keycloak              |
| Only 3 open ports    | `nmap -Pn <vps-ip>` from outside                                        | 22, 80, 443                   |
| Backups flowing      | `kubectl -n <app> get backups`; look in the bucket                      | objects under the backup path |
| No secrets in Git    | `gitleaks git --redact .`                                               | no leaks                      |

**Your first admin user.** The production realm ships without users. Register on the login page
(self-registration), then grant yourself the admin role from inside the Keycloak pod — the bootstrap
admin's credentials are already in its environment, so no password is typed or shown:

```bash
kubectl -n <app> exec deploy/keycloak -- bash -c '
K=/opt/keycloak/bin/kcadm.sh; C="--config /tmp/kc.cfg"
$K config credentials $C --server http://localhost:8080/auth --realm master \
   --user "$KC_BOOTSTRAP_ADMIN_USERNAME" --password "$KC_BOOTSTRAP_ADMIN_PASSWORD" >/dev/null
$K add-roles -r <realm> $C --uusername <your-email> --rolename admin'
```

Log out and in again so your token carries the new role.

---

## 14. Day-2 operations

**Deploy a change:** PR → merge → approve. That's it.

**Roll back:** Actions → *Deploy to Production* → *Run workflow* → enter the commit SHA of the last good
version → approve. Images for every past commit still exist in GHCR.

**Look at what's happening:**

```bash
kubectl -n <app> get pods
kubectl -n <app> logs deploy/<name> --tail=100
kubectl -n <app> logs deploy/<name> --previous      # the crashed attempt before the current one
kubectl -n <app> describe pod <pod>                 # events: pulls, probes, restarts
kubectl -n <app> get events --sort-by=.lastTimestamp | tail -25
```

**Try a config fix live before committing it.** For a runtime setting, change it directly and watch:

```bash
kubectl -n <app> set env deploy/<name> SOME_SETTING=value
kubectl -n <app> rollout status deploy/<name>
```

If it works, put the same change in Git so the next deploy keeps it. This shortens the feedback loop
from "PR + build + deploy" (15+ min) to about a minute.

**Keycloak admin console** (not public on purpose):

```bash
kubectl -n <app> port-forward deploy/keycloak 8080:8080
# open http://localhost:8080/auth/admin
```

Keycloak's bootstrap admin is meant to be **temporary**: through the console, create a permanent admin
in the `master` realm and then remove the bootstrap one.

**Add or change a secret:** `sops deploy/k8s/overlays/prod/secrets.sops.yaml` → edit → close → commit →
deploy. Keys in the app Secret are exposed to the backend as environment variables automatically
(`envFrom`), so a new key needs no manifest change.

**Rotate the CI token:** `kubectl -n <app> delete secret ci-deploy-token && kubectl apply -k deploy/k8s/bootstrap`,
then set `KUBE_TOKEN` again.

**Restore drill** (do this once before relying on backups, then every few months): create a second CNPG
`Cluster` with `bootstrap.recovery` pointing at the same `ObjectStore` (optionally with a target time),
check the data, delete it. A backup you've never restored is a hope, not a backup.

**Upgrades:** k3s (re-run the installer with the new channel/version), the Helm add-ons (`helm upgrade`
with a new pinned version), and the kubectl version in the deploy workflow — keep kubectl within one
minor version of the cluster.

**Watch the bills:** VPS renewal, domain renewal, object storage.

---

## 15. Rules of thumb we learned

1. **Read the running thing's own error.** `kubectl logs --previous` and `describe` almost always name
   the exact problem.
2. **Verify versions against the live source** (chart repo, Docker Hub, release page) before pinning.
   Documentation and memory drift; registries don't.
3. **Separate what's cluster-wide from what's per-app.** Admin applies the first once; CI applies the second often.
4. **One secret, one place.** If two components need the same password, both read the same Secret.
5. **Never let a secret pass through a screen or chat.** Pipe values (`… | gh secret set …`), exec into
   pods that already hold credentials, use `--redact`.
6. **Backups live somewhere else**, and are tested.
7. **Health endpoints must be reachable without authentication** — Kubernetes restarts what it can't check.
8. **Build-time vs runtime configuration**: know which settings are baked into an image. Mismatches
   often surface only at startup.
9. **Test runtime fixes live, then commit them.** Short feedback loops keep deploy days sane.

---

## 16. Glossary

| Term                                           | Meaning                                                                                                                                     |
|------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------|
| **VPS**                                        | A virtual server you rent; you manage everything from the OS up.                                                                            |
| **k3s**                                        | A lightweight, single-binary Kubernetes distribution.                                                                                       |
| **Pod / Deployment / Service**                 | A running container (group) / the thing that keeps N pods running and rolls out new versions / a stable internal address for a set of pods. |
| **Namespace**                                  | A folder for Kubernetes objects; permissions can be scoped to one.                                                                          |
| **Gateway / HTTPRoute**                        | Gateway API objects: the entry point (ports, TLS) / rules that route paths to Services.                                                     |
| **Traefik**                                    | The reverse proxy k3s ships with; it implements the Gateway.                                                                                |
| **cert-manager / ClusterIssuer / Certificate** | Automates TLS: the issuer says *who signs* (Let's Encrypt), the certificate says *for which names*.                                         |
| **HTTP-01 challenge**                          | Let's Encrypt proves you control a domain by fetching a token over plain HTTP.                                                              |
| **Operator**                                   | A controller that manages a complex app (here Postgres) from a declarative resource.                                                        |
| **WAL**                                        | PostgreSQL's write-ahead log; archiving it continuously enables point-in-time restore.                                                      |
| **Helm chart**                                 | A packaged, versioned set of Kubernetes manifests for third-party software.                                                                 |
| **Kustomize base/overlay**                     | Shared manifests + per-environment patches, no templating.                                                                                  |
| **SOPS / age**                                 | Encrypt values inside YAML / the key pair doing the encryption.                                                                             |
| **Tailscale / tailnet / tag**                  | WireGuard-based private network / your private network / a role-like label for machines used in access rules.                               |
| **ServiceAccount / Role / RoleBinding**        | An identity for software / a set of permissions in one namespace / linking the two.                                                         |
| **GHCR**                                       | GitHub Container Registry, where the images live.                                                                                           |
| **Probe (liveness/readiness)**                 | Kubernetes health checks: "restart me if this fails" / "don't send traffic until this passes".                                              |
