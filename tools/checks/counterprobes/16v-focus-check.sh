#!/usr/bin/env bash
# S4d (plan UCH-99 section 2, Z10): the focus check of the discard dialog picture (ui-node-unfocused.py, called
# in emulator-showcase.sh after the picture) must turn red if "Keep editing" or "Discard" has the focus or is
# missing, and stay green on a dump of the dialog opened by touch. No emulator, so it runs in the job
# counterprobes on every run; 16w shows the same on the device.
# Fixture: fixtures/dialog-window.xml is the unchanged dump emulator-showcase-release-light-en-discard-dialog-window.xml
# from the artifact screenshots-637cafac388e9c44a8874dc62f10c7dd5df321dd of CI run 42486376909 (release build,
# dialog opened by tapping new_task_close; only fixed UI texts, no task data).
source "$(dirname "$0")/lib.sh"
check="$root/tools/checks/ui-node-unfocused.py"
fixtures="$(dirname "$0")/fixtures"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

# The screenshots job must call the checked script on both buttons, otherwise this counterprobe checks dead code.
calls_script() {
  grep -qF 'python3 -I "$here/ui-node-unfocused.py" "$tmp/window.xml" "$discard_keep_tag" "$discard_confirm_tag"' "$1" \
    || { echo "focus check: $1 does not call ui-node-unfocused.py on both buttons"; return 1; }
}
expect_green "emulator-showcase.sh calls ui-node-unfocused.py" calls_script "$root/tools/checks/emulator-showcase.sh"
cp "$root/tools/checks/emulator-showcase.sh" "$work/no-call.sh"
replace "$work/no-call.sh" 'python3 -I "$here/ui-node-unfocused.py" "$tmp/window.xml" "$discard_keep_tag" "$discard_confirm_tag"' \
  'python3 -I "$here/ui-node-unfocused.py" "$tmp/window.xml" "$discard_keep_tag"'
expect_red "focus check only on Keep editing" "does not call ui-node-unfocused.py" calls_script "$work/no-call.sh"

run_check() { python3 -I "$check" "$1" new_task_discard_keep new_task_discard_confirm; }
expect_green "dialog opened by touch, no button focused" run_check "$fixtures/dialog-window.xml"

# probe <name>: copy of the dump to change.
probe() { cp "$fixtures/dialog-window.xml" "$work/$1.xml"; echo "$work/$1.xml"; }

f="$(probe keep-focused)"
replace "$f" 'resource-id="new_task_discard_keep" class="android.view.View" package="de.uchilab.todo" content-desc="" checkable="false" checked="false" clickable="true" enabled="true" focusable="true" focused="false"' \
  'resource-id="new_task_discard_keep" class="android.view.View" package="de.uchilab.todo" content-desc="" checkable="false" checked="false" clickable="true" enabled="true" focusable="true" focused="true"'
expect_red "Keep editing focused" "focused: new_task_discard_keep" run_check "$f"

f="$(probe confirm-focused)"
replace "$f" 'resource-id="new_task_discard_confirm" class="android.view.View" package="de.uchilab.todo" content-desc="" checkable="false" checked="false" clickable="true" enabled="true" focusable="true" focused="false"' \
  'resource-id="new_task_discard_confirm" class="android.view.View" package="de.uchilab.todo" content-desc="" checkable="false" checked="false" clickable="true" enabled="true" focusable="true" focused="true"'
expect_red "Discard focused" "focused: new_task_discard_confirm" run_check "$f"

f="$(probe confirm-missing)"
replace "$f" 'resource-id="new_task_discard_confirm"' 'resource-id=""'
expect_red "Discard button missing" "missing: new_task_discard_confirm" run_check "$f"
