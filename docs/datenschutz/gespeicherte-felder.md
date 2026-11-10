# Gespeicherte Felder (DSGVO V2, V2b, P4)

Doku, keine Prüfung, Pflege bei jeder Änderung an gespeicherten Daten: Ändert ein PR das Room-Schema unter `app/schemas/` **oder** eine Einstellung, zieht er diese Liste im selben PR nach. Ändern nur nach Rückfrage bei DSGVO (V7). Seit UCH-124 gibt es dafür keine Pipeline-Prüfung mehr (ADR 0002, Entscheidung 10), vorher stand die Liste in `tools/checks/stored-fields.txt`.

Ein Feld je Zeile als `tabelle.spalte`, genau wie im Room-Schema:

```
task.id
task.title
task.note
task.created_at
task.status
```

## Einstellungen

Eine Zeile je gespeichertem Wert als `<domain>:<datei>/<schlüssel>`, wobei `<domain>` die Backup-Domain ist. Datei und Schlüssel stehen im Code genau so (`SharedPreferencesListSettings.FILE_NAME`, `KEY_HIDE_DONE`), der Abgleich im Review ist ein `git grep`.

```
sharedpref:list_settings.xml/hide_done
```

`hide_done` ist ein `Boolean` (erledigte Aufgaben ausgeblendet), ohne Zeitstempel und ohne Zähler (V2a). Quelle: S7, UCH-159, UCH-162.
