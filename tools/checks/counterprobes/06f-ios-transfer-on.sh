#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"
make_copy
# Point 6a: transfer to iOS switched on by removing <cross-platform-transfer platform="ios">
# (missing section = default = on).
perl -0pi -e 's|\s*<cross-platform-transfer platform="ios">.*?</cross-platform-transfer>||s' \
  app/src/main/res/xml/data_extraction_rules.xml
! grep -q "cross-platform-transfer" app/src/main/res/xml/data_extraction_rules.xml \
  || { echo "COUNTERPROBE BROKEN: <cross-platform-transfer> not removed" >&2; exit 2; }
expect_red "transfer to iOS on" "<cross-platform-transfer platform=\"ios\"> is missing" ./gradlew "${GRADLE_ARGS[@]}" privacyCheck
