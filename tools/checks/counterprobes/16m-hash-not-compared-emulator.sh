#!/usr/bin/env bash
# S4b emulator check (plan UCH-80 section 5, Z2): a copy of emulator-read-failure.sh changes one byte of the
# file on the device after the first start (same size, other content). The hash comparison must fail, so it is
# shown that step 4 really compares. Runs in the job emulator-read-failure only.
# Usage: 16m-hash-not-compared-emulator.sh <apk> <seed-dir>
source "$(dirname "$0")/lib.sh"
apk="$(realpath "$1")"; seed="$(realpath "$2")"
make_copy
# Case A starts with "K"; "Z" keeps the size and changes the content.
replace tools/checks/emulator-read-failure.sh '  timeout 30 adb shell am force-stop "$package"
  wait_process_gone' '  timeout 30 adb shell am force-stop "$package"
  wait_process_gone
  timeout 30 adb shell "printf Z | dd of=$db bs=1 count=1 conv=notrunc 2>/dev/null"'
expect_red "file changed on the device after the first start (emulator)" "case A after first start: file changed (sha256" \
  tools/checks/emulator-read-failure.sh "$apk" "$seed"
