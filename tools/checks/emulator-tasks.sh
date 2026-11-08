#!/usr/bin/env bash
# Emulator check for S3 (plan UCH-58 section 7) and S4 (plan UCH-63 section 6): tasks written by the S2 data
# layer (seed from EmulatorSeedTest) are shown in the list, in the expected order, the done task at its place.
# Then a task is created through the UI (adb shell input, nodes found by resource-id = test tag, never by UI
# text), a draft survives the app being killed in the background, and the list (UI task on top, then the
# seed) is shown after the process end and after a device reboot. Usage: emulator-tasks.sh <apk> <seed-dir>
# <seed-dir> holds tasks.db, titles.txt (expected titles in list order) and done.txt (title of the done task).
# Needs `adb root` (emulator only). With SCREENSHOTS=<dir>, screenshots of the list, the input and its hints
# and the discard dialog are written there (invented data only).
# Only ever targets one emulator (ANDROID_SERIAL=emulator-*), never another attached device.
# Every wait has a timeout and a clear message; the exit code decides.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
apk="$1"
seed="$2"
package="de.uchilab.todo"
app_dir="/data/data/$package"
name="$(basename "$apk" .apk)"
tmp="$(mktemp -d)"

# On failure the last UI dump goes next to the logcat (EMULATOR_LOGS, set by emulator-run.sh) for the reviewers.
fail() {
  echo "emulator-tasks [$name]: FAILED: $*" >&2
  if [ -n "${EMULATOR_LOGS:-}" ] && [ -s "$tmp/window.xml" ]; then
    mkdir -p "$EMULATOR_LOGS" && cp "$tmp/window.xml" "$EMULATOR_LOGS/$name-last-window.xml" || true
  fi
  exit 1
}
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
[ -s "$seed/done.txt" ] || fail "seed $seed/done.txt missing"

# Test tags of the app (NewTaskTags), used as resource-id thanks to testTagsAsResourceId.
action_tag="new_task_action"
form_tag="new_task_form"
title_tag="new_task_title"
title_hint_tag="new_task_title_hint"
title_counter_tag="new_task_title_counter"
save_tag="new_task_save"
discard_dialog_tag="new_task_discard_dialog"
discard_confirm_tag="new_task_discard_confirm"
# Invented, unique per run: a task or draft from an earlier run can never pass as this one.
run_id="$(od -An -N4 -tx1 /dev/urandom | tr -d ' \n')"
ui_title="UI-$run_id"
draft_title="Draft-$run_id"
# Expected list, top to bottom; grows by the UI task once it is created.
expected="$tmp/expected.txt"
cp "$seed/titles.txt" "$expected"

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

# dump_ui: current window hierarchy into $tmp/window.xml.
dump_ui() {
  timeout 30 adb shell uiautomator dump /data/local/tmp/window.xml >/dev/null 2>&1 || return 1
  timeout 30 adb exec-out cat /data/local/tmp/window.xml >"$tmp/window.xml" 2>/dev/null || return 1
}

# Checks the dump against $expected: the done task (done.txt) is shown, all titles present (in text or
# content-desc), tops strictly increasing, and the done task between its neighbours.
dump_matches() {
  dump_ui || return 1
  python3 -I - "$tmp/window.xml" "$expected" "$seed/done.txt" >"$tmp/match.txt" 2>&1 <<'PY'
import re, sys
import xml.etree.ElementTree as ET
nodes = ET.parse(sys.argv[1]).getroot().iter("node")
nodes = [(n.get("text", "") + "\n" + n.get("content-desc", ""), n.get("bounds", "")) for n in nodes]
titles = [t for t in open(sys.argv[2], encoding="utf-8").read().splitlines() if t]
done = open(sys.argv[3], encoding="utf-8").read().strip()
def top(title):
    hits = [b for text, b in nodes if title in text]
    return int(re.match(r"\[(-?\d+),(-?\d+)\]", hits[0]).group(2)) if hits else None
if done not in titles:
    print(f"broken seed: done task {done} not in the expected titles")
    sys.exit(1)
if top(done) is None:
    print(f"done task not at its place: {done} missing")
    sys.exit(1)
tops = []
for title in titles:
    if top(title) is None:
        print(f"missing: {title}")
        sys.exit(1)
    tops.append(top(title))
if any(a >= b for a, b in zip(tops, tops[1:])):
    print("wrong order: " + ", ".join(f"{t}@{y}" for t, y in zip(titles, tops)))
    sys.exit(1)
i = titles.index(done)
if (i > 0 and not tops[i - 1] < tops[i]) or (i + 1 < len(titles) and not tops[i] < tops[i + 1]):
    print(f"done task not at its place: {done} at index {i}: " + ", ".join(f"{t}@{y}" for t, y in zip(titles, tops)))
    sys.exit(1)
print("all titles in order: " + ", ".join(titles) + f"; done task {done} at index {i}")
PY
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
# field_has <tag> <text>: the field with this tag contains the text.
field_has() { dump_ui && node "$1" | cut -f2- | grep -qF -- "$2"; }

# tap <step> <tag>: waits up to 30 s for the node and taps the centre of its bounds.
tap() {
  local step="$1" tag="$2" xy
  wait_until 30 "$step: no node with resource-id $tag" shown "$tag"
  xy="$(node "$tag" | cut -f1)"
  # shellcheck disable=SC2086 # "x y" are two arguments
  timeout 10 adb shell input tap $xy || fail "$step: tap on $tag failed"
}

# type_text <step> <text>: types into the focused field without tapping it (letters, digits and - only).
type_text() { timeout 120 adb shell input text "$2" || fail "$1: input text failed"; }

# The image may have no keyboard app; then the keyboard stays with Robolectric (plan UCH-63 section 5).
ime_list=""
keyboard_shown() { timeout 10 adb shell dumpsys input_method | tr -d '\r' | grep -q 'mInputShown=true'; }

# open_new_task <step>: taps the action and waits until the title field is there (and the keyboard, if any).
open_new_task() {
  tap "$1" "$action_tag"
  wait_until 30 "$1: title field not shown after tapping the action" shown "$title_tag"
  if [ -n "$ime_list" ]; then
    wait_until 10 "$1: keyboard not shown after opening the input" keyboard_shown
  fi
}

# back_to_dialog <step>: Back until the discard dialog shows (a first Back may only close the keyboard).
back_to_dialog() {
  local tries=0
  until timeout 10 adb shell input keyevent KEYCODE_BACK && sleep 1 && shown "$discard_dialog_tag"; do
    tries=$((tries + 1))
    [ "$tries" -lt 3 ] || fail "$1: no discard dialog after Back"
  done
}

# discard <step>: Back, confirm in the dialog, wait until the input is closed.
discard() {
  back_to_dialog "$1"
  tap "$1" "$discard_confirm_tag"
  wait_until 30 "$1: input still open after discarding" gone "$form_tag"
}

# shot <name>: screenshot for the feature PR (only with SCREENSHOTS set).
shot() {
  local file="$SCREENSHOTS/$name-$1.png"
  mkdir -p "$SCREENSHOTS"
  sleep 1
  timeout 30 adb exec-out screencap -p >"$file" || fail "screenshot $1 failed"
  [ -s "$file" ] || fail "screenshot $1 empty"
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

# wait_list <step>: waits until the list shows the expected titles in order; no crash.
wait_list() {
  local step="$1" end
  end=$((SECONDS + 90))
  until dump_matches; do
    if crashed >"$tmp/crash.txt"; then cat "$tmp/crash.txt" >&2; fail "$step: crash"; fi
    if [ "$SECONDS" -ge "$end" ]; then
      fail "$step: expected titles not shown in expected order within 90s ($(cat "$tmp/match.txt" 2>/dev/null))"
    fi
    sleep 2
  done
  if crashed >"$tmp/crash.txt"; then cat "$tmp/crash.txt" >&2; fail "$step: crash"; fi
  timeout 10 adb shell pidof "$package" >/dev/null || fail "$step: process not running"
  log "$step: OK ($(cat "$tmp/match.txt"))"
}

# check_list <step>: starts the app and checks the list.
check_list() { start_app "$1"; wait_list "$1"; }

# install_seed: the seed in place of the app's database (app stopped): same owner as the data directory,
# restorecon, no -wal/-shm left over.
install_seed() {
  local owner
  owner="$(timeout 10 adb shell stat -c %u:%g "$app_dir" | tr -d '\r')"
  [ -n "$owner" ] || fail "owner of $app_dir unknown"
  timeout 60 adb push "$seed/tasks.db" /data/local/tmp/seed-tasks.db >/dev/null
  timeout 30 adb shell "mkdir -p $app_dir/databases && rm -f $app_dir/databases/tasks.db*"
  timeout 30 adb shell "cp /data/local/tmp/seed-tasks.db $app_dir/databases/tasks.db"
  timeout 30 adb shell "chown $owner $app_dir/databases $app_dir/databases/tasks.db && chmod 600 $app_dir/databases/tasks.db"
  timeout 30 adb shell "restorecon -R $app_dir/databases && rm -f /data/local/tmp/seed-tasks.db"
  log "seed installed (owner $owner)"
}

wait_for_boot
become_root
ime_list="$(timeout 10 adb shell ime list -s 2>/dev/null | tr -d '\r' || true)"
if [ -n "$ime_list" ]; then
  log "keyboard apps on this image: $(echo "$ime_list" | tr '\n' ' ')- keyboard checked after opening the input"
else
  log "no keyboard app on this image (ime list -s is empty): keyboard not checked on the emulator"
fi
apk="$(source "$here/apk-sign.sh" && sign_if_unsigned "$apk" "$tmp")"
timeout 60 adb uninstall "$package" >/dev/null 2>&1 || true
timeout 120 adb install "$apk" >/dev/null || fail "install failed"
component="$(timeout 30 adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN \
  -c android.intent.category.LAUNCHER "$package" | tr -d '\r' | tail -n 1)"
case "$component" in "$package"/*) ;; *) fail "no launcher activity found (got: $component)" ;; esac

# First start creates the data directory with the app's owner and SELinux label; then stop the app.
timeout 60 adb shell am start -W -n "$component" >/dev/null || fail "first start failed"
timeout 30 adb shell am force-stop "$package"
install_seed

check_list "after start"

# Create through the UI: the typed text lands in the title without tapping the field (focus is there).
open_new_task "after create"
type_text "after create" "$ui_title"
wait_until 30 "after create: typed title not in the title field (focus not in the title?)" field_has "$title_tag" "$ui_title"
tap "after create" "$save_tag"
{ echo "$ui_title"; cat "$seed/titles.txt"; } >"$expected"
wait_list "after create"

# Screenshots for the feature PR: list, empty input, title missing, title too long, discard dialog.
if [ -n "${SCREENSHOTS:-}" ]; then
  shot 1-list
  open_new_task "screenshots"
  shot 2-input-empty
  tap "screenshots" "$save_tag"
  wait_until 30 "screenshots: no hint after saving without a title" shown "$title_hint_tag"
  shot 3-title-missing
  type_text "screenshots" "$(python3 -I -c 'print(("Zu-lang-" * 30)[:201])')"
  wait_until 30 "screenshots: no counter for a too long title" shown "$title_counter_tag"
  wait_until 30 "screenshots: no hint for a too long title" shown "$title_hint_tag"
  shot 4-title-too-long
  back_to_dialog "screenshots"
  shot 5-discard-dialog
  tap "screenshots" "$discard_confirm_tag"
  wait_until 30 "screenshots: input still open after discarding" gone "$form_tag"
  wait_list "screenshots"
  log "screenshots written to $SCREENSHOTS"
fi

# Draft after the app was killed in the background (edge case app switch): am kill only ends a background
# process and keeps the saved state, unlike force-stop.
open_new_task "draft after process kill"
type_text "draft after process kill" "$draft_title"
wait_until 30 "draft after process kill: typed draft not in the title field" field_has "$title_tag" "$draft_title"
timeout 10 adb shell input keyevent KEYCODE_HOME || fail "draft after process kill: HOME failed"
# The process can be killed once it is cached in the background; retry until pidof no longer finds it.
for _ in 1 2 3 4 5 6; do
  sleep 2
  timeout 30 adb shell am kill "$package" >/dev/null 2>&1 || true
  timeout 10 adb shell pidof "$package" >/dev/null 2>&1 || break
done
wait_process_gone
start_app "draft after process kill"
wait_until 60 "draft after process kill: title field does not contain the draft after the restart" \
  field_has "$title_tag" "$draft_title"
log "draft after process kill: draft restored in the title field"
discard "draft after process kill"
wait_list "draft after process kill"

timeout 30 adb shell am force-stop "$package"
wait_process_gone
check_list "after process end"

timeout 60 adb reboot || fail "adb reboot failed"
wait_for_boot
check_list "after reboot"

log "OK: list after start, UI task created, draft kept after process kill, list unchanged after process end and reboot"
