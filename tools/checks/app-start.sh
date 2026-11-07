#!/usr/bin/env bash
# Starts the app on a running emulator/device and fails unless the launcher activity comes up without a
# crash (point 7). Usage: app-start.sh <apk>
# The start check itself is in app-start-lib.sh (shared with tools/ci/tryout-install-test.sh).
# An unsigned APK (release build with R8) is signed in a temp dir with a freshly generated throwaway key;
# the release build type itself stays unchanged and no secret is involved. The app is uninstalled at the end.
set -euo pipefail
source "$(dirname "$0")/app-start-lib.sh"
apk="$1"
package="de.uchilab.todo"
tmp="$(mktemp -d)"
cleanup() { adb uninstall "$package" >/dev/null 2>&1 || true; rm -rf "$tmp"; }
trap cleanup EXIT

build_tools="$ANDROID_HOME/build-tools/$(ls "$ANDROID_HOME/build-tools" | sort -V | tail -n 1)"
if ! "$build_tools/apksigner" verify "$apk" >/dev/null 2>&1; then
  keytool -genkeypair -keystore "$tmp/throwaway.jks" -storepass throwaway -keypass throwaway -alias throwaway \
    -keyalg RSA -keysize 2048 -validity 1 -dname "CN=throwaway" >/dev/null 2>&1
  "$build_tools/zipalign" -f -p 4 "$apk" "$tmp/aligned.apk"
  "$build_tools/apksigner" sign --ks "$tmp/throwaway.jks" --ks-pass pass:throwaway --out "$tmp/signed.apk" "$tmp/aligned.apk"
  apk="$tmp/signed.apk"
  echo "app-start: unsigned APK signed with a throwaway key"
fi

adb uninstall "$package" >/dev/null 2>&1 || true
adb install "$apk" >/dev/null
check_app_start "$package" "$(basename "$1")"
