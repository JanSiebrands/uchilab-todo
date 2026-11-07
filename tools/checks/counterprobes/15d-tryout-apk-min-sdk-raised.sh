#!/usr/bin/env bash
# S2a: an APK built with minSdk + 1 must fail the install test on the emulator of the job tryout-apk.
# Shows that the device really runs the lowest supported version. Runs in the job tryout-apk only.
source "$(dirname "$0")/lib.sh"
make_copy
min_sdk="$("$src/tools/ci/tryout-min-sdk.sh" .)"
replace app/build.gradle.kts "minSdk = $min_sdk" "minSdk = $((min_sdk + 1))"
expect_green "debug APK with minSdk $((min_sdk + 1)) builds" ./gradlew "${GRADLE_ARGS[@]}" assembleDebug
expect_red "minSdk raised" "test must run on minSdk" \
  "$src/tools/ci/tryout-install-test.sh" app/build/outputs/apk/debug/app-debug.apk "$("$src/tools/ci/tryout-min-sdk.sh" .)"
