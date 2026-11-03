#!/usr/bin/env bash
# Table-driven fixtures for deploy/scripts/deploy-decision.sh (spec 019, research D15). Builds a temporary git history:
# A (oldest), B, C (newest) on main; a side branch D from B; tags v0.0.1-rc1 on A and v0.0.1-rc2 on B.
# Each case asserts the key=value lines the script prints and its exit status.
#
# Run standalone: bash deploy/scripts/tests/deploy-decision-cases.sh
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
DECISION_SCRIPT="$REPO_ROOT/deploy/scripts/deploy-decision.sh"
FIXTURE="$(mktemp -d)"
trap 'rm -rf "$FIXTURE"' EXIT

pass_count=0
fail_count=0

git -C "$FIXTURE" init -q -b main
git -C "$FIXTURE" config user.email "fixture@example.com"
git -C "$FIXTURE" config user.name "fixture"
git -C "$FIXTURE" config commit.gpgsign false

commit_with_content() {
  echo "$1" > "$FIXTURE/file.txt"
  git -C "$FIXTURE" add file.txt
  git -C "$FIXTURE" commit -q -m "$1"
  git -C "$FIXTURE" rev-parse HEAD
}

COMMIT_A="$(commit_with_content a)"
COMMIT_B="$(commit_with_content b)"
git -C "$FIXTURE" tag v0.0.1-rc1 "$COMMIT_A"
git -C "$FIXTURE" tag v0.0.1-rc2 "$COMMIT_B"

git -C "$FIXTURE" checkout -q -b side "$COMMIT_B"
COMMIT_D="$(commit_with_content d)"
git -C "$FIXTURE" checkout -q main
COMMIT_C="$(commit_with_content c)"

MISSING_COMMIT="0000000000000000000000000000000000000000"

# args: description, expected key=value lines (newline-separated), script arguments...
check_case() {
  local description="$1"
  local expected_lines="$2"
  shift 2
  local output=""
  local exit_code=0
  output="$(cd "$FIXTURE" && bash "$DECISION_SCRIPT" "$@" 2>/dev/null)" || exit_code=$?

  local missing=""
  while IFS= read -r expected_line; do
    [ -n "$expected_line" ] || continue
    if ! printf '%s\n' "$output" | grep -qxF -- "$expected_line"; then
      missing="$missing [$expected_line]"
    fi
  done <<< "$expected_lines"

  if [ "$exit_code" -eq 0 ] && [ -z "$missing" ]; then
    pass_count=$((pass_count + 1))
  else
    fail_count=$((fail_count + 1))
    printf 'FAIL %s (exit %s, missing:%s)\n%s\n---\n' "$description" "$exit_code" "$missing" "$output"
  fi
}

check_exit_code() {
  local description="$1"
  local expected_exit_code="$2"
  shift 2
  local exit_code=0
  (cd "$FIXTURE" && bash "$DECISION_SCRIPT" "$@" >/dev/null 2>&1) || exit_code=$?
  if [ "$exit_code" -eq "$expected_exit_code" ]; then
    pass_count=$((pass_count + 1))
  else
    fail_count=$((fail_count + 1))
    printf 'FAIL %s (expected exit %s, got %s)\n---\n' "$description" "$expected_exit_code" "$exit_code"
  fi
}

check_case "automatic target newer than live proceeds" \
  "$(printf 'decision=proceed\ntarget_commit=%s\nimage_tag=sha-%s\nlive_commit=%s' "$COMMIT_C" "$COMMIT_C" "$COMMIT_B")" \
  --target "$COMMIT_C" --live-tag "sha-$COMMIT_B" --automatic true

check_case "automatic target older than live is superseded" \
  "$(printf 'decision=superseded\ntarget_commit=%s\nlive_commit=%s' "$COMMIT_A" "$COMMIT_C")" \
  --target "$COMMIT_A" --live-tag "sha-$COMMIT_C" --automatic true

check_case "manual target older than live proceeds (rollback)" \
  "$(printf 'decision=proceed\ntarget_commit=%s\nlive_commit=%s' "$COMMIT_A" "$COMMIT_C")" \
  --target "$COMMIT_A" --live-tag "sha-$COMMIT_C" --automatic false

check_case "automatic target equal to live proceeds (idempotent)" \
  "$(printf 'decision=proceed\ntarget_commit=%s\nlive_commit=%s' "$COMMIT_B" "$COMMIT_B")" \
  --target "$COMMIT_B" --live-tag "sha-$COMMIT_B" --automatic true

check_case "invalid grammar is refused" \
  "decision=refused" \
  --target v1.2 --automatic true

check_case "commit not in the history is refused" \
  "decision=refused" \
  --target "$MISSING_COMMIT" --automatic true

check_case "missing release tag is refused" \
  "decision=refused" \
  --target v9.9.9 --automatic false

check_case "release tag resolves and keeps the tag as image tag" \
  "$(printf 'decision=proceed\ntarget_commit=%s\nimage_tag=v0.0.1-rc2' "$COMMIT_B")" \
  --target v0.0.1-rc2 --live-tag "sha-$COMMIT_C" --automatic false

check_case "placeholder live tag gives unknown live commit and proceeds" \
  "$(printf 'decision=proceed\nlive_commit=unknown')" \
  --target "$COMMIT_C" --live-tag placeholder --automatic true

check_case "no live tag gives unknown live commit and proceeds" \
  "$(printf 'decision=proceed\nlive_commit=unknown')" \
  --target "$COMMIT_C" --automatic true

check_case "live release tag is resolved through git" \
  "$(printf 'decision=superseded\nlive_commit=%s' "$COMMIT_B")" \
  --target "$COMMIT_A" --live-tag v0.0.1-rc2 --automatic true

check_case "diverged side branch proceeds" \
  "$(printf 'decision=proceed\ntarget_commit=%s' "$COMMIT_D")" \
  --target "$COMMIT_D" --live-tag "sha-$COMMIT_C" --automatic true

check_exit_code "missing --automatic is an invalid argument" 2 --target "$COMMIT_C"

check_exit_code "unknown argument is an invalid argument" 2 --target "$COMMIT_C" --automatic true --bogus

echo "deploy-decision cases: $pass_count passed, $fail_count failed"
[ "$fail_count" -eq 0 ]
