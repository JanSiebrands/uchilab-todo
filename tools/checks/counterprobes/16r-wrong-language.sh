#!/usr/bin/env bash
# S4c screenshots (plan UCH-81 section 6, Z4): ui-node-text.py, compared with values-de/strings.xml, must fail
# for an invented window.xml whose new_task_action carries the English value, and pass for the German value.
# Both values are read from the string resources, not written here. No emulator.
source "$(dirname "$0")/lib.sh"
check="$root/tools/checks/ui-node-text.py"
res="$root/app/src/main/res"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
value() { python3 -I -c 'import sys, xml.etree.ElementTree as ET; print(next(s.text for s in ET.parse(sys.argv[1]).getroot().iter("string") if s.get("name") == "new_task_action"))' "$1"; }
window() {
  python3 -I - "$work/window.xml" "$1" <<'PY'
import sys
from xml.sax.saxutils import quoteattr
open(sys.argv[1], "w", encoding="utf-8").write(
    "<?xml version='1.0' encoding='UTF-8' standalone='yes' ?><hierarchy rotation=\"0\">"
    "<node text=\"\" resource-id=\"new_task_action\" bounds=\"[700,2100][1040,2260]\">"
    f"<node text={quoteattr(sys.argv[2])} resource-id=\"\" bounds=\"[760,2150][1000,2210]\" /></node></hierarchy>")
PY
}
en="$(value "$res/values/strings.xml")"; de="$(value "$res/values-de/strings.xml")"
[ -n "$en" ] && [ -n "$de" ] && [ "$en" != "$de" ] || { echo "COUNTERPROBE BROKEN: new_task_action en=$en de=$de" >&2; exit 2; }
window "$en"
expect_red "English text checked against German resource" "wrong language" python3 -I "$check" "$work/window.xml" new_task_action "$res/values-de/strings.xml" new_task_action
window "$de"
expect_green "German text checked against German resource" python3 -I "$check" "$work/window.xml" new_task_action "$res/values-de/strings.xml" new_task_action
