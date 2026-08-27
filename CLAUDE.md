# VanillaPlusAdditions — Claude instructions

## Branching / Workflow
Gerry hat für dieses Projekt explizit festgelegt: **direkt auf `master` arbeiten, kein PR-Flow.**
Es gibt kein `staging`, kein PR-Flow — direkt auf `master`.
(Eigene Feature-PRs schreibe ich generell nicht mehr; ich merge nur Mac-Claudes PRs.)

## Deploy / Commit / Push — nur auf Gerrys Kommando (WICHTIG, 2026-07-23)
- **Niemals ohne Gerrys ausdrückliches Kommando:** committen, taggen, pushen ODER auf den
  **Server (games2) deployen / den Server neustarten.**
- **Standard beim Iterieren an Änderungen: nur der lokale Client** —
  `bash scripts/deploy.sh --client [--no-build]`. Build + Client-Deploy darf ich frei machen.
- **Grund:** games2 ist ein **Live-Server**. Ein Restart zwingt Sebi, mit exakt der neuen
  Jar-Version neu zu starten (die Gerry ihm erst schicken muss) — ein überraschender Restart
  stört ihn mitten im Spiel. Darum Server-Deploys, Commits, Tags und Pushes aufheben, bis Gerry
  es explizit sagt.
- Nach jedem Client-Deploy: Gerry erinnern, MC **frisch** zu starten (nie ins laufende Spiel
  hot-swappen — korrumpiert das Jar).

### Nur auf Sebis PC (Windows / Git Bash) — `scripts/deploy-win.sh` (2026-08-27)
`scripts/deploy.sh` ist Linux-only (Prism-Instanzpfad, `pgrep`) und defaultet auf Server+Client —
auf diesem PC **nicht** verwenden. Stattdessen `scripts/deploy-win.sh`:
- **Standard ist IMMER OHNE `--client`:** `bash scripts/deploy-win.sh [--no-build]` kopiert das Jar
  nur nach `~/ClaudeProjekte/MinecraftModpack/mods/` (Staging, folgenlos).
- **`--client` nur auf Sebis ausdrückliche Ansage** — das tauscht das Jar im echten Client
  (`.minecraft\mods`) und zwingt ihn, MC frisch zu starten. Nicht ungefragt anhängen, nicht anbieten.
- Der Running-Game-Check läuft dort über PowerShell und **bricht ab, wenn er den Spielzustand nicht
  ermitteln kann** (bei `pgrep` in Git Bash würde die Prüfung still durchwinken und das Jar
  korrumpieren). `--force` umgeht ihn — nur als letzte Instanz.
- Ziel-Ordner überschreibbar via `VPA_MODPACK_MODS` / `VPA_CLIENT_MODS`.
- Server-Deploy kann das Script bewusst nicht; games2 bleibt Gerrys Box + Gerrys Kommando.

### Build-Voraussetzung: zwei JARs in `libs/`
`libs/sable-neoforge-1.21.1-1.2.2.jar` und `libs/ToughAsNails-neoforge-1.21.1-10.1.0.13.jar` sind
gitignored. Fehlen sie, bricht der Build mit ~34 Compile-Fehlern ab (`dev.ryanhcode.sable.api`,
`toughasnails.api.thirst`). Die CI lädt sie in `.github/workflows/build.yml` nach — lokal von dort
die URLs nehmen oder aus einem Mods-Ordner kopieren.

## Recipes & block loot: ALWAYS via code, never JSON
JSON-Datapack-Dateien laden in diesem Mod **nicht zuverlässig** (mehrfach bestätigt — auch im
korrekten 1.21-Singular-Ordner `recipe/`/`loot_table/`). Daher alles im Code, **zwei Fälle**:
- **Eigene Rezepte (für unsere Items/Blöcke)** → **im jeweiligen Modul selbst** registrieren,
  per `RecipeManager`-Injection im `AddReloadListenerEvent`, gegated auf `isModuleEnabled()`.
  So ist das Item craftbar, solange das Modul aktiv ist. Vorlage: `MinecartChunkLoadingModule`
  (`onAddReloadListener` + `applyChunkLoaderRailRecipe`), analog `FlyingFishModule`.
- **Rezept-Erweiterungen für Vanilla / andere Mods** → als One-Liner in
  `CustomCraftingRecipesConfig.DEFAULT_RECIPES` / `DEFAULT_SHAPELESS_RECIPES`
  (z.B. die fairen Rail-Upgrades). Dieses Modul ist auch für user-konfigurierbare Rezepte da.
- **Block-Drops** → `getDrops(BlockState, LootParams.Builder)` am Block überschreiben
  (siehe `ChunkLoaderRailBlock`), nicht per Loot-Table-JSON.
- **Keine** `data/.../recipe/`- oder `loot_table/`-JSONs mehr anlegen. Keine Migration zu JSON geplant.
Details: `docs/custom_crafting_recipes.md`.

## Worktrees
Do NOT use worktrees for this project. Edit files directly in the repository working copy.
