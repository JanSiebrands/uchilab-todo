#!/usr/bin/env bash
# S4c screenshots (plan UCH-81 section 6, Z1, Z2, Z5): a copy of emulator-showcase.sh that requires the tag of
# the read failure notice for the list picture; shot must fail with "wrong state" because that tag is neither
# in the dump before nor after screencap. Runs in the screenshots job only (one combination, the one set on the
# device), its pictures go to a temp dir, not into the artifact.
# Usage: 16s-wrong-state-emulator.sh <apk> <showcase-seed-dir>
source "$(dirname "$0")/lib.sh"
apk="$(realpath "$1")"; seed="$(realpath "$2")"
make_copy
replace tools/checks/emulator-showcase.sh 'shot list "$list_tag"' 'shot list task_list_error'
expect_red "list picture with the wrong state (emulator)" "screenshot list: wrong state" \
  env SCREENSHOTS="$work/shots" EMULATOR_LOGS= tools/checks/emulator-showcase.sh "$apk" "$seed"
