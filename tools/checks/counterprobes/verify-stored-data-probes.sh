#!/usr/bin/env bash
# Counterprobe of the counterprobes 10 to 14b (UCH-48/UCH-53): they must fail where the check is missing.
# Not part of run-all.sh. Usage: verify-stored-data-probes.sh [<commit>]
#   <commit>: state before the stored data checks, default 2eced09 (main before UCH-53).
# 1. Every new counterprobe except 12a/12b runs with COUNTERPROBE_SOURCE=<commit> and must fail because the
#    check stays green there (exit 1 with "check stayed green", not exit 2 = counterprobe broken).
# 2. 12a/12b hit the exported component rule that already exists in <commit>. They run against a copy of
#    this tree with that rule switched off and must fail the same way.
# Exit 0 only if all of them fail as expected.
source "$(dirname "$0")/lib.sh"
here="$(cd "$(dirname "$0")" && pwd)"
base="${1:-2eced09}"

old="$(mktemp -d)"
trap 'rm -rf "$old"' EXIT
git -C "$root" archive "$base" | tar -C "$old" -xf - || { echo "Could not export $base" >&2; exit 2; }

make_copy
trap 'rm -rf "$old" "$work"' EXIT
replace tools/checks/src/main/kotlin/de/uchilab/checks/ManifestCheck.kt \
  'if (exported && !allowed) "Exported $tag not allowed: $name" else null' 'null'
# Its unit tests would turn privacyCheck red for another reason; they are not what 12a/12b check.
rm tools/checks/src/test/kotlin/de/uchilab/checks/ManifestCheckTest.kt

# must_fail <source> <probe>: the probe must report that the check stayed green.
unexpected=()
must_fail() {
  local source="$1" probe="$2" log status=0
  log="$(mktemp)"
  COUNTERPROBE_SOURCE="$source" "$here/$probe" >"$log" 2>&1 || status=$?
  if [ "$status" -eq 1 ] && grep -q "check stayed green (exit 0)" "$log"; then
    echo "OK $probe fails as expected: $(grep 'COUNTERPROBE FAILED' "$log")"
  else
    tail -n 20 "$log"
    echo "UNEXPECTED $probe: exit $status" >&2
    unexpected+=("$probe")
  fi
  rm -f "$log"
}

for probe in 10-release-debuggable.sh 11a-backup-agent.sh 11b-fragile-user-data.sh 11c-direct-boot-aware.sh \
  12c-launcher-send-filter.sh 13a-log-d.sh 13b-println.sh 13c-set-query-callback.sh \
  14a-external-files-dir.sh 14b-device-protected-storage.sh; do
  must_fail "$old" "$probe"
done
for probe in 12a-exported-provider.sh 12b-exported-service.sh; do
  must_fail "$work" "$probe"
done
[ "${#unexpected[@]}" -eq 0 ] || { echo "Counterprobes that did not fail: ${unexpected[*]}" >&2; exit 1; }
echo "All new counterprobes fail without their check."
