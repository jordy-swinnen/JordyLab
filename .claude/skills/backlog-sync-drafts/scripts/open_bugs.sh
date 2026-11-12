#!/bin/sh
# Read-only list of the open entries in docs/testing/bug-log.md (status OPEN, FIXING, FIXED-LOCAL or BLOCKED).
# Prints one JSON array on stdout; exit 0, or 2 when the log is missing. Never writes, never calls Kaneo.
# Contract: specs/015-linear-backlog/contracts/draft-inventory.md (open_bugs.sh). Run from the repository root.
set -eu

LOG=${BUG_LOG:-docs/testing/bug-log.md}
if [ ! -f "$LOG" ]; then
  echo "open_bugs: $LOG not found" >&2
  exit 2
fi

awk '
  function flush() {
    if (id != "" && (st == "OPEN" || st == "FIXING" || st == "FIXED-LOCAL" || st == "BLOCKED"))
      printf "%s\t%s\t%s\t%s\t%s\t%s\n", id, title, st, sev, area, line
    id = ""
  }
  /^### BUG-[0-9]+:/ {
    flush()
    id = $2; sub(/:$/, "", id)
    title = $0; sub(/^### BUG-[0-9]+: */, "", title)
    st = ""; sev = ""; area = ""; line = NR
    next
  }
  /^### / { flush(); next }
  id != "" && /^- Status:/   { s = $0; sub(/^- Status: */, "", s); split(s, w, /[ (—]/); st = w[1] }
  id != "" && /^- Severity:/ { sev = $0; sub(/^- Severity: */, "", sev); sub(/ .*/, "", sev) }
  id != "" && /^- Area\/spec:/ { area = $0; sub(/^- Area\/spec: */, "", area) }
  END { flush() }
' "$LOG" | jq -R -s '
  split("\n") | map(select(length > 0) | split("\t")
    | {id: .[0], title: .[1], status: .[2],
       severity: (if .[3] == "" then null else .[3] end),
       area: (if .[4] == "" then null else .[4] end),
       headingLine: (.[5] | tonumber),
       priority: ({"S1":"Urgent","S2":"High","S3":"Medium","S4":"Low"}[.[3]])})'
