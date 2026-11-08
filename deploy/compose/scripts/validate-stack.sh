#!/usr/bin/env bash
# Validates the production stack definition with the Compose client the server uses (spec 024, FR-013, SC-009).
# Runs on the owner's Mac and on the VPS. It reads only the example environment file, so it needs no secret.
#
# It does not source podman-env.sh: that file sets the runtime directory and the socket of the deploy account on the
# server. Config validation needs neither.
set -euo pipefail

script_directory=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
compose_directory=$(cd "$script_directory/.." && pwd)
stack_file=$compose_directory/compose.yaml
example_env_file=$compose_directory/env/prod.env.example
export PODMAN_COMPOSE_PROVIDER=docker-compose
export PODMAN_COMPOSE_WARNING_LOGS=false
# The example file does not hold the release (deploy-release.sh exports it); a placeholder keeps the image names rendering.
export JORDYLAB_RELEASE="${JORDYLAB_RELEASE:-v0.0.0-validation}"

fail() {
  echo "validate-stack: $*" >&2
  exit 1
}

if ! command -v docker-compose >/dev/null 2>&1; then
  fail "docker-compose (Compose v2) is not on PATH; install it, then run this again"
fi

# Compose v2 names the Go client, whose release numbers now run 5.x; the check is on the major version, not the name.
compose_version=$(docker-compose version --short 2>/dev/null || true)
compose_major=${compose_version#v}
compose_major=${compose_major%%.*}
if [[ ! $compose_major =~ ^[0-9]+$ ]] || (( compose_major < 2 )); then
  fail "expected the Compose v2 client (major version 2 or later), found '${compose_version:-nothing}'"
fi

podman compose --env-file "$example_env_file" -f "$stack_file" config -q \
  || fail "the stack definition does not validate"

rendered_json=$(podman compose --env-file "$example_env_file" -f "$stack_file" config --format json) \
  || fail "could not render the stack definition as JSON"
published_services=$(printf '%s' "$rendered_json" | python3 -c '
import json, sys
services = json.load(sys.stdin)["services"]
print(" ".join(sorted(name for name, service in services.items() if service.get("ports") and name != "caddy")))
')
if [[ -n $published_services ]]; then
  fail "only caddy may publish ports; also published: $published_services"
fi

echo "validate-stack: the stack definition validates with Compose $compose_version; only caddy publishes ports"
