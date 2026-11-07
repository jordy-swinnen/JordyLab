#!/usr/bin/env bash
# Cases for the Kaneo check in deploy/scripts/smoke-check.sh (spec 022, T014). A fake curl on PATH answers each URL
# with a status code chosen by the case, so no network is needed. The Kaneo check only warns: it never fails the run.
#
# Run standalone: bash deploy/scripts/tests/smoke-check-kaneo-cases.sh
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
SMOKE="$REPO_ROOT/deploy/scripts/smoke-check.sh"

pass_count=0
fail_count=0
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
mkdir -p "$work/bin"

# The fake curl prints the status code for the path it was asked for. Each code comes from an environment variable.
cat > "$work/bin/curl" <<'FAKE'
#!/usr/bin/env bash
url="${@: -1}"
case "$url" in
  */api/health) code="${FAKE_KANEO_HEALTH:-200}" ;;
  */api/settings/users) code="${FAKE_API_CODE:-401}" ;;
  */api/identity/auth/csrf) code="${FAKE_CSRF_CODE:-204}" ;;
  */auth/realms/jordylab) code="${FAKE_OLD_AUTH_CODE:-404}" ;;
  *) code="${FAKE_HOME_CODE:-200}" ;;
esac
printf '%s' "$code"
exit 0
FAKE
chmod +x "$work/bin/curl"

run_smoke() {
  # Sets $output and $code. The retry settings keep every case fast.
  output="$(env PATH="$work/bin:$PATH" "$@" 2>&1)"
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

ok() { [ "$code" -eq 0 ]; }
failed() { [ "$code" -eq 1 ]; }
output_has() { grep -q -F -- "$1" <<<"$output"; }

# 1. Kaneo healthy: the run passes and the Kaneo line is printed.
run_smoke FAKE_KANEO_HEALTH=200 "$SMOKE" --base-url https://jordylab.be --kaneo-url https://kaneo.jordylab.be \
  --attempts 1 --delay-seconds 0
check "a healthy Kaneo keeps the run green" ok
check "a healthy Kaneo is reported" output_has "smoke kaneo"

# 2. Kaneo down, the application healthy: the run stays green and a warning is printed.
run_smoke FAKE_KANEO_HEALTH=503 "$SMOKE" --base-url https://jordylab.be --kaneo-url https://kaneo.jordylab.be \
  --attempts 1 --delay-seconds 0
check "a Kaneo outage never fails the application smoke check" ok
check "a Kaneo outage prints a warning, not an error" output_has "::warning::"
check "the warning names Kaneo" output_has "kaneo"

# 3. Kaneo down and an application check failing: the run fails, as before.
run_smoke FAKE_KANEO_HEALTH=503 FAKE_API_CODE=500 "$SMOKE" --base-url https://jordylab.be \
  --kaneo-url https://kaneo.jordylab.be --attempts 1 --delay-seconds 0
check "an application failure still fails the run" failed

# 4. Without --kaneo-url the script behaves exactly as before: no Kaneo line at all.
run_smoke FAKE_KANEO_HEALTH=503 "$SMOKE" --base-url https://jordylab.be --attempts 1 --delay-seconds 0
check "without --kaneo-url the run is green" ok
check "without --kaneo-url there is no Kaneo line" bash -c "! grep -q -i kaneo <<<\"\$output\""

echo
echo "$pass_count passed, $fail_count failed"
[ "$fail_count" -eq 0 ]
