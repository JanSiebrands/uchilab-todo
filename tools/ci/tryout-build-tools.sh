#!/usr/bin/env bash
# Prints the newest Android build-tools directory (aapt2, apksigner, zipalign). Shared by the tryout scripts.
set -euo pipefail
: "${ANDROID_HOME:?ANDROID_HOME is not set}"
dir="$ANDROID_HOME/build-tools/$(ls "$ANDROID_HOME/build-tools" | sort -V | tail -n 1)"
[ -x "$dir/apksigner" ] || { echo "tryout: no build-tools found in $ANDROID_HOME" >&2; exit 1; }
echo "$dir"
