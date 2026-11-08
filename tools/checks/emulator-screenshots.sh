#!/usr/bin/env bash
# Runs inside the screenshots job (UCH-65, plan UCH-81 section 1). First the release check of emulator-tasks.sh
# and counterprobe 16j: only this image has a keyboard app, so only here the check "keyboard open" (UCH-65)
# runs. Then the screenshots for the feature PR, all in the release build with R8: per device language (en,
# de; persist.sys.locale and reboot, read back) and per mode (light, dark; cmd uimode night, read back)
# emulator-showcase.sh (empty state, list, input states, 200 % font, landscape) and emulator-read-failure.sh
# with SCREENSHOTS (all three cases, case A gives the read failure picture). Then counterprobes 16s and 16w
# (discard dialog opened by key must fail with "focused:"; 16u and 16v need no emulator, job counterprobes) and
# screenshots-check.sh (exactly the 22 names of screenshots-expected.txt, schema, content, theme).
# Only invented data. Each step is limited to 10 minutes; the exit code decides.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
build="$here/../../app/build"
apk="$build/outputs/apk/release/app-release-unsigned.apk"
logs="$build/emulator-artifacts"
shots="$logs/screenshots"
save_logcat() { mkdir -p "$logs"; timeout 60 adb logcat -d -b all >"$logs/logcat.txt" 2>&1 || true; }
trap save_logcat EXIT
export EMULATOR_LOGS="$logs"

check=emulator-screenshots
name=release
tmp="$(mktemp -d)"
source "$here/emulator-lib.sh"
select_emulator
log "step: emulator-tasks.sh and 16j ($SECONDS s)"
timeout 600 "$here/emulator-tasks.sh" "$apk" "$build/emulator-seed"
timeout 600 "$here/counterprobes/16j-keyboard-closed-emulator.sh" "$apk" "$build/emulator-seed"

rm -rf "$shots"
for lang in en de; do
  locale="$([ "$lang" = de ] && echo de-DE || echo en-US)"
  wait_for_boot
  become_root
  timeout 10 adb shell setprop persist.sys.locale "$locale" || fail "setprop persist.sys.locale $locale failed"
  timeout 60 adb reboot || fail "adb reboot failed"
  wait_for_boot
  now="$(timeout 10 adb shell getprop persist.sys.locale | tr -d '\r')"
  [ "$now" = "$locale" ] || fail "device language: set $locale, read back $now"
  log "device language $now (read back)"
  for mode in light dark; do
    night="$([ "$mode" = dark ] && echo yes || echo no)"
    timeout 10 adb shell cmd uimode night "$night" >/dev/null || fail "cmd uimode night $night failed"
    now="$(timeout 10 adb shell cmd uimode night | tr -d '\r')"
    [ "$now" = "Night mode: $night" ] || fail "mode: set $night, read back $now"
    log "step: $mode-$lang ($now, read back) showcase ($SECONDS s)"
    timeout 600 env SCREENSHOTS="$shots" "$here/emulator-showcase.sh" "$apk" "$build/showcase-seed"
    log "step: $mode-$lang read failure ($SECONDS s)"
    timeout 600 env SCREENSHOTS="$shots" SHOT_BUILD=release SHOT_SUFFIX="-$mode-$lang" \
      "$here/emulator-read-failure.sh" "$apk" "$build/emulator-seed"
  done
done

log "step: 16s ($SECONDS s)"
timeout 600 "$here/counterprobes/16s-wrong-state-emulator.sh" "$apk" "$build/showcase-seed"
log "step: 16w ($SECONDS s)"
timeout 600 "$here/counterprobes/16w-dialog-by-key-emulator.sh" "$apk" "$build/showcase-seed"
log "step: screenshots-check.sh ($SECONDS s)"
timeout 600 "$here/screenshots-check.sh" "$shots"
rm -rf "$tmp"
echo "emulator-screenshots: OK ($SECONDS s)"
