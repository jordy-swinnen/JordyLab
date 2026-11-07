#!/usr/bin/env bash
# Throwaway Kaneo stack for the go/no-go trial (spec 022, User Story 1, tasks T004-T005).
#   run.sh up       start one Kaneo and one Postgres, wait until Kaneo answers, print the local address
#   run.sh down     stop and remove the stack and every resource that carries the trial label
#   run.sh verify   list what carries the trial label; exit 1 when anything remains
# The stack is named jordylab-kaneo-trial and labelled dev.jordylab.kaneo-trial=true, so it never touches the jordylab-be-*
# development stack. Nothing persists: no volumes, no bind mounts. The database password and the auth secret are generated
# per run and passed through the environment only; no key or password is written to disk or printed. On macOS run it from a
# path under /Users or /private/tmp (the repository's container rule). Podman on macOS, Docker elsewhere;
# KANEO_TRIAL_RUNTIME overrides the choice.
set -u

SCRIPT_DIRECTORY="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
readonly SCRIPT_DIRECTORY
readonly COMPOSE_FILE="$SCRIPT_DIRECTORY/compose.yaml"
readonly PROJECT="jordylab-kaneo-trial"
readonly LABEL="dev.jordylab.kaneo-trial=true"
readonly LOCAL_HOST="127.0.0.1"
readonly READY_SECONDS="${KANEO_TRIAL_READY_SECONDS:-240}"

usage() {
  echo "usage: run.sh up | down | verify" >&2
  exit 64
}

runtime() {
  if [[ -n "${KANEO_TRIAL_RUNTIME:-}" ]]; then
    echo "$KANEO_TRIAL_RUNTIME"
  elif [[ "$(uname)" == "Darwin" ]] && command -v podman >/dev/null 2>&1; then
    echo podman
  else
    echo docker
  fi
}

RUNTIME="$(runtime)"

guard_location() {
  [[ "$(uname)" == "Darwin" ]] || return 0
  case "$SCRIPT_DIRECTORY" in
    /Users/*|/private/tmp/*) return 0 ;;
  esac
  echo "kaneo-trial: on macOS run this from a path under /Users or /private/tmp (found $SCRIPT_DIRECTORY)" >&2
  exit 2
}

compose() {
  "$RUNTIME" compose -p "$PROJECT" -f "$COMPOSE_FILE" "$@"
}

# Prints "<kind> <id>" for every container, volume and network that carries the trial label, containers first so they
# are removed before the networks they use.
list_leftovers() {
  "$RUNTIME" ps -a -q --filter "label=$LABEL" 2>/dev/null | sed 's/^/container /'
  "$RUNTIME" volume ls -q --filter "label=$LABEL" 2>/dev/null | sed 's/^/volume /'
  "$RUNTIME" network ls -q --filter "label=$LABEL" 2>/dev/null | sed 's/^/network /'
}

remove_leftovers() {
  local kind id
  while read -r kind id; do
    case "$kind" in
      # </dev/null: the loop reads the resource list from stdin, so no removal may read it too.
      container) "$RUNTIME" rm -f -v "$id" >/dev/null 2>&1 </dev/null ;;
      volume) "$RUNTIME" volume rm -f "$id" >/dev/null 2>&1 </dev/null ;;
      network) "$RUNTIME" network rm -f "$id" >/dev/null 2>&1 </dev/null ;;
    esac
  done < <(list_leftovers)
}

verify_clean() {
  local leftovers
  leftovers="$(list_leftovers)"
  if [[ -n "$leftovers" ]]; then
    echo "kaneo-trial: LEFTOVERS remain (the trial is not clean):" >&2
    echo "$leftovers" >&2
    return 1
  fi
  echo "kaneo-trial: verified clean: no container, volume or network with the trial label remains"
}

down_stack() {
  compose down -v --remove-orphans >/dev/null 2>&1 || true
  remove_leftovers
  verify_clean
}

free_port() {
  python3 -I -c 'import socket; s = socket.socket(); s.bind(("127.0.0.1", 0)); print(s.getsockname()[1]); s.close()'
}

wait_until_healthy() {
  local deadline=$((SECONDS + READY_SECONDS))
  until curl -fsS --max-time 5 -o /dev/null "$TRIAL_CLIENT_URL/api/health" 2>/dev/null; do
    if (( SECONDS >= deadline )); then
      return 1
    fi
    sleep 1
  done
}

up_stack() {
  if [[ -n "$(list_leftovers)" ]]; then
    echo "kaneo-trial: resources of an earlier trial remain; run 'run.sh down' first, then 'run.sh verify'" >&2
    return 1
  fi

  local port
  port="$(free_port)"
  TRIAL_HOST_PORT="$port"
  TRIAL_CLIENT_URL="http://$LOCAL_HOST:$port"
  TRIAL_DB_PASSWORD="$(openssl rand -hex 16)"
  TRIAL_AUTH_SECRET="$(openssl rand -hex 32)"
  export TRIAL_HOST_PORT TRIAL_CLIENT_URL TRIAL_DB_PASSWORD TRIAL_AUTH_SECRET

  if ! compose up -d >&2; then
    echo "kaneo-trial: the stack did not start; tearing it down" >&2
    down_stack >&2
    return 1
  fi
  if ! wait_until_healthy; then
    echo "kaneo-trial: Kaneo did not answer on $TRIAL_CLIENT_URL within $READY_SECONDS s; tearing it down" >&2
    down_stack >&2
    return 1
  fi

  echo "kaneo-trial: running at $TRIAL_CLIENT_URL"
  echo "kaneo-trial: registration is closed from the start (DISABLE_REGISTRATION=true); quickstart step 1 asks whether the first user can still register"
}

main() {
  [[ $# -eq 1 ]] || usage
  guard_location
  case "$1" in
    up) up_stack ;;
    down) down_stack ;;
    verify) verify_clean ;;
    *) usage ;;
  esac
}

main "$@"
