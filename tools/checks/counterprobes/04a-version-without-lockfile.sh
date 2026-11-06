#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"
make_copy
# Point 4: a version changes in the catalog, the lock state is not updated (broken update PR).
# Upgrade, because a lower requested version is silently overridden by the locked one. If the catalog
# moves past 1.13.0, replace() reports the counterprobe as broken instead of passing silently.
replace gradle/libs.versions.toml "activityCompose = \"1.13.0\"" "activityCompose = \"1.14.0-alpha01\""
expect_red "version change without lock state" "enforced by Dependency Locking" ./gradlew "${GRADLE_ARGS[@]}" assembleDebug
