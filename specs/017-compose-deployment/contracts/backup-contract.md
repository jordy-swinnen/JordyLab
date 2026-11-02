# Contract: Backups and restore

The Postgres backups for the Compose stack. Retention and schedules match today's CloudNativePG setup (`deploy/k8s/cluster/ovh-object-storage.yaml`): 30-day restorable window, daily base backup at 03:00 UTC, weekly base backup on Sunday at 04:00 UTC (research R3).

## Repository

| Setting | Value | Why |
|---------|-------|-----|
| type | S3-compatible | OVH Object Storage |
| endpoint | `s3.gra.io.cloud.ovh.net` | same region as today (Gravelines) |
| region | `gra` | matches the endpoint |
| bucket | `jordylab-cnpg-backups` | the existing bucket |
| path | `/pgbackrest` | a new prefix, so it never mixes with the old `cnpg-backups` chain |
| URI style | `path` | required for this endpoint |
| credentials | `PGBACKREST_REPO1_S3_KEY`, `PGBACKREST_REPO1_S3_KEY_SECRET` from the environment | never written to a file |
| retention | `repo1-retention-full-type=time`, `repo1-retention-full=30` | the 30-day window (verified in research R3) |
| compression | `zst` | pgBackRest default family; any choice works |

The configuration file is `deploy/compose/pgbackrest/pgbackrest.conf` (its full content is in research R3). The stanza is `jordylab`.

## Schedules (host systemd timers)

The differential timer skips Sunday, so the weekly full at 04:00 never runs back to back with a differential at 03:00.

| Timer | When (UTC) | Command |
|-------|-----------|---------|
| `jordylab-backup-diff.timer` | Monday to Saturday 03:00 | `docker compose exec -T postgres pgbackrest --stanza=jordylab --type=diff backup` |
| `jordylab-backup-full.timer` | Sunday 04:00 | `docker compose exec -T postgres pgbackrest --stanza=jordylab --type=full backup` |

Each service writes to the journal. A failed run is visible with `systemctl status` and `journalctl -u`. A run that fails is not retried silently.

## Archiving

- `archive_mode=on`
- `archive_command = pgbackrest --stanza=jordylab archive-push %p`

Change logs are archived continuously. The first stanza setup (`stanza-create`) and first full backup run in plan step 11.

## Checks

- `pgbackrest --stanza=jordylab check` must pass after the stanza is created and after each timer.
- `pgbackrest --stanza=jordylab info` must list at least one full backup, and the newest one must be within the schedule.

## Restore (point in time)

On an empty data directory in a throwaway container with no published ports:

```text
pgbackrest --stanza=jordylab --type=time "--target=<UTC time>" --target-action=promote restore
```

Then start Postgres, check the row counts, and remove the container. The target time must lie inside the window and after the first full backup.

## The old chain (before cutover)

Backups taken before cutover are in the old Barman layout under `cnpg-backups`. pgBackRest cannot read that layout. The old chain stays in the bucket until its 30-day retention ends. A restore to a time before cutover uses the old tooling: the CloudNativePG recovery manifests kept under `deploy/k8s/cluster/` and `deploy/k8s/drills/`, applied to a cluster built from the kept Kubernetes material. The runbook records this (plan phase C, step 16 and the rollback rebuild in R14).

## Mac copy

The encrypted copy on the owner's Mac is not part of this contract's repository. It is restored with `age -d` and `pg_restore` (ownership checked as in research R4).
