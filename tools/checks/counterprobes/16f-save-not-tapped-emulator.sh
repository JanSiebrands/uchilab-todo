#!/usr/bin/env bash
# S4 emulator check (plan UCH-63 section 6, counterprobe 16f): Save is not tapped, the input is closed with
# Back + discard instead; emulator-tasks.sh must fail in "after create". Runs in the emulator job only.
# Usage: 16f-save-not-tapped-emulator.sh <apk> <seed-dir>
source "$(dirname "$0")/lib.sh"
apk="$(realpath "$1")"; seed="$(realpath "$2")"
make_copy
replace tools/checks/emulator-tasks.sh 'tap "after create" "$save_tag"' 'discard "after create"'
expect_red "Save not tapped (emulator)" \
  "after create: expected titles not shown in expected order within 90s (missing: UI-" \
  tools/checks/emulator-tasks.sh "$apk" "$seed"
