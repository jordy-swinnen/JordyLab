#!/usr/bin/env bash
set -euo pipefail
# Post-edit hook: flag Mockito/Vitest test-convention violations from AGENTS.md
# on the file that was just written/edited. Advisory only — never blocks,
# only surfaces a warning so it gets fixed before it reaches review.

INPUT=$(cat)
FILE_PATH=$(echo "$INPUT" | jq -r '.tool_input.file_path // .tool_input.filePath // empty')

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
  ASSERT_COUNT=$(grep -oE 'assertThat\(' "$FILE_PATH" | wc -l | tr -d ' ')
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
  echo "⚠️  Test convention check — $FILE_PATH:"
  for warning in "${WARNINGS[@]}"; do
    echo "  - $warning"
  done
fi

exit 0
