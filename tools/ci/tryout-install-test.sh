#!/usr/bin/env bash
# Install and start test of the APK to try out, on a running emulator (ADR 0004). The exit code decides.
# Usage: tryout-install-test.sh <apk> <minSdk> [<previous.apk>]
# 1. The device runs exactly <minSdk> (read from the build), the lowest supported Android version.
# 2. Fresh install of <previous.apk> (default: <apk> itself) must succeed.
# 3. Update with `adb install -r <apk>` must succeed (same signature, data stays).
# 4. Start check shared with app-start.sh (tools/checks/app-start-lib.sh).
# Unlike app-start.sh this script never signs: an unsigned or broken APK must turn it red.
# <previous.apk> is only set by counterprobe 10c (update signed with another key).
set -euo pipefail
apk="$1" min_sdk="$2" previous="${3:-$1}"
package="de.uchilab.todo"
source "$(dirname "$0")/../checks/app-start-lib.sh"
fail() { echo "tryout-install-test: FAILED: $*" >&2; exit 1; }
trap 'adb uninstall "$package" >/dev/null 2>&1 || true' EXIT

device_sdk="$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
[ "$device_sdk" = "$min_sdk" ] || fail "device runs API $device_sdk, minSdk is $min_sdk (test must run on minSdk)"

adb uninstall "$package" >/dev/null 2>&1 || true
out="$(adb install "$previous" 2>&1 | tr -d '\r')" && grep -qx 'Success' <<<"$out" \
  || fail "install failed: $(tail -n 2 <<<"$out")"
echo "tryout-install-test: install OK ($(basename "$previous"))"
out="$(adb install -r "$apk" 2>&1 | tr -d '\r')" && grep -qx 'Success' <<<"$out" \
  || fail "update failed (install -r): $(tail -n 2 <<<"$out")"
echo "tryout-install-test: update OK (install -r $(basename "$apk"))"
check_app_start "$package" "$(basename "$apk") on API $device_sdk"
