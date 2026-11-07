#!/usr/bin/env bash
# S3 emulator check (plan UCH-58 section 7, counterprobe a): the seed is not copied onto the device;
# emulator-tasks.sh must fail because the titles are not shown. Runs in the emulator job only.
# Usage: 16a-seed-missing-emulator.sh <apk> <seed-dir>
source "$(dirname "$0")/lib.sh"
apk="$(realpath "$1")"; seed="$(realpath "$2")"
make_copy
replace tools/checks/emulator-tasks.sh 'timeout 30 adb shell "cp /data/local/tmp/seed-tasks.db $app_dir/databases/tasks.db"' ':'
expect_red "seed not copied (emulator)" "after start: expected titles not shown" \
  tools/checks/emulator-tasks.sh "$apk" "$seed"
