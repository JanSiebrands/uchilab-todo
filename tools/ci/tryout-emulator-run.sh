#!/usr/bin/env bash
# Runs inside the emulator step of the job tryout-apk (API = minSdk): the install and start test of the file
# that is uploaded afterwards, then the counterprobes 15a-tryout to 15f-tryout (not the stored-field probes
# 15a to 15f). The exit code decides.
# Usage: tryout-emulator-run.sh <apk> <minSdk>
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
"$here/tryout-install-test.sh" "$1" "$2"
for probe in "$here"/../checks/counterprobes/15[a-f]-tryout-*.sh; do
  echo "::group::$(basename "$probe")"
  TRYOUT_APK="$1" "$probe"
  echo "::endgroup::"
done
