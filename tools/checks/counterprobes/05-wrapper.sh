#!/usr/bin/env bash
# Point 5: manipulated wrapper JAR and missing distribution checksum must make wrapper-check.sh fail.
source "$(dirname "$0")/lib.sh"
make_copy
# The check comes from the tree under test, so against an old commit without it this counterprobe fails.
check="$src/tools/checks/wrapper-check.sh"
expect_green "unchanged wrapper" "$check" "$work"
cp gradle/wrapper/gradle-wrapper.jar "$work/jar.orig"
printf '\0' >> gradle/wrapper/gradle-wrapper.jar
expect_red "wrapper JAR with one appended byte" "gradle-wrapper.jar does not match the official checksum" "$check" "$work"
cp "$work/jar.orig" gradle/wrapper/gradle-wrapper.jar
sed -i '/^distributionSha256Sum=/d' gradle/wrapper/gradle-wrapper.properties
expect_red "missing distributionSha256Sum" "distributionSha256Sum missing" "$check" "$work"
