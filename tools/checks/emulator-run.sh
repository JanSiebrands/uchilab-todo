#!/usr/bin/env bash
# Runs inside the emulator job (point 7): debug and release (R8) start, then the counterprobe without
# launcher entry. Then the S3 check (plan UCH-58 section 7): seeded tasks shown after start, process end and
# reboot, debug and release, with its counterprobes; extended for S4 (plan UCH-63 section 6): task created
# through the UI, draft after process kill, done task at its place, with counterprobes 16e-16h. The screenshots
# for the feature PR come from emulator-screenshots.sh in a job of its own (aosp_atd renders no picture).
# Each step is limited to 10 minutes. The exit code decides.
# Expects the seed in app/build/emulator-seed (EmulatorSeedTest, see ci.yml).
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
# Full logcat for the reviewers, also after a red run; uploaded by the emulator job (UCH-69). The emulator is
# fresh and holds no personal data. timeout: a hanging adb must not hold the job (UCH-62).
logs="$here/../../app/build/emulator-artifacts"
save_logcat() { mkdir -p "$logs"; timeout 60 adb logcat -d -b all >"$logs/logcat.txt" 2>&1 || true; }
trap save_logcat EXIT
export EMULATOR_LOGS="$logs"
apks="$here/../../app/build/outputs/apk"
seed="$here/../../app/build/emulator-seed"
step() { echo "::group::$*"; timeout 600 "$@"; echo "::endgroup::"; }
step "$here/app-start.sh" "$apks/debug/app-debug.apk"
step "$here/app-start.sh" "$apks/release/app-release-unsigned.apk"
step "$here/counterprobes/07b-launcher-missing-emulator.sh"
step "$here/emulator-tasks.sh" "$apks/debug/app-debug.apk" "$seed"
step "$here/emulator-tasks.sh" "$apks/release/app-release-unsigned.apk" "$seed"
step "$here/counterprobes/16a-seed-missing-emulator.sh" "$apks/debug/app-debug.apk" "$seed"
step "$here/counterprobes/16b-deleted-after-reboot-emulator.sh" "$apks/release/app-release-unsigned.apk" "$seed"
step "$here/counterprobes/16c-order-swapped-emulator.sh" "$apks/debug/app-debug.apk" "$seed"
step "$here/counterprobes/16d-r8-breaks-room-emulator.sh" "$seed"
step "$here/counterprobes/16e-pidof-hangs-emulator.sh" "$apks/debug/app-debug.apk" "$seed"
step "$here/counterprobes/16e-ui-task-lost-emulator.sh" "$apks/release/app-release-unsigned.apk" "$seed"
step "$here/counterprobes/16f-save-not-tapped-emulator.sh" "$apks/debug/app-debug.apk" "$seed"
step "$here/counterprobes/16g-force-stop-instead-of-kill-emulator.sh" "$apks/debug/app-debug.apk" "$seed"
step "$here/counterprobes/16h-done-task-hidden-emulator.sh" "$seed"
echo "emulator-run: all emulator checks and counterprobes OK"
