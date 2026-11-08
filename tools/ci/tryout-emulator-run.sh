#!/usr/bin/env bash
# Runs inside the emulator step of the job tryout-apk (API = minSdk): the install and start test of the file
# that is uploaded afterwards. The exit code decides.
# Usage: tryout-emulator-run.sh <apk> <minSdk>
set -euo pipefail
exec "$(cd "$(dirname "$0")" && pwd)/tryout-install-test.sh" "$1" "$2"
