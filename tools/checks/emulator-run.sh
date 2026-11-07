#!/usr/bin/env bash
# Runs inside the emulator job (point 7): debug and release (R8) start, then the counterprobe without
# launcher entry. The exit code decides.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
# Full logcat for the reviewers, also after a red run; uploaded by the emulator job (UCH-69). The emulator is
# fresh and holds no personal data.
logs="$here/../../app/build/emulator-artifacts"
save_logcat() { mkdir -p "$logs"; adb logcat -d -b all >"$logs/logcat.txt" 2>&1 || true; }
trap save_logcat EXIT
apks="$here/../../app/build/outputs/apk"
"$here/app-start.sh" "$apks/debug/app-debug.apk"
"$here/app-start.sh" "$apks/release/app-release-unsigned.apk"
"$here/counterprobes/07b-launcher-missing-emulator.sh"
