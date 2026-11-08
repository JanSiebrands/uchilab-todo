#!/usr/bin/env bash
# UCH-112, edge case "restart succeeds on the second attempt": restart_emulator (emulator-lib.sh) must retry
# a start whose emulator process ends, and continue green when the second start boots. Fake emulator and
# fake adb (fake-emulator.sh), no device needed. Expected: exit 0, exactly 2 starts, and the log names the
# second start as the one that came back. Red if the retry is missing or the success is reported for the
# wrong start.
source "$(dirname "$0")/lib.sh"
cd "$root"
work="$(mktemp -d)"
source "$(dirname "$0")/fake-emulator.sh"
trap fake_cleanup EXIT
out="$(mktemp -p "$work")"
status=0
fake_restart FAKE_STARTS="dies boots" EMULATOR_RESTARTS=3 EMULATOR_BOOT_TIMEOUT=20 >"$out" 2>&1 || status=$?
starts="$(wc -l <"$work/starts")"
if [ "$status" -ne 0 ] || [ "$starts" -ne 2 ] || ! grep -qF "emulator back after restart 2/3" "$out"; then
  cat "$out"
  echo "COUNTERPROBE FAILED: second start boots: expected green after 2 starts with 'back after restart 2/3', got exit $status after $starts starts" >&2
  exit 1
fi
echo "counterprobe OK: second start boots: green after 2 starts (back after restart 2/3)"
