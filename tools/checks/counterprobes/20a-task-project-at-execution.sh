#!/usr/bin/env bash
# UCH-68: Gradle 10 forbids Task.project in a task action. The build job runs with --warning-mode fail, so a
# project access in the action of privacyCheck must turn the build red; the unchanged copy stays green.
source "$(dirname "$0")/lib.sh"
make_copy
grep -qE '^\s+run: \./gradlew --warning-mode fail ' .github/workflows/ci.yml \
  || { echo "COUNTERPROBE FAILED: the Gradle step of the build job does not run with --warning-mode fail" >&2; exit 1; }
expect_green "unchanged privacyCheck with --warning-mode fail" \
  ./gradlew "${GRADLE_ARGS[@]}" --warning-mode fail privacyCheck
replace tools/checks/src/main/kotlin/de/uchilab/checks/PrivacyCheckPlugin.kt \
  "        val violations = mutableListOf<String>()
        val allowedPermissions" \
  "        val violations = mutableListOf<String>()
        logger.info(project.name)
        val allowedPermissions"
expect_red "Task.project in the action of privacyCheck" "Invocation of Task.project at execution time has been deprecated" \
  ./gradlew "${GRADLE_ARGS[@]}" --warning-mode fail privacyCheck
