#!/usr/bin/env bash
# One-off cold copy of production's volumes from Docker Engine to Podman (spec 024, research R6, switch procedure).
# Run as the deploy account on the VPS, in the window, after the Docker stack has been stopped.
#
# - Reads each Docker volume and writes it into a Podman volume of the same name, through the service image that
#   mounts it, so the copy downloads nothing.
# - Keeps numeric owners (tar --numeric-owner), so the files keep the same ids inside the rootless namespace.
# - Compares a sha256 manifest and the owner of the top directory on both sides; any difference stops the script.
# - Never changes or removes a Docker volume, so the way back stays a restart.
# The service images must ship GNU tar with --numeric-owner. Verified on the rehearsal machine (task T020).
set -euo pipefail

readonly project=jordylab
script_directory=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
compose_directory=$(cd "$script_directory/.." && pwd)
readonly stack_file=$compose_directory/compose.yaml
readonly env_file=${ENV_FILE:-/opt/jordylab/env/prod.env}
# Each entry: the volume name without the project prefix, and the service whose image reads and writes it.
readonly volume_services=(
  "postgres-data postgres"
  "artwork backend"
  "caddy-data caddy"
  "ntfy-cache ntfy"
)
readonly manifest_command='cd /data && find . -type f -exec sha256sum {} + | sort -k2 && stat -c "%u:%g %n" .'

fail() {
  echo "copy-volumes-to-podman: $*" >&2
  exit 1
}

# Docker's client, with any DOCKER_HOST set for Podman removed, so these calls always reach Docker Engine.
docker() {
  env -u DOCKER_HOST docker "$@"
}

# The copy reads a stopped stack only, so Docker's files cannot change while they are copied.
refuse_when_docker_stack_runs() {
  if [[ -n $(docker ps -q --filter "label=com.docker.compose.project=$project") ]]; then
    fail "containers of the $project stack are still running in Docker; stop them first (cold copy only)"
  fi
}

# The image of a service, as the Compose file names it. The release comes from JORDYLAB_RELEASE.
image_of() {
  docker compose --env-file "$env_file" -f "$stack_file" config --format json \
    | python3 -c 'import json, sys; print(json.load(sys.stdin)["services"][sys.argv[1]]["image"])' "$1"
}

docker_manifest() {
  docker run --rm --user 0:0 --entrypoint sh -v "$1:/data:ro" "$2" -c "$manifest_command"
}

podman_manifest() {
  podman run --rm --user 0:0 --entrypoint sh -v "$1:/data:ro" "$2" -c "$manifest_command"
}

copy_volume() {
  local volume_name=$1 service_name=$2
  local full_volume_name="${project}_${volume_name}"
  local image docker_side podman_side

  image=$(image_of "$service_name")

  # A missing volume is reported, never created: an empty copy would look like a successful restore.
  docker volume inspect "$full_volume_name" >/dev/null 2>&1 \
    || fail "Docker volume $full_volume_name is missing"
  if podman volume exists "$full_volume_name"; then
    fail "Podman volume $full_volume_name already exists; remove that copy first (it is Podman's, not Docker's)"
  fi
  podman volume create \
    --label "com.docker.compose.project=$project" \
    --label "com.docker.compose.volume=$volume_name" \
    "$full_volume_name" >/dev/null

  docker run --rm --user 0:0 --entrypoint tar -v "$full_volume_name:/from:ro" "$image" \
      --numeric-owner -C /from -cf - . \
    | podman run --rm -i --user 0:0 --entrypoint tar -v "$full_volume_name:/to" "$image" \
      --numeric-owner -C /to -xpf -

  docker_side=$(docker_manifest "$full_volume_name" "$image")
  podman_side=$(podman_manifest "$full_volume_name" "$image")
  if [[ $docker_side != "$podman_side" ]]; then
    diff <(printf '%s\n' "$docker_side") <(printf '%s\n' "$podman_side") >&2 || true
    fail "manifests of $full_volume_name differ; the copy is not trusted"
  fi
  echo "copied $full_volume_name: manifests equal ($(printf '%s\n' "$podman_side" | wc -l) lines)"
}

main() {
  : "${JORDYLAB_RELEASE:?set JORDYLAB_RELEASE to the live release before running this}"
  export JORDYLAB_RELEASE
  [[ -f $env_file ]] || fail "$env_file is missing"

  refuse_when_docker_stack_runs

  local pair volume_name service_name
  for pair in "${volume_services[@]}"; do
    read -r volume_name service_name <<<"$pair"
    copy_volume "$volume_name" "$service_name"
  done
  echo "copy-volumes-to-podman: every volume is copied and verified; the Docker volumes were not changed"
}

main "$@"
