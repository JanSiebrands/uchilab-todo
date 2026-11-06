#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"
make_copy
# Security P2 (B3): the app asks to keep its data on uninstall.
replace app/src/main/AndroidManifest.xml "    <application
" "    <application
        android:hasFragileUserData=\"true\"
"
expect_red "hasFragileUserData on application" "android:hasFragileUserData=\"true\" is not allowed on application" \
  ./gradlew "${GRADLE_ARGS[@]}" privacyCheck
