#!/usr/bin/env bash
# Smoke checks against the public site after a rollout (spec 019, contracts/deploy-workflow.md).
# Each check is retried. No response body is printed (FR-009, FR-028).
# Exit 0 when every check passes; exit 1 when a check still fails after its retries; exit 2 for invalid arguments.
#
# Usage: smoke-check.sh --base-url <url> [--kaneo-url <url>] [--attempts <n>] [--delay-seconds <s>]
#
# --kaneo-url adds the Kaneo health check (spec 022, FR-008). It only warns: a Kaneo outage never fails the run,
# because Kaneo is not part of the application release.
set -uo pipefail

base_url=""
kaneo_url=""
attempts=6
delay_seconds=10

usage() {
  echo "usage: smoke-check.sh --base-url <url> [--kaneo-url <url>] [--attempts <n>] [--delay-seconds <s>]" >&2
  exit 2
}

while [ $# -gt 0 ]; do
  case "$1" in
    --base-url) base_url="${2:-}"; shift 2 ;;
    --kaneo-url) kaneo_url="${2:-}"; shift 2 ;;
    --attempts) attempts="${2:-}"; shift 2 ;;
    --delay-seconds) delay_seconds="${2:-}"; shift 2 ;;
    *) usage ;;
  esac
done

[ -n "$base_url" ] || usage

run_check() {
  local name="$1"
  local path="$2"
  local expected_status="$3"
  local actual_status=""
  local attempt=1

  while [ "$attempt" -le "$attempts" ]; do
    actual_status="$(curl -s -o /dev/null -w '%{http_code}' --max-time 20 "$base_url$path" || true)"
    if [ "$actual_status" = "$expected_status" ]; then
      echo "smoke $name: expected $expected_status, got $actual_status after $attempt attempt(s)"
      return 0
    fi
    if [ "$attempt" -lt "$attempts" ]; then
      sleep "$delay_seconds"
    fi
    attempt=$((attempt + 1))
  done

  echo "::error::smoke $name failed: expected $expected_status, got ${actual_status:-no response} after $attempts attempt(s)"
  return 1
}

# Warns and returns 0 whatever the answer: Kaneo is optional for the application release (spec 022, FR-008).
check_kaneo_warn_only() {
  local status=""
  local attempt=1
  while [ "$attempt" -le "$attempts" ]; do
    status="$(curl -s -o /dev/null -w '%{http_code}' --max-time 20 "$kaneo_url/api/health" || true)"
    if [ "$status" = "200" ]; then
      echo "smoke kaneo: expected 200, got $status after $attempt attempt(s)"
      return 0
    fi
    if [ "$attempt" -lt "$attempts" ]; then
      sleep "$delay_seconds"
    fi
    attempt=$((attempt + 1))
  done
  echo "::warning::smoke kaneo: expected 200, got ${status:-no response} after $attempts attempt(s); the application release is not affected"
  return 0
}

failures=0
run_check home / 200 || failures=$((failures + 1))
run_check api-unauthenticated /api/settings/users 401 || failures=$((failures + 1))
run_check identity-csrf /api/identity/auth/csrf 204 || failures=$((failures + 1))
run_check old-auth /auth/realms/jordylab 404 || failures=$((failures + 1))

if [ -n "$kaneo_url" ]; then
  check_kaneo_warn_only
fi

[ "$failures" -eq 0 ]
