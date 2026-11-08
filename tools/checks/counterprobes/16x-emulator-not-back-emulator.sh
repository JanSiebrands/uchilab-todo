#!/usr/bin/env bash
# UCH-112: restart_emulator (emulator-lib.sh) replaces adb reboot for the language switch of the screenshots
# job. If the emulator does not come back, the job must be red, never green or skipped. Here the start cannot
# succeed: an AVD that does not exist, on a port where no emulator runs (5580), so the running emulator of the
# job (5554) is not touched. Expected: red after EMULATOR_RESTARTS starts with "did not come back".
# Runs in the screenshots job only (needs the emulator binary under ANDROID_HOME and EMULATOR_OPTIONS).
source "$(dirname "$0")/lib.sh"
cd "$root"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
expect_red "emulator does not come back after restart (emulator)" "emulator did not come back after 2 restarts" \
  env ANDROID_SERIAL=emulator-5580 EMULATOR_AVD=uch112-no-such-avd EMULATOR_RESTARTS=2 EMULATOR_LOGS="$work" \
  bash -c 'check=16x name=restart tmp="$(mktemp -d)"; source tools/checks/emulator-lib.sh; restart_emulator'
