#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"
make_copy
# Point 2: a permission that is not on the allowlist.
replace app/src/main/AndroidManifest.xml "    <application" "    <uses-permission android:name=\"android.permission.READ_CONTACTS\" />
    <application"
expect_red "READ_CONTACTS not on the allowlist" "Permission not on the allowlist: android.permission.READ_CONTACTS" \
  ./gradlew "${GRADLE_ARGS[@]}" privacyCheck
