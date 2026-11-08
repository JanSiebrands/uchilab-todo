#!/usr/bin/env bash
# Runs all counterprobes that need no emulator; the exit code decides. *-emulator.sh (07b, 16a to 16h) run
# in the emulator job (16j, 16s, 16w and 16x in the screenshots job, 16k to 16n in the job emulator-read-failure), 15a-tryout to 15f-tryout in the job tryout-apk (tools/ci/tryout-emulator-run.sh).
# 16u (list check) and 16v (focus check of the discard dialog) run here, on fixtures from real UI dumps.
# 16y and 16z (retry and time limit of restart_emulator, UCH-112) run here, against a fake emulator and fake adb.
# Usage: run-all.sh            (against this repository)
#        COUNTERPROBE_SOURCE=<dir> run-all.sh   (against another tree, e.g. an old commit)
set -uo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
failed=()
for probe in "$here"/[0-9]*.sh; do
  name="$(basename "$probe")"
  case "$name" in *-emulator.sh|15[a-f]-tryout-*) continue ;; esac
  echo "::group::$name"
  if "$probe"; then echo "PASS $name"; else echo "FAIL $name"; failed+=("$name"); fi
  echo "::endgroup::"
done
if [ "${#failed[@]}" -gt 0 ]; then
  echo "Counterprobes failed: ${failed[*]}" >&2
  exit 1
fi
echo "All counterprobes passed."
