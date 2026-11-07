#!/usr/bin/env bash
# Shared start check, used by app-start.sh (emulator job) and tools/ci/tryout-install-test.sh (tryout-apk job).
# Source it, install the app, then call: check_app_start <package> <label>
# Fails (exit 1) unless the launcher activity comes up without a crash, the process runs and the activity is
# in the foreground.

check_app_start() {
  local package="$1" label="$2" component crashes activity
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
  echo "app-start: OK ($component started without crash: $label)"
}
