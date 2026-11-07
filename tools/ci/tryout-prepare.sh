#!/usr/bin/env bash
# Job tryout-apk: names, signs and checks the APK to try out (ADR 0004). Runs after
# `./gradlew assembleRelease r8Check` and the cache restore of the signing key.
# Needs PR_NUMBER and HEAD_SHA (github.event.pull_request.number / .head.sha, not github.sha, which is the
# merge commit). Usage: tryout-prepare.sh <key-dir> <out-dir>
# Writes path, name, sha256, fingerprint and key (new|reused) to $GITHUB_OUTPUT (or stdout locally).
set -euo pipefail
keydir="$1" outdir="$2"
here="$(dirname "$0")"
[[ "${PR_NUMBER:-}" =~ ^[1-9][0-9]*$ ]] || { echo "tryout-prepare: PR_NUMBER is empty or invalid: '${PR_NUMBER:-}'" >&2; exit 1; }
[[ "${HEAD_SHA:-}" =~ ^[0-9a-f]{40}$ ]] || { echo "tryout-prepare: HEAD_SHA is empty or invalid: '${HEAD_SHA:-}'" >&2; exit 1; }
name="uchilab-todo-pr${PR_NUMBER}-${HEAD_SHA:0:7}.apk"
min_sdk="$("$here/tryout-min-sdk.sh")"
# Mask the password of a restored key before anything else runs (a new key is masked right after signing).
[ -n "${GITHUB_ACTIONS:-}" ] && [ -f "$keydir/password" ] && echo "::add-mask::$(cat "$keydir/password")"
key="$("$here/tryout-sign.sh" app/build/outputs/apk/release/app-release-unsigned.apk "$keydir" "$outdir/$name")"
[ -n "${GITHUB_ACTIONS:-}" ] && echo "::add-mask::$(cat "$keydir/password")"
"$here/tryout-apk-check.sh" "$outdir/$name" "$min_sdk" "$keydir/fingerprint.txt"
{
  echo "path=$outdir/$name"
  echo "name=$name"
  echo "min-sdk=$min_sdk"
  echo "sha256=$(sha256sum "$outdir/$name" | cut -d' ' -f1)"
  echo "fingerprint=$(cat "$keydir/fingerprint.txt")"
  echo "key=$key"
} | tee -a "${GITHUB_OUTPUT:-/dev/null}"
