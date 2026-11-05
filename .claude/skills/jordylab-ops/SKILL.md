---
name: jordylab-ops
description: JordyLab production operations — OVH VPS + Docker Compose, Tailscale SSH, GitHub Actions deploys (by commit sha or release tag), rollbacks, SOPS + age secrets (edited by Jordy in IntelliJ), identity/user administration, pgBackRest backups, TLS via Caddy. Load before running, proposing or explaining anything that touches the live host, the deploy pipeline, prod secrets or the prod identity data.
---

# JordyLab production operations

Everything needed to inspect, deploy, roll back and repair **JordyLab's production deployment**: where it
runs, how the pieces fit, and the exact commands. Jordy is an experienced application developer but new to
operations: explain *why* briefly when you propose something, and prefer commands he can copy-paste.

This skill is the single source of truth for production operations. The `jordylab-devops` subagent (Claude
Code and OpenCode) only loads it; edit operational knowledge here, not in the agents.

Production moved from k3s to Docker Compose on 2026-11-03 (spec 020). The k3s material is kept only as a rebuild
fallback; the narrative of the move is in `specs/020-compose-deployment/` and the project runbook is
`docs/runbook.md`.

## Safety rules (always)

1. **Read before you change.** Diagnose with read-only commands first (`docker ps`, `compose logs`, `compose config`,
   `pgbackrest info`). Quote the error you found before proposing a fix.
2. **Mutations need Jordy's explicit yes**: anything that runs `docker compose up/down/restart/build/exec` that changes
   state, `docker volume rm`, `ssh` commands that change the server, `ufw`, Tailscale policy changes, GitHub
   environment/secret/variable changes, or changing what a deploy does. A deploy is started by hand with a target
   (spec 020) and has no approval step; still, show the exact command and what it changes, then wait. A config change
   (`compose.yaml`, `Caddyfile`, `prod.env`, secrets) needs Jordy's yes even when it reaches production through a merge,
   and so does a Flyway migration or a realm change.
3. **Secrets never pass through the conversation.** Never print, echo, `cat`, or decrypt secret values to the screen.
   Pipe values (`… | gh secret set …`), never select password hashes or machine-token hashes in `psql`, use
   `gitleaks --redact`, and filter logs with `grep -v -i -E 'password|secret'`. Never read `/opt/jordylab/env/prod.env`.
4. **Production changes go through Git** (PR → merge → Build → deploy). A live `docker compose up -d` with a changed
   value is fine to *test* a fix quickly, but the same change must land in the repo, or the next deploy undoes it.
5. **Verify versions against the live source** (Docker Hub, GHCR, release pages) before pinning anything. Say so when
   you couldn't.
6. The host is reachable over Tailscale (`ssh ubuntu@100.93.81.41`, key-only). On Jordy's Mac that usually works; if
   your shell has no working SSH, give Jordy the commands to run and ask for the output. The old kubeconfig
   (`~/.kube/jordylab.yaml`) is useless now: k3s is stopped and disabled.
7. **Never open, print or save decrypted secret content**, and never read values from the running containers. Hand Jordy
   the IntelliJ `sops` command (see Secrets) and the key names to add. The only decrypt you may run is the integrity check
   `sops -d … >/dev/null && echo DECRYPT_OK`, whose output is discarded.

## Where it runs

| Thing         | Value                                                                                                                        |
|---------------|------------------------------------------------------------------------------------------------------------------------------|
| Public URL    | `https://jordylab.be` (DNS: OVH zone, A record → VPS IPv4)                                                                   |
| Server        | OVH VPS-2 (4 vCPU / 8 GB / 75 GB NVMe), London (UK), Ubuntu 26.04 LTS                                                        |
| Public IPv4   | `57.129.163.110` — SSH user `ubuntu`, key-only, passwordless sudo                                                            |
| Admin network | Tailscale; server = `jordylab-vps` (`100.93.81.41`, tag `tag:jordylab-vps`, key expiry disabled)                             |
| CI access     | Tailscale node tag `tag:ci`, allowed to the server on `tcp:22` only, then SSH as `jordylab-deploy` (docker group, no sudo)   |
| Stack         | Docker Compose project `jordylab` from `/opt/jordylab/compose/compose.yaml`, env file `/opt/jordylab/env/prod.env`           |
| Live release  | `/opt/jordylab/state/current-release` (a tag or `sha-<sha>`), previous in `previous-release`; images `ghcr.io/jordy-swinnen/jordylab-{backend,frontend}:<release>` |
| Backups       | pgBackRest → OVH Object Storage, bucket `jordylab-cnpg-backups`, path `/pgbackrest`, `https://s3.gra.io.cloud.ovh.net`        |
| CI/CD         | `Build` on every push; a green `main` build deploys through `Deploy to Production (Compose)` while `AUTO_DEPLOY` is `true`; a manual run takes a commit sha or release tag (no approval) |
| TLS           | Caddy, Let's Encrypt HTTP-01 on port 80 (contact `jordy.swinnen@pm.me`); certificate data in the `jordylab_caddy-data` volume  |
| Secrets       | SOPS + age; private key at `~/Library/Application Support/sops/age/keys.txt` (+ password manager + GitHub env secret)          |

Versions at the cutover (2026-11-03): Caddy 2.11.7, PostgreSQL 16.15 with pgvector (base image pinned by digest),
pgBackRest 2.60.0, ntfy v2.28.0, backend/frontend images from GHCR.

## Architecture

```
Internet ──► VPS :80/:443 (ufw: 22, 80, 443 public)
             └─ Docker Compose project `jordylab` (only caddy publishes ports)
                  caddy ──► /                                     frontend  (nginx :8080, Angular build)
                        ──► /api, /webauthn, /login/webauthn,
                            /.well-known/assetlinks.json           backend   (Spring Boot :8080)
                        ──► /ntfy (prefix stripped)               ntfy      (:80)
                        http → https redirect; TLS via Let's Encrypt HTTP-01
                  postgres  (jordylab-postgres:pg16-pgbackrest, data in jordylab_postgres-data)
                        └─ pgBackRest → S3 GRA: WAL continuous, diff Mon–Sat 03:00 UTC, full Sun 04:00 UTC, 30 days
                  artwork   (jordylab_artwork volume)
Laptop / CI ──Tailscale SSH──► host :22 (CI node tag:ci may reach tcp:22 only)
```

## Repo map (deployment)

| Path                                                             | What                                                                                     | Applied by                                    |
|------------------------------------------------------------------|------------------------------------------------------------------------------------------|-----------------------------------------------|
| `deploy/compose/compose.yaml`                                    | The five services, volumes, memory limits                                                | Mirrored to `/opt/jordylab/compose` per deploy |
| `deploy/compose/Caddyfile`                                       | Public routes, redirect, certificate issuance                                            | Same mirror                                   |
| `deploy/compose/env/prod.env.example`                            | The environment template (`render-env.sh` produces the real file)                        | Deploy                                        |
| `deploy/compose/postgres/`                                       | Production Postgres image: pgvector base + pgBackRest, both pinned                       | Built on the host, first `up`                 |
| `deploy/compose/pgbackrest/pgbackrest.conf`                      | pgBackRest stanza (S3 GRA, `/pgbackrest`, 30 days)                                        | Baked into the Postgres image                 |
| `deploy/compose/host/*.timer`, `*.service`                       | The backup timers (diff Mon–Sat 03:00, full Sun 04:00 UTC)                                | Installed once on the host                    |
| `deploy/compose/scripts/render-env.sh`                           | Decrypts SOPS on the runner and renders `prod.env`                                        | Deploy workflow                               |
| `deploy/compose/scripts/deploy-release.sh`                       | Pulls and restarts backend/frontend, checks the site, restores on failure                 | Deploy workflow, over SSH                     |
| `deploy/scripts/deploy-decision.sh`, `smoke-check.sh`            | Target resolution and the public smoke checks                                             | Deploy workflow                               |
| `deploy/k8s/`, `deploy/host/`                                    | The retired k3s material, kept only for the rebuild fallback (spec 020 R14)               | Nothing applies it                            |
| `.sops.yaml`                                                     | age public key + `encrypted_regex: ^(data\|stringData)$`                                  | sops                                          |
| `.github/workflows/build.yml`, `deploy-prod-compose.yml`         | CI/CD                                                                                     | GitHub                                        |

Secrets in `deploy/k8s/overlays/prod/secrets.sops.yaml`: `jordylab-secrets` (`IDENTITY_BOOTSTRAP_ADMIN_*`, AI keys,
S3 keys, `POSTGRES_SUPERUSER_PASSWORD`, Steam/IGDB/TheGamesDB, `NTFY_TOKEN`) and `jordylab-db-app` (basic-auth pair).
The same file feeds `prod.env` through the rendered environment.

## How the pieces fit (things that must stay true)

- **Only Caddy publishes ports** (80, 443). Everything else is internal to the Compose network; ports in `compose.yaml`
  for other services would be a regression.
- **Caddy routes by whole segment**: `/api` and `/api/*`, the exact `/webauthn` and `/login/webauthn` ceremony paths and
  `/.well-known/assetlinks.json` to the backend, `/ntfy` with the prefix stripped, everything else (including `/apis`) to
  the Angular app.
- **One secrets file, one render**: `render-env.sh` produces `prod.env` on the runner from
  `deploy/k8s/overlays/prod/secrets.sops.yaml`; the workflow writes it on the host (mode 600) and keeps the previous file
  as `prod.env.previous`. A rollback restores it.
- **The DB owner is the Postgres superuser**: the `postgres` service initialises from `POSTGRES_SUPERUSER_PASSWORD`; the
  app connects as `jordylab` (from `jordylab-db-app`). A first-boot init is the only time `POSTGRES_*` is honoured.
- **The Postgres image is built on the host**, not pulled: `jordylab-postgres:pg16-pgbackrest`, from
  `deploy/compose/postgres/Containerfile`. The base image is pinned by its **multi-arch manifest-list digest** (pinning a
  single-platform manifest broke the VPS build with `exec format error`, BUG-081) and pgBackRest by version.
- **WAL archiving is on** (`archive_command=pgbackrest … archive-push`); the timers run `pgbackrest … --type=diff|full
  backup` inside the Postgres container and log to journald. Retention is time-based: 30 days of full backups.
- **Identity is in the backend**: sessions in `identity.spring_session` survive a rollout; revoke/delete ends them on the
  next request. The first admin on a fresh database comes from `IDENTITY_BOOTSTRAP_ADMIN_*` and is ignored once accounts
  exist. Passkeys need `IDENTITY_WEBAUTHN_RP_ID=jordylab.be` and `IDENTITY_WEBAUTHN_ALLOWED_ORIGINS=https://jordylab.be`
  (defaults in `application.yaml`) and the Caddy routes above. Break-glass: `docs/runbook.md` §25.
- **Backend probes** `/actuator/health/{liveness,readiness}` are `permitAll`; `/actuator/info` reports the built commit.
- **Frontend image** copies `dist/apps/jordylab/browser` (Angular application builder output) into nginx.
- **Deploy workflow**: `deploy-prod-compose.yml`, called by `build.yml` for a green `main` build while `AUTO_DEPLOY` is
  `true`, or run by hand with a commit sha or a release tag (`target`). It resolves the target with
  `deploy/scripts/deploy-decision.sh` (`sha-<sha>` or the tag; refused when the images are missing), reads
  `state/current-release` and skips an automatic target older than it, renders `prod.env` from SOPS on the runner, and over
  Tailscale SSH runs `deploy-release.sh` on the host; it then smoke-checks the public site and asks the host to roll back
  when a step failed. Helpers and host scripts come from the workflow's own commit, the release files from the target.
  Pinned sops 3.13.3.
- **Build workflow**: images only on push to `main`; image matrix `fail-fast: false`; the backend gets
  `JORDYLAB_GIT_COMMIT` as a build argument.

## Command reference

Reach the host over Tailscale (key-only), and use one `$COMPOSE` shape for everything:

```bash
ssh ubuntu@100.93.81.41
export COMPOSE='sudo -u jordylab-deploy docker compose --env-file /opt/jordylab/env/prod.env -f /opt/jordylab/compose/compose.yaml'
```

### Access and health of the platform

```bash
tailscale status | grep jordylab-vps               # server reachable over Tailscale?
ssh ubuntu@100.93.81.41 'echo SSH_OK; hostname; uptime'
ssh ubuntu@100.93.81.41 'sudo ufw status verbose'  # expect 22, 80, 443 only
$COMPOSE ps                                        # six services, healthy
sudo docker stats --no-stream
df -h /; df -h /var/lib/docker
```

### App status and debugging

```bash
$COMPOSE ps
$COMPOSE logs --tail=100 backend                   # frontend | caddy | postgres | ntfy
$COMPOSE logs -f backend
$COMPOSE logs --since=15m backend | grep -i -E "error|exception"
cat /opt/jordylab/state/current-release /opt/jordylab/state/previous-release
$COMPOSE exec -T backend sh -c 'echo "$SPRING_PROFILES_ACTIVE"'   # names only, never a secret value
```

Test a runtime fix live (then land the same change in the repo):

```bash
$COMPOSE up -d --force-recreate backend      # after editing /opt/jordylab/env/prod.env by hand: only with Jordy's yes
$COMPOSE restart backend
```

### The stack definition

```bash
cd /opt/jordylab/compose && sudo -u jordylab-deploy docker compose \
  --env-file /opt/jordylab/env/prod.env -f compose.yaml config -q   # validates
# On the Mac, against the template (no secrets needed):
docker compose --env-file deploy/compose/env/prod.env.example -f deploy/compose/compose.yaml config -q
$COMPOSE pull backend frontend
```

The workflow mirrors `deploy/compose/` to `/opt/jordylab/compose` with `rsync --delete`; nothing host-side writes there.

### Deploy, watch, roll back (GitHub)

```bash
gh run list --repo jordy-swinnen/JordyLab --branch main --limit 5
gh run view <run-id> --repo jordy-swinnen/JordyLab --json status,headSha,event
gh run view <run-id> --repo jordy-swinnen/JordyLab --log-failed | tail -40
gh run rerun <run-id> --repo jordy-swinnen/JordyLab --failed
# Deploy a commit sha or a release tag; an older target is a rollback:
gh workflow run deploy-prod-compose.yml --repo jordy-swinnen/JordyLab -f target=<full-sha-or-release-tag>
# The last deploys and what they reported:
gh run list --repo jordy-swinnen/JordyLab --workflow deploy-prod-compose.yml --limit 5
# Pause or resume automatic deploys:
gh variable set AUTO_DEPLOY --repo jordy-swinnen/JordyLab --body false     # true to resume
gh pr create --base main --fill
```

No approval is needed: the Compose workflow runs without one (spec 020). Its job summary names the target, the image tag
and the live release on the host.

Check images exist and are public (anonymous pull):

```bash
for img in backend frontend; do
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

**Jordy edits secrets in IntelliJ** — always hand him the `SOPS_EDITOR="idea --wait"` form, run from the repo root.
`idea` is the JetBrains Toolbox launcher (on his `PATH`); `--wait` makes sops block until he closes the IntelliJ tab,
then sops re-encrypts on exit (sops ≥ 3.9 reads `SOPS_EDITOR` before `EDITOR`). Never run this yourself: the decrypted
file must only ever open in his editor. A changed value reaches production on the next deploy, which renders a new
`prod.env`; a `docker compose up -d` on the host alone does not pick up an unwritten change. Editors can keep plaintext in
local history: clear IntelliJ Local History for the temp file afterwards, or use the VS Code `sops` profile
(`workbench.localHistory.enabled: false`).

### GitHub `production` environment

```bash
REPO=jordy-swinnen/JordyLab
gh secret list --env production --repo $REPO     # DEPLOY_SSH_KEY SOPS_AGE_KEY TS_OAUTH_CLIENT_ID TS_OAUTH_CLIENT_SECRET
gh api repos/$REPO/environments/production --jq '{reviewers: [.protection_rules[]?.reviewers[]?.reviewer.login], branch_policy: .deployment_branch_policy}'
# The deploy SSH key (its public half is in ~jordylab-deploy/.ssh/authorized_keys). Generate a new pair and pipe the
# private half straight into the secret; never print it:
ssh-keygen -t ed25519 -N '' -C 'jordylab-deploy' -f /tmp/deploy_key
gh secret set DEPLOY_SSH_KEY --env production --repo $REPO < /tmp/deploy_key && rm -f /tmp/deploy_key
grep '^AGE-SECRET-KEY-' "$HOME/Library/Application Support/sops/age/keys.txt" | gh secret set SOPS_AGE_KEY --env production --repo $REPO
gh secret set TS_OAUTH_CLIENT_ID --env production --repo $REPO       # prompts
gh secret set TS_OAUTH_CLIENT_SECRET --env production --repo $REPO   # prompts; starts with tskey-client-
```

`KUBE_CA_CERT`, `KUBE_SERVER` and `KUBE_TOKEN` belong to the retired k3s pipeline: remove them when convenient (`gh secret
delete <name> --env production`). The workflow also pins the host's ed25519 fingerprint in `EXPECTED_ED25519_FP`.

Tailscale CI credential: admin console → Settings → *Trust credentials* → OAuth, scope **Keys → Auth Keys → Write**
only, tag `tag:ci`. The secret is shown once; revoke the old credential after replacing it.

### Identity and users

Accounts, grants, sessions, reset requests and machine tokens are managed in **Settings → Users / Machines**. There is no
identity container or admin console. A locked-out admin follows `docs/runbook.md` §25 (break-glass: a BCrypt hash and one
`UPDATE identity.user_account`).

Read-only checks (never select `password_hash`, token hashes or session attributes):

```bash
ssh ubuntu@100.93.81.41
export COMPOSE='sudo -u jordylab-deploy docker compose --env-file /opt/jordylab/env/prod.env -f /opt/jordylab/compose/compose.yaml'
$COMPOSE exec -T postgres psql -U postgres -d jordylab -At -c "select username, role, status from identity.user_account order by username"
$COMPOSE exec -T postgres psql -U postgres -d jordylab -At -c "select principal_name, count(*) from identity.spring_session group by principal_name"
$COMPOSE exec -T postgres psql -U postgres -d jordylab -At -c "select name, scope, revoked_at is not null from identity.machine_token order by created_at desc"
```

A machine token is shown once at creation; if it is lost, revoke it and add the machine again.

### Database and backups (pgBackRest)

```bash
ssh ubuntu@100.93.81.41
export COMPOSE='sudo -u jordylab-deploy docker compose --env-file /opt/jordylab/env/prod.env -f /opt/jordylab/compose/compose.yaml'
$COMPOSE ps postgres
$COMPOSE exec -T postgres pg_isready -U postgres -d jordylab
systemctl list-timers 'jordylab-backup-*'
$COMPOSE exec -T postgres pgbackrest --stanza=jordylab info
$COMPOSE exec -T postgres pgbackrest --stanza=jordylab --type=full backup       # on demand
sudo journalctl -u 'jordylab-backup-*' -n 50                                     # timer output
$COMPOSE exec -T postgres psql -U postgres -d jordylab -At -c "select version, description, success from flyway_schema_history order by installed_rank desc limit 3"
```

The restore drill (point-in-time restore into a throwaway volume) is `docs/runbook.md` §15. The old Barman chain under
`cnpg-backups` is history that pgBackRest cannot read; a pre-cutover restore uses the kept k3s manifests.

### Host, Docker, Tailscale

```bash
ssh ubuntu@100.93.81.41 'sudo docker --version; sudo docker compose version'
ssh ubuntu@100.93.81.41 'sudo docker ps --format "{{.Names}}\t{{.Status}}\t{{.Image}}"'
ssh ubuntu@100.93.81.41 'sudo docker system df; df -h /var/lib/docker'
ssh ubuntu@100.93.81.41 'sudo reboot'            # the stack returns by itself (restart: unless-stopped)
ssh ubuntu@100.93.81.41 'sudo apt-get update && sudo apt-get upgrade -y'
ssh ubuntu@100.93.81.41 'sudo tailscale status | head -3'
```

ufw rules in place: default deny incoming; 80/tcp and 443/tcp only. **Public SSH (22) is closed** (2026-11-03, spec 020
T056); administration is Tailscale SSH (`tailscale up --ssh`), which tailscaled terminates on `tailscale0`, so no ufw rule
for 22 is needed — verified with public 22 closed and tailnet SSH working. SSH hardening lives in
`/etc/ssh/sshd_config.d/00-hardening.conf` (no passwords, no root login) — always keep a second session open when changing
it. A leftover `6443/tcp on tailscale0` rule from k3s can be deleted (`sudo ufw delete allow in on tailscale0 to any port
6443 proto tcp`): nothing listens there any more.

Tailscale policy: `tagOwners` for `tag:jordylab-vps` and `tag:ci`; grants: members → everything,
`tag:ci` → `tag:jordylab-vps` `tcp:22` only; a `tests` block enforces it on save.

### DNS and external checks

```bash
dig +short jordylab.be @1.1.1.1                   # → 57.129.163.110
curl -sI http://jordylab.be | head -3             # 301 → https
curl -sI https://jordylab.be/ | head -3           # 200
curl -s -o /dev/null -w '%{http_code}\n' https://jordylab.be/api/identity/auth/csrf   # 204
curl -s -o /dev/null -w '%{http_code}\n' https://jordylab.be/auth/realms/jordylab     # 404: the old identity route is gone
sudo nmap -Pn -p- -T4 --max-rate 200 57.129.163.110                        # only 22, 80, 443 open
```

## Open items

- Remove the retired `KUBE_*` environment secrets and the unused legacy DB role Secret from SOPS/cluster.
- Restrict the OVH Object Storage user to the single bucket (currently project-wide operator role).
- ntfy subdomain if attachments / web push are needed.
- Turn on VPS auto-renewal at OVH; watch domain renewal.
