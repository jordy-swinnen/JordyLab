#!/usr/bin/env bash
# Proves the runner's cleanup guarantees on this machine (spec 012 SC-011, SC-013): four runs with a trivial test command,
# each followed by a leftover check, plus an unchanged-dev-database check when the dev stack is running.
#   1. passing test      2. failing test      3. Ctrl-C (SIGINT) mid-test      4. SIGKILL mid-test, then the sweep
# Exit 0 only when nothing is left after every scenario. Needs a built backend jar (run.sh builds it on the first run).
set -u
SCRIPT_DIRECTORY="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/cleanup.sh
. "$SCRIPT_DIRECTORY/lib/cleanup.sh"
RUNTIME="$(e2e_runtime)"
if [[ "$RUNTIME" == "podman" && -z "${DOCKER_HOST:-}" ]]; then
  DOCKER_HOST="unix://$(podman machine inspect --format '{{.ConnectionInfo.PodmanSocket.Path}}')"
  export DOCKER_HOST
fi
DEV_DATABASE_CONTAINER="${E2E_DEV_DATABASE_CONTAINER:-jordylab-be-pgvector-1}"
FAILURES=0
# E2E_PROOF_ONLY="sigint sigterm" runs just those scenarios (pass fail sigint sigterm sigkill); default is all of them.
wanted() { [[ -z "${E2E_PROOF_ONLY:-}" || " $E2E_PROOF_ONLY " == *" $1 "* ]]; }

# Starts the runner in the background with SIGINT at its default action. A shell started in the background by a script,
# a CI step or another tool inherits SIGINT as ignored, and bash cannot trap a signal that was ignored on entry, which would
# make a Ctrl-C test meaningless; a terminal never ignores it, so this matches real use.
start_runner_with_default_sigint() {
  local mode="$1" log="$2"
  E2E_SKIP_BACKEND_BUILD=1 python3 -c '
import os, signal, sys
signal.signal(signal.SIGINT, signal.SIG_DFL)
os.execvp("bash", ["bash", sys.argv[1], sys.argv[2]])
' "$SCRIPT_DIRECTORY/run.sh" "$mode" >"$log" 2>&1 &
}

# One "schema.table count" line per application table of the dev database, or nothing when the dev stack is not running.
# Read-only; runs inside the dev container so no credential is handled here.
dev_row_counts() {
  "$RUNTIME" ps --format '{{.Names}}' 2>/dev/null | grep -qx "$DEV_DATABASE_CONTAINER" || return 0
  "$RUNTIME" exec -i "$DEV_DATABASE_CONTAINER" sh -c 'psql -U "$POSTGRES_USER" -d "${POSTGRES_DB:-jordylab}" -At' 2>/dev/null <<'SQL'
select table_schema || '.' || table_name || ' ' ||
       (xpath('/row/c/text()', query_to_xml(format('select count(*) as c from %I.%I', table_schema, table_name), false, true, '')))[1]::text
from information_schema.tables
where table_type = 'BASE TABLE'
  and table_schema not in ('pg_catalog', 'information_schema', 'keycloak')
order by 1;
SQL
}

check_no_leftovers() {
  local scenario="$1" leftovers
  leftovers="$(e2e_list_labelled)"
  if [[ -n "$leftovers" ]]; then
    echo "PROOF FAIL [$scenario]: resources with the run label remain:"
    echo "$leftovers"
    FAILURES=$((FAILURES + 1))
  else
    echo "PROOF OK   [$scenario]: no container, volume or network with the run label remains"
  fi
}

run_scenario() {
  local scenario="$1" mode="$2" expected_exit="$3"
  E2E_SKIP_BACKEND_BUILD=1 bash "$SCRIPT_DIRECTORY/run.sh" "$mode" >"/tmp/e2e-proof-$scenario.log" 2>&1
  local exit_code=$?
  if [[ "$exit_code" != "$expected_exit" ]]; then
    echo "PROOF FAIL [$scenario]: exit code $exit_code, expected $expected_exit (log: /tmp/e2e-proof-$scenario.log)"
    FAILURES=$((FAILURES + 1))
  fi
  check_no_leftovers "$scenario"
}

wait_for_log_line() {
  local file="$1" text="$2" deadline=$((SECONDS + 300))
  until grep -q "$text" "$file" 2>/dev/null; do
    (( SECONDS >= deadline )) && return 1
    sleep 1
  done
}

BEFORE="$(dev_row_counts)"
[[ -n "$BEFORE" ]] && echo "dev stack found: recorded $(echo "$BEFORE" | wc -l | tr -d ' ') table counts" || echo "no dev stack running: dev-database check skipped"

wanted pass && run_scenario pass selftest-pass 0
wanted fail && run_scenario fail selftest-fail 1

# 3. interrupt: a real SIGINT (Ctrl-C) and a SIGTERM (CI cancel) to the runner while the test command is running.
# Job control gives each background run its own process group and keeps SIGINT enabled, as in a terminal.
interrupt_scenario() {
  local scenario="$1" signal="$2" expected_exit="$3" runner_pid exit_code
  start_runner_with_default_sigint selftest-wait "/tmp/e2e-proof-$scenario.log"
  runner_pid=$!
  if wait_for_log_line "/tmp/e2e-proof-$scenario.log" "waiting for an interrupt"; then
    kill "-$signal" "$runner_pid"
    wait "$runner_pid"
    exit_code=$?
    if [[ "$exit_code" != "$expected_exit" ]]; then
      echo "PROOF FAIL [$scenario]: runner exit code $exit_code, expected $expected_exit"; FAILURES=$((FAILURES + 1))
    fi
  else
    echo "PROOF FAIL [$scenario]: the run never reached the test phase"; kill -KILL "$runner_pid" 2>/dev/null; FAILURES=$((FAILURES + 1))
  fi
  check_no_leftovers "$scenario"
}
wanted sigint && interrupt_scenario sigint INT 130
wanted sigterm && interrupt_scenario sigterm TERM 143

# 4. hard kill: the trap cannot run, the next run's sweep (or the CI always() step) must clean up
E2E_SKIP_BACKEND_BUILD=1 bash "$SCRIPT_DIRECTORY/run.sh" selftest-wait >/tmp/e2e-proof-sigkill.log 2>&1 &
RUNNER_PID=$!
if wait_for_log_line /tmp/e2e-proof-sigkill.log "waiting for an interrupt"; then
  kill -KILL "$RUNNER_PID"; wait "$RUNNER_PID" 2>/dev/null
  pkill -f "jordylab-be/build/libs" 2>/dev/null   # the orphaned backend of the killed run
  echo "sigkill scenario: leftovers right after the kill (expected, the trap could not run):"
  e2e_list_labelled | sed 's/^/    /'
  bash "$SCRIPT_DIRECTORY/lib/cleanup.sh" sweep
else
  echo "PROOF FAIL [sigkill]: the run never reached the test phase"; kill -KILL "$RUNNER_PID" 2>/dev/null; FAILURES=$((FAILURES + 1))
fi
check_no_leftovers sigkill-after-sweep

AFTER="$(dev_row_counts)"
if [[ "$BEFORE" == "$AFTER" ]]; then
  echo "PROOF OK   [dev database]: table counts identical before and after"
else
  echo "PROOF FAIL [dev database]: counts changed:"; diff <(echo "$BEFORE") <(echo "$AFTER"); FAILURES=$((FAILURES + 1))
fi
[[ "$FAILURES" == 0 ]] && echo "ALL PROOFS PASSED" || echo "$FAILURES PROOF(S) FAILED"
exit "$FAILURES"
