# Backlog

Ideen/Aufträge, die noch nicht eingeplant sind. Beim Umsetzen: Eintrag in ein Modul-Doc
überführen und hier abhaken.

- [x] **Create Water-Wheel-Unstucker** (Gerry, 2026-07-07): ~~Entities befreien~~ — der
  ursprüngliche Eintrag beschrieb das Problem falsch. Tatsächlicher Zweck (Gerry,
  2026-07-08): Create-Wasserräder drehen sich manchmal nicht mehr, nachdem ihr Chunk
  entladen und wieder geladen wurde (Kinetik-Desync). Umgesetzt in beta.39 als Modul
  `create_water_wheel_unstucker`: Positionen werden beim Chunk-Load und bei Platzierungen
  gemerkt, periodischer Check nur über diese Positionen, stehende Räder werden per
  Soft-Kick (FlowScore-Neuberechnung) bzw. Hard-Kick (Kinetik detach/re-attach) wieder
  angeworfen.
