#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"
make_copy
# Point 1: release build type without minify setting (default is false).
replace app/build.gradle.kts "isMinifyEnabled = true" ""
replace app/build.gradle.kts "isShrinkResources = true" ""
expect_red "R8 off via missing isMinifyEnabled" "R8 is not active in the release build" \
  ./gradlew "${GRADLE_ARGS[@]}" assembleRelease r8Check
