# Contract: Deploy to Production (`.github/workflows/deploy-prod.yml`)

The workflow is the only thing that changes production. It has two callers: the `deploy` job of `build.yml` (automatic)
and a person with *Run workflow* (manual, including rollback and dry run).

## Inputs

| Input | Trigger | Type | Required | Default | Meaning |
|-------|---------|------|----------|---------|---------|
| `target` | `workflow_call`, `workflow_dispatch` | string | yes | — | A full 40-hex commit sha, or a release tag `vX.Y.Z` or `vX.Y.Z-rcN` |
| `automatic` | `workflow_call` only | boolean | no | `false` | `true` from `build.yml`; enables the superseded check (D5) |
| `dry_run` | `workflow_dispatch` only | boolean | no | `false` | Validate and read the cluster; apply nothing (D12) |

`build.yml` calls it with `target: ${{ github.sha }}` and `automatic: true`. A manual run sets `automatic` to false
implicitly and cannot enable it.

## Concurrency

```yaml
concurrency:
  group: deploy-production
  cancel-in-progress: false
  queue: max
```

At most one run deploys at a time. Queued runs wait and run in the order they were queued. No queued run is dropped by a
later one. A queued automatic run whose commit is already superseded is skipped by the decision, not by GitHub (D2, D5).

## Outcomes

| Outcome | When | Run result | Cluster |
|---------|------|------------|---------|
| `refused` | `target` matches neither grammar, or an image is missing for it | failed | not touched |
| `superseded` | automatic, and the target is an ancestor of the live commit | succeeded, with a notice | not touched |
| `live` | the target's images and manifests are applied, rollouts complete, smoke checks pass | succeeded | target live |
| `rolled-back` | rollout or smoke failed, previous commit known, rollback passed | failed | previous commit live |
| `rollback-failed` | the rollback itself failed | failed | state unknown; runbook §11 |
| `no-previous` | rollout or smoke failed, no previous commit known | failed | target live but failing |
| `dry-run-ok` | `dry_run: true`, all validation and checks pass | succeeded | not touched |

The run summary (job summary) always states: the target commit, the previous live commit, the live commit at the end, the
backup time and age, and any warning (FR-016, FR-018).

## Checks, in order

1. Grammar and image existence for all three components (`ghcr.io/jordy-swinnen/jordylab-{backend,frontend,keycloak}`).
2. Live state read from the cluster (three images). Live commit mapped (D3).
3. Backup freshness (warning only).
4. Decision (`deploy-decision.sh`). `superseded` ends the run here.
5. Dry run stops here after `kubectl apply --dry-run=server`, then runs the smoke checks.
6. Apply the target's manifests and images; rollouts (backend 8 min, frontend 5 min, keycloak 8 min).
7. Smoke checks (below).
8. On failure, rollback to the previous commit (D4), then its rollouts and smoke checks.

## Smoke checks

Each is retried 6 times, 10 seconds apart, and must return the expected status.

| Check | URL | Expected |
|-------|-----|----------|
| home | `https://jordylab.be/` | `200` |
| api-unauthenticated | `https://jordylab.be/api/settings/users` (no token) | `401` |
| keycloak-discovery | `https://jordylab.be/auth/realms/jordylab/.well-known/openid-configuration` | `200` |

## Secrets and permissions

- Environment `production`: `SOPS_AGE_KEY`, `TS_OAUTH_CLIENT_ID`, `TS_OAUTH_CLIENT_SECRET`, `KUBE_SERVER`, `KUBE_CA_CERT`,
  `KUBE_TOKEN`. No secret is printed; the decrypted secrets file is never written to a log or summary.
- Job permissions: `contents: read`, `packages: read`.
- The environment must **not** have a required reviewer (owner action; FR-002). The environment stays for its secrets.

## Feature switches

- `AUTO_DEPLOY` (repository variable, read by `build.yml`): `'true'` enables the automatic `deploy` job. Anything else
  pauses automatic deploys. Manual runs ignore it (FR-027).
