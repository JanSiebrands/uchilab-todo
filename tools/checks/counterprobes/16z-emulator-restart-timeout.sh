#!/usr/bin/env bash
# UCH-112, edge case "emulator start takes longer than usual": a start that keeps running but does not boot
# within EMULATOR_BOOT_TIMEOUT must count as failed, be stopped and retried; after the last start the job must
# be red with the time limit as reason, never green or skipped. Fake emulator and fake adb
# (fake-emulator.sh), no device needed; time limit 4 s instead of 420 s so the counterprobe is fast.
source "$(dirname "$0")/lib.sh"
cd "$root"
work="$(mktemp -d)"
source "$(dirname "$0")/fake-emulator.sh"
trap fake_cleanup EXIT
expect_red "start does not boot within the time limit" \
  "emulator did not come back after 2 restarts (last: no boot within 4 s" \
  fake_restart FAKE_STARTS="hangs hangs" EMULATOR_RESTARTS=2 EMULATOR_BOOT_TIMEOUT=4
for pid in $(cat "$work/pids"); do
  if kill -0 "$pid" 2>/dev/null; then
    echo "COUNTERPROBE FAILED: start that did not boot (pid $pid) was not stopped before the next start" >&2
    exit 1
  fi
done
echo "counterprobe OK: starts that did not boot were stopped"
