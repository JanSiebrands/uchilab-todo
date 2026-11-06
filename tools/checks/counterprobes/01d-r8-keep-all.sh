#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"
make_copy
# Point 1: R8 effectively switched off by a keep-all rule (no flag involved).
printf "\n-keep class ** { *; }\n" >> app/proguard-rules.pro
expect_red "R8 effectively off via -keep class ** { *; }" "R8 did not rename any class" \
  ./gradlew "${GRADLE_ARGS[@]}" assembleRelease r8Check
