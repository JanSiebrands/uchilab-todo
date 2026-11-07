#!/usr/bin/env bash
# S2a: each rule of the job tryout-apk, broken one at a time in a copy of ci.yml, must fail
# tools/ci/tryout-workflow-check.sh with its own message.
source "$(dirname "$0")/lib.sh"
make_copy
check="$src/tools/ci/tryout-workflow-check.sh"
wf=.github/workflows/ci.yml
expect_green "unchanged workflow" "$check" "$wf"
variant() { cp "$wf" "$1"; echo "$1"; }

f="$(variant needs.yml)"; replace "$f" "needs: [build, counterprobes, emulator, secrets, vulnerabilities]" "needs: [build, counterprobes, secrets, vulnerabilities]"
expect_red "(i) job missing in needs" "does not need: emulator" "$check" "$f"
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
