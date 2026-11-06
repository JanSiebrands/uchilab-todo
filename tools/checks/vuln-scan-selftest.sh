#!/usr/bin/env bash
# Negative test for the vulnerability scan: a known vulnerable library must make it fail.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

cat > "$work/clean.lockfile" <<'LOCK'
# Gradle lockfile fixture (clean)
androidx.core:core-ktx:1.19.1=debugRuntimeClasspath
LOCK
cat > "$work/vulnerable.lockfile" <<'LOCK'
# Gradle lockfile fixture (log4j-core 2.14.1 is affected by Log4Shell, CVE-2021-44228)
org.apache.logging.log4j:log4j-core:2.14.1=debugRuntimeClasspath
LOCK
# The scanner picks the parser by file name, so each fixture lives in its own directory.
mkdir "$work/clean" "$work/vulnerable"
mv "$work/clean.lockfile" "$work/clean/gradle.lockfile"
mv "$work/vulnerable.lockfile" "$work/vulnerable/gradle.lockfile"

"$here/vuln-scan.sh" "$work/clean/gradle.lockfile" >/dev/null || { echo "FAIL: clean lockfile was reported" >&2; exit 1; }
if "$here/vuln-scan.sh" "$work/vulnerable/gradle.lockfile" >/dev/null 2>&1; then
  echo "FAIL: known vulnerable dependency was NOT detected" >&2
  exit 1
fi
echo "vuln-scan-selftest: OK"
