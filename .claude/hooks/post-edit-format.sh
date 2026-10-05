#!/usr/bin/env bash
set -euo pipefail

HOOK_DIRECTORY="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/edit-lock.sh
. "$HOOK_DIRECTORY/lib/edit-lock.sh"

# Read the tool input from stdin
INPUT=$(cat)
FILE_PATH=$(echo "$INPUT" | jq -r '.tool_input.file_path // .tool_input.filePath // empty')

if [[ -z "$FILE_PATH" ]] || [[ ! -f "$FILE_PATH" ]]; then
  exit 0
fi

EXTENSION="${FILE_PATH##*.}"

case "$EXTENSION" in
  py)
    ruff format "$FILE_PATH" 2>/dev/null || true
    ruff check --fix "$FILE_PATH" 2>/dev/null || true
    ;;
  ts|html|css|scss|json)
    # Prettier is installed in jordylab-fe only; the repo rule is bun/bunx, never npx.
    if [[ "$FILE_PATH" == */jordylab-fe/* ]]; then
      # Hold the per-file lock while Prettier rewrites the file so the parallel lint hook never reads it half written
      # (spec 012 FR-017). If the lock cannot be taken in 3 s, format anyway: formatting matters more than lint timing.
      edit_lock_acquire "$FILE_PATH" 3 || true
      (cd "${FILE_PATH%%/jordylab-fe/*}/jordylab-fe" && bunx prettier --write "$FILE_PATH" 2>/dev/null) || true
      edit_lock_release
    fi
    ;;
  java)
    # Only run spotless if it's configured in the project
    if grep -q "spotless" jordylab-be/build.gradle.kts 2>/dev/null; then
      cd jordylab-be && ./gradlew spotlessApply 2>/dev/null || true
    fi
    ;;
esac

exit 0
