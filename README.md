# uchilab-todo

Kostenlose Offline-Todo-App für Android (Arbeitstitel, späterer Name: Kerbholz).

## 1. Grundsätze
- Kostenlos, ohne Abo und ohne In-App-Käufe.
- Funktioniert offline, ohne Anmeldung.
- Keine Tracker, keine Werbe-, Analytics- oder Cloud-SDKs, keine Internet-Berechtigung (wird in der Pipeline geprüft).
- Daten bleiben lokal, auch Android Auto Backup ist abgeschaltet.
- Oberfläche auf Deutsch und Englisch.

## 2. Voraussetzungen
- JDK 21
- Android SDK mit Plattform 37 (`platforms;android-37.0`) und Build-Tools; Umgebungsvariable `ANDROID_HOME` gesetzt
- Android Studio ist nicht nötig. Für die Skripte unter `tools/checks`: `bash`, `curl`, `sha256sum`, `tar`.

## 3. Bauen
```
./gradlew assembleDebug
```
Die APK liegt danach unter `app/build/outputs/apk/debug/app-debug.apk` und lässt sich z. B. mit `adb install` installieren.

Release-Build mit R8 (unsigniert, Signierung ist noch nicht Teil des Projekts): `./gradlew assembleRelease`.

Nach dem ersten Download aller Abhängigkeiten funktioniert der Build auch ohne Netz: `./gradlew --offline assembleDebug`. (Robolectric lädt die Android-Laufzeit für die Tests einmalig nach.)

## 4. Testen
```
./gradlew testDebugUnitTest   # Unit- und UI-Tests (Robolectric, ohne Emulator)
./gradlew lint                # Android Lint
./gradlew privacyCheck        # Datenschutz-Checks (siehe 6) inklusive ihrer eigenen Tests
./gradlew assembleRelease r8Check   # R8 ist im Release-Build wirklich aktiv
tools/checks/wrapper-check.sh # gradle-wrapper.jar gegen die offizielle Prüfsumme von Gradle
tools/checks/secret-scan.sh   # Secret-Scan (gitleaks)
tools/checks/vuln-scan.sh     # Schwachstellenprüfung der Abhängigkeiten (osv-scanner, offline)
```
Alles zusammen, wie in der Pipeline: `./gradlew assembleDebug assembleRelease testDebugUnitTest lint r8Check privacyCheck`

**Gegenproben:** `tools/checks/counterprobes/run-all.sh` baut je Prüfung einen Fehler in eine Wegwerf-Kopie des Projekts ein und zeigt am Exit-Code, dass die Prüfung rot wird (bei Lint mit älterer Version: grün bleibt). Der eingecheckte Stand wird nicht verändert. Gegen einen anderen Stand: `COUNTERPROBE_SOURCE=<verzeichnis> tools/checks/counterprobes/run-all.sh`.

Dass 03a, 04a und 04b nach einem Update von `activity-compose` gültig bleiben, zeigt `tools/checks/counterprobes/verify-version-independence.sh [<version>]` (braucht Netz, nicht in `run-all.sh`): Es hebt die Version in einer Kopie auf die angegebene bzw. neueste veröffentlichte, schreibt Lock-Stand und Prüfsummen neu und lässt die drei Gegenproben dagegen laufen.

**App-Start auf dem Emulator:** bei laufendem Emulator `tools/checks/emulator-run.sh` (Debug, Release mit R8, Gegenprobe ohne Launcher-Eintrag). Die unsignierte Release-APK wird dabei mit einem frisch erzeugten Wegwerf-Schlüssel signiert. Den Emulator danach beenden.

## 5. Projektstruktur
```mermaid
graph TD
  UI[ui: Compose, MainActivity, Theme] --> Domain[domain: reine Kotlin-Modelle/Use Cases]
  Data[data: Persistenz, ab S2] --> Domain
  UI -. nie direkt .-> Data
```
```
app/                     Android-App (Kotlin, Jetpack Compose)
  src/main/kotlin/de/uchilab/todo/ui   Oberfläche
  src/test/              Unit- und UI-Tests (JVM)
gradle/                  Version Catalog, Wrapper, verification-metadata.xml
tools/checks/            Datenschutz-Checks, Secret-Scan, Schwachstellen-Scan
docs/adr/                Architekturentscheidungen
.github/workflows/       Pipeline
```
Die Domain kennt weder Android noch Compose; `domain` und `data` entstehen ab S2.

## 6. Datenschutz-Checks
`./gradlew privacyCheck` prüft die zusammengeführten Manifeste (Debug und Release) und alle Abhängigkeiten:
- keine Berechtigung `INTERNET` oder `ACCESS_NETWORK_STATE`, kein Klartext-Verkehr,
- `allowBackup="false"`,
- keine exportierten Komponenten außer der Start-Activity,
- jede Berechtigung muss in `tools/checks/permissions-allowlist.txt` stehen (auch über Abhängigkeiten eingeschleppte); `INTERNET` und `ACCESS_NETWORK_STATE` bleiben auch dann verboten,
- Backup und Transfer aus: das Manifest verweist auf `dataExtractionRules` und `fullBackupContent`, beide Dateien existieren, `<cloud-backup>`, `<device-transfer>` und `<cross-platform-transfer platform="ios">` sind vorhanden, alle neun Bereiche sind ausgeschlossen, kein `<include>`,
- jede Abhängigkeit muss auf der Allowlist stehen (`tools/checks/allowlist.txt` für die App, `tools/checks/allowlist-test.txt` zusätzlich für Tests). Bekannte Tracker stehen in `denylist.txt` und führen zu einer klaren Fehlermeldung.

**Neue Abhängigkeit aufnehmen:** Gruppe (oder `gruppe:artefakt`) bewusst in die passende Allowlist eintragen. Die Änderung wird im Pull Request geprüft. Danach `./gradlew :app:dependencies --write-locks` und `./gradlew --write-verification-metadata sha256 assembleDebug assembleRelease testDebugUnitTest lint` ausführen und die geänderten Dateien einchecken.

Die Checks sind selbst getestet, inklusive Negativtests (`tools/checks/src/test`, `secret-scan-selftest.sh`, `vuln-scan-selftest.sh`).

## 7. Pipeline
Bei jedem Pull Request, Push auf `main` und täglich auf `main` läuft GitHub Actions (`.github/workflows/ci.yml`), ohne Secrets:
1. Wrapper-Prüfung, dann Build (Debug), Release-Build mit R8, Unit- und UI-Tests, Lint, `r8Check`, `privacyCheck`
2. Gegenproben (`tools/checks/counterprobes/run-all.sh`)
3. App-Start auf dem Emulator als Debug und als Release mit R8, mit Gegenprobe ohne Launcher-Eintrag
4. Secret-Scan (Arbeitsbaum und Git-Historie)
5. Schwachstellenprüfung der Abhängigkeiten

Lint meldet keine neueren Versionen (die Pipeline wird ohne Codeänderung nicht rot). Neue Versionen kommen als Pull Request von Renovate (`.github/workflows/renovate.yml`, wöchentlich, nur auf `main`, Lockfile und `verification-metadata.xml` im selben PR). Update-PRs werden nie automatisch gemergt. Renovate nutzt das Token des Bot-Kontos `uchilabbot` aus dem Repo-Secret `RENOVATE_TOKEN` (Entscheidung von Jan: keine GitHub App, damit keine zweite Identität Pull Requests freigeben kann). Einen Lauf von Hand startest du mit `gh workflow run renovate.yml --ref main`.

Renovate gibt `./gradlew` nur ausgewählte Umgebungsvariablen weiter. `ANDROID_HOME` reicht der Workflow ausdrücklich über `RENOVATE_CUSTOM_ENV_VARIABLES` durch (`tools/checks/renovate-android-home.sh`). Die heutigen Gradle-Befehle von Renovate brauchen das SDK nicht, das ist eine Absicherung für künftige AGP-Versionen. Andere Variablen, vor allem `RENOVATE_TOKEN`, gelangen nicht zu Gradle (kein `exposeAllEnv`). Mehr in ADR 0002, Punkt 4.

**Rote Läufe abfragen:** `tools/ci/main-run-status.sh` fragt mit `gh run list` (nur lesend) den jüngsten abgeschlossenen Lauf von `ci.yml` und `renovate.yml` auf `main` ab. Abgebrochene und übersprungene Läufe zählen nicht. Ausgabe je Workflow eine Zeile `GREEN|RED <workflow> <url> since=<datum> count=<n>` (`since`/`count`: seit wann und wie oft in Folge, höchstens über die letzten 20 Läufe), bei einem Fehler `ERROR <workflow> <grund>`. Exit-Code `0` alles grün, `1` mindestens ein Workflow rot, `2` Abfrage gescheitert oder kein abgeschlossener Lauf (hat Vorrang, die übrigen Zeilen stehen trotzdem in der Ausgabe). Der PO ruft das Skript täglich im Abgleich der Pull Requests auf und legt bei Rot oder Fehler ein Paperclip-Issue an. Andere Branches oder Workflows: `--branch <b> --workflow <datei>`. `ci.yml` lässt sich mit `gh workflow run ci.yml --ref <branch>` von Hand starten.

Schlägt ein Schritt fehl, ist der Check rot. Alle Werkzeuge laufen im Runner, es werden keine Projektdaten an Dritte gesendet. Mehr dazu in `docs/adr/0001-technologie.md` und `docs/adr/0002-pipeline-pruefungen.md`.

## 8. Beitragen
- Code, Commits und Bezeichner auf Englisch, Doku und README auf Deutsch.
- Testgetrieben: erst Tests, dann Umsetzung.
- Lizenz: siehe `LICENSE`.
