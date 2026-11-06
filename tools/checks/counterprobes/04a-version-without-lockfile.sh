#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"
make_copy
# Point 4: a version changes, the lock state is not updated (broken update PR). Uses the test library
# probe-lib (lib.sh), so the counterprobe does not depend on the version of a real library.
# Upgrade, because a lower requested version is silently overridden by the locked one.
use_probe_lib
replace app/build.gradle.kts "de.uchilab.probe:probe-lib:1.0" "de.uchilab.probe:probe-lib:2.0"
expect_red "version change without lock state" "enforced by Dependency Locking" ./gradlew "${GRADLE_ARGS[@]}" assembleDebug
