#!/usr/bin/env bash
# Cases for deploy/compose/scripts/ensure-kaneo-database.sh (spec 022, T013). A fake docker on PATH records its
# arguments and standard input, so no container runtime is needed and nothing outside a temporary folder is touched.
#
# Run standalone: bash deploy/compose/scripts/tests/ensure-kaneo-database-cases.sh
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
SCRIPT="$REPO_ROOT/deploy/compose/scripts/ensure-kaneo-database.sh"
# Generated per run, so no password-shaped literal is ever committed (the secret scan would flag one).
PASSWORD="$(openssl rand -hex 16)"

pass_count=0
fail_count=0
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

mkdir -p "$work/bin"
cat > "$work/bin/docker" <<'FAKE'
#!/usr/bin/env bash
# Records the argument list and the standard input; the password must only ever appear in the latter.
printf '%s\n' "$*" >> "$FAKE_DOCKER_ARGS"
cat >> "$FAKE_DOCKER_STDIN"
exit "${FAKE_DOCKER_EXIT:-0}"
FAKE
chmod +x "$work/bin/docker"

write_env() {
  # $1: file name; the remaining arguments are NAME=value pairs written as the rendered file writes them.
  local file="$work/$1"
  shift
  : > "$file"
  for pair in "$@"; do
    printf "%s\n" "${pair%%=*}='${pair#*=}'" >> "$file"
  done
  echo "$file"
}

reset_fake() {
  : > "$work/args"
  : > "$work/stdin"
}

run_script() {
  # args: environment file. Sets $output and $code.
  output="$(env PATH="$work/bin:$PATH" FAKE_DOCKER_ARGS="$work/args" FAKE_DOCKER_STDIN="$work/stdin" \
    bash "$SCRIPT" "$1" 2>&1)"
  code=$?
}

check() {
  # args: description, condition command...
  local description="$1"
  shift
  if "$@"; then
    echo "PASS $description"
    pass_count=$((pass_count + 1))
  else
    echo "FAIL $description"
    fail_count=$((fail_count + 1))
  fi
}

stdin_has() { grep -q -F -- "$1" "$work/stdin"; }
args_have() { grep -q -F -- "$1" "$work/args"; }
output_has() { grep -q -F -- "$1" <<<"$output"; }
output_lacks() { ! grep -q -F -- "$1" <<<"$output"; }
ok() { [ "$code" -eq 0 ]; }
refused() { [ "$code" -ne 0 ] && grep -q -F -- "ensure-kaneo-database:" <<<"$output"; }

# 1. A good environment file: the SQL runs through the stack's Postgres, with the password only in standard input.
env_file="$(write_env prod.env "KANEO_DB_NAME=kaneo" "KANEO_DB_USER=kaneo" "KANEO_DB_PASSWORD=$PASSWORD")"
reset_fake
run_script "$env_file"
check "creates the role and the database when the environment file is complete" ok
check "the SQL creates the role if it is missing" stdin_has "CREATE ROLE kaneo"
check "the SQL sets the role password from the environment file" stdin_has "ALTER ROLE kaneo LOGIN PASSWORD '$PASSWORD'"
check "the SQL creates the database owned by the role" stdin_has "CREATE DATABASE kaneo OWNER kaneo"
check "the SQL closes the database to PUBLIC" stdin_has "REVOKE CONNECT ON DATABASE kaneo FROM PUBLIC"
check "the password never appears in the docker arguments" output_lacks "$PASSWORD"
check "the password never appears in the docker arguments file" bash -c "! grep -q -F -- '$PASSWORD' '$work/args'"
check "the script prints no password" output_lacks "$PASSWORD"

# 2. Running again is safe: the same command succeeds a second time.
reset_fake
run_script "$env_file"
check "a second run succeeds (idempotent)" ok

# 3. A missing password stops the run before docker is called, and the message names the variable only.
env_file="$(write_env missing.env "KANEO_DB_NAME=kaneo" "KANEO_DB_USER=kaneo")"
reset_fake
run_script "$env_file"
check "a missing password is refused" refused
check "the message names the missing variable" output_has "KANEO_DB_PASSWORD"
check "docker is not called when a value is missing" bash -c "[ ! -s '$work/args' ]"

# 4. A password that could break the SQL is refused (quotes, spaces, semicolons).
env_file="$(write_env quote.env "KANEO_DB_NAME=kaneo" "KANEO_DB_USER=kaneo" "KANEO_DB_PASSWORD=ab'cd;drop")"
reset_fake
run_script "$env_file"
check "a password with a quote or a semicolon is refused" refused
check "docker is not called for an unsafe password" bash -c "[ ! -s '$work/args' ]"
check "the refusal does not print the unsafe value" output_lacks "ab'cd"

# 5. A database or role name outside the lower-case identifier rule is refused.
env_file="$(write_env name.env "KANEO_DB_NAME=Kaneo;DROP" "KANEO_DB_USER=kaneo" "KANEO_DB_PASSWORD=$PASSWORD")"
reset_fake
run_script "$env_file"
check "an unsafe database name is refused" refused
check "docker is not called for an unsafe name" bash -c "[ ! -s '$work/args' ]"

# 6. A failing docker call fails the script.
env_file="$(write_env prod.env "KANEO_DB_NAME=kaneo" "KANEO_DB_USER=kaneo" "KANEO_DB_PASSWORD=$PASSWORD")"
reset_fake
output="$(env PATH="$work/bin:$PATH" FAKE_DOCKER_ARGS="$work/args" FAKE_DOCKER_STDIN="$work/stdin" FAKE_DOCKER_EXIT=3 \
  bash "$SCRIPT" "$env_file" 2>&1)"
code=$?
check "a failing psql call fails the script" refused

echo
echo "$pass_count passed, $fail_count failed"
[ "$fail_count" -eq 0 ]
