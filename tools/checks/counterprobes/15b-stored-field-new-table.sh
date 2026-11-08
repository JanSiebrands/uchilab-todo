#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"
make_copy
# DSGVO P4 (V2): a new entity (table) in the database, field list not touched.
cat >app/src/main/kotlin/de/uchilab/todo/data/ProbeEntity.kt <<'KOTLIN'
package de.uchilab.todo.data

import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "probe")
class ProbeEntity(@PrimaryKey val id: Long)
KOTLIN
replace app/src/main/kotlin/de/uchilab/todo/data/TaskDatabase.kt "entities = [TaskEntity::class]" \
  "entities = [TaskEntity::class, ProbeEntity::class]"
expect_red "new table probe" "probe.id is stored but not in tools/checks/stored-fields.txt" \
  ./gradlew "${GRADLE_ARGS[@]}" privacyCheck
