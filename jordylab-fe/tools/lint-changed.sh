#!/usr/bin/env bash
# Shared lint command for edited frontend files (spec 012 FR-014). Knows nothing about any agent tool: the Claude Code hook
# (.claude/hooks/post-lint-check.sh) and the OpenCode instruction in jordylab-fe/AGENTS.md both call this.
#   lint-changed.sh [--strict] <file>...
# Prints one diagnostic per line, `path:line:col  error|warn  rule  message` (path relative to jordylab-fe), and nothing else.
# Empty output means clean, skipped, unparsable, linter missing, or time limit reached. Read-only: never fixes.
# Exit code is 0 always; with --strict it is 1 when there are diagnostics (for manual use).
# Linters: ESLint (about 0.6 s per file here) owns the Angular, template, boundary, core and typescript-eslint rules; Oxlint (about
# 10 ms) owns only the oxc and unicorn rules ESLint does not have (.oxlintrc.json, tools/check-lint-ownership.sh). Both run in
# parallel; a linter that is not installed is skipped.
# Env: LINT_CHANGED_GUARD_SECONDS (default 2) is the hard time limit for the linter runs together.
set -u

STRICT=0
if [[ "${1:-}" == "--strict" ]]; then
  STRICT=1
  shift
fi

FRONTEND_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GUARD_SECONDS="${LINT_CHANGED_GUARD_SECONDS:-2}"
ESLINT_BINARY="$FRONTEND_ROOT/node_modules/.bin/eslint"
OXLINT_BINARY="$FRONTEND_ROOT/node_modules/.bin/oxlint"

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
[[ -x "$ESLINT_BINARY" || -x "$OXLINT_BINARY" ]] || exit 0
command -v jq >/dev/null 2>&1 || exit 0

OUTPUT_FILE="$(mktemp)"
OXLINT_OUTPUT_FILE="$(mktemp)"
trap 'rm -f "$OUTPUT_FILE" "$OXLINT_OUTPUT_FILE"' EXIT
LINT_PIDS=()

if [[ -x "$ESLINT_BINARY" ]]; then
  # --output-file, not stdout: the Nx ESLint plugin prints a warning to stdout before the JSON when no project graph is cached.
  ( cd "$FRONTEND_ROOT" && "$ESLINT_BINARY" --format json --output-file "$OUTPUT_FILE" "${FILES[@]}" >/dev/null 2>&1 ) &
  LINT_PIDS+=($!)
fi
if [[ -x "$OXLINT_BINARY" && -f "$FRONTEND_ROOT/.oxlintrc.json" ]]; then
  ( cd "$FRONTEND_ROOT" && "$OXLINT_BINARY" -c .oxlintrc.json --format=json "${FILES[@]}" >"$OXLINT_OUTPUT_FILE" 2>/dev/null ) &
  LINT_PIDS+=($!)
fi

# Hard time limit without `timeout` (not on macOS by default): poll, then kill the whole tree.
kill_tree() {
  local pid="$1" child
  for child in $(pgrep -P "$pid" 2>/dev/null); do
    kill_tree "$child"
  done
  kill "$pid" 2>/dev/null
}
any_running() {
  local pid
  for pid in "${LINT_PIDS[@]}"; do
    kill -0 "$pid" 2>/dev/null && return 0
  done
  return 1
}
DEADLINE=$(( $(date +%s) + GUARD_SECONDS ))
while any_running; do
  if (( $(date +%s) >= DEADLINE )); then
    for pid in "${LINT_PIDS[@]}"; do kill_tree "$pid"; done
    exit 0
  fi
  sleep 0.05
done
wait 2>/dev/null

DIAGNOSTICS="$(jq -r --arg root "$FRONTEND_ROOT/" '
  .[]? | .filePath as $file | .messages[]?
  | select(.fatal != true)
  | "\($file | sub($root; "")):\(.line):\(.column)  \(if .severity == 2 then "error" else "warn" end)  \(.ruleId // "-")  \(.message)"
' "$OUTPUT_FILE" 2>/dev/null)"

OXLINT_DIAGNOSTICS="$(jq -r --arg root "$FRONTEND_ROOT/" '
  .diagnostics[]?
  | "\(.filename | sub($root; "")):\(.labels[0].span.line // 1):\(.labels[0].span.column // 1)  \(if .severity == "error" then "error" else "warn" end)  \(.code)  \(.message)"
' "$OXLINT_OUTPUT_FILE" 2>/dev/null)"
if [[ -n "$OXLINT_DIAGNOSTICS" ]]; then
  DIAGNOSTICS="${DIAGNOSTICS:+$DIAGNOSTICS$'\n'}$OXLINT_DIAGNOSTICS"
fi

[[ -n "$DIAGNOSTICS" ]] || exit 0
echo "$DIAGNOSTICS"
(( STRICT )) && exit 1
exit 0
