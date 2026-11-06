#!/usr/bin/env bash
# Secret scan with gitleaks (local, no account, no upload).
# Usage: secret-scan.sh [directory]   scans files in the directory (default: repository root)
#        secret-scan.sh --history     scans the full git history of the repository
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
root="$(cd "$here/../.." && pwd)"
tools="$("$here/ensure-tools.sh")"

if [ "${1:-}" = "--history" ]; then
  exec "$tools/gitleaks" git --no-banner --redact --config "$here/gitleaks.toml" "$root"
fi
exec "$tools/gitleaks" dir --no-banner --redact --config "$here/gitleaks.toml" "${1:-$root}"
