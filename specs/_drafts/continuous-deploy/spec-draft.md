# Feature Draft: Continuous deploy on green main

**Status**: Draft, direction agreed (no RC tags, no manual approval)
**Target path**: `specs/_drafts/continuous-deploy/spec.md`
**Amends**: spec 011 (release flow, BUG-008), `docs/runbook.md` §11 and §20, `release.yml`, `deploy-prod.yml`

> Written without repo access. The workflow facts come from `jordylab-current-state.md` (2026-10-07). Verify the list at the bottom first.

## Problem

Today a merge to `main` only builds and tests. Going live needs a `vX.Y.Z[-rcN]` tag, then a manual approval on the `production` environment. There are 19 release candidates and no `v0.1.0`, so the version numbers carry no meaning. With one developer, the merge is the review, and the approval click adds delay without adding information. `release.yml` also builds and signs the Android APK on every release, which would be wasteful and risky on every merge.

## Requirements

- **FR-001**: When `build` (tests, scans, `app-boot`, required `e2e-web`) succeeds on `main`, the image `sha-<commit>` that was tested is deployed to production automatically. No retagging, no approval.
- **FR-002**: Deploys never overlap. A concurrency group queues them, and when several merges land close together only the newest pending one runs.
- **FR-003**: After the rollout the workflow waits for readiness and runs a smoke check against `https://jordylab.be` (health plus one authenticated-free page load). If either fails it rolls back to the previously deployed sha, fails the run and sends an Ntfy notification.
- **FR-004**: Database migrations must be compatible with the previous release (expand first, contract in a later deploy), because a code rollback does not roll back the schema. This becomes a rule in the `/flyway-migration` skill and a checklist item for the `code-reviewer` agent.
- **FR-005**: The deploy job logs the time of the last successful Postgres backup and warns (does not block) when it is older than the expected daily base backup interval.
- **FR-006**: A manual run of *Deploy to Production* accepts a commit sha or an existing tag. It is the rollback and redeploy path and keeps old rc tags usable.
- **FR-007**: The deployed commit sha is visible from the running app (for example in the backend info endpoint) so "what is live" never needs a guess.
- **FR-008**: APK build, signing and `POST /api/mobile/releases` move to a separate manually triggered workflow. `e2e-android` stops running after releases and becomes manual or nightly. Both go away if `pwa-replaces-capacitor` proceeds.
- **FR-009**: GitHub release drafts and tag-based versioning are no longer part of deploy. Existing tags stay as history.
- **FR-010**: Agent-facing and human docs are updated in the same change: root `AGENTS.md` (it says a push to `main` does not deploy), `docs/runbook.md`, the `jordylab-ops` skill and the `jordylab-devops` agent.

## Success criteria

- **SC-001**: A merged PR is live without any click, and the time from merge to live is measured and recorded in the runbook.
- **SC-002**: A drill with a deliberately broken image (fails readiness) ends with the previous sha running and a notification sent.
- **SC-003**: Rollback by manual dispatch to a chosen sha completes without touching anything else.
- **SC-004**: A release-less week of merges produces no APK builds.

## Out of scope

Staging environment (the project has exactly `local` and `prod`), canary or blue-green on a single node, changing the build and test stages themselves, feature flags.

## Open questions

1. Trigger shape: `workflow_run` on `build` success, or a final job inside `build.yml` with `needs`? A final job is simpler to reason about, `workflow_run` keeps deploy separate.
2. Is the current gate (`e2e-web`, `app-boot`, scans) enough confidence without a human? If not, add a journey rather than bring the approval back.
3. Restart impact: until `ai-ingestion-pipeline` phase A lands, a deploy during a running briefing or enrichment batch interrupts it. Acceptable in the meantime, or deploy around the scheduled windows?
4. Session loss: with in-memory sessions every deploy logs people out. This matters once the auth draft replaces Keycloak (see `auth-without-keycloak`).
5. Required reviewers on the `production` GitHub environment must be removed by the repo owner. This is a settings change I cannot make.

## Facts to verify in the repo

- Exact job names and triggers in `build.yml`, `release.yml`, `deploy-prod.yml`, `e2e-android.yml`.
- How `deploy-prod.yml` applies manifests and waits for rollout today (it can probably be reused as is).
- Whether a version or info endpoint already exposes the commit sha.
