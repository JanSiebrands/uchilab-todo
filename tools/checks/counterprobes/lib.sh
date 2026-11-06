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

# Replaces the first match of a Perl regex in a file and fails if there is none. For values that change
# over time (e.g. the current version in the catalog); fixed text uses replace().
replace_regex() {
  local file="$1" regex="$2" new="$3"
  grep -qP -- "$regex" "$file" || { echo "COUNTERPROBE BROKEN: /$regex/ not found in $file" >&2; exit 2; }
  REGEX="$regex" NEW="$new" perl -0pi -e 's/$ENV{REGEX}/$ENV{NEW}/m' "$file"
}

# Test library de.uchilab.probe:probe-lib in a local Maven repository of the copy (probe-repo/), versions
# 1.0 and 2.0, so the locking and verification counterprobes do not depend on the version of a real
# library. The repository only serves the group de.uchilab.probe. The app copy depends on probe-lib:1.0;
# lock state and checksums for it are written first. JAR and POM are generated here, nothing is
# downloaded. The dependency policy (privacyCheck) does not run in these counterprobes, so the group needs no
# allowlist entry.
use_probe_lib() {
  local version dir
  for version in 1.0 2.0; do
    dir="probe-repo/de/uchilab/probe/probe-lib/$version"
    mkdir -p "$dir/content"
    echo "probe-lib $version" >"$dir/content/version.txt"
    "${JAVA_HOME:+$JAVA_HOME/bin/}jar" --create --file "$dir/probe-lib-$version.jar" -C "$dir/content" .
    rm -rf "$dir/content"
    cat >"$dir/probe-lib-$version.pom" <<POM
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>de.uchilab.probe</groupId>
  <artifactId>probe-lib</artifactId>
  <version>$version</version>
</project>
POM
  done
  replace settings.gradle.kts "    repositories {
        google()
        mavenCentral()
    }
}

rootProject" "    repositories {
        google()
        mavenCentral()
        maven {
            url = uri(\"probe-repo\")
            content { includeGroup(\"de.uchilab.probe\") }
        }
    }
}

rootProject"
  replace app/build.gradle.kts "    implementation(libs.androidx.compose.ui)
" "    implementation(libs.androidx.compose.ui)
    implementation(\"de.uchilab.probe:probe-lib:1.0\")
"
  # Lock state and checksums are only written where the project has them; the counterprobes must not set up
  # the mechanism they check. Without locking, --write-locks writes nothing and 04a stays green (= fails).
  # --write-verification-metadata would create a missing file, so it only runs if the file exists.
  expect_green "write lock state for probe-lib 1.0" ./gradlew "${GRADLE_ARGS[@]}" --dependency-verification lenient :app:dependencies --write-locks
  if [ -f gradle/verification-metadata.xml ]; then
    expect_green "write checksums for probe-lib 1.0" ./gradlew "${GRADLE_ARGS[@]}" --write-verification-metadata sha256 :app:dependencies
    grep -q 'name="probe-lib-1.0.jar"' gradle/verification-metadata.xml \
      || { echo "COUNTERPROBE BROKEN: probe-lib 1.0 has no checksum" >&2; exit 2; }
  else
    echo "note: gradle/verification-metadata.xml missing, no checksums written"
  fi
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

# write_probe_source <statement>: writes app/src/main/kotlin/de/uchilab/todo/Probe.kt with the statement on
# line 6, inside a function with a Context parameter. privacyCheck does not compile, but the file is valid
# Kotlin, so the counterprobe does not depend on that detail.
write_probe_source() {
  local file=app/src/main/kotlin/de/uchilab/todo/Probe.kt
  [ -d "$(dirname "$file")" ] || { echo "COUNTERPROBE BROKEN: $(dirname "$file") not found" >&2; exit 2; }
  cat >"$file" <<KOTLIN
package de.uchilab.todo

import android.content.Context

fun probe(context: Context) {
    $1
}
KOTLIN
}
