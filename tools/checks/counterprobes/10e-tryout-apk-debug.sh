#!/usr/bin/env bash
# S2a: a debug APK instead of the release build must fail tryout-apk-check.sh (application-debuggable).
# Runs in the job tryout-apk with the other counterprobes 10.
source "$(dirname "$0")/lib.sh"
make_copy
expect_green "debug APK builds" ./gradlew "${GRADLE_ARGS[@]}" assembleDebug
expect_red "debug APK" "tryout-apk-check: FAILED: APK is debuggable" \
  "$src/tools/ci/tryout-apk-check.sh" app/build/outputs/apk/debug/app-debug.apk "$("$src/tools/ci/tryout-min-sdk.sh" .)"
