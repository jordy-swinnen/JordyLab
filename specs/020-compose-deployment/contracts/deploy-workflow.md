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
5. **Write the environment file on the host**: `/opt/jordylab/env/prod.env`, mode 600, owner the deploy user. The file it replaces is kept as `prod.env.previous` until the next deploy overwrites it, and a rollback restores it.
6. **Deploy on the host**: `scripts/deploy-release.sh deploy <tag>` over SSH. The script runs its own health check on the host before it records success (see below). On any failure it rolls back itself and exits non-zero.
7. **External check from the runner**: `https://jordylab.be/` returns 200, and `https://jordylab.be/auth/realms/jordylab/.well-known/openid-configuration` returns 200 with JSON. This checks DNS, the certificate and the firewall from outside the host.
8. **Automatic rollback**: runs only when a step from 5 to 7 has failed (`if: failure()`). It runs `scripts/deploy-release.sh rollback <tag>` over SSH, and the script acts only when `<tag>` is still the live release.

Steps 5 to 8 together mean that every failure after the environment file is written ends with the previous release live again, including a failed external check (FR-011).

## `deploy-release.sh` (on the host)

Two modes. Both keep the state files `/opt/jordylab/state/current-release` (the live tag) and `previous-release` (the tag before it) up to date, and both run with `set -euo pipefail`. The state files change only on success, so during a failed deploy `current-release` still names the release that was live before it.

**`deploy <tag>`**
- Read the current tag (may be empty on the first deploy).
- Export `JORDYLAB_RELEASE=<tag>`, run `docker compose pull backend frontend keycloak`, then `docker compose up -d --wait --wait-timeout 600 backend frontend keycloak`. Postgres, ntfy and Caddy are not restarted by a release. Caddy restarts only when `Caddyfile` changed, which is a separate, owner-approved step.
- Run the host health check: `https://jordylab.be/` and the OpenID discovery URL, requested with `curl --resolve jordylab.be:443:127.0.0.1`, so they go through Caddy and its real certificate and routes.
- On success: `previous-release` = the old current tag, `current-release` = `<tag>`, exit 0. `prod.env.previous` is kept.
- On failure of the pull, the `up --wait` or the health check: restore `prod.env` from `prod.env.previous`, redeploy the **live** tag (the old `current-release`) with the same commands and health check, leave the state files unchanged, and exit 1. If there was no live tag (first deploy), exit 1 and change nothing else. The owner decides.

**`rollback <tag>`**
- Runs after `<tag>` was deployed and then failed the external check (step 7). It acts only when `<tag>` is `current-release`; otherwise it exits 1 and changes nothing.
- Restore `prod.env` from `prod.env.previous` if that file exists. Redeploy the tag in `previous-release` with the same commands as `deploy`, then run the same health check.
- On success: `current-release` = the previous tag, exit 1 (the job still failed). On failure, exit 1 and leave the state file naming the tag that was live last.
- If there is no previous tag, exit 1 and change nothing else. The owner decides.

Every tag is checked against the release pattern before anything runs. The local script `scripts/deploy-release.sh` implements these rules (spec 020, tasks T009).

## Rollback by hand

Run the workflow with an earlier release tag (`workflow_dispatch`, same approval). The rule is unchanged from today's runbook (§20).

## Known limit (recorded, not changed)

Flyway runs when the backend starts. A release that applies a migration cannot be rolled back by redeploying the older image without a database change. This is the same as on k3s today; the runbook states it.

## Success and failure

- Success: the job reports success only when `deploy-release.sh deploy` exits 0 and the external check (step 7) passes.
- Failure: any failed step fails the job. Steps 5 to 7 are followed by the automatic rollback in step 8, and the job reports the failure either way. Nothing is retried silently, and the state file always names the live tag.
