#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"
make_copy
# Point 3: a newer version of a dependency exists (activity-compose 1.12.0 while 1.13.0 is published).
# Lock state and checksums are rewritten in the copy, so the build itself is consistent; lint must stay green.
# The androidx.activity atomic group keeps the resolved version at 1.13.0; lint's version checks look at
# the declared 1.12.0 in the catalog, which is exactly the case of Jan's review.
replace gradle/libs.versions.toml "activityCompose = \"1.13.0\"" "activityCompose = \"1.12.0\""
expect_green "rewrite lock state for 1.12.0" ./gradlew "${GRADLE_ARGS[@]}" --dependency-verification lenient :app:dependencies --write-locks
expect_green "rewrite checksums for 1.12.0" ./gradlew "${GRADLE_ARGS[@]}" --write-verification-metadata sha256 lint
expect_green "lint with an older dependency version" ./gradlew "${GRADLE_ARGS[@]}" lint
