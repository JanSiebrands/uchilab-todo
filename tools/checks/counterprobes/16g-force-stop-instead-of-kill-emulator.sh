#!/usr/bin/env bash
# S4 emulator check (plan UCH-63 section 6, counterprobe 16g): force-stop instead of am kill drops the saved
# state; emulator-tasks.sh must fail in "draft after process kill". Shows that the step really checks the
# restored draft. Runs in the emulator job only. Usage: 16g-force-stop-instead-of-kill-emulator.sh <apk> <seed-dir>
source "$(dirname "$0")/lib.sh"
apk="$(realpath "$1")"; seed="$(realpath "$2")"
make_copy
replace tools/checks/emulator-tasks.sh 'timeout 30 adb shell am kill "$package"' 'timeout 30 adb shell am force-stop "$package"'
expect_red "force-stop instead of am kill (emulator)" \
  "draft after process kill: title field does not contain the draft after the restart" \
  tools/checks/emulator-tasks.sh "$apk" "$seed"
