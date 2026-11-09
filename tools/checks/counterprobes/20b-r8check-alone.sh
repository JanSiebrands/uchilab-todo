#!/usr/bin/env bash
# UCH-68: mapping.txt is written after R8 by the Compose mapping merge. r8Check depends on assembleRelease, so
# it is green when run alone on a fresh copy; depending on the R8 task only, it finds no mapping.txt.
source "$(dirname "$0")/lib.sh"
make_copy
expect_green "r8Check alone on a fresh copy" ./gradlew "${GRADLE_ARGS[@]}" r8Check
replace tools/checks/src/main/kotlin/de/uchilab/checks/PrivacyCheckPlugin.kt \
  "listOf(project.tasks.named(RELEASE_TASK))" "listOf(project.tasks.named(R8_TASK))"
rm -rf app/build/outputs/mapping
expect_red "r8Check depends on the R8 task only" "r8Check: R8 mapping.txt not found" \
  ./gradlew "${GRADLE_ARGS[@]}" r8Check
