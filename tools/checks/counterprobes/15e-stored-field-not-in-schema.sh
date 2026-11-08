#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"
make_copy
# DSGVO P4 (V2): the list must match exactly, an entry without column in the schema is red too.
echo "task.probe_column" >>tools/checks/stored-fields.txt
expect_red "field list entry not in the schema" "task.probe_column is in tools/checks/stored-fields.txt but not in the Room schema" \
  ./gradlew "${GRADLE_ARGS[@]}" privacyCheck
