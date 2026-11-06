#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"
make_copy
# Security P4 (L2): Room query log. The check is text based, so a comment is enough and the
# counterprobe needs no Room on main.
write_probe_source "// RoomDatabase.Builder.setQueryCallback(callback, executor)"
expect_red "setQueryCallback in app/src/main" "Probe.kt:6: setQueryCallback is not allowed" \
  ./gradlew "${GRADLE_ARGS[@]}" privacyCheck
