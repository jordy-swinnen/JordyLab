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
| 2 (local) | 2026-11-02 (UTC) | branch tip | not run: `deploy/compose/compose.yaml` does not exist yet | |
| 1a, 6a (old chain) | | | not run: needs the owner's yes, since it creates a drill cluster on the live cluster | |
| 3–14 | | | not yet run | |
