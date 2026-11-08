# Feature Specification: Production on Podman

**Feature Branch**: `claude/docker-podman-migration-51813c`

**Created**: 2026-11-08

**Status**: Draft

**Ticket**: JOR-99 (Kaneo)

**Input**: User description: "Replace Docker Engine with Podman on the production VPS. Production runs the Docker Compose stack on the VPS under Docker Engine, while local development already runs its containers on Podman. Replacing Docker Engine with Podman on the VPS puts local and production on the same container engine, so fewer differences between them need managing. The exact approach (rootless or rootful, how Compose is run) is for the spec to settle. Context: Compose deploy in deploy/compose, workflow .github/workflows/deploy-prod-compose.yml, runbook docs/runbook.md. AGENTS.md says production runs Docker Compose on the VPS (spec 020); local containers run on Podman (compose.yaml, dev Postgres). specs/020-compose-deployment is the Compose deploy this would change; the switch needs its own spec or an amendment to 020. Related: JOR-31 (Compose instead of k3s, Done), JOR-41 (BUG-018, backend tests on Podman, Done)."

## Overview

Production runs on a single VPS as one container stack (spec 020), and that stack runs on Docker Engine. Local development, the end-to-end test stack and the owner's checks all run on Podman. Two engines mean two sets of behaviour: a stack change can pass on the owner's Mac and still behave differently on the server, and the owner has to know the quirks of both.

This feature moves the production stack from Docker Engine to Podman on the same VPS, so that local development and production run the same container engine. From a visitor's side nothing changes: same addresses, same accounts, same data, same release process. From the owner's side there is one engine to learn, test against and write runbooks for.

This feature makes one choice differently from spec 020. Spec 020 fixed Docker Engine as the server engine and expected local development to move to Docker. This spec supersedes that for the server: the engine on the server becomes Podman, and local development stays on Podman.

How the switch is built (rootless or rootful containers, how the stack definition is run, how services start at boot) is for the plan to decide. The requirements below must hold whichever way it goes.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Production keeps serving the same site on Podman (Priority: P1)

The owner switches production from Docker Engine to Podman on the same VPS. Visitors reach the same addresses over HTTPS, existing users sign in with the same accounts, and the library, artwork and notification history are all still there.

**Why this priority**: This is the whole purpose of the feature. Nothing else matters if the site or the data is lost or changed.

**Independent Test**: After the switch, load the site, sign in as an existing user, open a game with artwork, and confirm the push-notification address still answers. Each works and shows the data that was there before the switch.

**Acceptance Scenarios**:

1. **Given** the stack is running on Podman, **When** a visitor opens the site's main address, **Then** the site loads over HTTPS with a valid certificate.
2. **Given** the switch is complete, **When** an existing user signs in, **Then** the user reaches the same account and data as before the switch.
3. **Given** the switch is complete, **When** a visitor requests an existing artwork file, **Then** it is returned unchanged.
4. **Given** the switch is complete, **When** the services are listed, **Then** every service of the stack is running and healthy, and none of them is run by Docker Engine.

---

### User Story 2 - Data is provably safe through the switch (Priority: P1)

Before the switch, the owner holds a fresh backup of production that has been restored and checked. After the switch, the database keeps being backed up on the same schedules, and a restore to a chosen time still works, including a time from before the switch.

**Why this priority**: The switch moves the database, the artwork and the certificates from one engine's storage to another's. The backup is the only way back if the move goes wrong, so it must be proven before it is needed.

**Independent Test**: Restore the pre-switch backup into a throwaway database and compare row counts with production. After the switch, run a restore to a chosen time before the switch and one after it, and check the data.

**Acceptance Scenarios**:

1. **Given** a fresh backup taken before the switch, **When** it is restored into a throwaway database, **Then** the row counts for every table match production.
2. **Given** the stack is running on Podman, **When** the scheduled backups run, **Then** base backups and change logs are written to object storage on the same daily and weekly schedules, and backups older than 30 days expire.
3. **Given** the stack is running on Podman, **When** a point-in-time restore is run to a time before the switch and to a time after it, **Then** both restored states match what production held at those times.
4. **Given** the switch is complete, **When** the artwork, the database and the certificates are compared with their state before the switch, **Then** nothing is missing or changed.

---

### User Story 3 - Deploys, rollbacks and reboots keep working untouched (Priority: P2)

A release is deployed from the existing deploy workflow onto the Podman host. A bad release is rolled back by running the same workflow with an earlier release. When the VPS reboots, the stack comes back on its own.

**Why this priority**: These are how the site changes and recovers. They must keep working without manual access to the server, but they can only be proven once the stack runs on Podman.

**Independent Test**: Deploy a release, then deploy an earlier one, both through the workflow. Reboot the VPS. Each ends with every service healthy and no manual command on the server.

**Acceptance Scenarios**:

1. **Given** a release, **When** the deploy workflow runs, **Then** the new images start, the health checks pass, and the workflow reports success.
2. **Given** a deployed release that fails its health checks, **When** the deploy workflow runs, **Then** the previous release is restored automatically.
3. **Given** a working release, **When** the owner runs the workflow with an earlier release, **Then** that earlier release is deployed and serving.
4. **Given** the stack is running on Podman, **When** the VPS reboots, **Then** every service is healthy again with no manual step.
5. **Given** the stack is running on Podman, **When** the nightly backup jobs fire, **Then** they run to completion without Docker Engine on the host.

---

### User Story 4 - Local and production behave alike (Priority: P2)

The owner can check a change to the production stack on their Mac with the same engine that runs in production, and expect the same result on the server. The few differences that remain are written down.

**Why this priority**: This is the reason for the feature, but it only pays off once the server is on Podman and the difference list has been written.

**Independent Test**: Take the production stack definition and validate it on the owner's Mac with the same engine and tooling as the server. Start the stack's services locally with their health checks. Compare the result with the server.

**Acceptance Scenarios**:

1. **Given** a change to the production stack definition, **When** the owner validates it on their Mac, **Then** the validation uses the same engine and tooling as the server and reports the same result the server would.
2. **Given** the repository after the switch, **When** a reader looks for what still differs between local and production, **Then** a short written list names each remaining difference and why it exists.

---

### User Story 5 - Documentation and the way back are current (Priority: P3)

The runbook, the operations skill, the devops agent, the architecture drawing and the status document describe Podman. Docker Engine is removed from the host once Podman has proven itself. Until then, a written way back to Docker exists.

**Why this priority**: Operations rely on these documents, and a leftover engine on the host is a quiet security and upkeep cost. Neither blocks the switch itself.

**Independent Test**: Follow the rewritten runbook for a reboot and a restore on the Podman host. Each step works as written. Check the host for Docker Engine after the soak period.

**Acceptance Scenarios**:

1. **Given** the updated runbook, **When** a reader follows the reboot and restore procedures, **Then** every step works on the Podman host.
2. **Given** the repository after the switch, **When** a reader looks for the engine decision, **Then** spec 020 points to this spec as the one that supersedes its engine choice.
3. **Given** the soak period has ended without a rollback, **When** the host is checked, **Then** Docker Engine is removed and nothing on the host depends on it.
4. **Given** the switch window is about to start, **When** the owner reads the plan, **Then** a written procedure exists for returning to Docker Engine and restoring service from the pre-switch backup.

---

### Edge Cases

- What happens when the switch fails halfway? The stack must be brought back under Docker Engine, or rebuilt from the pre-switch backup, with the site down for as short a time as possible. The way back is written down before the window starts, and the window length allows for it.
- What happens to the data that lives in Docker's storage? The database files, the artwork, the proxy's certificates and the notification cache are stored by Docker Engine and are not visible to Podman. They are moved or restored deliberately and then checked. They are never assumed to appear.
- What happens to the TLS certificates if the certificate store is lost? New certificates are issued automatically, but issuing too many in a short time hits the certificate authority's rate limits. The certificate store is therefore moved along with the data, or the number of re-issues is counted before the window.
- What happens to the public ports? The proxy must still answer on the standard web ports, and the host must still show only the approved ports from outside. Podman and Docker add their own firewall rules differently, so the external port scan is run again on the new setup.
- What happens when the host reboots? Docker Engine restarts containers through its always-running service. Podman has no such service, so the restart behaviour is rebuilt and proven by a reboot test, not assumed.
- What happens to the host's backup jobs? The two timer units are tied to the Docker service and call Docker directly. They must be moved to Podman and tested, or backups stop silently after the switch.
- What happens to the deploy workflow and the scripts? They call Docker by name for starting the stack, running commands in a container and copying files into one. Each call is replaced and re-proven. The CI side, which builds and publishes images, is not part of this.
- What happens if an automatic deploy fires during the switch? Automatic deploys are paused for the window and resumed afterwards, so a release cannot land on a half-switched host.
- What happens to features the stack definition uses, such as "start only after the other service is healthy", memory limits and log size limits? Each is checked under Podman. If one behaves differently, the difference is fixed or written down before the switch.
- What happens to pulling images from the registry? The deploy user must be able to pull the pinned images under Podman, with the same registry access as today.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: After the switch, every service of the production stack MUST run on Podman, and no production service MUST depend on Docker Engine.
- **FR-002**: Production MUST serve the same public addresses and paths as before the switch, over HTTPS with a valid certificate, with the same accounts and data.
- **FR-003**: Before the switch begins, a fresh backup of production MUST exist and MUST have been restored into a throwaway database and checked against production by row counts.
- **FR-004**: The database files, the artwork, the proxy's certificates and the notification cache MUST survive the switch unchanged, and each MUST be checked afterwards against its state before the switch.
- **FR-005**: Services MUST restart on their own after a host reboot, and each service MUST keep a health check, a memory limit and a log size limit equal to today's.
- **FR-006**: The host MUST expose only the public web ports and any ports the owner has approved. The external port scan MUST be run after the switch and MUST match the result before it.
- **FR-007**: Database backups MUST keep running on the same schedules (continuous archiving, daily and weekly base backups) to the same object storage, with the same 30 days of restorable history, and the host's scheduled backup jobs MUST run without Docker Engine.
- **FR-008**: A point-in-time restore MUST work after the switch to a time before the switch and to a time after it.
- **FR-009**: The deploy workflow MUST keep its current behaviour on the Podman host: it connects over the private network, waits for the new release to report healthy, restores the previous release automatically if it does not, and allows a rollback by running it with an earlier release, all without manual access to the server.
- **FR-010**: Every image MUST stay pinned to a release tag or a content digest, and secrets MUST stay encrypted in the repository and be decrypted only during deploy.
- **FR-011**: Automatic deploys MUST be paused from the start of host preparation until the first successful deploy after the switch, and the host change itself MUST be approved by the owner at the start of the window.
- **FR-012**: A written procedure MUST exist before the window starts for returning to Docker Engine and restoring service, and it MUST stay valid until the soak period ends.
- **FR-013**: The production stack definition and the deploy scripts MUST be validated with the same engine and tooling the owner uses locally, so that errors can be found on the owner's Mac before a deploy.
- **FR-014**: The repository MUST contain a short written list of the differences that remain between local and production, each with its reason.
- **FR-015**: After a soak period without a rollback, Docker Engine MUST be removed from the host, and the removal MUST be confirmed by checking that nothing on the host still uses it.
- **FR-016**: The runbook, the operations skill, the devops agent, the architecture drawing and the status document MUST be updated for Podman, and spec 020 MUST point to this spec as superseding its engine decision.
- **FR-017**: The length of the switch window, the downtime of the first release after the switch, and the host's memory use before and after MUST be recorded.

### Key Entities *(include if feature involves data)*

- **Container engine**: The program on a machine that runs the stack's containers. Production moves from Docker Engine to Podman. Local development already uses Podman.
- **Stack**: The set of services that make up production (the proxy, the frontend, the backend, the database with its backup tooling, and the notification server), described by one definition file and an environment file.
- **Persistent data set**: The data that must outlive containers: the database files, the artwork, the proxy's certificates and the notification cache. It is moved or restored during the switch and checked afterwards.
- **Backup set**: A copy of production used for recovery: the pre-switch backup that is restored and checked, and the chain of base backups and change logs in object storage.
- **Switch window**: The period from the start of the owner's approved host change to the stack serving traffic again on Podman. Its length is recorded.
- **Soak period**: The time after the switch during which the way back to Docker stays available, before Docker Engine is removed.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: After the switch, existing users can sign in and reach their data, the site loads over HTTPS with a valid certificate, and every service reports healthy.
- **SC-002**: The pre-switch backup restores into a throwaway database with row counts matching production for every table, and after the switch the artwork file count and checksums match their state before it.
- **SC-003**: A point-in-time restore to a chosen time before the switch and one after it both succeed.
- **SC-004**: A deploy and a rollback each complete through the deploy workflow with no manual access to the server.
- **SC-005**: After a host reboot, every service is healthy again within five minutes with no manual step.
- **SC-006**: An external port scan after the switch shows exactly the same open ports as before it.
- **SC-007**: The scheduled backup jobs complete on the first night after the switch without Docker Engine on the host, and the next scheduled base backups appear in object storage.
- **SC-008**: The switch window is recorded and ends within the length the owner announced before starting, and the downtime of each release after the switch is no longer than the few seconds accepted in spec 020.
- **SC-009**: A change to the production stack definition can be validated on the owner's Mac with the same engine and tooling as the server, and the written difference list contains every remaining difference found during the switch.
- **SC-010**: At the end of the soak period, the host has no Docker Engine installed and no document describing the server still tells the reader to use it.

## Out of Scope

- High availability, more than one server, or a change of hosting provider.
- A change of local development, the end-to-end test stack, or the CI image build. Local development already runs on Podman.
- Replacing any component of the stack itself (the proxy, the database, the backup tool, the notification server).
- Changes to the deploy trigger or the release process beyond what the engine change needs.
- Rebuilding the VPS from scratch, or changing its operating system.

## Assumptions

- Local development, the end-to-end test stack and the owner's checks already run on Podman, as `AGENTS.md` and the local stack's definition show. The decision in spec 020 that Docker Engine runs on the server and that local development moves to Docker is superseded by this feature for the server, and local development stays on Podman.
- The VPS stays the same host with the same public address, so DNS does not change.
- The switch happens in a planned maintenance window with the whole site down for its length. The owner is available for it and approves the host change at the start. The target length is 60 minutes, which is a working figure for planning, not a commitment from the owner.
- The published images run unchanged under Podman, so no application image is rebuilt for this feature. The database image, which is built for the stack, is rebuilt only if its build file needs it.
- The privilege model (rootless or rootful containers), how the stack definition is run, and how services start at boot are chosen in planning. The requirements here hold whichever is chosen.
- The host's operating system offers a supported release of Podman and its Compose tooling. Planning confirms this and records the versions.
- Object storage and the existing backup chain are independent of the container engine, so the chain continues through the switch without a break.
- The soak period is one week without a rollback, then Docker Engine is removed. The owner can lengthen or shorten it.
- The ticket system for this repository is now Kaneo. Files in the repository that still name Linear are not changed by this feature.
