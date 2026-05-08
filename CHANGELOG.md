# Changelog
All notable changes to VanillaPlusAdditions will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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

## [Unreleased]
### Added
- None

### Changed
- None

### Deprecated
- None

### Removed
- None

### Fixed
- None

### Security
- None

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