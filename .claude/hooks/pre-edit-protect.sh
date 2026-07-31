#!/usr/bin/env bash
set -euo pipefail

# Read the tool input from stdin
INPUT=$(cat)
FILE_PATH=$(echo "$INPUT" | jq -r '.tool_input.file_path // .tool_input.filePath // empty')

if [[ -z "$FILE_PATH" ]]; then
  exit 0
fi

# Block edits to applied Flyway migrations (V* files that already exist on disk)
if echo "$FILE_PATH" | grep -qE 'db/migration/V[0-9]'; then
  if [[ -f "$FILE_PATH" ]]; then
    echo "BLOCKED: Cannot modify applied Flyway migration: $FILE_PATH. Create a new migration instead." >&2
    exit 2
  fi
fi

# Block edits to lock files
BASENAME=$(basename "$FILE_PATH")
if [[ "$BASENAME" == "bun.lock" || "$BASENAME" == "bun.lockb" || "$BASENAME" == "package-lock.json" ]]; then
  echo "BLOCKED: Cannot edit lock file: $BASENAME. Use the package manager to update dependencies." >&2
  exit 2
fi

# Block edits to .env files
if [[ "$BASENAME" == .env* ]]; then
  echo "BLOCKED: Cannot edit environment file: $BASENAME. Edit .env files manually for security." >&2
  exit 2
fi

# Block edits to .git internals
if echo "$FILE_PATH" | grep -qE '(^|/)\.git/'; then
  echo "BLOCKED: Cannot edit .git internals: $FILE_PATH." >&2
  exit 2
fi

exit 0
