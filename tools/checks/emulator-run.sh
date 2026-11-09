#!/usr/bin/env bash
# Runs inside the emulator job (plan UCH-125 section 4): debug and release (R8) start (app-start.sh), the tasks
# check (emulator-tasks.sh: seeded tasks after start, process end and reboot, task created through the UI) and
# the read failure check (emulator-read-failure.sh: a damaged database is never deleted), each debug and
# release. Only with RUN_COUNTERPROBE=true (path filter, tools/ci/pipeline-changed.sh) counterprobe 16b.
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
step "$here/emulator-tasks.sh" "$apks/debug/app-debug.apk" "$seed"
step "$here/emulator-tasks.sh" "$apks/release/app-release-unsigned.apk" "$seed"
step "$here/emulator-read-failure.sh" "$apks/debug/app-debug.apk" "$seed"
step "$here/emulator-read-failure.sh" "$apks/release/app-release-unsigned.apk" "$seed"
if [ "${RUN_COUNTERPROBE:-false}" = true ]; then
  step "$here/counterprobes/16b-deleted-after-reboot-emulator.sh" "$apks/release/app-release-unsigned.apk" "$seed"
else
  echo "emulator-run: counterprobe 16b skipped (no change to tools/, .github/ or the Gradle configuration)"
fi
echo "emulator-run: all emulator checks OK"
