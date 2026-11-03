# Implementation Plan: Production on Docker Compose

**Branch**: `jordyswinnen/jor-31-production-on-docker-compose-instead-of-k3s` | **Date**: 2026-11-02 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/017-compose-deployment/spec.md`

## Summary

Move production from the one-node k3s cluster on the VPS to a single Docker Compose stack on the same VPS. Caddy takes ports 80 and 443 and keeps the current public paths. The stack has six services: Caddy, the frontend, the backend, Keycloak, ntfy and Postgres with pgBackRest. Backups go to the same object-storage region, under a new prefix, with 30-day restore parity. Deploys keep the release-tag chain and the `production` approval, and retarget the rollout to Compose with automatic rollback. The cutover runs in one maintenance window on the same host, with an encrypted Mac backup as the safety net. The technical choices are in [research.md](research.md).

## Technical Context

**Language/Version**: Compose file (Compose v2 format), Caddyfile, POSIX shell for host scripts, GitHub Actions YAML. Postgres 16 with pgvector (a pinned `pgvector/pgvector:pg16` tag, the same major and extension as production today, which ran on CloudNativePG 16.15).

**Primary Dependencies**: Docker Engine with the Compose plugin (current stable at install, recorded on the host); Caddy (pinned tag); pgBackRest (Debian package, in the Postgres image); Keycloak 26.7.4, nginx-unprivileged 1.30, ntfy v2.28.0 (as today); backend, frontend and Keycloak images from GHCR, pinned by release tag; SOPS 3.13.3 and age 1.3.2 on the runner (as today).

**Storage**: Postgres data in a named volume; artwork files in a named volume; ntfy cache and Caddy certificate data in named volumes; the pgBackRest repository in OVH Object Storage (Gravelines, prefix `/pgbackrest`); one encrypted copy of production on the owner's Mac (outside the repository).

**Testing**: no automated unit tests for this change (no application code changes). Verification is the checklist in [quickstart.md](quickstart.md): restore and row counts, point-in-time restore, forced certificate renewal, reboot, deploy and rollback through the workflow, external port scan, and the smoke checks.

**Target Platform**: Ubuntu LTS on the OVH VPS-2 (4 vCPU, 8 GB RAM, London), reached over Tailscale for administration.

**Project Type**: deployment configuration (infrastructure as code) in the monorepo, plus workflow changes.

**Performance Goals**: memory use on the host lower than on k3s (measured before and after); a few seconds of downtime per release, as decided.

**Constraints**: no second VPS; only ports 80 and 443 public (and 22 until public SSH is closed at cutover); 30-day restore window; no secret printed or committed.

**Scale/Scope**: one host, six services, one Postgres database with the `jordylab` and `keycloak` schemas, about 122 tables, a few hundred megabytes of data.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

The constitution covers application code (Java, Angular, testing). This feature changes no application code, so most principles do not apply. The principles that do apply to the scripts and workflow:

| Principle | How this plan meets it | Result |
|-----------|------------------------|--------|
| I. Clean Code Discipline | Host and runner scripts stay short, with full descriptive names and one job each; no abbreviations in script names | PASS |
| II. Fail Fast, No Silent Failures | Scripts run with `set -euo pipefail`; a failed health check fails the deploy and triggers the automatic rollback; a failed restore check stops the cutover before traffic returns; no step ignores an error | PASS |
| III. Immutable, Builder-First Design | Not applicable: no Java or Lombok code changes | N/A |
| IV. Testing Discipline | The verification is a documented, repeatable checklist (quickstart), not ad-hoc. No test is marked as passed unless it was run | PASS |
| V. Language & Tooling Currency | Compose v2, current Caddy, pinned images; no code frameworks change | PASS |

Gate result: PASS. No unresolved clarifications remain (spec and research).

*Re-check after Phase 1 design*: still PASS. The design adds scripts, Compose and workflow files, and no application code. The Postgres base image change in research R3 does not touch any principle.

## Phase 0: Outline & Research

Done in [research.md](research.md). Summary of decisions:

- R1: production Compose files in `deploy/compose/`, separate from local development.
- R2: Caddy routes the same paths as the Gateway, and obtains its own certificate at cutover.
- R3: pgBackRest on the pinned Postgres 16 image; time-based retention of 30 days (verified); weekly full backups and differential backups Monday to Saturday.
- R4: final dump with owners; roles created before restore.
- R5: deploy over the tailnet with a dedicated key; `tcp:22` grant for `tag:ci` needs owner approval.
- R6: SOPS decrypt on the runner, environment file written with mode 600.
- R7: health checks, restart policy, log limits and memory limits.
- R8: fixed Compose subnet for the trusted proxy; only Caddy publishes ports.
- R9: named volumes with a bulk copy before the window and a delta copy inside it.
- R11: systemd timers for backups.
- R14: rollback after cutover is a rebuild from the Mac backup.

## Phase 1: Design & Contracts

- [data-model.md](data-model.md): the entities behind the spec (backup set, release, maintenance window, service, public route).
- [contracts/public-routes.md](contracts/public-routes.md): the public paths and the service each one reaches.
- [contracts/deploy-workflow.md](contracts/deploy-workflow.md): the inputs, steps, success rule and automatic rollback of the Compose deploy.
- [contracts/env-contract.md](contracts/env-contract.md): every environment variable the stack needs, its source and whether it changes from today.
- [contracts/backup-contract.md](contracts/backup-contract.md): the pgBackRest stanza, schedules, retention and restore commands.
- [quickstart.md](quickstart.md): the validation checklist, in the order it must run.

### Cutover sequence

The cutover is a sequence of phases. Each phase has an exit check, and a failed check stops the sequence.

**Phase A: preparation, no production change to traffic** (each host change needs the owner's yes):

1. Build the Postgres image with pgBackRest and run it locally against the encrypted Mac backup (restore test, quickstart step 1).
2. Write the Compose files, the environment template and the host timers. Validate them locally with dummy values (`docker compose config`).
3. Install Docker Engine and the Compose plugin on the host. Publish no ports yet. k3s keeps running.
4. Copy the artwork files from the k3s volume to a staging folder on the host (bulk copy).
5. Owner approves the Tailscale policy change for `tag:ci` (`tcp:22`) and the deploy key for the runner.
6. Add the Compose deploy workflow as a separate file. The current release path keeps deploying to k3s until cutover.

6a. Run the kept CloudNativePG restore drill to a point in time inside the 30-day window, while k3s still runs. This is the only way to prove that the old backup chain restores (backup contract, "The old chain"). It creates a drill cluster, so it needs the owner's yes, and the drill cluster and its volume are deleted afterwards.

*Exit check*: the local restore matches, the Compose config validates, Docker is up, the staging copy matches the source, and the tailnet SSH path works from a test job.

**Phase B: maintenance window** (the owner starts it):

**Release freeze**: from the start of this phase until step 22, no release tag is pushed. `release.yml` still targets k3s, so a tag pushed in that time would fail. Phase C tests run the Compose workflow by hand.

7. Scale the write paths down (backend, Keycloak, ntfy) so no new data arrives. This is a cluster change, so it needs the owner's yes.
8. Take the final dump with owners, encrypt it to the Mac, and check the header and the row counts against production.
9. Copy the changed artwork files (delta) into the host volume.
10. Stop and disable k3s. This frees ports 80 and 443.
11. Start Postgres with pgBackRest. Create the `jordylab` and `keycloak` roles. Restore the final dump. Create the pgBackRest stanza and take the first full backup.
12. Start the backend, Keycloak, ntfy, the frontend and Caddy with `up -d --wait`. Caddy obtains the certificate on first request.
13. Smoke checks (quickstart step 5): site over HTTPS, sign-in, artwork, ntfy publish, Keycloak discovery, row counts against the final dump.
14. If every check passes, the window ends. The public address is unchanged, so DNS needs no change.
15. Owner approves closing public SSH after tailnet SSH is confirmed. This is the last host change in the window.

*Abort rule*: if step 12 or 13 fails and cannot be fixed inside the window the owner set before it started, the way back is the rebuild in R14. The abort time is agreed before the window starts, so it is not decided during it.

**Phase C: after the window**:

16. Confirm the first pgBackRest full backup and the differential timer, Monday to Saturday (`pgbackrest info`).
17. Forced certificate renewal test on Caddy.
18. Reboot test (every service returns unaided).
19. Point-in-time restore to a time inside the window onto a throwaway Postgres container with no published ports, then remove it.
20. External port scan of the new host, recorded in the quickstart log.
21. Deploy a release candidate and roll back through the workflow.
22. Retire the k3s deploy job and switch `release.yml` to the Compose workflow.
23. Rewrite the runbook, the `jordylab-ops` skill and the `jordylab-devops` agent for Compose (FR-018). Keep the Kubernetes material as history.
24. Keep the Mac backup until steps 16 to 21 pass, then the owner decides whether to delete it.

### Compose stack layout

`deploy/compose/compose.yaml` defines these services, all `restart: unless-stopped`, all with health checks and log limits (R7):

| Service | Image | Publishes | Depends on |
|---------|-------|-----------|------------|
| `caddy` | Caddy (pinned tag) | 80, 443 | frontend, backend, keycloak, ntfy |
| `frontend` | `ghcr.io/jordy-swinnen/jordylab-frontend:<release>` | none | — |
| `backend` | `ghcr.io/jordy-swinnen/jordylab-backend:<release>` | none | postgres, keycloak |
| `keycloak` | `ghcr.io/jordy-swinnen/jordylab-keycloak:<release>` | none | postgres |
| `ntfy` | `binwiederhier/ntfy:v2.28.0` | none | — |
| `postgres` | `deploy/compose/postgres/` image (`pgvector/pgvector:pg16` pinned + pgBackRest) | none | — |

Named volumes: `postgres-data`, `artwork`, `ntfy-cache`, `caddy-data`. One Compose network with subnet `172.28.0.0/16`.

## Project Structure

### Documentation (this feature)

```text
specs/017-compose-deployment/
├── spec.md
├── plan.md              # this file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   ├── public-routes.md
│   ├── deploy-workflow.md
│   ├── env-contract.md
│   └── backup-contract.md
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2 output (/speckit-tasks, not created here)
```

### Source Code (repository root)

```text
deploy/compose/
├── compose.yaml                     # the production stack (six services)
├── Caddyfile                        # public routes and TLS (R2)
├── env/
│   └── prod.env.example             # variable names only, no values (env-contract)
├── postgres/
│   └── Containerfile                # pgvector/pgvector:pg16 (pinned) + pgBackRest
├── pgbackrest/
│   └── pgbackrest.conf              # stanza jordylab, S3 repo, 30-day retention
├── host/
│   ├── jordylab-backup-full.timer   # weekly full backup (systemd)
│   ├── jordylab-backup-full.service
│   ├── jordylab-backup-diff.timer   # differential backup, Monday to Saturday (systemd)
│   └── jordylab-backup-diff.service
└── scripts/
    ├── render-env.sh                # runner: decrypt SOPS, write prod.env to stdout
    └── deploy-release.sh            # host: pull, up --wait, roll back on failure

.github/workflows/
└── deploy-prod-compose.yml          # the Compose deploy (release.yml switches to it at step 22)

deploy/k8s/, deploy/host/            # kept as history (Kubernetes material)
docs/runbook.md                      # rewritten for Compose (step 23)
docs/runbooks/vps-k3s-deployment/    # kept as history
```

**Structure Decision**: the production stack is a new `deploy/compose/` folder beside the existing `deploy/` folders. The old Kubernetes folders stay in place as history, because the rollback path (R14) re-applies them.

## Complexity Tracking

> Fill ONLY if Constitution Check has violations that must be justified

No violations. Two deploy workflows exist during the transition (steps 6 and 22). That is a planned, short-lived state that keeps the current release path working until cutover.
