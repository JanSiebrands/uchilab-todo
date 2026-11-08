#!/usr/bin/env bash
# S4 emulator check (plan UCH-63 section 6, counterprobe 16h): the list only shows open tasks
# (WHERE status = 'open' in TaskDao.observeAll; 'open' is the stored value of TaskStatus.OPEN); the debug build
# must fail emulator-tasks.sh with "done task not at its place". Runs in the emulator job only.
# Usage: 16h-done-task-hidden-emulator.sh <seed-dir>
source "$(dirname "$0")/lib.sh"
seed="$(realpath "$1")"
make_copy
replace app/src/main/kotlin/de/uchilab/todo/data/TaskDao.kt '@Query("SELECT * FROM task ORDER BY id DESC")' \
  "@Query(\"SELECT * FROM task WHERE status = 'open' ORDER BY id DESC\")"
expect_green "debug build with only open tasks in the list" ./gradlew "${GRADLE_ARGS[@]}" assembleDebug
expect_red "done task hidden (emulator)" "done task not at its place" \
  "$src/tools/checks/emulator-tasks.sh" app/build/outputs/apk/debug/app-debug.apk "$seed"
