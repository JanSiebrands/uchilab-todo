#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"
make_copy
# Security P2 (B2): a backup agent on the application.
replace app/src/main/AndroidManifest.xml "    <application
" "    <application
        android:backupAgent=\".ProbeBackupAgent\"
"
expect_red "backupAgent on application" "android:backupAgent is not allowed on application" \
  ./gradlew "${GRADLE_ARGS[@]}" privacyCheck
