# Data Model: Production on Docker Compose

**Spec**: [spec.md](spec.md) · **Research**: [research.md](research.md)

These are the things the feature tracks. They are records in the runbook, the deploy state on the host and the backup repositories, not application database tables.

## BackupSet

A copy of production that can be restored.

| Field | Meaning |
|-------|---------|
| `kind` | `mac-dump` (encrypted `pg_dump`), `mac-volumes` (encrypted tar of the artwork volume), `repo-full` or `repo-diff` (pgBackRest backups), `repo-wal` (archived change logs) |
| `taken_at` | UTC time the copy was taken |
| `contents` | `database`, `volumes` or `both` |
| `format_owners` | whether ownership is included (`yes` for the cutover dump, `no` for the 2026-11-02 Mac copy) |
| `restore_checked_at` | when the copy was last restored into a throwaway database |
| `row_counts_match` | `true` only when every table matches production at the time of the check |

**Validation rules**
- A `mac-dump` is usable for cutover only when `restore_checked_at` is set and `row_counts_match` is `true`.
- `repo-*` sets older than 30 days expire under the retention rule (research R3). A full set must exist that is older than 30 days before any set expires.

## Release

A deployed version of production, identified by its tag.

| Field | Meaning |
|-------|---------|
| `tag` | `vX.Y.Z` or `vX.Y.Z-rcN` (the same rule the workflow checks today) |
| `images` | backend, frontend and Keycloak images pinned to this tag |
| `state` | `current`, `previous` or `failed` |
| `deployed_at` | UTC time the deploy reported healthy |

**State transitions**
- A successful deploy: the old `current` becomes `previous`; the new tag becomes `current`.
- A failed deploy: the new tag becomes `failed`, the `previous` tag is redeployed and becomes `current` again.
- A rollback by hand: the earlier tag becomes `current`; the tag that was `current` becomes `previous`.

## MaintenanceWindow

The period from the final backup to the restored stack serving traffic.

| Field | Meaning |
|-------|---------|
| `started_at`, `ended_at` | UTC times, recorded for every cutover (SC-007) |
| `abort_deadline` | the time after which the rebuild path (R14) is taken; agreed before the window starts |
| `steps` | the ordered phase-B steps from plan.md, each with `status` (`pending`, `done`, `failed`) and `checked_at` |
| `outcome` | `served` (traffic back, all smoke checks pass) or `rebuild` (abort path taken) |

## Service

One container in the Compose stack.

| Field | Meaning |
|-------|---------|
| `name` | `caddy`, `frontend`, `backend`, `keycloak`, `ntfy`, `postgres` |
| `image` | the image reference, with the release tag for application services |
| `publishes` | `80`, `443` for Caddy only; none for the rest |
| `health` | the check that `up --wait` uses |
| `memory_limit_mib` | the limit from research R7 |
| `volumes` | the named volumes it mounts |

**Validation rule**: only `caddy` publishes ports. Any other publish fails the external port scan (FR-014).

## PublicRoute

One path the public site accepts, and where it goes.

| Field | Meaning |
|-------|---------|
| `path` | the path, as a prefix or an exact match |
| `match` | `prefix` or `exact` |
| `target` | the service that answers |
| `rewrite` | `none`, or `strip-prefix` for `/ntfy` |

The full list is in [contracts/public-routes.md](contracts/public-routes.md).
