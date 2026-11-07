#!/usr/bin/env bash
# Prints minSdk from app/build.gradle.kts, so no script hard-codes it. Fails unless there is exactly one
# plain number assignment. Usage: tryout-min-sdk.sh [project-dir]
set -euo pipefail
file="${1:-.}/app/build.gradle.kts"
values="$(sed -nE 's/^[[:space:]]*minSdk[[:space:]]*=[[:space:]]*([0-9]+)[[:space:]]*$/\1/p' "$file")"
if [ -z "$values" ] || [ "$(wc -l <<<"$values")" -ne 1 ]; then
  echo "tryout-min-sdk: expected exactly one 'minSdk = <number>' in $file, got: '${values//$'\n'/ }'" >&2
  exit 1
fi
echo "$values"
