#!/usr/bin/env bash
# S2a: the unsigned release APK must fail the install test (the test never signs on its own). Runs in the
# job tryout-apk only, after its release build.
source "$(dirname "$0")/lib.sh"
unsigned="$root/app/build/outputs/apk/release/app-release-unsigned.apk"
[ -f "$unsigned" ] || { echo "COUNTERPROBE BROKEN: $unsigned missing" >&2; exit 2; }
"$("$src/tools/ci/tryout-build-tools.sh")/apksigner" verify "$unsigned" >/dev/null 2>&1 \
  && { echo "COUNTERPROBE BROKEN: $unsigned is signed" >&2; exit 2; }
expect_red "unsigned release APK" "tryout-install-test: FAILED: install failed" \
  "$src/tools/ci/tryout-install-test.sh" "$unsigned" "$("$src/tools/ci/tryout-min-sdk.sh" "$src")"
