#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"
make_copy
# Security P3 (E1): an exported content provider.
replace app/src/main/AndroidManifest.xml "    </application>" "        <provider android:name=\"de.uchilab.todo.ProbeProvider\" android:authorities=\"de.uchilab.todo.probe\" android:exported=\"true\" />
    </application>"
expect_red "exported provider" "Exported provider not allowed: de.uchilab.todo.ProbeProvider" \
  ./gradlew "${GRADLE_ARGS[@]}" privacyCheck
