#!/usr/bin/env bash
# UCH-69: every rule of the reviewer artifacts, broken one at a time in the copy, must turn
# tools/ci/review-artifacts-check.sh (workflow) or tools/ci/review-artifacts.sh (expected files) red with its
# own message.
source "$(dirname "$0")/lib.sh"
make_copy
check=tools/ci/review-artifacts-check.sh
wf=.github/workflows/ci.yml
expect_green "unchanged workflow" "$check" "$wf"
variant() { cp "$wf" "$1"; echo "$1"; }
apk_upload='.uses // "" | test("^actions/upload-artifact@")) | select(.with.name == "debug-apk-${{ env.REVIEW_SHA }}"'

f="$(variant no-upload.yml)"; yq -i "del(.jobs.build.steps[] | select($apk_upload))" "$f"
expect_red "(a) upload step of the debug APK removed" "build needs exactly one upload step for debug-apk" "$check" "$f"
f="$(variant two-uploads.yml)"; yq -i ".jobs.build.steps += [.jobs.build.steps[] | select($apk_upload)]" "$f"
expect_red "(b) upload step of the debug APK twice" "build needs exactly one upload step for debug-apk" "$check" "$f"
f="$(variant path.yml)"; replace "$f" 'path: ${{ runner.temp }}/review/r8' 'path: ${{ runner.temp }}/review/mapping'
expect_red "(c) wrong path of the R8 mapping" "r8-mapping: path must be" "$check" "$f"
f="$(variant emu-path.yml)"; replace "$f" "path: app/build/emulator-artifacts" "path: app/build/emulator"
expect_red "(d) wrong path of the emulator logs" "emulator-logs: path must be" "$check" "$f"
f="$(variant no-emu.yml)"; yq -i 'del(.jobs.emulator.steps[] | select(.uses // "" | test("^actions/upload-artifact@")))' "$f"
expect_red "(e) upload step of the emulator logs removed" "emulator needs exactly one upload step for emulator-logs" "$check" "$f"
f="$(variant always.yml)"; yq -i "del(.jobs.build.steps[] | select($apk_upload) | .if)" "$f"
expect_red "(f) upload without if: always()" "debug-apk: upload needs if: always()" "$check" "$f"
f="$(variant retention.yml)"; yq -i "(.jobs.build.steps[] | select($apk_upload) | .with.retention-days) = 7" "$f"
expect_red "(g) retention 7 days" "debug-apk: retention-days must be 14" "$check" "$f"
f="$(variant name.yml)"; replace "$f" 'name: reports-${{ env.REVIEW_SHA }}' 'name: reports-${{ github.run_id }}'
expect_red "(h) name without commit SHA" 'reports: name must be reports-${{ env.REVIEW_SHA }}' "$check" "$f"
f="$(variant sha.yml)"; replace "$f" 'REVIEW_SHA: ${{ github.event.pull_request.head.sha || github.sha }}' 'REVIEW_SHA: ${{ github.run_id }}'
expect_red "(i) REVIEW_SHA not the commit" "workflow env REVIEW_SHA must be" "$check" "$f"
f="$(variant no-files.yml)"; yq -i "(.jobs.build.steps[] | select($apk_upload) | .with.if-no-files-found) = \"warn\"" "$f"
expect_red "(j) if-no-files-found warn" "debug-apk: upload needs if-no-files-found: error" "$check" "$f"
f="$(variant continue.yml)"; yq -i "(.jobs.build.steps[] | select($apk_upload) | .continue-on-error) = true" "$f"
expect_red "(k) continue-on-error at the upload" "debug-apk: continue-on-error at the upload" "$check" "$f"
f="$(variant collect-always.yml)"; yq -i 'del(.jobs.build.steps[] | select(.run // "" | test("^tools/ci/review-artifacts.sh ")) | .if)' "$f"
expect_red "(l) collecting step without if: always()" "the tools/ci/review-artifacts.sh step needs if: always()" "$check" "$f"
f="$(variant no-collect.yml)"; yq -i 'del(.jobs.build.steps[] | select(.run // "" | test("^tools/ci/review-artifacts.sh ")))' "$f"
expect_red "(m) collecting step removed" "one tools/ci/review-artifacts.sh step" "$check" "$f"
f="$(variant outcome.yml)"; replace "$f" 'GRADLE_OUTCOME: ${{ steps.gradle.outcome }}' 'GRADLE_OUTCOME: failure'
expect_red "(n) Gradle outcome fixed to failure" 'GRADLE_OUTCOME must be ${{ steps.gradle.outcome }}' "$check" "$f"
# Upload moved in front of the collecting step: it would upload an empty directory.
f="$(variant order.yml)"; yq -i '.jobs.build.steps |= ([.[] | select(.with.name == "debug-apk-${{ env.REVIEW_SHA }}")] + [.[] | select(.with.name != "debug-apk-${{ env.REVIEW_SHA }}")])' "$f"
expect_red "(o) upload before the collecting step" "debug-apk: upload (step 0) must come after tools/ci/review-artifacts.sh" "$check" "$f"
cp tools/checks/emulator-run.sh emulator-run.bak
replace tools/checks/emulator-run.sh 'logs="$here/../../app/build/emulator-artifacts"' 'logs="$here/../../build/emulator-logs"'
expect_red "(p) emulator-run.sh writes its logs elsewhere" "does not write its logs to app/build/emulator-artifacts" "$check" "$wf"
cp emulator-run.bak tools/checks/emulator-run.sh

# Expected files (tools/ci/review-artifacts.sh): a fake build output with every file, then one file missing.
collect=tools/ci/review-artifacts.sh
for file in app/build/test-results/testDebugUnitTest/TEST-a.xml app/build/reports/tests/testDebugUnitTest/index.html \
    tools/checks/build/test-results/test/TEST-b.xml tools/checks/build/reports/tests/test/index.html \
    app/build/reports/lint-results-debug.html app/build/reports/lint-results-debug.xml \
    app/build/reports/lint-results-debug.sarif app/build/outputs/apk/debug/app-debug.apk \
    app/build/outputs/mapping/release/mapping.txt; do
  mkdir -p "$(dirname "$file")"; echo probe >"$file"
done
expect_green "all expected files after a green build" "$collect" out-complete success
[ -f out-complete/apk/app-debug.apk ] && [ -f out-complete/r8/mapping.txt ] \
  && [ -f out-complete/reports/lint/lint-results-debug.sarif ] \
  && [ -f out-complete/reports/checks-tests/junit-xml/TEST-b.xml ] \
  || { echo "COUNTERPROBE FAILED: collected files not in the uploaded directories" >&2; exit 1; }
rm app/build/outputs/mapping/release/mapping.txt
expect_red "(q) mapping.txt missing after a green build" "missing although the build was green: app/build/outputs/mapping/release/mapping.txt" "$collect" out-q success
expect_green "(q') mapping.txt missing after a red build is only noted" "$collect" out-q2 failure
rm app/build/reports/lint-results-debug.sarif
expect_red "(r) lint SARIF missing after a green build" "app/build/reports/lint-results-debug.sarif" "$collect" out-r success
rm tools/checks/build/test-results/test/TEST-b.xml
expect_red "(s) test results of tools/checks missing" "tools/checks/build/test-results/test/TEST-*.xml" "$collect" out-s success
