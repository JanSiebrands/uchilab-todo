#!/usr/bin/env bash
# Shared device helpers of emulator-tasks.sh, emulator-read-failure.sh and emulator-showcase.sh (plan UCH-80 section 5). Sourced, not
# run. The caller sets: check (script name for messages), apk, package, app_dir, name (build name for messages
# and files) and tmp (temp dir it removes itself). Only ever targets one emulator (ANDROID_SERIAL=emulator-*),
# never another attached device. Nodes are found by resource-id = test tag, never by UI text. Every wait has a
# timeout and a clear message; the exit code decides.

# On failure the last UI dump goes next to the logcat (EMULATOR_LOGS, set by the job script) for the reviewers.
fail() {
  echo "$check [$name]: FAILED: $*" >&2
  if [ -n "${EMULATOR_LOGS:-}" ] && [ -s "$tmp/window.xml" ]; then
    mkdir -p "$EMULATOR_LOGS" && cp "$tmp/window.xml" "$EMULATOR_LOGS/$check-$name-last-window.xml" || true
  fi
  exit 1
}
log() { echo "$check [$name]: $*"; }

# select_emulator: exactly one emulator. An explicitly set ANDROID_SERIAL must be an emulator.
select_emulator() {
  if [ -n "${ANDROID_SERIAL:-}" ]; then
    case "$ANDROID_SERIAL" in emulator-*) ;; *) fail "ANDROID_SERIAL=$ANDROID_SERIAL is not an emulator" ;; esac
  else
    local emulators
    emulators="$(adb devices | tr -d '\r' | awk '$1 ~ /^emulator-/ && $2 == "device" {print $1}')"
    [ "$(printf '%s' "$emulators" | grep -c .)" -eq 1 ] || fail "need exactly one running emulator, found: ${emulators:-none}"
    export ANDROID_SERIAL="$emulators"
  fi
}

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

# restart_emulator: replaces a guest reboot (adb reboot) in the screenshots job (UCH-112). On the image with
# rendering (target default) the emulator process itself died twice during adb reboot and never came back
# (run 42502889583, both attempts: "device did not come back", then "could not connect to TCP port 5554").
# Instead the emulator is stopped (sync, adb emu kill) and started again with the same AVD, port and options
# as android-emulator-runner (EMULATOR_AVD, EMULATOR_OPTIONS from the job). A cold boot of the same AVD keeps
# /data, so persist.* properties take effect as after a reboot. Up to EMULATOR_RESTARTS starts (default 3),
# each with a time limit of EMULATOR_BOOT_TIMEOUT seconds (default 420); a start whose process ends early or
# that does not boot in time is stopped and retried. Only the start is retried, never a check. If the emulator
# does not come back, it fails with the number of starts actually made and the reason of the last one, never
# skips. Output of each start goes to $EMULATOR_LOGS/emulator-restart-<call>-<start>.log for the reviewers.
# Counterprobes: 16x (process ends, real emulator binary), 16y (second start succeeds), 16z (time limit).
emulator_port() { echo "${ANDROID_SERIAL#emulator-}"; }
console_closed() { ! (exec 3<>"/dev/tcp/127.0.0.1/$(emulator_port)") 2>/dev/null; }
# boot_within <seconds> <pid>: 0 when booted and the package manager answers in time; 1 on timeout or when the
# emulator process <pid> has ended. The reason of a failure is left in boot_failure.
boot_within() {
  local end=$((SECONDS + $1)) pid="$2"
  boot_failure=""
  until booted && pm_ready; do
    kill -0 "$pid" 2>/dev/null || { boot_failure="emulator process ended"; log "$boot_failure (pid $pid)"; return 1; }
    [ "$SECONDS" -lt "$end" ] || { boot_failure="no boot within $1 s"; log "$boot_failure"; return 1; }
    sleep 2
  done
}
# stop_pid <pid>: ends a start that failed, so the next start does not share the port with it.
stop_pid() {
  local i
  kill "$1" 2>/dev/null || return 0
  for i in $(seq 1 15); do kill -0 "$1" 2>/dev/null || return 0; sleep 2; done
  kill -9 "$1" 2>/dev/null || true
}
restart_call=0
restart_emulator() {
  local bin="${ANDROID_HOME:?ANDROID_HOME is not set}/emulator/emulator" avd="${EMULATOR_AVD:?EMULATOR_AVD is not set}"
  local opts="${EMULATOR_OPTIONS:?EMULATOR_OPTIONS is not set}" attempts="${EMULATOR_RESTARTS:-3}"
  local limit="${EMULATOR_BOOT_TIMEOUT:-420}" attempt tried=0 pid out started
  local -a args
  read -r -a args <<<"$opts"
  restart_call=$((restart_call + 1))
  timeout 30 adb shell sync >/dev/null 2>&1 || true
  for attempt in $(seq 1 "$attempts"); do
    timeout 30 adb emu kill >/dev/null 2>&1 || true
    wait_until 120 "emulator on port $(emulator_port) did not stop" console_closed
    out="${EMULATOR_LOGS:-$tmp}/emulator-restart-$restart_call-$attempt.log"
    mkdir -p "$(dirname "$out")"
    started=$SECONDS
    "$bin" -port "$(emulator_port)" -avd "$avd" "${args[@]}" >"$out" 2>&1 </dev/null &
    pid=$!
    tried=$attempt
    log "emulator restart $attempt/$attempts (pid $pid, log $(basename "$out"))"
    if boot_within "$limit" "$pid"; then
      timeout 10 adb shell wm dismiss-keyguard >/dev/null 2>&1 || true
      timeout 10 adb shell input keyevent 82 >/dev/null 2>&1 || true
      log "emulator back after restart $attempt/$attempts (boot took $((SECONDS - started)) s)"
      return 0
    fi
    stop_pid "$pid"
  done
  fail "emulator did not come back after $tried restarts (last: $boot_failure; logs emulator-restart-$restart_call-*.log)"
}

# dump_ui: current window hierarchy into $tmp/window.xml.
dump_ui() {
  timeout 30 adb shell uiautomator dump /data/local/tmp/window.xml >/dev/null 2>&1 || return 1
  timeout 30 adb exec-out cat /data/local/tmp/window.xml >"$tmp/window.xml" 2>/dev/null || return 1
}

# node <tag>: prints "x y<TAB>text" for the node whose resource-id is the test tag (centre of its bounds),
# from the last dump_ui; exit 1 if there is none.
node() {
  python3 -I - "$tmp/window.xml" "$1" <<'PY'
import re, sys
import xml.etree.ElementTree as ET
for n in ET.parse(sys.argv[1]).getroot().iter("node"):
    rid = n.get("resource-id", "")
    if rid == sys.argv[2] or rid.endswith(":id/" + sys.argv[2]):
        l, t, r, b = map(int, re.findall(r"-?\d+", n.get("bounds", "")))
        print(f"{(l + r) // 2} {(t + b) // 2}\t{n.get('text', '')}")
        sys.exit(0)
sys.exit(1)
PY
}
shown() { dump_ui && node "$1" >/dev/null; }
gone() { dump_ui && ! node "$1" >/dev/null; }

# display_state: power and display state in one line, for the log.
display_state() {
  timeout 10 adb shell "dumpsys power | grep -E 'mWakefulness=|mStayOn='; dumpsys display | grep -E 'mScreenState='" \
    2>/dev/null | tr -d '\r' | tr -s ' \n' ' ' || true
}

# screen_on: the display may have gone off since the start (no input for a while); screencap then sees black.
screen_on() {
  timeout 10 adb shell svc power stayon true || fail "screenshots: svc power stayon failed"
  timeout 10 adb shell input keyevent KEYCODE_WAKEUP || fail "screenshots: wakeup failed"
  log "screenshots: display $(display_state)"
}

# shot <name> [<tag>]: screenshot (only call with SCREENSHOTS set). File name: with SHOT_BUILD set
# $SCREENSHOTS/<SHOT_BUILD>-<name><SHOT_SUFFIX>.png (schema <build>-<state>-<light|dark>-<de|en>.png of the
# screenshots job, plan UCH-81 section 2), otherwise $SCREENSHOTS/<build>-<name>.png as before. With a tag,
# the node with this resource-id must be in the dump directly before screencap (operation, not a check: whether
# a picture is right, the designer and the PO judge by looking at it, UCH-116 R2).
shot() {
  local file
  if [ -n "${SHOT_BUILD:-}" ]; then
    file="$SCREENSHOTS/$SHOT_BUILD-$1${SHOT_SUFFIX:-}.png"
  else
    file="$SCREENSHOTS/$name-$1.png"
  fi
  mkdir -p "$SCREENSHOTS"
  sleep 1
  if [ -n "${2:-}" ]; then shown "$2" || fail "screenshot $1: wrong state, $2 not shown before screencap"; fi
  # Only on an image with rendering (screenshots job, target default): on aosp_atd both screencap and the
  # emulator host give a single colour although the UI is there (runs 42435169228, 42436517057, UCH-65).
  timeout 30 adb exec-out screencap -p >"$file" || fail "screenshot $1 failed"
  [ -s "$file" ] || fail "screenshot $1 empty"
  log "screenshot $(basename "$file")${2:+ (state $2 before)} ($(display_state))"
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
      0) [ "$SECONDS" -lt "$end" ] || fail "timeout after 30s: process still running after it was stopped"; sleep 2 ;;
      124) fail "adb shell pidof did not answer within 10s (adb hangs), process end unknown" ;;
      *) fail "adb shell pidof failed with exit $status, process end unknown" ;;
    esac
  done
}

crashed() { timeout 30 adb logcat -b crash -d | tr -d '\r' | grep -F "$package"; }

start_app() {
  timeout 30 adb logcat -b crash -c || true
  timeout 60 adb shell am start -W -n "$component" >/dev/null || fail "$1: am start failed"
}

# install_app: signs the APK if needed, installs it fresh, finds the launcher activity (component) and starts
# it once, so the data directory exists with the app's owner and SELinux label; then stops the app.
install_app() {
  apk="$(source "$here/apk-sign.sh" && sign_if_unsigned "$apk" "$tmp")"
  timeout 60 adb uninstall "$package" >/dev/null 2>&1 || true
  timeout 120 adb install "$apk" >/dev/null || fail "install failed"
  component="$(timeout 30 adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN \
    -c android.intent.category.LAUNCHER "$package" | tr -d '\r' | tail -n 1)"
  case "$component" in "$package"/*) ;; *) fail "no launcher activity found (got: $component)" ;; esac
  timeout 60 adb shell am start -W -n "$component" >/dev/null || fail "first start failed"
  timeout 30 adb shell am force-stop "$package"
}

# install_db <local file>: the file in place of the app's database (app stopped): same owner as the data
# directory, restorecon, no -wal/-shm left over. An empty file is allowed (read failure case B).
install_db() {
  local owner
  owner="$(timeout 10 adb shell stat -c %u:%g "$app_dir" | tr -d '\r')"
  [ -n "$owner" ] || fail "owner of $app_dir unknown"
  timeout 60 adb push "$1" /data/local/tmp/seed-tasks.db >/dev/null
  timeout 30 adb shell "mkdir -p $app_dir/databases && rm -f $app_dir/databases/tasks.db*"
  timeout 30 adb shell "cp /data/local/tmp/seed-tasks.db $app_dir/databases/tasks.db"
  timeout 30 adb shell "chown $owner $app_dir/databases $app_dir/databases/tasks.db && chmod 600 $app_dir/databases/tasks.db"
  timeout 30 adb shell "restorecon -R $app_dir/databases && rm -f /data/local/tmp/seed-tasks.db"
  log "database file installed (owner $owner)"
}
