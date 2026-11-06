#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"
make_copy
# Point 4: version and lock state change, the checksums in verification-metadata.xml do not.
# Upgrade, because a lower version is pulled back up by the androidx.activity atomic group.
replace gradle/libs.versions.toml "activityCompose = \"1.13.0\"" "activityCompose = \"1.14.0-alpha01\""
expect_green "rewrite lock state for 1.14.0-alpha01" ./gradlew "${GRADLE_ARGS[@]}" --dependency-verification lenient :app:dependencies --write-locks
grep -q "^androidx.activity:activity-compose:1.14.0-alpha01=" app/gradle.lockfile \
  || { echo "COUNTERPROBE BROKEN: activity-compose 1.14.0-alpha01 is not in the lock state" >&2; exit 2; }
expect_red "version change without checksums" "Dependency verification failed" ./gradlew "${GRADLE_ARGS[@]}" assembleDebug
