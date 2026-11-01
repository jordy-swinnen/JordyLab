#!/usr/bin/env bash
# Prints the state the next agent needs: git, open PRs, the active spec's tasks and open bugs.
# Read-only. Usage: handover_state.sh [spec-number]   (e.g. 013; guessed from branch and commits when omitted)
set -uo pipefail

root=$(git rev-parse --show-toplevel 2>/dev/null) || { echo "Not in a git repository" >&2; exit 1; }
cd "$root" || exit 1
base=main
branch=$(git branch --show-current)

section() { printf '\n## %s\n' "$1"; }

section "Git"
echo "branch: ${branch:-detached at $(git rev-parse --short HEAD)}"
upstream=$(git rev-parse --abbrev-ref --symbolic-full-name '@{u}' 2>/dev/null || true)
if [ -n "$upstream" ]; then
  echo "upstream: $upstream ($(git rev-list --count "$upstream"..HEAD) unpushed, $(git rev-list --count HEAD.."$upstream") behind)"
else
  echo "upstream: none (branch never pushed)"
fi
echo "commits on top of $base:"
git log --format='  %h %s' "$base"..HEAD 2>/dev/null | head -30
echo "uncommitted changes:"
git status --short | sed 's/^/  /'
[ -n "$(git stash list)" ] && { echo "stashes:"; git stash list | sed 's/^/  /'; }

section "Pull requests"
if command -v gh >/dev/null 2>&1; then
  if ! gh pr list --state open --limit 10 --json number,title,headRefName \
    --template '{{range .}}  #{{.number}} {{.title}} [{{.headRefName}}]{{"\n"}}{{end}}' 2>/dev/null; then
    echo "  (gh failed: not logged in, offline or no GitHub remote; PR state unknown)"
  elif [ -n "$branch" ]; then
    gh pr view "$branch" --json number,state,url,statusCheckRollup \
      --template 'this branch: #{{.number}} {{.state}} {{.url}}{{"\n"}}{{range .statusCheckRollup}}  check {{.name}}: {{.conclusion}}{{.status}}{{"\n"}}{{end}}' 2>/dev/null \
      || echo "this branch: no PR"
  fi
else
  echo "  (gh not installed)"
fi

section "Active spec"
spec=${1:-}
if [ -z "$spec" ]; then
  # 1) a number in the branch name, 2) Refs: trailers on this branch, 3) the most recently changed tasks.md
  spec=$(printf '%s' "$branch" | grep -oE '(^|[/-])[0-9]{3}([/-]|$)' | grep -oE '[0-9]{3}' | head -1)
  [ -z "$spec" ] && spec=$(git log --format='%(trailers:key=Refs,valueonly)' "$base"..HEAD 2>/dev/null \
    | tr -s ' ,' '\n\n' | grep -E '^[0-9]{3}(/T[0-9]+)?$' | cut -c1-3 | head -1)
  [ -z "$spec" ] && spec=$(git log -1 --format= --name-only -- 'specs/[0-9]*/tasks.md' 2>/dev/null \
    | grep -oE 'specs/[0-9]{3}' | grep -oE '[0-9]{3}' | head -1)
fi
dir=$(ls -d specs/"${spec:-none}"-*/ 2>/dev/null | head -1)
if [ -z "$dir" ]; then
  echo "none found (pass the spec number as the first argument, or this is NO-CODE work)"
else
  dir=${dir%/}
  echo "spec: $dir"
  [ -f "$dir/HANDOFF-next-agent.md" ] && echo "previous handoff: $dir/HANDOFF-next-agent.md ($(git log -1 --format='%ad, %h' --date=short -- "$dir/HANDOFF-next-agent.md"))"
  if [ -f "$dir/tasks.md" ]; then
    open=$(grep -cE '^- \[ \]' "$dir/tasks.md")
    done_=$(grep -cE '^- \[[xX]\]' "$dir/tasks.md")
    echo "tasks.md: $done_ done, $open open"
    echo "open tasks (first 20, cut at 160 chars):"
    grep -nE '^- \[ \]' "$dir/tasks.md" | head -20 | cut -c1-160 | sed 's/^/  /'
  else
    echo "no tasks.md yet (spec not planned/tasked)"
  fi
fi

section "Open bugs (docs/testing/bug-log.md)"
if [ -f docs/testing/bug-log.md ]; then
  awk '
    /^### BUG-/ { title = $0; sub(/^### /, "", title); next }
    /^- Status:/ && title != "" {
      status = $0; sub(/^- Status: */, "", status)
      if (status !~ /^(DEPLOYED|VERIFIED|WONTFIX)/) printf "  %s — %s\n", substr(title, 1, 90), substr(status, 1, 60)
      title = ""
    }' docs/testing/bug-log.md
else
  echo "  (no bug log)"
fi
