#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"
make_copy
# DSGVO P4 (V2): a new column in TaskEntity, schema and field list not touched. privacyCheck regenerates the
# schema from the code and must name the new field.
replace app/src/main/kotlin/de/uchilab/todo/data/TaskEntity.kt "    val status: String,
) {" "    val status: String,
    val probeColumn: Int = 0,
) {"
expect_red "new column in TaskEntity" "task.probeColumn is stored but not in tools/checks/stored-fields.txt" \
  ./gradlew "${GRADLE_ARGS[@]}" privacyCheck
