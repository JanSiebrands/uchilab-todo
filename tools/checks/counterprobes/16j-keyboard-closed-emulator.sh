#!/usr/bin/env bash
# S4 emulator check (plan UCH-63 section 5, requirement 7 of UCH-65, counterprobe 16j): on an image with a
# keyboard app, emulator-tasks.sh checks mInputShown=true after opening the input. Here the keyboard is closed
# with Back right after it came up, as if the app did not open it; the check must fail with "keyboard not shown
# after opening the input". Without a keyboard app the check is skipped, the script stays green and this
# counterprobe fails, so it only works on the image of the screenshots job (target default).
# Usage: 16j-keyboard-closed-emulator.sh <apk> <seed-dir>
source "$(dirname "$0")/lib.sh"
apk="$(realpath "$1")"; seed="$(realpath "$2")"
make_copy
replace tools/checks/emulator-tasks.sh '  if [ -n "$ime_list" ]; then
    wait_until 10 "$1: keyboard not shown after opening the input" keyboard_shown' '  if [ -n "$ime_list" ]; then
    wait_until 10 "counterprobe 16j: keyboard never came up before Back" keyboard_shown
    timeout 10 adb shell input keyevent KEYCODE_BACK
    sleep 2
    wait_until 10 "$1: keyboard not shown after opening the input" keyboard_shown'
expect_red "keyboard closed after opening the input (emulator)" \
  "after create: keyboard not shown after opening the input" \
  tools/checks/emulator-tasks.sh "$apk" "$seed"
