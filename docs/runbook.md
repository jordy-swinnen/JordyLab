# JordyLab Production Runbook

Exact, copy-pasteable procedures for operating JordyLab's production Docker Compose stack on its OVH VPS. Every section
below that touches a live VPS, a billable OVH resource, DNS, or a real credential is Jordy's to run himself — nothing in
this repository's automation performs these steps.

See `docs/learn/` for the concept explanations behind each procedure here, and `docs/environments.md` for the
`local`/`prod` settings table.

## Contents

1. [Order the VPS](#1-order-the-vps)
2. [Harden the OS](#2-harden-the-os)
3. [Install Docker Engine and the Compose plugin](#3-install-docker-engine-and-the-compose-plugin)
4. [Join the VPS to Tailscale](#4-join-the-vps-to-tailscale)
5. [The host layout and the stack](#5-the-host-layout-and-the-stack)
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
19. [Docker / OS upgrades](#19-docker--os-upgrades)
20. [Deploys, releases and the APK](#20-deploys-releases-and-the-apk)
21. [Manual tests the E2E campaign could not run](#21-manual-tests-the-e2e-campaign-could-not-run)
22. [Before a release that changes the database: backup and pgvector](#22-before-a-release-that-changes-the-database-backup-and-pgvector)
23. [LibBot and the auto-fill: what to check](#23-libbot-and-the-auto-fill-what-to-check)
24. [Postgres major-version upgrade](#24-postgres-major-version-upgrade)
25. [Break-glass: an admin locked out](#25-break-glass-an-admin-locked-out)

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
3. Firewall (`ufw`), allowing only what the stack needs:
   ```
   sudo ufw default deny incoming
   sudo ufw default allow outgoing
   sudo ufw allow 80/tcp     # Caddy: Let's Encrypt HTTP-01 and the HTTP to HTTPS redirect
   sudo ufw allow 443/tcp    # Caddy: the site
   # No rule for 22: public SSH was closed on 2026-11-03 (spec 020 T056). Administration is Tailscale SSH (§4),
   # which tailscaled terminates on tailscale0 and which therefore needs no ufw rule.
   sudo ufw enable
   ```
   Nothing else is public. Only `caddy` publishes ports (`compose.yaml`), and administration goes over Tailscale (§4):
   no NodePorts, no 6443, no 10250.
## 3. Install Docker Engine and the Compose plugin
**(operator step — do not run these commands yourself)**

Production runs the Docker Compose stack in `deploy/compose/`. k3s is not part of it any more: it was retired at the
Compose cutover (spec 020) and its manifests stay only as a rebuild fallback (§16).

1. Install Docker Engine and the Compose plugin, and note the versions you install:
   ```
   docker --version && docker compose version
   ```
2. Create the deploy user and the host layout. The deploy workflow connects as `jordylab-deploy` (never root) and
   mirrors `deploy/compose/` into place:
   ```
   sudo adduser --system --group --shell /bin/bash jordylab-deploy
   sudo usermod -aG docker jordylab-deploy
   sudo install -d -o jordylab-deploy -g jordylab-deploy /opt/jordylab/compose /opt/jordylab/env /opt/jordylab/state
   ```
   Put the CI key's public half in `~jordylab-deploy/.ssh/authorized_keys`; its private half is the `DEPLOY_SSH_KEY`
   environment secret (§9).
3. Validate the stack definition against the example environment file (no secrets needed):
   ```
   docker compose --env-file deploy/compose/env/prod.env.example -f deploy/compose/compose.yaml config -q
   ```
4. Bring the stack up (a deploy normally does this per release, §10):
   ```
   sudo -u jordylab-deploy docker compose --env-file /opt/jordylab/env/prod.env \
     -f /opt/jordylab/compose/compose.yaml up -d --wait
   ```
   Every service must report healthy; the backend's health check waits out its 150 s start period.
## 4. Join the VPS to Tailscale
**(operator step — do not run these commands yourself)**

The deploy workflow reaches the host over Tailscale SSH as `jordylab-deploy`, so a CI node needs TCP 22 to the server
and nothing else (the k3s API's 6443 is not used any more):

1. On the VPS:
   ```
   curl -fsSL https://tailscale.com/install.sh | sh
   sudo tailscale up --ssh
   ```
2. In the Tailscale admin console, tag the node `tag:jordylab-vps` (key expiry disabled), and let `tag:ci` reach it on
   `tcp:22` only.
3. Verify from another tailnet device: `ssh ubuntu@100.93.81.41 'echo TAILNET_SSH_OK'`.
## 5. The host layout and the stack
**(reference — check here first when something is not where you expect it)**

| Path | What |
|------|------|
| `/opt/jordylab/compose/` | Mirror of `deploy/compose/`: `compose.yaml`, `Caddyfile`, the scripts, the Postgres build, the backup timers |
| `/opt/jordylab/env/prod.env` | The environment file, rendered from SOPS by the deploy; `prod.env.previous` is the one before |
| `/opt/jordylab/state/current-release`, `previous-release` | The live release (a tag or `sha-<sha>`) and the one before it |

- Only `caddy` publishes ports (80, 443). Caddy terminates TLS, redirects HTTP to HTTPS, and routes `/api` plus the
  WebAuthn ceremony paths and `/ntfy` (`Caddyfile`); every other path goes to the Angular app.
- The other services are `backend`, `frontend`, `postgres` and `ntfy`, each with `restart: unless-stopped`,
  a memory limit and capped json-file logs (10 MB × 3).
- The database is `jordylab-postgres:pg16-pgbackrest`, built on the host from `deploy/compose/postgres/`: pgvector
  included, base image pinned by digest, pgBackRest pinned by version.
- Artwork lives in the `jordylab_artwork` volume; PostgreSQL data in `jordylab_postgres-data`; Caddy's certificates in
  `jordylab_caddy-data`. They survive a `docker compose down`; only a host loss destroys them.

When you are on the host, this is the one command shape used below:

```bash
export COMPOSE='sudo -u jordylab-deploy docker compose --env-file /opt/jordylab/env/prod.env -f /opt/jordylab/compose/compose.yaml'
$COMPOSE ps
```
## 6. DNS & first TLS issuance
**(operator step — do this yourself)**

1. At your domain registrar (OVHcloud), create an `A` record for the domain pointing at the VPS's public IPv4 (§1).
2. Wait for propagation: `dig +short <domain>` returns the VPS IP from multiple resolvers.
3. Nothing else to install: Caddy issues and renews the certificate itself through the HTTP-01 challenge on port 80
   (`Caddyfile`, with the Let's Encrypt contact address), so port 80 stays open.
4. Verify: `curl -sI https://<domain>/` answers 200, and `openssl s_client -connect <domain>:443` shows a valid chain.
   Caddy keeps the certificate in the `jordylab_caddy-data` volume; a forced renewal test is quickstart step 7.
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
   - The GitHub `production` environment, as a secret named `SOPS_AGE_KEY` (§9). The deploy renders `prod.env` from
     `deploy/k8s/overlays/prod/secrets.sops.yaml` with it.
4. Delete the local `jordylab-prod.age.key` file once both copies are stored.
## 8. OVH Object Storage bucket
**(operator step — billable resource, do not create it yourself)**

1. In the OVHcloud control panel, create an S3-compatible Object Storage container named `jordylab-cnpg-backups` in
   Gravelines (`s3.gra.io.cloud.ovh.net`, path-style access).
2. Generate S3 access/secret keys scoped to that container.
3. Add them to `deploy/k8s/overlays/prod/secrets.sops.yaml` as `S3_ACCESS_KEY` and `S3_SECRET_KEY` (§13). The deploy
   passes them to pgBackRest as `PGBACKREST_REPO1_S3_KEY` / `PGBACKREST_REPO1_S3_KEY_SECRET`.
4. pgBackRest writes under `/pgbackrest` with 30 days of full backups
   (`deploy/compose/pgbackrest/pgbackrest.conf`). The old Barman chain under `cnpg-backups` is history and pgBackRest
   cannot read it (spec 020's backup contract).
## 9. First-time CI access setup

**(operator step — do not create these yourself)**

1. In the GitHub repo settings, create a `production` Environment for its secrets. It has **no** required reviewer:
   deploys are started by hand and run without an approval (spec 019 FR-002, spec 020).
2. Environment secrets the Compose deploy workflow needs:
   - `SOPS_AGE_KEY` — the private key from §7. It renders `prod.env` on the runner, never on the host.
   - `DEPLOY_SSH_KEY` — the private key of the `jordylab-deploy` user on the VPS (spec 020 T023; the public half is in
     that user's `authorized_keys`).
   - `TS_OAUTH_CLIENT_ID` / `TS_OAUTH_CLIENT_SECRET` — a Tailscale OAuth client for ephemeral `tag:ci` nodes
     (`tailscale/github-action@v4`). The tailnet policy must let `tag:ci` reach the host on TCP 22.
3. The workflow pins the host's ed25519 fingerprint (`EXPECTED_ED25519_FP` in `deploy-prod-compose.yml`) and fails on a
   mismatch before it connects.
4. No k3s credential is used any more. Production runs on Docker Compose (spec 020), k3s is stopped and disabled, and the
   retired pipeline's `KUBE_*` environment secrets have been removed from the `production` environment.
5. Automatic deploys are on since the pipeline was rewired (spec 020 T062). The repository variable `AUTO_DEPLOY` gates
   them: `true` deploys a green `main` build by itself, anything else pauses that, and manual runs are unaffected.

## 10. Deploy

Production is the Docker Compose stack on the VPS (spec 020); k3s is retired. A deploy runs without an approval: a green
build on `main` deploys by itself while `AUTO_DEPLOY` is `true`, and a manual run works with or without it. An automatic run
skips a target older than the live release (a slow build that finishes late); a manual run never does, which is what makes
an older target a rollback.

1. Merge to `main`. `build.yml` tests, scans and publishes `ghcr.io/jordy-swinnen/jordylab-*:sha-<full-sha>`, and while
   `AUTO_DEPLOY` is `true` it then calls the Compose workflow with that commit.
2. A manual deploy uses the same workflow: GitHub → Actions → *Deploy to Production (Compose)* → *Run workflow*, or from a
   terminal:
   ```
   gh workflow run deploy-prod-compose.yml --repo jordy-swinnen/JordyLab -f target=<full-sha>
   ```
   The target is a full commit sha or a release tag `vX.Y.Z` / `vX.Y.Z-rcN`. It maps to the image tag `sha-<sha>` or the
   tag itself, so a sha deploy runs exactly the images its Build published: nothing is rebuilt and nothing is retagged.
3. Before the host is touched, a target that cannot be resolved is refused: a sha that is not in the history, a tag that
   does not exist, or any of the three images missing from GHCR (FR-014).
4. The workflow mirrors `deploy/compose` to `/opt/jordylab/compose`, applies a changed Caddyfile (validated, atomic reload;
   see §17), renders `prod.env` from the SOPS secrets on the runner,
   writes it on the host (mode 600, the file it replaces kept as `prod.env.previous`), and runs
   `/opt/jordylab/compose/scripts/deploy-release.sh deploy <release>`. That script pulls and restarts **only** backend
   and frontend (removing any orphaned service), then checks the site through Caddy on the host. Postgres, ntfy and Caddy
   are not restarted by a release.
5. From the runner it smoke-checks `https://jordylab.be`: the home page answers 200, an unauthenticated `/api` call
   answers 401, and the identity CSRF endpoint answers 204.
6. If anything fails from the environment file onwards, the workflow runs `deploy-release.sh rollback <release>`. It acts
   only when that release is still the live one: it restores `prod.env.previous` and the previous release, and the job
   ends red. The state files are `/opt/jordylab/state/current-release` and `previous-release`.
7. Confirm: `ssh ubuntu@100.93.81.41 'sudo docker ps'` (six services healthy), then the `quickstart.md` checks.

**Outage**: a release restarts three services, so expect seconds rather than minutes. There is one host and no blue/green.

**Background work a deploy can interrupt** (FR-021). A restart stops whatever is running, and each job catches up on its own:

| Job | When | If a deploy interrupts it |
|-----|------|---------------------------|
| Feed ingestion | three times a day (cron 06, 12, 18) | catches up on its next run |
| Auto-fill sweep | daily 04:30 Europe/Brussels | catches up on its next run |
| Monthly briefing | 06:30 on the 1st | rerun by hand: `POST /api/fna/briefing/trigger` (admin) |
| Stock prices | every 30 minutes | catches up on its next run |

## 11. Rollback

Automatic: when a Compose deploy fails after the host was touched, the workflow asks the host script to restore the
previous release and `prod.env.previous`, and the run ends red with the outcome in its summary.

By hand, to any commit or release tag (this is also how you redeploy): *Deploy to Production (Compose)* → *Run workflow*
with the full commit sha, or from a terminal:
```
gh workflow run deploy-prod-compose.yml --repo jordy-swinnen/JordyLab -f target=<full-sha-or-release-tag>
```
An older target is allowed, which is what makes that a rollback.

Emergency path (no pipeline), on the host:
```
ssh ubuntu@100.93.81.41 'sudo -u jordylab-deploy /opt/jordylab/compose/scripts/deploy-release.sh deploy <release>'
```
The script pulls, restarts and checks the site, and restores the live release itself if that fails.

Three things to know before you roll back:

- **A rollback stays live until the next deploy.** Automatic deploys are off (T062), so nothing overwrites it by itself.
  Revert the bad commit on `main` so the next deploy does not put it back.
- **A rollback restores the environment file that was live before the last deploy** (`prod.env.previous`). A secret changed
  in the failed deploy therefore goes back too; for a secret rotation (§13), roll forward instead.
- **The database is not rolled back.** See the subsection below.

### Rollback and the database

A rollback returns the code to the previous commit. It does not return the database schema: a migration that was applied
stays applied (spec 019, FR-020). A release is only rolled back safely when its migrations work with the previous release,
which is why a migration that removes or renames something is split into an expand step now and a contract step in a later
release (`.claude/skills/flyway-migration/SKILL.md`).

After a rollback, check:

- the latest Flyway history rows:
  `$COMPOSE exec -T postgres psql -U postgres -d jordylab -At -c "select version, description, success from flyway_schema_history order by installed_rank desc limit 3"`;
- the backend logs for schema or migration errors:
  `$COMPOSE logs --since=15m backend | grep -i -E "flyway|migration|schema"`;
- the row counts of any table the failed release changed.

## 12. Logs
The stack's logs are capped json-file logs on the host:

```bash
ssh ubuntu@100.93.81.41
export COMPOSE='sudo -u jordylab-deploy docker compose --env-file /opt/jordylab/env/prod.env -f /opt/jordylab/compose/compose.yaml'
$COMPOSE logs -f backend          # also: frontend, caddy, postgres, ntfy
$COMPOSE ps
sudo docker stats --no-stream
sudo journalctl -u 'jordylab-backup-*' -n 50    # the backup timers (§15)
```
## 13. Rotate a secret
1. `SOPS_EDITOR="idea --wait" sops deploy/k8s/overlays/prod/secrets.sops.yaml` (from the repo root) — opens the
   decrypted content in a temp file in IntelliJ; edit the value, save, close the tab, and `sops` re-encrypts on
   exit. Without `SOPS_EDITOR`, sops falls back to `$EDITOR`.
2. Commit and push the updated ciphertext.
3. Deploy a commit that contains it (§10): the runner renders `prod.env` from the new ciphertext and writes it on the
   host. The containers take the new values when that deploy recreates them; there is nothing to restart by hand.
4. (Player-count key: see §23, "Player counts are missing".) A `docker compose up -d` on the host alone does **not** pick
   up a new value: it reads the `prod.env` that is already there, which only the deploy writes.
## 14. Age key lost (re-keying)
If the private key is gone from **both** the password manager and the GitHub environment secret:

1. Every value in `secrets.sops.yaml` is unrecoverable as ciphertext — you must know the plaintext values
   independently (e.g. re-generate DB credentials and the identity bootstrap password, re-fetch API keys from their providers).
2. Generate a **new** age key (§7).
3. Re-create `secrets.sops.yaml` from scratch with the new key's public half in `.sops.yaml`, and the
   (re-obtained) plaintext values.
4. Update the GitHub environment secret and your password manager with the new private key.
5. Deploy (§10) to re-render `prod.env` and roll every affected credential.
## 15. Restore drill
The database is backed up with pgBackRest to OVH Object Storage: a full backup every Sunday 04:00 UTC, a differential
Monday to Saturday 03:00 UTC, and continuous WAL archiving (`deploy/compose/host/`, `pgbackrest.conf`). The contract is
`specs/020-compose-deployment/contracts/backup-contract.md`. Run the drill quarterly and after any change to the backup
setup; it takes about 15 minutes.

**Check the chain** (on the host):

```bash
export COMPOSE='sudo -u jordylab-deploy docker compose --env-file /opt/jordylab/env/prod.env -f /opt/jordylab/compose/compose.yaml'
systemctl list-timers 'jordylab-backup-*'
$COMPOSE exec -T postgres pgbackrest --stanza=jordylab info
```

The newest backup of either kind must be within 24 hours, and the newest full backup within 7 days.

**Restore to a point in time** into a throwaway container with no published ports and its own volume:

```bash
sudo docker volume create jordylab-restore-drill
sudo docker run --rm --name jordylab-restore-drill --env-file /opt/jordylab/env/prod.env \
  --mount type=volume,src=jordylab-restore-drill,dst=/var/lib/postgresql/data \
  --entrypoint bash jordylab-postgres:pg16-pgbackrest -c '
    pgbackrest --stanza=jordylab --type=time "--target=<UTC time>" --target-action=promote restore &&
    pg_ctl -D /var/lib/postgresql/data -o "-p 5433" -w start &&
    psql -p 5433 -U postgres -d jordylab -At -c "select count(*) from gamecatalog.game" &&
    pg_ctl -D /var/lib/postgresql/data stop'
sudo docker volume rm jordylab-restore-drill
```

Pick a target time inside the window and after the first full backup, and write it down first. Compare the row counts
with production, then log the drill below.

| Date | Restored to | Time to restore | Row counts match | By | Next due |
|------|-------------|-----------------|------------------|----|----------|
| 2026-10-01 | k3s era: base backup `cnpg-daily-backup-20261001030000` + WAL to ~09:53 UTC | 1 min 55 s | yes — `gamecatalog.game` 128/128, users 6/6, `flyway_schema_history` 20/20 | agent (approved by Jordy) | replaced by the Compose drill |
| 2026-11-03 | the cutover window: `finance.article` at exactly its value at 20:38 UTC (823 vs 822 in the pre-window baseline) | inside the window; recorded in the cutover records | yes for the checked row | agent (approved by Jordy), in the cutover window | 2027-02-03 (quarterly) |

Restores to a time **before** the cutover use the old CloudNativePG chain and the kept k3s manifests (backup contract,
"The old chain"); pgBackRest cannot read it.
## 16. Full VPS rebuild
If the VPS is lost entirely:

1. Order and harden a new VPS (§1–§2).
2. Install Docker and the Compose plugin, create the deploy user and the layout, and join Tailscale (§3–§4).
3. From a clean checkout of `main`: mirror `deploy/compose/` to `/opt/jordylab/compose` and render `prod.env` from the
   SOPS secrets (the deploy workflow does both; §10), then bring the stack up (§3 step 4).
4. Restore the database from the latest pgBackRest backup (§15) into the `jordylab_postgres-data` volume.
5. Re-point DNS at the new VPS's IP (§6); Caddy issues a fresh certificate.
6. Here is what does **not** survive a full host loss: the `artwork` volume and the Caddy certificate volume. The
   database does, via Object Storage. Artwork is regenerated by the auto-fill after the next scan (never seed it by hand).

Fallback: the k3s material is kept under `deploy/k8s/` and `docs/runbooks/vps-k3s-deployment/` (spec 020, research R14)
in case the Compose path itself cannot be rebuilt. It is history, not a live path.
## 17. Certificate troubleshooting
```bash
ssh ubuntu@100.93.81.41
export COMPOSE='sudo -u jordylab-deploy docker compose --env-file /opt/jordylab/env/prod.env -f /opt/jordylab/compose/compose.yaml'
$COMPOSE logs caddy | grep -i -E "certificate|challenge|error"
$COMPOSE ps caddy
curl -sI https://jordylab.be/
```

### Testing renewal without touching DNS

The zone has no wildcard, so a test hostname under `jordylab.be` would need a DNS record. `https://<ip-with-dashes>.sslip.io`
resolves to the host with no DNS change. Add a temporary site block with the staging CA, apply it with the script, and
remove it afterwards (the deploy applies a committed change automatically):

```
57-129-163-110.sslip.io {
    tls {
        ca https://acme-staging-v02.api.letsencrypt.org/directory
    }
    respond "renewal test OK"
}
```

Two Caddy behaviours matter:

- **A changed Caddyfile is applied by the deploy** (JOR-82): Caddy mounts the directory (`/srv`), so the mirrored file is
  visible, and `apply-caddy-config.sh` validates it, reloads atomically, and keeps `state/Caddyfile.previous` for the
  rollback. The first deploy after that change recreates the container once, because the old one mounts the file. If you
  edit the mirrored file by hand for a test, run `ssh ubuntu@100.93.81.41 /opt/jordylab/compose/scripts/apply-caddy-config.sh`
  and then put the repo copy back (a deploy does it).
- **Deleting the stored certificate and reloading does not force a new one**, because the certificate cache lives in the
  process. Delete the host's certificate directory and restart the container.

Record of the 2026-11-04 test: the first staging certificate took 7 s (serial `2CF0DCF…`); after deleting it and
restarting Caddy, a new one took 6 s (serial `2C1AA79…`). Production's certificate was untouched (serial `064F290B…`,
issuer `YE2`), the test site and its certificate were removed, and the host Caddyfile ended byte-identical to the repo copy.

Common causes: DNS not propagated (§6), port 80 not reachable from outside (firewall, §2 — HTTP-01 needs it), or
Let's Encrypt rate limits after repeated failures. For a renewal test, use a staging hostname (quickstart step 7)
rather than burning production attempts. Caddy stores its data in the `jordylab_caddy-data` volume; deleting the
certificate directory forces a re-issue.
## 18. Disk cleanup
75 GB total, shared by images, volumes and logs. Check and reclaim:

```bash
df -h / /var/lib/docker
sudo docker system df
sudo docker image prune            # dangling images only; running and previous-release images are kept
sudo docker builder prune
du -sh /var/lib/docker/volumes/*
```

Keep the current and previous release images: they are the rollback targets named in the state files (§5).
## 19. Docker / OS upgrades
1. **OS**: `sudo apt-get update && sudo apt-get upgrade -y`, reboot if the kernel requires it. The stack comes back
   by itself (`restart: unless-stopped`); verify with `$COMPOSE ps` and the quickstart checks (§12).
2. **Docker**: upgrade the engine and the Compose plugin deliberately, then `$COMPOSE ps` and one smoke check. A deploy
   does not upgrade Docker.
3. **The application**: a release is a deploy (§10). Nothing on the host changes.
4. **Postgres / pgBackRest**: change the pinned digest or version in `deploy/compose/postgres/Containerfile`
   deliberately, then `cd /opt/jordylab/compose && sudo -u jordylab-deploy docker compose ... build postgres` and a
   deploy in a maintenance window — restarting Postgres is downtime, and the image build is not part of a release.
5. **No k3s to upgrade**: k3s is stopped and disabled since the cutover (spec 020). Its upgrade steps are history in
   `docs/runbooks/vps-k3s-deployment/`.
## 20. Deploys, releases and the APK

**Status: the deploy platform changed on 2026-11-03.** The automatic k3s pipeline (spec 019) deployed a green `main` build
without approval; the Compose cutover (spec 020), later the same day, retired it. Deploys now run on Docker Compose: a green
`main` build deploys by itself while `AUTO_DEPLOY` is `true`, and a manual run takes a commit sha or a release tag (§10,
§11). The measurement tables below are the record of the k3s pipeline, not of the Compose deploys, which time themselves in
their own runs.

The first deploy through the Compose workflow (T049) was run 41728815560 on 2026-11-03: target `76f38366` (a commit sha),
every step green, `current-release` set to it, and the public smoke checks 200/401/200. Later deploys are read the same way:
the run's own timings, and `/opt/jordylab/state/current-release` for what is live.

The rollback path was exercised the same day: the older sha `9cc83c4` went live and `76f38366` was restored (runs
41732623682 and 41732871477), and an unresolvable target (`v9.9.9`) failed at checkout with the live release untouched
(run 41733143053). Automatic deploys were switched on afterwards (`AUTO_DEPLOY=true`), so the next merge deployed itself.

### The flow

1. Merge to `main`: the Build publishes the three `sha-<commit>` images and deploys nothing.
2. Deploy a target by hand: a full commit sha, or a release tag (§10). A sha needs no tag and no retag.
3. A tag is only a label: `git tag v0.1.0 <sha>` and `git push origin v0.1.0` deploys nothing by itself. Tags are `vX.Y.Z`
   or `vX.Y.Z-rcN`; minor and patch ≤ 99, rc ≤ 98.
4. The Android APK is gone with the native app (spec 018); no workflow builds one.

### Measurements (FR-026)

Record the first five automatic deploys: the time from merge to live (from the `Build` run's start to the deploy job's
end), the backend outage (the old pod stops before the new one is ready: `strategy: Recreate`), and whether users stayed
signed in (sign in before the deploy, reload after it).

**Dry run, 2026-11-03** (run 41638441811, target `8e21ec9`, the merge of PR #188): validation, the live read (production ran `e3ca834`), the newest backup, the server-side apply dry run and the three smoke checks passed, and nothing changed in the cluster. The two negative targets (`v9.9.9` and an all-zero sha) failed at the checkout step, before the cluster was read: git cannot check out a ref that does not exist, so the refusal comes from the checkout, not from the decision script. The server dry run reports the three secret objects as `configured`; the first automatic deploy shows whether that is a real change.

| Deploy (commit) | Merge to live | Backend outage | Users stayed signed in | Notes |
|-----------------|---------------|----------------|------------------------|-------|
| 1 (`09f8f23`, run 41640471104) | 13:00:16 → 13:15:50 UTC: 15 min 34 s (Build 13:00–13:13, deploy job 2 min 3 s) | about 74 s: the rollout step; the old pod stops when the new one is applied (`Recreate`) | not measured: needs a signed-in browser session | all three images `sha-09f8f23`; `/actuator/info` reports the merge commit; smoke checks passed |
| 2 (`ef9dcd7`, run 41644801911) | 13:29:37 → 13:51:09 UTC: 21 min 32 s (Build 13:29–13:41, deploy job 9 min 51 s) | about 9 min: the drill's failed rollout, then the rollback (`Recreate`) | not measured | **drill**: the rollout timed out, the rollback restored `09f8f23`, the post-rollback smoke check passed, the run ended red |
| 3 (`6088af8`, run 41648404964) | 13:53:24 → 14:08:31 UTC: 15 min 7 s (the Build waited behind the newer one, deploy job 1 min 59 s) | about 74 s | not measured | two merges within three minutes: both deploys ran, in order, and neither was skipped |
| 4 (`cca388d`, run 41648908792) | 13:56:46 → 14:11:11 UTC: 14 min 25 s (Build 13:56–14:09, deploy job 1 min 46 s) | about 70 s | not measured | `main` head; `/actuator/info` reports it |
| 5 (`14f97f0`, run 41653928431) | 14:27:52 → 14:42:06 UTC: 14 min 14 s (Build 14:27–14:39, deploy job 1 min 53 s) | about 70 s | not measured | `main` head at the time; `/actuator/info` reports it. After five deploys: all green, no run needed a manual rollback except the drill's deliberate one |

The rollback drill above also proved the manual path (T057): a dispatch to `09f8f230` put that commit live with green smoke checks, and a second dispatch to `cca388d` restored the head of `main`, about 2 minutes each.

**Not yet observed live:** the superseded skip (FR-006). The two merges that landed close together (rows 3 and 4) both deployed, in order, because the newer commit's build finished last. The skip is covered by the fixtures in `deploy/scripts/tests/deploy-decision-cases.sh`; watch for it the next time a slow build finishes after a newer one is live.

**Spec 019 is live (2026-11-03), then the platform moved.** Six automatic deploys, all green: `09f8f23`, `ef9dcd7` (the drill), `6088af8`, `cca388d`, `14f97f0` and `2c1091d`. The only rollbacks were the drill's and the manual ones it proved. No APK or release workflow exists any more, so no merge can build one (SC-005). The superseded skip was observed live on 2026-11-04: re-running the older deploy job (target `c2bbbc6`) while
`3f8396d` was live decided `superseded` and skipped the apply, the job ended green, and the live release was unchanged
(run 41734429896, attempt 2). Later the same day the Compose cutover (spec 020) retired the k3s pipeline; see the status at the top of §20.

### Broken-image drill and manual rollback (quickstart Q5, Q6)

| Date | Broken commit | Rolled back to | Time from start to previous commit serving | Manual rollback result |
|------|---------------|----------------|---------------------------------------------|------------------------|
| 2026-11-03 (run 41644801911) | `ef9dcd7`, readiness path `/actuator/health/does-not-exist` (PR #192) | `09f8f23`, with its own images and manifests | 9.8 min from the start of the deploy job to the previous commit serving; the rollout waited the full 8 min; the backend was down about 9 min (`Recreate`) | manual dispatch to `09f8f230` (run 41651288462): live in 1 min 49 s with green smoke checks; then a dispatch to `cca388d` (run 41651600183) restored the head of `main` in 1 min 44 s |

### Deploy or roll back to any version

See §11: *Deploy to Production (Compose)* → *Run workflow* with a commit sha or a release tag.
`deploy-prod-compose.yml` accepts a full commit sha that has images, or a tag `vX.Y.Z[-rcN]` that exists, and runs that
release's images on the Compose stack. The superseded skip (`deploy-decision.sh`) is wired for automatic runs; the Compose
workflow is manual-only until T062, so it always proceeds.

### Removing the Android app stack (one-time cutover, after the first release with spec 018)

Do these once, after the release that removed the `mobile` module is running in production. Each is verifiable and
safe to skip temporarily; none of them changes the web app.

1. Deploy the release (above). The backend boots with `jordylab.notifications.*` and `/.well-known/assetlinks.json`
   now answers 403 (expected — the endpoint is gone).
2. The APK volume was a k3s PVC (`mobile-releases`) on the old cluster's local disk. k3s is gone (spec 020), so
   there is nothing to delete, and the data is not needed.
3. The native app's identity clients and roles are gone with the identity-provider removal (spec 019 release B); nothing
   is left to delete.
4. Remove the retired CI secret from GitHub (`gh secret remove MOBILE_RELEASE_CI_CLIENT_SECRET --repo jordy-swinnen/JordyLab`
   and, if present, `--env production`).
5. SOPS: `MOBILE_RELEASE_CI_CLIENT_SECRET` and `MOBILE_DOWNLOAD_LINK_SECRET` are already gone from
   `deploy/k8s/overlays/prod/secrets.sops.yaml`; `prod.env` follows the next deploy (§13).
6. Verify: a throw-away sign-up produces the ntfy push and its tap opens `/settings/users`; the manifest audit on
   `https://jordylab.be` reports the app installable.

### GitHub settings (owner only)

Branch ruleset on `main`: require PRs and the Build checks. The `production` environment has no required reviewer (§9).
A tag ruleset is only needed if tags are still created as labels; deploys do not depend on tags.

## 21. Manual tests the E2E campaign could not run

See [`docs/testing/manual-test-runbook.md`](testing/manual-test-runbook.md): a signed-in pass on production, the phone PWA install and share sheet, the JordyBox scan, a VPS reboot, point-in-time restore, CSP rollout, colour contrast and a full rebuild.

---

## 22. Before a release that changes the database: backup and pgvector
Both are safe to repeat.

**1. A fresh backup first.** The full backup is Sunday 04:00 UTC and the differential Monday–Saturday 03:00 UTC (§15).
For a release that rewrites data, take one now:

```bash
ssh ubuntu@100.93.81.41
export COMPOSE='sudo -u jordylab-deploy docker compose --env-file /opt/jordylab/env/prod.env -f /opt/jordylab/compose/compose.yaml'
$COMPOSE exec -T postgres pgbackrest --stanza=jordylab --type=full backup
$COMPOSE exec -T postgres pgbackrest --stanza=jordylab info
```

**2. pgvector.** The production image is `pgvector/pgvector:pg16` (pinned by digest in
`deploy/compose/postgres/Containerfile`), so the extension is available and the backend's
`CREATE EXTENSION IF NOT EXISTS vector` succeeds. Check it before a release that depends on it:

```bash
$COMPOSE exec -T postgres psql -U postgres -d jordylab -At -c "select extname, extversion from pg_extension where extname = 'vector'"
```
## 23. LibBot and the auto-fill: what to check
- **Covers or descriptions are missing.** The Sources page shows the library health (games without a cover, without a description, not yet searchable by LibBot) with the games behind each number. The background worker fills them after every scan, at start-up and daily at 04:30 (Europe/Brussels); it logs `Auto-fill handled N game(s)`. If the numbers do not fall: check the backend log with `$COMPOSE logs backend | grep -i "auto-fill"` (§12 sets `$COMPOSE`); IGDB needs `IGDB_CLIENT_ID` and `IGDB_CLIENT_SECRET` (without them non-Steam games only get libretro art); the AI descriptions need `OPENROUTER_API_KEY` (or `ANTHROPIC_API_KEY`).
- **Admin buttons.** "Refresh game data" is free; "Regenerate AI data" asks for confirmation because every game is a paid call. A run shows its progress and can be stopped; five failures in a row with the same cause (credits, a rejected key) end it with the cause shown.
- **LibBot says it cannot answer.** The model call failed (credits, key, provider down): the message says so and the guest's allowance is not spent. Check the AI call counters (`jordylab.ai.calls`) and Settings → AI Models. Questions in a language other than English or Dutch get a fixed request to rephrase.
- **Player counts are missing (TheGamesDB).** LibBot's "player count isn't known yet" note counts local-multiplayer games without `max_local_players`. IGDB fills what it can; TheGamesDB (spec 014) adds the count for console games that are flagged local multiplayer, but only when every entry for that title on that console agrees. The Sources page → "Player counts" shows how many each source filled, how many games have no count and why (Steam games, entries disagree, no exact match, count under 2, console not listed, not looked up yet) and the games behind each number. Steam games and games whose local flag is unknown are never looked up there.
  - **Secret.** `THEGAMESDB_API_KEY`: locally in the untracked `jordylab-be/.env`, in production under `stringData` of `jordylab-secrets` in `deploy/k8s/overlays/prod/secrets.sops.yaml` (edit with `SOPS_EDITOR="idea --wait" sops …`, §13). Without it the source is skipped, the log says so once at start-up, and the Sources page shows "not configured".
  - **Check it works, without printing it.** Presence only: `$COMPOSE exec -T backend sh -c 'test -n "$THEGAMESDB_API_KEY" && echo SET'`. By behaviour: the Sources page shows the remaining monthly allowance after a refresh, or run `./gradlew theGamesDbCheck` locally (one lookup).
  - **A paused source.** The Sources page says until when and why: the key was refused or the monthly allowance (1,000 lookups) is used up (paused at least an hour, or until the allowance timer runs out), the allowance is almost used up (20 left), or the service could not be reached (15 minutes). Games are not charged a failed attempt while it is paused. The key is regenerated on the service's own API key page; then update the secret (§13) and deploy.
  - **Never paste a response.** TheGamesDB echoes the full request address, key included, in the `pages` field of every answer. The backend never logs addresses or bodies; do not paste raw answers into chat, tickets or files either.
- **Search index.** `game_embedding` is rebuilt by the auto-fill sweep when a game changes or the embedding model changes. If it is empty, LibBot still answers (by structure and words), only without meaning-based ranking.

## 24. Postgres major-version upgrade

**Status: written 2026-11-03; the dump and restore were rehearsed on 2026-11-04** against a throwaway PostgreSQL 17
cluster (`pgvector/pgvector:pg17` at manifest-list digest `sha256:7d0a4146…`, plus pgBackRest 2.60.0). The rehearsal covered
steps 2 and 4: a 15 MB `pg_dumpall` restored in about a second, with `gamecatalog.game` 512/512, `flyway_schema_history` at
`20261103005`, the `vector` extension present. The cutover and the way back (steps 5 and 7)
need a real major bump and were not rehearsed. A minor or patch bump never needs any of this: it is a new tag of the same
major image. A major upgrade is a deliberate rebuild, a dump and a restore, and it is downtime: Postgres stops for the dump
and again for the cutover.

**Before you start:** a full backup and a restore drill from the last week (§15); the live release in
`state/current-release` (§5); at least twice the database size free under `/var/lib/docker` (a dump plus a second volume);
and confirmation that the backend (Spring Boot/Hibernate) supports the new major — check its release
notes, and keep the pgvector extension: the pinned base image is the `pgvector/pgvector` one.

1. **Stop the writers, keep Postgres up**, so the dump is consistent:
   ```
   $COMPOSE stop backend
   ```
2. **Dump everything** — roles and the `jordylab` database — as root, with the redirect inside
   the root shell so the file is never world-readable:
   ```
   sudo sh -c 'umask 077; docker exec -i jordylab-postgres-1 pg_dumpall -U postgres > /opt/jordylab/postgres-<old-major>-<date>.sql'
   ```
   Do **not** add `--clean`: it emits `DROP ROLE postgres` and `DROP DATABASE template1`, and a running cluster refuses the
   first ("current user cannot be dropped"). The target is a fresh volume, so no drops are needed. The dump is a copy of
   production data: keep it mode 600 on the host and delete it once the new stack is proven.
3. **Build the new image.** In `deploy/compose/postgres/Containerfile`, change the base to the new major's
   `pgvector/pgvector:pg<new>` **multi-arch manifest-list digest** (a single-platform manifest breaks the build, BUG-081)
   and the pinned `pgbackrest` version. Mirror `deploy/compose` to the host (§5), then build; keep the old image:
   ```
   cd /opt/jordylab/compose
   sudo -u jordylab-deploy docker compose --env-file /opt/jordylab/env/prod.env build postgres
   sudo docker image ls jordylab-postgres
   ```
4. **Rehearse into a throwaway volume** (no published ports, nothing touches the real data):
   ```
   sudo docker volume create jordylab-postgres-<new>-rehearsal
   sudo docker run -d --name jordylab-pg<new>-rehearsal --env-file /opt/jordylab/env/prod.env \
     --mount type=volume,src=jordylab-postgres-<new>-rehearsal,dst=/var/lib/postgresql/data \
     jordylab-postgres:pg<new>-pgbackrest
   sudo sh -c 'docker exec -i jordylab-pg<new>-rehearsal psql -U postgres -d postgres < /opt/jordylab/postgres-<old-major>-<date>.sql'
   sudo docker exec -i jordylab-pg<new>-rehearsal psql -U postgres -d jordylab -At \
     -c "select count(*) from gamecatalog.game" -c "select version, success from flyway_schema_history order by installed_rank desc limit 1"
   sudo docker rm -f jordylab-pg<new>-rehearsal && sudo docker volume rm jordylab-postgres-<new>-rehearsal
   ```
   Expect exactly one harmless error, `role "postgres" already exists` (the fresh cluster already has it); any other error
   is a real problem. Do not add `ON_ERROR_STOP`, or that one error aborts the restore. Fix anything else here, not on the
   real volume.
5. **Cut over.** Stop the stack, point `compose.yaml` at the new image and a **new** volume
   (`jordylab_postgres-data-<new>`), start Postgres alone, load the dump, then the rest of the stack:
   ```
   $COMPOSE down
   # edit compose.yaml: postgres image and the postgres-data volume name
   $COMPOSE up -d --wait postgres
   sudo sh -c 'docker exec -i jordylab-postgres-1 psql -U postgres -d postgres < /opt/jordylab/postgres-<old-major>-<date>.sql'
   $COMPOSE up -d --wait
   ```
   Then the smoke checks (§10 step 5). Leave the old volume in place: that is the way back.
6. **Restart backups on the new major.** Take a full backup now; WAL archiving resumes by itself, and pgBackRest reads
   the old chain fine (retention drops it on its own):
   ```
   $COMPOSE exec -T postgres pgbackrest --stanza=jordylab --type=full backup
   $COMPOSE exec -T postgres pgbackrest --stanza=jordylab info
   ```
   Delete the dump file from step 2 once this succeeds.
7. **Go back** (while the old volume still exists): `$COMPOSE down`, point `compose.yaml` back at the old image and
   `jordylab_postgres-data`, `$COMPOSE up -d --wait`, and deploy the previous release (§11). Anything written after the
   cutover lives only in the new volume: dump it first if it matters.

---

## 25. Break-glass: an admin locked out

There is no email recovery. A normal user's reset goes through Settings → Users (the person requests it, the admin
approves it). When the **only admin** cannot sign in, the operator restores the admin password directly in the
database — the documented last resort (spec 019 US6, D15). Do it over Tailscale, never over a public port.

1. Generate a BCrypt hash of a temporary password (12+ characters, not the username). `htpasswd` ships with macOS
   (`/usr/sbin/htpasswd`) and `apache2-utils` on Linux; Spring Security's BCrypt encoder accepts the `$2y$` prefix.
   ```bash
   htpasswd -bnBC 10 "" 'choose-a-temporary-password' | tr -d ':\n'
   ```
2. Update the admin row in production. This also clears the lockout state:
   ```bash
   ssh ubuntu@100.93.81.41 "sudo docker compose --env-file /opt/jordylab/env/prod.env \
     -f /opt/jordylab/compose/compose.yaml exec -T postgres psql -U postgres -d jordylab -c \
     \"UPDATE identity.user_account SET password_hash = '\$2y\$10\$…', failed_attempts = 0, locked_until = NULL WHERE username = 'jordy';\""
   ```
   Quote the hash in single quotes; escape `$` when the shell would expand it. No restart is needed: the next
   sign-in reads the hash.
3. Sign in with the temporary password, change it immediately in Account settings, then enroll or verify a passkey.
4. Rehearse on dev with a throwaway account, never with the real admin: register a user, replace its hash, sign in,
   then delete the account from Settings → Users.
