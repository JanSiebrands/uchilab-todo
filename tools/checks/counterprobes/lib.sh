#!/usr/bin/env bash
# Shared helpers for the counterprobe scripts (one per job at most, only with the path filter, UCH-116 R1b).
# A counterprobe builds a defect into a throwaway copy of the project and shows by exit code that the
# responsible check turns red. The checked-in tree is never modified.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
GRADLE_ARGS=(--console=plain --build-cache)

# Copies the project (without build outputs and git metadata) to a temp dir, cd's into it and removes it
# on exit or interrupt.
make_copy() {
  work="$(mktemp -d)"
  trap 'rm -rf "$work"' EXIT
  trap 'exit 130' INT TERM
  tar -C "$root" --exclude=./.git --exclude=build --exclude=.gradle --exclude=.kotlin --exclude=./.tools \
    -cf - . | tar -C "$work" -xf -
  cd "$work"
}

# Replaces a fixed string in a file and fails if it was not there (a counterprobe that changes nothing
# proves nothing).
replace() {
  local file="$1" old="$2" new="$3"
  grep -qF -- "$old" "$file" || { echo "COUNTERPROBE BROKEN: '$old' not found in $file" >&2; exit 2; }
  OLD="$old" NEW="$new" perl -0pi -e 's/\Q$ENV{OLD}\E/$ENV{NEW}/g' "$file"
}

# expect_red <description> <expected messages, one per line> <command...>
# Succeeds only if the command fails AND its output contains every expected message. A red result for
# another reason (network, typo) does not count.
expect_red() {
  local description="$1" expected="$2"; shift 2
  local log status=0 missing=() line
  log="$(mktemp)"
  "$@" >"$log" 2>&1 || status=$?
  if [ "$status" -eq 0 ]; then
    tail -n 40 "$log"; rm -f "$log"
    echo "COUNTERPROBE FAILED: $description: check stayed green (exit 0)" >&2
    return 1
  fi
  while IFS= read -r line; do
    [ -z "$line" ] || grep -qF -- "$line" "$log" || missing+=("$line")
  done <<<"$expected"
  if [ "${#missing[@]}" -gt 0 ]; then
    tail -n 40 "$log"; rm -f "$log"
    printf 'COUNTERPROBE FAILED: %s: red (exit %s), but without the expected message: %s\n' \
      "$description" "$status" "${missing[@]}" >&2
    return 1
  fi
  rm -f "$log"
  echo "counterprobe OK: $description: red as expected (exit $status), messages:"
  printf '  %s\n' "$expected"
}
