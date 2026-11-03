#!/usr/bin/env bash
# Resolves a deploy target and decides whether to apply it (spec 019, research D3, D5, D6).
# Prints key=value lines for $GITHUB_OUTPUT. Exit 0 for every decision; exit 2 for invalid arguments.
#
# Usage: deploy-decision.sh --target <sha|release-tag> [--live-tag <tag>] --automatic true|false
set -euo pipefail

usage() {
  echo "usage: deploy-decision.sh --target <input> [--live-tag <tag>] --automatic true|false" >&2
  exit 2
}

target_input=""
live_tag=""
automatic=""

while [ $# -gt 0 ]; do
  case "$1" in
    --target) target_input="${2:-}"; shift 2 ;;
    --live-tag) live_tag="${2:-}"; shift 2 ;;
    --automatic) automatic="${2:-}"; shift 2 ;;
    *) usage ;;
  esac
done

[ -n "$target_input" ] || usage
case "$automatic" in
  true|false) ;;
  *) usage ;;
esac

decision=""
target_commit=""
image_tag=""
live_commit="unknown"
reason=""

emit() {
  printf 'decision=%s\n' "$decision"
  printf 'target_commit=%s\n' "$target_commit"
  printf 'image_tag=%s\n' "$image_tag"
  printf 'live_commit=%s\n' "$live_commit"
  printf 'reason=%s\n' "$reason"
}

refuse() {
  decision=refused
  reason="$1"
  target_commit=""
  image_tag=""
  live_commit=""
  emit
  exit 0
}

if [[ "$target_input" =~ ^[0-9a-f]{40}$ ]]; then
  target_commit="$target_input"
  image_tag="sha-$target_commit"
  git cat-file -e "$target_commit^{commit}" 2>/dev/null || refuse "commit $target_commit is not in the repository history"
elif [[ "$target_input" =~ ^v[0-9]+\.[0-9]+\.[0-9]+(-rc[0-9]+)?$ ]]; then
  target_commit="$(git rev-parse --verify --quiet "refs/tags/$target_input^{commit}")" || refuse "tag $target_input does not exist"
  image_tag="$target_input"
else
  refuse "target '$target_input' is neither a 40-character commit sha nor a release tag vX.Y.Z or vX.Y.Z-rcN"
fi

if [[ "$live_tag" =~ ^sha-([0-9a-f]{40})$ ]]; then
  live_commit="${BASH_REMATCH[1]}"
elif [[ "$live_tag" =~ ^v[0-9]+\.[0-9]+\.[0-9]+(-rc[0-9]+)?$ ]]; then
  resolved_live_commit="$(git rev-parse --verify --quiet "refs/tags/$live_tag^{commit}" || true)"
  if [ -n "$resolved_live_commit" ]; then
    live_commit="$resolved_live_commit"
  fi
fi

if [ "$automatic" = "true" ] \
  && [ "$live_commit" != "unknown" ] \
  && [ "$target_commit" != "$live_commit" ] \
  && git merge-base --is-ancestor "$target_commit" "$live_commit" 2>/dev/null; then
  decision=superseded
  reason="target $target_commit is older than the live commit $live_commit"
else
  decision=proceed
  reason="target $target_commit is applied"
fi

emit
