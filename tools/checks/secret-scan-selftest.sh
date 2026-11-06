#!/usr/bin/env bash
# Negative test for the secret scan: it must fail on a planted fake secret and pass on clean files.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

mkdir "$work/clean" "$work/dirty"
echo "val greeting = \"hello\"" > "$work/clean/Clean.kt"
# Fake key assembled at runtime so that no secret-like string is committed to the repository.
fake_key="AKIA$(printf 'QYLPMN5HHHFPZAM%s' 'Q')"
fake_secret="$(printf 'wJalrXUtnFEMI/K7MDENG/%s' 'bPxRfiCYzK3Y9qT4bL')"
printf 'aws_access_key_id = %s\naws_secret_access_key = %s\n' "$fake_key" "$fake_secret" > "$work/dirty/config.properties"

"$here/secret-scan.sh" "$work/clean" >/dev/null || { echo "FAIL: clean directory was reported" >&2; exit 1; }
if "$here/secret-scan.sh" "$work/dirty" >/dev/null 2>&1; then
  echo "FAIL: planted secret was NOT detected" >&2
  exit 1
fi
echo "secret-scan-selftest: OK"
