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

# The workflow step itself (tools/ci/set-renovate-android-home.sh, called by renovate.yml) must fail without
# an SDK and must not write an empty value to GITHUB_ENV. Run with bash -e like GitHub Actions does.
step=tools/ci/set-renovate-android-home.sh
grep -qF "run: $step" .github/workflows/renovate.yml \
  || { echo "COUNTERPROBE BROKEN: renovate.yml does not call $step" >&2; exit 2; }
step_without_sdk() {
  local env_file code=0
  env_file="$(mktemp -p "$work")"
  env -u ANDROID_HOME GITHUB_ENV="$env_file" bash -e "$step" || code=$?
  [ ! -s "$env_file" ] || { echo "step wrote to GITHUB_ENV: $(cat "$env_file")"; return 0; }
  return "$code"
}
expect_red "workflow step without ANDROID_HOME" "ANDROID_HOME is not set" step_without_sdk
step_with_sdk() {
  local env_file
  env_file="$(mktemp -p "$work")"
  GITHUB_ENV="$env_file" bash -e "$step"
  [ "$(cat "$env_file")" = "RENOVATE_CUSTOM_ENV_VARIABLES=$(jq -cn --arg sdk "$ANDROID_HOME" '{ANDROID_HOME: $sdk}')" ]
}
expect_green "workflow step with real SDK writes the JSON to GITHUB_ENV" step_with_sdk
