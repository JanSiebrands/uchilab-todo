#!/usr/bin/env bash
# Signs the unsigned release APK with the signing key of this pull request (ADR 0004).
# Usage: tryout-sign.sh <unsigned.apk> <key-dir> <out.apk>
# <key-dir> is restored by actions/cache (key tryout-signing-pr-<N>). If it is empty, a new key is generated
# there: RSA 2048 in a PKCS12 keystore with a random password. Prints "reused" or "new" on stdout.
# Keystore and password never appear in the log: tool output goes to /dev/null, the password is only passed
# as a file (tryout-prepare.sh also masks it in GitHub Actions). <key-dir> must be outside the checkout.
set -euo pipefail
unsigned="$1" keydir="$2" out="$3"
bt="$("$(dirname "$0")/tryout-build-tools.sh")"
ks="$keydir/keystore.p12" pw="$keydir/password" fp="$keydir/fingerprint.txt"
alias=tryout

fingerprint() {
  keytool -exportcert -keystore "$ks" -storetype PKCS12 -alias "$alias" -storepass:file "$pw" 2>/dev/null \
    | sha256sum | cut -d' ' -f1
}

case "$(realpath -m "$keydir")/" in "$(git rev-parse --show-toplevel 2>/dev/null || echo /nonexistent)"/*)
  echo "tryout-sign: key dir must be outside the checkout" >&2; exit 1 ;; esac

if [ -f "$ks" ] && [ -f "$pw" ] && [ -f "$fp" ]; then
  status=reused
elif [ -e "$ks" ] || [ -e "$pw" ] || [ -e "$fp" ]; then
  echo "tryout-sign: incomplete key dir $keydir (restored cache is broken)" >&2; exit 1
else
  status=new
  mkdir -p "$keydir"; chmod 700 "$keydir"
  (umask 077; openssl rand -hex 32 >"$pw")
  keytool -genkeypair -keystore "$ks" -storetype PKCS12 -alias "$alias" -storepass:file "$pw" -keypass:file "$pw" \
    -keyalg RSA -keysize 2048 -validity 3650 -dname "CN=uchilab-todo tryout" >/dev/null 2>&1 \
    || { echo "tryout-sign: key generation failed" >&2; exit 1; }
  fingerprint >"$fp"
fi
grep -qxE '[0-9a-f]{64}' "$fp" || { echo "tryout-sign: invalid fingerprint.txt" >&2; exit 1; }
[ "$(fingerprint)" = "$(cat "$fp")" ] || { echo "tryout-sign: keystore does not match fingerprint.txt" >&2; exit 1; }

tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
"$bt/zipalign" -f -p 4 "$unsigned" "$tmp/aligned.apk" >/dev/null
mkdir -p "$(dirname "$out")"
"$bt/apksigner" sign --ks "$ks" --ks-type PKCS12 --ks-key-alias "$alias" --ks-pass "file:$pw" \
  --out "$out" "$tmp/aligned.apk" >/dev/null 2>&1 || { echo "tryout-sign: apksigner sign failed" >&2; exit 1; }
rm -f "$out.idsig"
echo "$status"
