#!/usr/bin/env bash
# Print the git state a handover needs: branch, HEAD, worktrees, and uncommitted work.
#
#   scripts/handoff_check.sh [--strict]
#
# --strict exits 1 when the tree is dirty, so it can gate "only hand over when committed".
# It reads git only; it never changes the repository.
set -u
strict=0
[ "${1:-}" = "--strict" ] && strict=1

command -v git >/dev/null 2>&1 || { echo "handoff_check: git not found" >&2; exit 2; }
git rev-parse --is-inside-work-tree >/dev/null 2>&1 || { echo "handoff_check: not a git repository" >&2; exit 2; }

root="$(git rev-parse --show-toplevel)"
branch="$(git rev-parse --abbrev-ref HEAD)"
head="$(git rev-parse --short HEAD)"
echo "repository : $root"
echo "branch     : $branch"
echo "HEAD       : $head"

echo "--- last 3 commits"
git log --oneline -3

echo "--- worktrees"
git worktree list

echo "--- uncommitted work"
dirty="$(git status --porcelain)"
if [ -z "$dirty" ]; then
  echo "clean"
else
  echo "$dirty"
  echo "count: $(printf '%s\n' "$dirty" | wc -l | tr -d ' ')"
fi

if git rev-parse --abbrev-ref --symbolic-full-name '@{upstream}' >/dev/null 2>&1; then
  echo "--- upstream"
  git status -sb | head -1
  ahead="$(git rev-list --count '@{upstream}..HEAD')"
  behind="$(git rev-list --count 'HEAD..@{upstream}')"
  echo "ahead of upstream: $ahead, behind: $behind (do not push without asking; never rewrite pushed history)"
else
  echo "--- upstream: none (local branch)"
fi

if [ -n "$dirty" ] && [ "$strict" -eq 1 ]; then
  echo "handoff_check: FAILED -- commit or restore the changes above before handing over." >&2
  exit 1
fi
echo "handoff_check: ok"
