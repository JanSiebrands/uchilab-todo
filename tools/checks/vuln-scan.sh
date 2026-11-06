#!/usr/bin/env bash
# Vulnerability scan of the locked dependencies with osv-scanner in offline mode:
# the advisory database is downloaded, the dependency list never leaves the machine.
# Usage: vuln-scan.sh [lockfile ...]   (default: app/gradle.lockfile)
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
root="$(cd "$here/../.." && pwd)"
tools="$("$here/ensure-tools.sh")"

if [ "$#" -eq 0 ]; then set -- "$root/app/gradle.lockfile"; fi
args=()
for lockfile in "$@"; do args+=(--lockfile "$lockfile"); done
exec "$tools/osv-scanner" scan source --offline-vulnerabilities --download-offline-databases "${args[@]}"
