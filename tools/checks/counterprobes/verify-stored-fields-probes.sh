#!/usr/bin/env bash
# Counterprobe of the counterprobes 15a to 15f (UCH-52): they must fail where the check is missing.
# Not part of run-all.sh. Usage: verify-stored-fields-probes.sh [<highest>]
#   <highest>: state where only the highest schema file counted, default b984395 (first version of UCH-52).
#   Keep this fixed commit after the merge.
# Each probe must exit 1 with "check stayed green" (exit 2 = counterprobe broken does not count):
# 1. 15a, 15b, 15d, 15e against this tree with the result of StoredFieldsCheck discarded in privacyCheck.
# 2. 15c against this tree with tools/checks/schema-check.sh replaced by "exit 0".
# 3. 15f against <highest>: there a stale higher schema file hid the new field.
# Without the dependency of privacyCheck on KSP there is no generated Room code in a fresh copy, so
# privacyCheck is red for that reason; it cannot stay green, so that variant is not a counterprobe here.
# Exit 0 only if all of them fail as expected.
source "$(dirname "$0")/lib.sh"
here="$(cd "$(dirname "$0")" && pwd)"
highest="${1:-b984395}"

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
mkdir "$tmp/highest"
git -C "$root" archive "$highest" | tar -C "$tmp/highest" -xf - || { echo "Could not export $highest" >&2; exit 2; }

make_copy
trap 'rm -rf "$tmp" "$work"' EXIT
cp -a "$work" "$tmp/no-field-check"
(cd "$tmp/no-field-check" && replace tools/checks/src/main/kotlin/de/uchilab/checks/PrivacyCheckPlugin.kt \
  "violations += StoredFieldsCheck.check(" "listOf<String>() + StoredFieldsCheck.check(")
cp -a "$work" "$tmp/no-schema-check"
printf '#!/usr/bin/env bash\nexit 0\n' >"$tmp/no-schema-check/tools/checks/schema-check.sh"

unexpected=()
must_fail() {
  local source="$1" probe="$2" log status=0
  log="$(mktemp)"
  COUNTERPROBE_SOURCE="$source" "$here/$probe" >"$log" 2>&1 || status=$?
  if [ "$status" -eq 1 ] && grep -q "check stayed green (exit 0)" "$log"; then
    echo "OK $probe ($(basename "$source")) fails as expected: $(grep 'COUNTERPROBE FAILED' "$log")"
  else
    tail -n 20 "$log"
    echo "UNEXPECTED $probe ($(basename "$source")): exit $status" >&2
    unexpected+=("$probe")
  fi
  rm -f "$log"
}

for probe in 15a-stored-field-new-column.sh 15b-stored-field-new-table.sh 15d-stored-field-not-listed.sh \
  15e-stored-field-not-in-schema.sh; do
  must_fail "$tmp/no-field-check" "$probe"
done
must_fail "$tmp/no-schema-check" 15c-stale-schema.sh
must_fail "$tmp/highest" 15f-stale-higher-schema.sh
[ "${#unexpected[@]}" -eq 0 ] || { echo "Counterprobes that did not fail: ${unexpected[*]}" >&2; exit 1; }
echo "All counterprobes 15a to 15f fail without their check."
