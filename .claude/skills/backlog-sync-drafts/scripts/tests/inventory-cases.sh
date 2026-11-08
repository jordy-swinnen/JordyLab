#!/usr/bin/env bash
# Fixture for repo_inventory.sh: builds a temporary tree of drafts and numbered specs, runs the inventory against it
# (INVENTORY_ROOT) and asserts the JSON. No network, nothing outside the temp tree is read.
#
# Run standalone: bash .claude/skills/backlog-sync-drafts/scripts/tests/inventory-cases.sh
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCRIPT="$HERE/../repo_inventory.sh"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

pass_count=0
fail_count=0

ok()   { pass_count=$((pass_count + 1)); }
bad()  { fail_count=$((fail_count + 1)); printf 'FAIL [%s]: %s\n' "$CASE" "$1"; }

mk_draft() { # name title statusline
  mkdir -p "$ROOT/specs/_drafts/$1"
  { printf '# Feature Specification: %s\n\n' "$2"; [ -n "${3:-}" ] && printf '**Status**: %s\n' "$3"; printf '\n### Story (Priority: High)\n'; } > "$ROOT/specs/_drafts/$1/spec-draft.md"
}
mk_spec() { # folder title [statusline]
  mkdir -p "$ROOT/specs/$1"
  { printf '# Feature Specification: %s\n\n' "$2"; [ -n "${3:-}" ] && printf '**Status**: %s\n' "$3"; } > "$ROOT/specs/$1/spec.md"
}
mk_tasks() { # folder done open  (a counting loop, because BSD `seq 1 0` counts down)
  local i=1; : > "$ROOT/specs/$1/tasks.md"
  while [ "$i" -le "$2" ]; do printf -- '- [X] T%03d done thing %s\n' "$i" "$i" >> "$ROOT/specs/$1/tasks.md"; i=$((i + 1)); done
  i=1
  while [ "$i" -le "$3" ]; do printf -- '- [ ] T%03d open thing %s\n' "$((100 + i))" "$i" >> "$ROOT/specs/$1/tasks.md"; i=$((i + 1)); done
}

ROOT="$WORK/tree"
mkdir -p "$ROOT/specs/_drafts"
printf 'beta\t002\n' > "$WORK/overrides.tsv"
printf 'alpha\tGame Catalog\tFeature\n001-alpha\tGame Catalog\tFeature\ndelta\tPlatform\tChore\n' > "$WORK/app-map.tsv"

mk_draft alpha "Alpha Thing" "Draft (reference)"
mk_spec 001-alpha "Alpha Thing"; mk_tasks 001-alpha 2 3
printf '\n**Module**: x\n\n## Overview\n\nAlpha does the\nfirst thing for the owner.\n\nSecond paragraph.\n' >> "$ROOT/specs/001-alpha/spec.md"
mk_draft beta "Beta Thing" "Draft, direction agreed"
mk_spec 002-gamma-thing "Gamma Thing"; mk_tasks 002-gamma-thing 0 4
mk_draft delta "Delta Thing" "Draft, conditional on \`alpha\`"
printf 'Delta is a draft with a story first.\n' >> "$ROOT/specs/_drafts/delta/spec-draft.md"
mk_draft eps "Eps Thing"; mk_draft zeta "Zeta Thing" "Draft"
mk_spec 003-omnibus "Omnibus"; mk_tasks 003-omnibus 5 0
printf 'absorbs specs/_drafts/eps and specs/_drafts/zeta\n' > "$ROOT/specs/003-omnibus/research.md"
mkdir -p "$ROOT/specs/_drafts/onlyresearch"; echo "notes" > "$ROOT/specs/_drafts/onlyresearch/research.md"
mk_spec 004-shipped "Shipped" "Implemented (2026-10-05)"; mk_tasks 004-shipped 1 2
mk_spec 005-almost "Almost"; mk_tasks 005-almost 9 3
mk_spec 006-notasks "No Tasks"

OUT="$WORK/out.json"
run() { INVENTORY_ROOT="$ROOT" INVENTORY_OVERRIDES="$WORK/overrides.tsv" INVENTORY_APPMAP="$WORK/app-map.tsv" sh "$SCRIPT" > "$OUT" 2> "$WORK/err"; STATUS=$?; }
cd / || exit 1   # prove nothing relies on the current directory
run

q() { jq -r "$1" "$OUT" 2>/dev/null; }
expect() { [ "$(q "$2")" = "$3" ] && ok || bad "$1: expected '$3', got '$(q "$2")'"; }

CASE="output"
[ "$STATUS" -eq 0 ] && ok || bad "exit $STATUS: $(cat "$WORK/err")"
jq -e 'type == "array"' "$OUT" >/dev/null 2>&1 && ok || bad "not a JSON array"
expect "sorted by key" '[.[].key] == ([.[].key]|sort)' true
expect "item count (6 specs + 4 draftless-or-undecided drafts)" 'length' 10

CASE="slug match: draft shares the spec issue"
expect "spec 001 links its draft" '.[]|select(.key=="specs/001-alpha")|.draftFolders|join(",")' "specs/_drafts/alpha"
expect "no separate draft item for alpha" '[.[]|select(.key=="specs/_drafts/alpha")]|length' 0
expect "title from spec.md" '.[]|select(.key=="specs/001-alpha")|.title' "Alpha Thing"
expect "draft status wording kept" '.[]|select(.key=="specs/001-alpha")|.statusWording' "Draft (reference)"
expect "priority from the draft" '.[]|select(.key=="specs/001-alpha")|.priority' "High"

CASE="override match"
expect "spec 002 links beta" '.[]|select(.key=="specs/002-gamma-thing")|.draftFolders|join(",")' "specs/_drafts/beta"
expect "how" '.[]|select(.key=="specs/002-gamma-thing")|.matchHow' "override"

CASE="no match"
expect "delta is a draft item" '.[]|select(.key=="specs/_drafts/delta")|.kind' "draft"
expect "delta drafted" '.[]|select(.key=="specs/_drafts/delta")|.suggestedState' "Drafted"
expect "delta confidence" '.[]|select(.key=="specs/_drafts/delta")|.matchConfidence' "none"
expect "related draft from backticks" '.[]|select(.key=="specs/_drafts/delta")|.relatedDrafts|join(",")' "alpha"

CASE="a spec mentioned by two drafts is needs-decision"
expect "eps" '.[]|select(.key=="specs/_drafts/eps")|.matchConfidence' "needs-decision"
expect "eps possible spec" '.[]|select(.key=="specs/_drafts/eps")|.possibleSpecs|join(",")' "specs/003-omnibus"
expect "zeta" '.[]|select(.key=="specs/_drafts/zeta")|.matchConfidence' "needs-decision"
expect "omnibus does not claim them" '.[]|select(.key=="specs/003-omnibus")|.draftFolders|length' 0

CASE="missing status line / spec draft"
expect "eps status null" '.[]|select(.key=="specs/_drafts/eps")|.statusWording' "null"
expect "folder without spec-draft: title is the folder name" '.[]|select(.key=="specs/_drafts/onlyresearch")|.title' "onlyresearch"

CASE="spec states from tasks.md"
expect "some done, some open" '.[]|select(.key=="specs/001-alpha")|.suggestedState' "In Progress"
expect "counts" '.[]|select(.key=="specs/001-alpha")|"\(.tasksDone)/\(.tasksTotal)"' "2/5"
expect "tasks exist, none done" '.[]|select(.key=="specs/002-gamma-thing")|.suggestedState' "Todo"
expect "all done" '.[]|select(.key=="specs/003-omnibus")|.suggestedState' "Done"
expect "status line Implemented wins" '.[]|select(.key=="specs/004-shipped")|.suggestedState' "Done"
expect "three open tasks: done candidate" '.[]|select(.key=="specs/005-almost")|.doneCandidate' "true"
expect "four open tasks: not a candidate" '.[]|select(.key=="specs/002-gamma-thing")|.doneCandidate' "false"
expect "no tasks.md" '.[]|select(.key=="specs/006-notasks")|.suggestedState' "Specified"
CASE="summary"
expect "overview paragraph, flattened" '.[]|select(.key=="specs/001-alpha")|.summary' "Alpha does the first thing for the owner."
expect "draft summary when the spec has none" '.[]|select(.key=="specs/_drafts/delta")|.summary' "Delta is a draft with a story first."
expect "empty when there is no prose" '.[]|select(.key=="specs/006-notasks")|.summary' ""
expect "open task listed" '.[]|select(.key=="specs/005-almost")|.openTasks[0].id' "T101"

CASE="app and type map"
expect "from the spec entry" '.[]|select(.key=="specs/001-alpha")|.suggestedApp' "Game Catalog"
expect "draft entry" '.[]|select(.key=="specs/_drafts/delta")|"\(.suggestedApp)/\(.suggestedType)"' "Platform/Chore"
expect "unmapped is null" '.[]|select(.key=="specs/003-omnibus")|.suggestedApp' "null"

CASE="missing drafts folder"
INVENTORY_ROOT="$WORK/empty" sh "$SCRIPT" >/dev/null 2>&1; [ $? -eq 2 ] && ok || bad "expected exit 2"

CASE="read-only"
[ -z "$(find "$ROOT" -newer "$WORK/overrides.tsv" -type f ! -name 'tasks.md' ! -name 'spec.md' ! -name 'spec-draft.md' ! -name research.md 2>/dev/null)" ] && ok || bad "the script wrote a file in the tree"

echo "inventory cases: ${pass_count} passed, ${fail_count} failed"
[ "$fail_count" -eq 0 ]
