# Quickstart: Verifying Production on Podman

Runnable checks for the rehearsal machine, the maintenance window and the soak period. Q1 to Q11 run first on a throwaway Ubuntu 26.04 machine (the rehearsal). Q4, Q5, Q6, Q7 and Q10 are repeated on the VPS after the switch, together with the production checks below. Host commands are operator steps: the owner runs them. Nothing here prints a secret.

Details live in the [host contract](contracts/host-contract.md), the [switch procedure](contracts/switch-procedure.md) and the [data model](data-model.md). Pass criteria refer to the spec's [success criteria](spec.md).

## Q1: Validate the stack definition on the Mac (SC-009, FR-013)

```bash
deploy/compose/scripts/validate-stack.sh
```

Expected: exit 0. The script runs `podman compose config -q` with the Docker Compose client and the example environment file, and fails if the provider is not the Docker Compose client.

## Q2: Host settings on the rehearsal machine (research claims 6, 7)

As the owner, on the rehearsal machine, after applying the host contract:

```bash
podman --version && docker-compose version --short
id jordylab-deploy
sha256sum "$(command -v docker-compose)"
ssh <rehearsal-host> 'sudo -u jordylab-deploy env XDG_RUNTIME_DIR=/run/user/$(id -u jordylab-deploy) podman ps'
grep jordylab-deploy /etc/subuid /etc/subgid
loginctl show-user jordylab-deploy -p Linger
sysctl net.ipv4.ip_unprivileged_port_start
```

Expected: Podman 5.7.0 or newer; an id of 1000 or more; subordinate ranges present; `Linger=yes`; the sysctl is `80`; the Compose client's checksum equals the one pinned in the host contract; the `podman ps` command runs as the deploy account from a non-interactive `ssh` session. Record the versions.

## Q3: Pull, build and start with `--wait` (claim 1)

As the deploy account:

```bash
podman compose --env-file /opt/jordylab/env/prod.env -f /opt/jordylab/compose/compose.yaml pull backend frontend
podman compose --env-file /opt/jordylab/env/prod.env -f /opt/jordylab/compose/compose.yaml build postgres
podman compose --env-file /opt/jordylab/env/prod.env -f /opt/jordylab/compose/compose.yaml up -d --wait --wait-timeout 600
```

Expected: the pull works without a registry login; the build succeeds; the last command returns only when all five services are healthy; container names are `jordylab-<service>-1`.

## Q4: Limits are enforced (claim 4)

```bash
podman inspect jordylab-backend-1 --format '{{.HostConfig.Memory}}'
podman inspect jordylab-postgres-1 --format '{{.HostConfig.ShmSize}}'
podman inspect jordylab-caddy-1 --format '{{json .HostConfig.LogConfig}}'
podman stats --no-stream
```

Expected: memory 1,610,612,736 bytes for the backend; shm 268,435,456 for Postgres; log options with size and file limits; `podman stats` shows the limits as the cap.

## Q5: Reboot recovery (claim 2, SC-005)

```bash
sudo reboot
```

After the machine returns, with no login to the deploy account:

```bash
sudo -u jordylab-deploy env XDG_RUNTIME_DIR=/run/user/$(id -u jordylab-deploy) podman ps --format '{{.Names}} {{.Status}}'
```

Expected: all five containers up and `healthy` within five minutes.

## Q6: Client address and port scan (claim 3, SC-006)

From another machine, request the site, then read the Caddy access log on the host for the request.

```bash
curl -sS -o /dev/null -w '%{http_code}\n' https://<rehearsal-host>/
sudo -u jordylab-deploy env XDG_RUNTIME_DIR=/run/user/$(id -u jordylab-deploy) podman logs --tail 5 jordylab-caddy-1
nmap -Pn -p- <rehearsal-host>
```

Expected: the log shows the requesting machine's address, not a container or gateway address; the scan shows only the public web ports.

## Q7: Backup timers (claim 5, SC-007)

```bash
sudo systemctl start jordylab-backup-diff.service
sudo journalctl -u jordylab-backup-diff.service -n 20 --no-pager
sudo -u jordylab-deploy env XDG_RUNTIME_DIR=/run/user/$(id -u jordylab-deploy) podman exec jordylab-postgres-1 pgbackrest --stanza=jordylab info
```

Expected: the unit finishes successfully with no Docker on the machine; `info` lists the new backup.

## Q8: Volume copy keeps the cluster (claim 8, FR-004)

On the rehearsal machine, with a Docker stack holding rehearsal data:

```bash
deploy/compose/scripts/copy-volumes-to-podman.sh
```

Expected: exit 0 and a manifest with equal checksums for all four volumes. Start the stack on the copy: row counts per table equal the Docker side, `pgbackrest check` passes without a stanza upgrade, and Caddy serves with the copied certificates and no new issuance.

## Q9: Point-in-time restore across the switch (FR-008, SC-003)

Using the backup contract's restore into a throwaway container, restore to a time before the switch and to a time after it.

Expected: both restored states match what production held at those times.

## Q10: Deploy and rollback through the workflow (FR-009, SC-004)

After the switch, run the workflow with the live release, then with the previous release, then with the live release again.

Expected: each run ends with the site healthy; the rollback run restores the earlier release; no manual command on the server. Force one failure on the rehearsal machine (a broken health check) and confirm the previous release returns automatically.

## Q11: The way back (FR-012)

On the rehearsal machine, run the "Inside the window" steps of the switch procedure's way back.

Expected: the Docker stack serves again from its untouched volumes and the site check passes.

## Production checks after the switch (SC-001, SC-002, SC-006, SC-008)

On the VPS, after the window's step 6:

- The site loads over HTTPS with the existing certificate; an existing user signs in; an artwork file is returned; the notification address answers.
- Row counts per table equal the baseline taken in Phase 0; artwork file count and checksums equal.
- The memory, shm and log limits match Q4.
- A request from outside shows its real address in Caddy's log (Q6).
- The external port scan equals the baseline.
- The window length and the first release's downtime are recorded; `free -m` before and after are recorded.

## End of the soak period (SC-010)

```bash
command -v docker; systemctl list-unit-files 'docker*'; ls /var/lib/docker
```

Expected: nothing found. The runbook and the operations skill no longer tell the reader to use Docker for the server.

## Results log

Fill in as each check is run (date, machine, result, versions or numbers). Nothing here is marked passed unless it was run.

| Check | Where | Date | Result | Notes |
|-------|-------|------|--------|-------|
| Q1 | Mac | 2026-11-08 | pass | Compose 5.7.0 (Homebrew bottle), provider `docker-compose`; only caddy publishes ports |
| Q2 | Rehearsal | | | Versions: |
| Q3 | Rehearsal | | | |
| Q4 | Rehearsal | | | |
| Q5 | Rehearsal | | | |
| Q6 | Rehearsal | | | |
| Q7 | Rehearsal | | | |
| Q8 | Rehearsal | | | |
| Q9 | Rehearsal | | | |
| Q10 | Rehearsal | | | |
| Q11 | Rehearsal | | | |
| Baseline (port scan, memory, disk, row counts, live release) | VPS | | | |
| Window length | VPS | | | |
| Production checks after the switch | VPS | | | |
| Q5 reboot | VPS | | | |
| Q10 deploy and rollback | VPS | | | |
| First backups under Podman | VPS | | | |
| First release downtime; memory after | VPS | | | |
| End of soak period | VPS | | | |
