# Data Model: Continuous Deploy on Green Main

**Feature**: 019 | **Spec**: [spec.md](spec.md) | **Research**: [research.md](research.md)

Nothing here is stored in the database. The entities are the values a deploy run reads from git, GHCR, the cluster and
the backup objects, and the record it writes to its own run summary.

## DeployTarget

The input of a deploy run, after validation.

| Field | Type | Rule |
|-------|------|------|
| `input` | string | The raw `target` input |
| `kind` | `commit` \| `release` | `commit` for a 40-hex sha; `release` for `vX.Y.Z[-rcN]` |
| `commit` | 40-hex sha | The input for `commit`; `git rev-parse "<tag>^{commit}"` for `release` |
| `imageTag` | string | `sha-<commit>` for `commit`; the release tag for `release` |

Validation (FR-014): the input matches one of the two grammars, and all three images exist for `imageTag`. Anything else
is `refused` before the cluster is read.

## LiveState

What the cluster runs, read before any change.

| Field | Type | Rule |
|-------|------|------|
| `backendImage`, `frontendImage`, `keycloakImage` | string | `jsonpath` of each Deployment's container image |
| `liveTag` | string | The tag part of `backendImage` |
| `liveCommit` | sha \| `unknown` | From `liveTag` by D3; `unknown` when it cannot be mapped |

`liveCommit` is the **previously live commit** for the run. The three image strings are the rollback target for FR-010.

## DeployDecision

| Field | Type | Rule |
|-------|------|------|
| `decision` | `refused` \| `superseded` \| `proceed` | D5 |
| `targetCommit` | sha | From DeployTarget |
| `liveCommit` | sha \| `unknown` | From LiveState |
| `reason` | string | Printed in the run summary (for example "target is older than the live commit") |

## BackupFreshness

| Field | Type | Rule |
|-------|------|------|
| `newestCompletedAt` | timestamp \| none | Max `status.stoppedAt` over Backup objects with `status.phase == "completed"` |
| `ageHours` | number \| none | Now minus `newestCompletedAt` |
| `warning` | bool | True when there is no completed backup or `ageHours > 26` (FR-018); never blocks |

## SmokeCheck

| Field | Type | Rule |
|-------|------|------|
| `name` | `home` \| `api-unauthenticated` \| `keycloak-discovery` | The three checks in D7 |
| `url` | string | `https://jordylab.be/`, `…/api/settings/users`, `…/auth/realms/jordylab/.well-known/openid-configuration` |
| `expectedStatus` | `200` \| `401` | As listed |
| `attempts` | 1–6 | Retries at 10 s |

## ProductionDeploy (the run summary)

One per run. Written to the job summary (FR-016), not stored elsewhere.

| Field | Type | Rule |
|-------|------|------|
| `trigger` | `automatic` \| `manual` \| `dry-run` | `automatic` when called from `build.yml` |
| `target` | DeployTarget | |
| `previousLive` | LiveState | Read before apply |
| `outcome` | `live` \| `superseded` \| `refused` \| `rolled-back` \| `rollback-failed` \| `no-previous` \| `dry-run-ok` | Final state |
| `liveAfter` | sha \| `unknown` | The commit live at the end: the target, or the restored previous commit |
| `backup` | BackupFreshness | |
| `warnings` | string list | Backup warning; secrets-changed-on-rollback warning |
| `startedAt`, `finishedAt` | timestamp | Used for SC-001 measurements |

### State transitions

```
refused ──────────────────────────────────────────────► (end, nothing touched)
   ▲
   │ grammar and images ok
   ▼
read LiveState ──► superseded (automatic, target older than live) ──► (end, success, notice)
   │
   │ proceed
   ▼
apply target ──► rollouts ──► smoke ──► live
                    │            │
                    └────fail────┘
                          │
                          ▼
              previousLive.liveCommit known?
                 │no                    │yes
                 ▼                      ▼
             no-previous          apply previous ──► rollouts ──► smoke
           (end, failed)              │                     │
                                      ▼                     ▼
                                rolled-back            rollback-failed
                               (end, failed)           (end, failed)
```

Dry run (`dry_run: true`): `refused`, or `dry-run-ok` after `kubectl apply --dry-run=server` and the smoke checks. Nothing
is applied, rolled out or rolled back.

## ReleaseTag (existing, git)

| Field | Type | Rule |
|-------|------|------|
| `name` | `vX.Y.Z` or `vX.Y.Z-rcN` | Existing history; kept |
| `commit` | sha | `git rev-parse "<tag>^{commit}"` |

Release tags are no longer created by any workflow for deploy. They are a valid DeployTarget (`kind: release`).

## ApkRelease (manual workflow)

| Field | Type | Rule |
|-------|------|------|
| `tag` | `vX.Y.Z[-rcN]` | Workflow input; must exist and be on `main` |
| `versionCode` | int | `MAJOR·1000000 + MINOR·10000 + PATCH·100 + (rcN or 99)`, as today |
| `releaseNotes` | text | `git log` between the previous tag and `tag` (D10); truncated to 4000 bytes as today |
