#!/usr/bin/env bash
# Cleanup library for the E2E runner (spec 012 FR-042/043): remove everything a run started, find leftovers by label,
# and sweep runs whose runner process is gone. Sourced by run.sh and runnable on its own:
#   lib/cleanup.sh sweep               remove resources of runs whose runner is no longer alive
#   lib/cleanup.sh remove <runId>      remove everything of one run
#   lib/cleanup.sh verify <runId>      list leftovers of one run and exit 1 if any
#   lib/cleanup.sh verify-all          list every resource carrying any E2E run label and exit 1 if any (CI's last step)
# Resources are found by the label dev.jordylab.e2e.run=<runId> (and the pid label), never by guessing names.

E2E_RUN_LABEL="dev.jordylab.e2e.run"
E2E_PID_LABEL="dev.jordylab.e2e.pid"

e2e_runtime() {
  if [[ -n "${E2E_RUNTIME:-}" ]]; then
    echo "$E2E_RUNTIME"
  elif [[ "$(uname)" == "Darwin" ]] && command -v podman >/dev/null 2>&1; then
    echo podman
  else
    echo docker
  fi
}

# Prints "<kind> <id-or-name> <labels>" for every container, volume and network that carries the run label key.
e2e_list_labelled() {
  local runtime
  runtime="$(e2e_runtime)"
  "$runtime" ps -a --filter "label=$E2E_RUN_LABEL" --format '{{.ID}} {{.Labels}}' 2>/dev/null | sed 's/^/container /'
  "$runtime" volume ls --filter "label=$E2E_RUN_LABEL" --format '{{.Name}} {{.Labels}}' 2>/dev/null | sed 's/^/volume /'
  "$runtime" network ls --filter "label=$E2E_RUN_LABEL" --format '{{.ID}} {{.Labels}}' 2>/dev/null | sed 's/^/network /'
}

# Lists the leftovers of one run (labelled with its id), one "<kind> <id>" per line.
e2e_leftovers() {
  local run_id="$1"
  local runtime
  runtime="$(e2e_runtime)"
  "$runtime" ps -a -q --filter "label=$E2E_RUN_LABEL=$run_id" 2>/dev/null | sed 's/^/container /'
  "$runtime" volume ls -q --filter "label=$E2E_RUN_LABEL=$run_id" 2>/dev/null | sed 's/^/volume /'
  "$runtime" network ls -q --filter "label=$E2E_RUN_LABEL=$run_id" 2>/dev/null | sed 's/^/network /'
}

e2e_remove_run() {
  local run_id="$1"
  local runtime
  runtime="$(e2e_runtime)"
  local item
  for item in $("$runtime" ps -a -q --filter "label=$E2E_RUN_LABEL=$run_id" 2>/dev/null); do
    "$runtime" rm -f -v "$item" >/dev/null 2>&1
  done
  for item in $("$runtime" volume ls -q --filter "label=$E2E_RUN_LABEL=$run_id" 2>/dev/null); do
    "$runtime" volume rm -f "$item" >/dev/null 2>&1
  done
  for item in $("$runtime" network ls -q --filter "label=$E2E_RUN_LABEL=$run_id" 2>/dev/null); do
    "$runtime" network rm -f "$item" >/dev/null 2>&1
  done
}

e2e_verify_clean() {
  local run_id="$1"
  local leftovers
  leftovers="$(e2e_leftovers "$run_id")"
  if [[ -n "$leftovers" ]]; then
    echo "e2e: LEFTOVERS from run $run_id (the run fails):" >&2
    echo "$leftovers" >&2
    return 1
  fi
  echo "e2e: verified clean: no container, volume or network of run $run_id remains"
}

# Removes the resources of every run whose runner process (pid label) is no longer alive. A run that is still running
# (live pid) is never touched, so two runs can share a machine.
e2e_sweep() {
  local run_ids line run_id pid
  run_ids="$(e2e_list_labelled | while read -r line; do
    # Labels print as key=value pairs separated by commas (docker and podman).
    run_id="$(echo "$line" | sed -n "s/.*$E2E_RUN_LABEL=\([^, ]*\).*/\1/p")"
    pid="$(echo "$line" | sed -n "s/.*$E2E_PID_LABEL=\([0-9]*\).*/\1/p")"
    if [[ -n "$run_id" ]] && { [[ -z "$pid" ]] || ! kill -0 "$pid" 2>/dev/null; }; then
      echo "$run_id"
    fi
  done | sort -u)"
  for run_id in $run_ids; do
    echo "e2e: sweeping leftovers of dead run $run_id"
    e2e_remove_run "$run_id"
  done
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  case "${1:-}" in
    sweep) e2e_sweep ;;
    remove) e2e_remove_run "${2:?run id}" ;;
    verify) e2e_verify_clean "${2:?run id}" ;;
    verify-all)
      remaining="$(e2e_list_labelled)"
      if [[ -n "$remaining" ]]; then
        echo "e2e: resources with an E2E run label remain:" >&2
        echo "$remaining" | cut -c1-120 >&2
        exit 1
      fi
      echo "e2e: verified clean: no E2E container, volume or network remains"
      ;;
    *) echo "usage: $0 sweep | remove <runId> | verify <runId> | verify-all" >&2; exit 64 ;;
  esac
fi
