# Quickstart: validating the Compose production stack

**Spec**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md)

Run these in order. Each step names what it proves and the result that counts as passing. Record the date, the commit and the result in the log at the end. Do not mark a step passed unless it was run.

## 1. Restore the encrypted Mac copy into a throwaway Postgres

Proves: the backup is restorable and matches production (SC-002, FR-006).

- Start a throwaway `pgvector/pgvector:pg16` container with a random superuser password and **no published ports**. Use a label such as `purpose=prod-restore-check`.
- Decrypt the dump to standard output with `age -d` and pipe it into `pg_restore --no-owner --no-privileges` in the container (the 2026-11-02 copy has no owners).
- Pass: exit code 0, no error lines in the log, and identical per-table row counts with production.
- Remove the container by its name.

## 1a. Old-chain point-in-time restore (before the window)

Proves: the backups taken before the move restore through the kept CloudNativePG manifests (backup contract, "The old chain"; plan step 6a).

- With k3s still running and the owner's yes, run the kept restore drill to a time inside the 30-day window.
- Pass: the drill cluster becomes Ready, its row counts are no greater than production's, and the drill cluster and its volume are deleted afterwards.

## 2. Validate the Compose files

Proves: the stack definition parses with the names in the environment template (contracts/env-contract.md).

- `docker compose --env-file deploy/compose/env/prod.env.example -f deploy/compose/compose.yaml config -q`
- Pass: exit code 0. Only `caddy` lists published ports (80, 443). No other service publishes a port.

## 3. Bring up the stack (on the host, after the window)

Proves: every service starts healthy and the dependency order holds (FR-009).

- `docker compose --env-file /opt/jordylab/env/prod.env up -d --wait`
- `docker compose ps`: every service `healthy` (or `running` for ntfy and Caddy if they have no check).
- Pass: exit code 0 within the wait timeout. The backend's health check waits out its 150 s start period first.

## 4. Public routes

Proves: the public addresses match the contract (contracts/public-routes.md, FR-002).

- `curl -sI https://jordylab.be/` → 200 over HTTPS, with a valid certificate.
- `curl -s https://jordylab.be/auth/realms/jordylab/.well-known/openid-configuration` → JSON containing the issuer `https://jordylab.be/auth/realms/jordylab`.
- `curl -s https://jordylab.be/.well-known/assetlinks.json` → the same body as before the move.
- `curl -sI https://jordylab.be/ntfy/` → the ntfy response (the prefix is removed before forwarding).
- `curl -sI https://jordylab.be/apis` → the frontend's response, not the backend's (whole-segment matching).
- Pass: each path reaches its target; `http://jordylab.be/` → 301 to HTTPS.

## 5. Sign-in and data (smoke)

Proves: existing accounts and data work after cutover (User Story 1).

- Sign in at `https://jordylab.be` as an existing user. Pass: the user reaches their own data.
- The artwork volume migrated with whatever it contains (currently 0 files, checksums in the T021 row). Artwork loading is verified after the first real scan on the new stack, never with seeded data (owner decision 2026-11-03).
- Row counts for the `jordylab` database against the final dump. Pass: identical.

## 6. Point-in-time restore

Proves: the restorable window works (SC-003, FR-005).

- Choose a UTC time inside the window and after the first full backup. Write the time down before the restore.
- Restore into a throwaway container with no published ports (contracts/backup-contract.md, "Restore").
- Pass: the row count for a table that changed around the chosen time matches its value at that time, and the container is removed afterwards.

## 7. Certificate renewal

Proves: renewal is automatic and verified (FR-003).

- Forced renewal test on a test hostname with the Let's Encrypt staging endpoint, and the stored certificate removed so Caddy must renew. Pass: a new certificate is issued and served, and production stays up.
- Record the Caddy log lines for the issuance.

## 8. Reboot

Proves: services come back without manual steps (SC-005, FR-009).

- `sudo reboot`, then wait up to 5 minutes.
- `docker compose ps` shows every service healthy, and step 4's checks pass again.
- Pass: no manual command was needed.

## 9. Deploy and rollback through the workflow

Proves: deploy and rollback need no server access (SC-004, FR-011, FR-012, contracts/deploy-workflow.md).

- Run the deploy workflow with a release candidate tag. Pass: the job succeeds, the state file names the new tag as current, and the smoke check passes.
- Run the workflow with the previous tag. Pass: the job succeeds, the state file names the previous tag as current, and the smoke check passes.
- Automatic rollback: run the workflow with a tag whose image does not exist. Pass: the job fails, the previous tag is live again, and the state file names it as current.

## 10. External port scan

Proves: only the expected doors are open (SC-006, FR-014). The same scan was run on the old host (research R12). It also runs on the new host before cutover, with the same command (tasks T017 and T054).

- `nmap -Pn <public IPv4>` from outside the tailnet.
- Pass: only 80 and 443 open, plus 22 until public SSH is closed. No other TCP port answers.

## 11. Private administration

Proves: SSH works over the tailnet before public SSH is closed (User Story 4, FR-013).

- From the owner's Mac and from a CI test job, connect to the host's tailnet address. Pass: both connect.
- Only after both pass and the owner approves: close public port 22, then check from outside that port 22 is closed.

## 12. Backup timers

Proves: backups run on schedule (FR-004).

- `systemctl list-timers 'jordylab-backup-*'` shows both timers with a next run.
- `pgbackrest --stanza=jordylab info` lists the full backup and the differential backups.
- Pass: the newest backup of either kind is within 24 hours, and the newest full backup is within 7 days.

## 13. Memory

Proves: the host uses less memory than on k3s (SC-007).

- Before cutover, record `kubectl top` for the namespace and `free -m` on the host.
- After the stack is up, record `docker stats --no-stream` totals and `free -m` after a 30-minute soak.
- Pass: the total after is lower than the total before.

## 14. Maintenance-window record

Proves: the downtime and the window length are recorded (SC-007).

- Record the start and end time of the window, the downtime per release from the deploy job logs, and the abort deadline that was agreed before the window.

## Log

| Step | Date (UTC) | Commit | Result | Notes |
|------|------------|--------|--------|-------|
| 1 | 2026-11-02 | `2627044` | the Mac copy restored, row counts identical (122 tables), volume archive listed | this was the 2026-11-02 copy with no owners |
| 1 (local analogue) | 2026-11-02 (UTC) | branch tip | pgBackRest 2.60.0 installs on `pgvector/pgvector:pg16` (PostgreSQL 16.15). On a local repository: stanza created, `check` passes, full and differential backups run, and a point-in-time restore to a time between two batches returns batches A and B (20 rows) and no batch C. The restored copy is not in recovery. | The S3 repository was not exercised. With placeholder keys, pgBackRest reached `s3.gra.io.cloud.ovh.net`, so the config parses, but TLS verification failed (self-signed certificate in the chain, from this Mac's network). Verify the S3 connection on the host. |
| 2 (local) | 2026-11-03 (UTC) | branch tip | `docker compose ... config -q` exits 0 with the example names. Only `caddy` publishes ports (80, 443). `caddy validate` (caddy 2.11.7) passes. `shellcheck` is clean for both scripts. | the three JORDYLAB_RELEASE blank-value warnings are expected: the release tag is set by deploy-release.sh |
| render (local, synthetic) | 2026-11-03 (UTC) | branch tip | render-env.sh on a synthetic SOPS file (test key, dummy values) prints exactly the names of the example file. Missing key, empty required value and quote in a value each fail with the variable name only. | not run on the real secrets file: POSTGRES_SUPERUSER_PASSWORD is not in it yet (T013) |
| 1a, 6a (old chain) | 2026-11-03 (UTC) | `67f5247` | the drill cluster restored to 2026-11-02 12:00 UTC became ready (cluster healthy, 100 tables). No table has fewer rows than production. 8 Keycloak tables have more (`keycloak.client`, `client_attributes`, `client_scope_client`, `offline_client_session`, `redirect_uris`, `scope_mapping`, `user_entity`, `web_origins`): the drill has two clients (`jordylab-mobile`, `mobile-release-ci`) and one user that production no longer has, plus expired sessions. This fits data that changed after the restore point, but it is not confirmed row by row. The drill cluster and its PVC were deleted. Production was unchanged: healthy, 35 backups, none from the drill. | The criterion 'no greater than production' is not strictly met. Owner decision 2026-11-03: accepted — no table lost data (the loss check passed on all 100 tables), and the 8 extra-row Keycloak tables match two clients (`jordylab-mobile`, `mobile-release-ci`) and one user deleted after the restore point plus expired sessions, which is what a genuine point-in-time restore contains. Accepted without a row-by-row re-run. |
| host prep (T015, T016, T018, T021) | 2026-11-03 (UTC) | `67f5247` | deploy user `jordylab-deploy` with its key in `DEPLOY_SSH_KEY`; Docker Engine 29.9.2 and Compose 5.7.0 installed, no containers; k3s pods unchanged; `/opt/jordylab/{env,state,staging}` created; artwork copied: the k3s volume is empty (0 files) and the checksums match | the artwork check in step 5 needs an artwork file first |
| policy (T014, step 11 prerequisite) | 2026-11-03 (UTC) | branch tip | Tailscale policy saved in the admin console (JSON editor, at the owner's request): the `tag:ci` → `tag:jordylab-vps` grant now allows `tcp:22` next to `tcp:6443`, and the policy test expects `tag:jordylab-vps:22` accepted instead of dropped; 6443 stays accepted, 80 and 443 stay denied. The save passed the policy test. | the console reformatted the file on save (the one-line grant became multi-line); the grant/test comments and the header date note were updated to stay truthful |
| 11 (first half, Mac) | 2026-11-03 15:35 (UTC) | branch tip | SSH over the tailnet from the Mac works: `ssh ubuntu@100.93.81.41` connects key-only (host `vps-c2987aab`). The tailnet host key was pinned to `known_hosts` only after all three key fingerprints matched the already-trusted public-IP anchor (`57.129.163.110`). | — |
| 11 (second half, CI) | 2026-11-03 15:41 (UTC) | branch tip | The Tailscale SSH smoke workflow (`.github/workflows/tailscale-ssh-smoke.yml`) passed: a `tag:ci` runner joined the tailnet and connected as `jordylab-deploy` to `100.93.81.41` with `DEPLOY_SSH_KEY`; the host key matched the pinned ed25519 fingerprint; log line `SSH_OK as jordylab-deploy on vps-c2987aab`. | run 41666201394; the branch was temporarily added to the production environment's deployment-branch policy so the run could use the environment's secrets, and the entry was removed immediately after (policy back to `main` + `v*`); the bootstrap push trigger was removed once the run had passed, so later branch pushes do not fire failing runs; once the workflow is on main it is dispatchable on demand |
| 13 (before, k3s) | 2026-11-03 15:35 (UTC) | branch tip | `kubectl -n jordylab top pods`: backend 670 Mi, cnpg-cluster-1 175 Mi, frontend 4 Mi, keycloak 467 Mi, ntfy 12 Mi — total 1328 Mi. Host `free -m`: total 7746, used 3090, free 247, shared 46, buff/cache 4753, available 4655, no swap. | compare after the window with `docker stats --no-stream` totals and `free -m` after a 30-minute soak (SC-007) |
| 10 (before cutover, T017) | 2026-11-03 18:11–18:35 (UTC) | branch tip | Open ports: 22 (ssh), 80 (http), 443 (https) from a full-range root sweep (`sudo nmap -Pn -p- -T4 --max-rate 200`, 658 s), plus 31460 and 32531 from a NodePort-range connect sweep (`nmap -Pn -p 30000-32767 -T4 --max-rate 50`, 165 s). Nothing else open in 1–65535; the two NodePorts are exactly the ones `kube-system/traefik` holds (32531→80, 31460→443, BUG-078). | T054 reruns both commands after cutover and expects only 80, 443 (and 22 until T056); the NodePorts go with k3s. Lessons: never scan this host fast — a 2000 pps raw SYN scan is silently mitigated upstream (OVH) and reports every port filtered while the site serves 200s; unprivileged full-range connect scans are not viable against the drop-everything firewall (hours; `-T5` aborts at its 900 s host timeout); the root raw sweep misses kube-proxy NodePorts, hence the second command |
| 11 (second check, T055) | 2026-11-03 19:23 (UTC) | `main` (post-#199) | Tailnet SSH re-checked with the deploy key in place: from the Mac (`ubuntu@vps-c2987aab` over the tailnet) and from a CI job — the tailscale-ssh-smoke workflow dispatched on demand from main (run 41702833214) connected as `jordylab-deploy` with the pinned fingerprint | T056 (close public SSH) still waits for T054's post-cutover scan |
| 3 (cutover, T032–T036) | 2026-11-03 20:11–20:29 (UTC) | branch tip | The stack came up on the host: Postgres built and healthy (after BUG-081's digest fix), roles created, the final dump restored with ownership (123/123 row counts identical), the environment file written (mode 600, deploy user), and all six services healthy under `JORDYLAB_RELEASE=sha-9cc83c4f636f6e6a309cf571d9402619ed413c18` (the PR #199 merge — the same code that was live on k3s). | two mid-window fixes: the Postgres pin (BUG-081) and the Caddy healthcheck (BUG-082); `/opt/jordylab/state/*` stays empty until the first deploy-release.sh run (T049) — the window brought the stack up directly |
| 4 (public routes) | 2026-11-03 20:43–20:45 (UTC) | branch tip | `https://jordylab.be/` 200; the OpenID discovery 200 with the right issuer; `/apis` 200 html from the frontend (whole-segment matching); `/ntfy/` answers through the stripped prefix (publish 200); `http://` → 308 Permanent Redirect to HTTPS (Caddy's permanent redirect; the old stack answered 301 — equivalent semantics); a fresh Let's Encrypt certificate issued by Caddy (issuer YE2, expires 2027-02-01). | the first public checks answered from the OLD k3s data plane (BUG-083): stopping the k3s service had left the iptables hostPort DNAT and shims alive, so Caddy saw zero traffic until `k3s-killall.sh` ran |
| 5 (smoke, T037) | 2026-11-03 20:43–20:46 (UTC) | branch tip | smoke-check.sh all green (home 200, `/api/settings/users` 401, discovery 200); ntfy publish 200 (message id qCcKWrsmmnmX); row counts identical with the final dump (T034: 123/123); the artwork volume migrated with its 0 entries (the source volume was empty); the owner signed in as an existing user and reached their own data. | artwork loading is verified after the first real scan on the new stack (owner decision 2026-11-03) |
| 14 (window record) | 2026-11-03 | branch tip | Window start 20:09 UTC (T026, write paths scaled to zero); the stack live and fully smoke-checked ~20:45 UTC; downtime ≈ 36 minutes. Abort deadline: start + 2.5 h = 22:39 UTC — never approached; no abort, the R14 rebuild path was not needed. | three defects found and fixed inside the window: BUG-081 (wrong-platform digest, ~6 min), BUG-082 (Caddy healthcheck) and BUG-083 (the old k3s data plane holding the public ports, ~15 min, including ~1 min of extra downtime from a too-broad pkill that `restart: unless-stopped` healed). Release freeze in effect until T062; AUTO_DEPLOY paused |
| 9 (deploy, T049) | 2026-11-03 22:12–22:14 (UTC) | `76f3836` | the workflow deployed a commit sha; every step green (the sops install fixed the earlier failure); `current-release` = `sha-76f3836…`, previous empty; all three services healthy; `/actuator/info` reports it; public 200/401/200 | run 41728815560; the first deploy through the workflow |
| 9 (rollback, T051) | 2026-11-03 22:38–22:42 (UTC) | `9cc83c4` then `76f3836` | the older sha `9cc83c4` deployed (run 41732623682; current `9cc83c4`, previous `76f3836`), then `76f3836` restored (run 41732871477; current `76f3836`, previous `9cc83c4`) | the first attempt (run 41730834972) failed because the target's old scripts replaced the current tooling; fixed in PR #204 |
| 9 (refusal, T052) | 2026-11-03 22:42 (UTC) | `v9.9.9` (refused) | the job failed at checkout, before the host was touched; the state files were unchanged and the site answered 200/401/200 | run 41733143053 |
| 9 (automatic, T062) | 2026-11-03 22:51–23:04 (UTC) | `c2bbbc6` | with `AUTO_DEPLOY=true`, the merge of PR #205 deployed itself: Build run 41734429896, `deploy / deploy` success with no manual dispatch, current `c2bbbc6`, previous `76f3836`, site 200/401/200 | the merge after this record proves it again |
| 10 (after cutover, T054) | 2026-11-04 10:09 (UTC) | `c2bbbc6` | full-range root sweep (`sudo nmap -Pn -p- -T4 --max-rate 200`, 658 s): only 80 and 443 open, 65533 filtered; public 22 closed | the pre-cutover NodePorts (31460, 32531) are gone |
| 11 (T057) | 2026-11-04 10:26 (UTC) | `c2bbbc6` | public 22 closed from the Mac (`nc` exit 1) and tailnet SSH works (`TAILNET_SSH_OK`); `ufw` lists only 80/443 after the three stale k3s rules were removed (T053) | administration is Tailscale SSH only |
| 12 (T045) | 2026-11-04 10:26 (UTC) | `c2bbbc6` | timers enabled: the first scheduled diff ran at 03:00:20 UTC (`20261104-030020D`); full `20261103-203343F`; `info` status ok; next diff 2026-11-05 03:00, first scheduled full 2026-11-08 04:00 | the full so far is the window's on-demand backup |
| 13 (after soak, T065) | 2026-11-04 10:56 (UTC) | `c2bbbc6` | 30 minutes after the first reading: host `free -m` used 2253, available 5492 (containers: backend 815, keycloak 600, postgres 56, caddy 13, ntfy 12, frontend 7 MiB). Against the 2026-11-03 15:35 baseline (used 3090, available 4655) that is 837 MiB less, so SC-007 holds. | the container sum (≈1504 MiB) is not comparable with the k3s pod sum (1328 MiB): the k3s figure excluded kube-system (Traefik, CoreDNS, cert-manager, CNPG operator), so the host figure is the comparison |
| 9 (superseded, FR-006) | 2026-11-04 11:27 (UTC) | `c2bbbc6` (older) | re-running the older deploy job with `automatic=true` while `3f8396d` was live: the workflow read the live release, decided `superseded` ("target c2bbbc6 is older than the live commit 3f8396d"), skipped the mirror, environment, deploy and smoke steps and ended green; the live release was unchanged | run 41734429896, attempt 2; this closes the last behaviour that had only fixture coverage |
| 8 (reboot, T064) | 2026-11-04 12:46–12:47 (UTC) | `7c4ed73` | `sudo reboot`; SSH back in 27 s; the six services were healthy 61 s after the reboot; site 200/401/200 and `http://` → 308; the live release was unchanged | no manual step was needed; the Caddy certificate survived in its volume |
| 24 (major-upgrade rehearsal) | 2026-11-04 12:56 (UTC) | `7c4ed73` | a 15 MB `pg_dumpall` restored in ~1 s into a throwaway PostgreSQL 17.11 container (`pgvector/pgvector:pg17` digest `sha256:7d0a4146…`, pgBackRest 2.60.0): one expected `role "postgres" already exists`, and `gamecatalog.game` 512/512, Flyway `20261103002`, `vector`, `keycloak.user_entity` 11/11 | rehearses runbook §24 steps 2 and 4 only; the cutover and go-back need a real major bump |
| backups (T040–T042, after BUG-084) | 2026-11-03 20:32–20:35 (UTC) | branch tip | The pgBackRest stanza was created and checked against the real S3 repository after the Postgres image was rebuilt with `ca-certificates` (BUG-084: the base image shipped no CA bundle, so the endpoint's normal Sectigo chain failed verification in-container; the host verified it fine). First full backup `20261103-203343F` (48.9 MB database → 10.7 MB compressed, 57 s), WAL archiving active, `info` status ok under `/pgbackrest` in `jordylab-cnpg-backups`. | the Postgres container was recreated for the image rebuild (~15 s database blip; every service healthy again after, home 200 + discovery 200); the old Barman chain stays untouched in the same bucket until its retention ends |
| 6 (T044, PITR) | 2026-11-03 20:38 target / 20:47 (UTC) | branch tip | Point-in-time restore to 2026-11-03 20:38 UTC into the throwaway `jor31-pitr-check` (no published ports, removed after): recovery reached the target, promoted, and `finance.article` counted 823 rows — its exact value at that time (822 in the 20:09 frozen baseline; the FNA ingestion added one article mid-window). Production-now equals the restore exactly: the owner's post-cutover sign-in wrote no new rows because the restored Keycloak keys and sessions kept the old SSO cookie valid. | two gotchas: the throwaway server needs the S3 keys at replay time (`archive-get`), and a target only minutes old needs `SELECT pg_switch_wal();` on production first — a quiet database does not fill its 16 MB WAL segment, so recovery otherwise ends before the target |
| T045–T065 | | | not yet run | |
