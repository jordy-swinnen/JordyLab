#!/usr/bin/env bash
set -euo pipefail
# Post-edit hook: flag Mockito/Vitest test-convention violations from AGENTS.md
# on the file that was just written/edited. Advisory only — never blocks, always
# exits 0, silent when the file is clean. Warnings reach the agent as PostToolUse
# JSON `additionalContext` (plain stdout at exit 0 only goes to the debug log), so
# they get fixed before they reach review.

INPUT=$(cat)
FILE_PATH=$(echo "$INPUT" | jq -r '.tool_input.file_path // .tool_input.filePath // empty' 2>/dev/null || true)

if [[ -z "$FILE_PATH" ]] || [[ ! -f "$FILE_PATH" ]]; then
  exit 0
fi

WARNINGS=()

if [[ "$FILE_PATH" == *Test.java && "$FILE_PATH" == */test/* ]]; then
  if grep -qE '\bany\(' "$FILE_PATH"; then
    WARNINGS+=("uses Mockito any() — jordylab-be/AGENTS.md forbids it; use explicit values or a named+asserted ArgumentCaptor")
  fi
  if grep -qE 'ArgumentCaptor\.forClass\([^)]*\)\.capture\(\)' "$FILE_PATH"; then
    WARNINGS+=("creates an ArgumentCaptor inline and calls .capture() without assigning it to a variable — this is any() in disguise")
  fi
  # grep exits 1 when the file has no assertThat( at all; that must not abort the hook (set -e + pipefail)
  ASSERT_COUNT=$({ grep -oE 'assertThat\(' "$FILE_PATH" || true; } | wc -l | tr -d ' ')
  if [[ "$ASSERT_COUNT" -ge 2 ]] && ! grep -q 'assertSoftly' "$FILE_PATH"; then
    WARNINGS+=("has $ASSERT_COUNT assertThat(...) calls but no assertSoftly — jordylab-be/AGENTS.md wants assertSoftly for multi-assertion tests")
  fi
fi

if [[ "$FILE_PATH" == *.spec.ts ]]; then
  if grep -qE 'useClass\s*:' "$FILE_PATH"; then
    WARNINGS+=("provides a dependency with useClass — jordylab-fe/AGENTS.md wants useValue + vi.fn() instead of a hand-written mock class")
  fi
  if grep -q 'as unknown as' "$FILE_PATH"; then
    WARNINGS+=("casts an injected dependency with 'as unknown as' — usually a symptom of a useClass mock; hold the mock's signals/spies from describe scope instead")
  fi
fi

if [[ ${#WARNINGS[@]} -gt 0 ]]; then
  MESSAGE="Test convention check — $FILE_PATH (advisory; fix these in this turn):"
  for warning in "${WARNINGS[@]}"; do
    MESSAGE+=$'\n'"  - $warning"
  done
  jq -n --arg message "$MESSAGE" '{
    hookSpecificOutput: {
      hookEventName: "PostToolUse",
      additionalContext: $message
    }
  }'
fi

exit 0
