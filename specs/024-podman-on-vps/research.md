# Research: Production on Podman

Decisions for [plan.md](plan.md). Facts about the repository were read from the files named. Facts about Podman come from a web search on 2026-11-08; most sources are blog posts rather than the Podman manual, so every behaviour that production depends on is listed in "Not yet verified" and proven in the rehearsal (R10) before the maintenance window.

## What the repository tells us

- Production is five services in [deploy/compose/compose.yaml](../../deploy/compose/compose.yaml): Caddy, frontend, backend, ntfy, Postgres with pgBackRest. Only Caddy publishes ports. Four named volumes: `postgres-data`, `artwork`, `ntfy-cache`, `caddy-data`.
- Every Docker call is in four places: [deploy-release.sh](../../deploy/compose/scripts/deploy-release.sh) (`docker compose pull/up`), [apply-caddy-config.sh](../../deploy/compose/scripts/apply-caddy-config.sh) and [revert-caddy-config.sh](../../deploy/compose/scripts/revert-caddy-config.sh) (`docker exec`, `docker cp`, `docker compose`), and the two backup units in [deploy/compose/host](../../deploy/compose/host) (`Requires=docker.service`, `docker compose exec -T`). The workflow calls Docker only on the runner, to inspect the registry, which this feature does not touch.
- The deploy script relies on `up -d --wait --wait-timeout 600 --remove-orphans` and on container names such as `jordylab-caddy-1`. Whatever runs Compose must keep both.
- The host is Ubuntu 26.04 LTS ([jordylab-ops skill](../../.claude/skills/jordylab-ops/SKILL.md)). The deploy account `jordylab-deploy` was created with `adduser --system` and sits in the `docker` group ([runbook §3](../../docs/runbook.md)).
- Local: the owner's Mac runs Podman 6.1.4 in a Linux VM. `podman compose` there delegates to `podman-compose` 1.6.0. The end-to-end script picks Podman on macOS and Docker elsewhere ([jordylab-fe/e2e/lib/cleanup.sh](../../jordylab-fe/e2e/lib/cleanup.sh)). The dev database file [jordylab-be/compose.yaml](../../jordylab-be/compose.yaml) is a separate, much smaller stack.
- The Postgres image is built on the host from [postgres/Containerfile](../../deploy/compose/postgres/Containerfile) with plain instructions only (no BuildKit-only syntax). No registry login step is documented for the host, so the images are presumably public on GHCR; the rehearsal confirms a rootless pull works the same way (quickstart Q3).

## R1: Rootless Podman, run by the deploy account

**Decision**: Run the stack rootless as `jordylab-deploy`. The deploy account leaves the `docker` group; nothing on the host needs root to deploy.

**Rationale**: Membership of the `docker` group is root on the host, and the deploy account is reachable by the CI key. Rootless Podman gives the same deploy flow with no root. A report on rootless Podman says it does not change firewall rules at all, which keeps `ufw` in charge (R5). Local Podman is also rootless by default, so this is the closer match to "alike".

**Alternatives considered**: Rootful Podman run through `sudo`: closest to today, but keeps a root-equivalent deploy path and its published ports are filtered outside `ufw`'s input rules (R5). A dedicated service account separate from the deploy account: cleaner on paper, but adds a second account and a hand-off between the two for no gain on one host.

## R2: Docker Compose v2 as the Compose provider, over Podman's socket

**Decision**: Use the Docker Compose v2 client as the Compose provider and point it at Podman's own API socket. `podman compose` does this: it runs the provider with `DOCKER_HOST` set to Podman's socket. The scripts call `podman compose`, not `docker compose`.

**Rationale**: `compose.yaml` uses `depends_on` with `condition: service_healthy`, `mem_limit`, `shm_size` and log limits, and the deploy script uses `--wait`, `--wait-timeout` and `--remove-orphans`. The Docker Compose v2 client implements all of them itself and only needs an API to talk to. Container names stay `jordylab-<service>-1`, so the scripts' `exec` targets stay valid. A search for `podman-compose` found support for `service_healthy` conditions but no confirmation of `up --wait`; the deploy script cannot work without `--wait`.

**Alternatives considered**: `podman-compose` (what the Mac uses by default): would need `--wait` proven and changes the container names to `jordylab_caddy_1`. Quadlet units (systemd-native): the best boot story, but replaces `compose.yaml` with five unit files, so production and local would no longer share a definition. Both stay open as later options and are not chosen now (YAGNI).

## R3: Boot recovery with `restart: always`, Podman's restart unit and lingering

**Decision**: Change the five `restart: unless-stopped` lines to `restart: always`. Enable Podman's restart unit for the deploy account and turn on lingering (`loginctl enable-linger`) so the account's service manager runs from boot without a login.

**Rationale**: Podman has no always-running daemon, so a restart policy is applied at boot by a unit. Sources disagree on whether that unit also starts `unless-stopped` containers; the one that quotes the unit's own text says it filters on `always` only. `always` is the value every source agrees on. The behavioural difference (a container stopped by hand comes back after a reboot) is small and is written in the runbook.

**Alternatives considered**: Keeping `unless-stopped` and adding a custom unit that starts containers with that policy: more moving parts for a nicety. Verified or not, the reboot test (SC-005) is the arbiter.

## R4: Lower `net.ipv4.ip_unprivileged_port_start` to 80

**Decision**: Ship `99-jordylab-ports.conf` with `net.ipv4.ip_unprivileged_port_start=80`, installed under `/etc/sysctl.d/`.

**Rationale**: By default an unprivileged process cannot bind ports below 1024, so rootless Caddy cannot publish 80 and 443. The sysctl is system-wide, so any unprivileged process on the host could then bind 80 and above. On a one-owner VPS with only the deploy account and Tailscale-only administration, that exposure is small, and the change is one line that is easy to review and to undo.

**Alternatives considered**: `setcap` on Podman's port-forwarding helper: breaks when the package updates. Publishing high ports and redirecting 80/443 with firewall NAT rules: adds firewall rules that `ufw` and Podman would both have to agree on. Socket activation: more moving parts than the problem warrants.

## R5: `ufw` stays authoritative

**Decision**: Keep `ufw` exactly as in the runbook (default deny; 80 and 443 allowed). Run the external port scan before and after the switch and require identical results.

**Rationale**: Rootless Podman does not write firewall rules, so a published port is reachable only if the host's own rules allow it. For rootful Podman, a discussion reports that published ports are rewritten before the input rules and then dropped by `ufw`'s forward policy, which would break or confuse the setup. Spec 020 already noted that Docker's published ports bypass `ufw`'s input rules; moving to rootless removes that property.

**Not verified**: that the real client address reaches Caddy through rootless port forwarding (see "Not yet verified").

## R6: Cold copy of the stopped volumes

**Decision**: During the window, stop the stack, then copy each Docker volume into a Podman volume as a tar stream: the Docker side is read as root, the Podman side writes inside a rootless container and sets ownership to the id the service uses (Postgres runs as id 999 in the official image). The copy script prints a checksum manifest of both sides and stops on any difference. Before stopping, take a final pgBackRest full backup. The encrypted copy on the owner's Mac from spec 020's procedure stays the last safety net.

**Rationale**: A cold copy of a cleanly stopped cluster keeps its system identifier, so pgBackRest's stanza stays valid, archiving continues from where it stopped, and a restore to a time before the switch uses the same chain. A dump and restore creates a new cluster with a new identifier, which forces a stanza upgrade and splits the restore history. The data is a few hundred megabytes, so a copy takes minutes. Docker's volumes are not touched, which makes the way back (R11) a restart rather than a restore.

**Alternatives considered**: Dump and restore: simpler in concept, loses chain continuity. Copying files while Postgres runs: unsafe. Moving instead of copying: removes the way back.

## R7: Backup timers stay system units but run as the deploy account

**Decision**: Keep the two timers and services as system units. Add `User=jordylab-deploy`, and a shell wrapper that sets `XDG_RUNTIME_DIR` from the account's id when the unit starts. `%U` cannot be used: in a system unit it is root's id. Replace `docker compose ... exec -T postgres pgbackrest ...` with `podman exec jordylab-postgres-1 pgbackrest ...`. Remove `docker.service`.

**Rationale**: The containers now belong to the deploy account, so only that account can reach them. Keeping the units as system units keeps the install step and the journal commands the same. `podman exec` without a terminal needs no `-T`.

**Not verified**: that a system unit with `User=` reaches the account's Podman through the user manager (rehearsal).

## R8: Health checks and limits

**Decision**: Keep every health check, memory limit, `shm_size` and log limit as written. Prove three in the rehearsal: `mem_limit` takes effect (it needs the memory controller delegated to the user's cgroup), the log size limit applies to the logging driver Podman maps `json-file` to, and a health check reports `healthy` through the socket so `--wait` returns.

**Rationale**: These are the three places Podman and Docker implement the same Compose key differently enough to matter.

## R9: The deploy account must be a normal-range account

**Decision**: Rootless Podman needs subordinate user and group id ranges, and a user manager that lingers. Accounts made with `adduser --system` get neither by default and sit in the system id range, which is awkward for both. Check this first in the rehearsal. If the existing account cannot do it, convert it (`usermod -u` to an id of 1000 or more, fix the ownership under `/opt/jordylab`) and add the ranges (`usermod --add-subuids --add-subgids`). The account name and the CI key do not change, so the workflow does not change.

**Rationale**: It is cheaper to find this on the rehearsal machine than in the window.

## R10: Rehearsal on a throwaway Ubuntu 26.04 machine

**Decision**: Before the window, build a throwaway Ubuntu 26.04 machine (a local VM on the Mac or a short-lived cloud instance, the owner's choice), apply the host contract, run the stack on it, and run the quickstart's rehearsal checks including a reboot. The Mac's own Podman runs inside a Fedora-style VM and is not a substitute: it proves the stack definition, not the host settings.

**Rationale**: The host settings (lingering, sysctl, user manager, units, firewall) are where this change can fail, and there is no old node to fall back to except Docker's stopped stack.

## R11: Docker Engine stays installed but stopped during the soak period

**Decision**: After the switch, stop the Docker stack without removing it, disable `docker.service` and `docker.socket`, and keep Docker's volumes untouched for one week. The way back is: stop the Podman stack, enable Docker, start the old stack. If a rollback is needed after new data was written, take a final backup from the Podman side first and restore it into Docker's volumes. At the end of the soak period, with the owner's approval, remove Docker Engine, its packages, its networks and its volumes, after confirming a fresh backup exists.

**Rationale**: Only one engine can hold ports 80 and 443, so the two stacks never run together. Copying rather than moving the volumes keeps the way back cheap for the first hours, when it is most likely needed.

## R12: Order of the pipeline work

**Decision**: Set the repository variable `AUTO_DEPLOY` to anything but `true` before merging. Merge the change. Run the switch. Run the deploy workflow once by hand with the live release, which proves the new scripts end to end (SC-004). Then set `AUTO_DEPLOY` back to `true`.

**Rationale**: The workflow mirrors the repository's scripts onto the host on every run. If the merged scripts (which call Podman) ran against a host that still runs Docker, the deploy would fail. Pausing first avoids that; the owner approves the host change at the start of the window (FR-011).

## R13: Local parity is checked with the Docker Compose client, selected by an environment variable

**Decision**: Add `validate-stack.sh`, which runs `podman compose config` against the production stack definition and the example environment file with `PODMAN_COMPOSE_PROVIDER=docker-compose`, so the Mac uses the same Compose client as the server without changing the owner's default (`podman-compose`) for daily work. Write the remaining differences in the runbook (host operating system, rootless inside a VM on the Mac, Caddy and pgBackRest present only in production, restart handled by a unit on the server).

**Rationale**: Changing the owner's default Compose provider would alter local development and the end-to-end stack, which the spec leaves out of scope. A script gives the "same tooling" check without that.

## Not yet verified (proven in the rehearsal, R10)

| # | Claim | Why unsure | Proven by |
|---|-------|------------|-----------|
| 1 | `podman compose up -d --wait --wait-timeout 600 --remove-orphans` returns only when services are healthy, with the Docker Compose client over the socket | Blog posts only | Quickstart Q3 |
| 2 | `restart: always` containers return after a reboot with lingering and the restart unit enabled | Sources disagree on `unless-stopped`; the reboot behaviour is system-specific | Quickstart Q5 |
| 3 | The real client address reaches Caddy through rootless port forwarding | Depends on Podman's rootless network layer | Quickstart Q6 |
| 4 | `mem_limit`, `shm_size` and the log size limit are enforced under rootless Podman | Cgroup delegation and driver mapping | Quickstart Q4 |
| 5 | A system unit with `User=` and `XDG_RUNTIME_DIR` can run `podman exec` into the account's container | Depends on the user manager being up | Quickstart Q7 |
| 6 | The existing deploy account, or a converted one, runs rootless Podman | Created as a system account | Quickstart Q2 |
| 7 | Podman 5.7.0 is what Ubuntu 26.04 installs, and the Compose client package is available | Taken from package listings and guides | Quickstart Q2 |
| 8 | The tar-stream copy preserves the Postgres cluster and the Caddy certificates | Ownership mapping | Quickstart Q8 |
| 9 | `sudo -u jordylab-deploy env XDG_RUNTIME_DIR=/run/user/<id> podman ps` runs over a non-interactive `ssh` command and finds the account's containers | Not tried | Quickstart Q2 |
| 10 | A `docker-compose` executable (Compose v2) can be installed on Ubuntu 26.04 from a pinned, checksummed source | A package may ship only the plugin form | Quickstart Q2 |

## Sources

- [Podman 5.7.0 in the Ubuntu 26.04 archive (podman-remote listing)](https://packages.ubuntu.com/resolute/admin/podman-remote), [install guide](https://computingforgeeks.com/install-podman-ubuntu-2604/)
- [Docker Compose v2 with Podman](https://oneuptime.com/blog/post/2026-03-17-use-docker-compose-v2-podman/view), [Compose through the Podman socket](https://oneuptime.com/blog/post/2026-03-17-use-docker-compose-podman-socket/view)
- [Compose `depends_on` with Podman](https://oneuptime.com/blog/post/2026-03-17-use-compose-depends-on-podman/view)
- [Podman restart policy `unless-stopped` discussion](https://discussion.fedoraproject.org/t/how-to-have-a-working-unless-stopped-behavior-for-containers/190134), [restart policy guide](https://oneuptime.com/blog/post/2026-03-16-run-container-restart-policy-podman/view)
- [Binding privileged ports with rootless Podman](https://oneuptime.com/blog/post/2026-03-18-bind-privileged-ports-rootless-podman/view), [the sysctl](https://oneuptime.com/blog/post/2026-03-18-configure-ip-unprivileged-port-start-rootless-podman/view), [Caddy with rootless Podman](https://piefed.nullspace.lol/post/16396)
- [Podman and firewall providers (GitHub discussion)](https://github.com/podman-container-tools/podman/discussions/29817), [rootless and firewall rules](https://oneuptime.com/blog/post/2026-03-04-configure-firewalld-docker-podman-containers-rhel-9/view)
