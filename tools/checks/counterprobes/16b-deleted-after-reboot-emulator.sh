#!/usr/bin/env bash
# S3 emulator check (plan UCH-58 section 7, counterprobe b): the database is deleted after the reboot,
# before the app starts; emulator-tasks.sh must fail in the reboot step. Runs in the emulator job only.
# Usage: 16b-deleted-after-reboot-emulator.sh <apk> <seed-dir>
source "$(dirname "$0")/lib.sh"
apk="$(realpath "$1")"; seed="$(realpath "$2")"
make_copy
replace tools/checks/emulator-tasks.sh 'check_list "after reboot"' \
  'become_root; timeout 30 adb shell "rm -f $app_dir/databases/tasks.db*"; check_list "after reboot"'
expect_red "database deleted after reboot (emulator)" "after reboot: expected titles not shown" \
  tools/checks/emulator-tasks.sh "$apk" "$seed"
