# Tasks: Production on Podman

**Input**: Design documents from `specs/024-podman-on-vps/`

**Prerequisites**: [plan.md](plan.md) (required), [spec.md](spec.md) (required), [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/), [quickstart.md](quickstart.md)

**Tests**: Not requested as test-writing tasks (no application code changes). Verification comes from the checks in [quickstart.md](quickstart.md) (Q1 to Q11 and the production checks), which the tasks reference by number and which are recorded in its "Results log". A task is checked only after its check was run.

**Organization**: As in spec 020, grouped by plan phase first (setup files, rehearsal, before the window, the window, after the window, end of the soak period), then by user story inside each phase. Tasks marked **OWNER APPROVAL** change the VPS, a GitHub setting, a pull request or data, and must not start until the owner says yes in chat. Host commands are operator steps: the owner runs them, or the devops agent runs them after the owner's yes.

**Commits**: every commit names the ticket (JOR-99) and ends with `Refs: 024/T###`.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an unfinished task)
- **[Story]**: the user story from spec.md (US1 to US5)
- `[x]` marks finished work

## Phase 1: Setup (repository files, no host change)

**Purpose**: write the changed and new files on this Mac. Nothing touches the VPS.

- [x] T001 Write `deploy/compose/scripts/podman-env.sh`, a file the other scripts source: sets `XDG_RUNTIME_DIR=/run/user/$(id -u)` when empty, exports `PODMAN_COMPOSE_PROVIDER=docker-compose` and `PODMAN_COMPOSE_WARNING_LOGS=false`, prints nothing, `set -euo pipefail` safe (host contract, "Scripts' environment")
- [x] T002 [P] Edit `deploy/compose/compose.yaml`: change the five `restart: unless-stopped` lines to `restart: always`, rewrite the header comment to name Podman and `podman compose`, reword the `shm_size` comment to "the container default"; change no image, port, volume, health check, limit or environment line (host contract, "compose.yaml changes")
- [x] T003 [P] Edit `deploy/compose/scripts/deploy-release.sh`: source `podman-env.sh`, make `compose()` call `podman compose --env-file "$env_file" -f "$compose_file" "$@"`, keep `--wait --wait-timeout 600 --remove-orphans`, update the header comment. Depends on T001
- [x] T004 [P] Edit `deploy/compose/scripts/apply-caddy-config.sh`: source `podman-env.sh`, replace every `docker compose`, `docker exec` and `docker cp` with the `podman` form (host contract, "Command mapping"). Depends on T001
- [x] T005 [P] Edit `deploy/compose/scripts/revert-caddy-config.sh`: source `podman-env.sh`, replace `docker exec` with `podman exec`. Depends on T001
- [x] T006 [P] Edit `deploy/compose/host/jordylab-backup-diff.service` and `deploy/compose/host/jordylab-backup-full.service` per the host contract: remove `Requires=docker.service` and `After=docker.service`, add `User=jordylab-deploy` and `ExecStart=/bin/sh -c 'XDG_RUNTIME_DIR=/run/user/$$(id -u) exec /usr/bin/podman exec jordylab-postgres-1 pgbackrest --stanza=jordylab --type=<diff|full> backup'` (`%U` is root's id in a system unit, so the account is looked up at run time); leave both `.timer` files unchanged
- [x] T007 [P] Write `deploy/compose/host/99-jordylab-ports.conf` containing `net.ipv4.ip_unprivileged_port_start=80` with a comment naming research R4
- [x] T008 [P] Write `deploy/compose/scripts/validate-stack.sh`: runs `podman compose --env-file deploy/compose/env/prod.env.example -f deploy/compose/compose.yaml config -q` with `PODMAN_COMPOSE_PROVIDER=docker-compose`, fails with a clear message when the Docker Compose client is missing, and fails when any service other than `caddy` has a `ports` entry; `set -euo pipefail` (research R13, FR-013)
- [x] T009 [P] Write `deploy/compose/scripts/copy-volumes-to-podman.sh`: for each of `postgres-data`, `artwork`, `caddy-data`, `ntfy-cache`, streams a tar from the Docker volume (`jordylab_<volume>`) into a Podman volume of the same name through a rootless container, keeps numeric owners (`tar --numeric-owner`) and compares the owner of the top directory on both sides, so nothing is re-owned by hand, prints a sorted sha256 manifest of both sides, and exits non-zero on any difference. Refuses to run while a Docker container of the stack is running (cold copy only). Never removes or changes the Docker volumes (research R6, data model)
- [x] T010 Lint the scripts: `shellcheck deploy/compose/scripts/podman-env.sh deploy/compose/scripts/deploy-release.sh deploy/compose/scripts/apply-caddy-config.sh deploy/compose/scripts/revert-caddy-config.sh deploy/compose/scripts/validate-stack.sh deploy/compose/scripts/copy-volumes-to-podman.sh`. Depends on T001, T003, T004, T005, T008, T009
- [x] T011 Install the Docker Compose v2 client on this Mac if it is missing (a local tool, not the owner's default provider), then run `deploy/compose/scripts/validate-stack.sh` and record the result in the quickstart's Results log as Q1 (SC-009). Depends on T002, T008
- [x] T012 [P] Update `docs/runbook.md` for Podman: §3 (install Podman and the Compose client, the deploy account, subordinate ids, lingering, the sysctl, the sockets), §5, §10, §11, §15, §16, §19, §22 and §24 (every `docker` command becomes its `podman` form from the host contract), and add a new section for the switch and the way back (copy of `contracts/switch-procedure.md`) and a "Local and production differences" list with the reason for each difference (FR-014, FR-016)
- [x] T013 [P] Update `README.md` (the Mermaid architecture block, edited as text: container engine), `docs/status.md` (a row for spec 024, status "in progress") and `AGENTS.md` (the Infrastructure "Deploys" paragraph and any line that says Docker for the server), per the repository's "keep the drawing and status current" rule (FR-016)
- [x] T014 [P] Update `.claude/skills/jordylab-ops/SKILL.md` for Podman (commands, the deploy account, the host facts, the server's engine). `.claude/agents/jordylab-devops.md` and `.opencode/agents/jordylab-devops.md` hold no Docker mentions today (checked 2026-11-08): leave them unchanged unless the skill edit makes them wrong (FR-016)
- [x] T015 [P] Edit `specs/020-compose-deployment/spec.md`: add "Superseded by spec 024 for the server" to Fixed Decision 3 and to the Out of Scope line about local development, linking to `specs/024-podman-on-vps/spec.md` (FR-016)
- [x] T059 [P] Extend the destructive-command guard so it blocks the Podman forms it blocks for Docker: in `.claude/hooks/pre_bash_guard.py` block `podman compose … down -v|--volumes` and `podman-compose … down -v|--volumes` with the same message style, and add matching `block`/`pass` cases next to the Docker ones in `.claude/hooks/tests/guard-cases.sh`; run `bash .claude/hooks/tests/guard-cases.sh` and expect no failure. (Numbered out of sequence because it was added after review; it belongs in Phase 1.)
- [ ] T016 **OWNER APPROVAL**: open a draft pull request for the branch `claude/docker-podman-migration-51813c` with `.github/pull_request_template.md`, ticket JOR-99, `Refs: 024`. Do not merge it before T037. Depends on T001 to T015 and T059

**Checkpoint**: the changed files validate locally; documentation describes Podman; nothing on the VPS changed.

---

## Phase 2: Foundational (rehearsal, blocks every user story)

**Purpose**: prove the host settings and the Podman behaviours on a throwaway Ubuntu 26.04 machine before the VPS is touched (research R10). Results go into the quickstart's Results log and into the "Not yet verified" table of `research.md`.

- [ ] T017 **OWNER APPROVAL**: choose and create the rehearsal machine: a throwaway Ubuntu 26.04 LTS machine with Docker Engine installed as the starting point (a local VM or a short-lived cloud instance; the owner decides and pays). Record how it was made and when it is deleted
- [ ] T018 Apply the host contract on the rehearsal machine (packages, a normal-range `jordylab-deploy` account with subordinate ids, lingering, `podman.socket`, the restart unit, `99-jordylab-ports.conf`, `ufw` with 80 and 443) and run quickstart Q2. Decide where the Docker Compose client (the `docker-compose` executable that `podman compose` looks for) comes from, and pin its version and sha256 checksum in `contracts/host-contract.md`. Check that `sudo -u jordylab-deploy env XDG_RUNTIME_DIR=/run/user/$(id -u jordylab-deploy) podman ps` works from a non-interactive `ssh` command. If the account cannot run rootless Podman, convert it as research R9 says and note it. Record the installed versions. Depends on T007 and T017
- [ ] T019 On the rehearsal machine, start the current Docker stack from `main`'s `deploy/compose` with an environment file of dummy values (no production secret) and a local file-based pgBackRest repository in place of object storage (kept outside the repository). Fill it with generic data only: `pgbench` data in a separate database, random artwork files, no production data, no hand-written application rows. Take a full backup, write marker data A and note its time A. Depends on T017
- [ ] T020 Run quickstart Q8 on the rehearsal machine: `copy-volumes-to-podman.sh` with the Docker stack stopped; manifests equal for all four volumes; start Postgres on the copy and check the row counts, `pgbackrest check` without a stanza upgrade, and that Caddy keeps its certificate files. Depends on T009, T018, T019
- [ ] T021 Run quickstart Q3 on the rehearsal machine: pull without a registry login, build the Postgres image, `podman compose up -d --wait --wait-timeout 600` returns only when all five services are healthy, container names are `jordylab-<service>-1`. Then write marker data C under Podman and note its time C. Depends on T002, T003, T018, T020
- [ ] T022 [P] Run quickstart Q4 on the rehearsal machine: memory, shm and log limits are enforced. Depends on T021
- [ ] T023 [P] Run quickstart Q6 on the rehearsal machine: the requesting machine's address is what Caddy logs, and an external port scan shows only the public web ports. Depends on T021
- [ ] T024 [P] Run quickstart Q7 on the rehearsal machine: install the changed backup units, start the differential unit by hand, it succeeds with no Docker, and `pgbackrest info` lists the new backup. Depends on T006, T021
- [ ] T025 Run quickstart Q9 on the rehearsal machine: a point-in-time restore to time A (before the copy) and to time C (written under Podman, after the switch) both succeed and match the marker data. Depends on T019, T021, T024
- [ ] T026 Run quickstart Q5 on the rehearsal machine: reboot, and every container is up and healthy within five minutes with no login. If the containers do not return, test the alternatives in research R3 and update the research and the host contract. Depends on T021
- [ ] T027 Run quickstart Q10 on the rehearsal machine, host side only: `deploy-release.sh deploy <release>` and `rollback <release>` as the deploy account, including one forced failure (a broken health check) that must restore the previous release. Depends on T003, T021
- [ ] T028 Run quickstart Q11 on the rehearsal machine: the "inside the window" way back restores the Docker stack from its untouched volumes. Depends on T020, T026, T027
- [ ] T029 Record the rehearsal: fill the "Not yet verified" table in `specs/024-podman-on-vps/research.md` with a result per claim, correct `contracts/host-contract.md`, `contracts/switch-procedure.md` and `plan.md` wherever a result differs, and fill the Results log. Depends on T020 to T028

**Checkpoint**: every claim in research "Not yet verified" has a recorded result and the contracts match it. If any of Q3, Q5, Q8 or Q11 failed and has no fix, stop and decide with the owner.

---

## Phase 3: Before the window (no downtime; the VPS still runs Docker)

**Purpose**: make the data safe and the host ready, without stopping anything. Automatic deploys are paused from T033 until T050.

- [ ] T030 [US2] **OWNER APPROVAL**: refresh the encrypted production copy on the owner's Mac if it is older than one day, restore it into a throwaway database, and compare row counts per table with production (FR-003, SC-002). Use the procedure in `docs/runbook.md` §22
- [ ] T031 [US2] Record the baseline from production, read-only: `pgbackrest info`, row counts per table, artwork file count and checksums, the live release (`/opt/jordylab/state/current-release`), the certificate expiry. Write it in the Results log (FR-004, FR-008)
- [ ] T032 [US1] Record the baseline for the host, read-only: the external port scan (`nmap -Pn -p- 57.129.163.110` from outside the tailnet), `free -m`, `df -h`, `ufw status verbose`. Write it in the Results log (FR-006, FR-017)
- [ ] T033 [US1] **OWNER APPROVAL**: first pause automatic deploys (`gh variable set AUTO_DEPLOY --body paused --repo jordy-swinnen/JordyLab`) so no deploy can run against a half-changed host; then prepare the VPS (host contract): install Podman, `uidmap`, `passt`, `netavark`, `aardvark-dns` and the Docker Compose client (source and checksum as decided in T018); make the deploy account a normal-range account if T018 showed it must be; add subordinate ids; enable lingering, `podman.socket` and the restart unit; install `99-jordylab-ports.conf` and run `sysctl --system`. Keep the `docker` group and Docker running. Record the versions. Depends on T029
- [ ] T034 [US1] As the deploy account on the VPS, pull the pinned images and build the Postgres image with `podman compose`, so the window does not wait for them; check `df -h` still leaves room for a second copy of the data. Depends on T033
- [ ] T035 [US4] On the VPS, run `deploy/compose/scripts/validate-stack.sh` and compare its result with T011 (same engine and tooling on both machines, FR-013). Depends on T011, T034
- [ ] T036 [US1] **OWNER APPROVAL**: agree the window date and start time, the expected end (target 60 minutes), the soak end date (one week after the window), and that the owner is available throughout. Write them in the Results log. Depends on T016, T030, T034

**Checkpoint (gate for the window)**: T029's checkpoint holds; the Mac copy restores with matching row counts; the baseline is recorded; Docker still serves the site.

---

## Phase 4: The window (the site is down)

**Purpose**: switch the stack. Follow `contracts/switch-procedure.md`, Phase 2. If a step fails and cannot be fixed in 15 minutes, do T048.

- [ ] T037 [US1] **OWNER APPROVAL** (start of the window): confirm the repository variable `AUTO_DEPLOY` is still not `true` (it was paused at T033), then merge the pull request. Depends on T036
- [ ] T038 [US2] Take a final pgBackRest full backup through Docker and confirm it with `pgbackrest info` (FR-003)
- [ ] T039 [US1] Stop the Docker stack with `docker compose stop` (not `down`). Note the time: the clock starts. Depends on T038
- [ ] T040 [US2] Run `copy-volumes-to-podman.sh` on the VPS as rehearsed; the manifests must be equal for all four volumes (FR-004). Depends on T039
- [ ] T041 [US1] Copy `deploy/compose` to `/opt/jordylab/compose` (the same rsync the workflow runs), install the changed backup units, `daemon-reload`, and enable both timers. Depends on T039
- [ ] T042 [US1] As the deploy account: `podman compose --env-file /opt/jordylab/env/prod.env -f /opt/jordylab/compose/compose.yaml up -d --wait --wait-timeout 600`; all five services healthy. Depends on T040, T041
- [ ] T043 [US1] Run the production checks from the quickstart: the site over HTTPS with the existing certificate and no new issuance, an existing user signs in, an artwork file, the notification address (SC-001); the memory, shm and log limits (quickstart Q4, FR-005); and that Caddy logs the real address of a request made from outside (quickstart Q6). Depends on T042
- [ ] T044 [US2] Verify the data: row counts per table equal the baseline from T031, artwork file count and checksums equal, `pgbackrest check` passes without a stanza upgrade (SC-002). Depends on T042
- [ ] T045 [US1] **OWNER APPROVAL**: reboot the VPS and confirm every service returns healthy within five minutes with no manual step (quickstart Q5, SC-005). Depends on T043, T044
- [ ] T046 [US1] Run the external port scan and compare it with T032; the result must be identical (SC-006). Depends on T045
- [ ] T047 [US1] Close the window: stop the clock, record the length against the announced end (SC-008), and mark the Results log. Depends on T046
- [ ] T048 [US1] Only if the window cannot close: follow "The way back, inside the window" in `contracts/switch-procedure.md`, restore the previous scripts and units from the commit before the merge, revert the merge commit on `main`, keep `AUTO_DEPLOY` off, and record why. Tick it only if it was needed

**Checkpoint**: the Podman stack serves production, the data matches the baseline, a reboot recovered, and the port scan is unchanged.

---

## Phase 5: After the window

**Purpose**: prove the pipeline and the backups on Podman, then start the soak period.

- [ ] T049 [US3] Run the deploy workflow by hand with the live release as the target, then with the previous release as the target (rollback), then with the live release again (`gh workflow run deploy-prod-compose.yml`); each ends with the site healthy and no manual command on the server (quickstart Q10, SC-004). Depends on T047
- [ ] T050 [US3] Set `AUTO_DEPLOY` back to `true` (`gh variable set AUTO_DEPLOY --body true --repo jordy-swinnen/JordyLab`). Depends on T049
- [ ] T051 [US3] **OWNER APPROVAL**: disable `docker.service` and `docker.socket`, remove the deploy account from the `docker` group, and keep Docker's stopped containers and volumes untouched. Depends on T049
- [ ] T052 [US2] After the first scheduled backups run (a differential, and on Sunday a full): `pgbackrest info` lists them and the journal shows no failure (SC-007); run a point-in-time restore in a throwaway container to a time before the switch and to a time after it (SC-003, FR-008). Depends on T047
- [ ] T053 [US1] Record the first release's downtime and `free -m` after the switch next to the baseline (FR-017, SC-008). Depends on T049
- [ ] T054 [US4] Finalise the "Local and production differences" list in `docs/runbook.md` with every difference found in the rehearsal and the window, each with its reason (FR-014, SC-009). Depends on T029, T047
- [ ] T055 [US5] Update the documentation with the recorded versions and measurements (`docs/runbook.md`, `docs/status.md`, `.claude/skills/jordylab-ops/SKILL.md`), and set the status row to "soaking" with the soak end date. Depends on T053, T054

**Checkpoint (gate for the soak period)**: the workflow deploy, the rollback and the first backups succeeded.

---

## Phase 6: End of the soak period and polish

- [ ] T056 [US5] **OWNER APPROVAL** (this deletes data; ask first): one week after the window, with no fall-back, take a fresh backup and confirm it with `pgbackrest info`; remove Docker Engine, its packages, networks and volumes; check `command -v docker`, `systemctl list-unit-files 'docker*'` and `/var/lib/docker` find nothing (SC-010). Depends on T055 and the soak end date
- [ ] T057 [US5] Remove the way-back text from `docs/runbook.md` and `.claude/skills/jordylab-ops/SKILL.md`, set the `docs/status.md` row to done, and confirm `README.md` shows the final state (FR-016). Depends on T056
- [ ] T058 Final check: every success criterion SC-001 to SC-010 has a recorded result in the Results log; tick every task that was done; the owner closes JOR-99 in Kaneo. Depends on T057

---

## Dependencies & Execution Order

- **Phase 1** has no dependencies. Within it: T001 before T003 to T005; T008 and T009 before T010; T002 and T008 before T011; T001 to T015 and T059 before T016. T059 is independent of the other files.
- **Phase 2** depends on Phase 1 and **blocks every later phase**: the VPS is not touched until T029 is done.
- **Phase 3** depends on Phase 2. T030 to T032 can run in any order; T033 → T034 → T035 are in order; T036 needs the rest.
- **Phase 4** is strictly in order from T037 to T047. T048 is conditional.
- **Phase 5** depends on T047. T049 → T050 and T051; T052 waits for the first scheduled backups.
- **Phase 6** waits for the soak end date.

### User story order

US1 and US2 (both P1) share the window and are finished together at T047 (data matches the baseline, the site serves). US3 (P2) finishes at T051 and T052. US4 (P2) finishes at T054. US5 (P3) finishes at T057.

### Parallel opportunities

- Phase 1: T002 and T006 to T009 and T012 to T015 touch different files and can run together; T003 to T005 can run together once T001 exists.
- Phase 2: T022, T023 and T024 can run together after T021.
- Phase 3: T030, T031 and T032 are independent.

## Implementation Strategy

**MVP**: Phases 1 to 4 (US1 and US2). That is a working production on Podman with the data proven safe, before the pipeline and documentation work are finished. Do not skip Phase 2: the rehearsal is what makes the window safe.

**Incremental delivery**:
1. Phase 1 is reviewable on its own in the draft pull request.
2. Phase 2 can be done any day, with no effect on production.
3. Phases 3 and 4 happen on the agreed date.
4. Phase 5 follows the same day or the next. Phase 6 follows a week later.

## Notes

- Each task is committed on its own or with the tasks it shares a file with; a commit carries `Refs: 024/T###`.
- The agent never prints secrets, never opens the SOPS file, and never runs a command on the VPS without the owner's yes.
- Rehearsal data is generic and throwaway. Production data and hand-seeded application rows never go on the rehearsal machine.
