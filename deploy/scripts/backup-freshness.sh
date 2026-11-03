#!/usr/bin/env bash
# Reports how old the newest completed Postgres backup is (spec 019, research D9).
# Reads `kubectl -n jordylab get backups.postgresql.cnpg.io -o json` on stdin. Never fails a deploy: a stale,
# missing or unreadable backup is a warning (FR-018). Prints key=value lines for $GITHUB_OUTPUT.
# Exit 0 for every report; exit 2 for invalid arguments.
#
# Usage: backup-freshness.sh [--now-epoch <seconds>]
set -euo pipefail

MAXIMUM_AGE_HOURS=26
now_epoch=""

while [ $# -gt 0 ]; do
  case "$1" in
    --now-epoch) now_epoch="${2:-}"; shift 2 ;;
    *) echo "usage: backup-freshness.sh [--now-epoch <seconds>]" >&2; exit 2 ;;
  esac
done

if [ -z "$now_epoch" ]; then
  now_epoch="$(date -u +%s)"
fi
if ! [[ "$now_epoch" =~ ^[0-9]+$ ]]; then
  echo "usage: backup-freshness.sh [--now-epoch <seconds>]" >&2
  exit 2
fi

report() {
  printf 'newest_completed_at=%s\n' "$1"
  printf 'age_hours=%s\n' "$2"
  printf 'warning=%s\n' "$3"
  printf 'reason=%s\n' "$4"
  exit 0
}

input="$(cat)"

if ! printf '%s' "$input" | jq -e . >/dev/null 2>&1; then
  report "" "" true unreadable
fi

newest_completed_at="$(printf '%s' "$input" \
  | jq -r '[.items[]? | select(.status.phase == "completed") | .status.stoppedAt // empty] | max // empty')"

if [ -z "$newest_completed_at" ]; then
  report "" "" true none-completed
fi

if ! newest_epoch="$(printf '%s' "$newest_completed_at" | jq -R 'fromdateiso8601')"; then
  report "$newest_completed_at" "" true unreadable
fi

age_seconds=$((now_epoch - newest_epoch))
age_hours=$((age_seconds / 3600))

if [ "$age_seconds" -gt $((MAXIMUM_AGE_HOURS * 3600)) ]; then
  report "$newest_completed_at" "$age_hours" true older-than-26h
fi

report "$newest_completed_at" "$age_hours" false ""
