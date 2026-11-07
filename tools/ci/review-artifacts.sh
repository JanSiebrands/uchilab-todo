#!/usr/bin/env bash
# Collects what the reviewers read instead of building themselves (UCH-66, UCH-69) into <out-dir>, one
# subdirectory per artifact of the build job: reports/ (unit tests of the app and of tools/checks as JUnit XML
# and HTML, lint as HTML, XML and SARIF), apk/ (debug APK), r8/ (mapping.txt of the release build).
# Usage: review-artifacts.sh <out-dir> <outcome of the Gradle step: success|failure|cancelled|skipped>
# Exit: 1 if the Gradle step was green and an expected file is missing (a renamed output must not go
# unnoticed). After a red Gradle step it copies what exists, lists what is missing and exits 0.
set -euo pipefail
out="${1:?usage: review-artifacts.sh <out-dir> <gradle-outcome>}"
outcome="${2:?usage: review-artifacts.sh <out-dir> <gradle-outcome>}"
root="$(cd "$(dirname "$0")/../.." && pwd)"
missing=()

# collect <target dir below out> <source below root>: a directory is copied with its content, a pattern
# must match at least one file.
collect() {
  local target="$out/$1" pattern="$2" found=0 path
  mkdir -p "$target"
  for path in "$root"/$pattern; do
    [ -e "$path" ] || continue
    cp -R "$path" "$target/"
    found=1
  done
  [ "$found" -eq 1 ] || missing+=("$pattern")
}

collect reports/app-unit-tests/junit-xml 'app/build/test-results/testDebugUnitTest/TEST-*.xml'
collect reports/app-unit-tests 'app/build/reports/tests/testDebugUnitTest'
collect reports/checks-tests/junit-xml 'tools/checks/build/test-results/test/TEST-*.xml'
collect reports/checks-tests 'tools/checks/build/reports/tests/test'
collect reports/lint 'app/build/reports/lint-results-debug.html'
collect reports/lint 'app/build/reports/lint-results-debug.xml'
collect reports/lint 'app/build/reports/lint-results-debug.sarif'
collect apk 'app/build/outputs/apk/debug/app-debug.apk'
collect r8 'app/build/outputs/mapping/release/mapping.txt'

[ "${#missing[@]}" -eq 0 ] && { echo "review-artifacts: all expected files collected in $out"; exit 0; }
if [ "$outcome" = success ]; then
  printf 'review-artifacts: FAILED: missing although the build was green: %s\n' "${missing[@]}" >&2
  exit 1
fi
printf 'review-artifacts: note: missing after a red build (%s): %s\n' "$outcome" "${missing[@]}"
