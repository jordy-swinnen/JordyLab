# Data Model: Production on Podman

No application schema changes. The model below covers what moves (the persistent data set), what runs (the stack), and the two time periods the procedure tracks (the switch window and the soak period).

## Persistent data set

Four named volumes. Docker names them `<project>_<volume>` with the Compose project `jordylab`; the Docker Compose client over Podman's socket uses the same names, so each volume keeps its name.

| Volume | Holds | Written by (id in container) | Copy method | Check after copy | Loss if missing |
|--------|-------|------------------------------|-------------|------------------|-----------------|
| `postgres-data` | The database cluster | Postgres (999 in the official image; confirm in rehearsal) | Cold tar stream of the stopped cluster; set ownership inside a rootless container | Checksum manifest of both sides; after start, row counts per table equal the pre-switch counts; `pgbackrest check` passes | Everything; restore from the final pgBackRest backup or the Mac copy |
| `artwork` | Game artwork files | Backend | Tar stream | File count and checksums equal | Restore from the Mac copy (the spec 020 backup includes the artwork) |
| `caddy-data` | TLS certificates and account keys | Caddy | Tar stream | Certificate files present; Caddy serves without requesting a new certificate | New certificates are issued automatically; repeated issuing can hit rate limits |
| `ntfy-cache` | Notification message cache | ntfy | Tar stream | File present; ntfy health check passes | Recent notification history only |

State of a volume during the switch: `in-docker` → `copied` (manifest equal) → `verified` (service healthy on it) → `retired` (removed at the end of the soak period). A volume never skips `verified`.

## Stack

Five services, described by `deploy/compose/compose.yaml` and an environment file. Unchanged by this feature except `restart: always` on every service.

| Service | Role | Published ports | Depends on (healthy) |
|---------|------|-----------------|----------------------|
| `caddy` | Reverse proxy and TLS | 80, 443 | frontend, backend, ntfy |
| `frontend` | Static web app | none | none |
| `backend` | API | none | postgres |
| `ntfy` | Push notifications | none | none |
| `postgres` | Database with pgBackRest | none | none |

Container names stay `jordylab-<service>-1`.

## Backup set

Unchanged in structure (see the backup contract of spec 020). For this feature, three sets matter:

| Set | Where | Taken | Used for |
|-----|-------|-------|----------|
| Final pgBackRest full backup | Object storage | Just before the stack stops in the window | Recovery if the volume copy is bad |
| Encrypted production copy | Owner's Mac | Before the window; refreshed if older than one day | Last safety net; restored into a throwaway database to check row counts (FR-003) |
| Ongoing chain | Object storage | Continuous archiving and the timers | Point-in-time restore before and after the switch (FR-008) |

## Switch window

States: `prepared` (host ready, images pulled, copy script rehearsed) → `approved` (owner approves the host change; automatic deploys paused) → `stopped` (Docker stack stopped, final backup taken) → `copied` → `serving` (Podman stack healthy, checks passed) → `closed` (window length recorded). A failure in `stopped` or `copied` leads to `fell-back` (Docker stack restarted from its untouched volumes).

Recorded: start, end, length, and the reason for any fall-back.

## Soak period

States: `running` (one week from `closed`; Docker Engine installed, stopped, disabled; Docker volumes kept) → `ended` (no rollback needed; owner approves removal) → `removed` (Docker Engine, networks and volumes gone; fresh backup confirmed) or `rolled-back` (the Docker stack serves again).

Rule: the soak period only starts after the first workflow deploy and the first night of backups succeed under Podman.
