# Implementation Plan: Production on Podman

**Branch**: `claude/docker-podman-migration-51813c` | **Date**: 2026-11-08 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/024-podman-on-vps/spec.md`

## Summary

Move the production stack on the VPS from Docker Engine to rootless Podman, run by the existing deploy account, so that production and local development use the same container engine. The stack definition, the images, the public routes, the data and the backup chain stay the same. What changes is the engine, the account that runs it, how the stack starts at boot, how the backup jobs reach the database container, and how the data moves from Docker's storage to Podman's.

The technical choices are in [research.md](research.md). In short: rootless Podman as the deploy account (no `docker` group, no root-equivalent access); the Docker Compose v2 client as the Compose provider, talking to Podman's own socket, so `compose.yaml` and the deploy script keep their meaning; `restart: always` with Podman's restart unit and lingering for boot recovery; one sysctl line so the proxy can bind ports 80 and 443; a cold copy of the stopped volumes, so the Postgres cluster keeps its identity and the pgBackRest chain continues without a break; and Docker Engine kept installed but stopped during a one-week soak period as the way back. Every host-level claim is first proven on a throwaway Ubuntu 26.04 machine before the maintenance window.

## Technical Context

**Language/Version**: Compose v2 file format, bash for host scripts, systemd units, GitHub Actions YAML. No application code changes.

**Primary Dependencies**: Podman 5.7.0 from the Ubuntu 26.04 archive (version to be confirmed on the host and recorded); Docker Compose v2 client as the Compose provider (pinned version, recorded); netavark, aardvark-dns, pasta and `uidmap` as Podman's rootless networking and user-namespace support; Caddy, ntfy, the Postgres image with pgBackRest, the backend and frontend images exactly as pinned today.

**Storage**: Rootless container storage in the deploy account's home on the VPS's NVMe disk. Four named volumes (`postgres-data`, `artwork`, `ntfy-cache`, `caddy-data`). The pgBackRest repository stays in OVH Object Storage, unchanged. The encrypted production copy stays on the owner's Mac.

**Testing**: No unit tests (no application code changes). Verification is a rehearsal on a throwaway Ubuntu 26.04 machine and the checklist in [quickstart.md](quickstart.md): stack validation with the local engine, the volume copy with checksums, reboot recovery, deploy and rollback, port scan, backup timers, point-in-time restore, and the way back.

**Target Platform**: Ubuntu 26.04 LTS on the OVH VPS-2 (4 vCPU, 8 GB RAM, 75 GB NVMe), administered over Tailscale SSH. Local engine for comparison: Podman 6.1.4 on the owner's Mac, which runs it inside a Linux VM.

**Project Type**: Deployment configuration (infrastructure as code) in the monorepo, plus host scripts, systemd units and documentation.

**Performance Goals**: Switch window of 60 minutes or less (working figure from the spec); the downtime of each release after the switch stays at a few seconds; the host's memory use is measured before and after.

**Constraints**: One host. Only ports 80 and 443 public. `ufw` stays the single authority for the firewall. The deploy runs over the tailnet with no root and no `docker` group. No secret is printed or committed. The 30-day restorable history must not break.

**Scale/Scope**: One host, five services (Caddy, frontend, backend, ntfy, Postgres with pgBackRest), one database of a few hundred megabytes, one small artwork volume.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

The constitution covers application code (Java, Angular, testing). This feature changes no application code, so most principles do not apply. The ones that apply to the scripts, units and workflow:

| Principle | How this plan meets it | Result |
|-----------|------------------------|--------|
| I. Clean Code Discipline | Scripts stay short, with one job each and full descriptive names. The one-off volume copy is a script with named functions, not a block of pasted commands | PASS |
| II. Fail Fast, No Silent Failures | Scripts keep `set -euo pipefail`. A failed health check fails the deploy and triggers the rollback. A checksum mismatch after the volume copy stops the switch before the stack starts. A backup unit that cannot reach Podman fails visibly in the journal | PASS |
| III. Immutable, Builder-First Design | Not applicable: no Java or Lombok code changes | N/A |
| IV. Testing Discipline | Every host claim is proven on the rehearsal machine and recorded in the quickstart before the window. No check is marked passed unless it was run | PASS |
| V. Language & Tooling Currency | Current Podman from the Ubuntu LTS archive, Compose v2, pinned images | PASS |

Gate result: PASS. No unresolved clarifications remain (spec and research).

*Re-check after Phase 1 design*: still PASS. The design adds shell scripts, unit files, a sysctl file and documentation, and touches no application code. The rootless choice adds a few host settings (lingering, subordinate ids, one sysctl) that the host contract lists and the rehearsal proves.

## Phase 0: Outline & Research

Done in [research.md](research.md). Summary of decisions:

- R1: rootless Podman, run by the deploy account.
- R2: Docker Compose v2 as the Compose provider over Podman's socket; not `podman-compose`, not Quadlet.
- R3: `restart: always` plus Podman's restart unit and lingering for boot recovery.
- R4: lower `net.ipv4.ip_unprivileged_port_start` to 80 for the proxy's ports.
- R5: `ufw` stays authoritative because rootless Podman writes no firewall rules.
- R6: cold copy of the stopped volumes, preserving the Postgres cluster identity.
- R7: backup timers stay system units but run as the deploy account and call `podman exec`.
- R8: health checks and limits carry over; three behaviours are verified in the rehearsal.
- R9: the deploy account must be a normal-range account with subordinate ids.
- R10: rehearsal on a throwaway Ubuntu 26.04 machine before the window.
- R11: Docker Engine stays installed but stopped for a one-week soak period, then is removed with the owner's approval.
- R12: pause automatic deploys, merge, switch, run the workflow once, resume.
- R13: local parity is checked with the Docker Compose client selected by an environment variable, without changing the owner's defaults.

## Phase 1: Design & Contracts

- [data-model.md](data-model.md): the persistent data set (each volume, its owner, its copy method, its check), the stack's services, and the states of the switch window and soak period.
- [contracts/host-contract.md](contracts/host-contract.md): what the Podman host must provide (account, packages, settings, units), and the mapping from each Docker command in the scripts and units to its Podman form.
- [contracts/switch-procedure.md](contracts/switch-procedure.md): the ordered switch with its gates, the way back, and the end of the soak period.
- [quickstart.md](quickstart.md): the runnable checks for the rehearsal, the window and the soak period.

## Project Structure

### Documentation (this feature)

```text
specs/024-podman-on-vps/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   ├── host-contract.md      # The Podman host and the command mapping
│   └── switch-procedure.md   # The switch, the way back, the end of the soak period
├── checklists/
│   └── requirements.md       # Spec quality checklist (/speckit-specify)
├── spec.md              # /speckit-specify output
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
deploy/compose/
├── compose.yaml                      # restart: always; header and Docker-specific comments updated
├── scripts/
│   ├── podman-env.sh                 # NEW: sourced helper; runtime directory, Compose provider, quiet provider notice
│   ├── deploy-release.sh             # docker compose -> podman compose; sources podman-env.sh
│   ├── apply-caddy-config.sh         # docker exec/cp/compose -> podman equivalents
│   ├── revert-caddy-config.sh        # docker exec -> podman exec
│   ├── validate-stack.sh             # NEW: validates the stack definition with the local engine's tooling
│   └── copy-volumes-to-podman.sh     # NEW: one-off cold copy of the Docker volumes, with checksums
└── host/
    ├── jordylab-backup-diff.service  # runs as the deploy account, podman exec, no docker.service
    ├── jordylab-backup-full.service  # same
    └── 99-jordylab-ports.conf        # NEW: net.ipv4.ip_unprivileged_port_start=80

.claude/hooks/
├── pre_bash_guard.py                 # also blocks `podman compose down -v`, like the Docker form
└── tests/guard-cases.sh              # matching block/pass cases

.github/workflows/deploy-prod-compose.yml   # host calls already go through the scripts; header comments updated

docs/
├── runbook.md                        # §3, §5, §10, §11, §15, §16, §19, §22, §24: Podman commands; new switch and fallback section
├── status.md                         # row and end-goal entry for this feature
README.md                             # architecture drawing (Mermaid): container engine
AGENTS.md                             # Infrastructure and Deploys paragraphs
.claude/skills/jordylab-ops/SKILL.md  # commands and host facts
.claude/agents/jordylab-devops.md     # mirrored in .opencode/agents/jordylab-devops.md if it names Docker
specs/020-compose-deployment/spec.md  # pointer: Fixed Decision 3 is superseded by spec 024
```

**Structure Decision**: Infrastructure as code in the existing `deploy/compose/` folder, so production keeps one definition file and one set of host scripts. The two new scripts live next to the existing ones. The workflow needs no step changes because its host calls already go through the scripts. Application code, the local development files and the end-to-end stack are untouched (out of scope in the spec).

## Complexity Tracking

> Fill ONLY if Constitution Check has violations that must be justified

No violations. Two deliberate differences from the simplest possible move are recorded here because a reader may wonder:

| Choice | Why | Simpler alternative rejected because |
|--------|-----|--------------------------------------|
| Rootless instead of rootful Podman | Removes the root-equivalent `docker` group, and rootless Podman adds no firewall rules, so `ufw` stays in charge. It is also how Podman runs locally | Rootful is closest to today's setup, but it keeps the root-equivalent deploy account and its published ports are filtered outside `ufw`'s input rules (research R1, R5) |
| Cold copy of the volumes instead of a dump and restore | Keeps the Postgres cluster identity, so pgBackRest continues its stanza and point-in-time restore works across the switch with no stanza upgrade | A dump and restore creates a new cluster identity and forces a stanza upgrade, which splits the restore history (research R6) |
