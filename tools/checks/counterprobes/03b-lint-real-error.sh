#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"
make_copy
# Point 3: lint still finds real errors: a string without German translation.
replace app/src/main/res/values/strings.xml "</resources>" "    <string name=\"counterprobe_untranslated\">Only English</string>
</resources>"
expect_red "string without German translation" "MissingTranslation" ./gradlew "${GRADLE_ARGS[@]}" lint
