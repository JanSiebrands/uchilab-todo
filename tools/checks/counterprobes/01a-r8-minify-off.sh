#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"
make_copy
# Point 1: R8 switched off (isMinifyEnabled = false). Resource shrinking needs R8, so it goes off too;
# otherwise AGP itself fails for another reason and the counterprobe would prove nothing about r8Check.
replace app/build.gradle.kts "isMinifyEnabled = true" "isMinifyEnabled = false"
replace app/build.gradle.kts "isShrinkResources = true" "isShrinkResources = false"
expect_red "R8 off via isMinifyEnabled = false" "R8 is not active in the release build" \
  ./gradlew "${GRADLE_ARGS[@]}" assembleRelease r8Check
