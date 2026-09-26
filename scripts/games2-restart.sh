#!/usr/bin/env bash
#
# games2-restart.sh — Neustart von games2 mit Vorwarnung im Spiel.
#
# Warum ein Skript und kein `docker restart`:
#   AMP ENTFERNT den Container beim Stoppen. Nach einem `docker stop` gibt es
#   AMP_SebsModpackv401 nicht mehr, `docker start` laeuft ins Leere, und der Weg
#   zurueck fuehrt ausschliesslich ueber die ADS-Ebene (ADSModule/StartInstance).
#   Das hat uns am 2026-09-25 einen Server-Ausfall gekostet.
#
#   Beide ADS-Methoden wollen den INSTANZNAMEN (InstanceName), nicht die GUID -
#   mit InstanceID antwortet AMP mit HTTP 200 und {"Title":"Missing Field"}, also
#   einem Fehler, der wie ein Erfolg aussieht, wenn man die Antwort wegwirft.
#
# Der Countdown ist der eigentliche Zweck: Sebi und Gerry spielen auf einem
# Live-Server, ein Neustart ohne Vorwarnung reisst sie mitten aus dem Spiel.
#
# Aufruf:
#   scripts/games2-restart.sh                # 5 Sekunden Countdown
#   scripts/games2-restart.sh --countdown 30 # laengere Vorwarnung
#   scripts/games2-restart.sh --stop-only    # nur anhalten (z.B. fuer Chunk-Arbeit)
#   scripts/games2-restart.sh --start-only   # nur starten
#   scripts/games2-restart.sh --reason "Update auf beta.95"
#
set -euo pipefail

INSTANCE_NAME="SebsModpackv401"
AMP="$HOME/.claude/scripts/amp"
ADS="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/games2-ads.py"

COUNTDOWN=5
REASON="Neustart"
DO_STOP=1
DO_START=1

while [[ $# -gt 0 ]]; do
  case "$1" in
    --countdown) COUNTDOWN="$2"; shift 2 ;;
    --reason)    REASON="$2";    shift 2 ;;
    --stop-only)  DO_START=0; shift ;;
    --start-only) DO_STOP=0;  shift ;;
    -h|--help) sed -n '2,30p' "${BASH_SOURCE[0]}"; exit 0 ;;
    *) echo "!! unbekannt: $1" >&2; exit 1 ;;
  esac
done

say() { YES=1 "$AMP" cmd "say $1" >/dev/null 2>&1 || true; }

if [[ "$DO_STOP" == 1 ]]; then
  echo "==> Vorwarnung im Spiel ($COUNTDOWN s): $REASON"
  say "§e[$REASON] Server startet in $COUNTDOWN Sekunden neu."
  # Ab 5 Sekunden im Sekundentakt - vorher nur bei jedem fuenften, damit der Chat
  # bei einer langen Vorwarnung nicht zugespammt wird.
  for ((s = COUNTDOWN; s > 0; s--)); do
    if (( s <= 5 || s % 5 == 0 )); then
      say "§e[$REASON] Neustart in $s ..."
      echo "    $s"
    fi
    sleep 1
  done
  say "§c[$REASON] Server wird jetzt neu gestartet."

  echo "==> Welt speichern"
  YES=1 "$AMP" cmd "save-all flush" 2>&1 | grep -iE "saved|saving" | tail -2 || true
  sleep 3

  # Die Inode des laufenden Logs merken: Minecraft legt latest.log beim Start NEU an, also ist ein
  # Inode-Wechsel das einzige verlaessliche Zeichen, dass wir den neuen Lauf sehen. Auf den Inhalt
  # oder das mtime zu warten geht schief - das alte Log wird bis zur letzten Sekunde beschrieben.
  OLD_LOG_INODE="$(ssh gerry@82.165.95.152 'docker exec AMP_SebsModpackv401 stat -c %i /AMP/Minecraft/logs/latest.log 2>/dev/null' || echo none)"
  echo "==> Instanz anhalten"
  out="$(python3 "$ADS" stop "$INSTANCE_NAME")"
  if grep -q '"Title"' <<<"$out"; then echo "!! Stopp abgelehnt:"; echo "$out" | head -4; exit 1; fi
  for _ in $(seq 1 30); do
    python3 "$ADS" list | grep -q "$INSTANCE_NAME .*running=False" && break
    sleep 3
  done
  echo "    angehalten"
fi

if [[ "$DO_START" == 1 ]]; then
  echo "==> Instanz starten"
  # Antwort NICHT wegwerfen: AMP meldet Fehler mit HTTP 200 und einem Title-Feld.
  out="$(python3 "$ADS" start "$INSTANCE_NAME")"
  if grep -q '"Title"' <<<"$out"; then echo "!! Start abgelehnt:"; echo "$out" | head -4; exit 1; fi
  echo "==> Warten auf 'Done (' im Log ..."
  ssh gerry@82.165.95.152 '
    OLD="'"${OLD_LOG_INODE:-none}"'"
    for i in $(seq 1 120); do
      NEW=$(docker exec AMP_SebsModpackv401 stat -c %i /AMP/Minecraft/logs/latest.log 2>/dev/null || echo none)
      if [ "$NEW" != "none" ] && [ "$NEW" != "$OLD" ]; then
        if docker exec AMP_SebsModpackv401 sh -c "grep -q \"Done (\" /AMP/Minecraft/logs/latest.log" 2>/dev/null; then break; fi
      fi
      sleep 5
    done
    docker exec AMP_SebsModpackv401 sh -c "
      L=/AMP/Minecraft/logs/latest.log
      echo \"    Version: \$(ls /AMP/Minecraft/mods/ | grep -i vanillaplus)\"
      echo \"    Start:   \$(head -1 \$L | cut -c2-21)\"
      echo \"    \$(grep -E \"Done \\(\" \$L | tail -1 | cut -c1-80)\"
      echo \"    Datapack-Fehler: \$(grep -ci \"Errors in the currently selected data\" \$L)\"
    "
  '
  echo "    oben"
fi
