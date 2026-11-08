#!/usr/bin/env bash
# Applies the mirrored Caddyfile when it changed (JOR-82). Runs on the host as the deploy user.
#
# - validates the new file before anything is replaced, so a rejected config never reaches the proxy;
# - recreates the caddy container once if it still mounts the file instead of the directory (the pre-JOR-82
#   layout), which is why the first deploy after that change takes a few seconds longer;
# - reloads atomically, and restores the previous Caddyfile if the reload fails.
set -euo pipefail

script_directory=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=podman-env.sh
. "$script_directory/podman-env.sh"

readonly compose_dir=/opt/jordylab/compose
readonly state_dir=/opt/jordylab/state
readonly host_config=$compose_dir/Caddyfile
readonly container_config=/srv/Caddyfile
readonly previous_config=$state_dir/Caddyfile.previous
readonly applied_hash=$state_dir/caddy-applied.sha256

hash_of() {
  sha256sum "$1" | cut -d' ' -f1
}

reload_caddy() {
  podman exec jordylab-caddy-1 caddy reload --config "$container_config" --adapter caddyfile
}

recreate_caddy() {
  podman compose --env-file /opt/jordylab/env/prod.env -f "$compose_dir/compose.yaml" \
    up -d --no-deps --force-recreate caddy
}

if [ ! -f "$host_config" ]; then
  echo "apply-caddy-config: $host_config is missing" >&2
  exit 1
fi

current_hash=$(hash_of "$host_config")
if [ -f "$applied_hash" ] && [ "$(cat "$applied_hash")" = "$current_hash" ]; then
  echo "caddy config unchanged"
  exit 0
fi

# Validate from a copy: an invalid file never reaches the mount, and never causes a crashed recreation.
podman cp "$host_config" jordylab-caddy-1:/tmp/Caddyfile.validate
if ! podman exec jordylab-caddy-1 caddy validate --config /tmp/Caddyfile.validate --adapter caddyfile; then
  podman exec jordylab-caddy-1 rm -f /tmp/Caddyfile.validate
  echo "apply-caddy-config: Caddy rejected the new Caddyfile; the running config is untouched" >&2
  exit 1
fi
podman exec jordylab-caddy-1 rm -f /tmp/Caddyfile.validate

install -m 600 "$host_config" "$previous_config"

# The container from before JOR-82 mounts only /etc/caddy/Caddyfile and cannot see /srv/Caddyfile.
inside_hash=$(podman exec jordylab-caddy-1 sha256sum "$container_config" 2>/dev/null | cut -d' ' -f1 || true)
if [ "$inside_hash" != "$current_hash" ]; then
  echo "apply-caddy-config: caddy does not see the mirrored file; recreating it once" >&2
  recreate_caddy
fi

if ! reload_caddy; then
  echo "apply-caddy-config: reload failed; restoring the previous Caddyfile" >&2
  install -m 644 "$previous_config" "$host_config"
  reload_caddy || { echo "apply-caddy-config: restoring the previous Caddyfile also failed" >&2; exit 1; }
  exit 1
fi

printf '%s\n' "$current_hash" > "$applied_hash"
echo "caddy config applied"
