#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"
make_copy
# DSGVO P4 (V2): an entry removed from the field list while the schema still has the column.
replace tools/checks/stored-fields.txt "task.note
" ""
expect_red "task.note removed from the field list" "task.note is stored but not in tools/checks/stored-fields.txt" \
  ./gradlew "${GRADLE_ARGS[@]}" privacyCheck
