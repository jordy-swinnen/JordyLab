#!/usr/bin/env bash
# Shared lint command for edited frontend files (spec 012 FR-014). Knows nothing about any agent tool: the Claude Code hook
# (.claude/hooks/post-lint-check.sh) and the OpenCode instruction in jordylab-fe/AGENTS.md both call this.
#   lint-changed.sh [--strict] <file>...
# Prints one diagnostic per line, `path:line:col  error|warn  rule  message` (path relative to jordylab-fe), and nothing else.
# Empty output means clean, skipped, unparsable, linter missing, or time limit reached. Read-only: never fixes.
# Exit code is 0 always; with --strict it is 1 when there are diagnostics (for manual use).
# Linter: ESLint (single-file run is about 0.6 s here). The Oxlint branch is added when Oxlint is installed (spec 012 T027).
# Env: LINT_CHANGED_GUARD_SECONDS (default 2) is the hard time limit for the linter run.
set -u

STRICT=0
if [[ "${1:-}" == "--strict" ]]; then
  STRICT=1
  shift
fi

FRONTEND_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GUARD_SECONDS="${LINT_CHANGED_GUARD_SECONDS:-2}"
ESLINT_BINARY="$FRONTEND_ROOT/node_modules/.bin/eslint"

# Same ignores as eslint.config.mjs (dist, out-tsc, generated spartan helm libs) plus dependencies.
should_lint() {
  local absolute_path="$1"
  [[ "$absolute_path" == *.ts && -f "$absolute_path" ]] || return 1
  [[ "$absolute_path" == "$FRONTEND_ROOT"/* ]] || return 1
  case "$absolute_path" in
    */node_modules/*|*/dist/*|*/out-tsc/*|"$FRONTEND_ROOT"/libs/ui/helm/*) return 1 ;;
  esac
  return 0
}

absolute_path_of() {
  local path="$1"
  if [[ "$path" == /* ]]; then
    echo "$path"
  else
    echo "$(pwd)/$path"
  fi
}

FILES=()
for argument in "$@"; do
  absolute_path="$(absolute_path_of "$argument")"
  if should_lint "$absolute_path"; then
    FILES+=("$absolute_path")
  fi
done

[[ ${#FILES[@]} -gt 0 ]] || exit 0
[[ -x "$ESLINT_BINARY" ]] || exit 0
command -v jq >/dev/null 2>&1 || exit 0

OUTPUT_FILE="$(mktemp)"
trap 'rm -f "$OUTPUT_FILE"' EXIT

# --output-file, not stdout: the Nx ESLint plugin prints a warning to stdout before the JSON when no project graph is cached.
( cd "$FRONTEND_ROOT" && "$ESLINT_BINARY" --format json --output-file "$OUTPUT_FILE" "${FILES[@]}" >/dev/null 2>&1 ) &
LINT_PID=$!

# Hard time limit without `timeout` (not on macOS by default): poll, then kill the whole tree.
kill_tree() {
  local pid="$1" child
  for child in $(pgrep -P "$pid" 2>/dev/null); do
    kill_tree "$child"
  done
  kill "$pid" 2>/dev/null
}
DEADLINE=$(( $(date +%s) + GUARD_SECONDS ))
while kill -0 "$LINT_PID" 2>/dev/null; do
  if (( $(date +%s) >= DEADLINE )); then
    kill_tree "$LINT_PID"
    exit 0
  fi
  sleep 0.05
done
wait "$LINT_PID" 2>/dev/null

DIAGNOSTICS="$(jq -r --arg root "$FRONTEND_ROOT/" '
  .[]? | .filePath as $file | .messages[]?
  | select(.fatal != true)
  | "\($file | sub($root; "")):\(.line):\(.column)  \(if .severity == 2 then "error" else "warn" end)  \(.ruleId // "-")  \(.message)"
' "$OUTPUT_FILE" 2>/dev/null)"

[[ -n "$DIAGNOSTICS" ]] || exit 0
echo "$DIAGNOSTICS"
(( STRICT )) && exit 1
exit 0
