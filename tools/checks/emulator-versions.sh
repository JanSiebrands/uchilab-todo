#!/usr/bin/env bash
# Writes the versions of emulator, system image and platform tools to the log at the start of the emulator
# job, so a red job without a code change can be traced to a changed image or tool (UCH-62).
# The system image revision cannot be pinned (sdkmanager always installs the latest), so it is logged.
set -euo pipefail
sdk="${ANDROID_HOME:?ANDROID_HOME is not set}"
revision() { grep -E '^Pkg.Revision=' "$1" | cut -d= -f2- || echo unknown; }
echo "emulator-versions: $("$sdk/emulator/emulator" -version | head -n 1)"
for props in "$sdk"/system-images/*/*/*/source.properties; do
  image="${props#"$sdk/system-images/"}"
  echo "emulator-versions: system image ${image%/source.properties} revision $(revision "$props")"
done
echo "emulator-versions: platform-tools revision $(revision "$sdk/platform-tools/source.properties")"
echo "emulator-versions: device $(adb shell getprop ro.build.fingerprint | tr -d '\r')"
