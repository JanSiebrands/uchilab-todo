#!/usr/bin/env bash
# Starts the app on a running emulator/device and fails unless the launcher activity comes up without a
# crash (point 7). Usage: app-start.sh <apk>
# The start check itself is in app-start-lib.sh (shared with tools/ci/tryout-install-test.sh).
# An unsigned APK (release build with R8) is signed with a throwaway key (apk-sign.sh). The app is
# uninstalled at the end.
set -euo pipefail
source "$(dirname "$0")/app-start-lib.sh"
source "$(dirname "$0")/apk-sign.sh"
apk="$1"
package="de.uchilab.todo"
tmp="$(mktemp -d)"
cleanup() { adb uninstall "$package" >/dev/null 2>&1 || true; rm -rf "$tmp"; }
trap cleanup EXIT

apk="$(sign_if_unsigned "$apk" "$tmp")"

adb uninstall "$package" >/dev/null 2>&1 || true
adb install "$apk" >/dev/null
check_app_start "$package" "$(basename "$1")"
