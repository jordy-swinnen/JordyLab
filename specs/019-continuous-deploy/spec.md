# Feature Specification: Continuous Deploy on Green Main

**Feature Branch**: `019-continuous-deploy`

**Created**: 2026-11-03

**Status**: Draft

**Module**: CI/CD and deployment (`.github/workflows/`), the backend info endpoint, the database migration rule (skill and
review checklist), the APK release path, and the runbook and agent docs. Linear: JOR-32.

**Input**: User description: "Continuous deploy on green main. Today a merge to `main` only builds and tests. Going live
needs a `vX.Y.Z[-rcN]` tag, then a manual approval on the `production` environment. There are 19 release candidates and no
`v0.1.0`, so the version numbers carry no meaning. With one developer, the merge is the review, and the approval click
adds delay without adding information."

**Background**: `specs/_drafts/continuous-deploy/spec-draft.md` (direction agreed: no release-candidate tags, no manual
approval). This spec supersedes the draft's scope; the draft folder stays as history. The release flow it replaces is
the one specified in `specs/011-prod-e2e-hardening/` (its tag-based release, FR-018 delegated approval) and documented in
`docs/runbook.md` §10, §11 and §20.

## Overview

A merge to `main` that passes every check goes live on production by itself. The images that passed the checks are the
images that run; nothing is rebuilt or retagged, and nobody clicks approve. If the new version does not come up healthy,
production returns to the commit that was live before, and the owner is told.

Release tags stop being part of deployment. They remain in git as history and stay usable as a manual target, so an old
release can always be redeployed or rolled back to.

## Current state (verified in the repository, 2026-11-03)

- `build.yml` runs on every push to `main` and on every pull request. Its checks are `secret-scan`, `licence-check`,
  `test-backend`, `test-frontend`, `app-boot` and `e2e-web` (the required check). `build-and-push` runs only on `main`
  and publishes `ghcr.io/jordy-swinnen/jordylab-{backend,frontend,keycloak}:sha-<full sha>`. `build.yml` has **no
  concurrency group**: two merges close together can finish their builds in either order.
- `release.yml` runs on every `v*` tag. Its jobs are `verify` (tag on `main`, green Build), `retag` (adds the release tag
  to the tested images, no rebuild), `release` (draft GitHub Release), `deploy` (calls `deploy-prod.yml`), `publish`
  (makes the Release public), and `apk` (builds, signs and uploads the Android APK on every release).
- `deploy-prod.yml` is both `workflow_call` and `workflow_dispatch` with a single input `version`. It validates the input
  against `vX.Y.Z[-rcN]` only, checks out that tag, applies the prod overlay with the tag as the image tag, and waits for
  `rollout status` on backend, frontend and keycloak. It runs in the `production` environment, which has a required
  reviewer. It is **not reusable as is** for a commit sha: the input check and the checkout both assume a tag.
- `e2e-android.yml` runs after every completed `Release` (`workflow_run`), besides on demand and on pull requests that
  touch Android files.
- Git has 33 local release-candidate tags, all `v0.0.1-rc1` to `v0.0.1-rc33`, and no `v0.1.0`. (The issue text says 19.)
- The backend exposes `health` and `info` through Spring Boot, but `info` carries no build data, so the running app does
  not report its commit. The live image tag names the release (`vX.Y.Z`) and not the commit, so it does not answer
  "which commit is live" on its own.
- The public gateway routes `/`, `/api`, `/auth/...`, `/ntfy` and `/.well-known/assetlinks.json`. It does **not** route
  `/actuator/health`, so a smoke check cannot use it from outside the cluster. Unauthenticated `/api/**` answers 401 today.
- Postgres base backups are scheduled with CloudNativePG `ScheduledBackup`: daily at 03:00 UTC and weekly on Sunday at
  04:00 UTC, to OVH Object Storage, 30-day retention. The CI `ci-deploy` Role can read Deployments and CloudNativePG
  backups in the `jordylab` namespace.
- Background work runs on schedules that a restart interrupts: feed ingestion three times a day (cron 06, 12, 18), the
  auto-fill sweep daily at 04:30 Europe/Brussels, the monthly briefing at 06:30 on the 1st, and stock prices every 30
  minutes.
- Ntfy runs only inside the cluster (spec 007 FR-013 forbids the public ntfy.sh service), so a GitHub runner cannot post
  to it directly.

### Corrections to the draft

- The draft says the root `AGENTS.md` states that a push to `main` does not deploy. It does not. That statement is in
  `docs/runbook.md` (§10 and §20) and in the `jordylab-ops` skill (CI/CD row). The root `AGENTS.md` needs the new flow
  added to its Infrastructure section, not a correction.
- The draft says `deploy-prod.yml` "can probably be reused as is". It cannot, for the reasons above; it needs a changed
  input check, checkout and image tag.

## Clarifications

### Session 2026-11-03

- Q: Where should a failed deploy or rollback be reported? → A: GitHub's own failure notification only. No Ntfy push and
  no public ntfy.sh topic (FR-011).
- Q: Should a deploy wait for or interrupt scheduled background work? → A: Deploy anyway. The runbook lists the jobs a
  restart can interrupt and how each catches up (FR-021).
- Taken from the draft, not yet confirmed by Jordy: the existing Build checks are enough confidence without a human
  approval, and the `production` environment keeps its secrets while its required reviewer is removed. See Assumptions.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A merged change goes live without a click (Priority: P1)

As Jordy, when a change is merged to `main` and its checks pass, the images that passed those checks run in production
without any approval, so the merge is the only step and the review.

**Why this priority**: this is the whole purpose of the feature. Every other story protects it.

**Independent Test**: merge a small visible change (for example a text change in the app) to `main`. Without touching
anything else, the change is live on `https://jordylab.be`, and the deploy run names the same commit as the merge.

**Acceptance Scenarios**:

1. **Given** a commit on `main` whose Build succeeded, **When** the Build finishes, **Then** production runs the
   `sha-<commit>` images of backend, frontend and keycloak, and no approval is requested.
2. **Given** the same commit, **When** the deploy finishes, **Then** the deploy log names the deployed commit, the commit
   that was live before, and the time of the last successful database backup.
3. **Given** a commit whose Build failed or was cancelled, **When** the run ends, **Then** no deploy has started and the
   live version is unchanged.

---

### User Story 2 - A broken release returns to the last good commit (Priority: P1)

As Jordy, when a new version does not come up healthy, production returns to the commit that was live before, the run
fails, and I am told, so a bad merge costs minutes of downtime at most and nothing needs to be done by hand.

**Why this priority**: automatic deploys are only safe with an automatic way back. Without it the approval click would be
the only protection.

**Independent Test**: the drill in the Success Criteria (a deliberately broken image) ends with the previous commit
serving and a failed run that was reported.

**Acceptance Scenarios**:

1. **Given** a rollout whose new pods never become ready, **When** the rollout times out, **Then** the previous commit's
   images **and** manifests are applied again, the rollout completes, the run fails, and the failure is reported.
2. **Given** a rollout whose pods are ready but the smoke check fails, **When** the check ends, **Then** the same rollback
   happens.
3. **Given** a rollback, **When** the run ends, **Then** the log says which check failed and which commit is live now.

---

### User Story 3 - Redeploy or roll back to any commit or release tag (Priority: P2)

As Jordy, I can start *Deploy to Production* by hand with a commit sha or an existing release tag, so I can roll back or
redeploy without the pipeline having to guess.

**Why this priority**: it is the manual safety net for cases the automatic check cannot see (a bad change that is healthy
but wrong).

**Independent Test**: start the workflow with the tag of an earlier release. Only that release's images and manifests
change, no approval is asked, and the run log names the restored release.

**Acceptance Scenarios**:

1. **Given** a full commit sha with a successful Build, **When** I start the workflow with it, **Then** production runs
   that commit's images and manifests.
2. **Given** an existing `vX.Y.Z` or `vX.Y.Z-rcN` tag, **When** I start the workflow with it, **Then** production runs
   that release's images and manifests.
3. **Given** any other input, **When** I start the workflow, **Then** it is rejected before the cluster is touched.

---

### User Story 4 - Deploys never overlap and never go backwards (Priority: P2)

As Jordy, when two merges land close together, the deploys run one at a time, only the newest waiting one runs, and a
build of an older commit that finishes late cannot replace a newer commit that is already live.

**Why this priority**: without it, a slow older build can silently undo a newer fix.

**Independent Test**: push two commits to `main` within five minutes. At the end, the live commit is the newer one, and
no older commit was deployed after it.

**Acceptance Scenarios**:

1. **Given** a deploy is running, **When** two more deploys are queued, **Then** only the newer of the two runs next.
2. **Given** the newest commit is live, **When** the Build of an older commit finishes, **Then** its deploy is skipped
   and the log says it was superseded.
3. **Given** a manual deploy of an older commit (a rollback), **When** it is requested, **Then** it is allowed, because it
   is a deliberate choice, and it follows the same single-queue rule.

---

### User Story 5 - Always know what is live (Priority: P2)

As Jordy, I can tell which commit is live from the running app itself, not from memory or a guess.

**Why this priority**: the release-candidate history shows what happens without it.

**Independent Test**: after a deploy, the commit in the deploy log, the live image tag in the cluster and the commit the
backend reports on its info endpoint (checked inside the cluster) are the same.

**Acceptance Scenarios**:

1. **Given** a deployed commit, **When** the backend's info endpoint is read inside the cluster, **Then** it reports that
   commit's full sha.
2. **Given** a tag deploy, **When** the info endpoint is read, **Then** it still reports the commit the tag points to.

---

### User Story 6 - Migrations never block a rollback (Priority: P2)

As Jordy, a database change never makes the previous release unable to run, so the automatic rollback is always safe to
use.

**Why this priority**: a code rollback does not roll back the schema. An incompatible migration turns a rollback into a
second outage.

**Independent Test**: the `/flyway-migration` skill and the `code-reviewer` checklist refuse a migration that drops or
renames a column that the previous release still reads, and direct the change to a later release.

**Acceptance Scenarios**:

1. **Given** a migration that removes a column the previous release still uses, **When** it is reviewed, **Then** the
   review asks for an expand step now and a contract step in a later release.
2. **Given** a migration that only adds a nullable column or table, **When** it is reviewed, **Then** it is accepted in
   one release.

---

### User Story 7 - Release builds leave the merge path (Priority: P2)

As Jordy, the Android APK is built, signed and published only when I ask for it, and the end-to-end Android suite runs on
demand, so merges do not build an APK.

**Why this priority**: an APK on every merge is slow, uses the signing key on every change, and is not needed for a web
deploy.

**Independent Test**: after a week of merges with no release started by hand, the APK workflow has zero runs.

**Acceptance Scenarios**:

1. **Given** a merge to `main`, **When** its deploy runs, **Then** no APK build or signing step runs.
2. **Given** the APK workflow is started by hand with a release tag, **When** it finishes, **Then** the APK is published
   as it is today.

---

### User Story 8 - Backup freshness shows at every deploy (Priority: P3)

As Jordy, each deploy shows when the last successful database backup finished, and warns when it is too old, so a broken
backup schedule is noticed on the next merge.

**Why this priority**: a warning costs nothing and makes a silent failure visible. It does not block the deploy.

**Independent Test**: with a backup older than 26 hours, the deploy log shows a warning and the deploy still completes.

**Acceptance Scenarios**:

1. **Given** a successful backup within 26 hours, **When** a deploy runs, **Then** the log shows its time and no warning.
2. **Given** no successful backup within 26 hours, **When** a deploy runs, **Then** the log shows a warning and the deploy
   continues.

---

### User Story 9 - The docs and agents describe the new flow (Priority: P3)

As Jordy and as the agents, the runbook, the root `AGENTS.md`, the `jordylab-ops` skill and the `jordylab-devops` agent
say what happens on a merge, how to roll back, and that no approval is needed. No file still tells the reader to tag a
release to go live.

**Why this priority**: agents act on these files; out-of-date text leads them into the old flow.

**Independent Test**: a search of those files for "approve the production deployment" or "tag v" as the way to go live
finds nothing.

**Acceptance Scenarios**:

1. **Given** the runbook §10 and §20, **When** they are read, **Then** they describe the automatic deploy, the manual
   redeploy, and the rollback, and no longer describe the tag-based release as the way to go live.
2. **Given** the measured merge-to-live time from the first five automatic deploys, **When** the runbook is read, **Then**
   it records that time.

---

### Edge Cases

- **Two merges close together**: the older waiting deploy is replaced by the newer one (FR-005).
- **An older Build finishes after a newer deploy is live**: its deploy is skipped and logged as superseded (FR-006).
- **A Build for a commit whose images were never pushed**: no deploy starts, because `build-and-push` must succeed (FR-004).
- **A manual run with a sha or tag that has no images**: rejected before the cluster is touched (FR-014).
- **A new release that contains a migration the previous release cannot run with**: the rollback still runs, but the schema
  stays newer. FR-019 exists to stop this; FR-020 requires the runbook to say what to check when it happens anyway.
- **The smoke check fails for a reason outside the release** (DNS, certificate, gateway): the rollback still runs. It is
  harmless, but may not help. The log says which check failed so the cause is visible.
- **No previously live commit** (the first deploy after a cluster rebuild): nothing is rolled back; the run fails and is
  reported (FR-012).
- **The rollback itself fails**: the run fails and says so; the emergency path in the runbook (§11) applies (FR-013).
- **A docs-only merge**: it still deploys and restarts the three pods. Accepted (see Assumptions).
- **A deploy while a manual deploy is running**: it queues like any other (FR-005, FR-007).
- **Restart during a scheduled background job**: the job is interrupted and catches up as FR-021 describes.
- **Restart and user sessions**: each deploy restarts the backend. The draft expects this to sign users out; the search of
  the backend found no server-side session store, so the real effect is checked on the first automatic deploy (see
  Assumptions).

## Requirements *(mandatory)*

### Functional Requirements

**Automatic deploy**

- **FR-001**: A commit on `main` whose Build succeeds MUST be deployed to production automatically, using the images that
  Build published for that exact commit. Nothing is rebuilt or retagged.
- **FR-002**: The deploy MUST NOT wait for a manual approval. The `production` environment MUST NOT have a required
  reviewer. The environment itself stays, for its secrets.
- **FR-003**: The gate is the existing Build checks: secret scan, licence check, backend and frontend tests, `app-boot`,
  and the required `e2e-web`. No check is removed to make deploys faster.
- **FR-004**: A failed, cancelled or incomplete Build MUST NOT start a deploy.

**Ordering and concurrency**

- **FR-005**: Production deploys MUST NOT overlap. At most one runs at a time, and at most one waits behind it. A newer
  waiting deploy replaces an older waiting one.
- **FR-006**: An automatic deploy MUST NOT replace a live commit that is newer than its own commit on `main`. Such a deploy
  is skipped, and the log says it was superseded.
- **FR-007**: Manual deploys MUST follow the same single-queue rule as automatic ones.

**Readiness, smoke check and rollback**

- **FR-008**: After the rollout, the workflow MUST wait until backend, frontend and keycloak are ready, within the timeouts
  the current workflow uses.
- **FR-009**: After readiness, the workflow MUST run a smoke check against `https://jordylab.be`: the home page answers
  200, and an unauthenticated call to an `/api` endpoint answers 401. Readiness of the backend is checked inside the
  cluster (the public gateway does not route `/actuator/health`).
- **FR-010**: If readiness or the smoke check fails, the workflow MUST apply the previously live commit again, with its
  images **and** its manifests, MUST wait for that rollout, and MUST fail the run.
- **FR-011**: A failed run and a rollback MUST be reported to the owner through GitHub's own notification of a failed run.
  No other channel is added. The draft's Ntfy push is dropped: Ntfy stays inside the cluster (spec 007 FR-013), and a
  GitHub runner cannot post to it when the cluster is down, which is when a report matters most.
- **FR-012**: If no previously live commit is known, the run MUST fail and report it, and MUST NOT roll back.
- **FR-013**: If the rollback fails, the run MUST fail and say so. The emergency procedure in `docs/runbook.md` §11 applies.

**Manual deploy and rollback**

- **FR-014**: *Deploy to Production* MUST accept a full 40-character commit sha that has a published image set, or an
  existing tag `vX.Y.Z` or `vX.Y.Z-rcN`. Anything else MUST be rejected before the cluster is touched.
- **FR-015**: A manual deploy of an older commit or tag MUST deploy exactly that commit's images and manifests. It changes
  nothing else.

**Visibility**

- **FR-016**: The deploy log MUST state the deployed commit, the commit that was live before, the commit live at the end
  (the restored one after a rollback), and the time of the last successful database backup.
- **FR-017**: The backend MUST report the full sha of the commit it was built from on its info endpoint, so the running app
  and the deploy run agree.
- **FR-018**: If the last successful database base backup is older than 26 hours, the deploy log MUST show a warning. The
  deploy MUST continue.

**Database compatibility**

- **FR-019**: A migration MUST be compatible with the release that runs before it. Removing or renaming a column, table or
  index that the previous release still uses, or making a column required that the previous release still writes without
  it, MUST be split: an expand step in one release and a contract step in a later release. The rule MUST be added to the
  `/flyway-migration` skill and to the `code-reviewer` checklist.
- **FR-020**: The runbook MUST say that a code rollback does not roll back the schema, and what to check after one.
- **FR-021**: A deploy MUST NOT wait for a scheduled background job to finish or for its window to pass. A restart may
  interrupt feed ingestion, the auto-fill sweep, the monthly briefing or a stock-price refresh. The runbook MUST list which
  of these a deploy can interrupt and how each one catches up (feed and auto-fill on their next run; an interrupted
  monthly briefing is rerun by hand).

**APK and end-to-end Android**

- **FR-022**: The APK build, signing and upload MUST run only from a separate workflow that is started by hand. No push,
  merge or deploy starts it.
- **FR-023**: `e2e-android` MUST run on demand and on the pull-request path trigger it already has. It MUST NOT run after a
  release.
- **FR-024**: Deploys MUST NOT create or depend on GitHub Release drafts or on tag triggers. Existing tags stay as history
  and stay usable as manual targets (FR-014).

**Documentation**

- **FR-025**: The following MUST describe the new flow in the same change: `docs/runbook.md` (§9 for the environment, §10,
  §11 and §20), the root `AGENTS.md` (Infrastructure section), the `jordylab-ops` skill, and the `jordylab-devops` agent.
- **FR-026**: The merge-to-live time of the first five automatic deploys MUST be recorded in `docs/runbook.md`.

### Key Entities *(include if feature involves data)*

- **Production deploy**: one run of the deploy workflow. Attributes: trigger (automatic or manual), target commit, previously
  live commit, outcome (live, rolled back, superseded, failed), start and end time, backup time seen at the start.
- **Release tag**: an existing `vX.Y.Z` or `vX.Y.Z-rcN` tag. Remains history; a valid manual deploy target (FR-014).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A merged change is live without any click. The time from merge to live is measured over the first five
  automatic deploys and recorded in the runbook. The target is under 30 minutes.
- **SC-002**: In a drill with a deliberately broken image, the previous commit is serving again within 10 minutes of the
  failed readiness check, the run is red, and the failure is reported (FR-011).
- **SC-003**: A manual rollback to a chosen commit or tag changes only that commit's images and manifests, which the deploy
  log shows.
- **SC-004**: Two merges within five minutes leave the newer commit live, and no older commit is deployed after it.
- **SC-005**: A week without manually started releases produces zero APK builds.
- **SC-006**: After every deploy, the commit in the deploy log, the live image tag and the backend's info endpoint (read
  inside the cluster) name the same commit.
- **SC-007**: No merge to `main` asks for a manual approval.

## Assumptions

- Production is the only environment. Staging, canary and blue-green are out of scope; the project has `local` and `prod`.
- **Owner action before the first automatic deploy:** Jordy removes the required reviewer from the `production`
  environment. Until then, automatic runs wait for an approval that nothing asks for. The environment stays for its
  secrets. (The draft's open question 5.)
- The existing Build checks are enough confidence without a human. If an escaped bug shows a gap, add a journey to
  `e2e-web` rather than bring the approval back. (The draft's open question 2.)
- Every green merge deploys, including docs-only merges. Each deploy restarts the three pods, because every image tag is
  new. That is the accepted cost of the simplest rule; skipping no-op merges is a later optimisation.
- The trigger shape (a final job in `build.yml` that calls the deploy, or a separate workflow that waits for Build) is
  chosen in the plan. This spec only requires FR-001, FR-004 and FR-005.
- Each deploy restarts the backend. The draft expects users to be signed out by it (its open question 4). The search
  found no server-side session store configured, so this is checked, not assumed: the first automatic deploy records
  whether users stay signed in. The answer decides whether `auth-without-keycloak` must come first.
- Ntfy stays in-cluster (spec 007 FR-013) and this feature does not use it (FR-011).
- The smoke check uses the public home page and an unauthenticated `/api` call. It does not use `/actuator/health`, which
  the public gateway does not route.
- Release tags stay as history. The APK pipeline may disappear entirely if `pwa-replaces-capacitor` proceeds; FR-022 and
  FR-023 still hold until then.
- Build pushes images for every commit on `main`, and no workflow in the repository deletes images, so a commit whose Build
  pushed images stays a rollback target. Whether the registry itself prunes old images is not configured in the repo and
  is to be checked before the first rollback drill.

## Out of Scope

- A staging environment (the project has exactly `local` and `prod`).
- Canary or blue-green rollouts on the single node.
- Changes to the build and test stages themselves.
- Feature flags.
- Deciding whether the APK pipeline survives (`pwa-replaces-capacitor` decides that).

## Dependencies

- `specs/011-prod-e2e-hardening/`: the release flow this spec replaces (tag-based release, approval, delegated approval
  FR-018). Its history stays; the runbook changes.
- `specs/008-ovh-k8s-deployment/`: the cluster, the CI RBAC and the manifests that the deploy applies.
- `ai-ingestion-pipeline` (draft): not a precondition. Deploys go ahead and may interrupt background work (FR-021). That
  draft is meant to make such interruptions safe (transaction boundaries and durability); this spec does not depend on it.
- `auth-without-keycloak` (draft): affects the session note in Assumptions.
- `pwa-replaces-capacitor` (draft): may remove the APK pipeline (FR-022, FR-023).
