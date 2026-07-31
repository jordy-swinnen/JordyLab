#!/usr/bin/env bash
# Post-edit hook: run Spring Modulith boundary tests when a Java file is written/edited.
# Reads tool input from stdin (JSON). Exits 0 always — failures are warnings, not blockers.

INPUT=$(cat)
FILE_PATH=$(echo "$INPUT" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('file_path', d.get('path', '')))" 2>/dev/null)

if [[ "$FILE_PATH" != *.java ]]; then
  exit 0
fi

# Only run for source files (skip test files — modularity tests are authoritative)
if [[ "$FILE_PATH" == */test/* ]]; then
  exit 0
fi

BE_DIR="$(git -C "$(dirname "$FILE_PATH")" rev-parse --show-toplevel 2>/dev/null)/jordylab-be"
if [[ ! -f "$BE_DIR/gradlew" ]]; then
  # Try relative from CWD
  BE_DIR="$(pwd)/jordylab-be"
fi

if [[ ! -f "$BE_DIR/gradlew" ]]; then
  exit 0
fi

echo "⚙️  Running Spring Modulith boundary check..."
cd "$BE_DIR" && ./gradlew :test --tests "*ModularityTests*" -q --no-daemon 2>&1 | tail -8
