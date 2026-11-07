#!/usr/bin/env bash
# Runs all counterprobes that need no emulator; the exit code decides. 07b runs in the emulator job,
# 10a to 10f in the job tryout-apk (tools/ci/tryout-emulator-run.sh).
# Usage: run-all.sh            (against this repository)
#        COUNTERPROBE_SOURCE=<dir> run-all.sh   (against another tree, e.g. an old commit)
set -uo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
failed=()
for probe in "$here"/[0-9]*.sh; do
  name="$(basename "$probe")"
  case "$name" in 07b-*|10[a-f]-*) continue ;; esac
  echo "::group::$name"
  if "$probe"; then echo "PASS $name"; else echo "FAIL $name"; failed+=("$name"); fi
  echo "::endgroup::"
done
if [ "${#failed[@]}" -gt 0 ]; then
  echo "Counterprobes failed: ${failed[*]}" >&2
  exit 1
fi
echo "All counterprobes passed."
