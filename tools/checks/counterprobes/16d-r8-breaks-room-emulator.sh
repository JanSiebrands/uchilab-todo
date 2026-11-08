#!/usr/bin/env bash
# S3 emulator check (plan UCH-58 section 7, counterprobe d): R8 ignores Room's keep rules, so the generated
# TaskDatabase_Impl is renamed or removed; the release build must fail emulator-tasks.sh.
# Runs in the emulator job only. Usage: 16d-r8-breaks-room-emulator.sh <seed-dir>
source "$(dirname "$0")/lib.sh"
seed="$(realpath "$1")"
make_copy
replace app/build.gradle.kts '            isShrinkResources = true
' '            isShrinkResources = true
            optimization { keepRules { ignoreFrom("androidx.room3:room3-runtime-android") } }
'
expect_green "release build without Room keep rules" ./gradlew "${GRADLE_ARGS[@]}" assembleRelease
mapping=app/build/outputs/mapping/release/mapping.txt
! grep -q '^de.uchilab.todo.data.TaskDatabase_Impl -> de.uchilab.todo.data.TaskDatabase_Impl:' "$mapping" \
  || { echo "COUNTERPROBE BROKEN: TaskDatabase_Impl still kept under its name" >&2; exit 2; }
expect_red "R8 breaks Room in release (emulator)" "after start:" \
  "$src/tools/checks/emulator-tasks.sh" app/build/outputs/apk/release/app-release-unsigned.apk "$seed"
