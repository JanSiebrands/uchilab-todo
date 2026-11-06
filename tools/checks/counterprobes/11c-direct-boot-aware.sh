#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"
make_copy
# Security P2 (D3): a component (not only the application) runs before the first unlock.
replace app/src/main/AndroidManifest.xml "            android:name=\".ui.MainActivity\"
" "            android:name=\".ui.MainActivity\"
            android:directBootAware=\"true\"
"
expect_red "directBootAware on MainActivity" "android:directBootAware=\"true\" is not allowed on activity de.uchilab.todo.ui.MainActivity" \
  ./gradlew "${GRADLE_ARGS[@]}" privacyCheck
