# Tasks: Production on Docker Compose

**Input**: Design documents from `/specs/020-compose-deployment/`

**Prerequisites**: [plan.md](plan.md) (required), [spec.md](spec.md) (required), [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/), [quickstart.md](quickstart.md)

**Tests**: Not requested as test-writing tasks. Verification comes from the checks in [quickstart.md](quickstart.md), which the tasks reference by step number.

**Organization**: Grouped by plan phase first (A preparation, B cutover, C after the window), then by user story. Tasks marked **OWNER APPROVAL** change a host, the cluster, Tailscale or a GitHub secret, and must not start without Jordy's yes.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an unfinished task)
- **[Story]**: the user story from spec.md (US1 to US5)
- `[x]` marks work already done on `main`

## Phase 1: Setup (local files, no host change)

**Purpose**: write the Compose stack and its scripts on this Mac; nothing touches the VPS.

- [x] T001 Postgres base image pinned by digest and pgBackRest pinned to 2.60.0 in `deploy/compose/postgres/Containerfile`, with the config in `deploy/compose/pgbackrest/pgbackrest.conf` (merged in PR #183 and PR #186)
- [x] T002 Local point-in-time restore and backup checks recorded in `specs/020-compose-deployment/quickstart.md` (step 1, local analogue, 2026-11-02)
- [x] T003 [P] Write `deploy/compose/env/prod.env.example` with every variable name from `specs/020-compose-deployment/contracts/env-contract.md`, values empty, no secret values
- [x] T004 [P] Write `deploy/compose/compose.yaml` with six services (caddy, frontend, backend, keycloak, ntfy, postgres), `restart: unless-stopped`, health checks (backend with a 150 s start period), log limits of 10 MB by 3 files, memory limits from research R7, named volumes `postgres-data`, `artwork`, `ntfy-cache`, `caddy-data`, network `172.28.0.0/16`, and only caddy publishing 80 and 443
- [x] T005 [P] Write `deploy/compose/Caddyfile` with the matchers in `specs/020-compose-deployment/contracts/public-routes.md` (bare path and `/*` for prefix rows, `handle_path` for `/ntfy`, HTTP to HTTPS redirect, ACME e-mail from the existing cert-manager issuer)
- [x] T006 [P] Write `deploy/compose/host/jordylab-backup-full.service` and `deploy/compose/host/jordylab-backup-full.timer` for the Sunday 04:00 UTC full backup, per `specs/020-compose-deployment/contracts/backup-contract.md`
- [x] T007 [P] Write `deploy/compose/host/jordylab-backup-diff.service` and `deploy/compose/host/jordylab-backup-diff.timer` for the Monday to Saturday 03:00 UTC differential backup
- [x] T008 [P] Write `deploy/compose/scripts/render-env.sh`: decrypts `deploy/k8s/overlays/prod/secrets.sops.yaml` with `SOPS_AGE_KEY` and writes `prod.env` to standard output, failing on a missing key; `set -euo pipefail`; never prints a value
- [x] T009 [P] Write `deploy/compose/scripts/deploy-release.sh` with the `deploy <tag>` and `rollback` modes in `specs/020-compose-deployment/contracts/deploy-workflow.md`, `set -euo pipefail`
- [x] T010 Validate the Compose file: `docker compose --env-file deploy/compose/env/prod.env.example -f deploy/compose/compose.yaml config -q`; only `caddy` may publish ports (quickstart step 2). Depends on T003, T004
- [x] T011 [P] Validate the Caddyfile: `caddy validate --config deploy/compose/Caddyfile --adapter caddyfile`. Depends on T005
- [x] T012 [P] Lint the scripts: `shellcheck deploy/compose/scripts/render-env.sh deploy/compose/scripts/deploy-release.sh`. Depends on T008, T009

**Checkpoint**: the stack and scripts validate locally with dummy values.

---

## Phase 2: Foundational (blocks every user story; host work needs owner approval)

**Purpose**: prepare the VPS and prove the backup before anything is removed (plan phase A).

- [ ] T013 **OWNER APPROVAL**: add `POSTGRES_SUPERUSER_PASSWORD` to the SOPS file in IntelliJ with `SOPS_EDITOR="idea --wait" sops deploy/k8s/overlays/prod/secrets.sops.yaml`. The agent never opens or writes the value
- [x] T014 **OWNER APPROVAL**: in the Tailscale admin console (not a repo file), grant `tag:ci` `tcp:22` to `jordylab-vps`, in addition to the existing `tcp:6443`. Done by the agent at the owner's request (2026-11-03, HANDOFF-41): the grant became `"ip": ["tcp:22", "tcp:6443"]` and the policy test moved `tag:jordylab-vps:22` from `deny` to `accept`; the save passed the test
- [x] T015 **OWNER APPROVAL**: create a dedicated deploy user on the host and its SSH key; store the private key as the production environment secret with `gh secret set DEPLOY_SSH_KEY --env production --repo jordy-swinnen/JordyLab`, piped and never echoed
- [x] T016 **OWNER APPROVAL**: install Docker Engine and the Compose plugin on the host (`ssh ubuntu@57.129.163.110`). Publish no ports; k3s keeps running
- [ ] T017 Run the external port scan before cutover (FR-014), with the same command as T054: `nmap -Pn -p- 57.129.163.110` from outside the tailnet. Record every open port in the quickstart log (quickstart step 10, before cutover), including the two extra ports found earlier (31460 and 32531), so T054 can show whether they are gone. Depends on T016
- [x] T018 **OWNER APPROVAL**: create `/opt/jordylab/env` (mode 700), `/opt/jordylab/state` and `/opt/jordylab/staging` on the host, owned by the deploy user
- [x] T019 Record host and pod memory before the window: `kubectl -n jordylab top pods` and `free -m` on the host (quickstart step 13, before). Done 2026-11-03 15:35 UTC, recorded in the quickstart log
- [ ] T020 Check the timer calendar on the host: `systemd-analyze calendar 'Mon..Sat *-*-* 03:00:00'` and `systemd-analyze calendar 'Sun *-*-* 04:00:00'`. Depends on T006, T007
- [x] T021 **OWNER APPROVAL**: copy the artwork volume to `/opt/jordylab/staging/artwork` while k3s still runs. Find its host path with `kubectl -n jordylab get pvc gamecatalog-artwork -o jsonpath='{.spec.volumeName}'`, then copy from the k3s storage folder on the host
- [ ] T022 **OWNER APPROVAL**: run the old-chain point-in-time restore drill with the kept CloudNativePG manifests, to a time inside the 30-day window, then delete the drill cluster and its volume (quickstart step 1a, plan step 6a)
- [ ] T023 Tailnet SSH check: from the owner's Mac and from a CI test job, `ssh` to the host's tailnet address works (quickstart step 11, first half). Depends on T014, T015. Mac half done 2026-11-03 (`ssh ubuntu@100.93.81.41` connects key-only, host key verified against the public-IP anchor); the CI half still needs a workflow that connects with `DEPLOY_SSH_KEY` (none exists yet — see T047)
- [ ] T024 Phase A exit check: T002, T010, T016, T017, T021, T022 and T023 are done, and the results are recorded in the quickstart log. Depends on T002, T010, T016, T017, T021, T022, T023

**Checkpoint**: foundation ready. No user story work starts before this.

---

## Phase 3: User Story 1 - Production keeps serving the same site on Compose (Priority: P1) MVP

**Goal**: the cutover moves production to the Compose stack on the same VPS with the same addresses, accounts and artwork.

**Independent Test**: after cutover, load the site, sign in as an existing user and open a game with artwork. Each works with the same data as before (quickstart steps 4 and 5).

- [ ] T025 [US1] **OWNER APPROVAL**: agree the maintenance window, the abort deadline and the rebuild path with Jordy, and announce the release freeze: no release tag is pushed until T062 (plan phase B). Depends on T024.
- [ ] T026 [US1] **OWNER APPROVAL**: scale the k3s write paths to zero: `kubectl -n jordylab scale deploy/backend deploy/keycloak deploy/ntfy --replicas=0` (plan step 7). Depends on T025
- [ ] T027 [US1] Take the final dump with owners, not `--no-owner`, and encrypt it to the Mac recipient: `kubectl -n jordylab exec cnpg-cluster-1 -c postgres -- pg_dump -U postgres -d jordylab -Fc | age -r "$(cat ~/jordylab-backups/prod-2026-11-02/recipient.txt)" -o ~/jordylab-backups/cutover/jordylab-db.dump.age` (plan step 8). Depends on T026
- [ ] T028 [US1] Check the final dump: the file starts with `PGDMP`, and a restore into a throwaway container matches production row counts (quickstart steps 1 and 5). Depends on T027
- [ ] T029 [US1] Encrypt the artwork folder to the Mac recipient for the cutover backup (FR-008). The backend is stopped by T026, so the folder is stable. Read it from the k3s storage path found in T021 and write `~/jordylab-backups/cutover/artwork.tar.age` with `tar -C <k3s artwork path> -cf - . | age -r "$(cat ~/jordylab-backups/prod-2026-11-02/recipient.txt)" -o ~/jordylab-backups/cutover/artwork.tar.age`, then list the archive to check it is not empty. Depends on T021, T026
- [ ] T030 [US1] Copy the delta of the artwork files from `/opt/jordylab/staging/artwork` into the artwork volume (plan step 9). Depends on T021, T026
- [ ] T031 [US1] **OWNER APPROVAL**: stop and disable k3s on the host with `sudo systemctl disable --now k3s`. This frees ports 80 and 443 (plan step 10). Depends on T028, T030
- [ ] T032 [US1] Start Postgres with pgBackRest: `docker compose --env-file /opt/jordylab/env/prod.env -f deploy/compose/compose.yaml up -d postgres` (plan step 11). Depends on T010, T016, T018, T031
- [ ] T033 [US1] Create the `jordylab` and `keycloak` roles in the new Postgres before the restore (research R4). Depends on T032
- [ ] T034 [US1] Restore the final dump with ownership, `pg_restore -U postgres -d jordylab -Fc` into the postgres container, without `--no-owner`. Compare row counts with the dump. Depends on T033
- [ ] T035 [US1] Render the environment file and write it to the host with mode 600, without printing it: `deploy/compose/scripts/render-env.sh | ssh <deploy user>@<host> 'install -m 600 /dev/stdin /opt/jordylab/env/prod.env'`. Depends on T008, T018
- [ ] T036 [US1] Start the application services: `docker compose --env-file /opt/jordylab/env/prod.env -f deploy/compose/compose.yaml up -d --wait backend keycloak ntfy frontend caddy` (plan step 12). Caddy obtains the certificate on the first request. Depends on T004, T005, T032, T034, T035
- [ ] T037 [US1] Smoke checks (quickstart steps 4 and 5): HTTPS, the OpenID discovery JSON, `/apis` reaching the frontend, sign-in as an existing user, one artwork file, and an ntfy publish. Depends on T036
- [ ] T038 [US1] Confirm DNS is unchanged: `dig +short jordylab.be @1.1.1.1` returns `57.129.163.110`. Depends on T037
- [ ] T039 [US1] Record the window start, end and downtime in quickstart step 14 and in the log. If an abort is needed, follow the rebuild path in research R14 instead. Depends on T038

**Checkpoint**: the site is served by the Compose stack, and the data matches the final dump. The stack is not shippable until US2 is done, because backups are not running yet.

---

## Phase 4: User Story 2 - Data is provably safe before and after the move (Priority: P1)

**Goal**: backups run to object storage, and a point-in-time restore works after the move.

**Independent Test**: a point-in-time restore to a chosen time inside the window returns the state at that time (quickstart step 6), and the timers show backups on schedule (quickstart step 12).

- [ ] T040 [US2] Create the pgBackRest stanza and check it: `docker compose exec -T postgres pgbackrest --stanza=jordylab stanza-create`, then `... check` (plan step 11, `contracts/backup-contract.md`). Depends on T034
- [ ] T041 [US2] Take the first full backup: `docker compose exec -T postgres pgbackrest --stanza=jordylab --type=full backup`. Depends on T040
- [ ] T042 [US2] Confirm the S3 connection with a real repository: `pgbackrest --stanza=jordylab info` lists the full backup under `/pgbackrest` in `jordylab-cnpg-backups`. If TLS fails, check the host CA store, not the config. Depends on T041
- [ ] T043 [P] [US2] Install and enable the timers: copy `deploy/compose/host/jordylab-backup-*.service` and `.timer` to `/etc/systemd/system/`, run `sudo systemctl daemon-reload`, then `sudo systemctl enable --now jordylab-backup-full.timer jordylab-backup-diff.timer`. Depends on T006, T007, T042
- [ ] T044 [US2] Point-in-time restore test (quickstart step 6): choose a time inside the window and after the first full backup, restore into a throwaway container with no published ports, compare a changed table at that time, then remove the container. Depends on T041
- [ ] T045 [US2] Verify the timers after their first scheduled run: `systemctl list-timers 'jordylab-backup-*'` and `pgbackrest --stanza=jordylab info` show a full and a diff within schedule (quickstart step 12). Depends on T043
- [ ] T046 [US2] **OWNER DECISION**: keep or delete the encrypted Mac copies after T044 and T045 pass (plan step 24). Depends on T044, T045

**Checkpoint**: backups run on schedule, and a point-in-time restore has been proven on the new stack.

---

## Phase 5: User Story 3 - Operators deploy and roll back without touching the server (Priority: P2)

**Goal**: a release deploys through the workflow, and a rollback is the same workflow with an earlier tag.

**Independent Test**: deploy a release candidate, deploy an earlier tag, and force a failing deploy. Each finishes with the state file naming the live tag (quickstart step 9).

- [ ] T047 [US3] Write `.github/workflows/deploy-prod-compose.yml` with steps 1 to 8 of `specs/020-compose-deployment/contracts/deploy-workflow.md`. It is not wired into `release.yml` until T062. Depends on T009
- [ ] T048 [US3] **OWNER APPROVAL**: store the deploy key as the production secret (see T015) and check it with `gh secret list --env production --repo jordy-swinnen/JordyLab`. Depends on T015, T047
- [ ] T049 [US3] **OWNER APPROVAL**: run the workflow by hand with a release candidate tag: `gh workflow run deploy-prod-compose.yml --repo jordy-swinnen/JordyLab -f version=<rc tag>`, then approve it in the production environment. Depends on T036, T048
- [ ] T050 [US3] Check the state file on the host: `/opt/jordylab/state/current-release` names the new tag, and the external check passes (quickstart step 9, first check). Depends on T049
- [ ] T051 [US3] **OWNER APPROVAL**: run the workflow with the previous tag and check the state file names it (quickstart step 9, second check). Depends on T050
- [ ] T052 [US3] Automatic rollback check: run the workflow with a tag whose images do not exist. The job fails, and the previous tag is live again with the state file naming it (quickstart step 9, third check). Depends on T051

**Checkpoint**: deploy and rollback work through the workflow without server access.

---

## Phase 6: User Story 4 - Only the expected doors are open (Priority: P2)

**Goal**: only 80 and 443 are public, and the host's administrative access goes over the tailnet.

**Independent Test**: an external port scan shows only the expected ports, and SSH works over the tailnet before public SSH is closed (quickstart steps 10 and 11).

- [ ] T053 [US4] Check the host firewall and Docker's published ports: `sudo ufw status verbose` shows only 22, 80, 443 and the tailnet rule, and `docker ps --format '{{.Names}} {{.Ports}}'` shows only caddy publishing 80 and 443 (research R8). Depends on T036
- [ ] T054 [US4] External port scan from outside the tailnet: `nmap -Pn -p- 57.129.163.110`, the same scan as T017. Only 80 and 443 answer, plus 22 until T056. Record in the quickstart log (quickstart step 10). Depends on T053
- [ ] T055 [US4] Check tailnet SSH again from the owner's Mac and from a CI test job, now that the deploy key is in place (quickstart step 11, second check). Depends on T023, T048
- [ ] T056 [US4] **OWNER APPROVAL**: close public SSH on port 22 in the host firewall, only after T055 passes. Depends on T054, T055
- [ ] T057 [US4] From outside the tailnet, confirm port 22 is closed and that tailnet SSH still works (quickstart step 11). Depends on T056

**Checkpoint**: the external scan shows only the expected doors.

---

## Phase 7: User Story 5 - Runbooks match the new stack (Priority: P3)

**Goal**: the runbook, the operations skill and the devops agent describe the Compose stack, and the Kubernetes material stays as history.

**Independent Test**: following the rewritten runbook for a reboot and a restore works step by step.

- [ ] T058 [US5] Rewrite `docs/runbook.md` for Compose (install, deploy, rollback, backups, restore, reboot), and state that each release has a downtime of a few seconds (FR-019). Move the k3s sections to `docs/runbooks/vps-k3s-deployment/` as history. Depends on T036
- [ ] T059 [US5] Write the Postgres major-version upgrade procedure (FR-015) in `docs/runbook.md`: dump and restore into the new major image, how backups start again on the new major, and how to go back to the old image. It must exist before the next major version bump; it does not block the move. Depends on T058
- [ ] T060 [US5] Rewrite `.claude/skills/jordylab-ops/SKILL.md` for Compose: where it runs, safety rules, and the command reference. Depends on T058
- [ ] T061 [P] [US5] Update `.claude/agents/jordylab-devops.md` and `.opencode/agents/jordylab-devops.md` so their bodies match (the dual-agent rule). Depends on T058
- [ ] T062 [US5] **OWNER APPROVAL**: switch the deploy job in `.github/workflows/release.yml` to `deploy-prod-compose.yml`, and retire the kubectl steps in `deploy-prod.yml` (plan step 22). Only after T049, T051, T052 and T058 pass. This task ends the release freeze that T025 starts. Depends on T049, T051, T052, T058

**Checkpoint**: the runbook matches the running system.

---

## Phase N: Polish & cross-cutting checks

**Purpose**: checks that cover several stories and record the result.

- [ ] T063 Forced certificate renewal on a test hostname with the Let's Encrypt staging endpoint, with the stored certificate removed so Caddy must renew (quickstart step 7). Depends on T037
- [ ] T064 **OWNER APPROVAL**: reboot test: `sudo reboot`, then every service is healthy within 5 minutes and the quickstart step 4 checks pass without a manual step (quickstart step 8). Depends on T037, T043
- [ ] T065 Memory after a 30-minute soak: `docker stats --no-stream` and `free -m`, compared with T019 (quickstart step 13). Depends on T019, T037
- [ ] T066 Update the quickstart log table with every run, its date, its commit and its result, and the window record from T039 (quickstart steps 14 and log). Depends on T039, T046, T054, T057, T063, T064, T065

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: no host dependency; can start now
- **Foundational (Phase 2)**: depends on Setup; blocks all user stories
- **US1 (Phase 3)**: depends on Foundational; this is the cutover itself
- **US2 (Phase 4)**: depends on the restored database (T034); its timers need the first full backup
- **US3 and US4 (Phases 5 and 6)**: depend on the stack running (T036)
- **US5 (Phase 7)**: depends on the stack running (T036); T062 also depends on US3 passing
- **Polish (Phase N)**: depends on the stories it checks

### Story Dependencies

- **US1 (P1)**: starts after Foundational. Cannot be shipped alone, because the stack without backups fails US2.
- **US2 (P1)**: starts after the restore in US1 (T034).
- **US3 (P2)**: starts after the stack runs (T036).
- **US4 (P2)**: starts after the stack runs (T036); T056 needs tailnet SSH confirmed.
- **US5 (P3)**: starts after the stack runs (T036); T062 needs US3 passing; it ends the release freeze that T025 starts.

### Parallel Opportunities

- Setup: T003 to T009 touch different files and can run together (marked `[P]`); T010 to T012 wait for them.
- US2: T043 can run alongside T044.
- US5: T061 can run alongside T060.

---

## Implementation Strategy

### MVP is not enough on its own

The MVP is Phase 1, Phase 2 and US1, but US1 without US2 leaves the site live with no backups, which the spec does not allow. The smallest shippable slice is Phases 1 to 4.

### Incremental delivery

1. Phase 1 (local validation), then Phase 2 (owner approvals and host preparation)
2. US1 cutover, then US2 backups and the point-in-time check: the first shippable stack
3. US3 deploy and rollback, then US4 port and SSH hardening
4. US5 runbooks, then Polish checks
5. After each step, record the result in the quickstart log

---

## Notes

- Each `OWNER APPROVAL` task is a host, cluster, Tailscale or secret change. Do not run it without Jordy's yes in the conversation.
- The rebuild path (research R14) is not a task. It runs only if the abort deadline in T025 passes.
- The encrypted Mac copy stays until T046 is decided.
- The tasks reference `quickstart.md` steps by number; keep the numbers in step.
