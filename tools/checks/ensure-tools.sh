#!/usr/bin/env bash
# Downloads the pinned scanner binaries into .tools/ and verifies their SHA-256 checksums.
# Both tools run locally; they need no account or secret and send no project data anywhere.
# Output: prints the tool directory. To update a version, change the version and checksum here.
set -euo pipefail
root="$(cd "$(dirname "$0")/../.." && pwd)"
dir="$root/.tools"
mkdir -p "$dir"

GITLEAKS_VERSION="8.30.1"
GITLEAKS_SHA256="551f6fc83ea457d62a0d98237cbad105af8d557003051f41f3e7ca7b3f2470eb"
OSV_VERSION="2.7.0"
OSV_SHA256="160c6253e2922e9e0a6a9771c5c4c40a4b3c6090b34a3cdd5e80c96cc2e4218b"

verify() { echo "$2  $1" | sha256sum -c --status; }

if [ ! -x "$dir/gitleaks" ]; then
  archive="$dir/gitleaks.tar.gz"
  curl -fsSL -o "$archive" "https://github.com/gitleaks/gitleaks/releases/download/v${GITLEAKS_VERSION}/gitleaks_${GITLEAKS_VERSION}_linux_x64.tar.gz"
  verify "$archive" "$GITLEAKS_SHA256" || { echo "gitleaks checksum mismatch" >&2; rm -f "$archive"; exit 1; }
  tar -xzf "$archive" -C "$dir" gitleaks
  rm -f "$archive"
fi
if [ ! -x "$dir/osv-scanner" ]; then
  curl -fsSL -o "$dir/osv-scanner" "https://github.com/google/osv-scanner/releases/download/v${OSV_VERSION}/osv-scanner_linux_amd64"
  verify "$dir/osv-scanner" "$OSV_SHA256" || { echo "osv-scanner checksum mismatch" >&2; rm -f "$dir/osv-scanner"; exit 1; }
  chmod +x "$dir/osv-scanner"
fi
echo "$dir"
