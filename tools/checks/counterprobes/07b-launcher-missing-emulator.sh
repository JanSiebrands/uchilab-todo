#!/usr/bin/env bash
# Point 7: launcher entry removed; app-start.sh must fail on the emulator. Runs in the emulator job only.
source "$(dirname "$0")/lib.sh"
make_copy
perl -0pi -e "s|\s*<intent-filter>.*?</intent-filter>||s" app/src/main/AndroidManifest.xml
! grep -q "android.intent.category.LAUNCHER" app/src/main/AndroidManifest.xml \
  || { echo "COUNTERPROBE BROKEN: launcher entry not removed" >&2; exit 2; }
expect_green "debug APK without launcher entry builds" ./gradlew "${GRADLE_ARGS[@]}" assembleDebug
expect_red "launcher entry missing (emulator)" "no launcher activity found" \
  "$src/tools/checks/app-start.sh" app/build/outputs/apk/debug/app-debug.apk
