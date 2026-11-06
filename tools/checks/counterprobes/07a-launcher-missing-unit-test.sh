#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"
make_copy
# Point 7: launcher entry removed; the launcher unit test (Robolectric) must fail.
perl -0pi -e "s|\\s*<intent-filter>.*?</intent-filter>||s" app/src/main/AndroidManifest.xml
! grep -q "android.intent.category.LAUNCHER" app/src/main/AndroidManifest.xml \
  || { echo "COUNTERPROBE BROKEN: launcher entry not removed" >&2; exit 2; }
# Expect a failing test, not "No tests found" (which would also mention the class name).
expect_red "launcher entry missing (unit test)" "LauncherEntryTest > " \
  ./gradlew "${GRADLE_ARGS[@]}" testDebugUnitTest --tests "de.uchilab.todo.LauncherEntryTest"
