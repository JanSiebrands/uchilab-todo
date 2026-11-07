#!/usr/bin/env bash
# Sourced helper. sign_if_unsigned <apk> <tmp-dir>: prints the path of an installable APK. An unsigned APK
# (release build with R8) is signed in <tmp-dir> with a freshly generated throwaway key; the release build
# type itself stays unchanged and no secret is involved.
sign_if_unsigned() {
  local apk="$1" tmp="$2" build_tools
  build_tools="$ANDROID_HOME/build-tools/$(ls "$ANDROID_HOME/build-tools" | sort -V | tail -n 1)"
  if "$build_tools/apksigner" verify "$apk" >/dev/null 2>&1; then echo "$apk"; return; fi
  keytool -genkeypair -keystore "$tmp/throwaway.jks" -storepass throwaway -keypass throwaway -alias throwaway \
    -keyalg RSA -keysize 2048 -validity 1 -dname "CN=throwaway" >/dev/null 2>&1
  "$build_tools/zipalign" -f -p 4 "$apk" "$tmp/aligned.apk"
  "$build_tools/apksigner" sign --ks "$tmp/throwaway.jks" --ks-pass pass:throwaway --out "$tmp/signed.apk" \
    "$tmp/aligned.apk" >&2
  echo "unsigned APK signed with a throwaway key" >&2
  echo "$tmp/signed.apk"
}
