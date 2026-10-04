#!/usr/bin/env bash
set -euo pipefail

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
      (cd "${FILE_PATH%%/jordylab-fe/*}/jordylab-fe" && bunx prettier --write "$FILE_PATH" 2>/dev/null) || true
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
