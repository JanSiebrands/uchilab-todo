#!/usr/bin/env bash
# Checks the APK to try out before it goes to the emulator (ADR 0004). The exit code decides.
# Usage: tryout-apk-check.sh <apk> <minSdk> [<fingerprint.txt>]
# Package de.uchilab.todo, minSdkVersion = <minSdk> (read from the build, tryout-min-sdk.sh), not debuggable,
# signature verifies with scheme v2, exactly one signer, and its certificate equals <fingerprint.txt> if given.
set -euo pipefail
apk="$1" min_sdk="$2" fp_file="${3:-}"
bt="$("$(dirname "$0")/tryout-build-tools.sh")"
fail() { echo "tryout-apk-check: FAILED: $*" >&2; exit 1; }

badging="$("$bt/aapt2" dump badging "$apk" 2>&1)" || fail "cannot read APK: $(head -n 3 <<<"$badging")"
grep -qE "^package: name='de\.uchilab\.todo'" <<<"$badging" || fail "package is not de.uchilab.todo"
apk_min="$(sed -nE "s/^(min)?[sS]dkVersion:'([0-9]+)'$/\2/p" <<<"$badging" | head -n 1)"
[ "$apk_min" = "$min_sdk" ] || fail "minSdkVersion of the APK is '$apk_min', build says $min_sdk"
if grep -q '^application-debuggable' <<<"$badging"; then fail "APK is debuggable (application-debuggable)"; fi

verify="$("$bt/apksigner" verify --verbose --print-certs "$apk" 2>&1)" || fail "signature does not verify"
grep -qF 'Verified using v2 scheme (APK Signature Scheme v2): true' <<<"$verify" || fail "no v2 signature"
[ "$(grep -c '^Number of signers: 1$' <<<"$verify")" -eq 1 ] || fail "expected exactly one signer"
cert="$(sed -nE 's/^Signer #1 certificate SHA-256 digest: ([0-9a-f]{64})$/\1/p' <<<"$verify")"
[ -n "$cert" ] || fail "no certificate digest"
if [ -n "$fp_file" ]; then
  [ "$cert" = "$(cat "$fp_file")" ] || fail "certificate $cert differs from the key of this pull request ($(cat "$fp_file"))"
fi
echo "tryout-apk-check: OK (de.uchilab.todo, minSdk $min_sdk, not debuggable, v2, certificate $cert)"
