#!/usr/bin/env bash
# S4b (plan UCH-80 section 7, Z18, UCH-87): without the one @SuppressLint("RestrictedApi") at the driver's
# connection, lint must fail with RestrictedApi. Shows that lint checks the restricted API and that the
# suppression is the only thing keeping it green.
source "$(dirname "$0")/lib.sh"
make_copy
replace app/src/main/kotlin/de/uchilab/todo/data/KeepFileSQLiteDriver.kt '    @SuppressLint("RestrictedApi")
' ""
expect_red "RestrictedApi not suppressed" "RestrictedApi" ./gradlew "${GRADLE_ARGS[@]}" :app:lintDebug
