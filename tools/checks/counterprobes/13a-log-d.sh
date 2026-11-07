#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"
make_copy
# Security P4 (L2), DSGVO P2 (V5): logging with android.util.Log.
write_probe_source "android.util.Log.d(\"x\", \"y\")"
expect_red "Log.d in app/src/main" "[source] app/src/main/kotlin/de/uchilab/todo/Probe.kt:6: android.util.Log is not allowed" \
  ./gradlew "${GRADLE_ARGS[@]}" privacyCheck
