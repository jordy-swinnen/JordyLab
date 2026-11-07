#!/usr/bin/env bash
# Creates the Kaneo role and database in the production Postgres (spec 022, research R6, contracts/deployment.md).
# Runs on the host as the deploy user, from the deploy step after the application release. Idempotent: a second run
# changes nothing except the role's password, which follows the environment file.
#
# Usage: ensure-kaneo-database.sh [environment-file]        (default /opt/jordylab/env/prod.env)
#
# The password reaches Postgres only through standard input. It is never in an argument list, and no value is printed.
# Names and the password are checked against a strict pattern first, so nothing from the file can change the SQL.
set -euo pipefail

readonly env_file="${1:-/opt/jordylab/env/prod.env}"
readonly compose_file=/opt/jordylab/compose/compose.yaml

fail() {
  echo "ensure-kaneo-database: $*" >&2
  exit 1
}

# Prints the value of NAME from the environment file (NAME='value' lines, as render-env.sh writes them).
# Prints nothing when NAME is absent.
read_value() {
  local name="$1"
  local line
  line="$(grep -E "^${name}=" "$env_file" | tail -n 1 || true)"
  line="${line#"${name}="}"
  line="${line#\'}"
  line="${line%\'}"
  printf '%s' "$line"
}

db_name="$(read_value KANEO_DB_NAME)"
db_name="${db_name:-kaneo}"
db_user="$(read_value KANEO_DB_USER)"
db_user="${db_user:-kaneo}"
db_password="$(read_value KANEO_DB_PASSWORD)"

[[ -n "$db_password" ]] || fail "KANEO_DB_PASSWORD is not set in $env_file"
[[ "$db_name" =~ ^[a-z][a-z0-9_]{0,62}$ ]] || fail "KANEO_DB_NAME must be a lower-case identifier"
[[ "$db_user" =~ ^[a-z][a-z0-9_]{0,62}$ ]] || fail "KANEO_DB_USER must be a lower-case identifier"
[[ "$db_password" =~ ^[A-Za-z0-9]{16,}$ ]] ||
  fail "KANEO_DB_PASSWORD must be at least 16 letters or digits (openssl rand -hex 16 is enough)"

# The SQL is generated from validated values only. \gexec runs the statement built by each SELECT, so a second run
# finds the role and the database already there.
if ! {
  printf '%s\n' '\set ON_ERROR_STOP on'
  printf "SELECT 'CREATE ROLE %s LOGIN' WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = '%s') \\gexec\n" \
    "$db_user" "$db_user"
  printf "ALTER ROLE %s LOGIN PASSWORD '%s';\n" "$db_user" "$db_password"
  printf "SELECT 'CREATE DATABASE %s OWNER %s' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = '%s') \\gexec\n" \
    "$db_name" "$db_user" "$db_name"
  printf 'REVOKE CONNECT ON DATABASE %s FROM PUBLIC;\n' "$db_name"
  printf 'GRANT CONNECT ON DATABASE %s TO %s;\n' "$db_name" "$db_user"
} | docker compose --env-file "$env_file" -f "$compose_file" \
    exec -T postgres psql -X -q -v ON_ERROR_STOP=1 -U postgres -d postgres -f -; then
  fail "the Postgres step failed; the output above says why (no value is printed here)"
fi

echo "ensure-kaneo-database: role and database are in place"
