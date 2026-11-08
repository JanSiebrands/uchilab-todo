#!/usr/bin/env bash
# S2a: each rule of the job tryout-apk, broken one at a time in a copy of ci.yml, must fail
# tools/ci/tryout-workflow-check.sh with its own message.
source "$(dirname "$0")/lib.sh"
make_copy
check="$src/tools/ci/tryout-workflow-check.sh"
wf=.github/workflows/ci.yml
expect_green "unchanged workflow" "$check" "$wf"
variant() { cp "$wf" "$1"; echo "$1"; }

f="$(variant needs.yml)"; replace "$f" "needs: [build, counterprobes, emulator, emulator-read-failure, screenshots, secrets, vulnerabilities]" "needs: [build, counterprobes, emulator-read-failure, screenshots, secrets, vulnerabilities]"
expect_red "(i) job missing in needs" "does not need: emulator" "$check" "$f"
f="$(variant needs-screenshots.yml)"; replace "$f" "needs: [build, counterprobes, emulator, emulator-read-failure, screenshots, secrets, vulnerabilities]" "needs: [build, counterprobes, emulator, emulator-read-failure, secrets, vulnerabilities]"
expect_red "(i') screenshot job missing in needs" "does not need: screenshots" "$check" "$f"
f="$(variant needs-read-failure.yml)"; replace "$f" "needs: [build, counterprobes, emulator, emulator-read-failure, screenshots, secrets, vulnerabilities]" "needs: [build, counterprobes, emulator, screenshots, secrets, vulnerabilities]"
expect_red "(i'') read failure job missing in needs" "does not need: emulator-read-failure" "$check" "$f"
f="$(variant retention.yml)"; replace "$f" "retention-days: 30" "retention-days: 7"
expect_red "(ii) retention 7 days" "retention-days must be >= 14" "$check" "$f"
f="$(variant secret.yml)"; replace "$f" "      HEAD_SHA:" "      PROBE: \${{ secrets.X }}
      HEAD_SHA:"
expect_red "(iii) secret in the job" "secrets are used in the workflow" "$check" "$f"
f="$(variant if.yml)"; replace "$f" "    if: github.event_name == 'pull_request'
" ""
expect_red "(iv) if pull_request removed" "must have if: github.event_name == 'pull_request'" "$check" "$f"
# (v) upload step moved in front of the emulator step.
f="$(variant order.yml)"; yq -i '.jobs."tryout-apk".steps |= (map(select(.id == "upload")) + map(select(.id != "upload")))' "$f"
expect_red "(v) upload before the test" "must come after the emulator test" "$check" "$f"
# Further rules of the check.
f="$(variant archive.yml)"; replace "$f" "archive: false" "archive: true"
expect_red "(vi) archive true" "upload needs archive: false" "$check" "$f"
f="$(variant always.yml)"; replace "$f" "      - id: upload
" "      - id: upload
        if: always()
"
expect_red "(vii) upload also after a red test" "the upload step must not have an if" "$check" "$f"
f="$(variant after.yml)"; replace "$f" "      - run: tools/ci/tryout-summary.sh" "      - run: echo late
      - run: tools/ci/tryout-summary.sh"
expect_red "(viii) other step after the upload" "after the upload only tools/ci/tryout-summary.sh may run" "$check" "$f"
# Red test must not lead to an APK: continue-on-error or an if before the upload.
f="$(variant continue.yml)"; yq -i '(.jobs."tryout-apk".steps[] | select(.uses // "" | test("^reactivecircus/android-emulator-runner@")))."continue-on-error" = true' "$f"
expect_red "(ix) continue-on-error at the emulator step" "continue-on-error in tryout-apk" "$check" "$f"
f="$(variant job-continue.yml)"; replace "$f" "    runs-on: ubuntu-26.04
    # Two runs" "    runs-on: ubuntu-26.04
    continue-on-error: true
    # Two runs"
expect_red "(x) continue-on-error at the job" "continue-on-error in tryout-apk" "$check" "$f"
f="$(variant if-before.yml)"; yq -i '(.jobs."tryout-apk".steps[] | select(.id == "apk")).if = "always()"' "$f"
expect_red "(xi) if at a step before the upload" "steps before the upload must not have an if" "$check" "$f"
# Two runs of the same pull request must wait, not run in parallel (Plan 1).
f="$(variant group.yml)"; replace "$f" "group: tryout-apk-\${{ github.event.pull_request.number }}" "group: tryout-apk-\${{ github.sha }}"
expect_red "(xii) concurrency per commit" "concurrency group must be tryout-apk-<pull request number>" "$check" "$f"
f="$(variant cancel.yml)"; replace "$f" "cancel-in-progress: false" "cancel-in-progress: true"
expect_red "(xiii) cancel-in-progress true" "must not cancel running jobs" "$check" "$f"
f="$(variant no-files.yml)"; replace "$f" "if-no-files-found: error" "if-no-files-found: warn"
expect_red "(xiv) if-no-files-found warn" "upload needs if-no-files-found: error" "$check" "$f"
# The emulator tests exactly the uploaded file (Plan 4).
f="$(variant other-path.yml)"; replace "$f" 'tryout-emulator-run.sh "${{ steps.apk.outputs.path }}"' 'tryout-emulator-run.sh "${{ steps.apk.outputs.path }}.bak"'
expect_red "(xv) emulator tests another file" "the emulator step does not test the uploaded path" "$check" "$f"
f="$(variant two-uploads.yml)"; yq -i '.jobs."tryout-apk".steps += [.jobs."tryout-apk".steps[] | select(.id == "upload")]' "$f"
expect_red "(xvi) second upload step" "needs exactly one upload step and one emulator step" "$check" "$f"
# Read-only token.
f="$(variant permissions.yml)"; replace "$f" "permissions:
  contents: read
" "permissions:
  contents: write
"
expect_red "(xvii) workflow permissions write" "workflow permissions must be exactly contents: read" "$check" "$f"
f="$(variant job-permissions.yml)"; replace "$f" "    runs-on: ubuntu-26.04
    # Two runs" "    runs-on: ubuntu-26.04
    permissions:
      actions: write
    # Two runs"
expect_red "(xviii) job permissions" "tryout-apk must not set its own permissions" "$check" "$f"
# Signing key of the pull request (AK 4): one key for all runs of a pull request.
f="$(variant key-sha.yml)"; replace "$f" "key: tryout-signing-pr-\${{ github.event.pull_request.number }}" "key: tryout-signing-pr-\${{ github.event.pull_request.number }}-\${{ github.sha }}"
expect_red "(xix) cache key per commit" "cache key must be exactly tryout-signing-pr-<pull request number>" "$check" "$f"
f="$(variant restore-keys.yml)"; replace "$f" "          key: tryout-signing-pr-" "          restore-keys: tryout-signing-
          key: tryout-signing-pr-"
expect_red "(xx) restore-keys added" "the cache step must not have restore-keys" "$check" "$f"
f="$(variant no-cache.yml)"; yq -i 'del(.jobs."tryout-apk".steps[] | select(.uses // "" | test("^actions/cache@")))' "$f"
expect_red "(xxi) cache step removed" "needs exactly one actions/cache step" "$check" "$f"
f="$(variant cache-after.yml)"; yq -i '.jobs."tryout-apk".steps |= (map(select(.uses // "" | test("^actions/cache@") | not)) + map(select(.uses // "" | test("^actions/cache@"))))' "$f"
expect_red "(xxii) cache step after tryout-prepare.sh" "must come before tryout-prepare.sh" "$check" "$f"
f="$(variant cache-path.yml)"; replace "$f" "path: \${{ runner.temp }}/tryout-signing" "path: \${{ runner.temp }}/other-signing"
expect_red "(xxiii) cache path differs from the key dir" "tryout-prepare.sh does not use the cached key dir" "$check" "$f"
