#!/usr/bin/env bash
# Runs inside the job emulator-read-failure (UCH-86): the S4b check emulator-read-failure.sh for debug and
# release (R8), then its counterprobes 16k-16n. A job of its own, because the emulator job already takes about
# 21 minutes (run 42460364877) and these steps add two debug builds per counterprobe copy and six cases.
# Each step is limited to 10 minutes. The exit code decides.
# Expects the seed in app/build/emulator-seed (EmulatorSeedTest, see ci.yml).
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
# Full logcat for the reviewers, also after a red run (UCH-69). The emulator is fresh and holds no personal data.
logs="$here/../../app/build/emulator-artifacts"
save_logcat() { mkdir -p "$logs"; timeout 60 adb logcat -d -b all >"$logs/logcat.txt" 2>&1 || true; }
trap save_logcat EXIT
export EMULATOR_LOGS="$logs"
apks="$here/../../app/build/outputs/apk"
seed="$here/../../app/build/emulator-seed"
step() { echo "::group::$*"; timeout 600 "$@"; echo "::endgroup::"; }
step "$here/emulator-read-failure.sh" "$apks/debug/app-debug.apk" "$seed"
step "$here/emulator-read-failure.sh" "$apks/release/app-release-unsigned.apk" "$seed"
step "$here/counterprobes/16k-driver-deletes-emulator.sh" "$seed"
step "$here/counterprobes/16l-error-not-shown-emulator.sh" "$seed"
step "$here/counterprobes/16m-hash-not-compared-emulator.sh" "$apks/debug/app-debug.apk" "$seed"
step "$here/counterprobes/16n-crash-on-read-failure-emulator.sh" "$seed"
echo "emulator-read-failure-run: all read failure checks and counterprobes OK"
