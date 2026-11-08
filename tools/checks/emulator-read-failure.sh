#!/usr/bin/env bash
# Emulator check for S4b (plan UCH-80 section 5, Z1-Z3, Z6, Z8, Z11, Z12): a damaged database file is never
# deleted or changed, the app shows the read failure notice instead of the empty state, offers no new task and
# does not crash, also after a second start. Three invented files, each as databases/tasks.db:
#   A: not SQLite (invented text), B: 0 bytes, C: the seed from EmulatorSeedTest with every page after the
#   first (from byte 4096) overwritten by invented bytes, header intact (plan revision 2: page 2 alone keeps
#   the database readable).
# Per case: install the file, remember sha256sum on the device; start, read failure notice shown, empty state
# and new task action missing, crash buffer empty, process running; force-stop, process ended, hash equal;
# second start as before; force-stop, hash equal.
# Usage: emulator-read-failure.sh <apk> <seed-dir>   (<seed-dir> holds tasks.db from EmulatorSeedTest)
# Needs `adb root` (emulator only). With SCREENSHOTS=<dir>, case A writes <build>-read-failure.png (interface
# for S4c, UCH-79; checked by png-not-blank.py like every screenshot).
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
apk="$1"
seed="$2"
package="de.uchilab.todo"
app_dir="/data/data/$package"
db="$app_dir/databases/tasks.db"
name="$(basename "$apk" .apk)"
tmp="$(mktemp -d)"

check=emulator-read-failure
source "$here/emulator-lib.sh"
select_emulator

cleanup() { timeout 60 adb uninstall "$package" >/dev/null 2>&1 || true; rm -rf "$tmp"; }
trap cleanup EXIT

[ -s "$seed/tasks.db" ] || fail "seed $seed/tasks.db missing (EmulatorSeedTest with -Puchilab.seedOut)"

# Test tags of the app (TaskListTags, NewTaskTags), used as resource-id thanks to testTagsAsResourceId.
error_tag="task_list_error"
empty_tag="task_list_empty"
action_tag="new_task_action"

# The three files. Invented content only.
python3 -I - "$seed/tasks.db" "$tmp" <<'PY'
import sys
seed, out = sys.argv[1], sys.argv[2]
open(f"{out}/case-a.db", "wb").write(b"Kein SQLite, erfundener Inhalt fuer den Lesefehler-Test.\n" * 40)
open(f"{out}/case-b.db", "wb").close()
data = open(seed, "rb").read()
if len(data) <= 4096 or not data.startswith(b"SQLite format 3\x00"):
    print(f"broken seed: {seed} is no SQLite database with more than one page ({len(data)} bytes)")
    sys.exit(1)
filler = b"erfundene-Bytes-"
rest = (filler * (len(data) // len(filler) + 1))[: len(data) - 4096]
open(f"{out}/case-c.db", "wb").write(data[:4096] + rest)
PY

# device_hash: sha256 of tasks.db on the device; fails if the file is missing.
device_hash() {
  timeout 10 adb shell "test -f $db" || fail "$1: file changed: $db no longer exists"
  timeout 30 adb shell sha256sum "$db" | tr -d '\r' | awk '{print $1}'
}

# no_crash <step>: crash buffer empty for the app, process running.
no_crash() {
  if crashed >"$tmp/crash.txt"; then cat "$tmp/crash.txt" >&2; fail "$1: crash"; fi
  timeout 10 adb shell pidof "$package" >/dev/null || fail "$1: process not running"
}

# wait_read_failure <step>: starts the app and waits until the read failure notice is shown; the empty state
# or a crash fail at once. Then: no empty state, no new task action, no crash, process running.
wait_read_failure() {
  local step="$1" end=$((SECONDS + 90))
  start_app "$step"
  until shown "$error_tag"; do
    if crashed >"$tmp/crash.txt"; then cat "$tmp/crash.txt" >&2; fail "$step: crash"; fi
    if node "$empty_tag" >/dev/null 2>&1; then fail "$step: read failure notice not shown (empty state instead)"; fi
    [ "$SECONDS" -lt "$end" ] || fail "$step: read failure notice not shown within 90s"
    sleep 2
  done
  ! node "$empty_tag" >/dev/null || fail "$step: empty state shown next to the read failure notice"
  ! node "$action_tag" >/dev/null || fail "$step: new task action offered despite the read failure"
  no_crash "$step"
  log "$step: read failure notice shown, no empty state, no new task action, no crash"
}

# stop_and_compare <step> <hash>: force-stop, wait for the process end, file still there and byte-identical.
stop_and_compare() {
  timeout 30 adb shell am force-stop "$package"
  wait_process_gone
  local now
  now="$(device_hash "$1")"
  [ "$now" = "$2" ] || fail "$1: file changed (sha256 $2 before, $now after)"
  log "$1: file byte-identical ($now)"
}

# run_case <case> <local file>
run_case() {
  local c="$1" before
  install_db "$2"
  before="$(device_hash "case $c installed")"
  [ "$before" = "$(sha256sum "$2" | awk '{print $1}')" ] || fail "case $c: file on the device differs from $2"
  wait_read_failure "case $c first start"
  if [ "$c" = A ] && [ -n "${SCREENSHOTS:-}" ]; then
    screen_on
    shot read-failure
    log "screenshot written to $SCREENSHOTS"
  fi
  stop_and_compare "case $c after first start" "$before"
  wait_read_failure "case $c second start"
  stop_and_compare "case $c after second start" "$before"
}

wait_for_boot
become_root
install_app
run_case A "$tmp/case-a.db"
run_case B "$tmp/case-b.db"
run_case C "$tmp/case-c.db"
log "OK: cases A (not SQLite), B (0 bytes), C (pages after the first overwritten): read failure notice, no empty state, no new task action, no crash, file byte-identical after two starts"
