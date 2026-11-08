#!/usr/bin/env bash
# Restores the Caddyfile that was live before the failed release (JOR-82). Runs on the host as the deploy user.
# Best effort: a missing or identical previous file is a no-op.
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

if [ ! -f "$previous_config" ]; then
  echo "revert-caddy-config: no previous Caddyfile; nothing to do"
  exit 0
fi

if cmp -s "$host_config" "$previous_config"; then
  echo "revert-caddy-config: the mirrored Caddyfile already matches the previous one"
  exit 0
fi

install -m 644 "$previous_config" "$host_config"
if ! podman exec jordylab-caddy-1 caddy reload --config "$container_config" --adapter caddyfile; then
  echo "revert-caddy-config: reloading the previous Caddyfile failed" >&2
  exit 1
fi

printf '%s\n' "$(hash_of "$host_config")" > "$applied_hash"
rm -f "$previous_config"
echo "revert-caddy-config: previous Caddyfile restored"
