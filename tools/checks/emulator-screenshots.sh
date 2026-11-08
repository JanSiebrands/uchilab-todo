#!/usr/bin/env bash
# Runs inside the screenshots job (UCH-65): the release check of emulator-tasks.sh with SCREENSHOTS set, so the
# feature PR gets screenshots of the list, the empty input, title missing, title too long and the discard dialog.
# Only invented data (seed from EmulatorSeedTest). This image has a keyboard app, so emulator-tasks.sh also
# checks that the keyboard opens with the input; counterprobe 16j shows that this check turns red.
# Each step is limited to 10 minutes; the exit code decides.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
logs="$here/../../app/build/emulator-artifacts"
save_logcat() { mkdir -p "$logs"; timeout 60 adb logcat -d -b all >"$logs/logcat.txt" 2>&1 || true; }
trap save_logcat EXIT
export EMULATOR_LOGS="$logs"
timeout 600 env SCREENSHOTS="$logs/screenshots" "$here/emulator-tasks.sh" \
  "$here/../../app/build/outputs/apk/release/app-release-unsigned.apk" "$here/../../app/build/emulator-seed"
timeout 600 "$here/counterprobes/16j-keyboard-closed-emulator.sh" \
  "$here/../../app/build/outputs/apk/release/app-release-unsigned.apk" "$here/../../app/build/emulator-seed"
echo "emulator-screenshots: OK"
