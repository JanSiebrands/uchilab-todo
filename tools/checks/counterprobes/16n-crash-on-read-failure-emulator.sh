#!/usr/bin/env bash
# S4b emulator check (plan UCH-80 section 5 and 7, Z12): toUiState throws TaskReadException on instead of
# showing the read failure, so the app crashes on every start. emulator-read-failure.sh must fail with "crash".
# Runs in the job emulator-read-failure only. Usage: 16n-crash-on-read-failure-emulator.sh <seed-dir>
source "$(dirname "$0")/lib.sh"
seed="$(realpath "$1")"
make_copy
replace app/src/main/kotlin/de/uchilab/todo/ui/tasklist/TaskListViewModel.kt \
  "if (it is TaskReadException) emit(TaskListUiState.ReadFailed) else throw it" "throw it"
expect_green "debug build that rethrows the read failure" ./gradlew "${GRADLE_ARGS[@]}" assembleDebug
expect_red "crash on the read failure (emulator)" "case A first start: crash" \
  "$src/tools/checks/emulator-read-failure.sh" app/build/outputs/apk/debug/app-debug.apk "$seed"
