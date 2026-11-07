# Contract: Production Deployment of Kaneo

Extends the Compose stack (`deploy/compose/`). Nothing here changes how the backend and frontend are deployed or rolled
back. Names only; values live in the encrypted secrets file and `prod.env`.

## Compose service

```yaml
kaneo:
  image: ghcr.io/usekaneo/kaneo:<tag>@sha256:<digest>   # pinned; tag and digest taken from the registry during the work (task T017), same pin as the trial
  restart: unless-stopped
  mem_limit: ${KANEO_MEM_LIMIT:-768m}                    # operator-set key; adjusted from docker stats after a week without a code change
  logging: *logging
  environment:
    KANEO_CLIENT_URL: https://kaneo.jordylab.be
    AUTH_SECRET: ${KANEO_AUTH_SECRET}
    POSTGRES_HOST: postgres
    POSTGRES_DB: ${KANEO_DB_NAME}
    POSTGRES_USER: ${KANEO_DB_USER}
    POSTGRES_PASSWORD: ${KANEO_DB_PASSWORD}
    DISABLE_REGISTRATION: ${KANEO_DISABLE_REGISTRATION}
    DISABLE_WORKSPACE_CREATION: "true"
    DISABLE_GUEST_ACCESS: "true"
    TRUSTED_PROXIES: <Compose network range>   # read on the host (docker network inspect) before the first deploy, task T017
    GITHUB_APP_ID: ${KANEO_GITHUB_APP_ID}
    GITHUB_APP_NAME: <app name>                 # chosen when the owner creates the GitHub App (task T069)
    GITHUB_WEBHOOK_SECRET: ${KANEO_GITHUB_WEBHOOK_SECRET}
    GITHUB_PRIVATE_KEY_BASE64: ${KANEO_GITHUB_PRIVATE_KEY_BASE64}
  depends_on:
    postgres:
      condition: service_healthy
  healthcheck:
    test: ["CMD", "wget", "-q", "-O", "/dev/null", "http://127.0.0.1:5173/api/health"]   # as in Kaneo's own file
    interval: 15s
    timeout: 5s
    retries: 5
```

- No `ports:`. Caddy does **not** `depends_on` it. Kaneo's `KANEO_CLOUD`, Sentry and S3 variables stay unset.
- `KANEO_DISABLE_REGISTRATION`, `KANEO_MEM_LIMIT` and `BACKLOG_PROVIDER` are **GitHub repository variables** (set with
  `gh variable set`, like `AUTO_DEPLOY`), read by the deploy workflow and handed to `render-env.sh`, which writes them into
  `prod.env` with the defaults in the table below when a variable is unset. Closing registration, resizing the container and
  switching the backend are therefore never code changes. They take effect on the next deploy run. The other `DISABLE_*`
  values above are literals.
- `KANEO_API_KEY` is a **production environment secret** (`gh secret set KANEO_API_KEY --env production`, value piped,
  never printed) because it can only be created after Kaneo runs. The other secrets are in the SOPS file on the branch.

## Caddy

```
kaneo.jordylab.be {
	reverse_proxy kaneo:5173
}
```

A site block in `deploy/compose/Caddyfile`, validated and reloaded by the existing `apply-caddy-config.sh` step. DNS: an A
record for `kaneo.jordylab.be` at the VPS IPv4 (owner, at OVH).

## Database

`deploy/compose/scripts/ensure-kaneo-database.sh` (runs on the host as the deploy user, idempotent):

- reads `KANEO_DB_NAME`, `KANEO_DB_USER`, `KANEO_DB_PASSWORD` from `prod.env` without printing them;
- connects through `docker compose exec -T postgres psql -U postgres ...` and sends SQL on **stdin** (the password is
  passed as a `psql -v` variable and quoted with `format('%L', ...)` inside the script, never in argv);
- `CREATE ROLE` if missing (and `ALTER ROLE ... PASSWORD` so a rotated secret takes effect), `CREATE DATABASE ... OWNER`
  if missing via `\gexec`, `REVOKE CONNECT ... FROM PUBLIC` on the new database.

The existing backups cover it (WAL archiving plus diff/full backups of the whole cluster).

## Secrets and environment names

| Name | Source | Notes |
|---|---|---|
| `KANEO_AUTH_SECRET` | secrets file | `openssl rand -hex 32`, >= 32 chars; rotating it signs everyone out |
| `KANEO_DB_PASSWORD` | secrets file | generated without `/`, `+`, `=` |
| `KANEO_DB_NAME`, `KANEO_DB_USER` | config literals | `kaneo`, `kaneo` |
| `KANEO_DISABLE_REGISTRATION` | repository variable | default `true`; `false` only during the bootstrap window if the trial demands it |
| `KANEO_MEM_LIMIT` | repository variable | default `768m` |
| `BACKLOG_PROVIDER` | repository variable | `linear` until cutover, then `kaneo` |
| `KANEO_GITHUB_APP_ID`, `KANEO_GITHUB_WEBHOOK_SECRET`, `KANEO_GITHUB_PRIVATE_KEY_BASE64` | secrets file | from the GitHub App the owner creates |
| `KANEO_API_KEY` | production environment secret | created in Kaneo's UI after the first deploy; backend only |
| `KANEO_BASE_URL`, `KANEO_PROJECT_KEY`, `KANEO_TICKET_URL_TEMPLATE` | config literals | backend |

Added to `env/prod.env.example`, `scripts/render-env.sh` and the environment contract of the compose deployment. The
backend service gains the backend-side names in its `environment:` block. `LINEAR_API_KEY` stays until the follow-up pull
request.

## Deploy workflow step (best effort, never rolls back the release)

After `deploy-release.sh deploy <release>` succeeds and the public smoke check passes:

1. `ensure-kaneo-database.sh`
2. `docker compose up -d kaneo` (only recreates when the image pin or config changed)
3. wait for health (up to 2 minutes) and run the Kaneo smoke check: `GET https://kaneo.jordylab.be/api/health` expects 200
   (body **trial**), plus a closed-registration probe if the trial finds a safe unauthenticated one (for example the
   instance-status or sign-up configuration reporting registration disabled); without such a probe the runbook's manual
   check applies after every deploy that touches Kaneo's configuration (spec FR-006).

Any failure in 1-3 prints a warning in the job summary (`::warning::`) and the job stays green. The release state files are
not touched by this step.

## Upgrade procedure (runbook section)

1. Read the release notes for every version between the pin and the target (security notes first).
2. `pgbackrest --stanza=jordylab --type=diff backup`.
3. Change the tag **and** digest in `compose.yaml` in a pull request; merge; deploy (config change needs the owner's yes).
4. Check `/api/health` and sign in; open one ticket.
5. Rollback: restore the previous pin through a deploy of the previous commit; if the new version migrated the schema in
   a way the old one cannot read, restore the `kaneo` database from the backup taken in step 2 (restore drill, runbook).

## Capacity check (FR-010)

Before the first production start and one week after: `free -m`, `docker stats --no-stream`, `df -h /`. Record the numbers
in the runbook. Stop and ask the owner if available memory (including page cache the app relies on) would fall below 25%
of the host at normal load.
