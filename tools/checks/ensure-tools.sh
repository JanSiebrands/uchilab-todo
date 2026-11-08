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

# fetch <output> <owner/repo> <tag> <asset>: downloads a release asset. The direct download URL is tried first;
# if it fails (e.g. github.com answered HTTP 504 for hours on 2026-11-08 while the API was fine, UCH-90),
# the same asset is fetched through the GitHub API with gh. The pinned checksum is verified afterwards either way.
fetch() {
  curl -fsSL --retry 2 -o "$1" "https://github.com/$2/releases/download/$3/$4" && return 0
  echo "direct download of $4 failed, trying the GitHub API" >&2
  command -v gh >/dev/null || { echo "gh not available" >&2; return 1; }
  gh release download "$3" -R "$2" -p "$4" -O "$1" --clobber
}

if [ ! -x "$dir/gitleaks" ]; then
  archive="$dir/gitleaks.tar.gz"
  fetch "$archive" gitleaks/gitleaks "v${GITLEAKS_VERSION}" "gitleaks_${GITLEAKS_VERSION}_linux_x64.tar.gz"
  verify "$archive" "$GITLEAKS_SHA256" || { echo "gitleaks checksum mismatch" >&2; rm -f "$archive"; exit 1; }
  tar -xzf "$archive" -C "$dir" gitleaks
  rm -f "$archive"
fi
if [ ! -x "$dir/osv-scanner" ]; then
  fetch "$dir/osv-scanner" google/osv-scanner "v${OSV_VERSION}" osv-scanner_linux_amd64
  verify "$dir/osv-scanner" "$OSV_SHA256" || { echo "osv-scanner checksum mismatch" >&2; rm -f "$dir/osv-scanner"; exit 1; }
  chmod +x "$dir/osv-scanner"
fi
echo "$dir"
