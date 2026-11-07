#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"
make_copy
# Security P3 (E2): a second intent filter (SEND with data) on the launcher activity.
replace app/src/main/AndroidManifest.xml "            </intent-filter>
" "            </intent-filter>
            <intent-filter>
                <action android:name=\"android.intent.action.SEND\" />
                <category android:name=\"android.intent.category.DEFAULT\" />
                <data android:mimeType=\"text/plain\" />
            </intent-filter>
"
expect_red "SEND filter on the launcher activity" "launcher activity must only have the MAIN/LAUNCHER intent filter (Security E2)" \
  ./gradlew "${GRADLE_ARGS[@]}" privacyCheck
