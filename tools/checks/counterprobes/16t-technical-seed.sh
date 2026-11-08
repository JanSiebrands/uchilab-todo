#!/usr/bin/env bash
# S4c screenshots (plan UCH-81 section 6, Z4): showcase-seed-check.py must fail for titles in the pattern of
# EmulatorSeedTest (Seed-1-0a1b2c3d) and of the UI checks, and for a seed without a note over two lines or
# without a task without a note; readable invented titles stay green. No emulator.
source "$(dirname "$0")/lib.sh"
check="$root/tools/checks/showcase-seed-check.py"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
mkdir -p "$work/tech" "$work/hex" "$work/ok" "$work/no-multi" "$work/all-notes"
printf 'Seed-3-0a1b2c3d\nSeed-2-0a1b2c3d\nSeed-1-0a1b2c3d\n' >"$work/tech/titles.txt"
printf 'Blumen 0a1b2c3d gießen\n' >"$work/hex/titles.txt"
printf 'Rad flicken\nBlumen gießen\n' >"$work/ok/titles.txt"
echo '[{"title": "Rad flicken", "note": "Schlauch kaufen\nVentil prüfen"}, {"title": "Blumen gießen", "note": null}]' >"$work/ok/tasks.json"
cp "$work/ok/titles.txt" "$work/no-multi/titles.txt"
echo '[{"title": "Rad flicken", "note": "Schlauch kaufen"}, {"title": "Blumen gießen", "note": null}]' >"$work/no-multi/tasks.json"
cp "$work/ok/titles.txt" "$work/all-notes/titles.txt"
echo '[{"title": "Rad flicken", "note": "Schlauch kaufen\nVentil prüfen"}, {"title": "Blumen gießen", "note": "Gießkanne"}]' >"$work/all-notes/tasks.json"
expect_red "technical seed titles (EmulatorSeedTest pattern)" "technical title" python3 -I "$check" "$work/tech/titles.txt"
expect_red "title with an 8 hex run id" "technical title" python3 -I "$check" "$work/hex/titles.txt"
expect_red "no note over two lines" "no task with a note over more than one line" python3 -I "$check" "$work/no-multi/titles.txt"
expect_red "no task without a note" "no task without a note" python3 -I "$check" "$work/all-notes/titles.txt"
expect_green "readable invented titles" python3 -I "$check" "$work/ok/titles.txt"
