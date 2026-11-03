# Tasks: Continuous Deploy on Green Main

**Input**: Design documents from `/specs/019-continuous-deploy/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/, quickstart.md

**Tests**: included where the plan specifies them (research D15): the two fixture suites under `deploy/scripts/tests/`
and the Spring Boot info-endpoint test. The spec does not ask for test-first ordering, so each test task says what it
checks; it does not block the implementation tasks around it.

**Organization**: grouped by user story (spec priorities P1, P2, P3) so each story can be implemented and checked on
its own. Setup and Foundational come first; Polish and the operational rollout come last.

**Branch**: `jordyswinnen/jor-32-continuous-deploy-on-green-main` (spec folder `019-continuous-deploy`).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel: a different file and no dependency on an incomplete task in the same phase
- **[Story]**: user story label (US1–US9); used only in user story phases
- Every task names the file it changes, or the command it runs

## Planning refinements carried into these tasks

- `deploy/scripts/smoke-check.sh` is added to Foundational. The plan listed only two scripts; the same smoke checks run
  after a deploy, after a rollback and in the dry run (FR-009, FR-028), so one tested place is better than three copies.
- The workflow validates the target **before** reading the cluster, because the decision needs the live tag: the
  script is called once to validate and resolve (live tag empty), then the images are checked (US3), then the live state
  is read (US1), then the script is called again for the final decision.
- A rollback is gated on `apply started` (an output set just before `kubectl apply`), so a failure before the apply
  (for example the Tailscale join) never triggers a rollback.

---

## Phase 1: Setup

**Purpose**: a clean base and the local tools the later checks need

- [X] T001 Rebase `jordyswinnen/jor-32-continuous-deploy-on-green-main` onto `origin/main` (PR #185, spec 017, has merged); confirm `git status` is clean and `specs/019-continuous-deploy/` is unchanged
- [X] T002 [P] Install `actionlint` and `shellcheck` locally (`brew install actionlint shellcheck`) for the checks in T008 and T049

---

## Phase 2: Foundational (blocking prerequisites)

**Purpose**: the scripts every user story calls. No user story can be completed until these exist and their suites pass.

**Checkpoint**: both fixture suites pass locally; `smoke-check.sh` passes shellcheck; the `deploy-scripts` job runs on a pull request.

- [X] T003 [P] Create `deploy/scripts/deploy-decision.sh`: arguments `--target <input>`, `--live-tag <tag or empty>`, `--automatic true|false`. Validate the grammar (`^[0-9a-f]{40}$` or `^v[0-9]+\.[0-9]+\.[0-9]+(-rc[0-9]+)?$`); resolve `target_commit` (the sha itself, or `git rev-parse --verify --quiet "<tag>^{commit}"`) and `image_tag` (`sha-<sha>` or the release tag); refuse a sha that is not in the local history and a tag that does not resolve. Map the live tag to `live_commit`: `sha-<sha>` gives the sha, a release tag is resolved through git, anything else gives `unknown`. Decide per research D5: `superseded` only when automatic, live known, target ≠ live, and `git merge-base --is-ancestor target live` succeeds; otherwise `proceed`; invalid input gives `refused` with a `reason`. Print `decision=`, `target_commit=`, `image_tag=`, `live_commit=`, `reason=` lines for `$GITHUB_OUTPUT`. Exit 0 for every decision (the workflow fails on `refused`); exit 2 only for missing or invalid arguments. Use full names and `set -euo pipefail`.
- [X] T004 [P] Create `deploy/scripts/backup-freshness.sh`: reads the output of `kubectl -n jordylab get backups.postgresql.cnpg.io -o json` on stdin; takes the newest `status.stoppedAt` among objects whose `status.phase` is `completed`; prints `newest_completed_at=`, `age_hours=`, `warning=true|false`, `reason=` (`none-completed`, `older-than-26h`, `unreadable`, or empty). Accepts an optional `--now-epoch <seconds>` so tests are deterministic. Never exits non-zero for a stale, missing or unreadable backup (FR-018): empty or invalid input prints `warning=true reason=unreadable` and exits 0. Exit 2 only for invalid arguments.
- [X] T005 [P] Create `deploy/scripts/smoke-check.sh --base-url <url> [--attempts 6] [--delay-seconds 10]`: runs the three checks from contracts/deploy-workflow.md (`/` → 200; `/api/settings/users` without a token → 401; `/auth/realms/jordylab/.well-known/openid-configuration` → 200), retrying each check up to `--attempts` times with `--delay-seconds` between tries; prints one line per check (name, expected, actual, attempts); never prints a response body; exits 1 when any check still fails after its retries. No fixture suite: it needs the network, so it is checked by shellcheck (T049) and by the dry run (T054).
- [X] T006 Create `deploy/scripts/tests/deploy-decision-cases.sh`, table-driven in the style of `.githooks/tests/commit-msg-cases.sh`: builds a temporary git repository with three commits on one line (A older, B, C newest), the tags `v0.0.1-rc1` and `v0.0.1-rc2` on A and B, and a side branch that diverges from B. Cases must cover: automatic target C with live B → proceed; automatic target A with live C → superseded; manual target A with live C → proceed (rollback); automatic target B with live B → proceed; invalid grammar `v1.2` → refused; a sha not in the history → refused; missing tag → refused; release tag `v0.0.1-rc2` → proceed with `image_tag=v0.0.1-rc2`; live tag `placeholder` → `live_commit=unknown` and proceed; live tag `sha-<A>` → `live_commit=A`; automatic target on the side branch with live C (diverged) → proceed. Count passes and failures and exit non-zero on any failure.
- [X] T007 Create `deploy/scripts/tests/backup-freshness-cases.sh` with JSON fixtures and a fixed `--now-epoch`: fresh completed backup (2 h old) → `warning=false`; completed backup 30 h old → `warning=true reason=older-than-26h`; only `failed` or `started` phases → `warning=true reason=none-completed`; empty `items` → `reason=none-completed`; invalid JSON → `warning=true reason=unreadable` and exit 0; several completed objects → the newest `stoppedAt` wins; a completed object without `stoppedAt` is ignored.
- [X] T008 Add a job `deploy-scripts` to `.github/workflows/build.yml`: no `if`, so it runs on push and on pull request; checks out the repository; runs `bash deploy/scripts/tests/deploy-decision-cases.sh` and `bash deploy/scripts/tests/backup-freshness-cases.sh`; runs `actionlint` over `.github/workflows/*.yml` with the `rhysd/actionlint` Docker image pinned to a release tag; uses no secrets and needs no `packages` permission.

---

## Phase 3: User Story 1 — A merged change goes live without a click (Priority: P1) 🎯 MVP

**Goal**: a commit on `main` whose Build succeeds is deployed to production with its own `sha-<commit>` images, with no approval.

**Independent Test**: with `AUTO_DEPLOY` set to `true` and the reviewer removed, merge a small visible change. The `deploy` job starts after `build-and-push` without an approval prompt, every live image ends with `sha-<merge commit>`, and the job summary names the target, the previous live commit and the backup time.

**Note**: tasks T009–T016 all change `deploy-prod.yml` and must run in this order.

- [X] T009 [US1] Rewrite the triggers and inputs of `.github/workflows/deploy-prod.yml`: `workflow_call` with `target` (string, required) and `automatic` (boolean, default `false`); `workflow_dispatch` with `target` (string, required). Remove the tag-only regex and the `release.yml` header comments. Job `deploy` keeps `environment: production` and gets `permissions: contents: read, packages: read`. Checkout uses `ref: ${{ inputs.target }}` with `fetch-depth: 0` (history and tags are needed for the decision). Pass `target` and `automatic` to steps through `env:`, never inline. Carry over unchanged the Tailscale join, the tool installation (sops, age, kustomize, kubectl pins) and the kubeconfig steps.
- [X] T010 [US1] In `.github/workflows/deploy-prod.yml`, add a step `resolve_target` directly after checkout and before the Tailscale join: run `deploy/scripts/deploy-decision.sh --target "$TARGET" --live-tag "" --automatic "$AUTOMATIC"` and append its output to `$GITHUB_OUTPUT`; fail the job with `::error::` when `decision=refused`. Placing it before the Tailscale join means a refused target never reaches the cluster (FR-014).
- [X] T011 [US1] In `.github/workflows/deploy-prod.yml`, add a step `read_live_state` after the kubeconfig steps: read the three live images with `kubectl -n jordylab get deploy <name> -o jsonpath='{.spec.template.spec.containers[0].image}'` for backend, frontend and keycloak; write `live_backend_image`, `live_frontend_image`, `live_keycloak_image` and `live_tag` (the tag part of the backend image) to `$GITHUB_OUTPUT`. A missing Deployment fails the step (fail fast).
- [X] T012 [US1] In `.github/workflows/deploy-prod.yml`, add a step `decide` after `read_live_state`: run `deploy/scripts/deploy-decision.sh` again, this time with `--live-tag "$LIVE_TAG"` from `read_live_state`; write its outputs to `$GITHUB_OUTPUT`. Every later cluster-changing step is gated on `steps.decide.outputs.decision == 'proceed'`.
- [X] T013 [US1] In `.github/workflows/deploy-prod.yml`, add the apply step with id `apply` (gated on `proceed`): decrypt `deploy/k8s/overlays/prod/secrets.sops.yaml` in the ephemeral checkout only (as today); `kustomize edit set image` for the three images with `image_tag` from `decide`; `kustomize build . | kubectl apply -f -`. Write `started=true` to `$GITHUB_OUTPUT` immediately before `kubectl apply` (the rollback gate, T019).
- [X] T014 [US1] In `.github/workflows/deploy-prod.yml`, after the apply, wait for the rollouts with the existing timeouts (`deploy/backend` 8 min, `deploy/frontend` 5 min, `deploy/keycloak` 8 min), gated on `proceed`.
- [X] T015 [US1] In `.github/workflows/deploy-prod.yml`, add a backup step before the apply: `kubectl -n jordylab get backups.postgresql.cnpg.io -o json | deploy/scripts/backup-freshness.sh` with `|| true` on the kubectl side, so a read failure is reported as `reason=unreadable` and does not fail the run (FR-018); write the outputs to `$GITHUB_OUTPUT`.
- [X] T016 [US1] In `.github/workflows/deploy-prod.yml`, add a final step `summary` with `if: always()` that writes a markdown table to `$GITHUB_STEP_SUMMARY` from the step outputs: target commit, previous live commit (`live_commit` from `decide`), commit live at the end (the target when `live`), backup time and age (FR-016). Steps that did not run show as "not run", never as empty.
- [X] T017 [US1] In `.github/workflows/build.yml`, add top-level `permissions: contents: read` and a job `deploy`: `needs: [build-and-push]`; `if: github.event_name == 'push' && github.ref == 'refs/heads/main' && vars.AUTO_DEPLOY == 'true'` (the repository variable is unset until the dry run passes, D11); `permissions: contents: read, packages: read`; `uses: ./.github/workflows/deploy-prod.yml`; `with: target: ${{ github.sha }}, automatic: true`; `secrets: inherit`.

**Checkpoint**: with `AUTO_DEPLOY` unset the deploy job is skipped; with it set to `true` a merge deploys without an approval. Not yet safe to enable: US2 must be in place first.

---

## Phase 4: User Story 2 — A broken release returns to the last good commit (Priority: P1)

**Goal**: when a rollout or the smoke check fails, production returns to the commit that was live before, with its own images and manifests, and the run is reported as failed.

**Independent Test**: the broken-image drill (quickstart Q5, T056). The previous commit serves again within 15 minutes of the start of the failed deploy, the run is red, and GitHub's failure notice arrives.

**Note**: tasks T018–T022 all change `deploy-prod.yml`; they run after T017.

- [X] T018 [US2] In `.github/workflows/deploy-prod.yml`, add a step `smoke_check` after the rollouts, gated on `proceed`: `deploy/scripts/smoke-check.sh --base-url https://jordylab.be`; write the result to the summary through `summary` (T016).
- [X] T019 [US2] In `.github/workflows/deploy-prod.yml`, define the rollback gate: a rollback runs only when `failure()` and `steps.apply.outputs.started == 'true'` (the cluster was changed) and `steps.decide.outputs.live_commit != 'unknown'` (a previous commit is known) and not a dry run. When the previous commit is `unknown` the run fails with `outcome=no-previous` and an explicit message (FR-012); nothing is rolled back.
- [X] T020 [US2] In `.github/workflows/deploy-prod.yml`, add the rollback step, gated by T019: `git worktree add --detach "$RUNNER_TEMP/previous" "$PREVIOUS_COMMIT"` (previous commit = `live_commit`); in that worktree decrypt its `deploy/k8s/overlays/prod/secrets.sops.yaml`; `kustomize edit set image` with the three live image **tags** recorded by `read_live_state` (so the previous images and manifests are used together, FR-010); `kustomize build . | kubectl apply -f -`; wait for the same three rollouts. Remove the worktree in an `if: always()` step (`git worktree remove --force`).
- [X] T021 [US2] In `.github/workflows/deploy-prod.yml`, in the rollback path before the apply, compare the secrets file between the previous and target commits with `git diff --quiet "$PREVIOUS_COMMIT" "$TARGET_COMMIT" -- deploy/k8s/overlays/prod/secrets.sops.yaml`; when it differs, emit `::warning::` and a summary line that a secret value may revert (research D4; the runbook says roll forward for rotations).
- [X] T022 [US2] In `.github/workflows/deploy-prod.yml`, run `smoke-check.sh` after the rollback and set the outcome: `rolled-back` when the rollback's rollouts and smoke checks pass; `rollback-failed` otherwise, with `::error::` naming the failing step and pointing to `docs/runbook.md` §11 (FR-013). The run stays failed in both cases (the original failure is already recorded), and the summary states which commit is live at the end.

---

## Phase 5: User Story 3 — Redeploy or roll back to any commit or release tag (Priority: P2)

**Goal**: a person can start the workflow by hand with a commit sha or an existing release tag, or with `dry_run` to exercise the path without changing the cluster.

**Independent Test**: start the workflow with an earlier release tag: only that release's images and manifests change, no approval is asked, and the summary names the restored release. A dry run changes nothing.

- [X] T023 [US3] In `.github/workflows/deploy-prod.yml`, add the image existence check between `resolve_target` (T010) and `read_live_state` (T011): log in to `ghcr.io` with `docker/login-action@v4` and `GITHUB_TOKEN`, set up buildx, and run `docker buildx imagetools inspect` for `ghcr.io/jordy-swinnen/jordylab-{backend,frontend,keycloak}:<image_tag>`; a missing image fails the job as `refused` before the cluster is read (FR-014, research D6). Needs `packages: read` (already set in T009).
- [X] T024 [US3] In `.github/workflows/deploy-prod.yml`, add the `dry_run` input to `workflow_dispatch` (boolean, default `false`). When it is `true`: apply `kubectl apply --dry-run=server -f -` on the rendered manifests instead of the real apply; skip the rollouts and any rollback; run `smoke_check` (T018); set the outcome to `dry-run-ok` in the summary (FR-028, research D12). Secrets are still decrypted only inside the ephemeral checkout.
- [X] T025 [US3] In `.github/workflows/deploy-prod.yml`, write the `target` input descriptions for manual use (a full commit sha, or a release tag `vX.Y.Z` or `vX.Y.Z-rcN`; older commits are allowed for rollback). Confirm that manual runs leave `automatic` at its default `false`, so the decision can never return `superseded` for a manual target (FR-015).

---

## Phase 6: User Story 4 — Deploys never overlap and never go backwards (Priority: P2)

**Goal**: queued deploys run one after another in order, none is dropped, and an automatic deploy of a commit older than the live one is skipped.

**Independent Test**: push two commits within five minutes. The second run waits; the older commit is skipped with a notice when its turn comes after a newer commit is live; the newer commit is the one live at the end (quickstart Q4).

- [X] T026 [US4] In `.github/workflows/deploy-prod.yml`, add the job-level block `concurrency: { group: deploy-production, cancel-in-progress: false, queue: max }`. Add one short comment giving the reason (with the default queue, a newer deploy would cancel a queued manual rollback; research D2).
- [X] T027 [US4] In `.github/workflows/deploy-prod.yml`, handle `decision=superseded`: write `::notice::` and a summary line stating that the target is older than the live commit and was not applied; the job ends green. Every cluster-changing step is already gated on `proceed` (T013, T014, T018), so this step only reports (FR-006).

---

## Phase 7: User Story 5 — Always know what is live (Priority: P2)

**Goal**: the running backend reports the full sha of the commit it was built from, and the deploy checks that it matches.

**Independent Test**: after a deploy, the commit in the deploy log, the live image tag and the backend's `/actuator/info` (read inside the cluster) name the same commit (SC-006; quickstart Q3).

- [X] T028 [P] [US5] In `deploy/containers/backend/Containerfile`, runtime stage only: `ARG JORDYLAB_GIT_COMMIT=unknown` followed by `ENV JORDYLAB_GIT_COMMIT=${JORDYLAB_GIT_COMMIT}`. Do not change the build stage.
- [X] T029 [P] [US5] In `jordylab-be/src/main/resources/application.yaml`, add `management.info.env.enabled: true` (the environment contributor is disabled by default in Spring Boot 4.1) and `info.commit: ${JORDYLAB_GIT_COMMIT:unknown}`. Check the existing `management:` block around line 79 and keep one block.
- [X] T030 [P] [US5] Create `jordylab-be/src/test/java/dev/jordy/jordylab/ApplicationInfoEndpointTest.java` (beside `JordylabApplicationTests`, because `TestcontainersConfiguration` is package-private): a `@SpringBootTest` in the style of `JordylabApplicationTests` (`@Import(TestcontainersConfiguration.class)`, the issuer-uri property, the `JwtDecoder` substitute the existing tests use); `@AutoConfigureMockMvc`; a property `JORDYLAB_GIT_COMMIT` set to a fixed 40-hex sha; one test that GETs `/actuator/info` without a token and asserts `$.commit` equals that sha with `jsonPath`, and one that checks the default `unknown` when the property is absent is optional. Follow the project's Java style (`AGENTS.md` in `jordylab-be/`): explicit types, no `var`, a blank line before every `return`, full names, AssertJ, `@Language("JSON")` for any inline JSON. On a Podman host set `DOCKER_HOST` and `TESTCONTAINERS_RYUK_DISABLED=true` as the backend AGENTS.md describes.
- [X] T031 [US5] In `.github/workflows/build.yml`, change the `build-and-push` matrix to `include` entries so the backend entry passes `build-args: JORDYLAB_GIT_COMMIT=${{ github.sha }}` and the frontend and keycloak entries pass none (no unused-argument warnings). Runs after T017 (same file).
- [X] T032 [US5] In `.github/workflows/deploy-prod.yml`, after the smoke checks and only on `proceed` or `dry-run-ok`: start `kubectl -n jordylab port-forward deploy/backend 18080:8080` in the background, wait until it answers, read `commit` from `curl -fsS localhost:18080/actuator/info`, and compare it with the target commit. Write the result to the summary; on a mismatch, on `unknown` (images built before this feature) or when unreachable, emit `::warning::`; do not fail the run on it (rollback decisions do not depend on this check). Stop the port-forward in an `if: always()` step.

---

## Phase 8: User Story 6 — Migrations never block a rollback (Priority: P2)

**Goal**: a migration that the previous release cannot run with is split into an expand step now and a contract step later, and the skill and the reviewer say so.

**Independent Test**: a migration that drops a column the previous release still reads is flagged in review, with the expand/contract split proposed; a migration that only adds a nullable column is accepted in one release.

- [X] T033 [P] [US6] In `.claude/skills/flyway-migration/SKILL.md`, add a "Compatibility with the previous release" section (FR-019): removing or renaming a column, table or index that the previous release reads, or making a column required that the previous release still writes without it, is split into an expand migration in this release and a contract migration in a later one. Add the same point to the reminders in "Create Migration" and to "Verify".
- [X] T034 [P] [US6] In `.claude/agents/code-reviewer.md`, add a "## Database migration checklist" section between "## AI checklist" and "## Output Format", with blocking items for: drops, renames and required-column changes that the previous release still uses (expand/contract required); a migration that changes an already-applied file (never allowed); a missing schema header. Keep the marker style the AI checklist uses.
- [X] T035 [P] [US6] In `.opencode/agents/code-reviewer.md`, add the same section as T034, so the body matches the Claude copy after the frontmatter.
- [X] T036 [US6] In `docs/runbook.md`, add a subsection "Rollback and the database" (FR-020): a code rollback does not roll back the schema; after a rollback check the latest row of `flyway_schema_history`, the backend logs for schema errors, and row counts of any table the release changed. Runs before the US9 runbook tasks (same file).

---

## Phase 9: User Story 7 — Release builds leave the merge path (Priority: P2)

**Goal**: the Android APK is built, signed and published only by a manual workflow; merges and deploys never start it.

**Independent Test**: after a week of merges, the APK workflow has no runs started by a push (quickstart Q8).

- [X] T037 [US7] Delete `.github/workflows/release.yml` (refinement: the APK job was already removed on main by spec 018-pwa-replaces-capacitor, so nothing is left to rename; the rest of the file is the tag-based deploy that FR-024 retires, and manual rollback to a release goes through the tag input of `deploy-prod.yml`).
- [X] T038 [US7] Already done on main: spec 018-pwa-replaces-capacitor removed `e2e-android.yml` with the native app, so there is no `workflow_run` trigger left to remove.
- [X] T039 [US7] Nothing left to change: the comments that named `release.yml` went with the native app (spec 018-pwa-replaces-capacitor), and a repository search finds no other reference outside the history documents.

---

## Phase 10: User Story 8 — Backup freshness shows at every deploy (Priority: P3)

**Goal**: the deploy shows the newest completed backup, and warns when it is too old, without blocking.

**Independent Test**: with a backup older than 26 hours (a fixture in T007, and the real object in T041), the summary and the annotation show a warning and the deploy still completes.

- [X] T040 [US8] In `.github/workflows/deploy-prod.yml`, render the backup outputs from T015 in the summary (T016): on `warning=true`, add `::warning::` with the reason (`none-completed`, `older-than-26h` or `unreadable`) and a summary line; the step never fails the run (FR-018).
- [X] T041 [US8] Check the CloudNativePG phase value on the live objects: `kubectl -n jordylab get backups.postgresql.cnpg.io -o jsonpath='{range .items[*]}{.metadata.name} {.status.phase} {.status.stoppedAt}{"\n"}{end}'` (quickstart Q7). If the phase is not `completed` for finished backups, fix the match in `deploy/scripts/backup-freshness.sh` (T004) and its fixtures in `deploy/scripts/tests/backup-freshness-cases.sh` (T007), then re-run the suite.

---

## Phase 11: User Story 9 — The docs and agents describe the new flow (Priority: P3)

**Goal**: the runbook, the root `AGENTS.md`, the learn doc, the ops skill and the devops agent describe the automatic flow, the rollback and the APK workflow, and none still says a release tag or an approval is needed.

**Independent Test**: the grep in quickstart Q9 finds no line that tells the reader to tag a release to go live or to approve a deploy.

- [X] T042 [US9] In `docs/runbook.md`: §9 (the `production` environment has no required reviewer; it keeps its secrets; list them); §10 Deploy (the automatic flow, the `AUTO_DEPLOY` pause switch FR-027, the dry run FR-028, and the list of scheduled jobs a deploy can interrupt with how each catches up, FR-021); §11 Rollback (manual dispatch with a commit sha or a release tag, the rollback is temporary until the revert lands, a secret rotation is rolled forward not back, the emergency `kubectl rollout undo` path kept). Runs after T036 (same file).
- [X] T043 [US9] In `docs/runbook.md` §20: rewrite "Release a version" as the automatic deploy plus the manual APK workflow (`apk-release.yml` with its `tag` input); remove the tag-ruleset text from "GitHub settings (owner only)" and keep the branch ruleset; add the FR-026 table for merge-to-live times and the backend outage, empty until T058. Runs after T042.
- [X] T044 [P] [US9] In `AGENTS.md` (root), Infrastructure section: one paragraph on the deploy flow (automatic on a green `main` build, manual dispatch with a sha or tag for rollback and dry runs, the APK separate, the `AUTO_DEPLOY` pause switch). The Workflow section is unchanged.
- [X] T045 [P] [US9] In `docs/learn/10-cicd.md`: replace the approval walkthrough (the lines about `environment: production` waiting for approval, and the "approve the resulting run" step) with the automatic flow; explain the queue, the superseded skip and the rollback in the same terms as `deploy-prod.yml`.
- [X] T046 [P] [US9] In `.claude/skills/jordylab-ops/SKILL.md`: the approval-delegation rule (safety rules 2 and the surrounding text), the production flow in rule 4, the CI/CD row in the environment table, the deploy-workflow line, and the release, rollback and approval commands. Replace them with: automatic deploy on green `main`; `gh workflow run deploy-prod.yml -f target=<sha|tag>` for rollback and redeploy; `-f dry_run=true` for a check; the APK workflow by hand. Remove the rule that lets the agent approve a deployment: no approvals exist now.
- [X] T047 [P] [US9] In `.claude/agents/jordylab-devops.md`: replace "the skill's safety rules list what counts as a mutation and the one delegation exception" with a reference to the skill's current safety rules (no delegation exception).
- [X] T048 [P] [US9] In `.opencode/agents/jordylab-devops.md`: the same body change as T047.

---

## Final Phase: Polish and cross-cutting checks

**Purpose**: static checks, the local test runs, the doc sweep, and the commit and pull-request gate

- [X] T049 Run `actionlint .github/workflows/*.yml` and `shellcheck deploy/scripts/*.sh deploy/scripts/tests/*.sh`; fix every finding in the files this feature touched.
- [X] T050 [P] Run the checks from quickstart Q1: `bash deploy/scripts/tests/deploy-decision-cases.sh`, `bash deploy/scripts/tests/backup-freshness-cases.sh`, and `cd jordylab-be && ./gradlew test --tests '*ApplicationInfoEndpointTest*' --no-daemon` (set the Podman variables first on a Podman host). Only the changed test is run (root AGENTS.md: no full suite unless asked).
- [X] T051 Doc sweep (quickstart Q9): `grep -rn -i "approve the production\|git tag v0\|reviewer Jordy\|release.yml"` across `docs/runbook.md`, `AGENTS.md`, `.claude/skills/jordylab-ops/SKILL.md`, `.claude/agents`, `.opencode/agents`, `docs/learn/10-cicd.md`, `jordylab-fe/e2e/run.sh`, `.github/workflows`. Fix any line that describes the old flow. Historical mentions in `specs/0xx-*` and `docs/testing/` stay.
- [ ] T052 Commit and open the pull request only after Jordy confirms (AGENTS: do not push or merge unless asked). Commit per phase: subject `JOR-32: <summary>`, last paragraph `Refs: 019/T0xx …` naming the tasks in that commit; body says how it was verified and what was not. The PR uses `.github/pull_request_template.md` and `Part of JOR-32` (the PR that finishes the ticket uses `Fixes JOR-32`).

---

## Operational rollout (after merge; not code changes)

These run against production or the live repository settings and need Jordy's go-ahead. Each one is recorded in the
runbook (T042, T043).

- [X] T053 Owner step: remove the required reviewer from the `production` environment (GitHub → Settings → Environments); keep `AUTO_DEPLOY` unset (quickstart Q0; `docs/runbook.md` §9).
- [X] T054 Dry run on production (quickstart Q2): `gh workflow run deploy-prod.yml -f target=<main sha> -f dry_run=true`, then the two negative cases (`v9.9.9` and an all-zero sha, both refused). Expect `dry-run-ok`, the live commit, the backup line and the smoke checks passing; nothing changed in the cluster.
- [X] T055 Set the repository variable `AUTO_DEPLOY` to `true` and run the first automatic deploy (quickstart Q3): merge a small change; confirm no approval, every image ends with `sha-<merge commit>`, `/actuator/info` reports that commit (SC-006), and record the merge-to-live time, the backend outage, and whether users stayed signed in (`docs/runbook.md` §10).
- [X] T056 Broken-image drill (quickstart Q5), in a quiet window, not 03:00–05:00 UTC and not on the 1st of the month: a PR changes the backend readiness path in `deploy/k8s/base/backend.yaml` to `/actuator/health/does-not-exist`; merge; expect `rolled-back` within 15 minutes of the start (SC-002), a red run and GitHub's failure notice; then revert on `main` and merge the revert (the rollback is temporary).
- [ ] T057 Manual rollback and redeploy (quickstart Q6): `gh workflow run deploy-prod.yml -f target=<previous good sha>`; expect `live`, with only that commit's images and manifests changed (SC-003).
- [X] T058 Record the drill, the manual rollback, the ordering check (quickstart Q4, SC-004) and the first five merge-to-live times and outages in `docs/runbook.md` §20 (FR-026, quickstart Q10).
- [ ] T059 After one week of merges: confirm `apk-release.yml` has no runs started by a push (quickstart Q8; SC-005).

---

## Dependencies & Execution Order

### Phase dependencies

- **Setup (Phase 1)**: no dependencies.
- **Foundational (Phase 2)**: depends on Setup; blocks every user story (the scripts are called by all of them).
- **US1 (Phase 3)**: depends on Foundational. This is the first real deploy path.
- **US2 (Phase 4)**: depends on US1 (it uses the live state read in T011 and the apply in T013).
- **US3 (Phase 5)**: depends on US1 (it inserts into the same flow between T010 and T011).
- **US4 (Phase 6)**: depends on US1 (the gates it relies on are set in T012–T014).
- **US5 (Phase 7)**: T028–T030 are independent and can start as soon as Foundational is done; T031 depends on T017; T032 depends on T018 and the summary task T016.
- **US6 (Phase 8)**: independent of the deploy path; T033–T035 can run at any time after Setup.
- **US7 (Phase 9)**: independent; T038 and T039 can run in parallel with T037.
- **US8 (Phase 10)**: depends on US1 (T015–T016).
- **US9 (Phase 11)**: T044–T048 can be drafted in parallel at any time; T042 and T043 depend on T036 and on the behaviour of US1–US4 being final.
- **Polish (Final Phase)**: depends on every code task.
- **Operational rollout**: depends on the merge; T054 must pass before T055 sets `AUTO_DEPLOY` to `true`, and T056 must pass before the feature is called done.

### Within `deploy-prod.yml` (the sequence is fixed)

`T009` (triggers, checkout) → `T010` (resolve) → `T023` (images) → `T011` (live state) → `T012` (decide) → `T015` (backup)
→ `T013` (apply) → `T014` (rollouts) → `T018` (smoke) → `T019`–`T022` (rollback) → `T024` (dry run) → `T026`–`T027`
(concurrency, superseded) → `T032` (info check) → `T016` and `T040` (summary) — with `T025` anywhere after `T009`.

### Parallel opportunities

- Setup: T001 and T002 (different concerns).
- Foundational: T003, T004 and T005 (three different scripts). T006 after T003; T007 after T004; T008 after both suites.
- US5: T028, T029 and T030 (Containerfile, `application.yaml`, a new test class).
- US6: T033, T034 and T035 (skill and the two reviewer copies).
- US7: T038 and T039 (two workflows and comments) once T037 is under way.
- US9: T044, T045, T046, T047 and T048 (five different files).
- Polish: T050 with T049.

---

## Parallel Example: Foundational

```bash
# three scripts at once, all different files:
Task: "Create deploy/scripts/deploy-decision.sh (T003)"
Task: "Create deploy/scripts/backup-freshness.sh (T004)"
Task: "Create deploy/scripts/smoke-check.sh (T005)"
```

## Parallel Example: US9

```bash
Task: "AGENTS.md Infrastructure paragraph (T044)"
Task: "docs/learn/10-cicd.md (T045)"
Task: "jordylab-ops SKILL.md (T046)"
Task: "jordylab-devops agent, Claude copy (T047)"
Task: "jordylab-devops agent, OpenCode copy (T048)"
```

---

## Implementation Strategy

### MVP (the minimum that can go live)

1. Setup (T001–T002) and Foundational (T003–T008).
2. US1 (T009–T017) and US2 (T018–T022). **Both are P1**: an automatic deploy without an automatic rollback is not
   acceptable (spec User Story 2).
3. Dry run (T054) and the broken-image drill (T056) on production.
4. Only then set `AUTO_DEPLOY` to `true` (T055).

### Incremental delivery after the MVP

- US3 (manual redeploy and dry run) — needed before the drill, since the dry run is how the path is first exercised.
- US4 (queue and superseded skip) — needed before two merges can land close together.
- US5 (commit in the app) — can ship any time after Foundational; it is needed for SC-006.
- US6, US7, US8, US9 — documentation and the APK split; they can follow the MVP but must land in the same PR (one spec, one branch), so the runbook never describes a flow the workflow does not have.

### Parallel team strategy

With one developer the order above is the plan. If two people work on it: one takes the `deploy-prod.yml` sequence (US1–US4), the other takes US5–US7 and the scripts' tests. The docs (US9) wait for both.

---

## Coverage: functional requirements → tasks

| FR | Tasks |
|----|-------|
| FR-001 deploy the tested images, no retag | T009, T013, T017 |
| FR-002 no approval; no required reviewer | T053 (owner removes the reviewer); T009 keeps `environment: production` for its secrets |
| FR-003 existing Build gate unchanged | T017 (`needs: build-and-push`) |
| FR-004 failed Build deploys nothing | T017 (`needs`, `if`) |
| FR-005 no overlap; queued runs in order, none dropped | T026 |
| FR-006 older automatic deploy skipped | T027, T010/T012 (`superseded`) |
| FR-007 manual deploys in the same queue | T026 |
| FR-008 wait for rollouts within timeouts | T014 |
| FR-009 smoke check after readiness | T005, T018 |
| FR-010 rollback to previous images and manifests | T019, T020 |
| FR-011 failure reported through GitHub's notice | the failed run itself; checked in T056 |
| FR-012 no previous commit: fail, no rollback | T019 |
| FR-013 failed rollback fails and says so | T022 |
| FR-014 refuse invalid target or missing image before the cluster | T010, T023, T003 |
| FR-015 manual older target allowed, same checks | T025, T003 |
| FR-016 summary: target, previous, live, backup | T016 |
| FR-017 backend reports its commit | T028, T029, T030, T031 |
| FR-018 backup warning, never blocks | T004, T015, T040, T041 |
| FR-019 expand/contract rule in skill and reviewer | T033, T034, T035 |
| FR-020 runbook: schema not rolled back | T036 |
| FR-021 runbook lists interruptible jobs | T042 |
| FR-022 APK only by hand | T037 |
| FR-023 e2e-android on demand and PR path only | T038 |
| FR-024 no release drafts or tag triggers in deploy | T037 |
| FR-025 docs and agents updated | T036, T042–T048 |
| FR-026 merge-to-live times recorded | T043, T055, T058 |
| FR-027 pause switch `AUTO_DEPLOY` | T017, T042 |
| FR-028 dry run | T024, T054 |

## Notes

- Every commit subject starts with `JOR-32:`; every commit names its tasks in a `Refs: 019/T0xx` trailer (T052).
- `[P]` only where the files differ and nothing in the phase is pending; the `deploy-prod.yml` tasks are sequential on purpose.
- Do not start an operational task (T053–T059) from the code tasks: each one changes production or repository settings.
- The rollback gate (T019) and the superseded skip (T027) are the two places where a wrong condition can skip safety. Check both in the dry run and in the drill, not only in review.
