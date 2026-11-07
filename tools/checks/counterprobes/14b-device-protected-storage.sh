#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"
make_copy
# Security P6 (D3), DSGVO P3 (V1): device protected (Direct Boot) storage.
write_probe_source "context.createDeviceProtectedStorageContext()"
expect_red "createDeviceProtectedStorageContext in app/src/main" "Probe.kt:6: createDeviceProtectedStorageContext is not allowed" \
  ./gradlew "${GRADLE_ARGS[@]}" privacyCheck
