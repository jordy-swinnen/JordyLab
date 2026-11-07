#!/usr/bin/env bash
# Cases for scripts/kaneo/trial/run.sh (spec 022, T004). A fake container runtime and a fake curl stand in for Podman and
# the network, so the cases need no container engine and touch nothing outside a temporary folder.
# Run: bash scripts/kaneo/trial/tests/verify-cases.sh     (exit 0 when every case passes)
set -u

TRIAL_SOURCE_DIRECTORY="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
readonly TRIAL_SOURCE_DIRECTORY
readonly TRIAL_PROJECT="jordylab-kaneo-trial"
failures=0

if [[ "$(uname)" == "Darwin" ]]; then
  allowed_root="/private/tmp"
else
  allowed_root="${TMPDIR:-/tmp}"
fi
base="$(mktemp -d "$allowed_root/kaneo-trial-test.XXXXXX")"
outside="$(mktemp -d)"
trap 'rm -rf "$base" "$outside"' EXIT

bin="$base/bin"
state="$base/state"
trial="$base/trial"
mkdir -p "$bin" "$state" "$trial"
cp "$TRIAL_SOURCE_DIRECTORY/run.sh" "$TRIAL_SOURCE_DIRECTORY/compose.yaml" "$trial/" 2>/dev/null

# Fake container runtime: records every call in $FAKE_STATE_DIR/log and keeps labelled resources in three files.
cat > "$bin/podman" <<'FAKE'
#!/usr/bin/env bash
state="$FAKE_STATE_DIR"
echo "$*" >> "$state/log"
if [[ "$1" == "compose" && -n "${FAKE_COMPOSE_UP_EXIT:-}" && "$*" == *" up -d"* ]]; then
  exit "$FAKE_COMPOSE_UP_EXIT"
fi
if [[ "$1 $2" == "ps -a" ]]; then cat "$state/containers"; exit 0; fi
if [[ "$1 $2" == "volume ls" ]]; then cat "$state/volumes"; exit 0; fi
if [[ "$1 $2" == "network ls" ]]; then cat "$state/networks"; exit 0; fi
if [[ "$1" == "rm" || ( "$1" == "volume" && "$2" == "rm" ) || ( "$1" == "network" && "$2" == "rm" ) ]]; then
  id="${*: -1}"
  for kind in containers volumes networks; do
    grep -v -x -F "$id" "$state/$kind" > "$state/$kind.new" || true
    mv "$state/$kind.new" "$state/$kind"
  done
fi
exit 0
FAKE

# Fake curl: the health probe succeeds or fails as the case says.
cat > "$bin/curl" <<'FAKE'
#!/usr/bin/env bash
exit "${FAKE_CURL_EXIT:-0}"
FAKE
chmod +x "$bin/podman" "$bin/curl"

trial_env=(PATH="$bin:$PATH" FAKE_STATE_DIR="$state" KANEO_TRIAL_RUNTIME="$bin/podman")

reset_state() {
  rm -rf "$state"
  mkdir -p "$state"
  : > "$state/log"
  : > "$state/containers"
  : > "$state/volumes"
  : > "$state/networks"
}

# invoke <action> [NAME=value ...]: runs run.sh with the fake runtime and sets $output and $code.
invoke() {
  local action="$1"
  shift
  output="$(env "${trial_env[@]}" "$@" bash "$trial/run.sh" "$action" 2>&1)"
  code=$?
}

expect() {
  local name="$1" detail="$2"
  shift 2
  if "$@"; then
    echo "PASS $name"
  else
    echo "FAIL $name: $detail"
    failures=$((failures + 1))
  fi
}

# Called indirectly through expect "$@", which shellcheck cannot follow (SC2329).
# shellcheck disable=SC2329
log_has() { grep -q -F -- "$1" "$state/log"; }
# Called indirectly through expect "$@", which shellcheck cannot follow (SC2329).
# shellcheck disable=SC2329
no_compose_up() { ! grep -q -F -- " up -d" "$state/log"; }
# Called indirectly through expect "$@", which shellcheck cannot follow (SC2329).
# shellcheck disable=SC2329
output_has() { grep -q -F -- "$1" <<<"$output"; }
# Called indirectly through expect "$@", which shellcheck cannot follow (SC2329).
# shellcheck disable=SC2329
output_clean() { ! grep -q -E 'PASSWORD|SECRET' <<<"$output"; }
# Called indirectly through expect "$@", which shellcheck cannot follow (SC2329).
# shellcheck disable=SC2329
no_dev_stack() { ! grep -q -F -- "jordylab-be" "$state/log"; }

# A. Refuses to run outside /Users or /private/tmp on macOS, before any runtime call.
reset_state
if [[ "$(uname)" == "Darwin" ]]; then
  cp "$TRIAL_SOURCE_DIRECTORY/run.sh" "$TRIAL_SOURCE_DIRECTORY/compose.yaml" "$outside/" 2>/dev/null
  output="$(env "${trial_env[@]}" bash "$outside/run.sh" up 2>&1)"
  code=$?
  expect "refuses to run outside /Users or /private/tmp" "exit $code: $output" test "$code" -eq 2
  expect "the refusal makes no runtime call" "runtime was called" test ! -s "$state/log"
else
  echo "SKIP refuses to run outside /Users or /private/tmp (macOS only)"
fi

# B. up starts the stack and prints only the local address.
reset_state
invoke up
expect "up exits 0 when the stack is healthy" "exit $code: $output" test "$code" -eq 0
expect "up starts the trial project" "no compose call for $TRIAL_PROJECT" log_has "compose -p $TRIAL_PROJECT"
expect "up brings the stack up" "no 'up -d' call" log_has " up -d"
expect "up prints the local address" "output: $output" output_has "http://127.0.0.1:"
expect "up prints no password or secret" "output names a secret: $output" output_clean

# C. up writes nothing into the trial folder (no keys or passwords on disk).
reset_state
before="$(find "$trial" -mindepth 1 -maxdepth 1 -exec basename {} \; | sort)"
invoke up
after="$(find "$trial" -mindepth 1 -maxdepth 1 -exec basename {} \; | sort)"
expect "up writes nothing into the trial folder" "before: $before / after: $after" test "$before" = "$after"

# D. up refuses while labelled resources of an earlier trial remain.
reset_state
echo "old1" > "$state/containers"
invoke up
expect "up refuses while an earlier trial's resources remain" "exit $code" test "$code" -eq 1
expect "up starts nothing while leftovers remain" "compose up was called" no_compose_up

# E. up tears the stack down again when Kaneo never answers.
reset_state
invoke up FAKE_CURL_EXIT=7 KANEO_TRIAL_READY_SECONDS=2
expect "up fails when Kaneo never becomes healthy" "exit $code" test "$code" -eq 1
expect "up tears the stack down after a failed start" "no down call" log_has " down"

# F. verify is clean when nothing carries the trial label.
reset_state
invoke verify
expect "verify exits 0 when nothing carries the trial label" "exit $code: $output" test "$code" -eq 0
expect "verify says it is clean" "output: $output" output_has "verified clean"

# G. verify fails and names what remains.
reset_state
echo "abc123" > "$state/containers"
invoke verify
expect "verify exits 1 while a trial resource remains" "exit $code" test "$code" -eq 1
expect "verify names the leftover" "output: $output" output_has "abc123"

# H. down removes labelled containers, volumes and networks, then proves nothing is left.
reset_state
echo "c1" > "$state/containers"
echo "v1" > "$state/volumes"
echo "n1" > "$state/networks"
invoke down
expect "down exits 0" "exit $code: $output" test "$code" -eq 0
expect "down removes the labelled container" "no 'rm -f -v c1' call" log_has "rm -f -v c1"
expect "down removes the labelled volume" "no 'volume rm -f v1' call" log_has "volume rm -f v1"
expect "down removes the labelled network" "no 'network rm -f n1' call" log_has "network rm -f n1"
invoke verify
expect "verify is clean after down" "exit $code: $output" test "$code" -eq 0

# I. The trial never names the development stack.
reset_state
invoke up
invoke down
invoke verify
expect "no runtime call names the development stack" "a call mentions jordylab-be" no_dev_stack

echo
if [[ "$failures" -eq 0 ]]; then
  echo "all trial cases passed"
  exit 0
fi
echo "$failures trial case(s) failed"
exit 1
