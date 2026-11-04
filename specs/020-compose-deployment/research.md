# Research: Production on Docker Compose

**Spec**: [spec.md](spec.md) · **Date**: 2026-11-02

This file resolves the technical choices the plan needs. The owner's Fixed Decisions in the spec are inputs and are not reopened here. Repository facts are cited from the files they come from. Two external facts were checked with web search; they are marked **verified**.

## R1. Where the Compose stack lives

- **Decision**: a new `deploy/compose/` folder holds the production Compose file, the Caddy configuration, the Postgres image with pgBackRest, the host scripts and an environment template. The Kubernetes folders (`deploy/k8s/`, `deploy/host/`) stay in place, marked as history.
- **Rationale**: production stays apart from local development (`jordylab-be/compose.yaml`), while sharing the same way of writing Compose files. The Kubernetes material is kept for the rollback path (R14).
- **Alternatives considered**: reusing `jordylab-be/compose.yaml` for production (rejected: that file is the owner's local dev stack, with dev credentials and the dev database volume); a separate repository (rejected: the deploy workflow and the images already live here).

## R2. Public routes and reverse proxy (Caddy)

- **Decision**: Caddy is the only container that publishes ports (80 and 443). It routes the same paths the Gateway routes today:

  | Path | Service | Notes |
  |------|---------|-------|
  | `/` (everything else) | `frontend` | nginx on the internal network |
  | `/api*` | `backend` | prefix match in Gateway |
  | `/.well-known/assetlinks.json` | `backend` | exact match in Gateway |
  | `/auth/realms/jordylab*` | `keycloak` | prefix |
  | `/auth/resources*` | `keycloak` | prefix |
  | `/auth/.well-known*` | `keycloak` | prefix |
  | `/ntfy*` | `ntfy` | the prefix is removed before forwarding (Gateway `ReplacePrefixMatch: /`) |

  Caddy uses `handle_path /ntfy/*` to strip the prefix. HTTP redirects to HTTPS by default.
- **Rationale**: the same URLs keep the Keycloak redirect URIs, the notification path and the Android app-links file working (spec FR-002).
- **Certificates**: Caddy obtains a new certificate for `jordylab.be` at cutover, through the HTTP-01 challenge on port 80. The existing cert-manager secret is not reused, because copying a private key is an unnecessary secret move. Let's Encrypt limits duplicate certificates, and one new issuance is well within them. Certificate data lives in a named volume, so renewal survives restarts (FR-003).
- **Alternatives considered**: Traefik with its Docker provider (rejected by the owner's decision, Caddy); copying the cert-manager key (rejected: unnecessary secret handling).

## R3. Postgres image and pgBackRest

- **Decision**: a small image built `FROM pgvector/pgvector:pg16` pinned by digest (the same major version and extension as production, and the same image family as local development's `jordylab-be/compose.yaml`), with the Debian `pgbackrest` package added. The base image is pinned by digest and the pgBackRest package by version (FR-015), so a rebuild cannot change the production Postgres or pgBackRest. The Compose service runs `archive_mode=on` with `archive_command = pgbackrest --stanza=jordylab archive-push %p`.
- **Why not the CloudNativePG image** (a correction made during planning): the CloudNativePG image is built for the operator. It does not read the `POSTGRES_*` variables that a standalone container uses to create the superuser and the database, so it would not start the way `env-contract.md` assumes. The official pgvector image does. Data moves as a logical dump, so the base image change does not affect the restore.
- **pgBackRest configuration** (`deploy/compose/pgbackrest/pgbackrest.conf`):

  ```ini
  [global]
  repo1-type=s3
  repo1-s3-endpoint=s3.gra.io.cloud.ovh.net
  repo1-s3-region=gra
  repo1-s3-bucket=jordylab-cnpg-backups
  repo1-s3-uri-style=path
  repo1-path=/pgbackrest
  repo1-retention-full-type=time
  repo1-retention-full=30
  compress-type=zst
  start-fast=y

  [jordylab]
  pg1-path=/var/lib/postgresql/data
  ```

  The keys come from `PGBACKREST_REPO1_S3_KEY` and `PGBACKREST_REPO1_S3_KEY_SECRET`, which pgBackRest reads from the environment, so no key is written into the file. The `repo1-path=/pgbackrest` prefix keeps the new repository apart from the old Barman prefix (`cnpg-backups`), so the two never mix.
- **No repository encryption added.** The old Barman setup did not encrypt the bucket either (its only transform is gzip for WAL), so adding it here would be a new unrecoverable-key risk with no parity gain. It is recorded as an assumption in plan.md and can be added later with its own key handling.
- **Retention parity (FR-005)**: **verified** (web search, pgBackRest docs and the operator docs that reuse them): `repo1-retention-full-type=time` with `repo1-retention-full=30` keeps full backups for 30 days. A full backup older than 30 days is required before expiry, so the restorable window is at least 30 days. The window matches the current `retentionPolicy: 30d`.
- **Schedule (FR-004)**: a weekly full backup and differential backups Monday to Saturday together satisfy "daily and weekly base backups". Both run from host systemd timers (R13).
- **Restore**: point-in-time restore uses `pgbackrest --stanza=jordylab --type=time --target=<UTC time> restore` against an empty data directory.
- **Alternatives considered**: WAL-G (rejected by the owner's decision, pgBackRest); keeping the Barman plugin outside Kubernetes (rejected: the plugin needs the CNPG operator).
- **Confirmed locally on 2026-11-02**: the `pgbackrest` package installs on the `pgvector/pgvector:pg16` base (pgBackRest 2.60.0, PostgreSQL 16.15, Debian bookworm). The S3 connection is not yet confirmed; see the quickstart log.

## R4. Ownership and dump format at cutover

- **Decision**: the final dump before cutover is taken **with** ownership and privileges (no `--no-owner`). The roles `jordylab` and `keycloak` are created on the new Postgres before `pg_restore` runs, so ownership is restored as it was.
- **Rationale**: the Mac backup taken on 2026-11-02 was made with `--no-owner --no-privileges`, so its objects belong to the restoring user. That backup is a fallback only; if it is used, the owners are reassigned by hand before Keycloak starts.
- **Alternatives considered**: keep `--no-owner` and reassign ownership after restore (rejected: more manual steps during the maintenance window).

## R5. Deploy path: GitHub Actions to the Compose host

- **Decision**: the deploy job joins the tailnet as `tag:ci` (as today), connects over SSH to the host's tailnet address with a dedicated deploy user and key, and runs the steps in `contracts/deploy-workflow.md`. Deploys run only on release tags, through the same `release.yml` → `deploy-prod.yml` chain and the `production` environment approval.
- **Tailscale policy change needed**: the current grant lets `tag:ci` reach only `tcp:6443` on the server. The new deploy path needs `tcp:22` as well. This is a Tailscale policy change, so the owner approves it at cutover (matches FR-013 and the `jordylab-ops` safety rules).
- **Alternatives considered**: Tailscale SSH (rejected for the CI runner: it needs an interactive identity flow; a dedicated key is simpler to rotate); a public SSH port (rejected: the owner's decision closes public SSH).

## R6. Secrets in the deploy

- **Decision**: the runner decrypts `deploy/k8s/overlays/prod/secrets.sops.yaml` with `SOPS_AGE_KEY` (the same secret used today) and renders a Compose environment file. The file goes to the host over the SSH session's standard input, is written with mode 600 to `/opt/jordylab/env/prod.env`, and is never printed. Compose reads it through `env_file`.
- **Env mapping** (the full contract is in `contracts/env-contract.md`): the secret keys in `jordylab-secrets`, plus `jordylab-db-app` (`username`, `password`) for the backend and the owner role, and `jordylab-db-keycloak` (`username`, `password`) for Keycloak.
- **Rationale**: keeps the same SOPS source of truth and the same decryption point (FR-016).

## R7. Healthchecks, restarts, logs and memory

- **Health checks**: Postgres `pg_isready`; backend `/actuator/health/readiness` (its probe today); Keycloak `http://localhost:9000/health/ready` (the management port, its probe today); ntfy `/v1/health`; frontend `curl -f http://localhost:8080/`; Caddy its admin endpoint. `docker compose up -d --wait` waits for these. The backend has a start period of 150 s, matching the startup probe on main (JOR-76: a cold start can take about 70 s), so failures in that window do not count.
- **Restart**: `restart: unless-stopped` on every service, and Docker enabled at boot (FR-009).
- **Logs**: `json-file` driver with `max-size: 10m` and `max-file: 3` per container (FR-010).
- **Memory limits** (from today's manifests, `deploy/k8s/base/`): backend 1536 MiB, Keycloak 1024 MiB, Postgres 1024 MiB, ntfy 128 MiB, frontend 64 MiB, Caddy 128 MiB. The limits total 3904 MiB, under the 8 GB host with room for the operating system and Docker. Current k3s use is measured before cutover (SC-007).

## R8. Networking and the trusted proxy

- **Decision**: one Compose network with a fixed subnet (`172.28.0.0/16`). Keycloak trusts `X-Forwarded-*` headers only from that subnet (`KC_PROXY_TRUSTED_ADDRESSES=172.28.0.0/16`), which replaces the k3s pod range `10.42.0.0/16` in the overlay.
- **Service names**: the backend reaches the database at `postgres:5432` and Keycloak at `http://keycloak:8080/auth` (the name `KEYCLOAK_INTERNAL_URL` already uses), so only the database URL changes.
- **Firewall and Docker**: Docker publishes ports through its own firewall rules, which bypass `ufw`'s INPUT rules. Only Caddy publishes ports (80 and 443). Every other service stays on the internal network, and the external scan (FR-014) checks the result. This is also why the port finding (R12) is checked on the new host.

## R9. Volumes and data

- **Decision**: named volumes for `postgres-data`, `artwork`, `ntfy-cache` and `caddy-data`, and a host folder for the pgBackRest spool. Data moves at cutover by a `tar` stream from the k3s volumes into the new volumes, after a first bulk copy before the window (only the delta moves inside the window).
- **Rationale**: the same data shape as today's PVCs. The artwork folder is about 4 KB (measured 2026-11-02), so the copy is small. The APK volume was removed on main with the native app (spec 018), so it is not copied.

## R10. Cutover order (summary; full steps in plan.md)

The sequence is in `plan.md`, phase by phase. The key constraints are: k3s holds ports 80 and 443 until it is stopped, so Caddy cannot start before k3s is removed; the final database dump must come after writes stop; and the restore must finish before traffic returns.

## R11. Host backups and maintenance commands

- **Decision**: systemd timers on the host run `docker compose exec postgres pgbackrest --stanza=jordylab --type=diff backup` Monday to Saturday and `--type=full backup` on Sunday, and their output goes to journald.
- **Rationale**: the timers are visible on the host and need no extra container.
- **Alternatives considered**: a cron container (rejected: one more moving part to watch).

## R12. Port finding

- **Decision**: no change to k3s. The Compose host gets the same external scan before cutover, and the scan result is recorded in the quickstart log.
- **Open point to test**: whether the port finding on the old host came from a path Docker would also bypass (R8). The scan on the new host answers it.

## R13. Keeping the owner's Mac backup

- **Decision**: the Mac backup is kept until pgBackRest has run its first full backup on the new stack and its restore test passes (plan phase 5). It is not copied elsewhere.
- **Rationale**: it is a fallback, and the owner chose a local copy on purpose.

## R14. Rollback

- **Decision**: a rollback before the new stack goes live is unchanged (k3s is still there). After cutover, the path is a rebuild: reinstall k3s on the host following the historical runbook sections kept under `docs/runbooks/`, apply the kept manifests with `kustomize build deploy/k8s/overlays/prod`, and restore from the Mac backup. Fallback time is not measured in advance; it is recorded as a trade-off in the spec.
- **Rationale**: the owner accepted this trade-off in the draft (no second VPS).

## Summary of open items for planning

None. Every spec requirement has a decided approach above, and no clarification markers remain.
