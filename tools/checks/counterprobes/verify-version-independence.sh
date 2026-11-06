#!/usr/bin/env bash
# Shows that 03a, 04a and 04b stay valid after an update of activity-compose (UCH-28, point 3).
# Not part of run-all.sh, because it rewrites versions itself and needs the network.
# Usage: verify-version-independence.sh [<version>]
#   <version>: published activity-compose version newer than the catalog's. Default: the newest newer
#   version from Google's Maven index (stable preferred). Exit 2 if there is none.
# Makes a copy, raises activityCompose, rewrites lock state and checksums like Renovate does and runs
# 03a, 04a and 04b with COUNTERPROBE_SOURCE=<copy>. Exit 0 only if all of them pass.
source "$(dirname "$0")/lib.sh"
here="$(cd "$(dirname "$0")" && pwd)"
metadata_url=https://dl.google.com/android/maven2/androidx/activity/activity-compose/maven-metadata.xml

# is_newer <a> <b>: a is a newer version than b. A pre-release (1.14.0-alpha01) is older than its release
# (1.14.0); sort -V alone would order it the other way round.
is_newer() {
  local a="$1" b="$2" base_a="${1%%-*}" base_b="${2%%-*}"
  [ "$a" != "$b" ] || return 1
  if [ "$base_a" = "$base_b" ]; then
    [ "$a" = "$base_a" ] && return 0              # a is the release, b a pre-release of it
    [ "$b" = "$base_b" ] && return 1              # b is the release
    [ "$(printf '%s\n' "$a" "$b" | sort -V | tail -n 1)" = "$a" ]
  else
    [ "$(printf '%s\n' "$base_a" "$base_b" | sort -V | tail -n 1)" = "$base_a" ]
  fi
}

make_copy
current="$(sed -n 's/^activityCompose = "\(.*\)"$/\1/p' gradle/libs.versions.toml)"
[ -n "$current" ] || { echo "activityCompose not found in the catalog" >&2; exit 2; }
target="${1:-}"
if [ -z "$target" ]; then
  versions="$(curl -fsS "$metadata_url" | sed -n 's/.*<version>\([^<]*\)<\/version>.*/\1/p')" \
    || { echo "Could not read $metadata_url" >&2; exit 2; }
  newer=()
  for v in $versions; do is_newer "$v" "$current" && newer+=("$v"); done
  stable=()
  for v in "${newer[@]}"; do [[ "$v" == *-* ]] || stable+=("$v"); done
  pick=("${stable[@]}"); [ "${#pick[@]}" -gt 0 ] || pick=("${newer[@]}")
  [ "${#pick[@]}" -gt 0 ] || { echo "No published activity-compose version newer than $current" >&2; exit 2; }
  target="${pick[0]}"
  for v in "${pick[@]}"; do is_newer "$v" "$target" && target="$v"; done
fi
is_newer "$target" "$current" || { echo "$target is not newer than the current version $current" >&2; exit 2; }
echo "activity-compose: $current -> $target"

replace gradle/libs.versions.toml "activityCompose = \"$current\"" "activityCompose = \"$target\""
# The same Gradle commands as Renovate's Gradle manager (update PR).
expect_green "rewrite lock state for $target" ./gradlew "${GRADLE_ARGS[@]}" --dependency-verification lenient :app:dependencies --write-locks
expect_green "rewrite checksums for $target" ./gradlew "${GRADLE_ARGS[@]}" --write-verification-metadata sha256 :app:dependencies
grep -q "^androidx.activity:activity-compose:$target=" app/gradle.lockfile \
  || { echo "activity-compose $target is not in the lock state of the copy" >&2; exit 2; }

failed=()
for probe in 03a-lint-older-version.sh 04a-version-without-lockfile.sh 04b-version-without-checksum.sh; do
  echo "--- $probe against activity-compose $target"
  if COUNTERPROBE_SOURCE="$work" "$here/$probe"; then echo "PASS $probe"; else echo "FAIL $probe"; failed+=("$probe"); fi
done
[ "${#failed[@]}" -eq 0 ] || { echo "Not version independent: ${failed[*]}" >&2; exit 1; }
echo "03a, 04a and 04b pass with activity-compose $target."
