#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"
make_copy
# Security P3 (E1): an exported service.
replace app/src/main/AndroidManifest.xml "    </application>" "        <service android:name=\"de.uchilab.todo.ProbeService\" android:exported=\"true\" />
    </application>"
expect_red "exported service" "Exported service not allowed: de.uchilab.todo.ProbeService" \
  ./gradlew "${GRADLE_ARGS[@]}" privacyCheck
