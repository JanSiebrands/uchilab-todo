#!/usr/bin/env bash
# S3 emulator check (plan UCH-58 section 7, counterprobe a): the seed is not copied onto the device;
# emulator-tasks.sh must fail because the titles are not shown. Runs in the emulator job only.
# Usage: 16a-seed-missing-emulator.sh <apk> <seed-dir>
source "$(dirname "$0")/lib.sh"
apk="$(realpath "$1")"; seed="$(realpath "$2")"
make_copy
# Without the copy there is no file to chown; the directory keeps its owner.
replace tools/checks/emulator-lib.sh '  timeout 30 adb shell "cp /data/local/tmp/seed-tasks.db $app_dir/databases/tasks.db"
  timeout 30 adb shell "chown $owner $app_dir/databases $app_dir/databases/tasks.db && chmod 600 $app_dir/databases/tasks.db"' \
  '  timeout 30 adb shell "chown $owner $app_dir/databases"'
expect_red "seed not copied (emulator)" "after start: expected titles not shown" \
  tools/checks/emulator-tasks.sh "$apk" "$seed"
