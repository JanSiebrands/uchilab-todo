#!/usr/bin/env bash
# Workflow step of renovate.yml: writes RENOVATE_CUSTOM_ENV_VARIABLES={"ANDROID_HOME":"…"} to $GITHUB_ENV.
# The value is assigned first, so the step fails if tools/checks/renovate-android-home.sh fails
# (a command substitution inside the arguments of echo would not stop bash -e). Nothing is written then.
# Checked by counterprobe 08.
set -euo pipefail
root="$(cd "$(dirname "$0")/../.." && pwd)"
: "${GITHUB_ENV:?GITHUB_ENV is not set}"
value="$("$root/tools/checks/renovate-android-home.sh")"
echo "RENOVATE_CUSTOM_ENV_VARIABLES=$value" >>"$GITHUB_ENV"
