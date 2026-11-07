#!/usr/bin/env bash
# S2a: the same app signed with another throwaway key must not install over the APK to try out
# (install -r, INSTALL_FAILED_UPDATE_INCOMPATIBLE). Shows that the update step really checks the signature.
# Runs in the job tryout-apk only.
source "$(dirname "$0")/lib.sh"
: "${TRYOUT_APK:?TRYOUT_APK is not set}"
unsigned="$root/app/build/outputs/apk/release/app-release-unsigned.apk"
tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
"$src/tools/ci/tryout-sign.sh" "$unsigned" "$tmp/other-key" "$tmp/other-key.apk" >/dev/null
"$("$src/tools/ci/tryout-build-tools.sh")/apksigner" verify --print-certs "$TRYOUT_APK" \
  | grep -qF "certificate SHA-256 digest: $(cat "$tmp/other-key/fingerprint.txt")" \
  && { echo "COUNTERPROBE BROKEN: other key has the same certificate" >&2; exit 2; }
expect_red "update signed with another key" "tryout-install-test: FAILED: update failed" \
  "$src/tools/ci/tryout-install-test.sh" "$tmp/other-key.apk" "$("$src/tools/ci/tryout-min-sdk.sh" "$src")" "$TRYOUT_APK"
