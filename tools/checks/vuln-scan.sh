#!/usr/bin/env bash
# Vulnerability scan of the locked dependencies with osv-scanner in offline mode:
# the advisory database is downloaded, the dependency list never leaves the machine.
#
# Usage: vuln-scan.sh [lockfile ...]
#   With lockfiles: scans them as they are; any finding fails.
#   Without:        scans app/gradle.lockfile. Libraries that end up in the app (RuntimeClasspath,
#                   not unit test) fail the check. Test and build tool libraries are only reported.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
root="$(cd "$here/../.." && pwd)"
tools="$("$here/ensure-tools.sh")"
scan() { "$tools/osv-scanner" scan source --offline-vulnerabilities --download-offline-databases --lockfile "$1"; }

if [ "$#" -gt 0 ]; then
  status=0
  for lockfile in "$@"; do scan "$lockfile" || status=1; done
  exit "$status"
fi

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
mkdir "$work/shipped" "$work/other"
# Lockfile lines look like "group:name:version=config1,config2".
awk -F= '/^#/ || $1 == "" {next} $2 ~ /(^|,)(debug|release)RuntimeClasspath(,|$)/ {print $1 "=" $2}' \
  "$root/app/gradle.lockfile" > "$work/shipped/gradle.lockfile"
awk -F= '/^#/ || $1 == "" {next} $2 !~ /(^|,)(debug|release)RuntimeClasspath(,|$)/ {print $1 "=" $2}' \
  "$root/app/gradle.lockfile" > "$work/other/gradle.lockfile"

echo "== Shipped libraries (a finding fails the check) =="
status=0
scan "$work/shipped/gradle.lockfile" || status=1
echo "== Test and build tool libraries (informational only) =="
scan "$work/other/gradle.lockfile" || echo "Findings above are not shipped in the app; update when convenient."
exit "$status"
