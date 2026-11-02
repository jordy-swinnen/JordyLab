# Contract: Compose deploy workflow

The new workflow `.github/workflows/deploy-prod-compose.yml` replaces the kubectl steps of `deploy-prod.yml` at cutover (plan step 22). It keeps the trigger, the approval and the release-tag rules, and changes only what runs on the host.

## Trigger and gates (unchanged)

- Called by `release.yml` after a release tag is verified and its images are retagged, or run by hand with a release tag (`workflow_dispatch`).
- The tag must match `^v[0-9]+\.[0-9]+\.[0-9]+(-rc[0-9]+)?$`, as today.
- Runs in the `production` environment, so the owner approves each run.
- Concurrency group `deploy-production`, `cancel-in-progress: false`, as today.

## Steps

1. **Validate the tag** (same check as today).
2. **Join the tailnet** as `tag:ci` (existing step).
3. **Check out the tag** so the Compose file and scripts match the images.
4. **Decrypt and render**: `scripts/render-env.sh` decrypts `deploy/k8s/overlays/prod/secrets.sops.yaml` with `SOPS_AGE_KEY` and writes the environment file to standard output. The runner passes that output straight into the SSH session's standard input. Nothing is written to a log.
5. **Write the environment file on the host**: `/opt/jordylab/env/prod.env`, mode 600, owner the deploy user. The previous file is kept as `prod.env.previous` until step 7 succeeds.
6. **Deploy on the host**: run `scripts/deploy-release.sh <tag>` over SSH (see below).
7. **Smoke check from the runner**: `https://jordylab.be/` returns 200, and `https://jordylab.be/auth/realms/jordylab/.well-known/openid-configuration` returns 200 with JSON. Failure fails the job.

## `deploy-release.sh <tag>` (on the host)

- Read the current tag from `/opt/jordylab/state/current-release` (may be empty on first deploy).
- Export `JORDYLAB_RELEASE=<tag>`, then `docker compose pull backend frontend keycloak`.
- Run `docker compose up -d --wait --wait-timeout 600 backend frontend keycloak`. Postgres, ntfy and Caddy are not restarted by a release. Caddy is restarted only when `Caddyfile` changed, and that change is a separate, owner-approved step.
- On success: write `current-release` = `<tag>` and `previous-release` = the old current tag.
- On failure (pull error, unhealthy service, or `up --wait` exit code non-zero): restore the previous tag with the same commands, write `current-release` back to the previous tag, and exit non-zero. The job then fails and the owner is told which tag is live.
- If there is no previous tag, exit non-zero without changing anything else.

## Rollback

- **Automatic**: the failure path above, inside the same run.
- **By hand**: run the workflow with an earlier release tag (`workflow_dispatch`, same approval). The rule is unchanged from today's runbook (§20).

## Known limit (recorded, not changed)

Flyway runs when the backend starts. A release that applies a migration cannot be rolled back by redeploying the older image without a database change. This is the same as on k3s today; the runbook states it.

## Success and failure

- Success: the job reports success only when `deploy-release.sh` exits 0 and the smoke check passes.
- Failure: any step's failure fails the job. Nothing is retried silently; the state file always names the live tag.
