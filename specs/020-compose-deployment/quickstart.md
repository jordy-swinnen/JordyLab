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
- Open a game with artwork. Pass: the artwork loads.
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
| 1a, 6a (old chain) | 2026-11-03 (UTC) | `67f5247` | the drill cluster restored to 2026-11-02 12:00 UTC became ready (cluster healthy, 100 tables). No table has fewer rows than production. 8 Keycloak tables have more (`keycloak.client`, `client_attributes`, `client_scope_client`, `offline_client_session`, `redirect_uris`, `scope_mapping`, `user_entity`, `web_origins`): the drill has two clients (`jordylab-mobile`, `mobile-release-ci`) and one user that production no longer has, plus expired sessions. This fits data that changed after the restore point, but it is not confirmed row by row. The drill cluster and its PVC were deleted. Production was unchanged: healthy, 35 backups, none from the drill. | The criterion 'no greater than production' is not strictly met. Owner decision pending. |
| host prep (T015, T016, T018, T021) | 2026-11-03 (UTC) | `67f5247` | deploy user `jordylab-deploy` with its key in `DEPLOY_SSH_KEY`; Docker Engine 29.9.2 and Compose 5.7.0 installed, no containers; k3s pods unchanged; `/opt/jordylab/{env,state,staging}` created; artwork copied: the k3s volume is empty (0 files) and the checksums match | the artwork check in step 5 needs an artwork file first |
| policy (T014, step 11 prerequisite) | 2026-11-03 (UTC) | branch tip | Tailscale policy saved in the admin console (JSON editor, at the owner's request): the `tag:ci` → `tag:jordylab-vps` grant now allows `tcp:22` next to `tcp:6443`, and the policy test expects `tag:jordylab-vps:22` accepted instead of dropped; 6443 stays accepted, 80 and 443 stay denied. The save passed the policy test. | the console reformatted the file on save (the one-line grant became multi-line); the grant/test comments and the header date note were updated to stay truthful |
| 11 (first half, Mac) | 2026-11-03 15:35 (UTC) | branch tip | SSH over the tailnet from the Mac works: `ssh ubuntu@100.93.81.41` connects key-only (host `vps-c2987aab`). The tailnet host key was pinned to `known_hosts` only after all three key fingerprints matched the already-trusted public-IP anchor (`57.129.163.110`). | — |
| 11 (second half, CI) | 2026-11-03 15:41 (UTC) | branch tip | The Tailscale SSH smoke workflow (`.github/workflows/tailscale-ssh-smoke.yml`) passed: a `tag:ci` runner joined the tailnet and connected as `jordylab-deploy` to `100.93.81.41` with `DEPLOY_SSH_KEY`; the host key matched the pinned ed25519 fingerprint; log line `SSH_OK as jordylab-deploy on vps-c2987aab`. | run 41666201394; the branch was temporarily added to the production environment's deployment-branch policy so the run could use the environment's secrets, and the entry was removed immediately after (policy back to `main` + `v*`); once the workflow is on main it is dispatchable on demand |
| 13 (before, k3s) | 2026-11-03 15:35 (UTC) | branch tip | `kubectl -n jordylab top pods`: backend 670 Mi, cnpg-cluster-1 175 Mi, frontend 4 Mi, keycloak 467 Mi, ntfy 12 Mi — total 1328 Mi. Host `free -m`: total 7746, used 3090, free 247, shared 46, buff/cache 4753, available 4655, no swap. | compare after the window with `docker stats --no-stream` totals and `free -m` after a 30-minute soak (SC-007) |
| 3–14 | | | not yet run | |
