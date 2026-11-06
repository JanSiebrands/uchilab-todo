#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"
make_copy
# Point 4 of UCH-28: a red run on main must be reported by tools/ci/main-run-status.sh with exit 1 and the
# run URL; no completed run or a failed query must give exit 2, never green. A fake gh in PATH returns
# fixed answers of `gh run list`, so no network is needed.
mkdir -p fakebin fixtures
cat >fakebin/gh <<'GH'
#!/usr/bin/env bash
# Fake gh: prints fixtures/<workflow>.json; exit 1 without a fixture (like a failed query).
while [ $# -gt 0 ]; do [ "$1" = "--workflow" ] && wf="$2"; shift; done
cat "$FIXTURES/$wf.json"
GH
chmod +x fakebin/gh
run() { local u="https://github.com/o/r/actions/runs/$1"; echo "{\"databaseId\":$1,\"status\":\"$2\",\"conclusion\":\"$3\",\"event\":\"push\",\"createdAt\":\"2026-11-0$1T04:00:00Z\",\"url\":\"$u\",\"displayTitle\":\"t\"}"; }
fixture() { local name="$1"; shift; (IFS=,; echo "[$*]") >"fixtures/$name.json"; }
fixture red.yml "$(run 2 completed failure)" "$(run 1 completed success)"
fixture red-then-cancelled.yml "$(run 3 completed cancelled)" "$(run 2 completed timed_out)" "$(run 1 completed success)"
fixture green.yml "$(run 2 completed success)" "$(run 1 completed failure)"
fixture three-red.yml "$(run 5 in_progress "")" "$(run 4 completed startup_failure)" "$(run 3 completed failure)" \
  "$(run 2 completed failure)" "$(run 1 completed success)"
fixture empty.yml
fixture only-cancelled.yml "$(run 1 completed cancelled)"
fixture unknown.yml "$(run 2 completed action_required)" "$(run 1 completed success)"
export FIXTURES="$work/fixtures" PATH="$work/fakebin:$PATH"
run_status=tools/ci/main-run-status.sh

expect_red "latest run failed" "RED red.yml https://github.com/o/r/actions/runs/2 since=2026-11-02T04:00:00Z count=1" \
  "$run_status" --workflow red.yml
expect_red "failed run followed by a cancelled one stays red" \
  "RED red-then-cancelled.yml https://github.com/o/r/actions/runs/2 " "$run_status" --workflow red-then-cancelled.yml
expect_green "latest run green" "$run_status" --workflow green.yml
# Three red runs in a row: exactly one line with count=3, since = the oldest red run.
three_red() {
  local out code=0
  out="$("$run_status" --workflow three-red.yml)" || code=$?
  echo "$out"
  [ "$code" -eq 1 ] && [ "$out" = "RED three-red.yml https://github.com/o/r/actions/runs/4 since=2026-11-02T04:00:00Z count=3" ]
}
expect_green "three red runs give one line with count=3" three_red
# Exit 2 must never look like green: check the exact code, not only "not 0".
expect_exit() {
  local want="$1" code=0; shift
  "$@" || code=$?
  [ "$code" -eq "$want" ] || { echo "expected exit $want, got $code"; return 1; }
}
expect_green "empty run list gives exit 2" expect_exit 2 "$run_status" --workflow empty.yml
expect_green "only cancelled runs give exit 2" expect_exit 2 "$run_status" --workflow only-cancelled.yml
expect_green "failed query gives exit 2" expect_exit 2 "$run_status" --workflow missing.yml
expect_green "red and green together give exit 1" expect_exit 1 "$run_status" --workflow green.yml --workflow red.yml
# An unknown conclusion (e.g. action_required) is an error, never green.
expect_green "unknown conclusion gives exit 2" expect_exit 2 "$run_status" --workflow unknown.yml
# Exit 2 has precedence over 1, also if the red workflow comes first.
expect_green "red then failed query gives exit 2" expect_exit 2 "$run_status" --workflow red.yml --workflow missing.yml
expect_green "failed query then red gives exit 2" expect_exit 2 "$run_status" --workflow missing.yml --workflow red.yml
