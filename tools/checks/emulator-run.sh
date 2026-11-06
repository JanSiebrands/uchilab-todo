#!/usr/bin/env bash
# Runs inside the emulator job (point 7): debug and release (R8) start, then the counterprobe without
# launcher entry. The exit code decides.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
apks="$here/../../app/build/outputs/apk"
"$here/app-start.sh" "$apks/debug/app-debug.apk"
"$here/app-start.sh" "$apks/release/app-release-unsigned.apk"
"$here/counterprobes/07b-launcher-missing-emulator.sh"
