#!/usr/bin/env bash
# S4d (plan UCH-99 section 1, Z1-Z8): the list check of the screenshots job (ui-list-matches.py, called by
# list_matches in emulator-showcase.sh) must turn red for each of four defects in a real UI dump and stay green
# without one. No emulator, so it runs in the job counterprobes on every run.
# Fixture: fixtures/list-window.xml is the unchanged dump emulator-tasks-app-release-unsigned-last-window.xml
# from the artifact emulator-logs-f458108dc57097e2aa2f3afb771dd1cefb36ebad of CI run 42481532274 (release
# build, invented seed of EmulatorSeedTest: three tasks, Seed-3 without a note, Seed-2 and Seed-1 with one);
# fixtures/list-tasks.json lists the same seed in list order, as ShowcaseSeedTest writes tasks.json.
source "$(dirname "$0")/lib.sh"
check="$root/tools/checks/ui-list-matches.py"
fixtures="$(dirname "$0")/fixtures"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

# calls_script <emulator-showcase.sh>: the screenshots job must use ui-list-matches.py and keep no list check of
# its own, otherwise this counterprobe would check dead code (Z8).
calls_script() {
  grep -qF 'python3 -I "$here/ui-list-matches.py" "$tmp/window.xml" "$seed/tasks.json" "$item_tag"' "$1" \
    || { echo "list check: $1 does not call ui-list-matches.py"; return 1; }
  ! grep -qE 'wrong order|note not shown|json\.load' "$1" \
    || { echo "list check: $1 has a list check of its own"; return 1; }
}
expect_green "emulator-showcase.sh calls ui-list-matches.py" calls_script "$root/tools/checks/emulator-showcase.sh"
cp "$root/tools/checks/emulator-showcase.sh" "$work/inline.sh"
replace "$work/inline.sh" 'python3 -I "$here/ui-list-matches.py" "$tmp/window.xml"' \
  'python3 -I - "$tmp/window.xml" <<<"print(\"wrong order\")" #'
expect_red "list check inline again (dead code)" "does not call ui-list-matches.py" calls_script "$work/inline.sh"

run_check() { python3 -I "$check" "$1" "$fixtures/list-tasks.json" task_item; }
expect_green "unchanged dump" run_check "$fixtures/list-window.xml"

# probe <name>: copy of the dump to change.
probe() { cp "$fixtures/list-window.xml" "$work/$1.xml"; echo "$work/$1.xml"; }

f="$(probe note-missing)"   # note of Seed-2 removed from the note line and the merged content-desc
replace "$f" 'Erledigt' ''
expect_red "note missing" "note not shown: Seed-2-738445cc" run_check "$f"

f="$(probe note-extra)"     # note line of Seed-2 also in the entry of Seed-3, which has no note
replace "$f" 'content-desc="Seed-3-738445cc"' 'content-desc="Seed-3-738445cc. Note: Erledigt"'
expect_red "note for a task without a note" "note shown for a task without a note: Seed-3-738445cc" run_check "$f"

f="$(probe order)"          # bounds of the first two entries swapped
replace "$f" 'bounds="[0,64][320,120]"' 'bounds="SWAP"'
replace "$f" 'bounds="[0,121][320,189]"' 'bounds="[0,64][320,120]"'
replace "$f" 'bounds="SWAP"' 'bounds="[0,121][320,189]"'
expect_red "order swapped" "wrong order" run_check "$f"

f="$(probe title-missing)"  # title of Seed-1 removed from the title node and the merged content-desc
replace "$f" 'Seed-1-738445cc' ''
expect_red "title missing" "missing: Seed-1-738445cc" run_check "$f"
