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
    npx prettier --write "$FILE_PATH" 2>/dev/null || true
    ;;
  java)
    # Only run spotless if it's configured in the project
    if grep -q "spotless" jordylab-be/build.gradle.kts 2>/dev/null; then
      cd jordylab-be && ./gradlew spotlessApply 2>/dev/null || true
    fi
    ;;
esac

exit 0
