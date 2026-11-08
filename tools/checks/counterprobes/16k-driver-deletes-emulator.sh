#!/usr/bin/env bash
# S4b emulator check (plan UCH-80 section 5, Z1-Z3, Z15): TaskDatabase.create uses the standard driver again,
# which hands a damaged file to Android's deleting error handler. emulator-read-failure.sh must fail because the
# file changed or the empty state shows instead of the read failure notice.
# Runs in the job emulator-read-failure only. Usage: 16k-driver-deletes-emulator.sh <seed-dir>
source "$(dirname "$0")/lib.sh"
seed="$(realpath "$1")"
make_copy
replace app/src/main/kotlin/de/uchilab/todo/data/TaskDatabase.kt ".setDriver(KeepFileSQLiteDriver())" \
  ".setDriver(androidx.sqlite.driver.AndroidSQLiteDriver())"
expect_green "debug build with the standard driver" ./gradlew "${GRADLE_ARGS[@]}" assembleDebug
# Either message is a correct red result (plan section 5); any other reason does not count.
log="$(mktemp)"; status=0
"$src/tools/checks/emulator-read-failure.sh" app/build/outputs/apk/debug/app-debug.apk "$seed" >"$log" 2>&1 || status=$?
if [ "$status" -eq 0 ] || ! grep -qE "case A first start: read failure notice not shown|case A [a-z ]+: file changed" "$log"; then
  tail -n 40 "$log"
  echo "COUNTERPROBE FAILED: standard driver deletes the damaged file (emulator): exit $status, expected 'file changed' or 'read failure notice not shown' in case A" >&2
  exit 1
fi
echo "counterprobe OK: standard driver deletes the damaged file (emulator): red as expected (exit $status, $(grep -m1 -oE 'case A [^(]*' "$log"))"
