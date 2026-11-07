#!/usr/bin/env bash
# S3 emulator check (UCH-62): while waiting for the process end, adb hangs (pidof does not answer within its
# timeout). emulator-tasks.sh must abort with a clear message instead of counting it as "process ended".
# Runs in the emulator job only. Usage: 16e-pidof-hangs-emulator.sh <apk> <seed-dir>
source "$(dirname "$0")/lib.sh"
apk="$(realpath "$1")"; seed="$(realpath "$2")"
make_copy
replace tools/checks/emulator-tasks.sh 'timeout 10 adb shell pidof "$package" >/dev/null 2>&1 || status=$?' \
  'timeout 10 sleep 30 >/dev/null 2>&1 || status=$?'
expect_red "adb hangs while waiting for the process end (emulator)" "adb shell pidof did not answer within 10s" \
  tools/checks/emulator-tasks.sh "$apk" "$seed"
