# JordyLab Production Runbook

Exact, copy-pasteable procedures for operating JordyLab's self-managed k3s cluster on OVH. Written for a Fish
shell (no bash heredocs). Every section below that touches a live VPS, a billable OVH resource, DNS, or a real
credential is Jordy's to run himself — nothing in this repository's automation performs these steps.

See `docs/learn/` for the concept explanations behind each procedure here, and `docs/environments.md` for the
`local`/`prod` settings table.

## Contents

1. [Order the VPS](#1-order-the-vps)
2. [Harden the OS](#2-harden-the-os)
3. [Install k3s](#3-install-k3s)
4. [Join the VPS to Tailscale](#4-join-the-vps-to-tailscale)
5. [Install cluster add-ons](#5-install-cluster-add-ons)
6. [DNS & first TLS issuance](#6-dns--first-tls-issuance)
7. [Generate the age key](#7-generate-the-age-key)
8. [OVH Object Storage bucket](#8-ovh-object-storage-bucket)
9. [First-time CI access setup](#9-first-time-ci-access-setup)
10. [Deploy](#10-deploy)
11. [Rollback](#11-rollback)
12. [Logs](#12-logs)
13. [Rotate a secret](#13-rotate-a-secret)
14. [Age key lost (re-keying)](#14-age-key-lost-re-keying)
15. [Restore drill](#15-restore-drill)
16. [Full VPS rebuild](#16-full-vps-rebuild)
17. [Certificate troubleshooting](#17-certificate-troubleshooting)
18. [Disk cleanup](#18-disk-cleanup)
19. [k3s / OS upgrades](#19-k3s--os-upgrades)
20. [Release a version (one tag → GitHub Release → deploy)](#20-release-a-version-one-tag--github-release--deploy)

---

## 1. Order the VPS

**(operator step — billable OVH resource, do this yourself)**

1. At [ovhcloud.com](https://www.ovhcloud.com), order one **VPS-2** (4 vCores / 8 GB RAM / 75 GB NVMe).
2. Choose a **monthly, no-commitment** billing term (per `/speckit-clarify` — flexibility over the cheaper
   12-month upfront rate). Confirm the exact monthly price at checkout; it should stay near the quoted
   from-€7.21/mo figure but wasn't separately confirmed during research.
3. Pick the closest EU datacenter to where you'll put the Object Storage bucket (§8) — any EU region works
   identically; this is a checkout-time convenience choice, not an architectural one (spec.md Assumptions).
4. Choose a current Ubuntu LTS or Debian stable image.
5. Add your SSH public key at order time so root password login is never enabled in the first place.
6. Note the VPS's public IPv4 — you'll need it for DNS (§6) and the firewall (§2).

## 2. Harden the OS

**(operator step — do not run these commands yourself)**

SSH in as the image's default user, then:

1. Create a non-root sudo user and copy your SSH key to it; disable root SSH login and password
   authentication entirely (`PermitRootLogin no`, `PasswordAuthentication no` in `/etc/ssh/sshd_config`,
   then `systemctl restart sshd`).
2. Enable automatic security updates (`unattended-upgrades` on Debian/Ubuntu):
   ```
   sudo apt-get install -y unattended-upgrades
   sudo dpkg-reconfigure -plow unattended-upgrades
   ```
3. Firewall (`ufw`), allowing only what FR-019 requires:
   ```
   sudo ufw default deny incoming
   sudo ufw default allow outgoing
   sudo ufw allow 22/tcp
   sudo ufw allow 80/tcp
   sudo ufw allow 443/tcp
   sudo ufw enable
   ```
   Never open 10250 (kubelet), 8472/udp (flannel VXLAN), or 6443 (k3s API — reached only over
   Tailscale, §4/§9) to the public internet.

## 3. Install k3s

**(operator step — do not run these commands yourself)**

1. Install k3s at the version pinned in `research.md` §1:
   ```
   curl -sfL https://get.k3s.io | INSTALL_K3S_VERSION=v1.37.0+k3s1 sh -
   ```
2. Copy the kubeconfig to your laptop and point `KUBECONFIG` at it:
   ```
   scp your-user@<vps-ip>:/etc/rancher/k3s/k3s.yaml ~/.kube/jordylab.yaml
   sed -i '' 's/127.0.0.1/<vps-ip>/' ~/.kube/jordylab.yaml   # macOS sed; drop '' on Linux
   set -x KUBECONFIG ~/.kube/jordylab.yaml
   ```
3. Verify the bundled components:
   ```
   kubectl get pods -n kube-system
   ```
   Expect Traefik, CoreDNS, the `svclb-*` ServiceLB pods, and `local-path-provisioner` running.
4. Drop `deploy/host/traefik-helmchartconfig.yaml` onto the VPS to enable Traefik's Gateway API provider:
   ```
   scp deploy/host/traefik-helmchartconfig.yaml your-user@<vps-ip>:/tmp/
   ssh your-user@<vps-ip> "sudo mv /tmp/traefik-helmchartconfig.yaml /var/lib/rancher/k3s/server/manifests/traefik-config.yaml"
   ```
   k3s picks it up automatically — confirm with `kubectl get gtw,gatewayclass -A` shortly after.

## 4. Join the VPS to Tailscale

**(operator step — do not run these commands yourself)**

So the CI deploy job (§9, `deploy-prod.yml`) can reach the firewalled k3s API on port 6443 without exposing
it publicly (FR-019, `research.md` §8):

1. On the VPS:
   ```
   curl -fsSL https://tailscale.com/install.sh | sh
   sudo tailscale up --ssh
   ```
2. In the Tailscale admin console, tag the node (e.g. `tag:jordylab-vps`) and confirm your tailnet's ACLs
   allow a CI-side ephemeral node to reach it on port 6443 only.
3. Verify from another tailnet device: `curl -k https://<vps-tailscale-ip>:6443/healthz`.

## 5. Install cluster add-ons

**(operator step — do not run these commands yourself)**

Pinned versions from `research.md`:

```
helm repo add jetstack https://charts.jetstack.io
helm repo add cnpg https://cloudnative-pg.github.io/charts
helm repo update

helm install cert-manager jetstack/cert-manager \
  --namespace cert-manager --create-namespace \
  --version v1.21.2 \
  --set crds.enabled=true \
  --set config.enableGatewayAPI=true

helm install cnpg cnpg/cloudnative-pg \
  --namespace cnpg-system --create-namespace \
  --version 1.30.1

kubectl apply -f https://github.com/cloudnative-pg/plugin-barman-cloud/releases/latest/download/manifest.yaml
```

Then apply the ClusterIssuer, and once a domain exists, the full prod overlay:

```
kubectl apply -f deploy/k8s/bootstrap/cert-manager-clusterissuer.yaml
```

## 6. DNS & first TLS issuance

**(operator step — do this yourself)**

1. At your domain registrar (OVHcloud), create an `A` record for the domain pointing at the VPS's public
   IPv4 (from §1).
2. Wait for propagation (`dig +short <domain>` should return the VPS IP from multiple resolvers).
3. Deploy the prod overlay (§10) once the domain resolves. cert-manager will complete the HTTP-01 challenge
   automatically.
4. Verify:
   ```
   kubectl get certificate -n jordylab jordylab-tls
   curl -Iv https://<domain>/
   ```
   Expect `Ready: True` on the Certificate and a valid TLS handshake from curl.

## 7. Generate the age key

**(operator step — do not generate or store a real key yourself)**

1. Install `age` (v1.3.2, `research.md` §7), then:
   ```
   age-keygen -o jordylab-prod.age.key
   ```
2. Copy the `# public key:` line into `.sops.yaml`'s `age:` field, replacing the `age1REPLACE_...`
   placeholder, and commit that change (the public key is safe to commit).
3. Store the **private** key in two places, never a third:
   - Your password manager (the durable offline copy — losing this makes every prod secret
     unrecoverable).
   - The GitHub `production` environment, as a secret named `SOPS_AGE_KEY` (§9).
4. Delete the local `jordylab-prod.age.key` file once both copies are stored.

## 8. OVH Object Storage bucket

**(operator step — billable resource, do not create it yourself)**

1. In the OVHcloud control panel, create an Object Storage container (S3-compatible), ideally in the same
   region as the VPS (§1).
2. Generate S3 access/secret keys scoped to that container.
3. Add them to `deploy/k8s/overlays/prod/secrets.sops.yaml` under the S3 keys (§13, `contracts/secrets-schema.md`)
   — `sops` encrypt, commit, deploy.

## 9. First-time CI access setup

**(operator step — do not create these yourself)**

1. In the GitHub repo settings, create a `production` Environment with **Jordy** as the required reviewer.
2. Add these Environment secrets:
   - `SOPS_AGE_KEY` — the private key from §7.
   - `TS_OAUTH_CLIENT_ID` / `TS_OAUTH_CLIENT_SECRET` (or `TS_AUTHKEY`) — a Tailscale OAuth client / auth key
     scoped to join this tailnet as an ephemeral node (`tailscale/github-action@v4`).
3. Mint the namespace-scoped ServiceAccount token defined in `deploy/k8s/bootstrap/ci-deploy-rbac.yaml`:
   ```
   kubectl apply -f deploy/k8s/bootstrap/ci-deploy-rbac.yaml
   kubectl -n jordylab create token ci-deploy --duration=8760h
   ```
   Add the result as the `KUBE_TOKEN` Environment secret, and the cluster's CA + API server address
   (reachable over the tailnet from §4) as `KUBE_SERVER` / `KUBE_CA_CERT`.
4. Never use `/etc/rancher/k3s/k3s.yaml` (the cluster-admin kubeconfig) in CI.

## 10. Deploy

Production changes only on a **release** (§20): merging to `main` builds and tests the images but deploys
nothing.

1. Merge to `main`; `build.yml` tests, scans and publishes `ghcr.io/jordy-swinnen/jordylab-*:sha-<full-sha>`.
2. When `main` is green, tag it: `git tag v0.1.0 && git push origin v0.1.0` (or GitHub → Releases → *Draft a new
   release*). `release.yml` verifies, retags, creates the Release and calls `deploy-prod.yml`.
3. Approve the `production` deployment in that run (required reviewer, §9). The job joins Tailscale, decrypts
   secrets, applies the prod overlay with the release's images, and waits on `kubectl rollout status` — a failed
   rollout fails the job loudly.
4. Confirm: `kubectl -n jordylab get pods`, then the `quickstart.md` US1 checks.

## 11. Rollback

Preferred: GitHub → Actions → *Deploy to Production* → *Run workflow* → enter the previous release tag (e.g.
`v0.0.9`) → approve. Or from a terminal:
```
gh workflow run deploy-prod.yml --repo jordy-swinnen/JordyLab -f version=v0.0.9
```
Emergency path (no pipeline):
```
kubectl -n jordylab rollout undo deploy/backend
kubectl -n jordylab rollout undo deploy/frontend
kubectl -n jordylab rollout undo deploy/keycloak
```
Both should restore a healthy previous version within 5 minutes (SC-002).

## 12. Logs

```
kubectl -n jordylab logs deploy/backend -f
kubectl -n jordylab logs deploy/frontend -f
kubectl -n jordylab logs deploy/keycloak -f
kubectl -n jordylab logs deploy/ntfy -f
kubectl -n jordylab logs cnpg-cluster-1 -c postgres -f        # the database (cluster `cnpg-cluster`, namespace `jordylab`)
kubectl -n cnpg-system logs deploy/cnpg-cloudnative-pg -f   # the CloudNativePG operator itself
```

## 13. Rotate a secret

1. `SOPS_EDITOR="idea --wait" sops deploy/k8s/overlays/prod/secrets.sops.yaml` (from the repo root) — opens the
   decrypted content in a temp file in IntelliJ; edit the value, save, close the tab, and `sops` re-encrypts on
   exit. Without `SOPS_EDITOR`, sops falls back to `$EDITOR`.
2. Commit and push the updated ciphertext.
3. Deploy as usual (§10) — the new value reaches the cluster as an updated `Secret`.
4. Restart the pods that read it so they pick it up:
   ```
   kubectl -n jordylab rollout restart deploy/backend deploy/keycloak
   ```

## 14. Age key lost (re-keying)

If the private key is gone from **both** the password manager and the GitHub environment secret:

1. Every value in `secrets.sops.yaml` is unrecoverable as ciphertext — you must know the plaintext values
   independently (e.g. re-generate DB/Keycloak credentials, re-fetch API keys from their providers).
2. Generate a **new** age key (§7).
3. Re-create `secrets.sops.yaml` from scratch with the new key's public half in `.sops.yaml`, and the
   (re-obtained) plaintext values.
4. Update the GitHub environment secret and your password manager with the new private key.
5. Deploy (§10) to roll every affected credential.

## 15. Restore drill

Mandatory once before go-live, then quarterly (FR-015, `/speckit-clarify`). Takes ~10 minutes; must stay under 30
(SC-004). The manifest is `deploy/k8s/drills/restore-drill-cluster.yaml` — a separate `cnpg-restore-drill`
cluster recovered from the same ObjectStore, with no WAL archiving of its own.

```bash
export KUBECONFIG=~/.kube/jordylab.yaml
date -u +%FT%TZ                                                    # start time
kubectl -n jordylab apply -f deploy/k8s/drills/restore-drill-cluster.yaml
kubectl -n jordylab wait cluster/cnpg-restore-drill --for=condition=Ready --timeout=20m
date -u +%FT%TZ                                                    # ready time
```

Compare row counts between production and the restored copy. Run this only after the `wait` above returned
(the cluster is Ready, so recovery has finished); the queries are read-only, and writes after the last archived
WAL segment may differ slightly. The drill checks the data, not the app: unlike the earlier plan, no throwaway
backend is pointed at the restored database. Row counts plus Flyway history show the schemas and data came back,
which is what FR-015 asks of a backup.

```bash
Q="select 'gamecatalog.game', count(*) from gamecatalog.game union all select 'keycloak.user_entity', count(*) from keycloak.user_entity union all select 'flyway history', count(*) from flyway_schema_history"
kubectl -n jordylab exec cnpg-cluster-1 -c postgres -- psql -U postgres -d jordylab -At -c "$Q"
kubectl -n jordylab exec cnpg-restore-drill-1 -c postgres -- psql -U postgres -d jordylab -At -c "$Q"
```

Tear down and log the drill (date, recovery point, duration, result) in the table below:

```bash
kubectl -n jordylab delete cluster cnpg-restore-drill
kubectl -n jordylab get pvc | grep restore-drill || echo "drill volume removed"
```

| Date | Recovered from | Ready after | Row counts match | By | Next due |
|------|----------------|-------------|------------------|----|----------|
| 2026-10-01 | base backup `cnpg-daily-backup-20261001030000` (03:00 UTC) + archived WAL up to ~09:53 UTC | 1 min 55 s (09:53:35 → 09:55:30 UTC) | yes — `gamecatalog.game` 128/128, `keycloak.user_entity` 6/6, `flyway_schema_history` 20/20 | agent (approved by Jordy) | 2027-01-01 (quarterly, FR-015) |

## 16. Full VPS rebuild

If the VPS is lost entirely:

1. Order and harden a new VPS (§1–§2).
2. Install k3s at the same pinned version (§3), join Tailscale (§4), install the same add-on versions (§5).
3. Re-create `.sops.yaml`'s age key relationship: use the offline private key copy (§7) — it doesn't change.
4. `kubectl apply -k deploy/k8s/overlays/prod` from a clean checkout of `main`.
5. Restore the database from the latest Object Storage backup (§15's recovery procedure, targeting "now"
   instead of a specific point in time).
6. Re-point DNS at the new VPS's IP (§6) and wait for a fresh certificate to issue.
7. Everything else (game artwork, APKs) was only ever on the old VPS's local-path disk — it does **not**
   survive a full VPS loss; only the database survives, via Object Storage (spec.md edge case).

## 17. Certificate troubleshooting

```
kubectl get certificate -n jordylab jordylab-tls -o wide
kubectl describe certificate -n jordylab jordylab-tls
kubectl get challenges -n jordylab
kubectl logs -n cert-manager deploy/cert-manager -f
```
Common causes: DNS not yet propagated (§6), the `Gateway`'s HTTP listener not reachable on port 80
(firewall, §2), or `letsencrypt-prod`'s rate limits after repeated failed attempts — switch temporarily to
Let's Encrypt's staging `ClusterIssuer` server while debugging to avoid burning production rate limits.

## 18. Disk cleanup

75 GB total (images + `local-path` volumes). Check and reclaim:
```
sudo k3s crictl images
sudo k3s crictl rmi --prune
du -sh /var/lib/rancher/k3s/storage/*
df -h /var/lib/rancher/k3s
```

## 19. k3s / OS upgrades

1. OS: `sudo apt-get update && sudo apt-get upgrade -y`, reboot if a kernel update requires it. A single-node
   reboot causes brief documented downtime (spec.md edge case) — everything comes back without manual steps.
2. k3s: re-run the install script with a newer pinned version:
   ```
   curl -sfL https://get.k3s.io | INSTALL_K3S_VERSION=<new-version> sh -
   ```
   Check `research.md` and the k3s release notes for the current stable tag before bumping the pin.
3. After either, verify: `kubectl get nodes`, `kubectl -n jordylab get pods`, then the `quickstart.md` checks.

## 20. Release a version (one tag → GitHub Release → deploy)

**Status: implemented 2026-09-30** (spec 011 BUG-008). Releasing is one action on GitHub, covering the web app
and the Android APK together.

### The flow

1. Merge to `main` as usual. `build.yml` tests, scans, and publishes the three images tagged `sha-<full-sha>`.
   A push to `main` **does not deploy**; production only changes on a release.
2. Release by pushing a tag on a commit that is on `main` and has a green Build:
   ```
   git tag v0.1.0
   git push origin v0.1.0
   ```
   (or GitHub → Releases → *Draft a new release* → create the tag there). Tags are `vX.Y.Z` or `vX.Y.Z-rcN`
   (release candidate → GitHub prerelease). Minor/patch ≤ 99, rc ≤ 98.
3. `.github/workflows/release.yml` runs on `v*` tags:
   - **verify** — the tag's commit is on `main` and a Build of a push to `main` succeeded for it (else it fails);
   - **retag** — the tested images `sha-<sha>` get the tag `vX.Y.Z` (`docker buildx imagetools create`), nothing is
     rebuilt, so what ships is exactly what was tested;
   - **release** — a *draft* GitHub Release with generated notes (prerelease for `-rcN`);
   - **deploy** — calls `deploy-prod.yml` with the tag; it waits for your `production` approval (§9) and deploys
     `ghcr.io/jordy-swinnen/jordylab-*:vX.Y.Z`;
   - **publish** — the draft becomes public only once the deploy succeeded (a rejected or failed deploy leaves
     only a draft, which you can delete);
   - **apk** — builds and signs the Android APK with `versionName` X.Y.Z and
     `versionCode` = MAJOR·1000000 + MINOR·10000 + PATCH·100 + (rcN, or 99 for a final release), so every tag
     installs as an update; publishes it to `POST /api/mobile/releases` as the `mobile-release-ci` service
     account. The APK is **not** attached to the GitHub Release: the repo is public and spec 007 FR-002 allows
     downloads only for approved users (signed, short-lived links).
4. Verify with the `quickstart.md` US1 checks.

### Deploy or roll back to any version

See §11: *Deploy to Production* → *Run workflow* with the release tag. `deploy-prod.yml` accepts only
`vX.Y.Z[-rcN]` and deploys that tag's images and manifests.

### APK publish credentials

The `apk` job logs in as `mobile-release-ci`. Its GitHub secret must equal the value Keycloak uses, which comes
from `jordylab-secrets/MOBILE_RELEASE_CI_CLIENT_SECRET`. Set it from the cluster, so it is never
displayed or retyped. The `[ -n "$V" ]` guard matters: if `kubectl` fails (e.g. no `KUBECONFIG` in that shell), a
plain pipe would store an **empty** secret.

```bash
V=$(KUBECONFIG=~/.kube/jordylab.yaml kubectl -n jordylab get secret jordylab-secrets -o jsonpath='{.data.MOBILE_RELEASE_CI_CLIENT_SECRET}') && [ -n "$V" ] && printf '%s' "$V" | base64 -d | gh secret set MOBILE_RELEASE_CI_CLIENT_SECRET --repo jordy-swinnen/JordyLab; unset V
```

### GitHub settings (owner only)

Tag ruleset: only Jordy (and the agent acting for him) create `v*` tags. Branch ruleset on `main`: require PRs and
the Build checks.
