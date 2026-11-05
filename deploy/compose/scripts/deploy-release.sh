#!/usr/bin/env bash
# Host side of the Compose deploy (spec 020, contracts/deploy-workflow.md). Runs on the VPS as the deploy user.
#
#   deploy-release.sh deploy <release>     roll out <release>. If it fails, restore the live release and exit 1.
#   deploy-release.sh rollback <release>   <release> was rolled out and then failed the external check. Redeploy
#                                          the previous release and exit 1. Does nothing unless <release> is the
#                                          live release.
#
# A release is a release tag (vX.Y.Z or vX.Y.Z-rcN) or a commit's image tag (sha-<40 hex>): a sha deploy runs exactly
# the images that Build published, with no retag step (spec 019, FR-001).
# State lives in /opt/jordylab/state: current-release is the live release, previous-release is the one before it.
# prod.env.previous keeps the environment file that was live before the last deploy, so a rollback can restore it.
set -euo pipefail

readonly jordylab_home=/opt/jordylab
readonly compose_dir=$jordylab_home/compose
readonly compose_file=$compose_dir/compose.yaml
readonly env_file=$jordylab_home/env/prod.env
readonly env_previous_file=$jordylab_home/env/prod.env.previous
readonly state_dir=$jordylab_home/state
readonly current_file=$state_dir/current-release
readonly previous_file=$state_dir/previous-release
readonly public_url=https://jordylab.be/
readonly identity_url=https://jordylab.be/api/identity/auth/csrf
readonly release_pattern='^(v[0-9]+\.[0-9]+\.[0-9]+(-rc[0-9]+)?|sha-[0-9a-f]{40})$'

usage() {
  echo "usage: $0 deploy <release> | rollback <release>   (release: vX.Y.Z[-rcN] or sha-<40 hex>)" >&2
  exit 2
}

check_release() {
  if [[ ! $1 =~ $release_pattern ]]; then
    echo "deploy-release: $1 is not a release (vX.Y.Z[-rcN] or sha-<40 hex>)" >&2
    exit 2
  fi
}

read_state() {
  if [[ -f $1 ]]; then
    cat "$1"
  fi
}

write_state() {
  printf '%s\n' "$2" > "$1.tmp"
  mv "$1.tmp" "$1"
}

compose() {
  docker compose --env-file "$env_file" -f "$compose_file" "$@"
}

# Pulls and starts the release. Postgres, ntfy and Caddy are not restarted, so a release causes a few seconds of
# downtime at most (FR-019). Caddy restarts only when its configuration changes, which is a separate approved step.
# Callers run these inside an if condition, where set -e is off, so each failure is returned explicitly.
start_release() {
  export JORDYLAB_RELEASE=$1
  compose pull backend frontend || return 1
  compose up -d --wait --wait-timeout 600 --remove-orphans backend frontend || return 1
}

# Checks the public site through Caddy on this host, using the real certificate and routes.
check_public_site() {
  curl --fail --silent --show-error --output /dev/null --retry 5 --retry-delay 3 --retry-all-errors \
    --resolve jordylab.be:443:127.0.0.1 "$public_url" &&
    curl --fail --silent --show-error --output /dev/null --retry 5 --retry-delay 3 --retry-all-errors \
      --resolve jordylab.be:443:127.0.0.1 "$identity_url"
}

restore_previous_environment() {
  if [[ -f $env_previous_file ]]; then
    install -m 600 "$env_previous_file" "$env_file"
  fi
}

deploy_release() {
  local release=$1 live
  live=$(read_state "$current_file")

  if start_release "$release" && check_public_site; then
    write_state "$previous_file" "${live:-}"
    write_state "$current_file" "$release"
    echo "deployed $release (previous: ${live:-none})"
    return 0
  fi

  echo "deploy of $release failed" >&2
  restore_previous_environment
  if [[ -z $live ]]; then
    echo "there is no live release to restore; the owner decides" >&2
    exit 1
  fi
  if start_release "$live" && check_public_site; then
    echo "restored $live" >&2
  else
    echo "restoring $live also failed; the state file still names $live" >&2
  fi
  exit 1
}

rollback_release() {
  local failed=$1 live previous
  live=$(read_state "$current_file")
  previous=$(read_state "$previous_file")

  if [[ $live != "$failed" ]]; then
    echo "$failed is not the live release ($live is); nothing to roll back" >&2
    exit 1
  fi
  if [[ -z $previous ]]; then
    echo "there is no previous release to roll back to; the owner decides" >&2
    exit 1
  fi

  restore_previous_environment
  if start_release "$previous" && check_public_site; then
    write_state "$current_file" "$previous"
    echo "rolled back from $failed to $previous" >&2
  else
    echo "rollback to $previous failed; the state file still names $failed" >&2
  fi
  exit 1
}

main() {
  if [[ $# -ne 2 ]]; then
    usage
  fi
  check_release "$2"
  case $1 in
    deploy) deploy_release "$2" ;;
    rollback) rollback_release "$2" ;;
    *) usage ;;
  esac
}

main "$@"
