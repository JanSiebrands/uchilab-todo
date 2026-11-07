#!/usr/bin/env bash
# S2a: the signed APK to try out, cut to half its size, must fail the install test. Runs in the job
# tryout-apk only (needs the emulator and TRYOUT_APK, the file that is uploaded).
source "$(dirname "$0")/lib.sh"
: "${TRYOUT_APK:?TRYOUT_APK is not set}"
tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
head -c "$(( $(stat -c %s "$TRYOUT_APK") / 2 ))" "$TRYOUT_APK" >"$tmp/truncated.apk"
cmp -s "$TRYOUT_APK" "$tmp/truncated.apk" && { echo "COUNTERPROBE BROKEN: APK not truncated" >&2; exit 2; }
expect_red "truncated APK" "tryout-install-test: FAILED: install failed" \
  "$src/tools/ci/tryout-install-test.sh" "$tmp/truncated.apk" "$("$src/tools/ci/tryout-min-sdk.sh" "$src")"
