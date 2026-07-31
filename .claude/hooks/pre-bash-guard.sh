#!/usr/bin/env bash
set -euo pipefail

# Read the tool input from stdin
INPUT=$(cat)
COMMAND=$(echo "$INPUT" | jq -r '.tool_input.command // empty')

if [[ -z "$COMMAND" ]]; then
  exit 0
fi

# Block dangerous rm commands
if echo "$COMMAND" | grep -qE 'rm\s+-rf\s+(/|\.)\s*$'; then
  echo "BLOCKED: Destructive rm -rf on root or current directory is not allowed." >&2
  exit 2
fi

# Block git reset --hard
if echo "$COMMAND" | grep -qE 'git\s+reset\s+--hard'; then
  echo "BLOCKED: git reset --hard is not allowed. Use git stash or git checkout for specific files." >&2
  exit 2
fi

# Block git push --force
if echo "$COMMAND" | grep -qE 'git\s+push\s+.*--force'; then
  echo "BLOCKED: git push --force is not allowed. Use --force-with-lease if you must force push." >&2
  exit 2
fi

# Block direct DDL
if echo "$COMMAND" | grep -qiE '(DROP\s+(TABLE|SCHEMA|DATABASE)|TRUNCATE)'; then
  echo "BLOCKED: Direct DDL (DROP/TRUNCATE) is not allowed. Use Flyway migrations instead." >&2
  exit 2
fi

# Block docker compose down -v
if echo "$COMMAND" | grep -qE 'docker\s+compose\s+down\s+.*-v'; then
  echo "BLOCKED: docker compose down -v destroys volumes. Use docker compose down without -v." >&2
  exit 2
fi

exit 0
