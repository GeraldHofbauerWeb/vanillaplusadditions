# Changelog
All notable changes to VanillaPlusAdditions will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
- Server startup now reports diagnos tics summary when `worldgenCrashGuardEnabled=true`.

## [0.14.2] - 2026-05-13
### Added
- Worldgen Crash Guard: Auto-Cleanup-Feature bei Fehlern — korrupte Region-Dateien (region/, poi/, entities/) werden automatisch gelöscht.
- Worldgen Crash Guard Mixin: Nutzt `@Mixin(targets = "...")` für robuste Kompatibilität mit Minecraft-Versionen.

### Changed
- Worldgen Crash Guard: Fehlerbehandlung erweitert mit globalem `MessageBroadcaster`-Broadcast an alle Spieler.
- WorldgenGuardService: Unterstützt automatische Pfadauflösung für Overworld und Custom-Dimensions.

## [0.14.1] - 2026-05-13
### Changed
- Worldgen Crash Guard: Verbessertes Debug-Logging mit globaler Debug-Flag-Unterstützung, um Administratoren beim Isolieren inkompatsibler Mods zu helfen.
- Command `/vpa module status`: Neue dekorative Box-Formatierung mit Farben und Symbolen (✓/✗, ▲/▼) fuer bessere Lesbarkeit.
- Command `/vpa module status <module_id>`: Detailliertes Format mit UI-Kaesten und Farb-Highlights fuer einzelne Module.

## [0.14.0] - 2026-05-13
### Added
- Emergency-Worldgen-Crash-Guard als Mixin eingefuehrt: Faengt `IndexOutOfBoundsException` waehrend `structure_starts`-Generierung optional ab, um Serverabstuerze bei inkompatiblen Worldgen-Mod-Kombinationen zu vermeiden.
- Neue Mixin-Konfigurationsdatei `vanillaplusadditions.mixins.json` hinzugefuegt und in der Mod-Metadatei aktiviert.

### Changed
- Globale Config um `worldgenCrashGuardEnabled` erweitert (Default: `false`), damit der Guard bewusst als temporaerer Workaround ein-/ausgeschaltet werden kann.

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
- Release workflow: Backfill-Step erstellt Release-Text jetzt robust ueber eine temporaere Datei statt eines mehrzeiligen Inline-Strings.

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
- Haunted House: Neues Modul für atmosphärische Effekte (Nebel) und verstärktes Witch-Spawning in bestimmten Strukturen.
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