#!/usr/bin/env bash
# S4c screenshots (plan UCH-81 section 6, Z1-Z3, Z6, Z8): screenshots-check.sh against a generated directory with
# all names of screenshots-expected.txt (invented PNGs, light or dark after the name) must fail if (a) one is
# missing, (b) one is unexpected, (c) one is misnamed (light -> hell); complete it stays green. No emulator.
source "$(dirname "$0")/lib.sh"
check="$root/tools/checks/screenshots-check.sh"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
fill() {
  rm -rf "$work/shots"; mkdir -p "$work/shots"
  while IFS= read -r f; do
    [ -n "$f" ] || continue
    case "$f" in *-dark-*) t=dark ;; *) t=light ;; esac
    python3 -I "$root/tools/checks/counterprobes/png-fixture.py" "$work/shots/$f" "$t"
  done <"$root/tools/checks/screenshots-expected.txt"
}
fill
expect_green "all 22 screenshots present" env -u GITHUB_STEP_SUMMARY "$check" "$work/shots"
rm "$work/shots/release-read-failure-dark-de.png"
expect_red "(a) one screenshot missing" "missing: release-read-failure-dark-de.png" env -u GITHUB_STEP_SUMMARY "$check" "$work/shots"
fill; cp "$work/shots/release-list-light-en.png" "$work/shots/release-list-light-fr.png"
expect_red "(b) one screenshot unexpected" "unexpected: release-list-light-fr.png" env -u GITHUB_STEP_SUMMARY "$check" "$work/shots"
fill; mv "$work/shots/release-empty-light-de.png" "$work/shots/release-empty-hell-de.png"
expect_red "(c) one screenshot misnamed (light -> hell)" "name does not follow the schema" env -u GITHUB_STEP_SUMMARY "$check" "$work/shots"
