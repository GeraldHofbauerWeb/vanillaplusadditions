# Doku-Audit, 2026-09-20

Eine Datei je Modul. Jede enthält, was ein Prüf-Agent an der zugehörigen Seite unter
`docs/modules/` auszusetzen hatte — **nachdem** ein anderer Agent sie geschrieben hatte und ohne
dass irgendetwas davon schon behoben wäre.

Aufbau pro Befund:

| Feld | Bedeutung |
|---|---|
| `quote` | der beanstandete Satz oder die Tabellenzelle, wörtlich aus der Seite |
| `issue` | warum er falsch oder unbelegt ist, mit Datei und Zeile |
| `correction` | was stattdessen dort stehen sollte |
| `severity` | `wrong` · `unprovable` · `misleading` · `style` |

Dazu `sourceBugs`: was den Prüfern **im Java selbst** aufgefallen ist. Diese Liste ist getrennt
zusammengefasst in [`../source-findings-2026-09-20.md`](../source-findings-2026-09-20.md).

## Warum das hier liegt und nicht nur im Journal

Die Prüfläufe sind teuer. Ein Abbruch mitten in der Kette — davon gab es mehrere — hätte die
Befunde sonst verschluckt. So kann eine Reparaturrunde die Dateien einfach einlesen, ohne dass
noch einmal geprüft werden muss.

**Stand:** 47 von 48 Seiten. `dispenser_bucket_guard` fehlt, weil diese Seite von Hand geschrieben
und beim Schreiben verifiziert wurde.

Wer einen Befund abarbeitet: **erst selbst am Quelltext nachprüfen.** Die Stichproben waren
zuverlässig, aber ein Prüfer kann irren — in der Pilotrunde hat ein Reparatur-Agent begründet
Einspruch gegen zwei Vorwürfe erhoben und behielt recht.
