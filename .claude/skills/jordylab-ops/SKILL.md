---
name: jordylab-ops
description: JordyLab production operations — OVH VPS + k3s, Tailscale access, kubectl/helm, GitHub Actions deploys, approvals and rollbacks, SOPS + age secrets (edited by Jordy in IntelliJ), Keycloak admin via kcadm, CloudNativePG backups, TLS and Gateway routing. Load before running, proposing or explaining anything that touches the live cluster, the deploy pipeline, prod secrets or the prod Keycloak realm.
---

# JordyLab production operations

Everything needed to inspect, deploy, roll back and repair **JordyLab's production deployment**: where it
runs, how the pieces fit, and the exact commands. Jordy is an experienced application developer but new to
operations: explain *why* briefly when you propose something, and prefer commands he can copy-paste.

This skill is the single source of truth for production operations. The `jordylab-devops` subagent (Claude
Code and OpenCode) only loads it; edit operational knowledge here, not in the agents.

The full narrative of how this was built is in `docs/runbooks/vps-k3s-deployment/README.md`
(generic, teachable version) and `docs/runbook.md` (project runbook). Read them when you need more
depth than this file gives.

## Safety rules (always)

1. **Read before you change.** Diagnose with read-only commands first (`get`, `describe`, `logs`,
   `kustomize build`). Quote the error you found before proposing a fix.
2. **Mutations need Jordy's explicit yes**: anything that runs `kubectl apply/delete/patch/set/rollout
   restart/exec … kcadm (write)`, `helm install/upgrade/uninstall`, `ssh` commands that change the
   server, `ufw`, Tailscale policy changes, GitHub environment/secret changes, or approving a deploy.
   Show the exact command and what it changes, then wait. Exception: when Jordy has explicitly delegated
   deploy approval for the current task (e.g. the spec 011 E2E campaign), approve only commits you merged
   yourself with a green Build, and still pause for Flyway migrations, realm changes and secret/config changes.
3. **Secrets never pass through the conversation.** Never print, echo, `cat`, or decrypt secret values
   to the screen. Pipe values (`… | gh secret set …`), run `kcadm` inside the Keycloak pod using the
   env vars already there, use `gitleaks --redact`, and filter logs with
   `grep -v -i -E 'password|secret'`. `~/.kube/jordylab.yaml` is cluster-admin: never show its content.
4. **Production changes go through Git** (PR → merge → Build → approved deploy). A live
   `kubectl set env` / `patch` is fine to *test* a fix quickly, but the same change must land in
   the repo, or the next deploy undoes it.
5. **Verify versions against the live source** (Helm repo, Docker Hub, GHCR, release pages) before
   pinning anything. Say so when you couldn't.
6. The k3s API is only on Tailscale. On Jordy's Mac `kubectl` with `~/.kube/jordylab.yaml` usually works;
   if your shell has no working `kubectl`, give Jordy the commands to run and ask for the output.
7. **Never open, print or save decrypted secret content**, and never read Secret values from the cluster —
   hand Jordy the IntelliJ `sops` command (see Secrets) and the key names to add. The only decrypt you may
   run is the integrity check `sops -d … >/dev/null && echo DECRYPT_OK`, whose output is discarded.

## Where it runs

| Thing            | Value                                                                                                                 |
|------------------|-----------------------------------------------------------------------------------------------------------------------|
| Public URL       | `https://jordylab.be` (DNS: OVH zone, A record → VPS IPv4)                                                            |
| Server           | OVH VPS-2 (4 vCPU / 8 GB / 75 GB NVMe), London (UK), Ubuntu 26.04 LTS                                                 |
| Public IPv4      | `57.129.163.110` — SSH user `ubuntu`, key-only                                                                        |
| Admin network    | Tailscale; server = `jordylab-vps` (tag `tag:jordylab-vps`, key expiry disabled)                                      |
| k3s API          | `https://jordylab-vps.tailec3187.ts.net:6443` — only reachable over Tailscale                                         |
| Kubeconfig (Mac) | `~/.kube/jordylab.yaml` → `export KUBECONFIG=~/.kube/jordylab.yaml`                                                   |
| App namespace    | `jordylab`                                                                                                            |
| Backups          | OVH Object Storage, bucket `jordylab-cnpg-backups`, Gravelines (GRA), `https://s3.gra.io.cloud.ovh.net`               |
| Images           | `ghcr.io/jordy-swinnen/jordylab-{backend,frontend,keycloak}:sha-<full-commit-sha>` (public)                           |
| CI/CD            | GitHub Actions `Build` → `Deploy to Production` (environment `production`, reviewer Jordy, `main` only)               |
| TLS              | cert-manager + Let's Encrypt (`letsencrypt-prod`, account e-mail jordy.swinnen@pm.me)                                 |
| Secrets          | SOPS + age; private key at `~/Library/Application Support/sops/age/keys.txt` (+ password manager + GitHub env secret) |

Versions at first deploy (2026-09-30): k3s v1.36.4+k3s1 (stable channel), bundled Traefik 3.7.8,
cert-manager v1.21.2, CloudNativePG operator 1.30.1 (chart 0.29.1), Barman Cloud plugin v0.15.0
(chart 0.8.0), PostgreSQL 16.15 (`ghcr.io/cloudnative-pg/postgresql:16.15-standard-trixie`),
Keycloak 26.7.4, nginx-unprivileged 1.30, ntfy v2.28.0.

## Architecture

```
Internet ──► VPS :80/:443 (ufw: only 22, 80, 443 public)
             └─ k3s ─ Traefik (Gateway API, entryPoints web:8000 / websecure:8443)
                      Gateway jordylab-gateway (TLS secret jordylab-tls)
                        /                          → frontend  (nginx :8080, Angular build)
                        /api, /.well-known/assetlinks.json → backend (Spring Boot :8080)
                        /auth/realms|resources|.well-known → keycloak (:8080, mgmt :9000)
                        /ntfy (prefix stripped)    → ntfy (:80)
                        http listener: redirect → https (cert-manager's HTTP-01 route wins)
                      cnpg-cluster (PostgreSQL 16, 1 instance, local-path 10Gi)
                        db `jordylab` (owner jordylab) + schema `keycloak` (owner keycloak)
                        └─ Barman Cloud plugin → S3 GRA (WAL continuous, base daily 03:00 UTC
                           + weekly Sun 04:00 UTC, retention 30d)
Laptop / CI ──Tailscale──► :6443 k3s API (CI node tag:ci may reach only tcp:6443 on the server)
```

## Repo map (deployment)

| Path                                                          | What                                                                                                     | Applied by                                                                 |
|---------------------------------------------------------------|----------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------|
| `deploy/host/traefik-helmchartconfig.yaml`                    | Enables Traefik's Gateway provider                                                                       | Copied once to `/var/lib/rancher/k3s/server/manifests/traefik-config.yaml` |
| `deploy/k8s/bootstrap/`                                       | Namespace, ClusterIssuers (prod + staging), CI ServiceAccount/Role/RoleBinding + token Secret            | Jordy, admin kubeconfig: `kubectl apply -k`                                |
| `deploy/k8s/base/`                                            | Deployments, Services, PVCs, Gateway, HTTPRoutes                                                         | CI via overlay                                                             |
| `deploy/k8s/cluster/`                                         | CNPG `Cluster`, `ObjectStore`, `ScheduledBackup`s, `Certificate`                                         | CI via overlay                                                             |
| `deploy/k8s/overlays/prod/`                                   | `namespace: jordylab`, ConfigMaps (`backend-config`, `keycloak-config`), image tags, `secrets.sops.yaml` | CI                                                                         |
| `deploy/containers/{backend,frontend,keycloak}/Containerfile` | Image builds                                                                                             | Build workflow                                                             |
| `deploy/keycloak/realm-prod.json`                             | Realm, baked into the Keycloak image, imported on first start                                            | Keycloak `--import-realm`                                                  |
| `.sops.yaml`                                                  | age public key + `encrypted_regex: ^(data\|stringData)$`                                                 | sops                                                                       |
| `.github/workflows/build.yml`, `deploy-prod.yml`              | CI/CD                                                                                                    | GitHub                                                                     |

Secrets in `secrets.sops.yaml`: `jordylab-secrets` (Keycloak bootstrap admin, `KEYCLOAK_BACKEND_CLIENT_SECRET`,
Anthropic/OpenRouter keys, S3 keys, Steam, IGDB, `NTFY_TOKEN`), `jordylab-db-app` and
`jordylab-db-keycloak` (basic-auth: `username` + `password`). Contract:
`specs/008-ovh-k8s-deployment/contracts/secrets-schema.md`.

## How the pieces fit (things that must stay true)

- **Gateway listeners use 8000/8443**, Traefik's internal entryPoint ports — not 80/443.
- **No HTTP→HTTPS redirect at Traefik's entryPoint**; the redirect is an HTTPRoute, so Let's Encrypt HTTP-01 works.
- **Cluster-scoped objects live in `bootstrap/`**; CI's token is namespace-scoped (Role allows core, apps,
  gateway.networking.k8s.io, cert-manager.io, postgresql.cnpg.io, barmancloud.cnpg.io, batch).
- **One password, one Secret**: CNPG creates the DB owner from `jordylab-db-app` and syncs the `keycloak`
  role from `jordylab-db-keycloak`; backend and Keycloak read the same Secrets.
  `postInitSQL` runs in the `postgres` DB (creates role), `postInitApplicationSQL` in `jordylab` (creates schema).
- **CNPG `ScheduledBackup.schedule` is 6-field cron, seconds first** (`"0 0 3 * * *"`). Retention is only the
  ObjectStore's `retentionPolicy: 30d`.
- **pgvector comes from the CNPG `standard` image**; ImageVolume extensions need PostgreSQL 18+.
- **Keycloak build-time options are baked into the image** (`KC_DB`, `KC_HEALTH_ENABLED`, `KC_METRICS_ENABLED`,
  `KC_HTTP_RELATIVE_PATH=/auth`, `KC_HTTP_MANAGEMENT_RELATIVE_PATH=/`); `start --optimized` refuses a differing
  runtime value. Runtime (ConfigMap): `KC_HOSTNAME=https://jordylab.be/auth`, `KC_HTTP_RELATIVE_PATH=/auth`,
  `KC_HTTP_ENABLED=true`, `KC_PROXY_HEADERS=xforwarded`, `KC_PROXY_TRUSTED_ADDRESSES=10.42.0.0/16`,
  `PRODUCTION_DOMAIN=jordylab.be`. Probes: `:9000/health/{ready,live}`.
- **Realm import runs once** (skipped when the realm exists). Later realm changes: admin console or `kcadm.sh`,
  and record each one in the table in `deploy/keycloak/README.md` together with the realm-file change.
  Placeholders use `${VAR}`. The admin console **and the Admin REST API** (`/auth/admin/**`) are **not** routed
  publicly — so the backend calls Keycloak's Admin API in-cluster via `KEYCLOAK_INTERNAL_URL`
  (`http://keycloak:8080/auth`, `backend-config`), while browsers and the downloaded scan client use the public
  `KEYCLOAK_URL`. A public `/auth/admin/…` URL answers with the SPA's `index.html` (HTTP 200, HTML).
- **`jordylab-backend` has `fullScopeAllowed=false`**: its service-account token only carries the
  `realm-management` roles that are scope-mapped (`view-users`, `manage-users`, `view-realm`). A missing mapping
  shows up as a 403 from the Admin REST API.
- **`admin` is a composite of `guest` + `gamecatalog-scanner`**; `mobile-release-publisher` is CI-only.
- **Backend probes** `/actuator/health/{liveness,readiness}` are `permitAll` in `SecurityConfig`.
- **Frontend image** copies `dist/apps/jordylab/browser` (Angular application builder output) into nginx.
- **ntfy has no `NTFY_BASE_URL`** (sub-path hosting unsupported); a subdomain is needed for attachments/web push.
- **Deploy workflow**: only for `Build` runs from pushes to `main`; checks out the deployed SHA; pinned
  sops 3.13.3 / age 1.3.2 / kustomize 5.8.1 / kubectl 1.36.4 (keep kubectl within ±1 minor of k3s).
- **Build workflow**: images only on push to `main`; image matrix `fail-fast: false`.

## Command reference

All `kubectl` commands assume `export KUBECONFIG=~/.kube/jordylab.yaml` and Tailscale connected on the Mac.
In zsh, don't paste lines that start with `#` (interactive comments are off).

### Access and health of the platform

```bash
export KUBECONFIG=~/.kube/jordylab.yaml
tailscale status | grep jordylab-vps               # server reachable over Tailscale?
kubectl get nodes -o wide
kubectl version                                    # client within ±1 minor of server
which -a kubectl; hash -r                          # wrong kubectl version? find/clear the old one
kubectl get pods -A
kubectl get gatewayclass,gateway -A                # traefik ACCEPTED / gateway PROGRAMMED
kubectl get clusterissuer                          # letsencrypt-prod/staging READY True
helm list -A
ssh ubuntu@57.129.163.110                          # server shell (key-only)
ssh ubuntu@57.129.163.110 'sudo ufw status verbose'
ssh ubuntu@57.129.163.110 'tailscale ip -4; sudo systemctl status k3s --no-pager | head -5'
```

Re-fetch the kubeconfig (e.g. new laptop):

```bash
mkdir -p ~/.kube
ssh ubuntu@57.129.163.110 'sudo cat /etc/rancher/k3s/k3s.yaml' \
  | sed 's#https://127.0.0.1:6443#https://jordylab-vps.tailec3187.ts.net:6443#' > ~/.kube/jordylab.yaml
chmod 600 ~/.kube/jordylab.yaml
```

### App status and debugging

```bash
kubectl -n jordylab get pods,cluster,certificate,gateway,httproute
watch -n 5 'kubectl -n jordylab get pods,cluster,pvc'      # kubectl -w only takes one resource type
kubectl -n jordylab logs deploy/<backend|frontend|keycloak|ntfy> --tail=100
kubectl -n jordylab logs deploy/<name> --previous --tail=60     # the crashed attempt
kubectl -n jordylab describe pod -l app=<name> | sed -n '/Events:/,$p'
kubectl -n jordylab get events --sort-by=.lastTimestamp | tail -25
kubectl -n jordylab rollout status deploy/<name> --timeout=5m
kubectl -n jordylab get deploy <name> -o jsonpath='{.spec.template.spec.containers[0].image}{"\n"}'
kubectl -n kube-system logs deploy/traefik --tail=50
kubectl -n cert-manager logs deploy/cert-manager --tail=50
```

Test a runtime fix live (then commit the same change):

```bash
kubectl -n jordylab set env deploy/<name> KEY=value
kubectl -n jordylab rollout status deploy/<name> --timeout=5m
kubectl -n jordylab rollout restart deploy/<name>          # restart without changes
```

### Manifests

```bash
kubectl kustomize deploy/k8s/bootstrap | grep -E '^kind:' | sort | uniq -c
kubectl kustomize deploy/k8s/overlays/prod | grep -E '^kind:' | sort | uniq -c
kubectl apply -k deploy/k8s/bootstrap                      # admin only, when bootstrap/ changes
# Traefik config change (k3s re-applies it; Traefik restarts ~1 min):
ssh ubuntu@57.129.163.110 'sudo tee /var/lib/rancher/k3s/server/manifests/traefik-config.yaml >/dev/null' \
  < deploy/host/traefik-helmchartconfig.yaml
```

### Deploy, watch, roll back (GitHub)

```bash
gh run list --repo jordy-swinnen/JordyLab --branch main --limit 5
gh run view <run-id> --repo jordy-swinnen/JordyLab --json status,headSha,event
gh run view <run-id> --repo jordy-swinnen/JordyLab --log-failed | tail -40
gh run rerun <run-id> --repo jordy-swinnen/JordyLab --failed
# Rollback / redeploy a specific commit (then approve in Actions). Always pass sha — an empty sha deploys tag `sha-`:
gh workflow run "Deploy to Production" --repo jordy-swinnen/JordyLab -f sha=<full-commit-sha>
# Approve a pending deploy via the API (only when delegated, see safety rule 2):
ENV=$(gh api repos/jordy-swinnen/JordyLab/actions/runs/<run-id>/pending_deployments --jq '.[0].environment.id')
gh api -X POST repos/jordy-swinnen/JordyLab/actions/runs/<run-id>/pending_deployments -F "environment_ids[]=$ENV" -f state=approved -f comment="<why>"
gh pr create --base main --fill
```

Approve: GitHub → Actions → Deploy to Production → the run → *Review deployments* → `production` → *Approve and deploy*.

Check images exist and are public (anonymous pull):

```bash
for img in backend frontend keycloak; do
  T=$(curl -s "https://ghcr.io/token?scope=repository:jordy-swinnen/jordylab-$img:pull" | python3 -c 'import sys,json;print(json.load(sys.stdin).get("token",""))')
  printf '%s: ' $img; curl -s -H "Authorization: Bearer $T" "https://ghcr.io/v2/jordy-swinnen/jordylab-$img/tags/list" | head -c 300; echo
done
```

### Secrets (SOPS + age)

```bash
SOPS_EDITOR="idea --wait" sops deploy/k8s/overlays/prod/secrets.sops.yaml            # Jordy's default: IntelliJ
SOPS_EDITOR="code --profile sops --wait" sops deploy/k8s/overlays/prod/secrets.sops.yaml   # alternative: VS Code
sops -d deploy/k8s/overlays/prod/secrets.sops.yaml >/dev/null && echo DECRYPT_OK   # integrity check only (rule 7)
openssl rand -base64 33 | tr -d '/+='                      # generate a password (don't paste it anywhere)
gitleaks git --no-banner --redact .
gitleaks dir --no-banner --redact .                        # hits in gitignored .env / .nx cache are expected
```

**Jordy edits secrets in IntelliJ** — always hand him the `SOPS_EDITOR="idea --wait"` form, run from the repo
root. `idea` is the JetBrains Toolbox launcher (on his `PATH`); `--wait` makes sops block until he closes the
IntelliJ tab, then sops re-encrypts on exit (sops ≥ 3.9 reads `SOPS_EDITOR` before `EDITOR`). Never run this
yourself: the decrypted file must only ever open in his editor. New keys under `jordylab-secrets.stringData`
reach the backend automatically (`envFrom`); the Keycloak pod only sees keys wired explicitly in
`deploy/k8s/base/keycloak.yaml`. Editors can keep plaintext in local history: clear IntelliJ Local History for
the temp file afterwards, or use the VS Code `sops` profile (`workbench.localHistory.enabled: false`).

### GitHub `production` environment

```bash
REPO=jordy-swinnen/JordyLab
gh secret list --env production --repo $REPO     # KUBE_CA_CERT KUBE_SERVER KUBE_TOKEN SOPS_AGE_KEY TS_OAUTH_CLIENT_ID TS_OAUTH_CLIENT_SECRET
gh api repos/$REPO/environments/production --jq '{reviewers: [.protection_rules[]?.reviewers[]?.reviewer.login], branch_policy: .deployment_branch_policy}'
# Rotate the CI token:
kubectl -n jordylab delete secret ci-deploy-token && kubectl apply -k deploy/k8s/bootstrap
kubectl -n jordylab get secret ci-deploy-token -o jsonpath='{.data.token}' | base64 -d | gh secret set KUBE_TOKEN --env production --repo $REPO
# Other secret sources (always piped / prompted, never echoed):
kubectl config view --raw --minify -o jsonpath='{.clusters[0].cluster.certificate-authority-data}' | gh secret set KUBE_CA_CERT --env production --repo $REPO
printf 'https://100.93.81.41:6443' | gh secret set KUBE_SERVER --env production --repo $REPO
grep '^AGE-SECRET-KEY-' "$HOME/Library/Application Support/sops/age/keys.txt" | gh secret set SOPS_AGE_KEY --env production --repo $REPO
gh secret set TS_OAUTH_CLIENT_ID --env production --repo $REPO       # prompts
gh secret set TS_OAUTH_CLIENT_SECRET --env production --repo $REPO   # prompts; starts with tskey-client-
```

Tailscale CI credential: admin console → Settings → *Trust credentials* → OAuth, scope **Keys → Auth Keys → Write**
only, tag `tag:ci`. The secret is shown once; revoke the old credential after replacing it.

### Keycloak

```bash
# Admin console (not public): then open http://localhost:8080/auth/admin
kubectl -n jordylab port-forward deploy/keycloak 8080:8080
```

`kcadm` inside the pod, logging in with the bootstrap admin env vars (no password on screen):

```bash
kubectl -n jordylab exec deploy/keycloak -- bash -c '
K=/opt/keycloak/bin/kcadm.sh; C="--config /tmp/kc.cfg"
$K config credentials $C --server http://localhost:8080/auth --realm master \
  --user "$KC_BOOTSTRAP_ADMIN_USERNAME" --password "$KC_BOOTSTRAP_ADMIN_PASSWORD" >/dev/null 2>&1 && echo LOGIN_OK
# examples — pick what you need:
$K add-roles -r jordylab $C --uusername <email> --rolename admin
$K get-roles -r jordylab $C --uusername <email> --effective --fields name --format csv --noquotes
$K get client-scopes -r jordylab $C --fields name
$K get realms/jordylab/default-default-client-scopes $C --fields name
ID=$($K get clients -r jordylab -q clientId=jordylab-host $C --fields id --format csv --noquotes)
$K get clients/$ID/default-client-scopes -r jordylab $C --fields name --format csv --noquotes'
```

New users self-register (e-mail = username, password ≥ 12 chars) and have no role until an admin grants
one; they must log out/in to get the new role in their token. The bootstrap admin is temporary — replace it
with a permanent `master`-realm admin via the console.

### Database and backups (CloudNativePG)

```bash
kubectl -n jordylab get cluster cnpg-cluster                       # "Cluster in healthy state"
kubectl -n jordylab get scheduledbackup,backup
kubectl -n jordylab get objectstore ovh-object-storage -o yaml | sed -n '/status:/,$p'
kubectl -n cnpg-system get pods; kubectl -n cnpg-system get certificate
kubectl -n jordylab logs cnpg-cluster-1 -c plugin-barman-cloud --tail=50   # backup sidecar
kubectl -n jordylab patch scheduledbackup cnpg-daily-backup --type merge -p '{"spec":{"schedule":"0 0 3 * * *"}}'
# On-demand backup:
kubectl -n jordylab apply -f - <<'EOF'
apiVersion: postgresql.cnpg.io/v1
kind: Backup
metadata: { name: manual-backup-<yyyymmdd> }
spec:
  cluster: { name: cnpg-cluster }
  method: plugin
  pluginConfiguration: { name: barman-cloud.cloudnative-pg.io }
EOF
```

Restore drill (not yet performed as of 2026-09-30 — required before trusting backups; the first base backup
runs at 03:00 UTC after go-live because the schedules have `immediate: false`): create a second `Cluster` with
`bootstrap.recovery` from the `ovh-object-storage` ObjectStore (plugin `barman-cloud.cloudnative-pg.io`,
optional `recoveryTarget.targetTime`), verify data, delete it. See `docs/runbook.md` §15.

### Cluster add-ons (Helm)

```bash
helm repo add jetstack https://charts.jetstack.io
helm repo add cnpg https://cloudnative-pg.github.io/charts
helm repo update
helm search repo jetstack/cert-manager --versions | head -4
helm search repo cnpg/cloudnative-pg --versions | head -4          # CHART version ≠ APP version
helm search repo cnpg/plugin-barman-cloud --versions | head -4
helm upgrade cert-manager jetstack/cert-manager -n cert-manager --version <chart> --reuse-values --wait
helm upgrade cnpg cnpg/cloudnative-pg -n cnpg-system --version <chart> --wait
helm upgrade barman-cloud cnpg/plugin-barman-cloud -n cnpg-system --version <chart> --wait
```

Order for a fresh cluster: cert-manager (`--set crds.enabled=true --set config.enableGatewayAPI=true`) →
CNPG → Barman Cloud plugin (needs cert-manager).

### Server, k3s, Tailscale

```bash
curl -s https://update.k3s.io/v1-release/channels | grep -o '"name":"stable","latest":"[^"]*"'
# k3s install/upgrade (on the server; keep the TLS SANs):
curl -sfL https://get.k3s.io | INSTALL_K3S_CHANNEL=stable sh -s - server \
  --tls-san 100.93.81.41 --tls-san jordylab-vps.tailec3187.ts.net
```

ufw rules in place: default deny incoming; allow OpenSSH, 80/tcp, 443/tcp; allow from 10.42.0.0/16 and
10.43.0.0/16 (k3s pod/service networks); allow in on `tailscale0` to port 6443/tcp. SSH hardening lives in
`/etc/ssh/sshd_config.d/00-hardening.conf` (no passwords, no root login) — always keep a second session open
when changing it.

Tailscale policy: `tagOwners` for `tag:jordylab-vps` and `tag:ci`; grants: members → everything,
`tag:ci` → `tag:jordylab-vps` `tcp:6443` only; a `tests` block enforces it on save.

### DNS and external checks

```bash
dig +short jordylab.be @1.1.1.1                   # → 57.129.163.110
whois jordylab.be | grep -i -E 'status|nameserver' -A2
curl -sI http://jordylab.be | head -3             # 301 → https
curl -s https://jordylab.be/auth/realms/jordylab/.well-known/openid-configuration | head -c 200
curl -s -o /dev/null -w '%{http_code}\n' https://jordylab.be/auth/admin/   # must NOT be Keycloak
nmap -Pn 57.129.163.110                           # only 22, 80, 443 open
```

## Open items (as of 2026-09-30)

- Restore drill not yet performed.
- Replace Keycloak's temporary bootstrap admin with a permanent admin.
- Restrict the OVH Object Storage user to the single bucket (currently project-wide operator role).
- ntfy subdomain if attachments / web push are needed.
- Planned release flow (`v*` tag → GitHub Release → approved deploy): `docs/runbook.md` §20.
- Turn on VPS auto-renewal at OVH; watch domain renewal.
