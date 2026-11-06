#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"
make_copy
# Point 6: reference to the data extraction rules removed from the manifest (default = backup on).
replace app/src/main/AndroidManifest.xml "android:dataExtractionRules=\"@xml/data_extraction_rules\"" ""
expect_red "missing dataExtractionRules reference" "android:dataExtractionRules must reference an @xml resource (found: missing)" \
  ./gradlew "${GRADLE_ARGS[@]}" privacyCheck
