#!/usr/bin/env bash
# Splits app/gradle.lockfile for the vulnerability scan (ADR 0002): libraries that end up in the app
# (debug/releaseRuntimeClasspath) go to build/osv/shipped, test and build tool libraries to build/osv/other.
# The job vulnerabilities scans both with the official osv-scanner image (offline mode: the advisory database is
# downloaded, the dependency list never leaves the machine). Inside the workspace, the container sees only that.
set -euo pipefail
root="$(cd "$(dirname "$0")/../.." && pwd)"
out="$root/build/osv"
mkdir -p "$out/shipped" "$out/other"
# Lockfile lines look like "group:name:version=config1,config2".
awk -F= '/^#/ || $1 == "" {next} $2 ~ /(^|,)(debug|release)RuntimeClasspath(,|$)/ {print $1 "=" $2}' \
  "$root/app/gradle.lockfile" >"$out/shipped/gradle.lockfile"
awk -F= '/^#/ || $1 == "" {next} $2 !~ /(^|,)(debug|release)RuntimeClasspath(,|$)/ {print $1 "=" $2}' \
  "$root/app/gradle.lockfile" >"$out/other/gradle.lockfile"
echo "vuln-scan: $(wc -l <"$out/shipped/gradle.lockfile") shipped, $(wc -l <"$out/other/gradle.lockfile") other libraries"
