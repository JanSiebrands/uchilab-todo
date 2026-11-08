#!/usr/bin/env bash
# S4 emulator check (plan UCH-63 section 6, counterprobe 16e, addendum 2 of UCH-45): after the force-stop the
# database is replaced by the seed, so the task created through the UI is gone; emulator-tasks.sh must fail in
# "after process end" because the UI title is missing. Runs in the emulator job only.
# (Not to be confused with 16e-pidof-hangs-emulator.sh from UCH-62.)
# Usage: 16e-ui-task-lost-emulator.sh <apk> <seed-dir>
source "$(dirname "$0")/lib.sh"
apk="$(realpath "$1")"; seed="$(realpath "$2")"
make_copy
replace tools/checks/emulator-tasks.sh 'wait_process_gone
check_list "after process end"' 'wait_process_gone
install_seed
check_list "after process end"'
expect_red "UI task lost after process end (emulator)" \
  "after process end: expected titles not shown in expected order within 90s (missing: UI-" \
  tools/checks/emulator-tasks.sh "$apk" "$seed"
