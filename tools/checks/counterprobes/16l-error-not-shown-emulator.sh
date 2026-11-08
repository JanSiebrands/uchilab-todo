#!/usr/bin/env bash
# S4b emulator check (plan UCH-80 section 5, Z1, Z6): toUiState turns TaskReadException into an empty list, so
# the empty state and the new task action show instead of the read failure notice.
# emulator-read-failure.sh must fail. Runs in the job emulator-read-failure only.
# Usage: 16l-error-not-shown-emulator.sh <seed-dir>
source "$(dirname "$0")/lib.sh"
seed="$(realpath "$1")"
make_copy
replace app/src/main/kotlin/de/uchilab/todo/ui/tasklist/TaskListViewModel.kt \
  "if (it is TaskReadException) emit(TaskListUiState.ReadFailed) else throw it" \
  "if (it is TaskReadException) emit(TaskListUiState.Loaded(emptyList())) else throw it"
expect_green "debug build without the read failure state" ./gradlew "${GRADLE_ARGS[@]}" assembleDebug
expect_red "read failure shown as empty list (emulator)" "case A first start: read failure notice not shown" \
  "$src/tools/checks/emulator-read-failure.sh" app/build/outputs/apk/debug/app-debug.apk "$seed"
