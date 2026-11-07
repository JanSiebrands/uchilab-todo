#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"
make_copy
# DSGVO P4: new column in TaskEntity and in the field list, but the old schema is committed. privacyCheck is
# green (it reads the regenerated schema), the CI step after the build must find the stale schema.
replace app/src/main/kotlin/de/uchilab/todo/data/TaskEntity.kt "    val status: String,
) {" "    val status: String,
    val probeColumn: Int = 0,
) {"
echo "task.probeColumn" >>tools/checks/stored-fields.txt
# The copy has no git metadata; schema-check.sh compares with a commit, so commit this state.
git init -q
git add -A
git -c user.name=counterprobe -c user.email=counterprobe@invalid commit -q -m "stale schema"
expect_green "privacyCheck with field list entry" ./gradlew "${GRADLE_ARGS[@]}" privacyCheck
expect_red "schema committed from the old code" "schema-check: app/schemas differs from the commit" \
  tools/checks/schema-check.sh
