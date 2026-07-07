#!/usr/bin/env bash
# Textur-/Modell-Varianten in die Live-Assets kopieren.
#
#   ./apply.sh                          -> Gruppen + Varianten auflisten
#   ./apply.sh cat-station A-gemuetliches-holz
#   ./apply.sh axolotl-armor original   -> zurueck zum alten Stand
#
# Danach normal bauen/deployen (./gradlew build). Die Sets sind vollstaendig —
# ein apply ueberschreibt alle betroffenen Texturen UND Modelle der Gruppe.
set -euo pipefail
cd "$(dirname "$0")"

if [ $# -lt 2 ]; then
    echo "Verfuegbare Varianten:"
    for g in cat-station axolotl-station cat-armor axolotl-armor; do
        [ -d "$g" ] || continue
        echo "  $g:"
        for v in "$g"/*/; do
            echo "    $(basename "$v")"
        done
    done
    echo
    echo "Usage: ./apply.sh <gruppe> <variante>"
    exit 0
fi

GROUP="$1"
VAR="$2"
SRC="$GROUP/$VAR/assets"
if [ ! -d "$SRC" ]; then
    echo "Unbekannte Variante: $GROUP/$VAR" >&2
    echo "Vorhanden in $GROUP:" >&2
    ls "$GROUP" >&2
    exit 1
fi

cp -r "$SRC/." ../src/main/resources/assets/
echo "OK: $GROUP/$VAR nach src/main/resources/assets/ kopiert."
echo "Jetzt ./gradlew build und Jar deployen."
