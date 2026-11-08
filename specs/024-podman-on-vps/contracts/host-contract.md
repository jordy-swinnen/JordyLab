# Contract: The Podman host

What the VPS must provide for the stack to run on Podman, and how each Docker command in the repository maps to its Podman form. Host commands are operator steps: the owner runs them over Tailscale SSH (see the runbook's convention). Versions are recorded on the host when installed.

## Account and settings

| Item | Requirement | Why |
|------|-------------|-----|
| Deploy account | `jordylab-deploy`, an id of 1000 or more, not in the `docker` group, not in `sudo`; same SSH key as today | Rootless Podman needs a normal-range account (research R9); removing the `docker` group removes root-equivalent access |
| Subordinate ids | Entries for the account in `/etc/subuid` and `/etc/subgid` (a range of at least 65,536) | User namespaces for rootless containers |
| Lingering | `loginctl enable-linger jordylab-deploy` | The account's service manager runs from boot, so the stack and the timers work with nobody logged in |
| Restart unit | Podman's restart unit enabled for the account (`systemctl --user enable podman-restart.service`, run as the account) | Starts `restart: always` containers at boot (research R3) |
| Socket | `podman.socket` enabled for the account (`systemctl --user enable --now podman.socket`) | The Compose client talks to this socket |
| Privileged ports | `/etc/sysctl.d/99-jordylab-ports.conf` containing `net.ipv4.ip_unprivileged_port_start=80`, applied with `sysctl --system` | Caddy binds 80 and 443 (research R4) |
| Firewall | `ufw` unchanged: default deny incoming; 80/tcp and 443/tcp allowed | Rootless Podman adds no rules (research R5) |
| Packages | `podman`, `uidmap`, `passt` (pasta), `netavark`, `aardvark-dns`, and the Docker Compose v2 client as an executable named `docker-compose` on the `PATH` | Names as the Ubuntu 26.04 archive provides them. `podman compose` looks for an executable named `docker-compose`; the `docker-compose-v2` package ships only the plugin form, so the pin row below adds the symlink (decided in T018) |
| Compose client pin | Package `docker-compose-v2` version `2.40.3+ds1-0ubuntu1` from the Ubuntu archive, installed with apt and held (`apt-mark hold docker-compose-v2`). Then `sudo ln -s /usr/libexec/docker/cli-plugins/docker-compose /usr/local/bin/docker-compose`. Installed binary sha256 (`/usr/libexec/docker/cli-plugins/docker-compose`): arm64 `8b7b2350aee234d0982dbcd69157e187b8b59b080e044b738b1d294ec0e9366d`, amd64 `93226cc2ea191353641921350ad87d96cb942e29a21c7c68c430adfd9954af34`. Package sha256 (archive index): arm64 `17d4e94e9387d8276df43e9031f933aed825234c14c2d815c29a4a4ec11f543e`, amd64 `fb8bbea5614c65cbdf426a7fd13bfeaf0f777a8f4d62758fbbfa4f051be330da` | Chosen over a downloaded binary because apt checks the archive signature and the rehearsal VM already runs this package. Both architectures are recorded because the VPS architecture is not yet confirmed. Quickstart Q2 checks the installed binary's hash for the machine's architecture |
| Scripts' environment | `XDG_RUNTIME_DIR=/run/user/<id>` set if empty; `PODMAN_COMPOSE_PROVIDER=docker-compose`; `PODMAN_COMPOSE_WARNING_LOGS=false` | `sudo -u` does not set the runtime directory; the provider must be explicit; the provider notice is noise in deploy logs |
| Docker Engine | Installed, stopped and disabled during the soak period; removed at its end | Way back (research R11) |

## Backup units

Both services keep their timers. The service files change as follows (`<kind>` is `diff` or `full`):

```ini
[Unit]
Description=JordyLab pgBackRest <kind> backup

[Service]
Type=oneshot
User=jordylab-deploy
ExecStart=/bin/sh -c 'XDG_RUNTIME_DIR=/run/user/$$(id -u) exec /usr/bin/podman exec jordylab-postgres-1 pgbackrest --stanza=jordylab --type=<kind> backup'
```

`%U` is root's id in a system unit, so the wrapper looks up the account's runtime directory when the unit starts (`$$` is systemd's escape for a literal `$`). No `Requires=` on the user manager is needed: lingering keeps `/run/user/<id>` up from boot. `Requires=docker.service` and `After=docker.service` are removed. Schedules and the journal behaviour are unchanged.

## Command mapping

| Where | Docker form today | Podman form |
|-------|-------------------|-------------|
| `deploy-release.sh` | `docker compose --env-file … -f … <args>` | `podman compose --env-file … -f … <args>` (same arguments, including `--wait --wait-timeout 600 --remove-orphans`) |
| `apply-caddy-config.sh` | `docker compose … <args>` | `podman compose … <args>` |
| `apply-caddy-config.sh`, `revert-caddy-config.sh` | `docker exec jordylab-caddy-1 …` | `podman exec jordylab-caddy-1 …` |
| `apply-caddy-config.sh` | `docker cp <file> jordylab-caddy-1:<path>` | `podman cp <file> jordylab-caddy-1:<path>` |
| Backup units | `docker compose … exec -T postgres pgbackrest …` | `podman exec jordylab-postgres-1 pgbackrest …` |
| Runbook, as the deploy account | `sudo -u jordylab-deploy docker compose …` | `sudo -u jordylab-deploy env XDG_RUNTIME_DIR=/run/user/$(id -u jordylab-deploy) podman compose …` (`env` sets the runtime directory that `sudo -u` does not) |
| Runbook, listing | `sudo docker ps` | `sudo -u jordylab-deploy env XDG_RUNTIME_DIR=/run/user/$(id -u jordylab-deploy) podman ps` |
| Runbook, volumes | `docker volume …` | `podman volume …`, as the deploy account |
| Runbook §3, validation | `docker compose … config -q` | `podman compose … config -q` |

## `compose.yaml` changes

- Every `restart: unless-stopped` becomes `restart: always`.
- The header comment names Podman and `podman compose`.
- The comment on `shm_size` names the container default rather than "Docker's".
- No image, port, volume, health check, limit or environment line changes.

## What does not change

Images and their pins, the Caddyfile, the public routes, the environment contract, the pgBackRest configuration and stanza, the workflow's steps (they reach the host only through the scripts), and the runner-side registry check.
