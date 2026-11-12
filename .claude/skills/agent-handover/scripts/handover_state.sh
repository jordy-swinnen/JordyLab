#!/usr/bin/env bash
# Prints the state the next agent needs: git, open PRs, the active ticket and open bugs.
# Read-only. Usage: handover_state.sh [JOR-number]   (e.g. 122; guessed from the branch and the Refs trailers)
# The ticket's own state (columns, stories, decisions) is on the board: read it with get_task_by_ticket_id.
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

section "Active ticket"
ticket=${1:-}
if [ -z "$ticket" ]; then
  # 1) jor-<n> in the branch name, 2) a JOR-<n> (or JOR-<n>/D<k>) in the Refs trailers of this branch
  ticket=$(printf '%s' "$branch" | grep -oiE 'jor-[0-9]+' | head -1 | sed 's/.*-//')
  [ -z "$ticket" ] && ticket=$(git log --format='%(trailers:key=Refs,valueonly)' "$base"..HEAD 2>/dev/null \
    | tr -s ' ,' '\n\n' | grep -oE '^JOR-[0-9]+' | head -1 | sed 's/JOR-//')
fi
if [ -n "$ticket" ]; then
  echo "JOR-$ticket  (read it with get_task_by_ticket_id: ticketId JOR-$ticket, projectId"
  echo "n98q252x7sd5objcw94xrj9t, workspaceId vnXfguYyxl5dmvNuO1S82f8sH7e9HRTP)"
  [ -f "docs/handoff/HANDOFF-JOR-$ticket.md" ] && echo "previous handoff: docs/handoff/HANDOFF-JOR-$ticket.md"
else
  echo "no ticket found (pass the JOR number as the first argument, or this is NO-CODE work)"
fi

section "Frozen spec (only for branches that predate JOR-122)"
spec=${1:-}
if [ -z "$spec" ]; then
  spec=$(git log -1 --format= --name-only -- 'specs/[0-9]*/tasks.md' 2>/dev/null \
    | grep -oE 'specs/[0-9]{3}' | grep -oE '[0-9]{3}' | head -1)
fi
dir=$(ls -d specs/"${spec:-none}"-*/ 2>/dev/null | head -1)
if [ -z "$dir" ]; then
  echo "none (specs/001-024 are frozen history; new work has no tasks.md)"
else
  dir=${dir%/}
  echo "spec: $dir"
  [ -f "$dir/HANDOFF-next-agent.md" ] && echo "previous handoff: $dir/HANDOFF-next-agent.md ($(git log -1 --format='%ad, %h' --date=short -- "$dir/HANDOFF-next-agent.md"))"
  if [ -f "$dir/tasks.md" ]; then
    open=$(grep -cE '^- \[ \]' "$dir/tasks.md")
    done_=$(grep -cE '^- \[[xX]\]' "$dir/tasks.md")
    echo "tasks.md: $done_ done, $open open"
    grep -nE '^- \[ \]' "$dir/tasks.md" | head -20 | cut -c1-160 | sed 's/^/  /'
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
