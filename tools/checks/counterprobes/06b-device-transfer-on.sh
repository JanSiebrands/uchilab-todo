#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"
make_copy
# Point 6: device transfer switched on by removing <device-transfer> (missing section = default = on).
perl -0pi -e "s|\\s*<device-transfer>.*?</device-transfer>||s" app/src/main/res/xml/data_extraction_rules.xml
! grep -q "device-transfer" app/src/main/res/xml/data_extraction_rules.xml \
  || { echo "COUNTERPROBE BROKEN: <device-transfer> not removed" >&2; exit 2; }
expect_red "device transfer on" "<device-transfer> is missing" ./gradlew "${GRADLE_ARGS[@]}" privacyCheck
