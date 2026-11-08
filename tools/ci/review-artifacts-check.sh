#!/usr/bin/env bash
# Checks that the CI workflow uploads what the reviewers read (UCH-66, UCH-69). The exit code decides.
# Usage: review-artifacts-check.sh [workflow-file]   (default .github/workflows/ci.yml; needs yq v4)
# - workflow env REVIEW_SHA is the pull request head, otherwise github.sha,
# - build job: the Gradle step has id gradle; tools/ci/review-artifacts.sh collects into $RUNNER_TEMP/review
#   after it with if: always(),
# - every expected artifact (table below) is uploaded exactly once by its job, after the collecting step,
#   with if: always(), the expected path, name <prefix>-${{ env.REVIEW_SHA }}, retention-days 14 and
#   if-no-files-found: error, without continue-on-error,
# - tools/checks/emulator-run.sh and emulator-read-failure-run.sh write the logs their emulator jobs upload.
set -euo pipefail
wf="${1:-.github/workflows/ci.yml}"
root="$(cd "$(dirname "$0")/../.." && pwd)"
command -v yq >/dev/null || { echo "review-artifacts-check: yq (v4) is missing" >&2; exit 1; }
errors=0
fail() { echo "review-artifacts-check: FAILED: $*" >&2; errors=$((errors + 1)); }
q() { yq -r "$1" "$wf"; }
upload='^actions/upload-artifact@'

# job | name prefix | path
expected=(
  'build|reports|${{ runner.temp }}/review/reports'
  'build|debug-apk|${{ runner.temp }}/review/apk'
  'build|r8-mapping|${{ runner.temp }}/review/r8'
  'emulator|emulator-logs|app/build/emulator-artifacts'
  'emulator-read-failure|read-failure-logs|app/build/emulator-artifacts'
  'screenshots|screenshots|app/build/emulator-artifacts'
)

[ "$(q '.env.REVIEW_SHA')" = '${{ github.event.pull_request.head.sha || github.sha }}' ] \
  || fail "workflow env REVIEW_SHA must be \${{ github.event.pull_request.head.sha || github.sha }}"

steps='.jobs.build.steps'
gradle="$(q "$steps | to_entries | map(select(.value.id == \"gradle\")) | map(.key) | join(\",\")")"
collect="$(q "$steps | to_entries | map(select(.value.run // \"\" | test(\"^tools/ci/review-artifacts.sh \"))) | map(.key) | join(\",\")")"
if ! [[ "$gradle" =~ ^[0-9]+$ ]] || ! [[ "$collect" =~ ^[0-9]+$ ]]; then
  fail "build needs exactly one step with id gradle and one tools/ci/review-artifacts.sh step (gradle: '$gradle', collect: '$collect')"
  exit 1
fi
[ "$collect" -gt "$gradle" ] || fail "tools/ci/review-artifacts.sh (step $collect) must come after the Gradle step (step $gradle)"
[ "$(q "$steps[$collect].if")" = "always()" ] || fail "the tools/ci/review-artifacts.sh step needs if: always()"
[ "$(q "$steps[$collect].run")" = 'tools/ci/review-artifacts.sh "$RUNNER_TEMP/review" "$GRADLE_OUTCOME"' ] \
  || fail "tools/ci/review-artifacts.sh must collect into \$RUNNER_TEMP/review with \$GRADLE_OUTCOME"
[ "$(q "$steps[$collect].env.GRADLE_OUTCOME")" = '${{ steps.gradle.outcome }}' ] \
  || fail "GRADLE_OUTCOME must be \${{ steps.gradle.outcome }}"

for entry in "${expected[@]}"; do
  IFS='|' read -r job prefix path <<<"$entry"
  s=".jobs.\"$job\".steps"
  idx="$(q "$s // [] | to_entries | map(select(.value.uses // \"\" | test(\"$upload\")) | select(.value.with.name // \"\" | test(\"^$prefix-\"))) | map(.key) | join(\",\")")"
  if ! [[ "$idx" =~ ^[0-9]+$ ]]; then
    fail "$job needs exactly one upload step for $prefix (got: '$idx')"
    continue
  fi
  with="$s[$idx].with"
  [ "$(q "$with.name")" = "$prefix-\${{ env.REVIEW_SHA }}" ] || fail "$prefix: name must be $prefix-\${{ env.REVIEW_SHA }}"
  [ "$(q "$with.path")" = "$path" ] || fail "$prefix: path must be $path (got: $(q "$with.path"))"
  [ "$(q "$with.retention-days")" = 14 ] || fail "$prefix: retention-days must be 14 (got: $(q "$with.retention-days"))"
  [ "$(q "$with.if-no-files-found")" = error ] || fail "$prefix: upload needs if-no-files-found: error"
  [ "$(q "$s[$idx].if")" = "always()" ] || fail "$prefix: upload needs if: always()"
  [ "$(q "$s[$idx] | has(\"continue-on-error\")")" = false ] || fail "$prefix: continue-on-error at the upload"
  [ "$job" != build ] || [ "$idx" -gt "$collect" ] || fail "$prefix: upload (step $idx) must come after tools/ci/review-artifacts.sh"
done

grep -qF 'logs="$here/../../app/build/emulator-artifacts"' "$root/tools/checks/emulator-run.sh" \
  || fail "tools/checks/emulator-run.sh does not write its logs to app/build/emulator-artifacts"
grep -qF 'logs="$here/../../app/build/emulator-artifacts"' "$root/tools/checks/emulator-read-failure-run.sh" \
  || fail "tools/checks/emulator-read-failure-run.sh does not write its logs to app/build/emulator-artifacts"

[ "$errors" -eq 0 ] || exit 1
echo "review-artifacts-check: OK ($wf)"
