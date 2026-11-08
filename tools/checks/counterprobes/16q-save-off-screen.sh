#!/usr/bin/env bash
# S4c screenshots (plan UCH-81 section 6, Z3): ui-node-visible.py must fail if new_task_save (a) is missing,
# (b) reaches beyond the right edge of the display, (c) has zero area; inside it stays green. Invented
# window.xml, no emulator.
source "$(dirname "$0")/lib.sh"
check="$root/tools/checks/ui-node-visible.py"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
window() {
  cat >"$work/window.xml" <<XML
<?xml version='1.0' encoding='UTF-8' standalone='yes' ?><hierarchy rotation="0"><node index="0" text="" resource-id="" bounds="[0,0][1080,2400]"><node index="1" text="" resource-id="$1" bounds="$2" /></node></hierarchy>
XML
}
window new_task_save "[880,140][1060,260]"
expect_green "Save inside the display" python3 -I "$check" "$work/window.xml" new_task_save 1080 2400
window new_task_close "[880,140][1060,260]"
expect_red "(a) Save missing" "new_task_save missing" python3 -I "$check" "$work/window.xml" new_task_save 1080 2400
window new_task_save "[980,140][1160,260]"
expect_red "(b) Save beyond the right edge" "outside the display" python3 -I "$check" "$work/window.xml" new_task_save 1080 2400
window new_task_save "[880,140][880,260]"
expect_red "(c) Save with zero area" "zero area" python3 -I "$check" "$work/window.xml" new_task_save 1080 2400
