# Changelog
All notable changes to VanillaPlusAdditions will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0-beta.27] - 2026-07-03
### Changed
- **Cat Guardian: Katzen-Inventar liegt jetzt auf einem Modifier-Keybind (Default: Strg)**: Ein **normaler** Rechtsklick auf die eigene Katze löst wieder die **Vanilla-Aktion** (Sitzen/Stehen) aus — das Inventar-GUI kapert plain Klicks nicht mehr. Zum Öffnen des Katzen-Inventars **Modifier halten + Rechtsklick** (Default **Strg/Ctrl**, im Steuerungs-Menü frei belegbar, auch auf eine Maustaste). Shift+Rechtsklick bleibt bei Carry On. Rüstung equipt weiterhin per Rechtsklick mit Cat-Armor in der Hand.

## [1.0.0-beta.26] - 2026-07-03
### Added
- **Free Anvil Repair: konfigurierbare Zusatz-Materialien (Quark-Style)**: Über die neue Config-Liste `extra_repair_materials` (Format `item=material`) lassen sich Repair-Kombis definieren, die Vanilla nicht kennt — und sie sind ebenso **gratis** wie normale Material-Repairs. Defaults: **Netherite-Gear mit Diamanten** (alle Werkzeuge + Rüstung) sowie **Creates Diving-Gear** (Netherite-Diving mit Diamanten, Copper-Diving mit Kupfer). Einträge für nicht installierte Items/Mods werden still übersprungen. Da das Modul die Reparatur selbst berechnet, greift Quarks eigener Diamant-Repair (der XP kostet) nicht mehr — unsere Variante ist kostenlos. (Netherite- und Copper-Diving werden mit ihrem Basismaterial ohnehin schon vom regulären Material-Pfad kostenlos repariert; die Copper-Einträge sind explizite Absicherung.)

## [1.0.0-beta.25] - 2026-07-03
### Changed
- **Lib-Mods (Create/Sable/ToughAsNails/BlueMap) jetzt wirklich optional**: Das Bundle lädt und läuft ohne die Modpack-Mods. Fixes: `cat_guardian` referenziert Sable nur noch über die neue `SableCatBlocks`-Factory (vorher crashte die Modul-Klasse beim **Linken** ohne Sable — Bytecode-Verifier lädt die Sable-Subklassen wegen Type-Join in den Registrierungs-Lambdas; ein `isLoaded()`-Check kann das nicht verhindern). `end_oxygen` nutzt Create-Backtanks nur noch via `CreateBacktankCompat` (ohne Create: normales Luftanhalten im End). `arm_target_overlay`-/`debug_overlay`-Client-Handler gaten auf `isLoaded("create")` (Goggles-Check fällt auf den `arm_goggles`-Tag zurück). `create` ist jetzt als optionale Dependency in der mods.toml deklariert. (`item_vault_viewer` war bereits sauber isoliert.) Verifiziert: Bundle bootet auf blankem NeoForge-Server ohne Lib-Mods; End-Atmung, Cat-Guardian-Blöcke (Vanilla-Varianten) und Rezept-Degradation laufen sauber.

## [1.0.0-beta.24] - 2026-07-03
### Added
- **Free Anvil Repair Modul**: Reine Reparaturen am Amboss kosten **keine XP-Level** mehr — sowohl Material-Repair (z. B. Diamantspitzhacke + Diamanten) als auch das Kombinieren zweier gleicher Items, solange das Opfer-Item **unverzaubert** ist. Verzauberungs-Kombis, Bücher und Umbenennen kosten weiterhin Vanilla-XP. Auch Items, die durch die Prior-Work-Penalty schon „Zu teuer!" waren, sind wieder reparierbar; die Penalty steigt bei Gratis-Repairs standardmäßig **nicht** mehr an (Config: `increase_prior_work_penalty`, dazu `free_material_repair` / `free_combine_repair`). Standalone-Jar: `vpa_free_anvil_repair` (eigener Mixin für die Cost-0-Entnahme).

## [1.0.0-beta.23] - 2026-07-01
### Added
- **Standalone-Modul-Jars für ALLE Module**: Der Pilot (beta.22) ist jetzt auf **alle 24 registrierten Module** ausgerollt. Die Pipeline baut `vpa_core` + je ein `vpa_<modul>`-Jar. Neu unterstützt: **Cross-Modul-Deps** (z. B. `vpa_cat_guardian` braucht `vpa_debug_overlay` + `vpa_flying_fish`; die Chunk-Loader brauchen `vpa_debug_overlay`) und **Mixins pro Modul** (`vpa_cat_guardian`, `vpa_bluemap_signs` bringen ihre eigene `mixins.json` mit). Data-Files (Loot/Biome/Tags/Damage-Type) werden dem jeweiligen Modul-Jar zugeordnet; Assets liegen zentral in `vpa_core`. (`mob_drops` ist bewusst ausgenommen — im Bundle nicht registriert. Der globale Worldgen-Crash-Guard bleibt Bundle-only.)
### Changed
- **`@EventBusSubscriber(modid = …)` in Modul-Client-Handlern** entfällt (nutzt jetzt die Default-modId der ladenden Jar). Verhalten im Bundle unverändert; nötig, damit Client-Handler (Overlays, Keybinds, Renderer) auch in den Standalone-Jars registriert werden. Analog referenzieren `block_glow`/`texture_kill`/`arm_target_overlay` den Logger über `core.Vpa` statt die `@Mod`-Klasse.

## [1.0.0-beta.22] - 2026-07-01
### Added
- **Standalone-Modul-Jars (Build-System, Pilot)**: Die GitHub-Pipeline erzeugt jetzt **zusätzlich** zum gebündelten Jar pro (Pilot-)Modul ein eigenständiges Mod-Jar plus ein gemeinsames `vpa_core`-Jar (Framework + Assets). So lassen sich einzelne Features standalone laden — `vpa_core` + gewünschte `vpa_<modul>`-Jars (Pilot: `flying_fish`, `death_coordinates`, `idle_gamerules`). Die Modul-Jars deklarieren `vpa_core` als Pflicht-Dependency und sind mit dem All-in-one-Bundle als `incompatible` markiert (nie zusammen laden). Das Bundle bleibt unverändert das primäre Artefakt; Releases/CI-Artefakte enthalten beides.

## [1.0.0-beta.21] - 2026-06-28
### Added
- **BlueMap Signs Modul**: `[bm]`-Schilder werden zu kuratierten BlueMap-Markern mit eigenen Icon-Keys (34 Pins); Verwaltung via `/bmsigns` (list/add/addat/edit/remove/help). Server-seitig, optional (nur aktiv wenn BlueMap installiert; Klassen-isoliert über `BlueMapAPI`).
- **Idle Gamerules Modul**: pausiert `doDaylightCycle`, `doWeatherCycle` und `doSeasonCycle`, solange kein Spieler online ist, und schaltet sie beim ersten Join wieder ein.
- **Chunk Anchor** (Stationary Chunk Loader): platzierbarer Block, der die umliegenden Chunks force-loaded (persistent), plus Fixes am Minecart-Chunk-Loader.

## [1.0.0-beta.19] - 2026-06-26
### Added
- **Minecart Chunk Loading**: `only_while_players_online` (Default **true**) — Force-Loading pausiert, sobald kein Spieler mehr online ist. Die Rail-Chunks mit aktiven Carts werden **persistent** gespeichert (`SavedData`) und beim **Server-Start / ersten Join** wieder geladen, sodass stehengebliebene Carts weiterfahren. (`false` = lädt auch bei 0 Spielern, z.B. für Endlosschleifen.)

## [1.0.0-beta.18] - 2026-06-26
### Changed
- **Minecart Chunk Loading**: Chunk-Loader-Rail-**Rezept** (8 Powered Rails + 1 Enderperle → 8 Rails) und **Block-Drop** jetzt im **Modul-Code** statt als JSON-Datapack (JSON lädt in diesem Mod nicht zuverlässig). Das Rezept ist craftbar, solange das Modul aktiv ist.
- **Konvention dokumentiert** (CLAUDE.md / docs): eigene Rezepte im jeweiligen Modul registrieren, Vanilla-/Fremdmod-Erweiterungen in `CustomCraftingRecipesConfig`-Defaults; Block-Drops via `getDrops`-Override; keine Recipe-/Loot-JSONs mehr.
### Removed
- `chunk_loader_rail` Recipe- und Loot-Table-JSONs (durch Code ersetzt).

## [1.0.0-beta.17] - 2026-06-26
### Changed
- **Minecart Chunk Loading**: Chunk-Border-Debug-Overlay transparenter gemacht — vor allem das Rot der aktuell geladenen Chunks (Linien- und Füll-Alpha reduziert).

## [1.0.0-beta.16] - 2026-06-26
### Added
- **✨ Minecart Chunk Loading Modul (neu)**: Neue craftbare **Chunk Loader Rail** (Detector-Rail-Optik, rot→blau eingefärbt, 3D-Modell passt sich VanillaTweaks an). Fährt ein Minecart darüber, werden die umliegenden Chunks force-geladen (NeoForge `TicketController`, ticking), sodass Carts nicht mehr an Chunk-Grenzen stehenbleiben. Release nach Timeout, nichts Persistentes (clean slate bei Neustart). Konfigurierbar: Lade-Radius (Default **1** = 3×3), Aktiv-Timeout. Rezept: 8 Powered Rails + 1 Enderperle → 8 Loader Rails.
- **✨ Debug-Overlay-Framework (neu)**: Allgemeines, erweiterbares Client-Overlay-System (`debug_overlay`-Modul) — zentraler Toggle auf **Numpad +**, Goggles-Gate (Create Engineer's Goggles / `arm_goggles`-Tag), Registry für Debug-Renderer, geteilte Render-Helfer (xray + depth-getestet). Das Cat-Guardian-Overlay teilt sich jetzt diesen Toggle/Keybind.
- **Chunk-Border-Renderer**: Mit Goggles + Toggle werden alle Chunks mit Loader-Rail dauerhaft umrandet — **blau** (Rail vorhanden) bzw. **rot** (gerade geladen), depth-getestet (von Blöcken verdeckt).
### Changed
- **Custom Crafting Recipes**: `chunk_loader_rail`-Rezept + Loot-Table in den korrekten 1.21-Singular-Ordnern (`recipe/`, `loot_table/`).
### Removed
- Nicht ladende `cooked_flying_fish`-JSONs (Plural-Ordner) bereits in beta.15 entfernt.

## [1.0.0-beta.15] - 2026-06-26
### Added
- **Custom Crafting Recipes Modul**: Faire Rail-Upgrade-Rezepte als Defaults — normale Rails lassen sich zu Powered/Detector/Activator Rails aufwerten (Vanilla-Layout, Rails ersetzen den Metallrahmen). Powered: 6 Rails + 1 Gold + 1 Redstone; Detector: 6 Rails + 1 Stone Pressure Plate + 1 Redstone; Activator: 6 Rails + 2 Sticks + 1 Redstone Torch (Ausbeute je 6).
### Removed
- **Flying Fish Modul**: Nicht ladende `cooked_flying_fish`-Smelting/Smoking/Campfire-JSONs entfernt. Sie lagen im Pre-1.21-Plural-Ordner `recipes/` (seit 1.21 = `recipe/`) und wurden in 1.21.1 nie geladen; Entfernung verhindert Duplicate-ID-Kollisionen bei späterer JSON-Migration. TODO zur Umstellung auf echte JSON-Datapacks in `docs/custom_crafting_recipes.md` vermerkt.

## [1.0.0-beta.14] - 2026-06-17
### Added
- **Item Vault Viewer Modul**: Neues Create-ItemVault-Viewer-Modul mit Read-only Grid-Ansicht, vollständiger Vault-Inhaltsanzeige (ein Stack pro Item-Typ, auch >64), Suche und Sortierung nach Stackgröße.
### Changed
- **Item Vault Viewer Modul**: UI-Polish für bessere Lesbarkeit (breiteres Interface, Footer-Controls, präzise Pagination-Anzeige, dynamische Count-Schriftgröße ab 100/1000).
- **Item Vault Viewer Modul**: Interaktion verfeinert — Öffnen mit Goggles cancelt das Click-Event, Shift-Click öffnet den Viewer nie.

## [1.0.0-beta.12] - 2026-06-17
### Fixed
- **Cat Guardian Modul**: Alle zahmen Katzen folgen dem Spieler nicht mehr, wenn das CatGuardianModule aktiv ist. Der `CatGuardianFollowMixin` blockiert jetzt `FollowOwnerGoal` für alle Katzen (nicht nur Wächterkatzen), um stabiles Züchten mit Katzen zu ermöglichen (standing spawn beim Breeding).
- **Cat Guardian Modul**: Kisten können jetzt geöffnet werden, auch wenn eine Katze darauf sitzt. Der `ChestBlockCatMixin` wurde angepasst, um Katzen nicht länger zu blockieren.

## [1.0.0-beta.11] - 2026-06-16
### Added
- **Cat Guardian Modul**: Katzen streicheln statt schlagen — leerer Hand-Linksklick auf jede Katze (auch nicht-zahme) cancelt den Angriff komplett und spielt stattdessen Schnurr-Sound + Herzpartikel ab.
- **Cat Guardian Modul**: Katzen mit Fisch füttern — Rechtsklick auf eine zahme Katze mit Fisch in der Hand (vanilla `#minecraft:fishes` sowie unsere Flying Fish, roh/gekocht) verbraucht 1 Fisch, heilt die Katze voll und füllt bei Wächterkatzen den Sättigungstimer (`fed_duration_ticks`) auf das Maximum auf.
- **Cat Guardian Modul**: Feeding Station gibt jetzt ein Redstone-Comparator-Signal proportional zur Anzahl assoziierter Katzen aus (`floor(katzen * 15 / max_cats_per_station)`, voll = Signalstärke 15).
- Dokumentation für 8 bisher undokumentierte Module ergänzt: Arm Target Overlay, Block Glow, Chunk Reset, Custom Crafting Recipes, End Oxygen, Mob Drops, Overpacked Slowdown Override, Texture Kill (`docs/`).
### Fixed
- **Cat Guardian Modul**: Wächterkatzen tauchen jetzt zuverlässig zu Unterwasser-Zielen ab. Der `createNavigation`-Mixin zielte fälschlich auf `Cat` statt `Mob` (Cat überschreibt die Methode nicht, daher griff der Mixin nie); neue `CatAmphibiousNavigation` erlaubt korrektes Unterwasser-Pathfinding bei weiterhin gültigem `GroundPathNavigation`-Typ für `FollowOwnerGoal`. `FloatGoal` wird während aktiver Navigation unterdrückt, damit Katzen nicht ständig an die Oberfläche gedrückt werden.
- **Cat Guardian Modul**: Oberflächen-Katapult beim Auftauchen behoben (Schwimmstatus wird jetzt im `EntityTickEvent.Pre` vor `travel()` zurückgesetzt) und Tauchgeschwindigkeit von 0.18 auf 0.08 reduziert.
### Changed
- **Cat Guardian Modul**: Katzen-Statistik-Overlay (Goggles) ist jetzt halbtransparent (Alpha 0xF0 → 0x80) und erscheint rechts neben dem Fadenkreuz statt zentriert über der Katze, analog zum Station-Tooltip.

## [1.0.0-beta.6] - 2026-06-14
### Fixed
- **Arm Target Overlay**: Falschen Namespace im `arm_goggles`-Item-Tag korrigiert (`create_aeronautics:aviators_goggles` → `aeronautics:aviators_goggles`). Die Aviator's Goggles aus *Create: Aeronautics* zählen damit wieder für das Arm Target Overlay, und der `TagLoader`-ERROR beim Server-Start ist behoben. Beide Tag-Einträge sind nun als `required: false` markiert (saubere Cross-Mod-Tags).

## [1.0.0-beta.5] - 2026-06-14
### Removed
- **Cat Guardian Modul**: Redundanten `CatGuardianMaxUpStepMixin` entfernt. Der Mixin schlug beim Start ohnehin fehl (`maxUpStep` ist in `Cat` nur geerbt, nicht deklariert) und wurde durch das `STEP_HEIGHT`-Attribut (1.5) vollständig ersetzt. Beseitigt die Mixin-Warnung im Log; keine Verhaltensänderung beim Klettern.

## [1.0.0-beta.4] - 2026-06-14
### Fixed
- **Cat Guardian Modul**:
  - **Teleport-Bug endgültig behoben**: Wächterkatzen teleportieren sich nicht mehr zum Besitzer. Neben dem `FollowOwnerGoal` wird nun auch der Panik-Teleport (`TamableAnimalPanicGoal`, Priorität 1) unterbunden, der bisher im Kampf – besonders auf Dedicated Servern, wenn der Besitzer weit weg war – auslöste. Zusätzlicher Mixin auf `TamableAnimal.shouldTryTeleportToOwner()` blockiert beide Quellen an der Wurzel.
  - **Schwimmen gegen die Strömung**: Katzen schwimmen jetzt aktiv Richtung Ziel bzw. zurück zum Napf, statt von fließendem Wasser abgetrieben zu werden.
  - **Pathfinding**: Sprung- und Antriebsrichtung folgen dem nächsten Pfad-Knoten statt der Luftlinie zum Ziel (Pfad und Ziel weichen oft ab). Katzen werden nicht mehr in Richtung unerreichbarer Ziele gezogen (Endknoten-Distanz-Gate).
  - **Zäune**: Wächterkatzen können jetzt über Zäune navigieren (`canWalkOverFences` + 1.5 Stufenhöhe), wodurch kürzere Routen durch die Basis gefunden werden.
### Added
- **Cat Guardian Modul**: Wächterkatzen töten Creeper sofort (lore-konform – Creeper fürchten Katzen; keine Explosion). Der Instakill greift jedoch nur auf unter 1 Block Reichweite, also kein „Fernkill".
### Changed
- **Cat Guardian Modul**: Standard-`guard_radius` von 64 auf 32 gesenkt (Maximum bleibt 128).

## [1.0.0-beta.3] - 2026-06-14
### Fixed
- **Cat Guardian Modul**:
  - **Goggles-Overlay** zeigt Ziel und Katze jetzt zuverlässig an – die client-seitige Wächter-Erkennung hängt nicht mehr am nicht-synchronisierten `CAT_BOWL_POS`-Attachment.
  - **XP-Sammeln** funktioniert: von Katzen getötete Mobs droppen XP (Gutschrift an den Besitzer via `setLastHurtByPlayer`), die in den Katzen-Puffer und an der Station in XP-Flaschen umgewandelt wird.
  - **Targeting** ist auf die Guard-Zone begrenzt (Ziel- und Reisedistanz, schnelleres Aufgeben unerreichbarer Mobs, radiusbegrenzte Vergeltung); sauberes Heimkehren von außerhalb ohne Oszillation.
  - **Klettern & Tauchen**: Katzen überwinden zuverlässig ~1,5-Block-Stufen (Step-Height-Attribut + Sprung-Assist) und tauchen Unterwasser-Zielen nach (3D-Steering + Wasseratmung).
### Added
- **Cat Guardian Modul**: Umschaltbares Debug-Overlay per rebindbarem Keybind (Standard: Numpad +; Overlays standardmäßig aus). XP-Leisten in Station- und Katzen-UI, „Associated Cats"-Tooltip beim Anschauen einer Station.
- **Übersetzungen**: Neue Sprachen **de_AT** (österreichischer Dialekt) und **cs_CZ** (Tschechisch); **es_ES** und **fr_FR** vervollständigt. Alle Locales decken jetzt alle Keys ab.
### Changed
- **Cat Guardian Modul**: GUIs an den Station-Stil angeglichen – rechtsbündiges 5-Slot-Loot-Grid (bündig mit dem Spielerinventar), umrandete Bars mit Tooltips, eigener Header-Streifen für die XP-Leiste, durchlaufende Separator-Linien entfernt.

## [1.0.0-beta.2] - 2026-06-13
### Fixed
- **Cat Guardian Modul**:
  - **Wasser-Targeting**: Katzen greifen jetzt auch Mobs im Wasser an (z. B. Drowned) und bleiben dabei nicht mehr stecken.
  - Katzen kehren nach dem Verlust aller Ziele zuverlässig zur Basis zurück.
### Added
- **Cat Guardian Modul**: XP-System mit Leisten in den UIs; asymmetrischer Wachradius (separater XZ- und Y-Radius).
### Changed
- **Cat Guardian Modul**: Goggles-Overlay wird über die Blickrichtung mit 15-Sekunden-Timeout aktiviert (statt Klick-Toggle); Standard-Wachradius angepasst.

## [1.0.0-beta] - 2026-06-12
### Added
- **Cat Guardian Modul**:
  - **Goggles-Integration**: Engineering Goggles (Create) zeigen jetzt die aktuelle Zielentität einer Wächterkatze mit einem roten Umriss an, wenn man die Katze ansieht.
  - **Pathfinding-Verbesserung**: Wächterkatzen haben nun eine erhöhte Stufenhöhe (1.5 Blöcke), um besser über Hindernisse wie Zäune oder Mauern navigieren zu können (via Mixin).
  - Synchronisierung der Angriffsziele vom Server zum Client für verbesserte Visualisierung.

### Changed
- **Cat Guardian Modul**:
  - **Fütterungsstation**: Hitboxen und VoxelShapes wurden komplett überarbeitet, um exakt zum 3D-Modell zu passen und für alle Ausrichtungen korrekt zu rotieren.
  - Modell-Anpassung der Fütterungsstation für eine stimmigere Darstellung der Glas-Elemente und der Basis.
- Die Mod-Version wurde auf `1.0.0-beta` angehoben.

## [1.0.0-alpha] - 2026-06-12
### Added
- **Cat Guardian Modul**: Umfangreiches System für Hauskatzen.
  - Neue Blöcke: **Katzennapf** und **Fütterungsstation** (inkl. Sable-Integration für bewegte Strukturen).
  - **Katzen-Rüstungen**: Eisen, Gold, Diamant und Netherit (erhöhen Angriffsschaden und absorbieren Schaden).
  - **Wächter-Logik**: Katzen können an Näpfe gekoppelt werden (Shift-Rechtsklick) und bewachen bei ausreichender Fütterung (Fisch) aktiv die Basis gegen Monster.
  - **Loot-Sammlung**: Katzen sammeln Drops von besiegten Gegnern in einem internen Inventar, das an Fütterungsstationen automatisch entleert werden kann.
  - **UI & Inventar**: Eigenes Katzen-Inventar-GUI (Shift-Rechtsklick) zum Ausrüsten und Verwalten von Items.
  - Automatisches Koppeln von Katzen an nahegelegene Näpfe und Rückzug-Logik bei niedriger Gesundheit.
- **Battle Dogs Modul**:
  - **Wolfs-Rüstungen**: Eisen, Gold, Diamant und Netherit.
  - Erhöhen den Angriffsschaden des Wolfs je nach Material.
  - Einfaches Ausrüsten per Rechtsklick und Entfernen mittels Schere.
- **Arm Target Overlay Modul**:
  - Client-Feature für die Mod *Create*.
  - Zeigt Input- und Output-Positionen von Mechanical Arms als Overlay in der Welt an, wenn Engineering Goggles getragen werden.
- Neues Skript `scripts/apply_cat_armor_alpha_mask.py` zur automatischen Übertragung der Transparenzmaske vom Diamant-Katzenrüstungsmodell auf andere Materialien (Gold, Eisen, Netherit).

### Changed
- Alle ungenutzten Cat-Armor-Assets (Vorschauen, UV-Maps, alte Texturversionen) wurden in den Ordner `unused_assets/cat_armor/` verschoben, um die Projektstruktur zu bereinigen.
- Die `.gitignore` wurde aktualisiert, um den neuen `unused_assets/` Ordner auszuschließen.
- Die Mod-Version wurde auf `1.0.0-alpha` angehoben.

## [0.16.3] - 2026-05-15
### Fixed
- BlockGlow/Sable-Integration client-seitig isoliert (in `client/compat` verschoben), damit Dedicated-Server keine Client-Klassen laden muessen.
- BlockGlow-Sable-Highlight-Suche korrigiert: Bounding-Box-Transformationen verwenden jetzt pro Sub-Level eine frische Box, um fehlerhafte Treffer bei mehreren Sub-Levels zu vermeiden.

### Changed
- Test-Server-Startskript kopiert die Mod-JAR nicht mehr manuell; das uebernimmt weiterhin der Gradle-Task `copyJarToTestEnvironments` nach `build`.
- Test-Client-Hinweistext nutzt jetzt den tatsaechlich vorhandenen JAR-Namen statt einer fest kodierten Versionsnummer.

## [0.16.2] - 2026-05-15
### Fixed
- CI-Builds laden jetzt die lokalen Drittanbieter-JARs zur Build-Zeit nach (`sable` und `toughasnails`), damit GitHub Actions trotz nicht versionierter lokaler Abhängigkeiten erfolgreich bauen kann.

### Changed
- `libs/ToughAsNails-neoforge-1.21.1-10.1.0.13.jar` aus dem Repository entfernt.
- Lokale ToughAsNails-JARs werden jetzt per `.gitignore` ausgeschlossen.

## [0.16.1] - 2026-05-15
### Fixed
- BlockGlow: Unterstützung für Sable/Create-Aeronautics-Sub-Levels ergänzt, damit Block-Highlights auch auf bewegten Sub-Levels erscheinen.

### Changed
- Sable-Integration als lokale optionale Abhängigkeit robust gemacht (`libs/sable-neoforge-*.jar`), damit Builds auch ohne lokale Sable-JAR erfolgreich laufen.
- Lokale Sable-JARs werden jetzt per `.gitignore` ausgeschlossen, um unbeabsichtigtes Einchecken von Drittanbieter-Binärdateien zu verhindern.

## [0.16.0] - 2026-05-15
### Added
- BlockGlow: Neues Modul mit `/blockglow`, das passende Blöcke als X-Ray-ähnliche Umrisse hervorhebt, auch durch andere Blöcke hindurch.
  - Selektionsmodus konfigurierbar (`nearest` oder `scan_order`).
  - Highlight-Reichweite, Dauer und Outline-Farbe über die Modul-Config steuerbar.

### Changed
- BlockGlow: Renderlogik so angepasst, dass die Umrisse radial um den Spieler ausgewählt und ohne Depth-Test gezeichnet werden.

## [0.15.1] - 2026-05-15
### Fixed
- Flying Fish Boots: Prevented incompatible enchantments (Depth Strider, Frost Walker) from being applied to Flying Fish Boots via anvil.
  - Added tooltip to inform players about this incompatibility.
  - Updated all language files (DE, EN, ES, FR).

## [0.15.0] - 2026-05-13
### Added
- Neues **Flying Fish**-Modul mit eigenem Wasser-Ambient-Mob, Spawn-Egg, Bucket und speziellen Flying-Fish-Boots.
- Neue Food-Items: roher und gegarter Flying Fish inklusive Smelting-, Smoking- und Campfire-Rezepten.
- Zentraler **Vanilla Plus Additions**-Creative-Tab, in den Module ihre Inhalte gesammelt eintragen können.
- Eigene Item- und Entity-Texturen für Flying Fish, Flying-Fish-Bucket und Flying-Fish-Boots sowie Lokalisierungen in mehreren Sprachen.

### Changed
- Flying-Fish-Boots basieren jetzt auf Diamond-Boots-Stats und können über ein eigenes Rezept mit Diamond Boots + Flying Fish Bucket hergestellt werden.
- Flying-Fish-Crafting wird nun direkt im `FlyingFishModule` zur Laufzeit wie im konfigurierbaren Crafting-Modul injiziert, damit das Rezept serverseitig robust verfügbar ist.
- Flying-Fish-Items werden nicht mehr auf verschiedene Vanilla-Tabs verteilt, sondern über die neue zentrale Creative-Tab-Infrastruktur angezeigt.

### Fixed
- Flying Fish droppen jetzt verlässlich ihren Fisch-Item-Drop; falls die Loot-Table nicht greift, sorgt ein Modul-Fallback für den korrekten Raw-/Cooked-Drop.
- Mehrere Flying-Fish-Assets und Modelle wurden überarbeitet, damit Bucket-, Boots- und Item-Darstellung konsistent im Spiel erscheinen.

## [0.14.6] - 2026-05-13
### Fixed
- **Critical**: Server no longer hangs for 60 seconds (watchdog timeout) caused by region file deletion.
  - Root cause: deleting `.mca` files at runtime caused an infinite chunk regeneration loop:
    chunk fails → file deleted → server retries load → file missing → regenerates → fails again → ∞
  - Region files are **never deleted at runtime** anymore. Admins are instructed via log/chat to
    delete the file after a clean shutdown.
- **Threading**: Broadcasts to players are now always scheduled on the main server thread via
  `server.execute()` instead of being called from ForkJoinPool worker threads (potential deadlock).
- **Spam prevention**: Log messages and player broadcasts are now rate-limited (5s / 10s cooldown)
  to prevent log disk fill and chat flood when many chunks fail quickly.

## [0.14.5] - 2026-05-13
### Fixed
- **Critical**: Server no longer crashes on `ArrayIndexOutOfBoundsException` (Aquifer) or `IllegalStateException: Parent chunk missing` during async chunk generation.
- Root cause: Both exceptions propagate through `GenerationChunkHolder.lambda$applyStep$0` where they get wrapped in `ReportedException` and kill the server. New `GenerationChunkHolderMixin` intercepts the `CompletableFuture.handle()` callback BEFORE the wrapping and suppresses known exceptions gracefully.
- Chunks that fail with known exceptions are now silently skipped instead of crashing.

## [0.14.4] - 2026-05-13
### Added
- Worldgen Guard: Extended exception handling to catch `IllegalStateException: Parent chunk missing` errors.
  - These occur when chunks are generated outside the world border or in invalid states.
  - Chunks are now silently skipped instead of crashing the server.
  - Auto-cleanup of corrupted region files still applied.

### Changed
- Mixin exception filter now specifically checks for "Parent chunk missing" message instead of catching all IllegalStateException.

## [0.14.3] - 2026-05-13
### Added
- Worldgen Diagnostics Tool: Automatically detects incompatible mod combinations (lithostitched, sable, yungsapi, mr_dungeons_andtaverns) and provides remediation guidance when worldgen crashes occur.
- Clear documentation: Crash Guard is a **temporary workaround**, not a permanent fix. It requires incompatible mods to be disabled or replaced with compatible alternatives.

### Changed
- Worldgen Guard documentation clarified: ArrayIndexOutOfBoundsException in Aquifer generation is caused by Mod A modifying worldgen structures in ways that Mod B doesn't expect. This is a root-cause mod conflict that cannot be fixed by protection code alone.
- Server startup now reports diagnostics summary when `worldgenCrashGuardEnabled=true`.

## [0.14.2] - 2026-05-13
### Added
- Worldgen Crash Guard: Auto-Cleanup-Feature bei Fehlern — korrupte Region-Dateien (region/, poi/, entities/) werden automatisch gelöscht.
- Worldgen Crash Guard Mixin: Nutzt `@Mixin(targets = "...")` für robuste Kompatibilität mit Minecraft-Versionen.

### Changed
- Worldgen Crash Guard: Fehlerbehandlung erweitert mit globalem `MessageBroadcaster`-Broadcast an alle Spieler.
- WorldgenGuardService: Unterstützt automatische Pfadauflösung für Overworld und Custom-Dimensions.

## [0.14.1] - 2026-05-13
### Changed
- Worldgen Crash Guard: Verbessertes Debug-Logging mit globaler Debug-Flag-Unterstützung, um Administratoren beim Isolieren inkompatibler Mods zu helfen.
- Command `/vpa module status`: Neue dekorative Box-Formatierung mit Farben und Symbolen (✓/✗, ▲/▼) für bessere Lesbarkeit.
- Command `/vpa module status <module_id>`: Detailliertes Format mit UI-Kästen und Farb-Highlights für einzelne Module.

## [0.14.0] - 2026-05-13
### Added
- Emergency-Worldgen-Crash-Guard als Mixin eingeführt: Fängt `IndexOutOfBoundsException` während `structure_starts`-Generierung optional ab, um Serverabstürze bei inkompatiblen Worldgen-Mod-Kombinationen zu vermeiden.
- Neue Mixin-Konfigurationsdatei `vanillaplusadditions.mixins.json` hinzugefügt und in der Mod-Metadatei aktiviert.

### Changed
- Globale Config um `worldgenCrashGuardEnabled` erweitert (Default: `false`), damit der Guard bewusst als temporärer Workaround ein-/ausgeschaltet werden kann.

## [0.13.5] - 2026-05-13
### Added
- Neue Admin-Befehle: `/vpa module status|enable|disable|clear` für Runtime-Overrides ohne Config-Datei-Edit sowie `/hauntedhouse whereami` für In-/Outdoor-Debugging.
- Build-Tasks für lokale Tests: `deployJarToLocalMinecraftMods`, `resetLocalMinecraftVpaConfig`, `enableHauntedHouseInLocalConfig` und `deployToLocalMinecraft`.

### Changed
- HauntedHouseModule: Spawn-/Reveal-Logik robuster und performanter gemacht (gedrosselte Reveal-Checks, Cache-Refresh-Intervall, gecachte Direct-Spot-Validierung, verbesserte Cave-vs-Structure-Heuristik).
- HauntedHouseConfig: Neue Tuning-Optionen `cache_refresh_interval_ticks` und `direct_spot_validation_interval_ticks` ergänzt.
- ModuleManager/AbstractModule: Runtime-Override-Pfad ergänzt, damit Module zur Laufzeit gezielt an/ausgeschaltet werden können.

## [0.13.4] - 2026-05-11
### Fixed
- DeathCoordinatesModule: Teleport-Befehl korrigiert — verwendet jetzt `@s` statt `@p`, um den Spieler zu teleportieren, der auf die Nachricht klickt, statt willkürlich den nächsten Spieler.

## [0.13.3] - 2026-05-11
### Fixed
- EndOxygenModule: Client-only GUI/Render-Code in eine dedizierte Client-Event-Klasse ausgelagert, damit auf Dedicated-Servern keine Client-Klassen mehr aus Common-Code geladen werden.

## [0.13.2] - 2026-05-10
### Fixed
- Release workflow: YAML-Syntaxfehler in `.github/workflows/release.yml` behoben, sodass GitHub Actions den Workflow wieder korrekt laden kann.
- Release workflow: Backfill-Step erstellt Release-Text jetzt robust über eine temporäre Datei statt eines mehrzeiligen Inline-Strings.

## [0.13.1] - 2026-05-10
### Changed
- Release workflow: Release-Notes werden jetzt automatisch aus `CHANGELOG.md` extrahiert — kein manuelles Pflegen des `release.yml`-Bodies mehr nötig.
- Release workflow: Beim Pushen eines neuen Tags werden alle bestehenden GitHub-Releases rückwirkend mit den korrekten Changelog-Einträgen aktualisiert (Backfill).

## [0.13.0] - 2026-05-10
### Added
- CustomCraftingRecipesModule: Neues konfigurierbares Modul für benutzerdefinierte Handwerksrezepte eingeführt.
  - Unterstützt **Shaped Recipes** (`recipe_id;result_item;result_count;pattern;keys`) mit Zeilen-Trenn­zeichen `|` oder gequoteten Zeilen.
  - Unterstützt **Shapeless Recipes** (`ingredient1,ingredient2,...->result_item[;result_count[;recipe_id]]`).
  - Zutaten können Item-IDs oder Tag-Referenzen (Präfix `#`) sein.
  - Rezepte werden über einen ReloadListener nach jedem `/reload` neu angewandt.

### Changed
- BetterMobsConfig: Dimension-Konfig-Lookup in private Methode `getDimensionConfigEntries()` extrahiert; Null-Safety und Fehlerbehandlung verbessert.
- HauntedHouseConfig: Standard-Beispielwerte für Spawn-Einträge (Hexen-Spawn, Struktur-ID, Block-Materialien) auf sinnvolle Defaults gesetzt.

## [0.12.0] - 2026-05-10
### Added
- MobDropsModule: Eigenständiges, konfigurierbares Modul für zusätzliche Mob-Drops eingeführt (`mob_id;item_id;chance[;max_drops]`).
- HauntedHouseConfig: Umfangreiche Spawn-Tuning-Optionen ergänzt (Presets, Material-Scan, Verteilung, direkte Area-Spawns, Cache- und Fog-Trail-Parameter).
- BetterMobsConfig: Neuer Schlüssel `WEAPON_TYPES` für materialbasierte Nahkampfwaffen hinzugefügt.

### Changed
- HauntedHouseModule: Spawn-Logik stark überarbeitet (konfigurierbare Replacement-Entity, verteilte Spawnpositionen, Cave-/Sky-Filter, direkte Spawns aus gecachten Indoor/Garden-Spots).
- HauntedHouseModule: Initialisierung nur noch von `mr_dungeons_andtaverns` abhängig; die Replacement-Entity ist nun frei konfigurierbar.
- BetterMobsModule/Config: Zonenlisten auf `;` als Trennzeichen umgestellt und Weapon-Randomizer/Materialzuordnung für Skeleton-Waffen verbessert (inkl. Goal-Reassessment).
- WitherSkeletonModule: Zusätzliche Drop-Logik entfernt; Drop-Konfiguration in das neue `mob_drops`-Modul verschoben.
- DeathCoordinatesModule und MobGlowModule: Spieler-/Command-Feedback auf direkte Textausgaben umgestellt.

## [0.11.1] - 2026-05-09
### Changed
- HauntedHouseModule: Modul standardmäßig aktiviert, da Alex's Mobs für 1.21.x verfügbar ist.
- HauntedHouseConfig: Standard-Zielstrukturen um `dungeons_and_taverns:witch_villa` erweitert.

## [0.11.0] - 2026-05-08
### Added
- BetterMobsModule: Weapon Randomizer Feature hinzugefügt. Mobs können nun zufällig mit Schwertern, Äxten oder Bögen ausgerüstet werden (konfigurierbar).
- BetterMobsModule: Dedizierter Konfigurationsschlüssel `WEAPON_ENCHANTMENTS` für Waffen-Verzauberungen eingeführt.

## [0.10.3] - 2026-05-08
### Changed
- Lokalisierung: Alle Sprachdateien wurden im Namensraum `vanillaplusadditions` konsolidiert.
- Code-Bereinigung: Hardcoded Strings in `MobGlowModule` und `DeathCoordinatesModule` wurden durch translatable Components ersetzt.
- Ressourcen-Management: Veraltete `create_gravity` Ressourcen entfernt und Damage-Type Definitionen verschoben.

## [0.10.2] - 2026-05-08
### Added
- Tough As Nails Tooltip: Grafische Anzeige der Durst-Wiederherstellung (Icons) anstelle von reinem Text.

### Fixed
- Tough As Nails Tooltip: Korrektur der Icon-Textur-Koordinaten und Abmessungen für eine konsistente Darstellung mit der Original-Mod.

## [0.10.1] - 2026-05-08
### Fixed
- Lokalisierung: Fehlende Übersetzung für den Durst-Wiederherstellungs-Tooltip hinzugefügt.

## [0.10.0] - 2026-05-08
### Added
- Tough As Nails Integration: Optionale Unterstützung für die Tough As Nails Mod.
- Thirst System: Gegenstände können nun Durst wiederherstellen, wenn Tough As Nails installiert ist.
- Food Effects Expansion: Umfangreiche Liste an neuen Standard-Effekten für Vanilla, Create und Tough As Nails Items.
- Probability System: Effekte (Potion & Durst) können nun mit einer konfigurierbaren Wahrscheinlichkeit auftreten.
- Tooltip Integration: Dynamische Anzeige von Durst-Wiederherstellung und Temperatur-Effekten in Item-Tooltips.

### Changed
- FoodEffectsModule: Automatische Umwandlung von konfigurierten Items in essbare Gegenstände (Always Edible).
- Code Quality: Umfassende Checkstyle-Bereinigung und Refactoring zur besseren Modularität.

## [0.9.3] - 2026-05-08
### Changed
- Stackables: Standard-Stackgröße für Tränke auf 64 erhöht.

## [0.9.2] - 2026-05-07
### Changed
- README: GitHub Banner hinzugefügt und Logo entfernt.

## [0.9.0] - 2026-05-07
### Added
- EndOxygenModule: New module that introduces oxygen mechanics in the End dimension.
- Custom damage type `create_gravity:out_of_oxygen` with localized death messages (EN, DE, ES, FR).
- Configuration options for oxygen consumption speed and Water Breathing effect synergy.

## [0.8.2] - 2026-05-07
### Changed
- BetterMobsModule: Spawn-Raten für Ausrüstung und Wahrscheinlichkeiten für Verzauberungen angepasst.

## [0.8.1] - 2026-05-07
### Fixed
- WitherSkeletonModule: Ungenutzte Imports entfernt, um Checkstyle zufriedenzustellen.

## [0.8.0] - 2026-05-07
### Added
- WitherSkeletonModule: Zusätzliche, konfigurierbare Drops für Wither-Skelette hinzugefügt (Standard: 40% Goldapfel, 10% Netherite-Schrott, 15% Wither-Skelett-Schädel).
- WitherSkeletonModule: Caching-Mechanismus für effiziente Drop-Verarbeitung implementiert.

### Changed
- WitherSkeletonModule: Die Konfiguration für Wither-Skelett-Schädel wurde in die Liste der zusätzlichen Drops integriert.

## [0.7.1] - 2026-05-06
### Changed
- Stackables: Stack-Limits erhöht und neue stapelbare Gegenstände hinzugefügt.

## [0.7.0] - 2026-04-30
### Added
- Overpacked Slowdown: Neues Modul, das die Verlangsamung der "Overpacked" Mod mit einem konfigurierbaren Multiplikator überschreibt.

## [0.6.1] - 2026-04-30
### Added
- Dokumentation: Leitfäden für Modul-Konfiguration und Debug-Logging hinzugefügt.

## [0.6.0] - 2026-04-29
### Added
- Food Effects: Neues Modul für konfigurierbare Trankeffekte beim Verzehr von Lebensmitteln.

### Fixed
- Food Effects: Checkstyle-Warnungen in der Konfiguration behoben.

## [0.5.1] - 2025-11-08
### Changed
- Haunted House: Kleinere interne Anpassungen und Versions-Bump.

## [0.5.0] - 2025-11-05
### Added
- Haunted House: Neues Modul für atmosphärische Effekte (Nebel) und verstärktes Witch-Spawning in bestimmten StruktureN.
- Dokumentation: Umfassende Dokumentation für das Haunted House Modul hinzugefügt.

## [0.4.1] - 2025-11-02
### Fixed
- Stackables: Initialisierung der Konfiguration und Logging verbessert.

## [0.4.0] - 2025-11-02
### Added
- Stackables: Modul zum Ändern der maximalen Stapelgröße von Gegenständen (initialer Fokus auf nicht-Trank-Items).
- Death Coordinates: Modul zur Anzeige der Koordinaten beim Tod.

## [0.3.0] - 2025-10-19
### Changed
- Better Mobs: Debug-Nachrichten weiter verbessert.

## [0.2.3] - 2025-10-09
### Changed
- Better Mobs: Debug-Nachrichten optimiert.

## [0.2.2] - 2025-10-09
### Added
- Better Mobs: Dimensionsspezifische Konfigurationen hinzugefügt.

### Changed
- Better Mobs: Ausrüstungssystem für Mobs verbessert.

## [0.2.1] - 2025-10-08
### Changed
- Better Mobs & Wither Skeleton: Module aktualisiert.

## [0.2.0] - 2025-10-08
### Added
- Better Mobs: Initiales Modul mit Konfigurationssystem hinzugefügt.

### Fixed
- Code Quality: Diverse Probleme behoben und GitHub Actions auf v4 aktualisiert.

## [0.1.0] - 2025-10-07
### Added
- Initial project setup with GitHub Actions workflows
- Automated build workflow with artifact uploads
- Release automation workflow for version tags
- Code quality checks with Checkstyle and SpotBugs
- Instance switcher utility for managing Minecraft installations
- Comprehensive project documentation with AI collaboration transparency
- MIT License
- Core mod infrastructure and module management system
- Professional README with installation and development instructions

### Fixed
- Clean git history (removed proprietary files using git-filter-repo)

### Security
- Implemented code quality checks to maintain security standards
