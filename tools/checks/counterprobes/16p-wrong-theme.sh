#!/usr/bin/env bash
# S4c screenshots (plan UCH-81 section 6, Z5, Z9): png-theme.py must fail for a light PNG checked as dark and a
# dark PNG checked as light, and pass when the theme matches. Invented PNGs, no emulator. Also the two cases of
# the first run (UCH-95): a dark picture with a light keyboard below and a light picture under a dialog scrim
# keep their theme.
source "$(dirname "$0")/lib.sh"
check="$root/tools/checks/png-theme.py"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
python3 -I "$root/tools/checks/counterprobes/png-fixture.py" "$work/light.png" light
python3 -I "$root/tools/checks/counterprobes/png-fixture.py" "$work/dark.png" dark
python3 -I "$root/tools/checks/counterprobes/png-fixture.py" "$work/dark-keyboard.png" dark-keyboard
python3 -I "$root/tools/checks/counterprobes/png-fixture.py" "$work/light-scrim.png" light-scrim
expect_red "light picture checked as dark" "wrong theme" python3 -I "$check" "$work/light.png" dark
expect_red "dark picture checked as light" "wrong theme" python3 -I "$check" "$work/dark.png" light
expect_green "light picture checked as light" python3 -I "$check" "$work/light.png" light
expect_green "dark picture checked as dark" python3 -I "$check" "$work/dark.png" dark
expect_red "dark picture with light keyboard checked as light" "wrong theme" python3 -I "$check" "$work/dark-keyboard.png" light
expect_red "light picture under a scrim checked as dark" "wrong theme" python3 -I "$check" "$work/light-scrim.png" dark
expect_green "dark picture with light keyboard checked as dark" python3 -I "$check" "$work/dark-keyboard.png" dark
expect_green "light picture under a scrim checked as light" python3 -I "$check" "$work/light-scrim.png" light
