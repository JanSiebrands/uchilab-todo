#!/usr/bin/env bash
# S4c screenshots (plan UCH-81 section 6, Z5, Z9): png-theme.py must fail for a light PNG checked as dark and a
# dark PNG checked as light, and pass when the theme matches. Invented PNGs, no emulator.
source "$(dirname "$0")/lib.sh"
check="$root/tools/checks/png-theme.py"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
python3 -I "$root/tools/checks/counterprobes/png-fixture.py" "$work/light.png" light
python3 -I "$root/tools/checks/counterprobes/png-fixture.py" "$work/dark.png" dark
expect_red "light picture checked as dark" "wrong theme" python3 -I "$check" "$work/light.png" dark
expect_red "dark picture checked as light" "wrong theme" python3 -I "$check" "$work/dark.png" light
expect_green "light picture checked as light" python3 -I "$check" "$work/light.png" light
expect_green "dark picture checked as dark" python3 -I "$check" "$work/dark.png" dark
