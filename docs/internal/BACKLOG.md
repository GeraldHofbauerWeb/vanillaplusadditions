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

- [x] **`item_vault_viewer`: Inventar per Capability holen statt per Reflection auf
  `itemCapability`** — umgesetzt 2026-09-23. `getInventory` fragt jetzt zuerst
  `level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null)`; die beiden Reflection-Wege
  bleiben als Rückfallebene. **Im Spiel noch nicht gegengeprüft** — die Reproduktion unten
  (frisch geladener Chunk, Vault mit Inhalt, Brille auf, Vault vorher nicht öffnen) steht noch aus.
  Ursprünglicher Eintrag: **`item_vault_viewer`: Inventar per Capability holen statt per Reflection auf
  `itemCapability`** (gefunden 2026-09-19 beim Gegenlesen der Modul-Doku, von mir noch
  **nicht** selbst nachgeprüft): `ItemVaultViewerModule.getInventory` liest das Feld
  `itemCapability` der Create-`ItemVaultBlockEntity` per Reflection
  (`ItemVaultViewerModule.java:251`). Create initialisiert dieses Feld laut Befund aber
  **lazy** — `initCapability()` läuft erst aus dem in `registerCapabilities` registrierten
  Capability-Lookup heraus. Wurde das Vault seit dem Laden noch von niemandem über die
  Capability angefasst, ist das Feld `null`; unser Code fängt das zwar ab
  (`if (capabilityProvider != null)`), zeigt dann aber stillschweigend nichts an, statt den
  Inhalt zu liefern. Vorschlag aus dem Befund: erst
  `level.getCapability(Capabilities.ItemHandler.BLOCK, controllerPos, null)` abfragen — das
  löst `initCapability()` mit aus — und die Reflection nur als Rückfallebene behalten.
  **Vor dem Umbau im Spiel reproduzieren:** frisch geladener Chunk, Vault mit Inhalt, Brille
  auf, ohne das Vault vorher zu öffnen.

- [x] **Standalone-Jars für die letzten sechs Module** — erledigt (Stand 2026-09-23: alle 50 Module
  haben ein eigenes Jar, geprüft über `hasStandaloneJar` in allen Fakten-Blättern). Ursprünglicher
  Eintrag: **Standalone-Jars für die letzten sechs Module** (Gerry, 2026-09-20, ausgelöst durch
  `tipped_arrows`): Nach der `mob_drops`-Verdrahtung haben **6 von 48** Modulen kein eigenes Jar —
  `enhanced_ai_leader_loot`, `mob_cart_loader`, `pathfinder_quills`, `static_fov`, `tipped_arrows`,
  `waystone_amethyst_repair`. Sie stecken nur im Bundle, und die README-Tabelle weist sie als
  *bundle only* aus. Pro Modul sind es drei Handgriffe: Eintrag in `standaloneModules`
  (`build.gradle`), ein Entrypoint unter `standalone/<modul>/`, und ggf. `dataGlobs` für eigene
  `data/**`-Dateien. Danach `./gradlew moduleJars` und prüfen, dass das Jar ohne das Bundle startet.
  **Vorher klären:** `mob_cart_loader` hat Create-Compat und `pathfinder_quills` erbt von Quarks
  `PathfindersQuillItem` — bei denen ist zu prüfen, ob das Standalone-Jar ohne die fremde Mod
  überhaupt laden kann, oder ob es eine harte Dependency in der generierten `mods.toml` braucht.
  Gerry will mit `tipped_arrows` anfangen.

- [x] **39 Quelltext-Befunde aus dem Doku-Audit abarbeiten** — erledigt 2026-09-23, zusammen mit den
  18 Befunden des Nachtrags. 54 von 57 repariert, die drei übrigen mit Begründung geschlossen
  (Abschnitt „Was offen bleibt" in der Fundliste). Die beiden `chunk_reset`-Verdachtsfälle waren
  beide echt und sind beide behoben: die Dimension steht jetzt im Auftrag, und gelöscht wird erst,
  wenn der Chunk den Speicher verlassen hat. Ursprünglicher Eintrag:
  **39 Quelltext-Befunde aus dem Doku-Audit abarbeiten** (2026-09-20):
  `docs/internal/source-findings-2026-09-20.md`. Beim Gegenlesen der Modulseiten sind den
  Prüf-Agenten Fehler und falsche Kommentare im Java aufgefallen — belegt mit Datei und Zeile,
  aber **nicht** angefasst. Zwei Befunde in `chunk_reset` klingen nach echten Bugs und gehören
  zuerst geprüft: `PendingReset` merkt sich die Dimension nicht (die Bestätigung könnte im
  falschen Level zuschlagen), und die Löschung wird weder entladen noch geflusht, könnte also
  von `ChunkMap.save` überschrieben werden. Jeden Befund vor dem Umsetzen selbst verifizieren.

- [x] **ModuleManager-Lookup laeuft in Standalone-Jars ins Leere** — erledigt in `25325d4`
  (Stand 2026-09-23: kein Modul ruft `ModuleManager.getInstance().getModule(` mehr auf).
  Ursprünglicher Eintrag: **ModuleManager-Lookup laeuft in Standalone-Jars ins Leere**
  (gefunden 2026-09-20 bei der
  Machbarkeitsanalyse der sechs Bundle-only-Module, Mechanismus von mir am Quelltext bestaetigt,
  Auswirkung je Modul **noch nicht** einzeln geprueft):
  `ModuleManager.registeredModules` wird ausschliesslich von `VanillaPlusAdditions.registerModules()`
  befuellt, also nur im Bundle. `StandaloneModuleBootstrap.boot()` umgeht den `ModuleManager`
  **absichtlich** (Javadoc dort, Zeilen 12-24: das Singleton hat einen Einmal-Lebenszyklus, zwei
  Modul-Jars wuerden sich gegenseitig zerlegen) — der Bypass ist also richtig, aber
  `ModuleManager.getInstance().getModule("<id>")` liefert im Standalone-Jar `null`.
  Acht Module mit eigenem Jar tun genau das: `arm_target_overlay`, `block_glow`, `cat_guardian`,
  `end_oxygen`, `freecam_sublevel_noclip`, `item_vault_viewer`, `options`, `texture_kill`.
  Fuer `static_fov` (nur Bundle) hat die Analyse durchgerechnet, dass der Handler dadurch bei jedem
  Event vorzeitig zurueckkehrt, das Modul also vollstaendig wirkungslos waere.
  **Vorgeschlagener Fix:** das Muster aus `MobSpawnOverlayModule` uebernehmen — ein modul-lokales
  `private static X instance`, in `onInitialize()` gesetzt, plus `getInstance()`. Drei Zeilen je
  Modul, kein Eingriff in den gemeinsamen Lebenszyklus. Die Alternative (registrieren im
  Bootstrap) wuerde genau das aufweichen, was das Javadoc bewusst vermeidet.
  **Zuerst pruefen:** je Modul, was der Null-Lookup tatsaechlich kostet — manche Pfade sind
  unkritisch, andere legen das ganze Modul still.

- [x] **Standalone-Entrypoints fuer fuenf Module vorbereitet, aber nicht verdrahtet** — erledigt
  in `25325d4`. Nachtrag 2026-09-23: `conduit_attack_range` hatte dasselbe Problem andersherum —
  ein Eintrag in `standaloneModules` ohne Entrypoint, also ein Jar, das gebaut und veröffentlicht
  wurde und kein Verhalten enthielt. Der Entrypoint ist nachgetragen.
  Ursprünglicher Eintrag: **Standalone-Entrypoints fuer fuenf Module vorbereitet, aber nicht
  verdrahtet** (2026-09-20): `standalone/{enhanced_ai_leader_loot,mob_cart_loader,pathfinder_quills,
  tipped_arrows,waystone_amethyst_repair}/` liegen im Baum, es fehlt je ein Eintrag in
  `standaloneModules` (`build.gradle`) — ohne den wird kein Jar gebaut und es aendert sich nichts.
  Analysen dazu: `build/standalone-analysis/` (nicht versioniert). Offen: `pathfinder_quills`
  braucht moeglicherweise Quark als harte Dependency, und `renderModuleToml` kann bisher **keine**
  Fremd-Mod-Abhaengigkeit in die erzeugte `mods.toml` schreiben. `static_fov` hat bewusst keinen
  Entrypoint bekommen, weil dort erst der Lookup-Fix oben noetig ist.

- [ ] **Abgeleitete Vanilla-Texturen aufloesen** (Gerry, 2026-09-24, Befund aus dem Asset-Audit
  nach beta.92). Alle 194 eigenen Texturen wurden gegen jede gleich grosse Vanilla-Textur gehalten
  und der Anteil *buchstaeblich unveraenderter sichtbarer Pixel* gemessen. Die drei exakten Kopien
  sind in beta.93 erledigt (Modelle verweisen jetzt auf die Vanilla-Textur). Was bleibt:
  * **~19 Stations-Trims bei 96-97 %** — z.B. `axolotl_feeding_station_koralle_rot_trim.png` gegen
    `block/fire_coral_block.png`, `cat_feeding_station_dorf_trim.png` gegen `block/bricks.png`.
    Das sind Umfaerbungen, kein Kopieren. Sauberer Weg: derselbe `SpriteSource`-Mechanismus wie
    beim Weltkompass (siehe `WorldCompassSpriteSource`), dann faellt auch hier jedes fremde Pixel weg.
  * **~10 Skins bei 75-86 %**, gleiche Kategorie.
  * **`gui/cat_inventory.png` und `gui/axolotl_inventory.png`: 74 %** identisch mit Vanillas
    `gui/container/hopper.png`; die beiden Stations-GUIs 54 % mit `gui/container/shulker_box.png`.
  * **`block/chunk_anchor_active_side.png`: 64 %** von `block/lodestone_side.png`.
  * **`item/fire_arrow.png`: 46 %** von `item/arrow.png` — `scripts/gen_fire_arrow_texture.py` sagt
    es selbst: Federn und hinterer Schaft bleiben unveraendert.
  * **Unkritisch:** `end_conduit` (6), Wolf-/Katzen-/Axolotl-Ruestung, `flying_fish*`,
    `end_nautilus` liegen bei **0-1 %** unveraenderten Pixeln — sie teilen nur die Silhouette.
    Die Foxhound-Texturen sind von Quark abgeleitet, **mit Vazkiis ausdruecklicher Erlaubnis**.
  **Methodenwarnung fuer den naechsten Durchgang:** der Anteil muss ueber Pixel gebildet werden, die
  in *mindestens einem* der beiden Bilder sichtbar sind. Sonst melden zwei fast leere Texturen
  94 % Uebereinstimmung (erst passiert mit `entity/phantom_eyes.png`). Verglichen wurde nur gegen
  Vanilla, nicht gegen Create, Quark und die uebrigen Fremd-Mods.
