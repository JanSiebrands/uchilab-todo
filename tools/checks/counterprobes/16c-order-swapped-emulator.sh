#!/usr/bin/env bash
# S3 emulator check (plan UCH-58 section 7, counterprobe c): the expected order is reversed;
# emulator-tasks.sh must fail on the order. Runs in the emulator job only.
# Usage: 16c-order-swapped-emulator.sh <apk> <seed-dir>
source "$(dirname "$0")/lib.sh"
apk="$(realpath "$1")"; seed="$(realpath "$2")"
reversed="$(mktemp -d)"
trap 'rm -rf "$reversed"' EXIT
cp "$seed/tasks.db" "$reversed/"
tac "$seed/titles.txt" >"$reversed/titles.txt"
cmp -s "$seed/titles.txt" "$reversed/titles.txt" && { echo "COUNTERPROBE BROKEN: order unchanged" >&2; exit 2; }
expect_red "expected order reversed (emulator)" "wrong order" \
  "$src/tools/checks/emulator-tasks.sh" "$apk" "$reversed"
