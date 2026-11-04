# Implementation Plan: Continuous Deploy on Green Main

**Branch**: `019-continuous-deploy` (git branch `jordyswinnen/jor-32-continuous-deploy-on-green-main`) | **Date**: 2026-11-03 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/019-continuous-deploy/spec.md`

**Note**: This plan was produced by `/speckit-plan`. The clarifications it needed were answered in the spec
(FR-011: GitHub failure notice only; FR-021: deploy anyway). The decisions are in [research.md](research.md).

## Summary

A green `Build` on `main` deploys the images it just published, for that exact commit, with no approval. A failed
rollout or smoke check applies the previously live commit (its images and manifests) and fails the run. Manual
*Deploy to Production* takes a commit sha or a release tag, and a dry-run mode exercises the real cluster without changing
it. Release tags leave the deploy path; the Android APK moves to its own manual workflow.

Technical approach, in one line each:
- **Trigger**: a final `deploy` job in `build.yml` calls the reusable `deploy-prod.yml` (D1).
- **Queue**: `queue: max` so a queued manual rollback is never dropped; superseded commits are skipped by an ancestry check (D2, D5).
- **Live state**: read from the cluster's three Deployment images; the live commit is mapped from the backend tag (D3).
- **Rollback**: re-apply the previous commit from its own worktree (D4).
- **Visibility**: the backend reports its commit at `/actuator/info` via a build argument (D8).
- **Rollout switch**: the repository variable `AUTO_DEPLOY` gates the automatic job; the first real run is a dry run (D11, D12).

## Technical Context

**Language/Version**: GitHub Actions YAML; Bash (`ubuntu-latest`); Java 25 for the one backend property and its test.

**Primary Dependencies**: GitHub Actions (`actions/checkout@v4`, `docker/build-push-action@v7`, `docker/login-action@v4`,
`docker/setup-buildx-action@v3`, `tailscale/github-action@v4`); pinned `kustomize` v5.8.1, `kubectl` v1.36.4, `sops`
v3.13.3, `age` v1.3.2 (all as today); `jq` (preinstalled on the runner); Spring Boot 4.1.1 actuator.

**Storage**: none added. Reads: GHCR manifests, the cluster, CloudNativePG `Backup` objects, git history and tags.

**Testing**: table-driven Bash fixtures (`deploy/scripts/tests/`, style of `.githooks/tests/commit-msg-cases.sh`); a
Spring Boot test with MockMvc, AssertJ and `@Language("JSON")` (constitution IV); `actionlint` on the workflows; a dry
run and a broken-image drill on production (quickstart Q2, Q5).

**Target Platform**: GitHub-hosted runner → Tailscale → single-node k3s on OVH VPS-2; public `https://jordylab.be`.

**Project Type**: CI/CD configuration, two shell scripts, one backend property and build argument, and documentation.

**Performance Goals**: merge to live under 30 minutes (SC-001; the Build alone measured 9–12 minutes on `gh run list`);
rollback to the previous commit within 15 minutes of a failed deploy's start (SC-002).

**Constraints**:
- `strategy: Recreate` on the backend: every deploy has an outage (D13).
- The `production` environment must lose its required reviewer before automatic deploys (owner, FR-002).
- The CI credential is namespace-scoped (`ci-deploy` Role); the new checks only read `Backup` objects and Deployments.
- Nothing decrypted or secret-valued may be printed (root AGENTS.md, Secrets).
- `/actuator/health` is not routed by the public gateway; the smoke check uses `/`, `/api` and Keycloak discovery.
- Ntfy is in-cluster only (spec 007 FR-013); not used here (FR-011).

**Scale/Scope**: one developer; a handful of merges a day; three images; one node; one production environment.

## Constitution Check

*Checked against `.specify/memory/constitution.md` v1.1.2 after Phase 1 design (research, data model, contracts, quickstart).*

| Principle | Applies to | Result |
|-----------|-----------|--------|
| I. Clean Code | Scripts, workflow steps | Pass. Full descriptive names in the scripts (`live_commit`, not `lc`); early returns; one job per concern; each function short. |
| II. Fail Fast, No Silent Failures | Decision, backup check, rollback | Pass. Refused targets fail before the cluster is touched; a superseded deploy is a logged notice, not silence; a failed rollback fails the run and says so (FR-013); the backup check warns, never hides. |
| III. Immutable, Builder-First | Java | Pass. No new Java class; the endpoint is configuration only. |
| IV. Testing Discipline | Spring test, scripts | Pass. AssertJ, MockMvc with `@Language("JSON")`; no `any()`; scripts are isolated and deterministic (temporary git repositories, fixture JSON). |
| V. Language & Tooling Currency | Java | Pass. Java 25 and Spring Boot 4.1.1 unchanged. |
| Architecture (Additional constraints) | Backend | Pass. The change is a property and a build argument; no module boundary is crossed. |
| Governance | Docs | Pass. Operational detail stays in `docs/runbook.md` and the skills, as the constitution directs. |

**Gate result**: pass, with one deviation recorded under Complexity Tracking (the `AUTO_DEPLOY` switch).

## Project Structure

### Documentation (this feature)

```text
specs/019-continuous-deploy/
├── spec.md                  # /speckit-specify (+ planning edits, see "Spec changes from planning")
├── checklists/requirements.md
├── plan.md                  # this file
├── research.md              # Phase 0: D1–D16
├── data-model.md            # Phase 1
├── quickstart.md            # Phase 1: Q0–Q10
├── contracts/
│   ├── deploy-workflow.md
│   ├── info-endpoint.md
│   └── apk-release-workflow.md
└── tasks.md                 # /speckit-tasks (not created here)
```

### Source Code (repository root): what changes

```text
.github/workflows/
├── build.yml                # + deploy job (AUTO_DEPLOY gated), + deploy-scripts job,
│                            #   + build-args for backend, + top-level permissions
├── deploy-prod.yml          # rewritten: target/automatic/dry_run inputs, queue: max,
│                            #   live state, decision, backup check, apply, smoke, rollback
├── release.yml              # removed; replaced by apk-release.yml
├── apk-release.yml          # NEW (manual): verify + apk, tag input, git-log release notes
└── e2e-android.yml          # workflow_run trigger removed; on demand and PR path only

deploy/
├── scripts/
│   ├── deploy-decision.sh   # NEW: grammar, target commit, live commit, refused/superseded/proceed
│   ├── backup-freshness.sh  # NEW: newest completed CNPG Backup, age, warning
│   └── tests/
│       ├── deploy-decision-cases.sh   # NEW
│       └── backup-freshness-cases.sh  # NEW
└── containers/backend/Containerfile   # ARG/ENV JORDYLAB_GIT_COMMIT in the runtime stage

jordylab-be/
├── src/main/resources/application.yaml        # info.commit + management.info.env.enabled
└── src/test/java/dev/jordy/jordylab/shared/…  # NEW: info endpoint test (name at tasks time)

docs/
├── runbook.md               # §9 (reviewer removed), §10, §11, §20; merge-to-live record; migration note
└── learn/10-cicd.md         # the approval walkthrough becomes the automatic flow

AGENTS.md                    # Infrastructure: one paragraph on the deploy flow

.claude/skills/jordylab-ops/SKILL.md            # approval rules, CI/CD row, deploy commands
.claude/agents/jordylab-devops.md               # "the one delegation exception" wording
.opencode/agents/jordylab-devops.md             # same body as the Claude copy
.claude/skills/flyway-migration/SKILL.md        # expand/contract rule (FR-019)
.claude/agents/code-reviewer.md                 # migration checklist item (FR-019)
.opencode/agents/code-reviewer.md               # same body as the Claude copy

jordylab-fe/apps/jordylab-mobile/android/app/build.gradle   # comments: release.yml → apk-release.yml
```

**Structure Decision**: the deploy logic that can be tested without a cluster (decision, backup age) lives in two Bash
scripts beside the deploy artefacts, in `deploy/scripts/`, with fixture suites in `deploy/scripts/tests/`. The workflow
steps stay in YAML and call the scripts. The backend change is one property and one build argument; no new module,
package or class.

## Delivery slices (input to `/speckit-tasks`)

1. **Commit visible in the app**: Containerfile, `application.yaml`, the info test. Independently testable with Gradle.
2. **Pure decisions**: `deploy-decision.sh` and `backup-freshness.sh` with their fixture suites, and the `deploy-scripts` job.
3. **Deploy workflow**: rewrite `deploy-prod.yml` (inputs, queue, live state, decision, backup, apply, smoke, rollback,
   dry run, summary) and add the `deploy` job to `build.yml` behind `AUTO_DEPLOY`.
4. **APK split**: `apk-release.yml` from `release.yml`; `e2e-android.yml` trigger; `build.gradle` comments.
5. **Docs and agents**: runbook, `AGENTS.md`, `docs/learn/10-cicd.md`, ops skill, devops agents, migration rule in the
   skill and reviewer, and the Q9 grep passing.
6. **Operations** (not code, after merge): Q0 owner step, Q1–Q3 (dry run, first automatic deploy), Q5 drill with Jordy's
   go-ahead, Q6, Q7, Q10.

Slices 1–5 are one PR (one spec, one branch). Slice 6 runs after the merge and is recorded in the runbook.

## Spec changes from planning

These follow from the research. They are applied to `spec.md` in the same change.

| # | Where in spec | Change | Why (research) |
|---|---------------|--------|----------------|
| S1 | FR-005, User Story 4 AS1, edge case "two merges" | Queued deploys run in order; none is dropped; a superseded one is skipped (FR-006) | D2: `queue: single` would let a newer deploy cancel a queued rollback |
| S2 | SC-002 | "within 15 minutes of the start of the failed deploy" | D13: Recreate outage plus rollout timeout plus rollback |
| S3 | Assumptions | Each deploy restarts the three pods, with an outage equal to the new pod's start-up; Keycloak restarts on every deploy | D13, R1, R10 |
| S4 | Edge cases | A rollback is temporary (the next green merge redeploys the newest commit); a rollback re-applies the previous secrets file | D4, R3, R5 |
| S5 | FR-025 | Also `docs/learn/10-cicd.md`, the flyway skill and the code-reviewer (FR-019) | The learn doc teaches the approval flow |
| S6 | New FR-027 | Automatic deploys can be paused by the repository variable `AUTO_DEPLOY`; manual runs unaffected | D11 |
| S7 | New FR-028 | Manual `dry_run` validates the target, reads live state and backup, runs the decision and a server-side apply dry run and the smoke checks, and changes nothing | D12 |
| S8 | Out of Scope | "Feature flags" becomes "product feature flags"; the pause switch in FR-027 is an operational kill switch | Complexity Tracking |

## Risks and mitigations

| # | Risk | Likelihood / impact | Mitigation |
|---|------|---------------------|------------|
| R1 | Every deploy has an outage (Recreate) | Certain / low–medium | Accepted (D13); measured on the first five deploys (SC-001); RollingUpdate revisited after FR-019 is practised |
| R2 | Users are signed out by each deploy | Unknown / medium | Checked on the first automatic deploy (quickstart Q3); decides whether `auth-without-keycloak` must come first |
| R3 | A rollback reverts a secret rotated after the previous commit | Low / high | Warning from the secrets diff (D4); runbook: roll forward for rotations |
| R4 | A migration breaks the previous release, so the rollback cannot run | Low / high | FR-019 in the skill and reviewer; expand/contract; the runbook says what to check (FR-020) |
| R5 | A rollback is undone by the next green merge | High / medium | Stated in the spec and runbook; the revert commit is the permanent fix (quickstart Q5 step 5) |
| R6 | A manual rollback waits behind a running deploy | Medium / low | FIFO with `queue: max` (D2); a deploy takes minutes; the run shows *Waiting* |
| R7 | A false smoke failure (DNS, certificate, gateway) triggers a rollback | Low / medium | Six retries per check; the log names the failed check; the rollback is harmless but may not help |
| R8 | The required reviewer is still on `production` | Certain until done / high | Q0 step 1; the first automatic run would otherwise wait for an approval |
| R9 | Docs-only merges restart the three pods | Certain / low | Accepted (spec assumption); a later step could skip unchanged components |
| R10 | Keycloak restarts on every merge | Certain / medium | Accepted for v1 and measured; a later step can skip Keycloak when its inputs are unchanged (`git diff` against the live commit) |
| R11 | `AUTO_DEPLOY` left `false` by mistake | Low / medium | The summary of every manual run and the Q0 checklist show it; the Build run shows the `deploy` job as skipped |

## Open items (need Jordy)

1. **Confirm `queue: max`** and therefore the S1 change to FR-005 (recommended; the alternative silently drops a queued rollback).
2. **Confirm the `AUTO_DEPLOY` switch** as a kill switch, not a product feature flag (S6, S8, Complexity Tracking).
3. **Remove the required reviewer** from the `production` environment before the first automatic deploy (Q0; owner action).
4. **Accept the Recreate outage** (R1) for now, with the measured numbers recorded.
5. **Go-ahead and a quiet window for the drill** (Q5). It takes the backend down for up to about 11 minutes.

## Notes

- `main` has moved since this branch was cut: PR #185 (spec 017, `017-guest-feedback`) merged on 2026-11-03. Rebase onto
  `main` before implementation so the workflows match the current tree.
- The spec number is 019: 018 was taken on `main` by `018-pwa-replaces-capacitor` before this branch was rebased.

## Complexity Tracking

> Fill ONLY if Constitution Check has violations that must be justified

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Spec out-of-scope says "feature flags"; plan adds the `AUTO_DEPLOY` repository variable (FR-027) | The first automatic deploy is otherwise the first live run of a new workflow; the variable is also the only way to pause automatic deploys during an incident without a code change | Rolling out with no pause switch leaves no fast stop; reverting the workflow file is slower and is itself a deploy of the old workflow |
