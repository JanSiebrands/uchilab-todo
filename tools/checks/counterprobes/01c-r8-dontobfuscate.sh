#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"
make_copy
# Point 1: R8 partially switched off by a flag in the ProGuard rules.
printf "\n-dontobfuscate\n" >> app/proguard-rules.pro
expect_red "R8 obfuscation off via -dontobfuscate" "R8 is partially disabled by -dontobfuscate" \
  ./gradlew "${GRADLE_ARGS[@]}" assembleRelease r8Check
