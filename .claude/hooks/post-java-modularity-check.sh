#!/usr/bin/env bash
set -euo pipefail
# Post-edit hook: run Spring Modulith boundary tests when a Java file is written/edited.
# Reads tool input from stdin (JSON). Exits 0 always — failures are warnings, not blockers.
# A failing check reaches the agent as PostToolUse JSON `additionalContext` (plain stdout
# at exit 0 only goes to the debug log); a passing check is silent.

INPUT=$(cat)
FILE_PATH=$(echo "$INPUT" | jq -r '.tool_input.file_path // .tool_input.filePath // empty' 2>/dev/null || true)

if [[ "$FILE_PATH" != *.java ]]; then
  exit 0
fi

# Only run for source files (skip test files — modularity tests are authoritative)
if [[ "$FILE_PATH" == */test/* ]]; then
  exit 0
fi

BE_DIR="$(git -C "$(dirname "$FILE_PATH")" rev-parse --show-toplevel 2>/dev/null || true)/jordylab-be"
if [[ ! -f "$BE_DIR/gradlew" ]]; then
  # Try relative from CWD
  BE_DIR="$(pwd)/jordylab-be"
fi

if [[ ! -f "$BE_DIR/gradlew" ]]; then
  exit 0
fi

# The invoking shell often has no JAVA_HOME (e.g. a plain terminal, not an IDE
# run config), which makes the gradlew launcher itself fail before Gradle's
# own toolchain resolution ever runs. Fall back to a JetBrains-managed JDK
# under ~/.jdks if nothing is already on PATH.
if ! command -v java >/dev/null 2>&1 && [[ -z "${JAVA_HOME:-}" ]]; then
  CANDIDATE=$(find "$HOME/.jdks" -maxdepth 1 -type d -name "*-25*" 2>/dev/null | sort -V | tail -1)
  [[ -z "$CANDIDATE" ]] && CANDIDATE=$(find "$HOME/.jdks" -maxdepth 1 -type d ! -name ".jdks" 2>/dev/null | sort -V | tail -1)
  if [[ -n "$CANDIDATE" && -x "$CANDIDATE/bin/java" ]]; then
    export JAVA_HOME="$CANDIDATE"
    export PATH="$JAVA_HOME/bin:$PATH"
  fi
fi

# Advisory only: a failing modularity check must not fail the hook itself, so the
# gradle exit status is turned into a warning instead of being propagated.
GRADLE_STATUS=0
GRADLE_OUTPUT=$(cd "$BE_DIR" && ./gradlew :test --tests "*ModularityTests*" -q --no-daemon 2>&1) || GRADLE_STATUS=$?

if [[ "$GRADLE_STATUS" -ne 0 ]]; then
  TAIL=$(printf '%s\n' "$GRADLE_OUTPUT" | tail -8)
  jq -n --arg file "$FILE_PATH" --arg tail "$TAIL" '{
    hookSpecificOutput: {
      hookEventName: "PostToolUse",
      additionalContext: ("Spring Modulith boundary check failed after editing " + $file + " (advisory; fix the violation in this turn):\n" + $tail)
    }
  }'
fi

exit 0
