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

- [ ] **`item_vault_viewer`: Inventar per Capability holen statt per Reflection auf
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

- [ ] **Standalone-Jars für die letzten sechs Module** (Gerry, 2026-09-20, ausgelöst durch
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

- [ ] **39 Quelltext-Befunde aus dem Doku-Audit abarbeiten** (2026-09-20):
  `docs/internal/source-findings-2026-09-20.md`. Beim Gegenlesen der Modulseiten sind den
  Prüf-Agenten Fehler und falsche Kommentare im Java aufgefallen — belegt mit Datei und Zeile,
  aber **nicht** angefasst. Zwei Befunde in `chunk_reset` klingen nach echten Bugs und gehören
  zuerst geprüft: `PendingReset` merkt sich die Dimension nicht (die Bestätigung könnte im
  falschen Level zuschlagen), und die Löschung wird weder entladen noch geflusht, könnte also
  von `ChunkMap.save` überschrieben werden. Jeden Befund vor dem Umsetzen selbst verifizieren.

- [ ] **ModuleManager-Lookup laeuft in Standalone-Jars ins Leere** (gefunden 2026-09-20 bei der
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

- [ ] **Standalone-Entrypoints fuer fuenf Module vorbereitet, aber nicht verdrahtet**
  (2026-09-20): `standalone/{enhanced_ai_leader_loot,mob_cart_loader,pathfinder_quills,
  tipped_arrows,waystone_amethyst_repair}/` liegen im Baum, es fehlt je ein Eintrag in
  `standaloneModules` (`build.gradle`) — ohne den wird kein Jar gebaut und es aendert sich nichts.
  Analysen dazu: `build/standalone-analysis/` (nicht versioniert). Offen: `pathfinder_quills`
  braucht moeglicherweise Quark als harte Dependency, und `renderModuleToml` kann bisher **keine**
  Fremd-Mod-Abhaengigkeit in die erzeugte `mods.toml` schreiben. `static_fov` hat bewusst keinen
  Entrypoint bekommen, weil dort erst der Lookup-Fix oben noetig ist.
