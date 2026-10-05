#!/usr/bin/env bash
# Fixtures for post-test-convention-check.sh and post-java-modularity-check.sh. Both are advisory PostToolUse hooks: a warning
# must reach the agent as JSON `hookSpecificOutput.additionalContext` on stdout (plain stdout at exit 0 only goes to the debug
# log), a clean file or a passing check must stay silent, and the exit code is always 0. The real hooks run against throwaway
# files; the modularity hook runs against a fake repo whose jordylab-be/gradlew is a stub, so no JDK or Gradle is needed.
#
# Run standalone: bash .claude/hooks/tests/advisory-cases.sh
set -uo pipefail

REPOSITORY_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
CONVENTION_HOOK="$REPOSITORY_ROOT/.claude/hooks/post-test-convention-check.sh"
MODULARITY_HOOK="$REPOSITORY_ROOT/.claude/hooks/post-java-modularity-check.sh"

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

pass_count=0
fail_count=0

report() { # name verdict detail
  if [[ "$2" == "ok" ]]; then
    pass_count=$((pass_count + 1)); printf '  ok    %s\n' "$1"
  else
    fail_count=$((fail_count + 1)); printf '  FAIL  %s: %s\n' "$1" "$3"
  fi
}

# run_hook <hook> <file> [env assignments...] -> sets OUTPUT, EXIT_CODE. Runs from an empty directory so the modularity hook's
# "relative to the working directory" fallback can never reach the real jordylab-be.
EMPTY="$WORK/empty"; mkdir -p "$EMPTY"
run_hook() {
  local hook="$1" file="$2"; shift 2
  OUTPUT="$(cd "$EMPTY" && printf '{"tool_input":{"file_path":"%s"}}' "$file" | env "$@" "$hook" 2>/dev/null)"
  EXIT_CODE=$?
}

# context_of: the additionalContext of OUTPUT, or fails when OUTPUT is not exactly one PostToolUse JSON document
context_of() {
  echo "$OUTPUT" | jq -er 'select(.hookSpecificOutput.hookEventName == "PostToolUse") | .hookSpecificOutput.additionalContext' 2>/dev/null
}

expect_silent() { # name hook file [env...]
  local name="$1" hook="$2" file="$3"; shift 3
  run_hook "$hook" "$file" "$@"
  if [[ -z "$OUTPUT" && "$EXIT_CODE" == 0 ]]; then report "$name" ok ""; else report "$name" fail "output='$OUTPUT' exit=$EXIT_CODE"; fi
}

expect_warning() { # name hook file expected-text [env...]
  local name="$1" hook="$2" file="$3" expected="$4"; shift 4
  run_hook "$hook" "$file" "$@"
  local context
  if [[ "$EXIT_CODE" == 0 ]] && context="$(context_of)" && [[ "$context" == *"$expected"* ]]; then
    report "$name" ok ""
  else
    report "$name" fail "expected '$expected' in additionalContext; output='$OUTPUT' exit=$EXIT_CODE"
  fi
}

# ---------------------------------------------------------------- post-test-convention-check.sh
echo "post-test-convention-check.sh:"

JAVA_TEST_DIRECTORY="$WORK/be/src/test/java/x"; mkdir -p "$JAVA_TEST_DIRECTORY" "$WORK/be/src/main/java/x" "$WORK/fe"

cat >"$JAVA_TEST_DIRECTORY/AnyTest.java" <<'EOF'
class AnyTest { void t() { verify(repo).save(any(Order.class)); } }
EOF
cat >"$JAVA_TEST_DIRECTORY/CaptorTest.java" <<'EOF'
class CaptorTest { void t() { verify(repo).save(ArgumentCaptor.forClass(Order.class).capture()); } }
EOF
cat >"$JAVA_TEST_DIRECTORY/SoftTest.java" <<'EOF'
class SoftTest { void t() { assertThat(a).isEqualTo(1); assertThat(b).isEqualTo(2); } }
EOF
cat >"$JAVA_TEST_DIRECTORY/CleanTest.java" <<'EOF'
class CleanTest { void t() { assertSoftly(s -> { assertThat(a).isEqualTo(1); assertThat(b).isEqualTo(2); }); } }
EOF
cat >"$JAVA_TEST_DIRECTORY/SingleTest.java" <<'EOF'
class SingleTest { void t() { assertThat(a).isEqualTo(1); } }
EOF
cat >"$JAVA_TEST_DIRECTORY/AllTest.java" <<'EOF'
class AllTest { void t() { verify(repo).save(any(Order.class)); verify(repo).save(ArgumentCaptor.forClass(Order.class).capture()); assertThat(a).isEqualTo(1); assertThat(b).isEqualTo(2); } }
EOF
# same content as AnyTest but not a test class, and a "Test.java" outside a test directory: both are out of scope
cp "$JAVA_TEST_DIRECTORY/AnyTest.java" "$WORK/be/src/main/java/x/Service.java"
cp "$JAVA_TEST_DIRECTORY/AnyTest.java" "$WORK/be/src/main/java/x/NotATest.java"
cat >"$WORK/fe/class.spec.ts" <<'EOF'
providers: [{ provide: Api, useClass: FakeApi }]
EOF
cat >"$WORK/fe/cast.spec.ts" <<'EOF'
const api = TestBed.inject(Api) as unknown as FakeApi;
EOF
cat >"$WORK/fe/clean.spec.ts" <<'EOF'
providers: [{ provide: Api, useValue: { load: vi.fn() } }]
EOF
cat >"$WORK/fe/class.ts" <<'EOF'
providers: [{ provide: Api, useClass: RealApi }]
EOF

expect_warning "Mockito any(): warning reaches the agent as additionalContext" "$CONVENTION_HOOK" "$JAVA_TEST_DIRECTORY/AnyTest.java" "uses Mockito any()"
expect_warning "inline ArgumentCaptor.capture(): warning" "$CONVENTION_HOOK" "$JAVA_TEST_DIRECTORY/CaptorTest.java" "any() in disguise"
expect_warning "two assertThat without assertSoftly: warning" "$CONVENTION_HOOK" "$JAVA_TEST_DIRECTORY/SoftTest.java" "has 2 assertThat(...) calls but no assertSoftly"
expect_warning "spec.ts useClass: warning" "$CONVENTION_HOOK" "$WORK/fe/class.spec.ts" "useValue + vi.fn()"
expect_warning "spec.ts 'as unknown as': warning" "$CONVENTION_HOOK" "$WORK/fe/cast.spec.ts" "'as unknown as'"
expect_warning "the warning names the edited file" "$CONVENTION_HOOK" "$JAVA_TEST_DIRECTORY/AnyTest.java" "$JAVA_TEST_DIRECTORY/AnyTest.java"

run_hook "$CONVENTION_HOOK" "$JAVA_TEST_DIRECTORY/AllTest.java"
context="$(context_of)"
if [[ "$EXIT_CODE" == 0 && "$(echo "$OUTPUT" | jq -s 'length' 2>/dev/null)" == 1 && "$context" == *"uses Mockito any()"* \
  && "$context" == *"any() in disguise"* && "$context" == *"no assertSoftly"* ]]; then
  report "several violations: one JSON document carries all of them" ok ""
else
  report "several violations: one JSON document carries all of them" fail "output='$OUTPUT' exit=$EXIT_CODE"
fi

if printf '{"tool_input":{"filePath":"%s"}}' "$JAVA_TEST_DIRECTORY/AnyTest.java" | "$CONVENTION_HOOK" 2>/dev/null | jq -e '.hookSpecificOutput.additionalContext' >/dev/null 2>&1; then
  report "filePath spelling of the tool input is understood" ok ""
else
  report "filePath spelling of the tool input is understood" fail "no JSON"
fi

expect_silent "clean Java test: silent" "$CONVENTION_HOOK" "$JAVA_TEST_DIRECTORY/CleanTest.java"
expect_silent "single assertThat: silent" "$CONVENTION_HOOK" "$JAVA_TEST_DIRECTORY/SingleTest.java"
expect_silent "any() in a main source file: silent" "$CONVENTION_HOOK" "$WORK/be/src/main/java/x/Service.java"
expect_silent "Test.java outside a test directory: silent" "$CONVENTION_HOOK" "$WORK/be/src/main/java/x/NotATest.java"
expect_silent "clean spec.ts: silent" "$CONVENTION_HOOK" "$WORK/fe/clean.spec.ts"
expect_silent "useClass in a non-spec .ts file: silent" "$CONVENTION_HOOK" "$WORK/fe/class.ts"
expect_silent "missing file: silent" "$CONVENTION_HOOK" "$JAVA_TEST_DIRECTORY/Missing.java"

printf '{"tool_input":{}}' | "$CONVENTION_HOOK" >/dev/null 2>&1; if [[ $? == 0 ]]; then report "no file path: exit 0" ok ""; else report "no file path: exit 0" fail "non-zero"; fi
OUTPUT="$(printf 'not json' | "$CONVENTION_HOOK" 2>/dev/null)"; EXIT_CODE=$?
if [[ -z "$OUTPUT" && "$EXIT_CODE" == 0 ]]; then report "malformed stdin: silent, exit 0" ok ""; else report "malformed stdin: silent, exit 0" fail "output='$OUTPUT' exit=$EXIT_CODE"; fi

# ---------------------------------------------------------------- post-java-modularity-check.sh
echo "post-java-modularity-check.sh:"

# Fake repo: a git work tree (the hook finds the root with git) holding a stub Gradle wrapper that records that it ran and
# behaves by STUB_MODE.
FAKE="$WORK/repo"
# A second work tree without jordylab-be/gradlew (a nested folder would resolve to the first repo's root).
NOBE="$WORK/nobe"
mkdir -p "$FAKE/jordylab-be/src/main/java/x" "$FAKE/jordylab-be/src/test/java/x" "$NOBE/src/main/java/x"
git -C "$FAKE" init -q
git -C "$NOBE" init -q
cat >"$FAKE/jordylab-be/gradlew" <<'STUB'
#!/usr/bin/env bash
# Stub Gradle wrapper: records that it ran, then behaves by STUB_MODE.
touch "$STUB_CALLED_MARKER"
case "${STUB_MODE:-pass}" in
  fail)
    echo "ModularityTests > verifiesModuleStructure() FAILED"
    echo "    Module 'gamecatalog' depends on non-exposed type dev.jordy.jordylab.fna.internal.Feed"
    exit 1 ;;
  noisy) echo "Deprecated Gradle features were used in this build."; exit 0 ;;
  long) for i in $(seq 1 20); do echo "line $i"; done; exit 1 ;;
  *) exit 0 ;;
esac
STUB
chmod +x "$FAKE/jordylab-be/gradlew"
export STUB_CALLED_MARKER="$WORK/gradle-called"
echo "class A {}" >"$FAKE/jordylab-be/src/main/java/x/A.java"
echo "class ATest {}" >"$FAKE/jordylab-be/src/test/java/x/ATest.java"
echo "class B {}" >"$NOBE/src/main/java/x/B.java"
echo "plain" >"$FAKE/jordylab-be/src/main/java/x/notes.txt"

rm -f "$STUB_CALLED_MARKER"
expect_warning "failing boundary check: violation reaches the agent as additionalContext" "$MODULARITY_HOOK" \
  "$FAKE/jordylab-be/src/main/java/x/A.java" "depends on non-exposed type" STUB_MODE=fail
if [[ -e "$STUB_CALLED_MARKER" ]]; then report "failing boundary check: gradle was started" ok ""; else report "failing boundary check: gradle was started" fail "stub not invoked"; fi

expect_warning "the warning names the edited file" "$MODULARITY_HOOK" \
  "$FAKE/jordylab-be/src/main/java/x/A.java" "$FAKE/jordylab-be/src/main/java/x/A.java" STUB_MODE=fail

rm -f "$STUB_CALLED_MARKER"
expect_silent "passing boundary check: silent" "$MODULARITY_HOOK" "$FAKE/jordylab-be/src/main/java/x/A.java" STUB_MODE=pass
if [[ -e "$STUB_CALLED_MARKER" ]]; then report "passing boundary check: gradle was started" ok ""; else report "passing boundary check: gradle was started" fail "stub not invoked"; fi
expect_silent "gradle exits 0 with noise on stdout: silent (only a failure is a finding)" "$MODULARITY_HOOK" "$FAKE/jordylab-be/src/main/java/x/A.java" STUB_MODE=noisy

run_hook "$MODULARITY_HOOK" "$FAKE/jordylab-be/src/main/java/x/A.java" STUB_MODE=long
context="$(context_of)"
if [[ "$EXIT_CODE" == 0 && "$context" == *"line 20"* && "$context" == *"line 13"* && "$context" != *"line 12"* ]]; then
  report "long gradle output: only the last 8 lines are sent" ok ""
else
  report "long gradle output: only the last 8 lines are sent" fail "output='$OUTPUT' exit=$EXIT_CODE"
fi

not_started() { # name file
  rm -f "$STUB_CALLED_MARKER"
  expect_silent "$1" "$MODULARITY_HOOK" "$2" STUB_MODE=fail
  [[ -e "$STUB_CALLED_MARKER" ]] && report "$1 (gradle not started)" fail "stub was invoked"
}
not_started "Java test file: not checked" "$FAKE/jordylab-be/src/test/java/x/ATest.java"
not_started "non-Java file: not checked" "$FAKE/jordylab-be/src/main/java/x/notes.txt"
not_started "repo without jordylab-be/gradlew: not checked" "$NOBE/src/main/java/x/B.java"

printf '{"tool_input":{}}' | "$MODULARITY_HOOK" >/dev/null 2>&1; if [[ $? == 0 ]]; then report "no file path: exit 0" ok ""; else report "no file path: exit 0" fail "non-zero"; fi
OUTPUT="$(printf 'not json' | "$MODULARITY_HOOK" 2>/dev/null)"; EXIT_CODE=$?
if [[ -z "$OUTPUT" && "$EXIT_CODE" == 0 ]]; then report "malformed stdin: silent, exit 0" ok ""; else report "malformed stdin: silent, exit 0" fail "output='$OUTPUT' exit=$EXIT_CODE"; fi

# A Java file outside any git work tree: the hook falls back to ./jordylab-be of the working directory (empty here)
mkdir -p "$WORK/loose"; echo "class L {}" >"$WORK/loose/L.java"
OUTPUT="$(cd "$EMPTY" && printf '{"tool_input":{"file_path":"%s"}}' "$WORK/loose/L.java" | STUB_MODE=fail "$MODULARITY_HOOK" 2>/dev/null)"; EXIT_CODE=$?
if [[ -z "$OUTPUT" && "$EXIT_CODE" == 0 ]]; then report "file outside a git repo: silent, exit 0" ok ""; else report "file outside a git repo: silent, exit 0" fail "output='$OUTPUT' exit=$EXIT_CODE"; fi

echo
echo "$pass_count passed, $fail_count failed"
[[ $fail_count == 0 ]]
