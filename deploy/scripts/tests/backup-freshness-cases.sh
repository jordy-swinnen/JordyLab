#!/usr/bin/env bash
# Table-driven fixtures for deploy/scripts/backup-freshness.sh (spec 019, research D15). JSON fixtures stand in for
# `kubectl get backups.postgresql.cnpg.io -o json`; the clock is fixed at 2026-11-03T12:00:00Z.
#
# Run standalone: bash deploy/scripts/tests/backup-freshness-cases.sh
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
FRESHNESS_SCRIPT="$REPO_ROOT/deploy/scripts/backup-freshness.sh"
NOW_EPOCH=1793707200

pass_count=0
fail_count=0

# args: description, expected key=value lines (newline-separated), JSON input
check_case() {
  local description="$1"
  local expected_lines="$2"
  local json_input="$3"
  local output=""
  local exit_code=0
  output="$(printf '%s' "$json_input" | bash "$FRESHNESS_SCRIPT" --now-epoch "$NOW_EPOCH" 2>/dev/null)" || exit_code=$?

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

backup_object() { # name, phase, stoppedAt (empty for none)
  if [ -n "$3" ]; then
    printf '{"metadata":{"name":"%s"},"status":{"phase":"%s","stoppedAt":"%s"}}' "$1" "$2" "$3"
  else
    printf '{"metadata":{"name":"%s"},"status":{"phase":"%s"}}' "$1" "$2"
  fi
}

check_case "fresh completed backup is not a warning" \
  "$(printf 'newest_completed_at=2026-11-03T10:00:00Z\nage_hours=2\nwarning=false\nreason=')" \
  "{\"items\":[$(backup_object daily completed 2026-11-03T10:00:00Z)]}"

check_case "completed backup older than 26 hours warns" \
  "$(printf 'newest_completed_at=2026-11-02T06:00:00Z\nage_hours=30\nwarning=true\nreason=older-than-26h')" \
  "{\"items\":[$(backup_object daily completed 2026-11-02T06:00:00Z)]}"

check_case "only failed or started backups warn with none-completed" \
  "$(printf 'warning=true\nreason=none-completed')" \
  "{\"items\":[$(backup_object a failed 2026-11-03T10:00:00Z),$(backup_object b started '')]}"

check_case "empty item list warns with none-completed" \
  "$(printf 'warning=true\nreason=none-completed')" \
  '{"items":[]}'

check_case "invalid JSON is unreadable, not a failure" \
  "$(printf 'warning=true\nreason=unreadable')" \
  'not json'

check_case "empty input is unreadable, not a failure" \
  "$(printf 'warning=true\nreason=unreadable')" \
  ''

check_case "newest of several completed backups wins" \
  "$(printf 'newest_completed_at=2026-11-03T03:00:00Z\nage_hours=9\nwarning=false')" \
  "{\"items\":[$(backup_object old completed 2026-11-01T03:00:00Z),$(backup_object new completed 2026-11-03T03:00:00Z)]}"

check_case "completed backup without stoppedAt is ignored" \
  "$(printf 'newest_completed_at=2026-11-02T03:00:00Z\nage_hours=33\nwarning=true\nreason=older-than-26h')" \
  "{\"items\":[$(backup_object no-time completed ''),$(backup_object daily completed 2026-11-02T03:00:00Z)]}"

check_case "a failed backup newer than a completed one does not count" \
  "$(printf 'newest_completed_at=2026-11-02T03:00:00Z\nwarning=true\nreason=older-than-26h')" \
  "{\"items\":[$(backup_object daily completed 2026-11-02T03:00:00Z),$(backup_object failed-later failed 2026-11-03T11:00:00Z)]}"

echo "backup-freshness cases: $pass_count passed, $fail_count failed"
[ "$fail_count" -eq 0 ]
