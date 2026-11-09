#!/usr/bin/env bash
# Screenshots for the feature PR (plan UCH-81 sections 2-5): for the light/dark mode and the device language the
# caller has set (emulator-screenshots.sh), this script reads both from the device and writes
# release-<state>-<light|dark>-<de|en>.png to $SCREENSHOTS:
#   every combination: empty (fresh install), list (readable seed of ShowcaseSeedTest for the language);
#   light-en and dark-de: input-empty, input-title-missing, input-title-too-long, input-discard-dialog;
#   light-de: input-font200 (font scale 2.0); dark-de: input-landscape (user rotation 1);
#   every combination: edit; light-de and dark-en: edit-discard-dialog; light-en: edit-landscape (UCH-137).
# Before each picture the script waits for the state's test tag (operation). The pictures are evidence, not a
# check (UCH-116 R2): whether they show the right state, language and theme, the designer and the PO judge by
# looking at them. The discard dialogs are opened by tapping close, as with a finger (UCH-99). Every setting is
# read back and reset afterwards.
# Usage: SCREENSHOTS=<dir> emulator-showcase.sh <apk> <showcase-seed-dir>   (holds de/ and en/ of
# ShowcaseSeedTest). Needs `adb root` (emulator only), invented data only; the exit code decides.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
apk="$1"
seed_root="$2"
package="de.uchilab.todo"
app_dir="/data/data/$package"
name="release"
tmp="$(mktemp -d)"

check=emulator-showcase
source "$here/emulator-lib.sh"
[ -n "${SCREENSHOTS:-}" ] || fail "SCREENSHOTS not set"
select_emulator

# Test tags of the app (TaskListTags, NewTaskTags), used as resource-id thanks to testTagsAsResourceId.
list_tag="task_list"
item_tag="task_item"
empty_tag="task_list_empty"
done_heading_tag="task_list_done_heading"
action_tag="new_task_action"
form_tag="new_task_form"
title_tag="new_task_title"
title_hint_tag="new_task_title_hint"
title_counter_tag="new_task_title_counter"
save_tag="new_task_save"
discard_dialog_tag="new_task_discard_dialog"
discard_confirm_tag="new_task_discard_confirm"
discard_keep_tag="new_task_discard_keep"
close_tag="new_task_close"
item_note_tag="task_item_note"

reset_display() {
  timeout 10 adb shell settings put system font_scale 1.0 >/dev/null 2>&1 || true
  timeout 10 adb shell settings put system user_rotation 0 >/dev/null 2>&1 || true
}
cleanup() { reset_display; timeout 60 adb uninstall "$package" >/dev/null 2>&1 || true; rm -rf "$tmp"; }
trap cleanup EXIT

# setting <namespace> <key> <value>: sets an Android setting and reads it back.
setting() {
  timeout 10 adb shell settings put "$1" "$2" "$3" || fail "settings put $1 $2 $3 failed"
  local now
  now="$(timeout 10 adb shell settings get "$1" "$2" | tr -d '\r')"
  [ "$now" = "$3" ] || fail "setting $1 $2: set $3, read back $now"
  log "setting $1 $2 = $now (read back)"
}

wait_for_boot
become_root

# Mode and language as set by the caller, read back from the device.
case "$(timeout 10 adb shell cmd uimode night | tr -d '\r')" in
  *"Night mode: yes"*) mode=dark ;;
  *"Night mode: no"*) mode=light ;;
  *) fail "night mode unknown: $(timeout 10 adb shell cmd uimode night | tr -d '\r')" ;;
esac
case "$(timeout 10 adb shell getprop persist.sys.locale | tr -d '\r')" in
  de-DE) lang=de ;;
  en-US) lang=en ;;
  *) fail "device language unknown: persist.sys.locale=$(timeout 10 adb shell getprop persist.sys.locale | tr -d '\r')" ;;
esac
export SHOT_BUILD=release SHOT_SUFFIX="-$mode-$lang"
name="release$SHOT_SUFFIX"
seed="$seed_root/$lang"
log "mode $mode, language $lang (read back from the device), seed $seed"
[ -s "$seed/tasks.db" ] || fail "seed $seed/tasks.db missing (ShowcaseSeedTest with -Puchilab.showcaseOut)"
[ -s "$seed/tasks.json" ] || fail "seed $seed/tasks.json missing"

# Portrait and font scale 1.0 for the regular pictures.
setting system accelerometer_rotation 0
setting system user_rotation 0
setting system font_scale 1.0
screen_on

# tap <step> <tag>: waits up to 30 s for the node and taps the centre of its bounds.
tap() {
  local xy
  wait_until 30 "$1: no node with resource-id $2" shown "$2"
  xy="$(node "$2" | cut -f1)"
  # shellcheck disable=SC2086 # "x y" are two arguments
  timeout 10 adb shell input tap $xy || fail "$1: tap on $2 failed"
}

open_new_task() {
  tap "$1" "$action_tag"
  wait_until 30 "$1: title field not shown after tapping the action" shown "$title_tag"
}

# restart <step>: force-stop (drops the saved state, so no draft is left) and start with the list.
restart() {
  timeout 30 adb shell am force-stop "$package"
  wait_process_gone
  start_app "$1"
  wait_until 90 "$1: list not shown" shown "$list_tag"
}

rotation_is() { dump_ui && grep -q "<hierarchy rotation=\"$1\"" "$tmp/window.xml"; }

install_app

# Empty state: fresh install, no database.
start_app "empty"
wait_until 60 "empty: empty state not shown" shown "$empty_tag"
shot empty "$empty_tag"

# List: readable seed of this language.
timeout 30 adb shell am force-stop "$package"
wait_process_gone
install_db "$seed/tasks.db"
start_app "list"
wait_until 90 "list: list not shown" shown "$list_tag"
shot list "$list_tag"

# Edit (UCH-137, plan UCH-123 Z23/Z34/Z46): open the first entry with a note by tapping its note line (inside the
# clickable entry task_item); an existing task opens without focus and keyboard. Pictures only (evidence, UCH-116).
open_edit() {
  tap "$1" "$item_note_tag"
  wait_until 30 "$1: form not shown after tapping the entry" shown "$title_tag"
}
open_edit "edit"
shot edit "$form_tag"
if [ "$mode-$lang" = light-de ] || [ "$mode-$lang" = dark-en ]; then
  # Change the title and close by touch (as in the input flow above, so no key event leaves touch mode).
  tap "edit" "$title_tag"
  timeout 30 adb shell input text 2 || fail "edit: input text failed"
  tap "edit" "$close_tag"
  wait_until 30 "edit: no discard dialog after changing the title and closing" shown "$discard_dialog_tag"
  shot edit-discard-dialog "$discard_dialog_tag"
  tap "edit" "$discard_confirm_tag"
else
  tap "edit" "$close_tag"
fi
wait_until 30 "edit: form still open after closing" gone "$form_tag"

if [ "$mode-$lang" = light-en ] || [ "$mode-$lang" = dark-de ]; then
  open_new_task "input"
  shot input-empty "$form_tag"
  tap "input" "$save_tag"
  wait_until 30 "input: no hint after saving without a title" shown "$title_hint_tag"
  shot input-title-missing "$title_hint_tag"
  long="$(python3 -I -c 'import sys; print((sys.argv[1] * 30)[:201])' "$([ "$lang" = de ] && echo Zu-lang- || echo Too-long-)")"
  timeout 120 adb shell input text "$long" || fail "input: input text failed"
  wait_until 30 "input: no counter for a too long title" shown "$title_counter_tag"
  wait_until 30 "input: no hint for a too long title" shown "$title_hint_tag"
  shot input-title-too-long "$title_counter_tag"
  # The dialog for the picture is opened by touch (close button), as with a finger: a key event (input text,
  # KEYCODE_BACK) leaves touch mode and Compose then shows a focus frame on a dialog button (plan UCH-99
  # section 2).
  tap "input" "$close_tag"
  wait_until 30 "input: no discard dialog after tapping close" shown "$discard_dialog_tag"
  shot input-discard-dialog "$discard_dialog_tag"
  # Back opens the same dialog (check of UCH-81, kept): first back to the form with "Keep".
  tap "input" "$discard_keep_tag"
  wait_until 30 "input: discard dialog still open after Keep" gone "$discard_dialog_tag"
  wait_until 30 "input: form not shown after Keep" shown "$form_tag"
  tries=0
  until timeout 10 adb shell input keyevent KEYCODE_BACK && sleep 1 && shown "$discard_dialog_tag"; do
    tries=$((tries + 1))
    [ "$tries" -lt 3 ] || fail "input: no discard dialog after Back"
  done
  tap "input" "$discard_confirm_tag"
  wait_until 30 "input: input still open after discarding" gone "$form_tag"
fi

if [ "$mode-$lang" = light-de ]; then
  setting system font_scale 2.0
  restart "font200"
  open_new_task "font200"
  shot input-font200 "$form_tag"
  setting system font_scale 1.0
  restart "font200 reset"
fi

if [ "$mode-$lang" = dark-de ]; then
  setting system user_rotation 1
  wait_until 30 "landscape: display not rotated (hierarchy rotation 1)" rotation_is 1
  restart "landscape"
  open_new_task "landscape"
  shot input-landscape "$form_tag"
  setting system user_rotation 0
  wait_until 30 "landscape reset: display not back in portrait" rotation_is 0
  restart "landscape reset"
fi

# Edit in landscape (UCH-137, plan UCH-123 Z47): tap into the title, picture with the keyboard open.
keyboard_shown() { timeout 10 adb shell dumpsys input_method | tr -d '\r' | grep -q 'mInputShown=true'; }
if [ "$mode-$lang" = light-en ]; then
  # Through the window manager as well: the setting alone was not applied here (run 42552339309, no rotation in
  # the logcat), the read back below stays.
  timeout 10 adb shell wm user-rotation lock 1 || fail "edit landscape: wm user-rotation lock 1 failed"
  setting system user_rotation 1
  wait_until 30 "edit landscape: display not rotated (hierarchy rotation 1)" rotation_is 1
  restart "edit landscape"
  open_edit "edit landscape"
  tap "edit landscape" "$title_tag"
  wait_until 30 "edit landscape: keyboard not shown after tapping the title" keyboard_shown
  shot edit-landscape "$form_tag"
  timeout 10 adb shell wm user-rotation lock 0 || fail "edit landscape reset: wm user-rotation lock 0 failed"
  setting system user_rotation 0
  wait_until 30 "edit landscape reset: display not back in portrait" rotation_is 0
  restart "edit landscape reset"
fi

# Lists with done tasks (UCH-130, plan UCH-122 Z27/Z28): seeds done-<lang> and all-done-en of ShowcaseSeedTest.
# Pictures only (evidence, UCH-116), waiting for the heading "Done" before each one; last, because the steps
# above restart with the list seed.
shot_done_seed() {
  [ -s "$seed_root/$2/tasks.db" ] || fail "seed $seed_root/$2/tasks.db missing"
  timeout 30 adb shell am force-stop "$package"
  wait_process_gone
  install_db "$seed_root/$2/tasks.db"
  start_app "$1"
  wait_until 90 "$1: heading of the done tasks not shown" shown "$done_heading_tag"
  shot "$1" "$done_heading_tag"
}
shot_done_seed list-done "done-$lang"
if [ "$mode-$lang" = dark-en ]; then shot_done_seed list-all-done all-done-en; fi

log "OK: screenshots for $mode-$lang written to $SCREENSHOTS"
