#!/usr/bin/env bash
# Starts the app on a running emulator/device and fails unless the launcher activity comes up without a
# crash (point 7). Usage: app-start.sh <apk>
# An unsigned APK (release build with R8) is signed with a throwaway key (apk-sign.sh). The app is
# uninstalled at the end.
set -euo pipefail
apk="$1"
package="de.uchilab.todo"
tmp="$(mktemp -d)"
cleanup() { adb uninstall "$package" >/dev/null 2>&1 || true; rm -rf "$tmp"; }
trap cleanup EXIT

source "$(dirname "$0")/apk-sign.sh"
apk="$(sign_if_unsigned "$apk" "$tmp")"

adb uninstall "$package" >/dev/null 2>&1 || true
adb install "$apk" >/dev/null
adb logcat -b crash -c || true

component="$(adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN \
  -c android.intent.category.LAUNCHER "$package" | tr -d '\r' | tail -n 1)"
case "$component" in "$package"/*) ;; *)
  echo "app-start: no launcher activity found for $package (got: $component)" >&2; exit 1 ;; esac

adb shell am start -W -n "$component" | tr -d '\r'
sleep 5
crashes="$(adb logcat -b crash -d | tr -d '\r' | grep -F "$package" || true)"
if [ -n "$crashes" ]; then
  echo "app-start: crash after start:" >&2; echo "$crashes" >&2; exit 1
fi
if ! adb shell pidof "$package" >/dev/null; then
  echo "app-start: process $package is not running after start" >&2; exit 1
fi
activity="${component#*/}"
if ! adb shell dumpsys activity activities | tr -d '\r' | grep -E "(topResumedActivity|mResumedActivity|ResumedActivity)" \
    | grep -qF "$package/$activity"; then
  echo "app-start: $component is not in the foreground" >&2
  adb shell dumpsys activity activities | tr -d '\r' | grep -E "ResumedActivity" >&2 || true
  exit 1
fi
echo "app-start: OK ($component started without crash: $(basename "$1"))"
