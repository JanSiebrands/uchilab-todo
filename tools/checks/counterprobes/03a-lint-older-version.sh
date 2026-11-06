#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"
make_copy
# Point 3: a newer version of a dependency exists. The catalog declares activity-compose 1.9.0 while the
# current version <V> from the catalog is published. Lint's version checks only work for real artifacts,
# so this counterprobe keeps the real library; the version is read from the catalog, so updates of
# activity-compose do not break it. Lock state and checksums are rewritten in the copy, so the build itself
# is consistent; lint must stay green.
older=1.9.0
current="$(sed -n 's/^activityCompose = "\(.*\)"$/\1/p' gradle/libs.versions.toml)"
[ -n "$current" ] || { echo "COUNTERPROBE BROKEN: activityCompose not found in the catalog" >&2; exit 2; }
if [ "$older" = "$current" ] || [ "$(printf '%s\n' "$older" "$current" | sort -V | head -n 1)" != "$older" ]; then
  echo "COUNTERPROBE BROKEN: $older is not older than the current version $current" >&2; exit 2
fi
replace_regex gradle/libs.versions.toml '^activityCompose = "[^"]*"$' "activityCompose = \"$older\""
grep -qx "activityCompose = \"$older\"" gradle/libs.versions.toml \
  || { echo "COUNTERPROBE BROKEN: activityCompose was not set to $older" >&2; exit 2; }
expect_green "rewrite lock state for $older" ./gradlew "${GRADLE_ARGS[@]}" --dependency-verification lenient :app:dependencies --write-locks
expect_green "rewrite checksums for $older" ./gradlew "${GRADLE_ARGS[@]}" --write-verification-metadata sha256 lint
expect_green "lint with an older dependency version ($older, current $current)" ./gradlew "${GRADLE_ARGS[@]}" lint
