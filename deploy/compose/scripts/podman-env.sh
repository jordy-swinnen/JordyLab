# shellcheck shell=bash
# Environment for the host scripts that run Podman (spec 024, contracts/host-contract.md, "Scripts' environment").
# Sourced, never executed. Each value is set only when the caller has not set it already.
#
# - XDG_RUNTIME_DIR: rootless Podman keeps its runtime state under this directory. Commands started by ssh or sudo
#   do not always set it, and a different directory would hide the running stack from the next deploy.
# - DOCKER_HOST: the Compose client reaches the deploy account's Podman through its API socket (research R2).
# - PODMAN_COMPOSE_PROVIDER: names the Compose client, so podman compose does not search for another one.
# - PODMAN_COMPOSE_WARNING_LOGS: silences the provider notice that podman compose prints on every run. Confirmed on
#   the owner's Mac (podman 6.1.4, 2026-11-08); the rehearsal machine repeats the check (task T018).

if [ -z "${XDG_RUNTIME_DIR:-}" ]; then
  XDG_RUNTIME_DIR="/run/user/$(id -u)"
  export XDG_RUNTIME_DIR
fi
export DOCKER_HOST="${DOCKER_HOST:-unix://${XDG_RUNTIME_DIR}/podman/podman.sock}"
export PODMAN_COMPOSE_PROVIDER="${PODMAN_COMPOSE_PROVIDER:-docker-compose}"
export PODMAN_COMPOSE_WARNING_LOGS="${PODMAN_COMPOSE_WARNING_LOGS:-false}"
