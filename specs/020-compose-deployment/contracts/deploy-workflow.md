# Contract: Compose deploy workflow

The workflow `.github/workflows/deploy-prod-compose.yml` replaced the kubectl steps of `deploy-prod.yml` at cutover (plan step 22). It deploys the Compose stack on the VPS.

**Updated 2026-11-03, after the cutover.** `release.yml` no longer exists (spec 019 removed it), so nothing calls this workflow: it is run by hand. It accepts a full commit sha as well as a release tag, and maps the target to the image tag `sha-<sha>` or the tag itself, so a sha deploy runs exactly the images its Build published and no retag step is needed. `deploy-prod.yml` and the `build.yml` deploy job were removed with k3s. Automatic deploys are off; whether they return is T062.

## Trigger and gates

- Run by hand (`workflow_dispatch`) with a target: a full commit sha, or a release tag `vX.Y.Z` / `vX.Y.Z-rcN`.
- A target is refused before the host is touched when it cannot be resolved: a sha outside the history, a tag that does not exist, or any of the three images missing from GHCR.
- Runs in the `production` environment for its secrets. There is no approval step.
- Concurrency group `deploy-production`, `cancel-in-progress: false`, `queue: max`.

## Steps

1. **Resolve the target** with `deploy/scripts/deploy-decision.sh` (spec 019): the sha or tag is validated, and the result names the image tag `sha-<sha>` or the tag.
2. **Join the tailnet** as `tag:ci` (existing step).
3. **Check out the target** for the release files (`compose.yaml`, the Caddyfile, the Postgres build), and check out the
   **workflow's own commit** for the helpers and the host scripts. The target must not supply the tooling: a rollback
   to an older commit would otherwise restore older `deploy-release.sh`/`render-env.sh` (run 41730834972 failed that way).
4. **Decrypt and render**: `scripts/render-env.sh` decrypts `deploy/k8s/overlays/prod/secrets.sops.yaml` with `SOPS_AGE_KEY` and writes the environment file to standard output. The runner passes that output straight into the SSH session's standard input. Nothing is written to a log.
5. **Write the environment file on the host**: `/opt/jordylab/env/prod.env`, mode 600, owner the deploy user. The file it replaces is kept as `prod.env.previous` until the next deploy overwrites it, and a rollback restores it.
**Caddy config, between steps 5 and 6** (JOR-82): when the mirrored `Caddyfile` differs from the last applied one,
`scripts/apply-caddy-config.sh` validates it, reloads atomically, and keeps the previous file in
`/opt/jordylab/state/Caddyfile.previous`. An invalid file fails the deploy with the running config untouched; a failed
release restores the previous file through `revert-caddy-config.sh` before the release rollback.

6. **Deploy on the host**: `scripts/deploy-release.sh deploy <tag>` over SSH. The script runs its own health check on the host before it records success (see below). On any failure it rolls back itself and exits non-zero.
7. **External check from the runner**: `https://jordylab.be/` returns 200, and `https://jordylab.be/auth/realms/jordylab/.well-known/openid-configuration` returns 200 with JSON. This checks DNS, the certificate and the firewall from outside the host.
8. **Automatic rollback**: runs only when a step from 5 to 7 has failed (`if: failure()`). It runs `scripts/deploy-release.sh rollback <tag>` over SSH, and the script acts only when `<tag>` is still the live release.

Steps 5 to 8 together mean that every failure after the environment file is written ends with the previous release live again, including a failed external check (FR-011).

## `deploy-release.sh` (on the host)

Two modes. Both keep the state files `/opt/jordylab/state/current-release` (the live tag) and `previous-release` (the tag before it) up to date, and both run with `set -euo pipefail`. The state files change only on success, so during a failed deploy `current-release` still names the release that was live before it.

**`deploy <release>`**
- Read the current tag (may be empty on the first deploy).
- Export `JORDYLAB_RELEASE=<tag>`, run `docker compose pull backend frontend keycloak`, then `docker compose up -d --wait --wait-timeout 600 backend frontend keycloak`. Postgres, ntfy and Caddy are not restarted by a release. Caddy restarts only when `Caddyfile` changed, which is a separate, owner-approved step.
- Run the host health check: `https://jordylab.be/` and the OpenID discovery URL, requested with `curl --resolve jordylab.be:443:127.0.0.1`, so they go through Caddy and its real certificate and routes.
- On success: `previous-release` = the old current tag, `current-release` = `<tag>`, exit 0. `prod.env.previous` is kept.
- On failure of the pull, the `up --wait` or the health check: restore `prod.env` from `prod.env.previous`, redeploy the **live** tag (the old `current-release`) with the same commands and health check, leave the state files unchanged, and exit 1. If there was no live tag (first deploy), exit 1 and change nothing else. The owner decides.

**`rollback <release>`**
- Runs after `<tag>` was deployed and then failed the external check (step 7). It acts only when `<tag>` is `current-release`; otherwise it exits 1 and changes nothing.
- Restore `prod.env` from `prod.env.previous` if that file exists. Redeploy the tag in `previous-release` with the same commands as `deploy`, then run the same health check.
- On success: `current-release` = the previous tag, exit 1 (the job still failed). On failure, exit 1 and leave the state file naming the tag that was live last.
- If there is no previous tag, exit 1 and change nothing else. The owner decides.

Every release is checked against the release pattern (`vX.Y.Z[-rcN]` or `sha-<40 hex>`) before anything runs. The local script `scripts/deploy-release.sh` implements these rules (spec 020, tasks T009).

## Rollback by hand

Run the workflow with an earlier commit sha or release tag (`workflow_dispatch`). An older target is allowed; that is what makes it a rollback.

## Known limit (recorded, not changed)

Flyway runs when the backend starts. A release that applies a migration cannot be rolled back by redeploying the older image without a database change. This is the same as on k3s today; the runbook states it.

## Success and failure

- Success: the job reports success only when `deploy-release.sh deploy` exits 0 and the external check (step 7) passes.
- Failure: any failed step fails the job. Steps 5 to 7 are followed by the automatic rollback in step 8, and the job reports the failure either way. Nothing is retried silently, and the state file always names the live tag.
