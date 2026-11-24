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

# args: expected ("block"|"pass")  message  [commit time, epoch seconds, as CI passes it]
check() {
  local expected="$1" message="$2" when="${3:-}" actual
  printf '%s\n' "$message" > "$WORK_FILE"
  if (cd "$REPO_ROOT" && sh "$HOOK" "$WORK_FILE" $when >/dev/null 2>&1); then
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

BODY=$'JOR-12: something to change\n\nWhy it changed.\n\n'
COAUTHOR='Co-Authored-By: Someone <someone@example.com>'

check pass  "${BODY}Refs: 011 BUG-001"$'\n'"${COAUTHOR}"
check pass  "${BODY}Refs: 011, BUG-001"$'\n'"${COAUTHOR}"
check pass  "${BODY}Refs: 009/T039"
check pass  "${BODY}Refs: HANDOFF-02 DEPLOY-03 Q-07"
check pass  "${BODY}Refs: NO-CODE"$'\n'"${COAUTHOR}"
# the ticket, its decisions and its requirements (since JOR-122)
check pass  "${BODY}Refs: JOR-122"
check pass  "${BODY}Refs: JOR-78/D3"
check pass  "${BODY}Refs: JOR-122/FR-004"
check pass  "${BODY}Refs: JOR-122 JOR-94 BUG-001"
check pass  "${BODY}Refs: JOR-78/D3, JOR-78/D4"
check pass  $'Merge pull request #30 from jordy-swinnen/fix/x\n\nfix(x): y'
check pass  $'fixup! JOR-12: something'
check pass  $'Revert "fix(settings): something"\n\nThis reverts commit abc.'

check block "${BODY}${COAUTHOR}"
check block $'JOR-12: Refs: 011 in the subject only'
check block "${BODY}Refs: "
check block "${BODY}Refs: 999"
check block "${BODY}Refs: 999/T001"
check block "${BODY}Refs: BUG-999"
check block "${BODY}Refs: NO-CODE 011"
check block "${BODY}Refs: TICKET-12"
check block "${BODY}Refs: 11"
check block "${BODY}Refs: JOR-0"
check block "${BODY}Refs: JOR-122/D0"
check block "${BODY}Refs: JOR-122/D"
check block "${BODY}Refs: JOR-122X"
check block "${BODY}Refs: jor-122"
check block "${BODY}Refs: JOR-122/T004"
check block "${BODY}Refs: JOR-1234567"
check block "${BODY}Refs: NO-CODE JOR-122"

# ticket first (rule 1)
check pass  $'JOR-55: adding a button\n\nWhy.\n\nRefs: NO-CODE'
check pass  $'JOR-7: one digit\n\nWhy.\n\nRefs: NO-CODE'
check pass  $'JOR-123456: six digits\n\nWhy.\n\nRefs: NO-CODE'
check pass  $'JOR-12: fix(x): a conventional type after the ticket is still fine\n\nWhy.\n\nRefs: 011'
check block $'adding a button\n\nWhy.\n\nRefs: 011'
check block $'fix(settings): no ticket in front\n\nWhy.\n\nRefs: 011'
check block $'fix(JOR-12): ticket not first\n\nWhy.\n\nRefs: 011'
check block $'jor-12: lower case\n\nWhy.\n\nRefs: 011'
check block $'JOR-12 no colon\n\nWhy.\n\nRefs: 011'
check block $'JOR-12 fix(x): no colon after the ticket\n\nWhy.\n\nRefs: 011'
check block $'JOR-12:\n\nWhy.\n\nRefs: 011'
check block $'JOR-12:   \n\nWhy.\n\nRefs: 011'
check block $'JOR-: no number\n\nWhy.\n\nRefs: 011'
check block $'JOR-0: zero\n\nWhy.\n\nRefs: 011'
check block $'ABC-12: other project\n\nWhy.\n\nRefs: 011'
check block $'JOR-1234567: too many digits\n\nWhy.\n\nRefs: 011'
# the cutoff: CI passes the commit time, older commits are exempt from rule 1 only
check pass  $'fix(settings): old commit\n\nWhy.\n\nRefs: 011' 1793579399
check block $'fix(settings): new commit\n\nWhy.\n\nRefs: 011' 1793579400
check block $'fix(settings): old commit, bad refs\n\nWhy.\n\nRefs: 999' 1793579399
check pass  $'JOR-12: new, ticketed\n\nWhy.\n\nRefs: 011' 1793600000

echo "commit-msg cases: ${pass_count} passed, ${fail_count} failed"
[ "$fail_count" -eq 0 ]
