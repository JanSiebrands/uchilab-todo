#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"
make_copy
# Renovate's ./gradlew gets ANDROID_HOME only via RENOVATE_CUSTOM_ENV_VARIABLES, built by
# tools/checks/renovate-android-home.sh. Without a usable SDK path the step must fail, never pass on
# an empty or wrong value.
script=tools/checks/renovate-android-home.sh
[ -d "${ANDROID_HOME:-}/platforms" ] \
  || { echo "COUNTERPROBE BROKEN: needs a real Android SDK in ANDROID_HOME" >&2; exit 2; }
expect_red "ANDROID_HOME unset" "ANDROID_HOME is not set" env -u ANDROID_HOME "$script"
expect_red "ANDROID_HOME empty" "ANDROID_HOME is not set" env ANDROID_HOME= "$script"
empty_sdk="$(mktemp -d -p "$work")"
expect_red "ANDROID_HOME without SDK" "no platforms/ directory" env ANDROID_HOME="$empty_sdk" "$script"
# Positive case: valid JSON with exactly the key ANDROID_HOME and the real path as value.
check_json() {
  local out
  out="$("$script")"
  jq -e --arg sdk "$ANDROID_HOME" 'keys == ["ANDROID_HOME"] and .ANDROID_HOME == $sdk' <<<"$out" >/dev/null
}
expect_green "real SDK gives JSON with only ANDROID_HOME" check_json
