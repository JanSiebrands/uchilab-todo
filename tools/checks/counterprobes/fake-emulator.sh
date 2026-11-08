#!/usr/bin/env bash
# Fake emulator and fake adb for the counterprobes 16y and 16z (UCH-112): restart_emulator (emulator-lib.sh)
# runs against them without an emulator, so the retry and the time limit are shown in the counterprobes job.
# Sourced by the counterprobe after lib.sh. Sets up in $work (created by the caller):
# - $work/sdk/emulator/emulator (ANDROID_HOME=$work/sdk): per start, the next word of FAKE_STARTS decides:
#   "dies" ends at once (exit 1), "hangs" keeps running without booting, "boots" marks the device as booted
#   and keeps running. Every start is counted in $work/starts.
# - $work/bin/adb (first in PATH): getprop sys.boot_completed answers 1 and pm path android a package only
#   after a "boots" start; everything else succeeds without output. No real device is ever contacted.
# fake_cleanup ends all fake emulator processes; the caller puts it in its EXIT trap.
mkdir -p "$work/sdk/emulator" "$work/bin"
: >"$work/starts"
cat >"$work/sdk/emulator/emulator" <<FAKE
#!/usr/bin/env bash
echo \$\$ >>"$work/pids"
echo start >>"$work/starts"
n=\$(wc -l <"$work/starts")
mode=\$(echo "\$FAKE_STARTS" | cut -d' ' -f"\$n")
echo "fake emulator start \$n: \${mode:-none} (\$*)"
case "\$mode" in
  boots) touch "$work/booted"; exec sleep 30 ;;
  hangs) exec sleep 30 ;;
  *) exit 1 ;;
esac
FAKE
cat >"$work/bin/adb" <<FAKE
#!/usr/bin/env bash
case "\$*" in
  *"getprop sys.boot_completed"*) [ -e "$work/booted" ] && echo 1 || echo ;;
  *"pm path android"*) [ -e "$work/booted" ] && echo package:/system/framework/framework-res.apk ;;
esac
exit 0
FAKE
chmod +x "$work/sdk/emulator/emulator" "$work/bin/adb"
fake_cleanup() {
  if [ -f "$work/pids" ]; then xargs -r kill -9 <"$work/pids" 2>/dev/null || true; fi
  rm -rf "$work"
}
# fake_restart: restart_emulator with the fakes on port 5582 (no emulator runs there), at most 120 s.
fake_restart() {
  timeout 120 env PATH="$work/bin:$PATH" ANDROID_HOME="$work/sdk" ANDROID_SERIAL=emulator-5582 \
    EMULATOR_AVD=uch112-fake EMULATOR_OPTIONS=-no-window EMULATOR_LOGS="$work/logs" "$@" \
    bash -c 'check=fake name=restart tmp="$(mktemp -d)"; source tools/checks/emulator-lib.sh; restart_emulator'
}
