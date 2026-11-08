#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"
make_copy
# DSGVO P4: a new column in TaskEntity, field list not touched, and a stale schema file with a higher version
# than the code (e.g. a version bump that was reverted, 2.json left in the commit). Room only writes the
# schema of the code's version (1.json); the stale 2.json must not hide the new field.
replace app/src/main/kotlin/de/uchilab/todo/data/TaskEntity.kt "    val status: String,
) {" "    val status: String,
    val probeColumn: Int = 0,
) {"
schemas=app/schemas/de.uchilab.todo.data.TaskDatabase
[ -f "$schemas/1.json" ] && [ ! -e "$schemas/2.json" ] || { echo "COUNTERPROBE BROKEN: expected only $schemas/1.json" >&2; exit 2; }
cp "$schemas/1.json" "$schemas/2.json"
expect_red "new column hidden behind a stale 2.json" "task.probeColumn is stored but not in tools/checks/stored-fields.txt" \
  ./gradlew "${GRADLE_ARGS[@]}" privacyCheck
