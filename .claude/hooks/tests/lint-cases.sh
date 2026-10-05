#!/usr/bin/env bash
# Fixtures for post-lint-check.sh and jordylab-fe/tools/lint-changed.sh (spec 012 FR-018). The real hook and the real shared
# command run against a throwaway fake repo whose node_modules/.bin/eslint is a stub, so no Bun or ESLint install is needed.
#
# Run standalone: bash .claude/hooks/tests/lint-cases.sh
set -uo pipefail

REPOSITORY_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
HOOK="$REPOSITORY_ROOT/.claude/hooks/post-lint-check.sh"
REAL_LINT_COMMAND="$REPOSITORY_ROOT/jordylab-fe/tools/lint-changed.sh"
EDIT_LOCK_LIBRARY="$REPOSITORY_ROOT/.claude/hooks/lib/edit-lock.sh"

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
export TMPDIR="$WORK/tmp"
mkdir -p "$TMPDIR"

pass_count=0
fail_count=0

# Fake repo with the real hook scripts and a stub ESLint.
FAKE="$WORK/repo"
mkdir -p "$FAKE/jordylab-fe/tools" "$FAKE/jordylab-fe/node_modules/.bin" "$FAKE/jordylab-fe/src" \
  "$FAKE/jordylab-fe/libs/ui/helm/x" "$FAKE/jordylab-fe/dist" "$FAKE/jordylab-fe/out-tsc" "$FAKE/other"
cp "$REAL_LINT_COMMAND" "$FAKE/jordylab-fe/tools/lint-changed.sh"
cat >"$FAKE/jordylab-fe/node_modules/.bin/eslint" <<'STUB'
#!/usr/bin/env bash
# Stub ESLint: records that it ran, then prints canned JSON by STUB_MODE.
touch "$STUB_CALLED_MARKER"
file="${@: -1}"
# Like the real Nx plugin: noise on stdout; the JSON report goes to the file given with --output-file.
echo "warning No cached ProjectGraph is available. The rule will be skipped."
output_file=""
for ((i = 1; i <= $#; i++)); do [[ "${!i}" == "--output-file" ]] && output_file="${@:i+1:1}"; done
exec >"${output_file:-/dev/stdout}"
case "${STUB_MODE:-clean}" in
  error) printf '[{"filePath":"%s","messages":[{"ruleId":"no-debugger","severity":2,"message":"Unexpected debugger statement.","line":3,"column":5}]}]' "$file" ;;
  warn) printf '[{"filePath":"%s","messages":[{"ruleId":"no-console","severity":1,"message":"Unexpected console statement.","line":1,"column":1}]}]' "$file" ;;
  fatal) printf '[{"filePath":"%s","messages":[{"ruleId":null,"fatal":true,"severity":2,"message":"Parsing error: Unexpected token","line":1,"column":1}]}]' "$file" ;;
  slow) sleep 10 ;;
  *) printf '[{"filePath":"%s","messages":[]}]' "$file" ;;
esac
STUB
chmod +x "$FAKE/jordylab-fe/node_modules/.bin/eslint"
export STUB_CALLED_MARKER="$WORK/eslint-called"

for name in src/a.ts src/a.spec.ts src/a.html libs/ui/helm/x/h.ts dist/d.ts out-tsc/o.ts; do
  echo "export const x = 1;" >"$FAKE/jordylab-fe/$name"
done
echo "export const y = 1;" >"$FAKE/other/a.ts"

report() { # name verdict detail
  if [[ "$2" == "ok" ]]; then
    pass_count=$((pass_count + 1)); printf '  ok    %s\n' "$1"
  else
    fail_count=$((fail_count + 1)); printf '  FAIL  %s: %s\n' "$1" "$3"
  fi
}

# run_hook <file> [env assignments...] -> sets OUTPUT, EXIT_CODE, SECONDS_TAKEN
run_hook() {
  local file="$1"; shift
  rm -f "$STUB_CALLED_MARKER"
  local start=$SECONDS
  OUTPUT="$(printf '{"tool_input":{"file_path":"%s"}}' "$file" | env "$@" "$HOOK" 2>/dev/null)"
  EXIT_CODE=$?
  SECONDS_TAKEN=$(( SECONDS - start ))
}

expect_silent() { # name file [env...]
  local name="$1" file="$2"; shift 2
  run_hook "$file" "$@"
  if [[ -z "$OUTPUT" && "$EXIT_CODE" == 0 ]]; then report "$name" ok ""; else report "$name" fail "output='$OUTPUT' exit=$EXIT_CODE"; fi
}

expect_not_linted() { # name file
  expect_silent "$1" "$2" STUB_MODE=error
  if [[ -e "$STUB_CALLED_MARKER" ]]; then report "$1 (linter not started)" fail "ESLint was invoked"; fi
}

echo "post-lint-check.sh:"

run_hook "$FAKE/jordylab-fe/src/a.ts" STUB_MODE=error
if [[ "$EXIT_CODE" == 0 ]] && echo "$OUTPUT" | jq -e '.hookSpecificOutput.hookEventName == "PostToolUse"' >/dev/null 2>&1 \
  && echo "$OUTPUT" | jq -r '.hookSpecificOutput.additionalContext' | grep -q "src/a.ts:3:5  error  no-debugger"; then
  report "error file: diagnostic reaches the agent as additionalContext" ok ""
else
  report "error file: diagnostic reaches the agent as additionalContext" fail "output='$OUTPUT' exit=$EXIT_CODE"
fi

run_hook "$FAKE/jordylab-fe/src/a.spec.ts" STUB_MODE=error
echo "$OUTPUT" | jq -e '.hookSpecificOutput.additionalContext' >/dev/null 2>&1 \
  && report "spec files are linted too" ok "" || report "spec files are linted too" fail "output='$OUTPUT'"

run_hook "$FAKE/jordylab-fe/src/a.ts" STUB_MODE=warn
echo "$OUTPUT" | jq -r '.hookSpecificOutput.additionalContext' | grep -q "warn  no-console" \
  && report "warnings are labelled warn" ok "" || report "warnings are labelled warn" fail "output='$OUTPUT'"

expect_silent "clean file: silent" "$FAKE/jordylab-fe/src/a.ts" STUB_MODE=clean
expect_not_linted "ignored path libs/ui/helm: not linted" "$FAKE/jordylab-fe/libs/ui/helm/x/h.ts"
expect_not_linted "ignored path dist: not linted" "$FAKE/jordylab-fe/dist/d.ts"
expect_not_linted "ignored path out-tsc: not linted" "$FAKE/jordylab-fe/out-tsc/o.ts"
expect_not_linted "non-TypeScript file: not linted" "$FAKE/jordylab-fe/src/a.html"
expect_not_linted "TypeScript outside jordylab-fe: not linted" "$FAKE/other/a.ts"
expect_silent "unparsable file (fatal parse error): silent skip" "$FAKE/jordylab-fe/src/a.ts" STUB_MODE=fatal
expect_silent "missing file path: silent" "$FAKE/jordylab-fe/src/missing.ts" STUB_MODE=error

printf 'not json' | "$HOOK" >/dev/null 2>&1; [[ $? == 0 ]] && report "malformed stdin: exit 0" ok "" || report "malformed stdin: exit 0" fail "non-zero"

# Linter missing: a repo without node_modules/.bin/eslint
NOLINT="$WORK/nolint"; mkdir -p "$NOLINT/jordylab-fe/tools" "$NOLINT/jordylab-fe/src"
cp "$REAL_LINT_COMMAND" "$NOLINT/jordylab-fe/tools/lint-changed.sh"; echo "export const z = 1;" >"$NOLINT/jordylab-fe/src/a.ts"
expect_silent "linter not installed: silent" "$NOLINT/jordylab-fe/src/a.ts" STUB_MODE=error

# Time limit: a linter that hangs is cut off at the guard and the hook stays silent
run_hook "$FAKE/jordylab-fe/src/a.ts" STUB_MODE=slow LINT_CHANGED_GUARD_SECONDS=1
if [[ -z "$OUTPUT" && "$EXIT_CODE" == 0 && "$SECONDS_TAKEN" -le 3 ]]; then
  report "slow linter: cut off silently within the time limit (${SECONDS_TAKEN}s)" ok ""
else
  report "slow linter: cut off silently within the time limit" fail "output='$OUTPUT' exit=$EXIT_CODE took=${SECONDS_TAKEN}s"
fi

# Race with the formatter: while the format hook holds the file lock the lint hook skips after about 1 s, never reads the file
# shellcheck source=../lib/edit-lock.sh
. "$EDIT_LOCK_LIBRARY"
edit_lock_acquire "$FAKE/jordylab-fe/src/a.ts" 1
run_hook "$FAKE/jordylab-fe/src/a.ts" STUB_MODE=error
if [[ -z "$OUTPUT" && "$EXIT_CODE" == 0 && ! -e "$STUB_CALLED_MARKER" && "$SECONDS_TAKEN" -le 3 ]]; then
  report "formatter holds the lock: lint skips without reading (${SECONDS_TAKEN}s)" ok ""
else
  report "formatter holds the lock: lint skips without reading" fail "output='$OUTPUT' called=$([[ -e $STUB_CALLED_MARKER ]] && echo yes || echo no) took=${SECONDS_TAKEN}s"
fi
edit_lock_release
run_hook "$FAKE/jordylab-fe/src/a.ts" STUB_MODE=error
echo "$OUTPUT" | jq -e '.hookSpecificOutput' >/dev/null 2>&1 && report "lock released: lint runs again" ok "" || report "lock released: lint runs again" fail "output='$OUTPUT'"

# A lock left behind by a crashed formatter (older than 30 s) is cleared instead of blocking every later edit
LOCK_PATH="$(edit_lock_path "$FAKE/jordylab-fe/src/a.ts")"
mkdir -p "$LOCK_PATH" && touch -t 202001010000 "$LOCK_PATH"
run_hook "$FAKE/jordylab-fe/src/a.ts" STUB_MODE=error
echo "$OUTPUT" | jq -e '.hookSpecificOutput' >/dev/null 2>&1 && report "stale lock (older than 30 s) is cleared: lint runs" ok "" || report "stale lock (older than 30 s) is cleared: lint runs" fail "output='$OUTPUT'"
rmdir "$LOCK_PATH" 2>/dev/null

echo "lint-changed.sh:"
"$FAKE/jordylab-fe/tools/lint-changed.sh" --strict "$FAKE/jordylab-fe/src/a.ts" >/dev/null 2>&1
[[ $? == 0 ]] && report "no findings: exit 0 even with --strict" ok "" || report "no findings: exit 0 even with --strict" fail "non-zero"
STUB_MODE=error "$FAKE/jordylab-fe/tools/lint-changed.sh" --strict "$FAKE/jordylab-fe/src/a.ts" >/dev/null 2>&1
[[ $? == 1 ]] && report "findings with --strict: exit 1" ok "" || report "findings with --strict: exit 1" fail "wrong exit"
STUB_MODE=error "$FAKE/jordylab-fe/tools/lint-changed.sh" "$FAKE/jordylab-fe/src/a.ts" >/dev/null 2>&1
[[ $? == 0 ]] && report "findings without --strict: exit 0" ok "" || report "findings without --strict: exit 0" fail "non-zero"

echo
echo "$pass_count passed, $fail_count failed"
[[ $fail_count == 0 ]]
