#!/usr/bin/env bash
# Checks that local builds leave no Kotlin daemon and no long idle Gradle daemon behind (UCH-69; the shared
# machine froze on 2026-11-06). The exit code decides.
# Usage: gradle-daemon-check.sh [project-dir]   (default: this repository)
# - gradle.properties and tools/checks/gradle.properties (included build, does not read the root file):
#   kotlin.compiler.execution.strategy=in-process,
# - gradle.properties: org.gradle.daemon.idletimeout between 1 and 600000 ms (10 minutes).
# Like Gradle, the last entry of a key wins; key and value may be separated by = or : with spaces.
set -euo pipefail
dir="${1:-$(cd "$(dirname "$0")/../.." && pwd)}"
errors=0
fail() { echo "gradle-daemon-check: FAILED: $*" >&2; errors=$((errors + 1)); }
# Root file and included build named so that neither message is part of the other.
label() { [ "$1" = gradle.properties ] && echo "root gradle.properties" || echo "$1"; }

# prop <file> <key>: value of the last entry of the key, empty if missing.
prop() {
  [ -f "$1" ] || return 0
  awk -v key="$2" '
    /^[ \t]*[#!]/ { next }
    {
      line = $0; sub(/^[ \t]+/, "", line)
      if (!match(line, /[ \t]*[=:][ \t]*/)) next
      k = substr(line, 1, RSTART - 1); v = substr(line, RSTART + RLENGTH)
      sub(/[ \t\r]+$/, "", v)
      if (k == key) value = v
    }
    END { print value }' "$1"
}

for file in gradle.properties tools/checks/gradle.properties; do
  value="$(prop "$dir/$file" kotlin.compiler.execution.strategy)"
  [ "$value" = in-process ] \
    || fail "$(label "$file"): kotlin.compiler.execution.strategy must be in-process (got: '${value}')"
done

timeout="$(prop "$dir/gradle.properties" org.gradle.daemon.idletimeout)"
if ! [[ "$timeout" =~ ^[0-9]+$ ]] || [ "$timeout" -lt 1 ] || [ "$timeout" -gt 600000 ]; then
  fail "root gradle.properties: org.gradle.daemon.idletimeout must be 1..600000 ms (got: '${timeout}')"
fi

[ "$errors" -eq 0 ] || exit 1
echo "gradle-daemon-check: OK ($dir)"
