#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"
make_copy
# Security P6 (D2), DSGVO P3 (V1): external storage.
write_probe_source "context.getExternalFilesDir(null)"
expect_red "getExternalFilesDir in app/src/main" "Probe.kt:6: getExternalFilesDir is not allowed" \
  ./gradlew "${GRADLE_ARGS[@]}" privacyCheck
