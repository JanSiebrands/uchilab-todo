#!/usr/bin/env bash
# S4d screenshots (plan UCH-99 section 2, Z9, Z10): a copy of emulator-showcase.sh that opens the discard dialog
# for the picture by key (KEYCODE_BACK, as before UCH-99) instead of tapping close; the key event leaves touch
# mode, so a dialog button gets the focus and ui-node-unfocused.py must fail with "focused:". Proves that the
# check sees the focus at all. Runs in the screenshots job only, after 16s (dark-de is set there, a combination
# with the dialog); its pictures go to a temp dir, not into the artifact.
# Usage: 16w-dialog-by-key-emulator.sh <apk> <showcase-seed-dir>
source "$(dirname "$0")/lib.sh"
apk="$(realpath "$1")"; seed="$(realpath "$2")"
make_copy
replace tools/checks/emulator-showcase.sh '  tap "input" "$close_tag"
' '  tries=0
  until timeout 10 adb shell input keyevent KEYCODE_BACK && sleep 1 && shown "$discard_dialog_tag"; do
    tries=$((tries + 1))
    [ "$tries" -lt 3 ] || fail "input: no discard dialog after Back"
  done
'
expect_red "discard dialog opened by key (emulator)" "focused: new_task_discard_" \
  env SCREENSHOTS="$work/shots" EMULATOR_LOGS= tools/checks/emulator-showcase.sh "$apk" "$seed"
