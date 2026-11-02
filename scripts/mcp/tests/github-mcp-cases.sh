#!/usr/bin/env bash
# Table-driven fixture for scripts/mcp/github-mcp.sh. Each case builds a stub bin directory (a fake `gh` and a
# fake `bunx` that only record how they were called), runs the launcher with PATH set to that directory alone and
# asserts exit code, stderr and what the stub bunx saw. No network, no real token.
#
# Run standalone: bash scripts/mcp/tests/github-mcp-cases.sh
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
LAUNCHER="$REPO_ROOT/scripts/mcp/github-mcp.sh"
FAKE_TOKEN="gho_FAKE0token0for0tests"
URL="https://api.githubcopilot.com/mcp/"

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

pass_count=0
fail_count=0

ok()   { pass_count=$((pass_count + 1)); }
fail() { fail_count=$((fail_count + 1)); printf 'FAIL [%s]: %s\n' "$CASE" "$1"; }

# args: gh_mode ("ok"|"fail"|"empty"|"absent")  bunx_mode ("ok"|"absent")  [bunx_exit]
# Leaves STATUS, OUT, ERR, ARGS (one per line) and SEEN_ENV in the case directory variables.
run_case() {
  local gh_mode="$1" bunx_mode="$2" bunx_exit="${3:-0}"
  local dir="$WORK/$CASE"
  mkdir -p "$dir/bin"
  : > "$dir/args"; : > "$dir/env"

  case "$gh_mode" in
    ok)    printf '#!/bin/sh\n[ "$1 $2" = "auth token" ] && { printf "%%s\\n" "%s"; exit 0; }\nexit 1\n' "$FAKE_TOKEN" > "$dir/bin/gh" ;;
    fail)  printf '#!/bin/sh\necho "not logged in" >&2\nexit 1\n' > "$dir/bin/gh" ;;
    empty) printf '#!/bin/sh\nexit 0\n' > "$dir/bin/gh" ;;
    absent) ;;
  esac
  [ -f "$dir/bin/gh" ] && chmod +x "$dir/bin/gh"

  if [ "$bunx_mode" = "ok" ]; then
    printf '#!/bin/sh\nfor a in "$@"; do printf "%%s\\n" "$a" >> "%s/args"; done\nprintf "%%s\\n" "$GH_MCP_AUTH" > "%s/env"\nexit %s\n' \
      "$dir" "$dir" "$bunx_exit" > "$dir/bin/bunx"
    chmod +x "$dir/bin/bunx"
  fi

  OUT="$(PATH="$dir/bin" GITHUB_MCP_EXTRA_PATH="" /bin/sh "$LAUNCHER" 2>"$dir/err")"
  STATUS=$?
  ERR="$(cat "$dir/err")"
  ARGS="$(cat "$dir/args")"
  SEEN_ENV="$(cat "$dir/env")"
}

expect_status() { [ "$STATUS" -eq "$1" ] && ok || fail "expected exit $1, got $STATUS"; }
expect_err()    { case "$ERR" in *"$1"*) ok ;; *) fail "stderr lacks '$1'" ;; esac; }
expect_no_bunx_call() { [ -z "$ARGS" ] && ok || fail "bunx was called"; }
expect_clean_stdout() { [ -z "$OUT" ] && ok || fail "stdout must stay empty (it carries the MCP protocol)"; }

CASE="gh auth token fails"
run_case fail ok
expect_status 1; expect_err "gh auth login"; expect_no_bunx_call; expect_clean_stdout

CASE="gh prints an empty token"
run_case empty ok
expect_status 1; expect_err "gh auth login"; expect_no_bunx_call

CASE="gh not on PATH"
run_case absent ok
expect_status 1; expect_err "install"; expect_no_bunx_call; expect_clean_stdout

CASE="bunx not on PATH"
run_case ok absent
expect_status 2; expect_err "bunx"; expect_clean_stdout

CASE="success hands the endpoint and header placeholder to the bridge"
run_case ok ok
expect_status 0
case "$ARGS" in *"$URL"*) ok ;; *) fail "endpoint URL not passed" ;; esac
case "$ARGS" in *'Authorization:${GH_MCP_AUTH}'*) ok ;; *) fail "header placeholder not passed" ;; esac
[ "$(printf '%s\n' "$ARGS" | grep -c -x -F -e "$URL")" -eq 1 ] && ok || fail "bunx should get the URL exactly once"
[ "$SEEN_ENV" = "Bearer $FAKE_TOKEN" ] && ok || fail "bridge did not receive the token through GH_MCP_AUTH"

CASE="the token never appears in output or arguments"
run_case ok ok
case "$OUT$ERR$ARGS" in *"$FAKE_TOKEN"*) fail "token leaked" ;; *) ok ;; esac

CASE="pinned version"
run_case ok ok
first_arg="$(printf '%s\n' "$ARGS" | head -n 1)"
printf '%s' "$first_arg" | grep -Eq '^mcp-remote@[0-9]+\.[0-9]+\.[0-9]+$' && ok || fail "package argument is '$first_arg', not an exact version"
case "$first_arg" in *latest*|*next*) fail "tag instead of version" ;; *) ok ;; esac

CASE="bridge exit code passes through"
run_case ok ok 7
expect_status 7

echo "github-mcp cases: ${pass_count} passed, ${fail_count} failed"
[ "$fail_count" -eq 0 ]
