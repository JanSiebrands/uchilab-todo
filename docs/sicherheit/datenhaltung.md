<!-- Quelle: Paperclip-Dokument `vorgaben` in UCH-40 (Security), Stand 2026-11-06. Volltext, unverändert übernommen. -->
# Security-Vorgaben: gespeicherte Daten der Todo-App

Stand: 2026-11-06, Security. Gilt ab S2 ([UCH-31](/UCH/issues/UCH-31)) für alle Stories, die Aufgaben speichern, lesen, anzeigen oder weitergeben.

**Grundlage, gelesen:** Beschreibung von UCH-40 und UCH-38, `app/src/main/AndroidManifest.xml`, `res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml`, `tools/checks/src/main/kotlin/de/uchilab/checks/ManifestCheck.kt`, `tools/checks/*list*.txt`, `tools/checks/counterprobes/`, `docs/adr/0001-technologie.md`, `docs/adr/0002-pipeline-pruefungen.md` (Stand `main` und Branch `feature/UCH-16-s1-grundgeruest`). Eine Spec und ein Plan für S2 lagen noch nicht als Dokument vor. Ausgeführt habe ich nichts, die Vorgaben stützen sich auf das Gelesene.

**Schutzbedarf:** Titel und Notiz sind Freitext. Nutzer schreiben dort auch Persönliches (Gesundheit, Termine, Namen anderer). Wir behandeln jede Aufgabe deshalb wie personenbezogene Daten mit normalem Schutzbedarf. Bedrohungen, gegen die wir schützen: andere Apps auf dem Gerät, Weitergabe über Backup, Gerätetransfer und Logs, ein verlorenes, gesperrtes Gerät. Nicht schützen können und wollen wir gegen jemanden, der das entsperrte Gerät in der Hand hat oder es gerootet hat.

Jede Vorgabe hat eine Kennung, eine Begründung und sagt, wer sie prüft: **Pipeline** (mit Gegenprobe, Story an CI/CD, siehe Abschnitt 6), **Test** (Unit-Test in S2, prüft der Test-Reviewer) oder **Review** (Architekt und bei Anlass Security).

## 1. Ablage auf dem Gerät

**D1 Nur im app-privaten internen Speicher.** Aufgaben liegen ausschließlich in der Datenbank der App unter `Context.getDatabasePath(...)` (Room/SQLite, Ordner `databases/`). Keine Kopie in `filesDir`, `cacheDir`, SharedPreferences oder DataStore.
*Warum:* Den Ordner kann auf einem nicht gerooteten Gerät nur die App selbst lesen. Eine einzige Ablage hält Backup-Ausschluss und spätere Löschung überschaubar. Prüft: Review, ergänzend Pipeline P6.

**D2 Kein externer oder geteilter Speicher.** Verboten in `app/src/main`: `getExternalFilesDir`, `getExternalCacheDir`, `getExternalMediaDirs`, `Environment.getExternalStorage*`, `MediaStore`, `MODE_WORLD_READABLE`, `MODE_WORLD_WRITEABLE`.
*Warum:* Externer Speicher ist für andere Apps, per USB und teils für Backups erreichbar. Prüft: Pipeline P6. Berechtigungen für Speicher verhindert bereits die vorhandene Allowlist der Berechtigungen.

**D3 Kein geräteverschlüsselter Speicher (Direct Boot).** Kein `createDeviceProtectedStorageContext`, kein `android:directBootAware="true"`.
*Warum:* Der normale Speicher ist bis zum ersten Entsperren nach dem Neustart mit dem Schlüssel des Nutzers verschlüsselt. Der Direct-Boot-Speicher wäre schon vor dem Entsperren lesbar. Die Todo-App braucht vor dem Entsperren nichts. Prüft: Pipeline P6 (Code) und P2 (Manifest).

**D4 Keine zusätzliche Verschlüsselung in der App.** Kein SQLCipher, kein `androidx.security:security-crypto`, kein eigener Schlüssel im Keystore für die Datenbank.
*Warum:* Android verschlüsselt den app-privaten Speicher selbst (Dateiverschlüsselung, Pflicht für neue Geräte ab Android 10; auf Geräten mit Android 8 und 9 bis auf Ausnahmen ohne Sperrbildschirm Voll- oder Dateiverschlüsselung). Eine Verschlüsselung in der App bringt gegen unsere Bedrohungen nichts dazu: Wer das Gerät entsperrt hat, startet die App und sieht die Aufgaben. Wer Root hat, kommt auch an den Schlüssel im laufenden Prozess. Dafür kostet sie eine native Abhängigkeit außerhalb von `androidx.*` (SQLCipher) oder eine abgekündigte Bibliothek (`security-crypto`), und ein verlorener Keystore-Schlüssel (z. B. nach Änderung der Displaysperre auf manchen Geräten) heißt: alle Aufgaben weg. Restrisiko, bewusst getragen: alte Geräte ohne Sperrbildschirm und ohne Verschlüsselung. Wer das wieder aufmachen will, braucht ein neues Security-Review. Prüft: Review, Abhängigkeiten über Abschnitt 7.

**D5 Längen im Domain-Layer erzwingen.** Titel höchstens 200, Notiz höchstens 2000 Zeichen werden vor dem Speichern in `domain` geprüft, nicht nur im Eingabefeld.
*Warum:* Die Oberfläche ist nicht der einzige Weg zur Datenbank (spätere Stories, Import). Ohne Grenze kann ein Fehler die Datenbank unbegrenzt wachsen lassen. Prüft: Test (Grenzwerte 200/201 und 2000/2001).

**D6 Nur parametrisierte Abfragen.** Abfragen nur über Room-Annotationen (`@Query` mit `:parameter`, `@Insert`, `@Update`, `@Delete`). Kein `@RawQuery`, kein `rawQuery`, kein `SimpleSQLiteQuery` und kein `execSQL` mit Text, der aus Nutzereingaben zusammengesetzt ist. `execSQL` mit festem Text in späteren Migrationen ist erlaubt.
*Warum:* Verhindert SQL-Injection über Titel und Notiz. Prüft: Review.

**D7 Testdaten sind erfunden.** Tests, Screenshots und Beispiel-Datenbanken enthalten nur erfundene Aufgaben, keine echten Namen, Adressen oder Gesundheitsangaben.
*Warum:* Firmenregel, und Screenshots landen im Pull Request. Prüft: Review.

## 2. Android Auto Backup und Gerätetransfer

**B1 Die Regeln aus S1 reichen für die Datenablage aus D1, unter diesen Bedingungen:**
- Die Datenbank liegt in der Domain `database` (Standard bei Room mit `Room.databaseBuilder(context, ...)` und einfachem Dateinamen ohne Pfad). Die Neben-Dateien `-wal`, `-shm` und `-journal` liegen im selben Ordner. `backup_rules.xml` und `data_extraction_rules.xml` schließen alle neun Domains aus, inklusive `database` und `device_database`, für Cloud-Backup, Gerätetransfer und Transfer zu iOS. `BackupRulesCheck` und die Gegenproben 06a bis 06f sichern das bereits ab.
- `android:allowBackup` bleibt `false` (prüft `ManifestCheck`).
*Warum mehr nicht nötig ist:* Die Regeln sind domainweit und nicht an Dateinamen gebunden. Eine neue Datei in einer der neun Domains fällt automatisch unter den Ausschluss.

**B2 Kein eigener Backup-Agent.** Kein `android:backupAgent` im gemergten Manifest, keine Unterklasse von `BackupAgent` oder `BackupAgentHelper`, kein `BackupManager.dataChanged()`.
*Warum:* Ein eigener Agent könnte Daten selbst in ein Backup schreiben und an den Regeln vorbeiführen. Prüft: Pipeline P2 (Manifest).

**B3 Kein `android:hasFragileUserData="true"`.**
*Warum:* Die Option bietet beim Deinstallieren an, die Daten zu behalten. „Deinstallieren löscht alles“ soll ohne Ausnahme gelten, das erwartet auch die Datenschutzerklärung. Prüft: Pipeline P2.

**B4 Folge für Nutzer (keine Security-Vorgabe, zur Kenntnis):** Bei Gerätewechsel und Neuinstallation sind die Aufgaben weg. Ein Export zum Mitnehmen ist eine eigene Story und braucht vorher ein Security-Review (Abschnitt 4, E4).

## 3. Logs, Absturzberichte, Fehlermeldungen

**L1 Kein Inhalt einer Aufgabe in Logs.** Titel und Notiz dürfen nie in Logcat, `System.out`/`System.err`, Dateien oder Absturzberichte gelangen, auch nicht gekürzt, gehasht oder im Debug-Build.
*Warum:* Logcat landet in Fehlerberichten (`adb bugreport`), die Nutzer an Fremde schicken. Debug-Builds gehen als APK an Jan zum Ausprobieren, eine Ausnahme für Debug würde dort genau mit echten Daten greifen.

**L2 Die App loggt vorerst gar nicht.** Verboten in `app/src/main`: `android.util.Log` (alle Stufen), `println`, `print`, `System.out`, `System.err`, `printStackTrace()`, Timber oder andere Logging-Bibliotheken, Room `setQueryCallback` (gibt SQL samt Parameterwerten aus).
*Warum:* Ein generelles Verbot ist prüfbar, eine Regel „loggen ja, aber ohne Inhalt“ ist es nicht. Die App ist offline und hat niemanden, der Logs liest. Braucht eine spätere Story doch Logs, ist das ein Anlass für ein Security-Review. Prüft: Pipeline P4.

**L3 Fehlertexte von Ausnahmen enthalten keine Eingaben.** Eigene Ausnahmen und `require`/`check`-Meldungen nennen höchstens Feld und Grenze (`"title longer than 200"`), nie den Wert (`"title too long: $title"`).
*Warum:* Nicht abgefangene Ausnahmen schreibt Android mit Meldung in Logcat, auch ohne eigenes Logging. Prüft: Test für die Validierung aus D5 (die Meldung enthält den eingegebenen Text nicht), sonst Review.

**L4 `toString()` der Modelle gibt keinen Inhalt aus.** Klassen, die Titel oder Notiz halten (Domain-Modell, Room-Entity), überschreiben `toString()` so, dass höchstens Id und Status erscheinen.
*Warum:* `data class` erzeugt ein `toString()` mit allen Feldern. Das landet über Meldungen von Ausnahmen, Compose-Fehler oder Test-Ausgaben schnell in Logs. Prüft: Test (`toString()` enthält Titel und Notiz eines Beispiels nicht).

**L5 Fehlermeldungen an Nutzer sind feste Texte.** Die Oberfläche zeigt bei Fehlern nur String-Ressourcen, nie `exception.message`, Stacktraces oder SQL.
*Warum:* Meldungen von SQLite und Room können Interna und Werte enthalten, und sie erscheinen sonst in Screenshots. Prüft: Review.

**L6 Kein Absturzbericht nach außen.** Kein eigener `UncaughtExceptionHandler`, der Berichte speichert oder verschickt, keine Crash-Reporting-Bibliothek. Die Denylist und die fehlende Internet-Berechtigung decken die bekannten Dienste ab. Prüft: Review, Pipeline über vorhandene Denylist und Berechtigungs-Prüfung.

## 4. Exportierte Komponenten und Freigabe an andere Apps

**E1 Nur die Launcher-Aktivität ist exportiert.** Gilt weiter wie in S1 (`ManifestCheck`): kein exportierter `provider`, `service`, `receiver`, keine weitere `activity`. Nicht exportierte Komponenten aus Bibliotheken (z. B. `androidx.startup.InitializationProvider`) sind erlaubt.
*Warum:* Jede exportierte Komponente ist eine Schnittstelle, über die andere Apps Daten lesen oder schreiben können. Prüft: Pipeline (vorhanden). **Lücke:** Für exportierte Komponenten gibt es noch keine Gegenprobe in `tools/checks/counterprobes/`. Die kommt mit P3.

**E2 Die Launcher-Aktivität nimmt nur `MAIN`/`LAUNCHER` an.** Kein weiterer `intent-filter` (kein `SEND`, `VIEW`, Deep Link), und die App liest keine Extras oder Daten aus dem Start-Intent, um Aufgaben anzulegen oder anzuzeigen.
*Warum:* Die Aktivität ist exportiert, jede App kann sie starten. Solange sie nichts aus dem Intent übernimmt, kann eine fremde App darüber keine Aufgaben einschleusen oder auslesen. Prüft: Pipeline P3 (Manifest), Review (Code).

**E3 Kein Content Provider für Aufgaben, kein `FileProvider`, kein `grantUriPermissions`.**
*Warum:* In diesem Feature gibt es nichts zu teilen. Prüft: Pipeline (exportierte Provider, vorhanden), Review.

**E4 Teilen, Export und Import sind eigene Stories mit Security-Review vorab.** Dazu gehören Teilen per `ACTION_SEND`, Datei-Export über das Storage Access Framework, Widgets, Benachrichtigungen mit Aufgabeninhalt, App-Shortcuts und Kacheln.
*Warum:* Dort verlassen Aufgaben die App bzw. erscheinen auf dem Sperrbildschirm. Prüft: PO legt dafür die Security-Stufe fest.

**E5 Kein `FLAG_SECURE`, keine Sonderbehandlung der Tastatur (Entscheidung).** Bildschirmfotos und die Vorschau in „Zuletzt verwendet“ bleiben möglich, die Tastatur darf wie üblich lernen.
*Warum:* Das Schutzniveau einer Todo-Liste rechtfertigt es nicht, Nutzern Bildschirmfotos zu verbieten. Die Pull Requests brauchen Screenshots. Die Vorschau sieht nur, wer das Gerät entsperrt hat.

## 5. Release-Build

**R1 Der Release-Build ist nicht `debuggable`.** Im gemergten Release-Manifest steht kein `android:debuggable="true"`, `isDebuggable` ist im Release-Build-Typ nicht gesetzt.
*Warum:* Bei einem debuggable Build kann jeder mit USB-Zugang per `adb shell run-as` die Datenbank auslesen, auch ohne Root. Prüft: Pipeline P1.

**R2 R8 bleibt an** (vorhanden, `r8Check`).

**R3 Hinweis zur APK zum Ausprobieren (S2a):** Ein Debug-Build ist per `run-as` auslesbar (siehe R1). Für Jans Test mit erfundenen Aufgaben ist das in Ordnung. Wer die App mit echten Aufgaben nutzt, braucht einen Release-Build. Ein dauerhafter Signierschlüssel wäre ein Secret und damit eine Entscheidung von Jan.

## 6. Prüfungen für die Pipeline (Story an CI/CD)

Alle Prüfungen laufen auf den gemergten Manifesten (Debug und Release, außer P1) bzw. auf `app/src/main`, mit je mindestens einer Gegenprobe in `tools/checks/counterprobes/` nach dem Muster von ADR 0002: Der Fehler wird in einer Wegwerf-Kopie eingebaut, die Prüfung muss rot werden **und** die erwartete Meldung ausgeben, der Exit-Code entscheidet. Wie geprüft wird (Erweiterung von `ManifestCheck`, eigenes Skript, Lint-Regel), entscheiden Architekt und CI/CD.

| Nr. | Prüfung | Vorgabe | Gegenprobe (mindestens) |
|---|---|---|---|
| P1 | Release-Manifest hat kein `android:debuggable="true"` | R1 | `isDebuggable = true` im Release-Build-Typ |
| P2 | Kein `android:backupAgent`, kein `hasFragileUserData="true"`, kein `directBootAware="true"` (an `application` und Komponenten) | B2, B3, D3 | je eine: `backupAgent` gesetzt, `hasFragileUserData="true"`, `directBootAware="true"` |
| P3 | Exportierte Komponenten: Gegenproben für die vorhandene Prüfung; neu: die Launcher-Aktivität hat nur einen `intent-filter` mit `MAIN`/`LAUNCHER` | E1, E2, E3 | exportierter `provider`; exportierter `service`; zweiter `intent-filter` mit `android.intent.action.SEND` an `MainActivity` |
| P4 | Keine Logging-Aufrufe in `app/src/main` (Liste aus L2) | L1, L2 | `Log.d(...)` in einer Datei in `app/src/main`; `println(...)`; `setQueryCallback` |
| P5 | Backup-Regeln (vorhanden, `BackupRulesCheck`, Gegenproben 06a bis 06f) | B1 | vorhanden, keine neue nötig |
| P6 | Keine API für externen, geteilten oder Direct-Boot-Speicher in `app/src/main` (Liste aus D2 und D3) | D2, D3 | `getExternalFilesDir(null)`; `createDeviceProtectedStorageContext()` |

Für P4 und P6 gilt: Die Prüfung sucht nach Aufrufen im Quelltext von `app/src/main`, nicht in Tests. Treffer in Kommentaren dürfen die Prüfung rot machen, ein Ausnahme-Mechanismus ist nicht vorgesehen. Wird später eine Ausnahme nötig, ist das ein Anlass für Security.

Nicht von der Pipeline geprüft, bewusst: D4, D6, L3 bis L5 und E2 im Code (nicht zuverlässig maschinell erkennbar, Test oder Review). Die Prüfungen P1 bis P6 sind kein Muss vor S2: S2 kann parallel laufen, weil Test und Review dieselben Vorgaben abdecken. Sie müssen vor dem Merge des Feature-Pull-Requests auf `main` grün sein.

## 7. Abhängigkeiten für die Datenhaltung: wann Security prüft

Anlass für ein Security-Review ist jede der folgenden Änderungen. PO bzw. Architekt setzen dann die Stufe Security in die `executionPolicy` der Story.

- **A1** Eine neue Bibliothek im Runtime-Classpath der App, die Daten speichert, verschlüsselt, serialisiert, synchronisiert oder überträgt, auch innerhalb von `androidx.*`. Beispiele: `androidx.room`, `androidx.sqlite`, `androidx.datastore`, `androidx.security`, `kotlinx.serialization`, `androidx.work`.
  *Warum:* Die Allowlist lässt `androidx.*` und `org.jetbrains.*` ohne Änderung durch, sie sieht solche Bibliotheken nicht.
- **A2** Eine neue Gruppe in `tools/checks/allowlist.txt` oder `allowlist-test.txt` (immer, unabhängig vom Zweck).
- **A3** Ein neues Gradle-Plugin oder ein neuer Annotation-Prozessor bzw. Compiler-Plugin (z. B. KSP für Room).
  *Warum:* Läuft beim Build mit vollem Zugriff auf Runner und Quelltext.
- **A4** Eine Bibliothek mit nativem Code (`.so` in der APK).
- **A5** Eine Bibliothek, die dem gemergten Manifest eine Komponente (`provider`, `service`, `receiver`), eine Berechtigung oder ein Backup-Attribut hinzufügt, auch nicht exportiert. Exportierte Komponenten und Berechtigungen fängt die Pipeline ab, nicht exportierte nicht.
- **Kein Anlass:** Versions-Updates von Renovate für schon geprüfte Bibliotheken, solange die Pipeline grün ist (Berechtigungen, exportierte Komponenten, Schwachstellen-Scan und Prüfsummen greifen dort).

**Für S2 heißt das:** Room (A1) und KSP (A3) sind ein Anlass. S2 braucht eine Review-Stufe Security nach dem Review des Architekten. Ich prüfe dort gegen diese Vorgaben und den Plan.

## 8. Ablage im Repo

Diese Vorgaben kommen mit S2 als `docs/sicherheit/datenhaltung.md` ins Repo, unverändert oder mit Verweis auf dieses Dokument. Weichen spätere Stories davon ab, ändert Security das Dokument, nicht die umsetzende Story.

