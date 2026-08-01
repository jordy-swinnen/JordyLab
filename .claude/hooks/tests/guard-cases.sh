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

echo "uppercase -R is a documented GNU rm synonym for --recursive"
check block "rm -Rf /"
check block "rm -R ~"
check block "rm -Rf \$HOME"
check pass  "rm -Rf build/"

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

echo "cross-command false positives (chained commands must not leak flags/targets across segments)"
check pass  "ls /home/jordy && rm -rf build/"
check pass  "docker compose down; ls -v"
check pass  "grep -f patterns.txt file.txt && git clean -n"
check block "git push origin --force"

echo "rm flag order/spacing (3+ tokens, long-form) and parent-dir target"
check block "rm -r -v -f /home/jordy"
check block "rm -rf .."
check block "rm --recursive --force /home/jordy"
check block "rm --recursive -f /home/jordy"
check block "rm -r --force /home/jordy"
check block "rm -r --verbose --force /home/jordy"
check pass  "rm --recursive --force build/"

echo "word-boundary false positives (substring matches inside unrelated words)"
check pass  "echo \"please confirm -rf /home before proceeding\""
check pass  "digit clean -f"
check pass  "docker compose down --env-file .env-vault"

echo "recursive rm without an explicit force flag (still destructive non-interactively)"
check block "rm -r /"
check block "rm -r ~"
check block "rm -r \$HOME"
check block "rm --recursive /home/jordy"
check pass  "rm -r build/"
check pass  "rm -r node_modules/"

echo "commands merely mentioned inside another command's argument must not block"
check pass  "echo \"run: rm -rf /home to test\""
check pass  "echo \"run: git clean -f to clean\""
check pass  "echo \"run: git push --force to push\""
check pass  "echo \"run: docker compose down -v to clean\""

echo "real invocations wrapped in common prefixes must still block"
check block "sudo rm -rf /"
check block "VAR=1 rm -rf /home/jordy"

echo "git push force-with-lease must not suppress a separately-present real force flag"
check block "git push --force-with-lease --force origin main"
check block "git push --force --force-with-lease origin main"
check pass  "git push --force-with-lease"

echo "separator characters inside a quoted string must not create a spurious segment"
check pass  "echo \"warning; rm -rf /home/user/tmp is dangerous\""
check pass  "echo \"warning && rm -rf /home/user/tmp is dangerous\""
check pass  "echo \"warning | rm -rf /home/user/tmp is dangerous\""
check pass  "echo 'a && b'"
check pass  "ls /home/jordy && rm -rf build/"

echo "combined short flags must not evade the git push force check"
check block "git push -uf origin main"
check block "git push -fu origin main"
check pass  "git push origin feature-flag-x"

echo "heredocs and multi-line commands are tokenized properly, not line-by-line"
check pass  "cat <<'EOF'
rm -rf /home/user/tmp
EOF"
check pass  "cat <<EOF
Just a note about rm -rf /home
EOF"
check block "cat <<EOF
\$(rm -rf /home)
EOF"

echo "command substitution is recursed into and checked as a real invocation"
check block "echo \$(rm -rf /home)"
check block "echo \`rm -rf /home\`"
check pass  "echo \"\$(rm --help)\""

echo "backslash-escaped quotes are tokenized correctly, not treated as real quote boundaries"
check pass  "echo \"she said \\\"rm -rf /home\\\" as a joke\""

echo
echo "$pass_count passed, $fail_count failed"
[[ "$fail_count" -eq 0 ]]
