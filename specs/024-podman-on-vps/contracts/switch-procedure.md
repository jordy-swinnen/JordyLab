# Contract: The switch, the way back, and the end of the soak period

The ordered procedure for moving production from Docker Engine to Podman. Each phase has a gate: do not start the next phase until the gate holds. Host commands are operator steps. The owner approves the host change at the start of the window (FR-011). Cutover tooling is the same as spec 020's: the encrypted Mac copy, the throwaway-database restore check, and the external port scan.

## Phase 0: Before the window (no downtime)

1. Rehearse on a throwaway Ubuntu 26.04 machine: apply the [host contract](host-contract.md), run the stack, run quickstart Q2 to Q11. Record the versions installed.
2. Open the pull request with the repository changes. Do not merge yet.
3. Refresh the encrypted Mac copy if it is older than one day. Restore it into a throwaway database and compare row counts (FR-003).
4. Record the baseline: external port scan, `free -m`, `df -h`, the live release, the current backup info.
5. Write the dates: window start, expected end (target 60 minutes), soak end (one week after).

**Gate**: every row of the quickstart's rehearsal section passed; the Mac copy restores with matching row counts; the owner has read this procedure.

## Phase 1: Prepare the host (no downtime, Docker still serving)

1. Pause automatic deploys first (set `AUTO_DEPLOY` to anything but `true`), so no deploy runs against a half-changed host. Then install the packages, create or convert the deploy account, subordinate ids, lingering, the sysctl file, the socket and restart units (host contract). Do not remove the `docker` group yet: Docker's stack is still live and the old scripts still need it.
2. As the deploy account, pull the pinned backend, frontend, Caddy and ntfy images, and build the Postgres image, with `podman compose`. This moves the slow work out of the window.
3. Run `validate-stack.sh` on the host and on the Mac.

**Gate**: images present in Podman's storage; `podman compose config -q` passes; Docker still serves the site.

## Phase 2: The window

1. The owner approves the host change. Confirm `AUTO_DEPLOY` is still not `true`. Merge the pull request.
2. Take a final pgBackRest full backup through Docker. Confirm it with `pgbackrest info`.
3. Stop the Docker stack with `docker compose stop` (not `down`). The site is down from here. Start the clock.
4. Run `copy-volumes-to-podman.sh`: for each volume it streams a tar from Docker's volume into a Podman volume, sets ownership, and prints a checksum manifest of both sides. Any difference stops the script with a non-zero exit.
5. Copy the repository's `deploy/compose` folder to `/opt/jordylab/compose` (the same rsync the workflow does), and install the new backup units and the sysctl file.
6. As the deploy account: `podman compose … up -d --wait --wait-timeout 600`. Every service must report healthy.
7. Verify (quickstart, "Production checks after the switch", with Q4 and Q6): the site over HTTPS with the existing certificate and no new issuance, sign in as an existing user, an artwork file, the notification address, row counts per table equal to the baseline, `pgbackrest check`, the memory, shm and log limits, and the real client address in Caddy's log for a request from outside.
8. Reboot the VPS. Every service must come back healthy with no step (SC-005).
9. Run the external port scan and compare it with the baseline (SC-006).
10. Stop the clock. Record the window length.

**Gate for closing the window**: steps 6 to 9 all passed. If any of them fails and cannot be fixed within 15 minutes, go to "The way back".

## Phase 3: After the window

1. Run the deploy workflow once with the live release as the target. It must finish with the site healthy and no manual command on the server (SC-004). Run it a second time with the previous release to prove rollback, then again with the live release.
2. Set `AUTO_DEPLOY` back to `true`.
3. Disable `docker.service` and `docker.socket`. Remove the deploy account from the `docker` group. Keep Docker's stopped containers and volumes.
4. After the first scheduled backups run (a differential and, on Sunday, a full), check `pgbackrest info` shows the new runs and the journal shows no failure (SC-007).
5. Record the first release's downtime and the memory use after the switch (FR-017).
6. Update the documentation (FR-016): runbook, operations skill, devops agent, README drawing, status document, `AGENTS.md`, and the pointer in spec 020.

**Gate for starting the soak period**: the workflow deploy, the rollback and the first backups succeeded.

## The way back

Fall back to Docker if the window cannot close, or during the soak period.

**Inside the window (no new data written under Podman beyond the checks)**

1. `podman compose … stop` as the deploy account. Re-enable `docker.service` and `docker.socket` if they were disabled.
2. `docker compose … start` (the old containers and volumes are untouched). Wait for healthy. Run the site check.
3. Put the previous scripts and units back on the host (copy them from the commit before the merge), and revert the merge commit on `main` before `AUTO_DEPLOY` is set back to `true`: the workflow takes its host scripts from its own commit, so running it on an older target would not restore them. Record why.

**After the window (new data exists under Podman)**

1. Take a final pgBackRest full backup and a tar of the volumes from Podman.
2. Stop the Podman stack. Restore the database into Docker's `postgres-data` volume with the pgBackRest restore or `pg_restore`, and copy the artwork volume back.
3. Start the Docker stack. Check row counts against the last Podman counts.
4. Keep the Podman storage for inspection until the cause is found.

## End of the soak period

After one week without a fall-back and with the owner's approval (this deletes data):

1. Take a fresh backup and confirm it with `pgbackrest info`.
2. Remove Docker Engine, its packages, networks and volumes. Check nothing is left: no `docker` binary, no `docker` units, no `/var/lib/docker`.
3. Update the documentation that still mentions the way back.
4. Record the end date and close the ticket.
