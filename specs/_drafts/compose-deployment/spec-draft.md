# Feature Draft: Production on Docker Compose instead of k3s

**Status**: Draft, direction agreed in principle, low urgency (k3s was chosen mainly to try it; Docker would suffice). Eight decisions recorded 2026-11-02 (see open questions).
**Target path**: `specs/_drafts/compose-deployment/spec.md`
**Supersedes parts of**: the OVH k3s deployment spec, `deploy/k8s/`, `docs/runbooks/vps-k3s-deployment/`, `docs/runbook.md`
**Interacts with**: `continuous-deploy`, `public-private-exposure`, `auth-without-keycloak`

> Repository facts were checked on 2026-11-02; the sources are listed in `research.md`.

## Problem

Production is one node. Kubernetes gives scheduling, failover and rolling updates across machines, and none of that applies here. What it costs: k3s upgrades (pinned `v1.37.0+k3s1`), Traefik with Gateway API, cert-manager, ServiceLB, the CloudNativePG operator plus the Barman Cloud plugin, manifests with overlays, and a runbook of 23 sections. Local development already uses Compose, so production could share a file lineage with it.

## When to do it

Not now. The current setup works and has taught what it was meant to. Do it when the next k3s, cert-manager or CloudNativePG upgrade is due, or when a quiet weekend allows a migration. Do `continuous-deploy` and `auth-without-keycloak` first: both shrink what has to be migrated.

## Target topology

Docker Engine and the Compose plugin on the same kind of VPS (Ubuntu LTS, `ufw` on 22/80/443):

- Reverse proxy with automatic TLS: Caddy (decided 2026-11-02, see open questions).
- `frontend` (nginx), `backend`, `ntfy`, PostgreSQL 16 with pgvector, and Keycloak until `auth-without-keycloak` removes it.
- Postgres backups with WAL archiving plus daily and weekly base backups to OVH Object Storage (Gravelines), using pgBackRest. Parity with today's point-in-time restore is a hard requirement.
- Secrets stay SOPS + age in git, decrypted only by the deploy workflow.
- Deploy: GitHub Actions joins Tailscale as `tag:ci`, connects to the host, runs `docker compose pull && docker compose up -d --wait` with images pinned to the release tag (`vX.Y.Z`), as the k3s deploy already does. Rollback is running the deploy workflow with an older tag, as today.

## Requirements

- Everything that runs in production is described by one Compose project plus an env/secrets file, with images pinned by release tag.
- TLS issuance and renewal are automatic, verified by a forced-renewal test.
- Point-in-time restore works against object storage backups, and the quarterly restore drill (next due 2027-01-01, in the manual test runbook) is re-run on the new stack before the old one is decommissioned.
- Services restart after a VPS reboot, with healthchecks and `restart: unless-stopped`, and the reboot drill passes.
- Container log rotation limits are set (Docker's default json-file driver grows without bound), and memory limits fit the 8 GB VPS.
- The deploy workflow from `continuous-deploy` is retargeted to Compose, including readiness waiting and automatic rollback.
- `docs/runbook.md`, the `jordylab-ops` skill and the `jordylab-devops` agent are rewritten for Compose. `docs/learn/` Kubernetes notes stay as history.

## Migration plan

Decided 2026-11-02: no second VPS. The move happens on the same host, and a backup held on the owner's Mac is the safety net.

1. Before any change, back up production to the owner's Mac, encrypted with age: a dump of the `jordylab` database (it includes the `keycloak` schema), plus the artwork and APK volumes.
2. Restore the database dump into a throwaway local Postgres on the Mac and compare row counts with production. This proves the backup before anything is removed.
3. Open a maintenance window. Stop the workloads, then take a final database dump so no writes are lost.
4. Remove k3s, install Docker Engine and the Compose stack on the same host, restore the database and the volumes from the Mac backup, verify, and reopen traffic. The IP does not change, so DNS does not need to move.
5. Keep the Mac backup and the object-storage backups until pgBackRest has taken its first base backup on the new stack.

Trade-offs of this plan, recorded here so they are not forgotten:

- **Rollback is a rebuild, not a switch.** There is no old node. If the Compose stack fails, the way back is to reinstall k3s on the same host and restore from the Mac backup. This is slower than the earlier two-VPS plan.
- **Point-in-time restore before the cutover uses the old chain.** The old Barman chain in the bucket is not readable by pgBackRest. It stays in the bucket until its 30-day retention runs out, and restoring to a time before the cutover needs the old tooling.
- **Downtime covers the whole copy.** The maintenance window lasts from the final dump to the restore finishing.

## Success criteria

- The Mac backup restores into a throwaway Postgres with row counts matching production, before k3s is removed.
- A point-in-time restore to a chosen timestamp succeeds on the new stack.
- A deploy and a rollback both complete through the workflow with no manual server access.
- Memory use on the host is lower than on k3s (measure before and after, no assumption).

## Out of scope

High availability, multiple nodes, changing hosting provider, Kubernetes for other things.

## Open questions

The owner decided each question on 2026-11-02. The recorded answer is marked "Decided".

1. **Caddy or Traefik?** Decided 2026-11-02: Caddy. It issues and renews TLS on its own with a short config, and a second tailnet-only site (`public-private-exposure`) is simple to add. The current Gateway API routes do not carry over, because Compose has no Gateway, so keeping Traefik buys less than it seems.
2. **Restart gap per deploy.** Decided 2026-11-02: accept a few seconds of downtime per release. The backend already runs with `strategy: Recreate`, so a restart per release is today's behaviour. Frontend currently rolls without a gap, so Compose adds a few seconds there. Deploys run on release tags only (a push to `main` does not deploy), so "deploy-on-merge" is no longer the trigger.
3. **Docker or Podman on the server?** Decided 2026-11-02: Docker Engine. Local development is also moving from Podman to Docker; that is a separate change, not part of this draft.
4. **Postgres major upgrades** lose the operator's help. Decided 2026-11-02: pin the image to the major version, and write a `pg_upgrade` or dump/restore procedure before the next major bump. This does not block the move.
5. **Public SSH on port 22.** Decided 2026-11-02: close it once Tailscale SSH works for Jordy and for the CI runner (`tag:ci`). This is a host firewall change, so it needs Jordy's approval at cutover.
6. **Open port finding.** Decided 2026-11-02: leave it to the move and do not patch k3s for it. Compose has no NodePorts, so the move removes the cause. Run the same port scan on the new host before cutover. If the move slips past the next k3s upgrade, apply the k3s fix instead. Also check whether `ufw` filters the NodePort path at all, because if it does not, the new host needs its own firewall rules.
7. **Postgres backup tool.** Decided 2026-11-02: pgBackRest, the most established option for point-in-time restore to an S3-compatible endpoint. The 30-day retention must be reproduced exactly; check the option names in its docs when planning.
8. **Rebuild success criterion.** Decided 2026-11-02: dropped, so it is no longer a success criterion. The rebuild check was already dropped from the manual test runbook on 2026-10-05.

## Facts checked in the repo

The checked facts, with their sources, are in `research.md`. Still open for planning:

- The three manual test runbook procedures on Kubernetes (reboot, point-in-time restore, rebuild) need rewriting for Compose. The rebuild one was dropped.
- The Gateway routes (frontend, backend, keycloak, and ntfy with its `/ntfy` prefix rewrite) need Caddy equivalents, including the HTTP→HTTPS redirect.
- The artwork and APK volumes are local-path volumes, and only Postgres is in the object-storage backup. The VPS-loss case for those volumes is not covered by any backup today, and the Compose plan must say how they are backed up.
