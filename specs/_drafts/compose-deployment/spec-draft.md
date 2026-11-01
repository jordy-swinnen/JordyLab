# Feature Draft: Production on Docker Compose instead of k3s

**Status**: Draft, direction agreed in principle, low urgency (k3s was chosen mainly to try it; Docker would suffice)
**Target path**: `specs/_drafts/compose-deployment/spec.md`
**Supersedes parts of**: spec 008 (OVH k3s deployment), `deploy/k8s/`, `docs/runbooks/vps-k3s-deployment/`, `docs/runbook.md`
**Interacts with**: `continuous-deploy`, `public-private-exposure`, `auth-without-keycloak`

> Written without repo access. Topology details come from `jordylab-infrastructure-guide.md` (2026-10-07).

## Problem

Production is one node. Kubernetes gives scheduling, failover and rolling updates across machines, and none of that applies here. What it costs: k3s upgrades (pinned `v1.37.0+k3s1`), Traefik with Gateway API, cert-manager, ServiceLB, the CloudNativePG operator plus the Barman Cloud plugin, manifests with overlays, and a runbook of 21 sections. Local development already uses Compose, so production could share a file lineage with it.

## When to do it

Not now. The current setup works and has taught what it was meant to. Do it when the next k3s, cert-manager or CloudNativePG upgrade is due, or when a quiet weekend allows a migration. Do `continuous-deploy` and `auth-without-keycloak` first: both shrink what has to be migrated.

## Target topology

Docker Engine and the Compose plugin on the same kind of VPS (Ubuntu LTS, `ufw` on 22/80/443):

- Reverse proxy with automatic TLS (Caddy, or Traefik with ACME; see Open question 1).
- `frontend` (nginx), `backend`, `ntfy`, PostgreSQL 16 with pgvector, and Keycloak until `auth-without-keycloak` removes it.
- Postgres backups with WAL archiving plus daily and weekly base backups to OVH Object Storage (Gravelines), using pgBackRest or WAL-G. Parity with today's point-in-time restore is a hard requirement.
- Secrets stay SOPS + age in git, decrypted only by the deploy workflow.
- Deploy: GitHub Actions joins Tailscale as `tag:ci`, connects to the host, runs `docker compose pull && docker compose up -d --wait` with pinned `sha-<commit>` image tags. Rollback is deploying an older sha.

## Requirements

- **FR-001**: Everything that runs in production is described by one Compose project plus an env/secrets file, with images pinned by sha.
- **FR-002**: TLS issuance and renewal are automatic, verified by a forced-renewal test.
- **FR-003**: Point-in-time restore works against object storage backups, and the quarterly drill (next due 2027-01-01, manual runbook MRB-09/10/13) is re-run on the new stack before the old one is decommissioned.
- **FR-004**: Services restart after a VPS reboot, with healthchecks and `restart: unless-stopped`, and the reboot drill passes.
- **FR-005**: Container log rotation limits are set (Docker's default json-file driver grows without bound), and memory limits fit the 8 GB VPS.
- **FR-006**: The deploy workflow from `continuous-deploy` is retargeted to Compose, including readiness waiting and automatic rollback.
- **FR-007**: `docs/runbook.md`, the `jordylab-ops` skill and the `jordylab-devops` agent are rewritten for Compose. `docs/learn/` Kubernetes notes stay as history.

## Migration plan (proposed)

1. Provision a second VPS temporarily (billable, so done by Jordy), build the Compose stack on it.
2. Restore the latest base backup plus WAL from object storage into the new Postgres. This doubles as the restore drill.
3. Lower the DNS TTL, cut over, verify, keep the old node for a rollback window, then decommission.

## Success criteria

- **SC-001**: A full rebuild from an empty VPS to serving traffic, following only the runbook, succeeds and the time is recorded.
- **SC-002**: A point-in-time restore to a chosen timestamp succeeds on the new stack.
- **SC-003**: A deploy and a rollback both complete through the workflow with no manual server access.
- **SC-004**: Memory use on the host is lower than on k3s (measure before and after, no assumption).

## Out of scope

High availability, multiple nodes, changing hosting provider, Kubernetes for other things.

## Open questions

1. Caddy or Traefik? Caddy is simpler for automatic TLS and a second tailnet-only site (`public-private-exposure`), Traefik keeps existing familiarity.
2. Compose has no readiness-gated rolling update. A single replica recreates in seconds with downtime of a few seconds per deploy. Is that acceptable with deploy-on-merge? (Check how the current single-replica rollout behaves, it may already be similar.)
3. Docker or Podman on the server? Local dev uses Podman Compose, production Docker is the more common path.
4. Postgres major upgrades lose the operator's help and need a dump/restore or `pg_upgrade` plan.
5. Should SSH on port 22 close publicly and move to Tailscale SSH only?

## Facts to verify in the repo

- Current Postgres image and extensions in `deploy/` (pgvector version), and CNPG backup retention settings, so the new backup config matches.
- Existing rollout strategy for the backend and its volumes (artwork, APK volume).
- What `docs/testing/manual-test-runbook.md` MRB-09, MRB-10, MRB-13 assume about Kubernetes.
