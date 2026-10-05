#!/usr/bin/env bash
# One end-to-end run on its own throwaway Postgres + Keycloak (spec 012 US11, contracts/e2e-environment.md).
#   run.sh web                 build the app fresh, start the stack, run the Playwright suite
#   run.sh android             start the stack, run the Appium suite (emulator must be up; see android-setup.sh)
#   run.sh selftest-pass|selftest-fail|selftest-wait    full lifecycle with a trivial test command (cleanup proofs)
# Whatever happens (pass, fail, Ctrl-C, SIGTERM) the stack is removed and verified gone; leftovers fail the run (exit 97).
# Never touches jordylab-be/compose.yaml, the dev database or the dev realm. Credentials are generated per run, kept in an
# untracked 0600 file and never printed.
set -u

MODE="${1:-}"
SCRIPT_DIRECTORY="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FRONTEND_ROOT="$(cd "$SCRIPT_DIRECTORY/.." && pwd)"
REPOSITORY_ROOT="$(cd "$FRONTEND_ROOT/.." && pwd)"
# shellcheck source=lib/cleanup.sh
. "$SCRIPT_DIRECTORY/lib/cleanup.sh"

readonly READINESS_TIMEOUT_SECONDS="${E2E_READINESS_TIMEOUT_SECONDS:-240}"
readonly LEFTOVER_EXIT_CODE=97

RUNTIME="$(e2e_runtime)"
RUN_ID="$(date +%Y%m%d%H%M%S)-$(openssl rand -hex 3)"
PROJECT="jordylab-e2e-$RUN_ID"
RUN_DIRECTORY="$SCRIPT_DIRECTORY/.run/$RUN_ID"
BACKEND_PID=""
STATIC_SERVER_PID=""
TEST_PID=""
TEST_EXIT_CODE=1
FINISHED=0

log() { echo "e2e[$RUN_ID]: $*"; }

random_secret() { openssl rand -hex 16; }

free_port() {
  python3 -c 'import socket; s = socket.socket(); s.bind(("127.0.0.1", 0)); print(s.getsockname()[1]); s.close()'
}

port_is_free() {
  python3 -c 'import socket, sys; s = socket.socket(); s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1); s.bind(("127.0.0.1", int(sys.argv[1]))); s.close()' "$1" 2>/dev/null
}

compose() {
  "$RUNTIME" compose -p "$PROJECT" -f "$SCRIPT_DIRECTORY/compose.e2e.yaml" "$@"
}

# True when a container of this run has exited or the backend process is gone: nothing to wait for any more.
something_died() {
  if "$RUNTIME" ps -a -q --filter "label=$E2E_RUN_LABEL=$RUN_ID" --filter status=exited 2>/dev/null | grep -q .; then
    log "a container of this run exited"
    return 0
  fi
  if [[ -n "$BACKEND_PID" ]] && ! kill -0 "$BACKEND_PID" 2>/dev/null; then
    log "the backend process exited"
    return 0
  fi
  return 1
}

# wait_for <description> <command...>: retries the command every 2 s until READINESS_TIMEOUT_SECONDS (never a fixed sleep)
# and gives up at once when something it depends on died.
wait_for() {
  local description="$1"
  shift
  local deadline=$((SECONDS + READINESS_TIMEOUT_SECONDS))
  while ! "$@" >/dev/null 2>&1; do
    if something_died; then
      log "gave up waiting for $description"
      return 1
    fi
    if (( SECONDS >= deadline )); then
      log "TIMEOUT after ${READINESS_TIMEOUT_SECONDS}s waiting for $description"
      return 1
    fi
    sleep 2
  done
  log "ready: $description"
}

# Kills a process and everything it started (bunx -> nx -> node ... would otherwise outlive the runner).
kill_tree() {
  local pid="$1" child
  for child in $(pgrep -P "$pid" 2>/dev/null); do
    kill_tree "$child"
  done
  kill "$pid" 2>/dev/null
}

finish() {
  (( FINISHED )) && return
  FINISHED=1
  trap '' INT TERM
  local final_exit_code="$TEST_EXIT_CODE"
  log "cleaning up"
  [[ -n "$TEST_PID" ]] && kill_tree "$TEST_PID"
  [[ -n "$STATIC_SERVER_PID" ]] && kill_tree "$STATIC_SERVER_PID"
  [[ -n "$BACKEND_PID" ]] && kill_tree "$BACKEND_PID"
  if [[ -n "${E2E_ARTIFACT_DIRECTORY:-}" && -d "$RUN_DIRECTORY" ]]; then
    mkdir -p "$E2E_ARTIFACT_DIRECTORY"
    cp "$RUN_DIRECTORY"/*.log "$E2E_ARTIFACT_DIRECTORY"/ 2>/dev/null
    if [[ "$MODE" == "android" ]] && command -v adb >/dev/null 2>&1; then
      adb logcat -d >"$E2E_ARTIFACT_DIRECTORY/logcat.txt" 2>/dev/null
    fi
  fi
  compose down --volumes --remove-orphans >/dev/null 2>&1
  e2e_remove_run "$RUN_ID"
  rm -rf "$RUN_DIRECTORY"
  if ! e2e_verify_clean "$RUN_ID"; then
    final_exit_code=$LEFTOVER_EXIT_CODE
  fi
  exit "$final_exit_code"
}

on_signal() {
  log "interrupted"
  TEST_EXIT_CODE="$1"
  exit "$1"
}

# Android runs only. The app is built with fixed logical addresses (see environment.mobile-e2e.ts), so Keycloak, the backend and the
# web server use exactly these ports and the emulator reaches them with `adb reverse` on the same numbers.
ANDROID_KEYCLOAK_PORT=18180
ANDROID_API_PORT=18080
ANDROID_WEB_PORT=18200
ANDROID_PACKAGE="be.jordylab.app"
ANDROID_APP_LINK_HOST="e2e.jordylab.test"
DEBUG_CERT_SHA256=""
APK_FIRST=""
APK_NEWER=""
WEB_DIST_FOR_BROWSER=""

# Builds everything an Android run needs before the stack starts: the app as a debug APK (twice, the second with a higher versionCode
# so the update check has something newer to find), the same web build for the browser, and the debug certificate the backend pins.
prepare_android() {
  command -v adb >/dev/null 2>&1 || { log "adb not found: an Android run needs the Android SDK platform tools and a running emulator"; return 1; }
  [[ -n "${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}" ]] || { log "ANDROID_HOME is not set"; return 1; }
  command -v keytool >/dev/null 2>&1 || { log "keytool (JDK) not found"; return 1; }
  export ANDROID_HOME="${ANDROID_HOME:-$ANDROID_SDK_ROOT}"
  local mobile_directory="$FRONTEND_ROOT/apps/jordylab-mobile"
  local gradle_properties="-PjordylabAppLinkHost=$ANDROID_APP_LINK_HOST"
  mkdir -p "$RUN_DIRECTORY/apk"

  log "building the web app for the browser tests"
  (cd "$FRONTEND_ROOT" && bunx nx build jordylab --configuration=e2e --skip-nx-cache) || return 1
  WEB_DIST_FOR_BROWSER="$RUN_DIRECTORY/web-dist"
  cp -R "$FRONTEND_ROOT/dist/apps/jordylab/browser" "$WEB_DIST_FOR_BROWSER" || return 1

  log "building the native app (web build with the e2e mobile configuration, Capacitor sync, debug APKs)"
  (cd "$FRONTEND_ROOT" && bunx nx build jordylab --configuration=mobile-e2e --skip-nx-cache) || return 1
  (cd "$mobile_directory" && CAPACITOR_E2E=1 bunx cap sync android) || return 1
  # The Android Gradle plugin builds on JDK 21 (as release.yml does) while the backend needs 25: ANDROID_JAVA_HOME picks the former.
  (cd "$mobile_directory/android" && JAVA_HOME="${ANDROID_JAVA_HOME:-${JAVA_HOME:-}}" ./gradlew assembleDebug --no-daemon -q $gradle_properties -PjordylabVersionCode=1 -PjordylabVersionName=0.0.1-e2e) || return 1
  cp "$mobile_directory/android/app/build/outputs/apk/debug/app-debug.apk" "$RUN_DIRECTORY/apk/app-first.apk" || return 1
  (cd "$mobile_directory/android" && JAVA_HOME="${ANDROID_JAVA_HOME:-${JAVA_HOME:-}}" ./gradlew assembleDebug --no-daemon -q $gradle_properties -PjordylabVersionCode=2 -PjordylabVersionName=0.0.2-e2e) || return 1
  cp "$mobile_directory/android/app/build/outputs/apk/debug/app-debug.apk" "$RUN_DIRECTORY/apk/app-newer.apk" || return 1
  APK_FIRST="$RUN_DIRECTORY/apk/app-first.apk"
  APK_NEWER="$RUN_DIRECTORY/apk/app-newer.apk"

  DEBUG_CERT_SHA256="$(keytool -list -v -keystore "$HOME/.android/debug.keystore" -alias androiddebugkey -storepass android -keypass android 2>/dev/null \
    | awk '/SHA256:/ {print $2; exit}' | tr -d ':')"
  [[ -n "$DEBUG_CERT_SHA256" ]] || { log "could not read the debug certificate fingerprint"; return 1; }
  {
    echo "E2E_APK_FIRST=$APK_FIRST"
    echo "E2E_APK_NEWER=$APK_NEWER"
    echo "E2E_ANDROID_PACKAGE=$ANDROID_PACKAGE"
    echo "E2E_APP_LINK_HOST=$ANDROID_APP_LINK_HOST"
    echo "E2E_WEB_PORT=$ANDROID_WEB_PORT"
  } >>"$RUN_DIRECTORY/env"
  set -a
  # shellcheck disable=SC1091
  . "$RUN_DIRECTORY/env"
  set +a
}

start_backend() {
  local jar
  if [[ "${E2E_SKIP_BACKEND_BUILD:-0}" != "1" ]]; then
    log "building the backend jar"
    (cd "$REPOSITORY_ROOT/jordylab-be" && ./gradlew bootJar -x test --no-daemon -q) || return 1
  fi
  jar="${E2E_BACKEND_JAR:-$(ls "$REPOSITORY_ROOT"/jordylab-be/build/libs/*.jar 2>/dev/null | grep -v -- '-plain' | head -1)}"
  [[ -n "$jar" ]] || { log "no backend jar found"; return 1; }
  mkdir -p "$RUN_DIRECTORY/artwork"
  # The backend refuses to start without profile `local` or `prod`, and `local` hard-codes the DEV stack (database on
  # localhost:5432, Keycloak on 8180, a dev client secret, dev CORS origins). Environment variables beat profile files, so
  # every one of those values is overridden below; nothing may fall back to the dev stack (see the port guard in main).
  # -u: the throwaway backend must have no AI provider keys, whatever the developer's shell exports.
  env -u OPENROUTER_API_KEY -u ANTHROPIC_API_KEY \
    SPRING_PROFILES_ACTIVE=local \
    SERVER_PORT="$API_PORT" \
    SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:$POSTGRES_PORT/jordylab" \
    SPRING_DATASOURCE_USERNAME=jordylab \
    SPRING_DATASOURCE_PASSWORD="$E2E_DB_PASSWORD" \
    POSTGRES_URL="jdbc:postgresql://localhost:$POSTGRES_PORT/jordylab" \
    POSTGRES_USER=jordylab \
    POSTGRES_PASSWORD="$E2E_DB_PASSWORD" \
    KEYCLOAK_URL="http://localhost:$KEYCLOAK_PORT" \
    KEYCLOAK_BACKEND_SECRET="$E2E_BACKEND_CLIENT_SECRET" \
    JORDYLAB_SETTINGS_KEYCLOAK_SERVER_URL="http://localhost:$KEYCLOAK_PORT" \
    JORDYLAB_SETTINGS_KEYCLOAK_ADMIN_CLIENT_SECRET="$E2E_BACKEND_CLIENT_SECRET" \
    JORDYLAB_SCRIPT_KEYCLOAK_URL="http://localhost:$KEYCLOAK_PORT" \
    SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI="http://localhost:$KEYCLOAK_PORT/realms/jordylab" \
    JORDYLAB_CORS_ALLOWED_ORIGINS="http://localhost:$WEB_PORT" \
    GAMECATALOG_ARTWORK_DIR="$RUN_DIRECTORY/artwork" \
    MOBILE_APPLICATION_ID="$ANDROID_PACKAGE" \
    MOBILE_PRODUCTION_DOMAIN="$ANDROID_APP_LINK_HOST" \
    MOBILE_RELEASE_STORAGE_DIR="$RUN_DIRECTORY/releases" \
    MOBILE_RELEASE_SIGNING_CERT_SHA256="$DEBUG_CERT_SHA256" \
    MOBILE_DOWNLOAD_LINK_SECRET="$E2E_DOWNLOAD_LINK_SECRET" \
    java -jar "$jar" >"$RUN_DIRECTORY/backend.log" 2>&1 &
  BACKEND_PID=$!
}

web_is_up() { curl -fsS "http://localhost:$WEB_PORT/" -o /dev/null; }
keycloak_is_up() { curl -fsS "http://localhost:$KEYCLOAK_PORT/realms/jordylab/.well-known/openid-configuration" -o /dev/null; }
backend_is_up() { curl -fsS "http://localhost:$API_PORT/actuator/health" -o /dev/null; }

# Serves a built web app (static files plus the /api proxy) next to the backend.
serve_web() {
  E2E_WEB_DIST="$1" E2E_WEB_PORT="$WEB_PORT" E2E_API_ORIGIN="http://localhost:$API_PORT" \
    bun run "$SCRIPT_DIRECTORY/static-server.ts" >"$RUN_DIRECTORY/static-server.log" 2>&1 &
  STATIC_SERVER_PID=$!
  wait_for "the web server on port $WEB_PORT" web_is_up
}

# Web only: build the app fresh and serve it.
prepare_web() {
  if [[ "${E2E_SKIP_WEB_BUILD:-0}" != "1" ]]; then
    log "building the app fresh"
    (cd "$FRONTEND_ROOT" && bunx nx build jordylab --configuration=e2e --skip-nx-cache) || return 1
  fi
  serve_web "$FRONTEND_ROOT/dist/apps/jordylab/browser"
}

# The test command itself. It runs in the background and the runner `wait`s for it, so a signal is handled at once and the
# whole process tree is killed instead of the trap waiting for the tests to end.
run_tests() {
  case "$MODE" in
    selftest-pass) true ;;
    selftest-fail) false ;;
    selftest-wait) log "waiting for an interrupt"; sleep 600 ;;
    web) cd "$FRONTEND_ROOT" && bunx nx e2e jordylab-e2e --skip-nx-cache ;;
    android)
      "$SCRIPT_DIRECTORY/android-setup.sh" || exit 1
      cd "$FRONTEND_ROOT" && bunx nx e2e jordylab-mobile-e2e --skip-nx-cache
      ;;
  esac
}

main() {
  case "$MODE" in
    web|android|selftest-pass|selftest-fail|selftest-wait) ;;
    *) echo "usage: $0 web | android | selftest-pass | selftest-fail | selftest-wait" >&2; exit 64 ;;
  esac
  if [[ "$RUNTIME" == "podman" && -z "${DOCKER_HOST:-}" ]]; then
    DOCKER_HOST="unix://$(podman machine inspect --format '{{.ConnectionInfo.PodmanSocket.Path}}')"
    export DOCKER_HOST
  fi
  trap finish EXIT
  trap 'on_signal 130' INT
  trap 'on_signal 143' TERM

  log "runtime=$RUNTIME project=$PROJECT"
  e2e_sweep
  mkdir -p "$RUN_DIRECTORY" && chmod 700 "$RUN_DIRECTORY"

  POSTGRES_PORT="$(free_port)"; KEYCLOAK_PORT="$(free_port)"; API_PORT="$(free_port)"; WEB_PORT="$(free_port)"
  E2E_DB_PASSWORD="$(random_secret)"
  E2E_KEYCLOAK_ADMIN_PASSWORD="$(random_secret)"
  E2E_ADMIN_USERNAME="e2e-admin"
  E2E_ADMIN_PASSWORD="$(random_secret)"
  E2E_INGEST_CLIENT_SECRET="$(random_secret)"
  E2E_BACKEND_CLIENT_SECRET="$(random_secret)"
  E2E_DOWNLOAD_LINK_SECRET="$(random_secret)"
  if [[ "$MODE" == "android" ]]; then
    KEYCLOAK_PORT="$ANDROID_KEYCLOAK_PORT"; API_PORT="$ANDROID_API_PORT"; WEB_PORT="$ANDROID_WEB_PORT"
    for fixed_port in "$KEYCLOAK_PORT" "$API_PORT" "$WEB_PORT"; do
      port_is_free "$fixed_port" || { log "port $fixed_port is in use; an Android run needs ports $KEYCLOAK_PORT, $API_PORT and $WEB_PORT (the app is built with them)"; TEST_EXIT_CODE=1; exit 1; }
    done
  fi
  if [[ "$POSTGRES_PORT" == "5432" || "$KEYCLOAK_PORT" == "8180" ]]; then
    log "refusing to run: a chosen port collides with the dev stack"; TEST_EXIT_CODE=1; exit 1
  fi
  export E2E_PROJECT="$PROJECT" E2E_RUN_ID="$RUN_ID" E2E_RUNNER_PID="$$" E2E_REPO_ROOT="$REPOSITORY_ROOT"
  export E2E_POSTGRES_PORT="$POSTGRES_PORT" E2E_KEYCLOAK_PORT="$KEYCLOAK_PORT" E2E_WEB_PORT="$WEB_PORT"
  export E2E_DB_PASSWORD E2E_KEYCLOAK_ADMIN_PASSWORD E2E_ADMIN_USERNAME E2E_ADMIN_PASSWORD E2E_INGEST_CLIENT_SECRET E2E_BACKEND_CLIENT_SECRET E2E_DOWNLOAD_LINK_SECRET
  export E2E_REALM_FILE="$RUN_DIRECTORY/realm.json"

  python3 "$SCRIPT_DIRECTORY/lib/make-realm.py" "$E2E_REALM_FILE" || { TEST_EXIT_CODE=1; exit 1; }

  # Everything a test needs, in one untracked 0600 file (also exported below). Values are never printed.
  umask 077
  {
    echo "E2E_RUN_ID=$RUN_ID"
    echo "E2E_BASE_URL=http://localhost:$WEB_PORT"
    echo "E2E_KEYCLOAK_URL=http://localhost:$KEYCLOAK_PORT"
    echo "E2E_API_ORIGIN=http://localhost:$API_PORT"
    echo "E2E_ADMIN_USERNAME=$E2E_ADMIN_USERNAME"
    echo "E2E_ADMIN_PASSWORD=$E2E_ADMIN_PASSWORD"
    echo "E2E_INGEST_CLIENT_SECRET=$E2E_INGEST_CLIENT_SECRET"
  } >"$RUN_DIRECTORY/env"
  set -a
  # shellcheck disable=SC1091
  . "$RUN_DIRECTORY/env"
  set +a

  if [[ "$MODE" == "android" ]]; then
    prepare_android || { TEST_EXIT_CODE=1; exit 1; }
  fi

  log "starting Postgres and Keycloak (ports $POSTGRES_PORT / $KEYCLOAK_PORT)"
  compose up -d >"$RUN_DIRECTORY/compose.log" 2>&1 || { log "compose up failed (see compose log)"; tail -20 "$RUN_DIRECTORY/compose.log" >&2; TEST_EXIT_CODE=1; exit 1; }
  wait_for "Keycloak realm on port $KEYCLOAK_PORT" keycloak_is_up || { compose logs --tail 30 keycloak >&2; TEST_EXIT_CODE=1; exit 1; }
  start_backend || { TEST_EXIT_CODE=1; exit 1; }
  wait_for "the backend on port $API_PORT" backend_is_up || { tail -30 "$RUN_DIRECTORY/backend.log" >&2; TEST_EXIT_CODE=1; exit 1; }

  if [[ "$MODE" == "web" ]]; then
    prepare_web || { TEST_EXIT_CODE=1; exit 1; }
  elif [[ "$MODE" == "android" ]]; then
    serve_web "$WEB_DIST_FOR_BROWSER" || { TEST_EXIT_CODE=1; exit 1; }
  fi
  run_tests &
  TEST_PID=$!
  wait "$TEST_PID"
  TEST_EXIT_CODE=$?
  exit "$TEST_EXIT_CODE"
}

main
