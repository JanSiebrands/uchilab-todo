#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"
make_copy
# Point 6: full backup (Android 11 and older) switched on by an <include>.
replace app/src/main/res/xml/backup_rules.xml "</full-backup-content>" "    <include domain=\"file\" path=\".\" />
</full-backup-content>"
expect_red "full backup include" "full backup content: <include> is not allowed" ./gradlew "${GRADLE_ARGS[@]}" privacyCheck
