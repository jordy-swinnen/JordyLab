# Feature Specification: Production on Docker Compose

**Feature Branch**: `jordyswinnen/jor-31-production-on-docker-compose-instead-of-k3s`

**Created**: 2026-11-02

**Status**: Draft

**Linear**: JOR-31 (commits from the latest renumber carry `Refs: 020`; earlier commits carry `Refs: 017` or `Refs: 019`)

**Input**: User description: "Specify the draft in specs/_drafts/compose-deployment/ (spec-draft.md and research.md). Production moves from k3s to Docker Compose on the same VPS, with a local encrypted backup on the owner's Mac as the safety net. Decisions recorded in the draft are fixed inputs, not open questions."

## Overview

Production runs on a single VPS. Today it runs on a one-node Kubernetes cluster (k3s). That cluster adds upgrades, an ingress controller, certificate management, a database operator and a backup plugin, plus manifests and a long runbook, and none of it gives failover or scheduling on one machine. This feature moves production to a single Docker Compose stack on the same VPS, so that production shares its file layout with local development. The move keeps the same public addresses, the same data, and the same release process from the user's side.

The owner has fixed the decisions listed under "Fixed Decisions" below. This spec turns them into testable requirements.

## Fixed Decisions

These were decided by the owner on 2026-11-02. They are inputs to this spec, not questions for planning.

1. **Same host.** No second VPS. The move happens on the existing VPS.
2. **Reverse proxy: Caddy.** Automatic TLS in the proxy.
3. **Container engine: Docker Engine** with the Compose plugin on the server. Local development is moving from Podman to Docker as a separate change, not part of this feature.
4. **Downtime per release is accepted** at a few seconds, because a single host restarts the backend on each release. Deploys run on release tags only, not on merges to the main branch.
5. **Port finding left to the move.** Two extra public TCP ports were found on the VPS. The k3s configuration is not patched for them. The Compose host gets the same port scan before cutover.
6. **Rebuild check dropped.** The full rebuild from an empty VPS is not a success criterion.
7. **Public SSH closes after Tailscale SSH works** for the owner and the CI runner (tag `tag:ci`). The change to the host firewall needs the owner's approval at cutover.
8. **Backups: pgBackRest.** Point-in-time restore to an S3-compatible object store, with the same 30-day retention as today.
9. **Postgres major version pinned.** An upgrade procedure is written before the next major version bump. This does not block the move.
10. **Local safety net.** An encrypted backup of production is held on the owner's Mac. It is restored after the Compose stack is live.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Production keeps serving the same site on Compose (Priority: P1)

The owner moves production from the cluster to the Compose stack on the same VPS. Visitors reach the same addresses and sign in with the same accounts, and the library and artwork are all still there.

**Why this priority**: This is the whole purpose of the feature. Nothing else matters if the site or the data is lost.

**Independent Test**: After cutover, load the site, sign in as an existing user, open a game with artwork. Each works and shows the data that was there before the move.

**Acceptance Scenarios**:

1. **Given** the Compose stack is live on the VPS, **When** a visitor opens the site's main address, **Then** the site loads over HTTPS with a valid certificate.
2. **Given** the cutover is complete, **When** an existing user signs in, **Then** the user reaches the same account and data as before the move.
3. **Given** the cutover is complete, **When** a visitor requests an existing artwork file, **Then** it is returned unchanged.

---

### User Story 2 - Data is provably safe before and after the move (Priority: P1)

Before anything from k3s is removed, the owner holds a copy of production that has been restored and checked. After cutover, backups keep running, and a point-in-time restore is possible.

**Why this priority**: The move has no old node to fall back to, so the only way back is the backup. It must be proven before it is needed.

**Independent Test**: Restore the encrypted local copy into a throwaway database and compare row counts with production. Then, on the new stack, restore to a chosen time and check the data.

**Acceptance Scenarios**:

1. **Given** an encrypted backup held on the owner's Mac, **When** it is restored into a throwaway database, **Then** the row counts for every table match production.
2. **Given** the Compose stack is live, **When** a point-in-time restore is run to a chosen time in the last 30 days, **Then** the restored data matches the state at that time.
3. **Given** the Compose stack is live, **When** the scheduled backups run, **Then** base backups are written to object storage on the daily and weekly schedules and older backups expire after 30 days.

---

### User Story 3 - Operators deploy and roll back without touching the server (Priority: P2)

A release is deployed from the existing deploy workflow onto the Compose host. A bad release is rolled back by running the same workflow with an earlier release tag.

**Why this priority**: Deploys and rollbacks are how the site changes. They must keep working without manual server access.

**Independent Test**: Deploy a release tag, then deploy an earlier tag, both through the workflow. Each finishes with the site healthy and no manual command on the server.

**Acceptance Scenarios**:

1. **Given** a release tag, **When** the deploy workflow runs, **Then** the new images start, the health checks pass, and the workflow reports success.
2. **Given** a deployed release that fails its health checks, **When** the deploy workflow runs, **Then** the previous release is restored automatically.
3. **Given** a working release, **When** the owner runs the workflow with an earlier release tag, **Then** that earlier release is deployed and serving.

---

### User Story 4 - Only the expected doors are open (Priority: P2)

The host exposes only the public web ports. Administrative access goes through the private network.

**Why this priority**: A port scan found two unexplained public ports. The new host must not repeat that, and administrative access should not depend on a public port.

**Independent Test**: Scan the new host from outside. Only the expected public ports answer. SSH works over the private network before public SSH is closed.

**Acceptance Scenarios**:

1. **Given** the new host is live, **When** it is scanned from outside, **Then** only the public web ports and any ports the owner has approved answer.
2. **Given** the owner and the CI runner can reach the host over the private network, **When** public SSH is closed at cutover, **Then** both can still connect.

---

### User Story 5 - Runbooks match the new stack (Priority: P3)

The runbook, the operations skill and the devops agent describe the Compose stack. The Kubernetes notes stay in the repository as history.

**Why this priority**: The current runbook is the only guide for operating production. It must be correct after the move, but it does not block the move.

**Independent Test**: Follow the rewritten runbook for a reboot and a restore on the new stack. Each step works as written.

**Acceptance Scenarios**:

1. **Given** the new runbook, **When** a reader follows the reboot procedure, **Then** every step works on the Compose stack.
2. **Given** the repository after the move, **When** a reader looks for the Kubernetes material, **Then** it is kept in place and marked as history.

---

### Edge Cases

- What happens when the cutover restore fails halfway? The way back is a rebuild from the encrypted Mac backup, because there is no old node. The maintenance window must allow for that rebuild.
- What happens to point-in-time restores to a time before cutover? They need the old backup chain in object storage, which pgBackRest cannot read. That chain is kept until its 30-day retention ends, and restoring to a time before cutover uses the old tooling.
- What happens when the host reboots during a deploy? The stack must come back on its own, and the deploy must report the failure instead of stopping silently.
- What happens to the Keycloak and Postgres roles after restore? The cutover dump is made with owners and privileges, and the roles are created before the restore. The encrypted copy taken on 2026-11-02 has no owners, so if it is used, the roles and schema ownership must be recreated before Keycloak starts.
- What happens when the artwork volume is empty or missing at restore? The restore reports it, because an empty volume is a valid state but a missing one is not.
- What happens if the Postgres container restarts often before cutover? The cause must be understood before the maintenance window, because the final dump depends on a stable database.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Production MUST run as one stack described by a single definition file and an environment and secrets file, with every image pinned to a release tag.
- **FR-002**: Production MUST serve the same public addresses and paths as today, including the authentication paths and the notification path.
- **FR-003**: TLS certificates MUST be issued and renewed automatically, and renewal MUST be verified by a forced-renewal test.
- **FR-004**: Database backups MUST be archived continuously to object storage in the same region as today, with daily and weekly base backups.
- **FR-005**: Backups MUST keep 30 days of restorable history, matching today's retention.
- **FR-006**: Before the old cluster is removed, an encrypted backup of production MUST be held on the owner's Mac and MUST be restored and checked against production in a throwaway database.
- **FR-007**: The cutover MUST take a final backup after writes stop, restore it on the new stack, and verify the restored data before traffic returns.
- **FR-008**: The artwork files MUST survive the cutover unchanged and MUST be included in the encrypted backup on the Mac. The APK volume is not migrated: it was removed from production with the native app (spec 018).
- **FR-009**: Services MUST restart on their own after a host reboot, and each service MUST have a health check.
- **FR-010**: Container logs MUST have size limits, and memory limits MUST fit within the host's memory.
- **FR-011**: The deploy workflow MUST connect over the private network, wait for the new release to report healthy, and restore the previous release automatically if it does not.
- **FR-012**: A rollback MUST be possible by running the deploy workflow with an earlier release tag, without manual access to the server.
- **FR-013**: Public SSH MUST be closed after private access is verified for the owner and the CI runner, and the change MUST be approved by the owner at cutover.
- **FR-014**: The host MUST expose only the public web ports and any ports the owner has approved. The same external port scan MUST run on the new host before cutover.
- **FR-015**: The Postgres major version MUST be pinned, and an upgrade procedure MUST be written before the next major version bump.
- **FR-016**: Secrets MUST stay encrypted in the repository and be decrypted only during deploy.
- **FR-017**: Keycloak MUST keep running on the new stack until the separate authentication change removes it.
- **FR-018**: The runbook, the operations skill and the devops agent MUST be rewritten for the Compose stack, and the Kubernetes material MUST stay in the repository as history.
- **FR-019**: A downtime of a few seconds per release is accepted, and the release procedure MUST state it.

### Key Entities *(include if feature involves data)*

- **Backup set**: A copy of production used for recovery. There are two kinds: the encrypted copy on the owner's Mac, and the chain of base backups and change logs in object storage. Each set records when it was taken and what it contains.
- **Release**: A version of production identified by a release tag. A deploy or a rollback selects a release.
- **Maintenance window**: The period from the final backup to the restored stack serving traffic. Its duration is recorded for each cutover.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: After cutover, existing users can sign in and reach their data, and the site loads over HTTPS with a valid certificate.
- **SC-002**: The encrypted Mac backup restores into a throwaway database with row counts matching production for every table, before the old cluster is removed.
- **SC-003**: A point-in-time restore to a chosen time in the last 30 days succeeds on the new stack.
- **SC-004**: A deploy and a rollback each complete through the deploy workflow with no manual access to the server.
- **SC-005**: After a host reboot, every service is healthy again with no manual step.
- **SC-006**: An external port scan of the new host shows only the public web ports and any approved ports.
- **SC-007**: The downtime of each release and the length of the maintenance window are recorded, and the host's memory use is recorded before and after the move.

## Out of Scope

- High availability, more than one server, or a change of hosting provider.
- Kubernetes for anything else.
- The local development move from Podman to Docker, which is a separate change.
- The separate authentication change that removes Keycloak.
- Changes to the continuous deployment flow beyond pointing it at the Compose host. The deploy-on-green-main work is a separate ticket.

## Assumptions

- The Compose host is the same VPS in the same datacentre, with the same public address, so DNS does not change.
- The owner is available for the maintenance window and for the approval of the SSH change.
- The encrypted Mac backup, taken on 2026-11-02, is used only as a safety net. The final backup in the maintenance window replaces it for the restore.
- Object storage in the same region as today remains available for backups throughout the move.
- The old backup chain stays in object storage until its 30-day retention ends, and nothing deletes it early.
- Users accept a few seconds of downtime per release, as decided by the owner.
- The artwork volume is small enough to copy within the maintenance window (it was about 4 KB on 2026-11-02).
- The authentication change that removes Keycloak, and the deploy-on-green-main change, are scheduled after this feature, so this feature keeps Keycloak and the current deploy trigger.
