#!/usr/bin/env bash
# Cases for the Kaneo part of deploy/compose/scripts/render-env.sh (spec 022, T019). A fake sops prints a fixture
# instead of decrypting, so the cases need no age key. The fixture is built from the script's own key tables, so a
# key added to the tables is covered here too.
#
# Run standalone: bash deploy/compose/scripts/tests/render-env-kaneo-cases.sh
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
RENDER="$REPO_ROOT/deploy/compose/scripts/render-env.sh"

pass_count=0
fail_count=0
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
mkdir -p "$work/bin"

# Builds a multi-document YAML of every Secret the render reads. Arguments: "all" (every key present, default),
# "no-kaneo" (the Kaneo keys absent from jordylab-secrets), "no-anthropic" (one existing optional key misspelt away),
# "no-ntfy-topic" (one required key absent).
build_fixture() {
  local mode="$1"
  python3 -I - "$RENDER" "$mode" <<'PY' > "$work/secrets.yaml"
import re, sys
script, mode = sys.argv[1], sys.argv[2]
text = open(script, encoding="utf-8").read()
pairs = re.findall(r'\("([A-Z0-9_]+)", "([a-z0-9-]+)", "([A-Za-z0-9_]+)", (?:True|False)\)', text)
soft = re.findall(r'\("([A-Z0-9_]+)", "([a-z0-9-]+)", "([A-Za-z0-9_]+)"\)', text)
secrets = {}
for name, secret, key, in [(p[0], p[1], p[2]) for p in pairs]:
    secrets.setdefault(secret, {})[key] = "value-" + key.lower()
for name, secret, key in soft:
    if mode == "no-kaneo" and key.startswith("KANEO_"):
        continue
    if mode == "null-github" and key.startswith("KANEO_GITHUB_"):
        # The placeholders on main: YAML null, which the render must treat as empty.
        secrets.setdefault(secret, {})[key] = "null"
        continue
    secrets.setdefault(secret, {})[key] = "soft-" + key.lower()
if mode == "no-anthropic":
    secrets["jordylab-secrets"].pop("ANTHROPIC_API_KEY", None)
if mode == "no-ntfy-topic":
    secrets["jordylab-secrets"].pop("NTFY_TOPIC", None)
docs = []
for secret, data in sorted(secrets.items()):
    body = "\n".join(f"  {k}: {v}" for k, v in sorted(data.items()))
    docs.append(f"apiVersion: v1\nkind: Secret\nmetadata:\n  name: {secret}\nstringData:\n{body}")
print("\n---\n".join(docs))
PY
}

# A fake sops that prints the fixture (the render pipes sops output straight into python).
cat > "$work/bin/sops" <<'FAKE'
#!/usr/bin/env bash
cat "$FAKE_SECRETS"
FAKE
chmod +x "$work/bin/sops"

render() {
  # Sets $output (the rendered file) and $code. Extra arguments are NAME=value pairs for the environment.
  output="$(env PATH="$work/bin:$PATH" SOPS_AGE_KEY=test FAKE_SECRETS="$work/secrets.yaml" \
    SECRETS_FILE="$work/secrets.yaml" "$@" bash "$RENDER" 2>"$work/stderr")"
  code=$?
}

check() {
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

has_line() { grep -qxF -- "$1" <<<"$output"; }
ok() { [ "$code" -eq 0 ]; }
failed() { [ "$code" -ne 0 ]; }

# 1. Every Kaneo key present: the values are rendered.
build_fixture all
render
check "renders when every key is present" ok
check "renders the Kaneo auth secret" has_line "KANEO_AUTH_SECRET='soft-kaneo_auth_secret'"
check "renders the Kaneo database password" has_line "KANEO_DB_PASSWORD='soft-kaneo_db_password'"
check "renders the Kaneo database name as a literal" has_line "KANEO_DB_NAME='kaneo'"
check "renders the Kaneo database user as a literal" has_line "KANEO_DB_USER='kaneo'"

# 2. The Kaneo keys absent from the secrets file: the render still succeeds, with empty values.
build_fixture no-kaneo
render
check "renders while the Kaneo keys are not added yet" ok
check "an absent Kaneo auth secret renders empty" has_line "KANEO_AUTH_SECRET=''"
check "an absent Kaneo password renders empty" has_line "KANEO_DB_PASSWORD=''"
check "the application values still render" has_line "POSTGRES_PASSWORD='value-password'"

# 2b. The GitHub App keys added as YAML null (the placeholders on main): they render empty, the rest still renders.
build_fixture null-github
render
check "renders while the GitHub App keys are null placeholders" ok
check "a null GitHub App ID renders empty" has_line "KANEO_GITHUB_APP_ID=''"
check "a null webhook secret renders empty" has_line "KANEO_GITHUB_WEBHOOK_SECRET=''"
check "a null private key renders empty" has_line "KANEO_GITHUB_PRIVATE_KEY_BASE64=''"
check "the Kaneo auth secret still renders beside them" has_line "KANEO_AUTH_SECRET='soft-kaneo_auth_secret'"

# 3. Existing keys stay strict: a misspelt optional key still fails the render.
build_fixture no-anthropic
render
check "a missing existing optional key still fails the render" failed
check "the failure names the key, not a value" bash -c "grep -q ANTHROPIC_API_KEY '$work/stderr'"

# 4. A missing required key still fails the render.
build_fixture no-ntfy-topic
render
check "a missing required key still fails the render" failed

# 5. Settings from repository variables: defaults when unset, the variable when set.
build_fixture all
render
check "BACKLOG_PROVIDER defaults to linear" has_line "BACKLOG_PROVIDER='linear'"
check "KANEO_DISABLE_REGISTRATION defaults to true" has_line "KANEO_DISABLE_REGISTRATION='true'"
check "KANEO_MEM_LIMIT defaults to 768m" has_line "KANEO_MEM_LIMIT='768m'"
check "KANEO_DISABLE_WORKSPACE_CREATION defaults to true" has_line "KANEO_DISABLE_WORKSPACE_CREATION='true'"
render BACKLOG_PROVIDER=kaneo KANEO_MEM_LIMIT=1g
check "BACKLOG_PROVIDER follows the repository variable" has_line "BACKLOG_PROVIDER='kaneo'"
check "KANEO_MEM_LIMIT follows the repository variable" has_line "KANEO_MEM_LIMIT='1g'"
render KANEO_DISABLE_WORKSPACE_CREATION=false
check "KANEO_DISABLE_WORKSPACE_CREATION follows the repository variable" has_line "KANEO_DISABLE_WORKSPACE_CREATION='false'"
render BACKLOG_PROVIDER=
check "an empty repository variable takes the default" has_line "BACKLOG_PROVIDER='linear'"

echo
echo "$pass_count passed, $fail_count failed"
[ "$fail_count" -eq 0 ]
