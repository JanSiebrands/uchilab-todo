#!/usr/bin/env bash
# Prints the value for RENOVATE_CUSTOM_ENV_VARIABLES: {"ANDROID_HOME":"<path>"}.
# Renovate passes only a fixed list of variables to ./gradlew; ANDROID_HOME is not on it. Today's Renovate
# Gradle commands do not need the SDK, this is a safeguard for future AGP versions (ADR 0002, point 4).
# Only ANDROID_HOME is passed on, never exposeAllEnv, so RENOVATE_TOKEN never reaches Gradle.
# Exit 1 with a clear message if ANDROID_HOME is unset, empty or not an Android SDK (no platforms/).
set -euo pipefail
sdk="${ANDROID_HOME:-}"
if [ -z "$sdk" ]; then
  echo "ANDROID_HOME is not set; Renovate's ./gradlew would run without the Android SDK." >&2
  exit 1
fi
if [ ! -d "$sdk/platforms" ]; then
  echo "ANDROID_HOME does not point to an Android SDK (no platforms/ directory): $sdk" >&2
  exit 1
fi
jq -cn --arg sdk "$sdk" '{ANDROID_HOME: $sdk}'
