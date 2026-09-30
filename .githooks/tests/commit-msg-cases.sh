#!/usr/bin/env bash
# Table-driven fixture for .githooks/commit-msg. Writes each message to a temp file, runs the hook
# from the repo root and asserts the verdict (pass = exit 0, block = non-zero).
#
# Run standalone: bash .githooks/tests/commit-msg-cases.sh
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
HOOK="$REPO_ROOT/.githooks/commit-msg"
WORK_FILE="$(mktemp)"
trap 'rm -f "$WORK_FILE"' EXIT

pass_count=0
fail_count=0

# args: expected ("block"|"pass")  message
check() {
  local expected="$1" message="$2" actual
  printf '%s\n' "$message" > "$WORK_FILE"
  if (cd "$REPO_ROOT" && sh "$HOOK" "$WORK_FILE" >/dev/null 2>&1); then
    actual="pass"
  else
    actual="block"
  fi
  if [ "$actual" = "$expected" ]; then
    pass_count=$((pass_count + 1))
  else
    fail_count=$((fail_count + 1))
    printf 'FAIL (expected %s, got %s):\n%s\n---\n' "$expected" "$actual" "$message"
  fi
}

BODY=$'fix(settings): something\n\nWhy it changed.\n\n'
COAUTHOR='Co-Authored-By: Someone <someone@example.com>'

check pass  "${BODY}Refs: 011 BUG-001"$'\n'"${COAUTHOR}"
check pass  "${BODY}Refs: 011, BUG-001"$'\n'"${COAUTHOR}"
check pass  "${BODY}Refs: 009/T039"
check pass  "${BODY}Refs: HANDOFF-02 DEPLOY-03 Q-07"
check pass  "${BODY}Refs: NO-CODE"$'\n'"${COAUTHOR}"
check pass  $'Merge pull request #30 from jordy-swinnen/fix/x\n\nfix(x): y'
check pass  $'fixup! fix(settings): something'
check pass  $'Revert "fix(settings): something"\n\nThis reverts commit abc.'

check block "${BODY}${COAUTHOR}"
check block $'fix(settings): Refs: 011 in the subject only'
check block "${BODY}Refs: "
check block "${BODY}Refs: 999"
check block "${BODY}Refs: 999/T001"
check block "${BODY}Refs: BUG-999"
check block "${BODY}Refs: NO-CODE 011"
check block "${BODY}Refs: TICKET-12"
check block "${BODY}Refs: 11"

echo "commit-msg cases: ${pass_count} passed, ${fail_count} failed"
[ "$fail_count" -eq 0 ]
