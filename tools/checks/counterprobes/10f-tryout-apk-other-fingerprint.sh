#!/usr/bin/env bash
# S2a: the APK to try out, compared against the fingerprint.txt of another key, must fail
# tryout-apk-check.sh. Shows that a restored key dir is really compared with the signature (AK 4).
# Needs no device, but the signed APK of the job tryout-apk; runs there with the other counterprobes 10.
source "$(dirname "$0")/lib.sh"
: "${TRYOUT_APK:?TRYOUT_APK is not set}"
min_sdk="$("$src/tools/ci/tryout-min-sdk.sh" "$src")"
tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
openssl rand -hex 32 >"$tmp/fingerprint.txt"
"$("$src/tools/ci/tryout-build-tools.sh")/apksigner" verify --print-certs "$TRYOUT_APK" \
  | grep -qF "certificate SHA-256 digest: $(cat "$tmp/fingerprint.txt")" \
  && { echo "COUNTERPROBE BROKEN: random fingerprint equals the certificate" >&2; exit 2; }
expect_green "APK without fingerprint comparison" "$src/tools/ci/tryout-apk-check.sh" "$TRYOUT_APK" "$min_sdk"
expect_red "fingerprint of another key" "differs from the key of this pull request" \
  "$src/tools/ci/tryout-apk-check.sh" "$TRYOUT_APK" "$min_sdk" "$tmp/fingerprint.txt"
