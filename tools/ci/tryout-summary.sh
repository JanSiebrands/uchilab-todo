#!/usr/bin/env bash
# Writes the German summary of the job tryout-apk to $GITHUB_STEP_SUMMARY (after the upload).
# Needs NAME, URL, SHA256, FINGERPRINT, KEY (new|reused), MIN_SDK from the previous steps.
set -euo pipefail
for v in NAME URL SHA256 FINGERPRINT KEY MIN_SDK; do
  [ -n "${!v:-}" ] || { echo "tryout-summary: $v is empty" >&2; exit 1; }
done
if [ "$KEY" = reused ]; then
  key_text="Schlüssel wiederverwendet: Die APK lässt sich über eine ältere APK aus diesem Pull Request installieren, die Daten bleiben erhalten."
else
  key_text="**Neuer Schlüssel: alte Version vorher deinstallieren.** Eine ältere APK aus diesem Pull Request (oder aus einem anderen) hat eine andere Signatur."
fi
cat >>"${GITHUB_STEP_SUMMARY:-/dev/stdout}" <<MD
## APK zum Ausprobieren

- Download: [$NAME]($URL) (30 Tage verfügbar)
- SHA-256 der APK: \`$SHA256\`
- Fingerabdruck des Zertifikats (SHA-256): \`$FINGERPRINT\`
- $key_text
- Release-Build mit R8, auf einem Emulator mit Android-API $MIN_SDK installiert, aktualisiert und gestartet.
- Nur für erfundene Testaufgaben, kein Store-Build. Installation: siehe README, Abschnitt „APK zum Ausprobieren“.
MD
