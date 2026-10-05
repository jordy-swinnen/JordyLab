#!/usr/bin/env bash
# Post-edit hook: lint the TypeScript file that was just written/edited and hand the findings to the agent in the same turn
# (spec 012 US2). Advisory only: never blocks, never edits, silent when there is nothing to report, always exits 0.
# Only .ts files under jordylab-fe/ are checked; the shared command applies the ESLint ignores and the time limit.
# Time budget (also in specs/012-technical-improvements/contracts/lint-changed-command.md): lock wait 1 s + linter guard 2 s
# = 3 s worst case, inside the 5 s `timeout` in .claude/settings.json.
set -u

HOOK_DIRECTORY="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/edit-lock.sh
. "$HOOK_DIRECTORY/lib/edit-lock.sh"

INPUT=$(cat)
FILE_PATH=$(echo "$INPUT" | jq -r '.tool_input.file_path // .tool_input.filePath // empty' 2>/dev/null)

[[ -n "$FILE_PATH" && -f "$FILE_PATH" ]] || exit 0
[[ "$FILE_PATH" == *.ts ]] || exit 0
[[ "$FILE_PATH" == */jordylab-fe/* ]] || exit 0

LINT_COMMAND="${JORDYLAB_LINT_COMMAND:-${FILE_PATH%%/jordylab-fe/*}/jordylab-fe/tools/lint-changed.sh}"
[[ -x "$LINT_COMMAND" ]] || exit 0

# If the formatter holds the file for more than a second, skip instead of waiting: the next edit lints it again.
edit_lock_acquire "$FILE_PATH" 1 || exit 0
DIAGNOSTICS="$("$LINT_COMMAND" "$FILE_PATH" 2>/dev/null)"
edit_lock_release

[[ -n "$DIAGNOSTICS" ]] || exit 0

jq -n --arg file "$FILE_PATH" --arg diagnostics "$DIAGNOSTICS" '{
  hookSpecificOutput: {
    hookEventName: "PostToolUse",
    additionalContext: ("Lint findings in " + $file + " (fix them in this turn; ESLint owns Angular, template and module-boundary rules):\n" + $diagnostics)
  }
}'
exit 0
