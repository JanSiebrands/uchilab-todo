#!/usr/bin/env bash
# S4b (plan UCH-80 section 5, Z1-Z3, Z7, Z15): TaskDatabase.create uses the standard driver again, which hands a
# damaged file to Android's deleting error handler; CorruptDatabaseTest must fail. Robolectric shows the
# deletion (reported in UCH-85: 4 of 8 tests red), so this counterprobe stays next to 16k on the emulator.
source "$(dirname "$0")/lib.sh"
make_copy
f=app/src/main/kotlin/de/uchilab/todo/data/TaskDatabase.kt
replace "$f" ".setDriver(KeepFileSQLiteDriver())" ".setDriver(androidx.sqlite.driver.AndroidSQLiteDriver())"
expect_red "standard driver deletes a damaged database" \
  "CorruptDatabaseTest > a file that is not a database gives a read failure and stays byte-identical, also after reopening FAILED" \
  ./gradlew "${GRADLE_ARGS[@]}" :app:testDebugUnitTest --tests '*CorruptDatabase*'
