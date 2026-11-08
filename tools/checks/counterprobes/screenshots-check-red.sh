#!/usr/bin/env bash
# Counterprobe of screenshots-check.sh, no emulator: all expected names, one missing and one empty file. The
# check must turn red and name both. Runs in the job screenshots, only with the path filter.
source "$(dirname "$0")/lib.sh"
dir="$(mktemp -d)"
trap 'rm -rf "$dir"' EXIT
mapfile -t names < <(grep -v '^$' "$root/tools/checks/screenshots-expected.txt")
for f in "${names[@]}"; do echo png >"$dir/$f"; done
rm "$dir/${names[0]}"
: >"$dir/${names[1]}"
expect_red "screenshot missing" "missing: ${names[0]}" "$root/tools/checks/screenshots-check.sh" "$dir"
expect_red "screenshot empty" "empty: ${names[1]}" "$root/tools/checks/screenshots-check.sh" "$dir"
