#!/usr/bin/env bash
# Shared helpers for the counterprobes. Every counterprobe builds a defect into a throwaway copy of the
# project and shows by exit code that the responsible check turns red (or, for point 3, stays green).
# The checked-in tree is never modified.
#
# COUNTERPROBE_SOURCE: project tree to copy (default: this repository). Used to run the counterprobes
# against an old commit, where they must fail because the check is missing.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
src="${COUNTERPROBE_SOURCE:-$root}"
# Shared local build cache keeps the repeated builds of the copies fast; no remote cache.
GRADLE_ARGS=(--console=plain --build-cache)

# Copies the project (without build outputs and git metadata) to a temp dir, cd's into it and removes it
# on exit or interrupt.
make_copy() {
  work="$(mktemp -d)"
  trap 'rm -rf "$work"' EXIT
  trap 'exit 130' INT TERM
  tar -C "$src" --exclude=./.git --exclude=build --exclude=.gradle --exclude=.kotlin --exclude=./.tools \
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

# expect_red <description> <expected message> <command...>
# Succeeds only if the command fails AND its output contains the expected message. A red result for
# another reason (network, lock state, typo) does not count.
expect_red() {
  local description="$1" expected="$2"; shift 2
  local log status=0
  log="$(mktemp)"
  "$@" >"$log" 2>&1 || status=$?
  if [ "$status" -eq 0 ]; then
    tail -n 40 "$log"; rm -f "$log"
    echo "COUNTERPROBE FAILED: $description: check stayed green (exit 0)" >&2
    return 1
  fi
  if ! grep -qF -- "$expected" "$log"; then
    tail -n 40 "$log"; rm -f "$log"
    echo "COUNTERPROBE FAILED: $description: red (exit $status), but without the expected message: $expected" >&2
    return 1
  fi
  rm -f "$log"
  echo "counterprobe OK: $description: red as expected (exit $status, message: $expected)"
}

# expect_green <description> <command...>: succeeds only if the command succeeds.
expect_green() {
  local description="$1"; shift
  local log status=0
  log="$(mktemp)"
  "$@" >"$log" 2>&1 || status=$?
  if [ "$status" -ne 0 ]; then
    tail -n 40 "$log"; rm -f "$log"
    echo "COUNTERPROBE FAILED: $description: expected green, got exit $status" >&2
    return 1
  fi
  rm -f "$log"
  echo "counterprobe OK: $description: green as expected"
}
