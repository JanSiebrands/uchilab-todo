#!/usr/bin/env bash
# S4b (plan UCH-80 section 7, Z19, UCH-87): the driver opens without ENABLE_WRITE_AHEAD_LOGGING, so Android
# switches the journal mode and writes the file header (byte 18); the byte comparison of CorruptDatabaseTest
# must fail.
source "$(dirname "$0")/lib.sh"
make_copy
replace app/src/main/kotlin/de/uchilab/todo/data/KeepFileSQLiteDriver.kt \
  "SQLiteDatabase.CREATE_IF_NECESSARY or SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING" "SQLiteDatabase.CREATE_IF_NECESSARY"
expect_red "WAL flag removed, header rewritten" \
  "CorruptDatabaseTest > a database that opens but cannot be read gives a read failure and stays byte-identical FAILED" \
  ./gradlew "${GRADLE_ARGS[@]}" :app:testDebugUnitTest --tests '*CorruptDatabase*'
