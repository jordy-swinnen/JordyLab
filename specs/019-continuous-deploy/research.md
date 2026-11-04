# Research: Continuous Deploy on Green Main

**Feature**: 019 | **Date**: 2026-11-03 | **Spec**: [spec.md](spec.md)

Every decision below answers a question the spec left open or that the draft got wrong. Facts were checked in the
repository and in the upstream documentation named in each item.

## D1. Trigger shape: a final `deploy` job in `build.yml`

**Decision**: `build.yml` gets a `deploy` job with `needs: [build-and-push]`, a job-level `if` for a push to `main`, and
`uses: ./.github/workflows/deploy-prod.yml`. It passes `target: ${{ github.sha }}` and `automatic: true`, with
`secrets: inherit`.

**Rationale**: the job needs exactly the images that `build-and-push` just published for the same commit. Being in the
same run means no lookup of another run, no `head_sha` plumbing, and no risk of deploying on a pull-request build. If any
image or check fails, `needs` skips the deploy (FR-004).

**Alternatives considered**:
- `workflow_run` on Build: works, but the deploy would have to find the commit and the images from another run, and
  filter out pull-request runs. More moving parts for the same result.
- A separate `deploy.yml` on `push` to `main`: would race the build; it would have to wait for the images anyway.

**Consequence**: the deploy result shows on the `main` Build run. A failed deploy turns that run red even though the tests
passed. Pull-request checks are unaffected, because the job does not run for pull requests.

## D2. Queueing: `queue: max`, not the default `single`

**Decision**: `deploy-prod.yml` sets `concurrency: { group: deploy-production, cancel-in-progress: false, queue: max }`.

**Rationale** (GitHub docs, *Control the concurrency of workflows and jobs*): with the default `queue: single`, "any
existing `pending` job or workflow run in the same concurrency group will be canceled and the new queued job or workflow
will take its place." A queued manual rollback would therefore be cancelled silently by an automatic deploy merged a
minute later, which is exactly when a rollback is needed. With `queue: max`, up to 100 runs wait, processed first-in
first-out, and none is dropped. Superseded runs are then skipped by D5, not by GitHub.

**Consequence**: spec FR-005, User Story 4 and the two-merges edge case change from "a newer waiting deploy replaces an
older one" to "queued deploys run in order; none is dropped; a superseded one is skipped". The success criterion (no older
commit deployed after a newer one) still holds.

## D3. Live state and the live commit: read from the cluster

**Decision**: before anything is applied, the workflow reads the three live images from the cluster
(`kubectl -n jordylab get deploy backend frontend keycloak -o jsonpath=…image`). The live commit comes from the backend
image tag: `sha-<40 hex>` gives the sha; a release tag `vX.Y.Z[-rcN]` is mapped with `git rev-parse "<tag>^{commit}"` on
the full-history checkout; anything else (for example the `placeholder` tag in the overlay) gives `unknown`.

**Rationale**: the cluster is the only place that knows what is live. No second source of truth can drift from it. Every
deploy so far was tag-based, and the tag still maps to a commit through git, so old releases are covered.

**Alternatives considered**:
- A Deployment annotation with the commit, written at each deploy: needs a write after every apply and an interaction
  with server-side apply; it is state that can go stale.
- A GitHub variable with the last deployed sha: a second source of truth, and it breaks on a manual `kubectl` rollback.
- Asking the running app for its commit (`/actuator/info`, D8): only tells what runs now, needs a port-forward, and does
  not help the decision that happens before apply. Kept for verification (SC-006), not for decisions.

**Unknown live state**: when the live tag cannot be mapped, the superseded check is skipped (D5) and a rollback has no
target, so FR-012 applies: the run fails and reports, and does not roll back.

## D4. Rollback: re-apply the previous commit from its own checkout

**Decision**: a rollback runs `git worktree add --detach "$RUNNER_TEMP/previous" <previous commit>`, decrypts that
worktree's `secrets.sops.yaml`, sets the three images to the live tags recorded before the apply, runs `kustomize build`,
applies, and waits on the same rollouts. The previous commit's images **and** manifests are used (FR-010).

**Rationale**: the spec requires images and manifests. A commit's manifests are only consistent with its own images. The
worktree leaves the target checkout untouched.

**Alternatives considered**:
- Images only (`kubectl set image` to the old tags, keep today's manifests and secrets): keeps the secrets as they are,
  but the new commit's configuration may not match the old code (a removed env var, a changed probe). Rejected as the
  default; it could become an emergency option in the runbook.

**Known risk, accepted with a warning**: the previous commit's `secrets.sops.yaml` is applied, so a secret value changed
after that commit is reverted. The deploy job compares the secrets file between the previous and target commits
(`git diff --quiet <prev> <target> -- deploy/k8s/overlays/prod/secrets.sops.yaml`) and prints a warning when they differ,
because the ciphertext changes whenever the value does. The runbook says: for a secret rotation, roll forward, do not roll back.

## D5. Superseded guard (automatic deploys only)

**Decision**: a pure decision function, `deploy/scripts/deploy-decision.sh`, returns one of:
- `refused`: the target is neither a 40-hex sha nor a release tag (FR-014). Nothing touches the cluster.
- `superseded`: automatic run, and the target is an ancestor of the live commit (`git merge-base --is-ancestor target live`,
  target ≠ live). The run succeeds with a notice and does not apply (FR-006).
- `proceed`: everything else, including manual runs of older commits (rollbacks, FR-015), target = live (idempotent
  re-apply), and unknown live state.

**Rationale**: the late-finishing older build is the one way an automatic deploy can undo a newer fix. Ancestry on `main`
is the exact definition of "older". It is a function of its inputs, so it is testable with a temporary git repository and
fixture tags, following `.githooks/tests/commit-msg-cases.sh`.

**Ordering guarantee**: with `queue: max` (D2) runs never overlap, so the check and the apply cannot race.

## D6. Target validation and image existence

**Decision**: the grammar is checked by `deploy-decision.sh`: `^[0-9a-f]{40}$` or `^v[0-9]+\.[0-9]+\.[0-9]+(-rc[0-9]+)?$`.
The workflow then checks that all three images exist for the target (`docker buildx imagetools inspect` for
`ghcr.io/jordy-swinnen/jordylab-{backend,frontend,keycloak}:<image tag>`, where the image tag is `sha-<sha>` or the
release tag). The first failure stops the run before the cluster is read.

**Rationale**: a sha without images, or a typo, must fail before anything changes (FR-014). `imagetools` is already used
by the release retag step, so no new tool.

## D7. Smoke check

**Decision**: after the rollouts, three public checks run against `https://jordylab.be`, each retried 6 times at 10 s:
1. `GET /` returns `200` (the frontend through the gateway).
2. `GET /api/settings/users` returns `401` without a token. It is admin-only (`SecurityConfig`), so anonymous access gets
   401: the gateway, Traefik and the backend all answer.
3. `GET /auth/realms/jordylab/.well-known/openid-configuration` returns `200` (Keycloak behind the gateway; the login path).

Readiness of the backend is covered inside the cluster by `kubectl rollout status`, which waits for the readiness probe.

**Rejected**: `/actuator/health` for the public check. `SecurityConfig` permits it, but the gateway does not route
`/actuator` (only `/`, `/api`, `/auth/...`, `/ntfy` and `/.well-known/assetlinks.json`), so the public URL would 404.

## D8. The commit visible in the running app

**Decision**:
- `Containerfile` (backend): `ARG JORDYLAB_GIT_COMMIT=unknown` in the runtime stage, then `ENV JORDYLAB_GIT_COMMIT=${JORDYLAB_GIT_COMMIT}`.
- `build.yml`: the backend matrix entry passes `build-args: JORDYLAB_GIT_COMMIT=${{ github.sha }}`.
- `application.yaml`: `info.commit: ${JORDYLAB_GIT_COMMIT:unknown}` and `management.info.env.enabled: true`.

**Rationale** (Spring Boot reference, *Endpoints* → *Info*, verified): the `EnvironmentInfoContributor`, which exposes
`info.*` properties, is **disabled by default**; `management.info.env.enabled` must be true. A build argument is one line
and needs no Gradle plugin and no git metadata in the image build.

**Alternatives considered**: the `git.properties` route (`management.info.git`, enabled by default when the file exists)
needs a Gradle plugin that reads `.git`; the image build does not otherwise have git metadata, so this is more moving
parts than a build argument.

**Reading it**: the deploy job starts a `kubectl port-forward deploy/backend` and reads `/actuator/info` locally. This does
not need `curl` or `wget` in the image, whose tools were not checked. `/actuator/info` is `permitAll` in `SecurityConfig`
but not routed by the gateway, so the sha is not public.

**Images built before this change** report `unknown`. The deploy log uses the git mapping (D3) for them.

## D9. Backup freshness

**Decision**: `deploy/scripts/backup-freshness.sh` reads `kubectl -n jordylab get backups.postgresql.cnpg.io -o json` on
stdin and takes the newest `status.stoppedAt` among objects with `status.phase == "completed"`. It prints the time and a
warning when the age is over 26 hours or no completed backup exists. It never fails the run (FR-018).

**Rationale** (CloudNativePG API reference 1.28, verified): `BackupStatus` has `phase` and `stoppedAt`. The cluster-level
`status.lastSuccessfulBackup` is **deprecated and not set for backup plugins**; this project uses the Barman Cloud plugin,
so it cannot be used. The CI Role already has `get` and `list` on `postgresql.cnpg.io` (`ci-deploy-rbac.yaml`).

**To verify on the first run**: that `phase` reads `completed` on the live `Backup` objects (quickstart step 2).

**Schedule**: daily at 03:00 UTC (`ovh-object-storage.yaml`), so 26 hours covers one missed run plus margin.

## D10. The APK workflow

**Decision**: `release.yml` becomes `apk-release.yml` with `on: workflow_dispatch` and one input, `tag`. Its jobs are
`verify` (the existing tag rules: `vX.Y.Z[-rcN]`, on `main`, with a successful Build for its commit, versionCode from the
tag) and `apk` (the existing build, signature check and upload). The `retag`, `release`, `deploy` and `publish` jobs are
removed. The release notes that `apk` uploads come from `git log --no-merges --format='- %s' <previous tag>..<tag>`, because
no GitHub Release is created any more.

**Rationale**: the Android build stays manual (FR-022) with its verification unchanged. The notes source changes because
the release draft is gone (FR-024).

**Previous tag** for the notes: the last tag before the chosen one by version order (`git tag --list 'v*' --sort=v:refname`).

## D11. Rollout switch: `AUTO_DEPLOY`

**Decision**: the `deploy` job in `build.yml` runs only when the repository variable `AUTO_DEPLOY` is `'true'`
(`if: … && vars.AUTO_DEPLOY == 'true'`). It is set to `true` after the dry run (D12) passes, and set to `false` to pause
automatic deploys without touching code. Manual deploys are not affected.

**Rationale**: the first automatic deploy would otherwise be the first live test of the new workflow. The switch is also
the incident pause. It is a kill switch, not a product feature flag, so it does not conflict with the spec's "no feature
flags" out-of-scope line; this is recorded in the Complexity Tracking table of the plan.

**Owner decision needed**: confirm the switch (see plan, Open items).

## D12. Dry run for the manual path

**Decision**: `deploy-prod.yml` accepts `dry_run` (boolean, default false) on `workflow_dispatch`. With `dry_run: true` the
run validates the target, reads the live state, runs the backup check and the decision, runs
`kubectl apply --dry-run=server` on the rendered manifests, and runs the smoke check. It does not apply, roll out or
roll back.

**Rationale**: a workflow changed in a PR only runs from `main` once merged (GitHub dispatches only the default branch's
copy), so the first real exercise must be safe. The dry run exercises the real cluster read, the real images, the real
backup object and the real secrets decryption, without changing production.

## D13. Deployment strategy stays `Recreate`

**Decision**: keep `strategy: Recreate` in `deploy/k8s/base/backend.yaml`.

**Consequence**: every deploy stops the old backend before the new one starts, so each deploy has an outage equal to the
new pod's start-up time (Flyway included). A failed rollout keeps the backend down until the rollout timeout (8 minutes)
and the rollback finish. The spec's User Story 2 says "minutes of downtime at most"; that holds (about 11 minutes worst
case). SC-002 is set to 15 minutes accordingly.

**Rejected for now**: `RollingUpdate` would keep the old pod serving through a failed rollout. But during every deploy two
versions would share the database, which makes the expand/contract rule (FR-019) mandatory from day one, and the single
node's `local-path` volumes would need to be checked for two pods. Revisit after FR-019 has been practised.

**Measured**: the outage of each automatic deploy is recorded with SC-001 (merge to live, and the time the backend was
not ready).

## D14. Permissions and secrets

**Decision**:
- `build.yml`: top-level `permissions: contents: read`. The `build-and-push` job keeps `packages: write`.
- The `deploy` job (caller): `permissions: contents: read, packages: read`.
- `deploy-prod.yml`: job `permissions: contents: read, packages: read`. Secrets `SOPS_AGE_KEY`, `TS_OAUTH_CLIENT_*`,
  `KUBE_*` stay in the `production` environment.
- No new secret is needed.

**Rationale**: a called workflow cannot grant more than the caller allows, so the caller sets the scope. The job needs
`packages: read` for `imagetools inspect` against GHCR.

## D15. Tests

**Decision**:
- `deploy/scripts/tests/deploy-decision-cases.sh` and `deploy/scripts/tests/backup-freshness-cases.sh`: table-driven
  bash fixtures in the style of `.githooks/tests/commit-msg-cases.sh`, with a temporary git repository and commit ancestry
  for the decision cases, and JSON fixtures for the backup cases.
- A new job `deploy-scripts` in `build.yml` runs both on every push and pull request.
- A Spring Boot test for `/actuator/info` (MockMvc, `@Language("JSON")` assertion, AssertJ) asserts `commit` comes from
  `JORDYLAB_GIT_COMMIT`, per the constitution's testing principle IV.
- The workflows themselves are checked with `actionlint` (not installed locally; install at implementation time).

**Not automated**: the live rollout, rollback, smoke and notification. These are proven by the dry run and the drill
(quickstart), because no test can stand in for the real cluster.

## D16. Rollout timeouts

**Decision**: keep the current timeouts (backend 8 min, frontend 5 min, keycloak 8 min). Shortening them would make a
slow start-up look like a failure; lengthening them makes a bad release take longer to roll back.

## Verified facts used above

| Fact | Source |
|------|--------|
| `queue: single` (default) replaces a pending run; `queue: max` keeps up to 100 in FIFO order | GitHub Docs, *Control the concurrency of workflows and jobs* |
| `management.info.env.enabled` needed for `info.*`; env contributor disabled by default | Spring Boot 4.1 reference, *Endpoints* → *Info* |
| `BackupStatus` has `phase`, `stoppedAt`; `Cluster.status.lastSuccessfulBackup` deprecated for plugins | CloudNativePG API reference 1.28 |
| Backend Deployment is `strategy: Recreate` | `deploy/k8s/base/backend.yaml` |
| Gateway routes `/`, `/api`, `/auth/...`, `/ntfy`, assetlinks only | `deploy/k8s/base/gateway.yaml` |
| `/api/settings/**` admin-only; `/actuator/info` `permitAll` | `SecurityConfig.java` |
| Build durations about 9–12 min (push and PR) | `gh run list --workflow build.yml` |
| 33 local `v0.0.1-rc*` tags, no `v0.1.0` | `git tag --list` |
| `ci-deploy` Role: `get`/`list` on `postgresql.cnpg.io`, `apps` | `deploy/k8s/bootstrap/ci-deploy-rbac.yaml` |
| Scheduled backups at 03:00 UTC daily | `deploy/k8s/cluster/ovh-object-storage.yaml` |
| No workflow deletes images | `.github/workflows/*.yml` |
