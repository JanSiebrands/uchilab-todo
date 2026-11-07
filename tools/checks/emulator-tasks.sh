#!/usr/bin/env bash
# Emulator check for S3 (plan UCH-58 section 7): tasks written by the S2 data layer (seed from
# EmulatorSeedTest) are shown in the list, in the expected order, after start, after the process was killed
# and after a device reboot. Usage: emulator-tasks.sh <apk> <seed-dir>
# <seed-dir> holds tasks.db and titles.txt (expected titles in list order). Needs `adb root` (emulator only).
# Only ever targets one emulator (ANDROID_SERIAL=emulator-*), never another attached device.
# Every wait has a timeout and a clear message; the exit code decides.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
apk="$1"
seed="$2"
package="de.uchilab.todo"
app_dir="/data/data/$package"
name="$(basename "$apk")"
tmp="$(mktemp -d)"

fail() { echo "emulator-tasks [$name]: FAILED: $*" >&2; exit 1; }
log() { echo "emulator-tasks [$name]: $*"; }

# Target: exactly one emulator. An explicitly set ANDROID_SERIAL must be an emulator.
if [ -n "${ANDROID_SERIAL:-}" ]; then
  case "$ANDROID_SERIAL" in emulator-*) ;; *) fail "ANDROID_SERIAL=$ANDROID_SERIAL is not an emulator" ;; esac
else
  emulators="$(adb devices | tr -d '\r' | awk '$1 ~ /^emulator-/ && $2 == "device" {print $1}')"
  [ "$(printf '%s' "$emulators" | grep -c .)" -eq 1 ] || fail "need exactly one running emulator, found: ${emulators:-none}"
  export ANDROID_SERIAL="$emulators"
fi

cleanup() { timeout 60 adb uninstall "$package" >/dev/null 2>&1 || true; rm -rf "$tmp"; }
trap cleanup EXIT

[ -s "$seed/tasks.db" ] || fail "seed $seed/tasks.db missing (EmulatorSeedTest with -Puchilab.seedOut)"
[ -s "$seed/titles.txt" ] || fail "seed $seed/titles.txt missing"

# wait_until <seconds> <description> <command...>: polls every 2 s, fails with the description on timeout.
wait_until() {
  local seconds="$1" what="$2"; shift 2
  local end=$((SECONDS + seconds))
  until "$@" >/dev/null 2>&1; do
    [ "$SECONDS" -lt "$end" ] || fail "timeout after ${seconds}s: $what"
    sleep 2
  done
}

booted() { [ "$(timeout 10 adb shell getprop sys.boot_completed | tr -d '\r')" = "1" ]; }
is_root() { [ "$(timeout 10 adb shell id -u | tr -d '\r')" = "0" ]; }
pm_ready() { timeout 10 adb shell pm path android | grep -q '^package:'; }

become_root() {
  timeout 60 adb root >/dev/null 2>&1 || true
  timeout 60 adb wait-for-device
  wait_until 60 "adb root (is root available on this system image?)" is_root
}

wait_for_boot() {
  timeout 300 adb wait-for-device || fail "device did not come back within 300s"
  wait_until 300 "sys.boot_completed" booted
  wait_until 120 "package manager ready" pm_ready
  # The keyguard would hide the app after a reboot; this image has no lock credential.
  timeout 10 adb shell wm dismiss-keyguard >/dev/null 2>&1 || true
  timeout 10 adb shell input keyevent 82 >/dev/null 2>&1 || true
}

# Checks the dump against titles.txt: all titles present (in text or content-desc), tops strictly increasing.
dump_matches() {
  timeout 30 adb shell uiautomator dump /data/local/tmp/window.xml >/dev/null 2>&1 || return 1
  timeout 30 adb exec-out cat /data/local/tmp/window.xml >"$tmp/window.xml" 2>/dev/null || return 1
  python3 -I - "$tmp/window.xml" "$seed/titles.txt" >"$tmp/match.txt" 2>&1 <<'PY'
import re, sys
import xml.etree.ElementTree as ET
nodes = ET.parse(sys.argv[1]).getroot().iter("node")
nodes = [(n.get("text", "") + "\n" + n.get("content-desc", ""), n.get("bounds", "")) for n in nodes]
titles = [t for t in open(sys.argv[2], encoding="utf-8").read().splitlines() if t]
tops = []
for title in titles:
    hits = [b for text, b in nodes if title in text]
    if not hits:
        print(f"missing: {title}")
        sys.exit(1)
    tops.append(int(re.match(r"\[(-?\d+),(-?\d+)\]", hits[0]).group(2)))
if any(a >= b for a, b in zip(tops, tops[1:])):
    print("wrong order: " + ", ".join(f"{t}@{y}" for t, y in zip(titles, tops)))
    sys.exit(1)
print("all titles in order: " + ", ".join(titles))
PY
}

# wait_process_gone: waits up to 30 s until pidof answers "no such process" (exit 1). A hanging adb (timeout,
# exit 124) or any other error does not count as "process ended" and aborts with a clear message.
wait_process_gone() {
  local end=$((SECONDS + 30)) status
  while :; do
    status=0
    timeout 10 adb shell pidof "$package" >/dev/null 2>&1 || status=$?
    case "$status" in
      1) return 0 ;;
      0) [ "$SECONDS" -lt "$end" ] || fail "timeout after 30s: process still running after force-stop"; sleep 2 ;;
      124) fail "adb shell pidof did not answer within 10s (adb hangs), process end unknown" ;;
      *) fail "adb shell pidof failed with exit $status, process end unknown" ;;
    esac
  done
}

crashed() { timeout 30 adb logcat -b crash -d | tr -d '\r' | grep -F "$package"; }

# check_list <step>: starts the app and waits until the list shows the expected titles in order; no crash.
check_list() {
  local step="$1" end
  timeout 30 adb logcat -b crash -c || true
  timeout 60 adb shell am start -W -n "$component" >/dev/null || fail "$step: am start failed"
  end=$((SECONDS + 90))
  until dump_matches; do
    if crashed >"$tmp/crash.txt"; then cat "$tmp/crash.txt" >&2; fail "$step: crash after start"; fi
    if [ "$SECONDS" -ge "$end" ]; then
      cat "$tmp/match.txt" >&2 2>/dev/null || true
      fail "$step: expected titles not shown in expected order within 90s"
    fi
    sleep 2
  done
  if crashed >"$tmp/crash.txt"; then cat "$tmp/crash.txt" >&2; fail "$step: crash after start"; fi
  timeout 10 adb shell pidof "$package" >/dev/null || fail "$step: process not running"
  log "$step: OK ($(cat "$tmp/match.txt"))"
}

wait_for_boot
become_root
apk="$(source "$here/apk-sign.sh" && sign_if_unsigned "$apk" "$tmp")"
timeout 60 adb uninstall "$package" >/dev/null 2>&1 || true
timeout 120 adb install "$apk" >/dev/null || fail "install failed"
component="$(timeout 30 adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN \
  -c android.intent.category.LAUNCHER "$package" | tr -d '\r' | tail -n 1)"
case "$component" in "$package"/*) ;; *) fail "no launcher activity found (got: $component)" ;; esac

# First start creates the data directory with the app's owner and SELinux label; then stop the app.
timeout 60 adb shell am start -W -n "$component" >/dev/null || fail "first start failed"
timeout 30 adb shell am force-stop "$package"

# Seed in place of the app's database: same owner as the data directory, restorecon, no -wal/-shm left over.
owner="$(timeout 10 adb shell stat -c %u:%g "$app_dir" | tr -d '\r')"
[ -n "$owner" ] || fail "owner of $app_dir unknown"
timeout 60 adb push "$seed/tasks.db" /data/local/tmp/seed-tasks.db >/dev/null
timeout 30 adb shell "mkdir -p $app_dir/databases && rm -f $app_dir/databases/tasks.db*"
timeout 30 adb shell "cp /data/local/tmp/seed-tasks.db $app_dir/databases/tasks.db"
timeout 30 adb shell "chown $owner $app_dir/databases $app_dir/databases/tasks.db && chmod 600 $app_dir/databases/tasks.db"
timeout 30 adb shell "restorecon -R $app_dir/databases && rm -f /data/local/tmp/seed-tasks.db"
log "seed installed (owner $owner)"

check_list "after start"

timeout 30 adb shell am force-stop "$package"
wait_process_gone
check_list "after process end"

timeout 60 adb reboot || fail "adb reboot failed"
wait_for_boot
check_list "after reboot"

log "OK: tasks shown unchanged after start, process end and reboot"
