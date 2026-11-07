#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"
make_copy
# Security P4 (L2), DSGVO P2 (V5): println.
write_probe_source "println(\"x\")"
expect_red "println in app/src/main" "Probe.kt:6: println( is not allowed, rule \"println/print\"" \
  ./gradlew "${GRADLE_ARGS[@]}" privacyCheck
