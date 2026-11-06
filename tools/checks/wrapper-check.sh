#!/usr/bin/env bash
# Validates the Gradle wrapper before any ./gradlew call (point 5):
# 1. gradle-wrapper.jar must match the official checksum published by Gradle for the version in
#    distributionUrl (a modified JAR would run arbitrary code in the pipeline);
# 2. distributionSha256Sum must be set and match the official checksum of the distribution.
# Usage: wrapper-check.sh [project dir]   (default: this repository)
set -euo pipefail
dir="${1:-$(cd "$(dirname "$0")/../.." && pwd)}"
props="$dir/gradle/wrapper/gradle-wrapper.properties"
jar="$dir/gradle/wrapper/gradle-wrapper.jar"

url="$(sed -n 's/^distributionUrl=//p' "$props" | sed 's/\\:/:/g')"
dist="$(basename "$url")"                                  # gradle-9.8.2-bin.zip
version="$(echo "$dist" | sed -E 's/^gradle-(.+)-(bin|all)\.zip$/\1/')"
case "$url" in https://services.gradle.org/distributions/gradle-*) ;; *)
  echo "wrapper-check: distributionUrl is not an official Gradle distribution: $url" >&2; exit 1 ;; esac

official_jar="$(curl -fsSL "https://services.gradle.org/distributions/gradle-${version}-wrapper.jar.sha256")"
actual_jar="$(sha256sum "$jar" | cut -d' ' -f1)"
if [ "$actual_jar" != "$official_jar" ]; then
  echo "wrapper-check: gradle-wrapper.jar does not match the official checksum of Gradle $version" >&2
  echo "  expected $official_jar, found $actual_jar" >&2
  exit 1
fi

pinned="$(sed -n 's/^distributionSha256Sum=//p' "$props")"
if [ -z "$pinned" ]; then
  echo "wrapper-check: distributionSha256Sum missing in gradle-wrapper.properties" >&2
  exit 1
fi
official_dist="$(curl -fsSL "${url}.sha256")"
if [ "$pinned" != "$official_dist" ]; then
  echo "wrapper-check: distributionSha256Sum does not match the official checksum of $dist" >&2
  exit 1
fi
echo "wrapper-check: OK (Gradle $version, wrapper JAR and distribution checksum verified)"
