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

- [x] T013 **OWNER APPROVAL**: add `POSTGRES_SUPERUSER_PASSWORD` to the SOPS file in IntelliJ with `SOPS_EDITOR="idea --wait" sops deploy/k8s/overlays/prod/secrets.sops.yaml`. The agent never opens or writes the value. Done by the owner 2026-11-03; committed with their approval (HANDOFF-40) the same day: the decrypt integrity check passes and the key name is present under `jordylab-secrets` stringData. Note: merging it to main triggers a Build and an automatic k3s deploy, which applies the new key to the `jordylab-secrets` Secret (unused by the backend until the Compose stack needs it)
- [x] T014 **OWNER APPROVAL**: in the Tailscale admin console (not a repo file), grant `tag:ci` `tcp:22` to `jordylab-vps`, in addition to the existing `tcp:6443`. Done by the agent at the owner's request (2026-11-03, HANDOFF-41): the grant became `"ip": ["tcp:22", "tcp:6443"]` and the policy test moved `tag:jordylab-vps:22` from `deny` to `accept`; the save passed the test
- [x] T015 **OWNER APPROVAL**: create a dedicated deploy user on the host and its SSH key; store the private key as the production environment secret with `gh secret set DEPLOY_SSH_KEY --env production --repo jordy-swinnen/JordyLab`, piped and never echoed
- [x] T016 **OWNER APPROVAL**: install Docker Engine and the Compose plugin on the host (`ssh ubuntu@57.129.163.110`). Publish no ports; k3s keeps running
- [x] T017 Run the external port scan before cutover (FR-014), with the same command as T054: `nmap -Pn -p- 57.129.163.110` from outside the tailnet. Record every open port in the quickstart log (quickstart step 10, before cutover), including the two extra ports found earlier (31460 and 32531), so T054 can show whether they are gone. Depends on T016. Done 2026-11-03, in the quickstart log: 22, 80, 443 plus the two BUG-078 NodePorts, nothing else. The spec's bare command is not viable against this host (unprivileged it crawls for hours; as root at speed it trips upstream mitigation), so the commands of record are a root sweep at `--max-rate 200` plus a NodePort-range connect sweep at `--max-rate 50`; T054 runs the same pair. BUG-078's root cause is now confirmed (traefik's LoadBalancer nodePorts)
- [x] T018 **OWNER APPROVAL**: create `/opt/jordylab/env` (mode 700), `/opt/jordylab/state` and `/opt/jordylab/staging` on the host, owned by the deploy user
- [x] T019 Record host and pod memory before the window: `kubectl -n jordylab top pods` and `free -m` on the host (quickstart step 13, before). Done 2026-11-03 15:35 UTC, recorded in the quickstart log
- [x] T020 Check the timer calendar on the host: `systemd-analyze calendar 'Mon..Sat *-*-* 03:00:00'` and `systemd-analyze calendar 'Sun *-*-* 04:00:00'`. Depends on T006, T007. Verified 2026-11-03 on the host with the unit files' exact forms (both carry `UTC`): both normalize cleanly — diff next elapses Wed 2026-11-04 03:00:00 UTC, full next elapses Sun 2026-11-08 04:00:00 UTC. The timers are not installed until T043
- [x] T021 **OWNER APPROVAL**: copy the artwork volume to `/opt/jordylab/staging/artwork` while k3s still runs. Find its host path with `kubectl -n jordylab get pvc gamecatalog-artwork -o jsonpath='{.spec.volumeName}'`, then copy from the k3s storage folder on the host
- [x] T022 **OWNER APPROVAL**: run the old-chain point-in-time restore drill with the kept CloudNativePG manifests, to a time inside the 30-day window, then delete the drill cluster and its volume (quickstart step 1a, plan step 6a). Run 2026-11-03 (details in the quickstart 1a row); accepted by the owner the same day: no table lost data (the loss check passed on all 100 tables), and the 8 Keycloak tables with extra rows match two clients and one user deleted after the restore point plus expired sessions — accepted without a row-by-row re-run
- [x] T023 Tailnet SSH check: from the owner's Mac and from a CI test job, `ssh` to the host's tailnet address works (quickstart step 11, first half). Depends on T014, T015. Done 2026-11-03, both halves in the quickstart log: Mac — `ssh ubuntu@100.93.81.41` key-only, host key verified against the public-IP anchor; CI — the `tailscale-ssh-smoke` workflow passed (run 41666201394): `tag:ci` runner, `jordylab-deploy`, `DEPLOY_SSH_KEY`, pinned ed25519 fingerprint
- [x] T024 Phase A exit check: T002, T010, T016, T017, T021, T022 and T023 are done, and the results are recorded in the quickstart log. Depends on T002, T010, T016, T017, T021, T022, T023. Confirmed 2026-11-03: all seven ticked, each with a quickstart log row (steps 1, 1a, 2, 10, 11 and the host prep row)

**Checkpoint**: foundation ready. No user story work starts before this.

---

## Phase 3: User Story 1 - Production keeps serving the same site on Compose (Priority: P1) MVP

**Goal**: the cutover moves production to the Compose stack on the same VPS with the same addresses, accounts and artwork.

**Independent Test**: after cutover, load the site, sign in as an existing user and open a game with artwork. Each works with the same data as before (quickstart steps 4 and 5).

- [x] T025 [US1] **OWNER APPROVAL**: agree the maintenance window, the abort deadline and the rebuild path with Jordy, and announce the release freeze: no release tag is pushed until T062 (plan phase B). Depends on T024. Agreed 2026-11-03 19:11 UTC: the window starts tonight once the spec PR is merged and its automatic k3s deploy is green; the abort deadline is **start + 2.5 h** — if the stack is not live and smoke-checked by then, stop and follow the R14 rebuild path (reinstall k3s from the kept runbooks, apply the kept manifests, restore from the Mac backup; the data is safe in the final dump plus the Mac copies either way). Release freeze announced: no release tag until T062 (none pending). Window records stay on this branch until the end-of-window PR, so nothing pushes to main while k3s is down.
- [x] T026 [US1] **OWNER APPROVAL**: scale the k3s write paths to zero: `kubectl -n jordylab scale deploy/backend deploy/keycloak deploy/ntfy --replicas=0` (plan step 7). Depends on T025. Done 2026-11-03 20:09 UTC (window start)
- [x] T027 [US1] Take the final dump with owners, not `--no-owner`, and encrypt it to the Mac recipient (plan step 8). Depends on T026. Done 2026-11-03: 5.2 MB at ~/jordylab-backups/cutover/jordylab-db.dump.age (the recipient is the SOPS age key pair, verified by public-key derivation); starts with PGDMP
- [x] T028 [US1] Check the final dump: the file starts with `PGDMP`, and a restore into a throwaway container matches production row counts (quickstart steps 1 and 5). Depends on T027. Done 2026-11-03: throwaway container `jor31-cutover-check` (labelled, no published ports, removed after), restore clean, 123/123 row counts identical to the frozen production baseline
- [x] T029 [US1] Encrypt the artwork folder to the Mac recipient for the cutover backup (FR-008). Done 2026-11-03: ~/jordylab-backups/cutover/artwork.tar.age contains exactly the empty directory, matching the source volume (0 files; listed back through age) The backend is stopped by T026, so the folder is stable. Read it from the k3s storage path found in T021 and write `~/jordylab-backups/cutover/artwork.tar.age` with `tar -C <k3s artwork path> -cf - . | age -r "$(cat ~/jordylab-backups/prod-2026-11-02/recipient.txt)" -o ~/jordylab-backups/cutover/artwork.tar.age`, then list the archive to check it is not empty. Depends on T021, T026
- [x] T030 [US1] Copy the delta of the artwork files from `/opt/jordylab/staging/artwork` into the artwork volume (plan step 9). Depends on T021, T026. Done 2026-11-03: the delta is verifiably none (0 files on both sides; the backend was stopped since T026, so nothing changed after T021's checksums)
- [x] T031 [US1] **OWNER APPROVAL**: stop and disable k3s on the host with `sudo systemctl disable --now k3s`. This frees ports 80 and 443 (plan step 10). Depends on T028, T030. Done 2026-11-03 (owner-approved; AUTO_DEPLOY paused at the same moment). Caveat found at T036: the service stop left the iptables DNAT and shims alive (BUG-083) - the full cleanup needed k3s-killall.sh
- [x] T032 [US1] Start Postgres with pgBackRest (plan step 11). Depends on T010, T016, T018, T031. Done 2026-11-03: the image built on the host from /opt/jordylab/compose after BUG-081's digest fix; healthy. The env file was rendered first (T035 pulled ahead of T032 because the compose command consumes it)
- [x] T033 [US1] Create the `jordylab` and `keycloak` roles in the new Postgres before the restore (research R4). Depends on T032. Done 2026-11-03: both roles created with login, credentials read from prod.env host-side and never printed
- [x] T034 [US1] Restore the final dump with ownership, `pg_restore -U postgres -d jordylab -Fc` into the postgres container, without `--no-owner`. Compare row counts with the dump. Depends on T033. Done 2026-11-03: restore clean, ownership verified (finance.article owned by jordylab), 123/123 row counts identical to the baseline
- [x] T035 [US1] Render the environment file and write it to the host with mode 600, without printing it. Depends on T008, T018. Done 2026-11-03: 44 lines (20 literals + 24 secrets), mode 600, owned by jordylab-deploy, written through sudo on the ubuntu session (the Mac has no deploy-user key); rendered with the previous session's PyYAML venv because the Mac's pip is broken
- [x] T036 [US1] Start the application services (plan step 12). Depends on T004, T005, T032, T034, T035. Done 2026-11-03: all six services healthy under JORDYLAB_RELEASE=sha-9cc83c4f636f6e6a309cf571d9402619ed413c18 (the PR #199 merge - the same code that was live on k3s); two mid-window fixes: BUG-082 (healthcheck) and BUG-083 (the old k3s data plane still holding the public ports until k3s-killall.sh ran); the state files stay empty until the first deploy-release.sh run (T049)
- [x] T037 [US1] Smoke checks (quickstart steps 4 and 5): HTTPS, the OpenID discovery JSON, `/apis` reaching the frontend, sign-in as an existing user, and an ntfy publish. Done 2026-11-03 ~20:44 UTC: smoke-check.sh all green (home 200, api 401, discovery 200), /apis 200 html from the frontend, http->https 308 permanent, ntfy publish 200 (message qCcKWrsmmnmX), the artwork volume migrated with its 0 entries, and the owner signed in and reached their own data. Artwork check changed by owner decision (2026-11-03): Artwork check changed by owner decision (2026-11-03): production's artwork volume is empty, so the smoke check verifies the volume migration with whatever it contains (nothing), and artwork loading is verified after the first real scan on the new stack — never with seeded data (Validation Data rule). Depends on T036
- [x] T038 [US1] Confirm DNS is unchanged: `dig +short jordylab.be @1.1.1.1` returns `57.129.163.110`. Depends on T037. Done 2026-11-03: 57.129.163.110
- [x] T039 [US1] Record the window start, end and downtime in quickstart step 14 and in the log. Depends on T038. Done 2026-11-03: start 20:09 UTC (T026), live again ~20:45 UTC, downtime ~36 minutes; the abort deadline (22:39 UTC) was never approached; no abort, no R14 path needed

**Checkpoint**: the site is served by the Compose stack, and the data matches the final dump. The stack is not shippable until US2 is done, because backups are not running yet.

---

## Phase 4: User Story 2 - Data is provably safe before and after the move (Priority: P1)

**Goal**: backups run to object storage, and a point-in-time restore works after the move.

**Independent Test**: a point-in-time restore to a chosen time inside the window returns the state at that time (quickstart step 6), and the timers show backups on schedule (quickstart step 12).

- [x] T040 [US2] Create the pgBackRest stanza and check it (plan step 11, `contracts/backup-contract.md`). Depends on T034. Done 2026-11-03 20:32 UTC - after BUG-084's ca-certificates fix (the base image shipped no CA bundle, so the S3 endpoint's normal Sectigo chain failed verification); stanza-create and check both clean against the real S3 repository
- [x] T041 [US2] Take the first full backup. Depends on T040. Done 2026-11-03: full backup 20261103-203343F (48.9 MB database, 10.7 MB compressed set in repo1), completed in 57 s
- [x] T042 [US2] Confirm the S3 connection with a real repository: `pgbackrest --stanza=jordylab info` lists the full backup under `/pgbackrest` in `jordylab-cnpg-backups`. If TLS fails, check the host CA store, not the config. Depends on T041. Done 2026-11-03: info shows status ok, the full backup and WAL archive min/max 000000010000000000000001..05 (the TLS failure was the container's missing CA bundle, BUG-084, not the host's)
- [x] T043 [P] [US2] Install and enable the timers (owner-approved host change): units copied from /opt/jordylab/compose/host to /etc/systemd/system, daemon-reload, both timers enabled. Depends on T006, T007, T042. Done 2026-11-03 20:38 UTC: `systemctl list-timers` shows the differential next at Wed 2026-11-04 03:00 UTC and the full at Sun 2026-11-08 04:00 UTC
- [x] T044 [US2] Point-in-time restore test (quickstart step 6): choose a time inside the window and after the first full backup, restore into a throwaway container with no published ports, compare a changed table at that time, then remove the container. Depends on T041. Done 2026-11-03, target 2026-11-03 20:38 UTC: the restore replayed to the target and promoted; `finance.article` shows 823 rows at that time (822 in the 20:09 baseline - the FNA ingestion wrote one article mid-window, and the restore has exactly its value at the chosen time). Bonus finding: production-now equals the restore exactly - the owner's post-cutover sign-in wrote no new session rows because the restored Keycloak keys and sessions kept the old SSO cookie valid. Two gotchas recorded in the quickstart log: the throwaway needs the S3 keys for WAL replay (archive-get), and a target only minutes old needs `pg_switch_wal()` first because a quiet database does not fill its 16 MB WAL segment
- [ ] T045 [US2] Verify the timers after their first scheduled run: `systemctl list-timers 'jordylab-backup-*'` and `pgbackrest --stanza=jordylab info` show a full and a diff within schedule (quickstart step 12). Depends on T043
- [ ] T046 [US2] **OWNER DECISION**: keep or delete the encrypted Mac copies after T044 and T045 pass (plan step 24). Depends on T044, T045

**Checkpoint**: backups run on schedule, and a point-in-time restore has been proven on the new stack.

---

## Phase 5: User Story 3 - Operators deploy and roll back without touching the server (Priority: P2)

**Goal**: a release deploys through the workflow, and a rollback is the same workflow with an earlier tag.

**Independent Test**: deploy a release candidate, deploy an earlier tag, and force a failing deploy. Each finishes with the state file naming the live tag (quickstart step 9).

- [x] T047 [US3] Write `.github/workflows/deploy-prod-compose.yml` with steps 1 to 8 of `specs/020-compose-deployment/contracts/deploy-workflow.md`. It is not wired into `release.yml` until T062. Depends on T009. Written 2026-11-03, actionlint/shellcheck clean (the same lint command CI runs). Decisions: the input is named `target` like deploy-prod.yml (T049's command updated to `-f target=`); PyYAML is installed into a venv on the runner for render-env.sh; deploy/compose is rsync-mirrored (`--delete`) to /opt/jordylab/compose; the Postgres image is **built on the host** from that copy, not pushed to GHCR (compose.yaml already declares the pinned-digest build, Postgres changes are rare and deliberate, and no new registry pipeline is worth it); SSH as `jordylab-deploy` with `DEPLOY_SSH_KEY` and the pinned ed25519 fingerprint; the images-exist check refuses a tag before the host is touched (T052's path); shares the `deploy-production` concurrency group with deploy-prod.yml; the two compose scripts got the exec bit (they were 644, unlike deploy/scripts). Open for T049/T062: the retagging that the contract assigns to `release.yml` has no owner since spec 019 removed it — a NEW release tag has no GHCR images until that wiring exists; and render-env needs `POSTGRES_SUPERUSER_PASSWORD`, so T013's secrets edit must be on main (and in the tag) before any real run
- [x] T048 [US3] **OWNER APPROVAL**: store the deploy key as the production secret (see T015) and check it with `gh secret list --env production --repo jordy-swinnen/JordyLab`. Depends on T015, T047. Verified 2026-11-03: `DEPLOY_SSH_KEY` is listed (set by T015), and the tailscale-ssh-smoke run used it successfully (run 41666201394)
- [X] T049 [US3] **OWNER APPROVAL**: run the workflow by hand with a full commit sha on `main`: `gh workflow run deploy-prod-compose.yml --repo jordy-swinnen/JordyLab -f target=<full sha>`. A release tag also works, but a sha is recommended: its `sha-<sha>` images exist from the Build, so no retag is needed (the gap on T047 is resolved). Depends on T036, T048. Note: the images must contain the T013 secrets key Done 2026-11-03, run 41728815560: target `76f38366` (a commit sha) deployed through the workflow, all steps green (sops install, env render, host deploy, smoke checks), `current-release` = `sha-76f38366…`, all three services healthy, `/actuator/info` reports the same commit, public 200/401/200.
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
- [x] T055 [US4] Check tailnet SSH again from the owner's Mac and from a CI test job, now that the deploy key is in place (quickstart step 11, second check). Depends on T023, T048. Done 2026-11-03 19:23 UTC: Mac — `ssh ubuntu@100.93.81.41` over the tailnet; CI — the tailscale-ssh-smoke workflow dispatched on demand from main (run 41702833214), `jordylab-deploy` + `DEPLOY_SSH_KEY` + pinned fingerprint. Recorded in the quickstart log
- [X] T056 [US4] **OWNER APPROVAL**: close public SSH on port 22 in the host firewall, only after T055 passes. Depends on T054, T055 Done 2026-11-03: precondition verified (tailnet SSH from the Mac; CI in run 41724836036), the broad 22/tcp LIMIT rule deleted, public 22 closed from the Mac, tailnet SSH still works (Tailscale SSH needs no ufw rule).
- [ ] T057 [US4] From outside the tailnet, confirm port 22 is closed and that tailnet SSH still works (quickstart step 11). Depends on T056

**Checkpoint**: the external scan shows only the expected doors.

---

## Phase 7: User Story 5 - Runbooks match the new stack (Priority: P3)

**Goal**: the runbook, the operations skill and the devops agent describe the Compose stack, and the Kubernetes material stays as history.

**Independent Test**: following the rewritten runbook for a reboot and a restore works step by step.

- [X] T058 [US5] Rewrite `docs/runbook.md` for Compose (install, deploy, rollback, backups, restore, reboot), and state that each release has a downtime of a few seconds (FR-019). Move the k3s sections to `docs/runbooks/vps-k3s-deployment/` as history. Depends on T036
- [X] T059 [US5] Write the Postgres major-version upgrade procedure (FR-015) in `docs/runbook.md`: dump and restore into the new major image, how backups start again on the new major, and how to go back to the old image. It must exist before the next major version bump; it does not block the move. Depends on T058
- [X] T060 [US5] Rewrite `.claude/skills/jordylab-ops/SKILL.md` for Compose: where it runs, safety rules, and the command reference. Depends on T058
- [X] T061 [P] [US5] Update `.claude/agents/jordylab-devops.md` and `.opencode/agents/jordylab-devops.md` so their bodies match (the dual-agent rule). Depends on T058
- [X] T062 [US5] **OWNER APPROVAL**: decide whether automatic deploys return, and if so add a `deploy` job to `.github/workflows/build.yml` that calls `deploy-prod-compose.yml` with `target: ${{ github.sha }}` (after T049, T051, T052 and T058 pass). The k3s side is already gone: `deploy-prod.yml`, the old `build.yml` deploy job and `release.yml` no longer exist, and `deploy-prod-compose.yml` is the only deploy path, run by hand with a sha or tag. Automatic deploys are off and no workflow reads `AUTO_DEPLOY`. Depends on T049, T051, T052, T058 Done 2026-11-03: the owner approved automatic deploys; `build.yml` now calls `deploy-prod-compose.yml` with `target: github.sha` and `automatic: true`, gated by `AUTO_DEPLOY`, and the workflow reads `state/current-release` to skip a superseded target. The first automatic deploy after enabling the variable proves it.

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
