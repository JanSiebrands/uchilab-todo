#!/usr/bin/env bash
# Counterprobe of the task privacyCheck (UCH-116 R1b): the unit tests of ManifestCheck, BackupRulesCheck and
# SourceCheck show that each rule turns red; this script shows that all three rule classes are wired into the
# task. One copy with three defects, one run, all three messages. Job counterprobes, only with the path filter.
source "$(dirname "$0")/lib.sh"
make_copy
replace app/src/main/AndroidManifest.xml "    <application" "    <uses-permission android:name=\"android.permission.READ_CONTACTS\" />
    <application"
perl -0pi -e "s|<cloud-backup>.*?</cloud-backup>|<cloud-backup />|s" app/src/main/res/xml/data_extraction_rules.xml
grep -q "<cloud-backup />" app/src/main/res/xml/data_extraction_rules.xml \
  || { echo "COUNTERPROBE BROKEN: <cloud-backup> not replaced" >&2; exit 2; }
cat >app/src/main/kotlin/de/uchilab/todo/Probe.kt <<'KOTLIN'
package de.uchilab.todo

import android.content.Context

fun probe(context: Context) {
    android.util.Log.d("x", "y")
}
KOTLIN
expect_red "privacyCheck with three defects" "Permission not on the allowlist: android.permission.READ_CONTACTS
data extraction rules <cloud-backup>: domain file is not excluded
[source] app/src/main/kotlin/de/uchilab/todo/Probe.kt:6: android.util.Log is not allowed" \
  ./gradlew "${GRADLE_ARGS[@]}" privacyCheck
