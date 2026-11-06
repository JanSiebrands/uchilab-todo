#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"
make_copy
# Point 4: version and lock state change, the checksums in verification-metadata.xml do not. Uses the
# test library probe-lib (lib.sh), so the counterprobe does not depend on the version of a real library.
use_probe_lib
replace app/build.gradle.kts "de.uchilab.probe:probe-lib:1.0" "de.uchilab.probe:probe-lib:2.0"
expect_green "rewrite lock state for probe-lib 2.0" ./gradlew "${GRADLE_ARGS[@]}" --dependency-verification lenient :app:dependencies --write-locks
grep -q "^de.uchilab.probe:probe-lib:2.0=" app/gradle.lockfile \
  || { echo "COUNTERPROBE BROKEN: probe-lib 2.0 is not in the lock state" >&2; exit 2; }
expect_red "version change without checksums" "Dependency verification failed" ./gradlew "${GRADLE_ARGS[@]}" assembleDebug
