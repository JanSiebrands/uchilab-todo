#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"
make_copy
# Point 6: referenced rules file missing. Resource linking (AGP) already fails before privacyCheck runs,
# so the expected message is the one from AGP; the build is red either way.
rm app/src/main/res/xml/backup_rules.xml
expect_red "missing backup rules file" "xml/backup_rules" ./gradlew "${GRADLE_ARGS[@]}" privacyCheck
