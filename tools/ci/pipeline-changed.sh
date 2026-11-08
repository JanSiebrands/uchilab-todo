#!/usr/bin/env bash
# Path filter of the counterprobe scripts (plan UCH-125 section 4): writes changed=true|false to $GITHUB_OUTPUT.
# true if the diff touches tools/, .github/ or the Gradle configuration. A filter, not a check: decision and file
# list go to the log, the reviewers read them. Needs the checkout with fetch-depth 0.
set -euo pipefail
: "${GITHUB_OUTPUT:?GITHUB_OUTPUT is not set}"
case "${GITHUB_EVENT_NAME:-}" in
  pull_request) range="$BASE_SHA...$HEAD_SHA" ;;
  push) [[ "${BEFORE_SHA:-}" =~ ^0*$ ]] && range="" || range="$BEFORE_SHA..$HEAD_SHA" ;;
  schedule) echo "pipeline-changed: schedule, changed=false"; echo "changed=false" >>"$GITHUB_OUTPUT"; exit 0 ;;
  *) range="" ;;
esac
if [ -z "$range" ]; then
  echo "pipeline-changed: ${GITHUB_EVENT_NAME:-unknown} without a diff range, changed=true"
  echo "changed=true" >>"$GITHUB_OUTPUT"
  exit 0
fi
files="$(git diff --name-only "$range" -- tools .github '*.gradle.kts' gradle.properties gradle 'gradlew*')"
changed="$([ -n "$files" ] && echo true || echo false)"
echo "pipeline-changed: $range, changed=$changed"
[ -z "$files" ] || printf '  %s\n' $files
echo "changed=$changed" >>"$GITHUB_OUTPUT"
