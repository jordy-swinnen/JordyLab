#!/usr/bin/env bash
# Table-driven fixture for pre-bash-guard.sh. Pipes each command through the hook
# as the real {"tool_input":{"command": ...}} PreToolUse payload and asserts the
# exit code matches the expected verdict (block = exit 2, pass = exit 0).
#
# Run standalone: bash .claude/hooks/tests/guard-cases.sh
set -uo pipefail

HOOK_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
HOOK="$HOOK_DIR/pre-bash-guard.sh"

pass_count=0
fail_count=0

# args: expected ("block"|"pass")  command
check() {
  local expected="$1" cmd="$2" actual
  if printf '%s' "$cmd" | jq -Rs '{tool_input: {command: .}}' | "$HOOK" >/dev/null 2>&1; then
    actual="pass"
  else
    actual="block"
  fi
  if [[ "$actual" == "$expected" ]]; then
    pass_count=$((pass_count + 1))
    printf '  ok    [%-5s] %s\n' "$expected" "$cmd"
  else
    fail_count=$((fail_count + 1))
    printf '  FAIL  [expected %-5s got %-5s] %s\n' "$expected" "$actual" "$cmd"
  fi
}

echo "rm -rf"
check block "rm -rf /"
check block "rm -rf /home/jordy"
check block "rm -rf /etc"
check block "rm -rf ~"
check block "rm -rf \$HOME"
check block "rm -fr /"
check block "rm -rf ./"
check block "rm -rf *"
check block "rm -rf / --no-preserve-root"
check pass  "rm -rf build/"
check pass  "rm -rf node_modules/"

echo "git reset"
check block "git reset --hard"
check block "git reset --hard origin/main"
check pass  "git reset --soft HEAD~1"

echo "git clean"
check block "git clean -fdx"
check block "git clean -f"
check block "git clean --force"
check pass  "git clean -n"
check pass  "git clean -ndx"

echo "git push force"
check block "git push --force"
check block "git push --force origin main"
check block "git push -f origin main"
check pass  "git push --force-with-lease"
check pass  "git push origin main"

echo "DDL"
check block "DROP TABLE users"
check block "drop table users"
check block "TRUNCATE orders"
check pass  "SELECT * FROM users"

echo "docker compose down"
check block "docker compose down -v"
check block "docker-compose down -v"
check block "docker compose -f prod.yml down -v"
check block "docker compose down --volumes"
check pass  "docker compose down"
check pass  "docker compose up -d"

echo
echo "$pass_count passed, $fail_count failed"
[[ "$fail_count" -eq 0 ]]
