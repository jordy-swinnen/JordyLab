#!/usr/bin/env bash
# Smoke checks against the public site after a rollout (spec 019, contracts/deploy-workflow.md).
# Each check is retried. No response body is printed (FR-009, FR-028).
# Exit 0 when every check passes; exit 1 when a check still fails after its retries; exit 2 for invalid arguments.
#
# Usage: smoke-check.sh --base-url <url> [--attempts <n>] [--delay-seconds <s>]
set -uo pipefail

base_url=""
attempts=6
delay_seconds=10

usage() {
  echo "usage: smoke-check.sh --base-url <url> [--attempts <n>] [--delay-seconds <s>]" >&2
  exit 2
}

while [ $# -gt 0 ]; do
  case "$1" in
    --base-url) base_url="${2:-}"; shift 2 ;;
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

failures=0
run_check home / 200 || failures=$((failures + 1))
run_check api-unauthenticated /api/settings/users 401 || failures=$((failures + 1))
run_check keycloak-discovery /auth/realms/jordylab/.well-known/openid-configuration 200 || failures=$((failures + 1))

[ "$failures" -eq 0 ]
