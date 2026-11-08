#!/usr/bin/env bash
# S4b (plan UCH-80 section 5, Z8): the driver no longer treats an empty file as damaged; CorruptDatabaseTest
# must fail in the 0-byte test.
source "$(dirname "$0")/lib.sh"
make_copy
replace app/src/main/kotlin/de/uchilab/todo/data/KeepFileSQLiteDriver.kt \
  "        if (emptyFileFoundAtFirstOpen(fileName)) throw SQLiteDatabaseCorruptException()
" ""
expect_red "empty file not treated as damaged" \
  "CorruptDatabaseTest > an empty file counts as damaged, stays empty and fails again on a new instance FAILED" \
  ./gradlew "${GRADLE_ARGS[@]}" :app:testDebugUnitTest --tests '*CorruptDatabase*'
