#!/usr/bin/env bash
# Emulator check for S3 (plan UCH-58 section 7) and S4 (plan UCH-63 section 6): tasks written by the S2 data
# layer (seed from EmulatorSeedTest) are shown in the list, in the expected order, the done task at its place.
# Then a task is created through the UI (adb shell input, nodes found by resource-id = test tag, never by UI
# text), a draft survives the app being killed in the background, and the list (UI task on top, then the
# seed) is shown after the process end and after a device reboot. Usage: emulator-tasks.sh <apk> <seed-dir>
# <seed-dir> holds tasks.db, titles.txt (expected titles in list order) and done.txt (title of the done task).
# Needs `adb root` (emulator only). The screenshots for the feature PR moved to emulator-showcase.sh (UCH-81).
# Device helpers (one emulator only, every wait with a timeout) come from emulator-lib.sh; the exit code decides.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
apk="$1"
seed="$2"
package="de.uchilab.todo"
app_dir="/data/data/$package"
name="$(basename "$apk" .apk)"
tmp="$(mktemp -d)"

check=emulator-tasks
source "$here/emulator-lib.sh"
select_emulator

cleanup() { timeout 60 adb uninstall "$package" >/dev/null 2>&1 || true; rm -rf "$tmp"; }
trap cleanup EXIT

[ -s "$seed/tasks.db" ] || fail "seed $seed/tasks.db missing (EmulatorSeedTest with -Puchilab.seedOut)"
[ -s "$seed/titles.txt" ] || fail "seed $seed/titles.txt missing"
[ -s "$seed/done.txt" ] || fail "seed $seed/done.txt missing"

# Test tags of the app (NewTaskTags), used as resource-id thanks to testTagsAsResourceId.
action_tag="new_task_action"
form_tag="new_task_form"
title_tag="new_task_title"
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

# Checks the dump against $expected (repository order, newest first): the done task (done.txt) is shown, all
# titles present (in text or content-desc), open tasks first in that order, then the done task in its own
# group below the last open task (S6, UCH-129), tops strictly increasing.
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
titles = [t for t in titles if t != done] + [done]
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
if i > 0 and not tops[i - 1] < tops[i]:
    print(f"done task not at its place: {done} at index {i}: " + ", ".join(f"{t}@{y}" for t, y in zip(titles, tops)))
    sys.exit(1)
print("all titles in order: " + ", ".join(titles) + f"; done task {done} at index {i}")
PY
}

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

# install_seed: the seed in place of the app's database (app stopped), see install_db.
install_seed() { install_db "$seed/tasks.db"; }

wait_for_boot
become_root
ime_list="$(timeout 10 adb shell ime list -s 2>/dev/null | tr -d '\r' || true)"
if [ -n "$ime_list" ]; then
  log "keyboard apps on this image: $(echo "$ime_list" | tr '\n' ' ')- keyboard checked after opening the input"
else
  log "no keyboard app on this image (ime list -s is empty): keyboard not checked on the emulator"
fi
install_app
install_seed

check_list "after start"

# Create through the UI: the typed text lands in the title without tapping the field (focus is there).
open_new_task "after create"
type_text "after create" "$ui_title"
wait_until 30 "after create: typed title not in the title field (focus not in the title?)" field_has "$title_tag" "$ui_title"
tap "after create" "$save_tag"
{ echo "$ui_title"; cat "$seed/titles.txt"; } >"$expected"
wait_list "after create"

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
