# Gespeicherte Felder (DSGVO V2, P4)

Doku, keine Prüfung, Pflege bei jeder Schema-Änderung: Ändert ein PR das Room-Schema unter `app/schemas/`, zieht er diese Liste im selben PR nach. Ändern nur nach Rückfrage bei DSGVO (V7). Seit UCH-124 gibt es dafür keine Pipeline-Prüfung mehr (ADR 0002, Entscheidung 10), vorher stand die Liste in `tools/checks/stored-fields.txt`.

Ein Feld je Zeile als `tabelle.spalte`, genau wie im Room-Schema:

```
task.id
task.title
task.note
task.created_at
task.status
```
