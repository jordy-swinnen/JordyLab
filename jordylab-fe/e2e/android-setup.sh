#!/usr/bin/env bash
# Prepares the running emulator for one Android E2E run (spec 012 US12, US13). Called by run.sh android after the throwaway stack is
# up. Everything here is local to the emulator; it never touches a real device or the host beyond adb.
#   needs: adb on PATH, ONE running emulator, E2E_APK_FIRST, E2E_ANDROID_PACKAGE, E2E_APP_LINK_HOST (exported by run.sh)
set -u
: "${E2E_APK_FIRST:?run this through e2e/run.sh android}"
: "${E2E_ANDROID_PACKAGE:?}"
: "${E2E_APP_LINK_HOST:?}"

log() { echo "android-setup: $*"; }

devices="$(adb devices | awk 'NR > 1 && $2 == "device" {print $1}')"
[[ "$(echo "$devices" | grep -c .)" == 1 ]] || { log "expected exactly one running emulator, found: ${devices:-none}"; exit 1; }

deadline=$((SECONDS + 300))
until [[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; do
  (( SECONDS >= deadline )) && { log "the emulator did not finish booting within 300 s"; exit 1; }
  sleep 2
done

# The app and Chrome reach Keycloak, the backend and the web server on the host through these reversed ports (same numbers on both sides).
for port in 18180 18080 18200; do
  adb reverse "tcp:$port" "tcp:$port" >/dev/null || { log "adb reverse failed for $port"; exit 1; }
done

# A fresh emulator's Chrome shows its first-run welcome screens, which would cover the Keycloak login in the Custom Tab.
adb shell "echo 'chrome --disable-fre --no-default-browser-check --no-first-run' > /data/local/tmp/chrome-command-line" >/dev/null
adb shell chmod 755 /data/local/tmp/chrome-command-line >/dev/null
adb shell am set-debug-app --persistent com.android.chrome >/dev/null 2>&1
adb shell am force-stop com.android.chrome >/dev/null 2>&1

adb uninstall "$E2E_ANDROID_PACKAGE" >/dev/null 2>&1
adb install -r -g "$E2E_APK_FIRST" >/dev/null || { log "installing the debug APK failed"; exit 1; }

# Let the app handle https://<test host>/mobile/... without a verified assetlinks.json (a user choice, available from Android 12).
adb shell pm set-app-links-user-selection --user 0 --package "$E2E_ANDROID_PACKAGE" true "$E2E_APP_LINK_HOST" >/dev/null 2>&1 \
  || log "could not set the App Link user selection (the login test falls back to delivering the callback by intent)"
adb shell pm set-app-links-allowed --user 0 --package "$E2E_ANDROID_PACKAGE" true >/dev/null 2>&1

webview_version=""
for webview_package in com.google.android.webview com.android.webview; do
  webview_version="$(adb shell dumpsys package "$webview_package" 2>/dev/null | awk -F= '/versionName=/ {print $2; exit}' | tr -d '\r')"
  [[ -n "$webview_version" ]] && break
done
log "emulator ready: API $(adb shell getprop ro.build.version.sdk | tr -d '\r'), WebView ${webview_version:-unknown} ($webview_package)"
