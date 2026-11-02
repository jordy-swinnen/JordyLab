# Research: Production on Docker Compose instead of k3s

Facts checked against the repository on 2026-11-02, for the draft in `spec-draft.md`. Each fact names the file it comes from, so it can be re-checked when the move is planned.

## Current production topology (from `deploy/k8s/`)

| Piece | Current setup | Source |
|-------|---------------|--------|
| Workloads | `backend`, `frontend`, `keycloak`, `ntfy`, plus the single CNPG Postgres pod | `deploy/k8s/base/*.yaml`, `deploy/k8s/cluster/cnpg-cluster.yaml` |
| Postgres image | `ghcr.io/cloudnative-pg/postgresql:16.15-standard-trixie` (PostgreSQL 16, pgvector included in the standard flavour) | `deploy/k8s/cluster/cnpg-cluster.yaml` |
| Postgres instances | 1, storage 10 Gi on `local-path`, requests 250m / 512 Mi, limits 1 CPU / 1 Gi | `deploy/k8s/cluster/cnpg-cluster.yaml` |
| Postgres roles and schemas | `jordylab` database owned by `jordylab`; `keycloak` role owning the `keycloak` schema | `deploy/k8s/cluster/cnpg-cluster.yaml` |
| Backup target | OVH Object Storage, bucket `jordylab-cnpg-backups`, endpoint `s3.gra.io.cloud.ovh.net` (Gravelines), VPS is in London | `deploy/k8s/cluster/ovh-object-storage.yaml` |
| WAL archiving | Barman Cloud Plugin, `wal.compression: gzip` | `deploy/k8s/cluster/ovh-object-storage.yaml` |
| Base backups | Daily 03:00 UTC and weekly Sunday 04:00 UTC (`ScheduledBackup`) | `deploy/k8s/cluster/ovh-object-storage.yaml` |
| Retention | `retentionPolicy: 30d`, the only enforced retention (restore to any point in the last 30 days) | `deploy/k8s/cluster/ovh-object-storage.yaml` |
| TLS | cert-manager `Certificate` `jordylab-tls` from the `letsencrypt-prod` ClusterIssuer; the Gateway terminates TLS | `deploy/k8s/cluster/certificate.yaml`, `deploy/k8s/base/gateway.yaml` |
| Routing | Gateway API routes: frontend (`/`), backend (two path rules), keycloak (three path rules), ntfy (`/ntfy` prefix rewrite to `/`), HTTP→HTTPS redirect as an HTTPRoute | `deploy/k8s/base/gateway.yaml` |
| Ingress controller | Traefik with the Gateway API provider enabled, through the k3s HelmChartConfig | `deploy/host/traefik-helmchartconfig.yaml` |
| Volumes | `gamecatalog-artwork` 5 Gi (mounted by backend at `/var/jordylab/artwork`), `mobile-releases` 2 Gi (APK files, backend), `ntfy-cache` 1 Gi | `deploy/k8s/base/artwork-pvc.yaml`, `deploy/k8s/base/backend.yaml`, `deploy/k8s/base/ntfy.yaml` |
| Memory requests / limits | backend 768 Mi / 1536 Mi, ntfy 32 Mi / 128 Mi, frontend 32 Mi / 64 Mi, Postgres 512 Mi / 1 Gi | the manifests above |
| Pod count | 5 (backend, frontend, keycloak, ntfy, `cnpg-cluster-1`) | `deploy/k8s/base/`, `deploy/k8s/cluster/` |

## Current rollout behaviour (from `deploy/k8s/base/` and `.github/workflows/deploy-prod.yml`)

- **Backend and Keycloak use `strategy: Recreate`.** Each deploy already stops the single pod before starting the new one, so the downtime a Compose `up -d` causes is the same kind of downtime production has today. The backend readiness probe starts at 15 s.
- **Frontend has no strategy set**, so Kubernetes uses the default RollingUpdate. With one replica it surges first and then swaps, so frontend has no gap today. Compose recreates the container, so frontend will see a short gap that it does not have now.
- **Images are tagged by release, not by sha.** The manifests carry `:placeholder` tags. `release.yml` retags images for a `v*` release and calls `deploy-prod.yml`. The deploy job checks out the release tag, applies the manifests with `kustomize build | kubectl apply`, and waits on `rollout status` for backend (8 min), frontend (5 min) and keycloak (8 min).
- **Deploys happen only on release tags.** A push to `main` no longer deploys (the workflow header records this). Redeploy and rollback are a manual run of the same workflow with an older tag.
- **Access** is through Tailscale as `tag:ci`. CI uses a scoped `ci-deploy` service account (`deploy/k8s/bootstrap/ci-deploy-rbac.yaml`), not the cluster-admin kubeconfig.

## Manual test runbook

- The VPS reboot check (data and artwork survive a reboot) uses `kubectl exec` and `kubectl get pods`.
- The quarterly restore drill and the point-in-time restore check use `kubectl` and `deploy/k8s/drills/restore-drill-cluster.yaml`. Next due 2027-01-01.
- The full VPS rebuild from git check was **dropped by the owner on 2026-10-05** ("too much hassle to set up a second VPS"). The draft's "full rebuild from an empty VPS" success criterion therefore needs an owner decision, because nothing currently covers that case.

## Runbook and skills

- `docs/runbook.md` has 23 numbered sections (the draft said 21). Sections 3 (k3s install), 19 (k3s / OS upgrades) and the CNPG restore section are the ones that change.

## Port scan (open finding, not decided)

A port scan of the production VPS found two public TCP ports besides 22, 80 and 443. The probable cause is Traefik NodePorts from k3s ServiceLB. A Compose host has no NodePorts, so the port problem goes away with the move, but the same scan must run on the new host before cutover. Open point to check: whether the host firewall (`ufw`) filters the NodePort path at all. If it does not, the new host needs its own rule set, not only the same `ufw` 22/80/443 list.

## Local backup taken 2026-11-02 (pre-migration safety net)

Taken from the running production cluster with read-only commands. The files are outside the repo, in `~/jordylab-backups/prod-2026-11-02/`, mode 600 in a 700 folder. They contain password hashes and realm secrets, so keep them out of cloud-synced folders.

| File | Contents | Check |
|------|----------|-------|
| `jordylab-db.dump.age` | `pg_dump -Fc` of the `jordylab` database (includes the `keycloak` schema), encrypted with age to the SOPS age key's public key | Header is `PGDMP`. Restored into a throwaway `pgvector/pgvector:pg16` container: exit 0, no error lines. Per-table row counts match production for all 122 tables. |
| `volumes-artwork-apk.tar.age` | `/var/jordylab/artwork` and `/var/jordylab/mobile-releases` from the backend pod, as a tar, encrypted the same way | Lists the entries. Artwork is about 4 KB (nearly empty), mobile releases about 291 MB. |
| `rowcounts-prod.txt`, `rowcounts-restore.txt` | Per-table counts used for the comparison | Identical |

Notes for the restore step:

- The dump was made with `--no-owner --no-privileges`, so after a restore the restoring user owns every object. Before Keycloak starts on the new stack, create the `jordylab` and `keycloak` roles and give them back the `jordylab` database and `keycloak` schema.
- The counts were taken with production live. Writes after 22:33 on 2026-11-02 are not in this dump, so the maintenance-window dump replaces it.
- Before the move, the production Postgres pod had 4 restarts (last 31 hours before the check), and the ntfy pod had 2.
