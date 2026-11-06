#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"
make_copy
# Point 6: cloud backup switched on by emptying <cloud-backup> (an empty section backs up everything).
perl -0pi -e "s|<cloud-backup>.*?</cloud-backup>|<cloud-backup />|s" app/src/main/res/xml/data_extraction_rules.xml
grep -q "<cloud-backup />" app/src/main/res/xml/data_extraction_rules.xml \
  || { echo "COUNTERPROBE BROKEN: <cloud-backup> not replaced" >&2; exit 2; }
expect_red "cloud backup on" "data extraction rules <cloud-backup>: domain file is not excluded" \
  ./gradlew "${GRADLE_ARGS[@]}" privacyCheck
