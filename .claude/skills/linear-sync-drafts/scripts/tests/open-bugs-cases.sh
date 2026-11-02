#!/usr/bin/env bash
# Fixture for open_bugs.sh: a small bug log with every status, run through the script (BUG_LOG), asserted with jq.
# Run standalone: bash .claude/skills/linear-sync-drafts/scripts/tests/open-bugs-cases.sh
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCRIPT="$HERE/../open_bugs.sh"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
pass_count=0; fail_count=0
ok()  { pass_count=$((pass_count + 1)); }
bad() { fail_count=$((fail_count + 1)); printf 'FAIL [%s]: %s\n' "$CASE" "$1"; }

cat > "$WORK/log.md" <<'LOG'
# Bug log

**Status**: OPEN · FIXING

---

### BUG-001: Opened bug with `code` in the title
- Status: OPEN
- Severity: S1
- Area/spec: frontend shell / 001, 006

### BUG-002: Fixing
- Status: FIXING (branch x)
- Severity: S2
- Area/spec: gamecatalog / 009

### BUG-003: Fixed locally
- Status: FIXED-LOCAL — waiting for release
- Severity: S3
- Area/spec: platform

### BUG-004: Blocked
- Status: BLOCKED (needs owner)
- Severity: S4
- Area/spec: deploy / 008

### BUG-005: Deployed
- Status: DEPLOYED (`v0.0.1-rc2`)
- Severity: S3
- Area/spec: x

### BUG-006: Verified
- Status: VERIFIED
- Severity: S3
- Area/spec: x

### BUG-007: Verified on prod
- Status: VERIFIED-PROD (2026-10-02)
- Severity: S2
- Area/spec: x

### BUG-008: Question
- Status: WONTFIX-QUESTION
- Severity: S4
- Area/spec: x

### BUG-009: No severity line
- Status: OPEN
LOG

OUT="$WORK/out.json"
BUG_LOG="$WORK/log.md" sh "$SCRIPT" > "$OUT" 2> "$WORK/err"; STATUS=$?
q() { jq -r "$1" "$OUT" 2>/dev/null; }
expect() { [ "$(q "$2")" = "$3" ] && ok || bad "$1: expected '$3', got '$(q "$2")'"; }

CASE="selection"
[ "$STATUS" -eq 0 ] && ok || bad "exit $STATUS"
expect "only open statuses, in order" '[.[].id]|join(",")' "BUG-001,BUG-002,BUG-003,BUG-004,BUG-009"
CASE="fields"
expect "title keeps code ticks" '.[0].title' 'Opened bug with `code` in the title'
expect "status is the first word" '.[1].status' "FIXING"
expect "fixed-local" '.[2].status' "FIXED-LOCAL"
expect "severity" '.[0].severity' "S1"
expect "area" '.[0].area' "frontend shell / 001, 006"
expect "heading line" '.[0].headingLine' "7"
expect "priority mapping S1" '.[0].priority' "Urgent"
expect "priority mapping S2" '.[1].priority' "High"
expect "priority mapping S3" '.[2].priority' "Medium"
expect "priority mapping S4" '.[3].priority' "Low"
expect "missing severity" '.[4].severity' "null"
CASE="missing log"
BUG_LOG="$WORK/none.md" sh "$SCRIPT" >/dev/null 2>&1; [ $? -eq 2 ] && ok || bad "expected exit 2"

echo "open-bugs cases: ${pass_count} passed, ${fail_count} failed"
[ "$fail_count" -eq 0 ]
