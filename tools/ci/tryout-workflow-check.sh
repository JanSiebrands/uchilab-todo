#!/usr/bin/env bash
# Checks the rules of the job tryout-apk in the CI workflow (ADR 0004). The exit code decides.
# Usage: tryout-workflow-check.sh [workflow-file]   (default .github/workflows/ci.yml; needs yq v4)
# - read-only token, no secrets anywhere in the workflow, no job-level permissions,
# - the job runs only on pull_request and needs ALL other jobs (read from the file),
# - concurrency per pull request without cancelling,
# - no continue-on-error, no step before the upload has an `if`,
# - the upload comes after the emulator step, which tests exactly the uploaded path,
# - upload with archive: false, retention-days >= 14, if-no-files-found: error, no `if`,
# - exactly one actions/cache step before tools/ci/tryout-prepare.sh, key exactly
#   tryout-signing-pr-<pull request number>, no restore-keys, its path is the key dir of the prepare step,
# - after the upload only the summary step (tools/ci/tryout-summary.sh).
set -euo pipefail
wf="${1:-.github/workflows/ci.yml}"
job=tryout-apk
command -v yq >/dev/null || { echo "tryout-workflow-check: yq (v4) is missing" >&2; exit 1; }
errors=0
fail() { echo "tryout-workflow-check: FAILED: $*" >&2; errors=$((errors + 1)); }
q() { yq -r "$1" "$wf"; }

[ "$(q ".jobs | has(\"$job\")")" = true ] || { fail "job $job missing"; exit 1; }
[ "$(q '.permissions | to_entries | map(.key + "=" + .value) | join(",")')" = "contents=read" ] \
  || fail "workflow permissions must be exactly contents: read"
[ "$(q ".jobs.\"$job\" | has(\"permissions\")")" = false ] || fail "$job must not set its own permissions"
if grep -nE 'secrets[.[]' "$wf" >&2; then fail "secrets are used in the workflow"; fi

[ "$(q ".jobs.\"$job\".if")" = "github.event_name == 'pull_request'" ] \
  || fail "$job must have if: github.event_name == 'pull_request'"
# Exact names: contains() would match substrings ("emulator" inside "emulator-read-failure").
missing="$(yq -r ".jobs.\"$job\".needs as \$n | .jobs | keys | .[] | select(. != \"$job\") | select(. as \$k | (\$n // []) | any_c(. == \$k) | not)" "$wf")"
[ -z "$missing" ] || fail "$job does not need: $(tr '\n' ' ' <<<"$missing")"
[ "$(q ".jobs.\"$job\".concurrency.group")" = 'tryout-apk-${{ github.event.pull_request.number }}' ] \
  || fail "$job concurrency group must be tryout-apk-<pull request number>"
[ "$(q ".jobs.\"$job\".concurrency.cancel-in-progress")" = false ] || fail "$job must not cancel running jobs"

steps=".jobs.\"$job\".steps"
[ "$(q "[.jobs.\"$job\", $steps[]] | map(select(has(\"continue-on-error\"))) | length")" = 0 ] \
  || fail "continue-on-error in $job"
upload="$(q "$steps | to_entries | map(select(.value.uses // \"\" | test(\"^actions/upload-artifact@\"))) | map(.key) | join(\",\")")"
emulator="$(q "$steps | to_entries | map(select(.value.uses // \"\" | test(\"^reactivecircus/android-emulator-runner@\"))) | map(.key) | join(\",\")")"
if ! [[ "$upload" =~ ^[0-9]+$ ]] || ! [[ "$emulator" =~ ^[0-9]+$ ]]; then
  fail "$job needs exactly one upload step and one emulator step (upload: '$upload', emulator: '$emulator')"
  exit 1
fi
[ "$upload" -gt "$emulator" ] || fail "upload (step $upload) must come after the emulator test (step $emulator)"
[ "$(q "$steps[:$upload] | map(select(has(\"if\"))) | length")" = 0 ] || fail "steps before the upload must not have an if"
[ "$(q "$steps[$upload] | has(\"if\")")" = false ] || fail "the upload step must not have an if"
path="$(q "$steps[$upload].with.path")"
case "$(q "$steps[$emulator].with.script")" in "tools/ci/tryout-emulator-run.sh \"$path\" "*) ;; *)
  fail "the emulator step does not test the uploaded path $path" ;; esac
[ "$(q "$steps[$upload].with.archive")" = false ] || fail "upload needs archive: false"
retention="$(q "$steps[$upload].with.retention-days")"
[[ "$retention" =~ ^[0-9]+$ ]] && [ "$retention" -ge 14 ] || fail "retention-days must be >= 14 (got: $retention)"
[ "$(q "$steps[$upload].with.if-no-files-found")" = error ] || fail "upload needs if-no-files-found: error"

# Signing key of the pull request (AK 4): the same key in every run of the pull request.
cache="$(q "$steps | to_entries | map(select(.value.uses // \"\" | test(\"^actions/cache@\"))) | map(.key) | join(\",\")")"
prepare="$(q "$steps | to_entries | map(select(.value.run // \"\" | test(\"^tools/ci/tryout-prepare.sh \"))) | map(.key) | join(\",\")")"
if ! [[ "$cache" =~ ^[0-9]+$ ]] || ! [[ "$prepare" =~ ^[0-9]+$ ]]; then
  fail "$job needs exactly one actions/cache step and one tryout-prepare.sh step (cache: '$cache', prepare: '$prepare')"
  exit 1
fi
[ "$cache" -lt "$prepare" ] || fail "the cache step (step $cache) must come before tryout-prepare.sh (step $prepare)"
[ "$(q "$steps[$cache].with.key")" = 'tryout-signing-pr-${{ github.event.pull_request.number }}' ] \
  || fail "cache key must be exactly tryout-signing-pr-<pull request number>"
[ "$(q "$steps[$cache].with | has(\"restore-keys\")")" = false ] || fail "the cache step must not have restore-keys"
keydir="$(q "$steps[$cache].with.path")"
[[ "$keydir" =~ ^'${{ runner.temp }}/'[A-Za-z0-9._-]+$ ]] || fail "cache path must be \${{ runner.temp }}/<dir> (got: $keydir)"
case "$(q "$steps[$prepare].run")" in "tools/ci/tryout-prepare.sh \"\$RUNNER_TEMP/${keydir#*\}\}/}\" "*) ;; *)
  fail "tryout-prepare.sh does not use the cached key dir $keydir" ;; esac
after="$(q "$steps[$((upload + 1)):] | map(.run // \"<not run>\") | join(\"|\")")"
[ -z "$after" ] || [ "$after" = tools/ci/tryout-summary.sh ] \
  || fail "after the upload only tools/ci/tryout-summary.sh may run (got: $after)"

[ "$errors" -eq 0 ] || exit 1
echo "tryout-workflow-check: OK ($wf)"
