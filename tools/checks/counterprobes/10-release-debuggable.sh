#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"
make_copy
# Security P1 (R1): the release build type is debuggable.
replace app/build.gradle.kts "        release {
" "        release {
            isDebuggable = true
"
expect_red "release build debuggable" "[release manifest] android:debuggable must not be \"true\" in the release build (Security R1)" \
  ./gradlew "${GRADLE_ARGS[@]}" privacyCheck
