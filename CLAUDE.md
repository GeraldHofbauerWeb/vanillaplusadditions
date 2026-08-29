# VanillaPlusAdditions — Claude instructions

## Branching / Workflow
Es gibt **kein `staging`**. Basis-Branch ist immer `master`. Welcher Weg dorthin gilt, hängt
davon ab, auf wessen Rechner ich laufe — im Zweifel `git config user.email` prüfen:

- **Auf Gerrys Rechnern (Linux-Box, Mac):** direkt auf `master` committen, kein PR-Flow.
- **Auf Sebis PC (Windows / Git Bash):** **kein Direkt-Commit auf `master`.** Stattdessen
  Feature-Branch + **PR gegen `master`** — das ist seit 2026-08-29 ausdrücklich erlaubt und
  der vorgesehene Weg (siehe PR #1 als Vorlage: eigenes Modul-Verzeichnis, Doku unter `docs/`,
  ausgefüllter Test-Plan mit ehrlichen offenen Punkten).
  Branch-Namen nach dem eingeführten Muster `feature/…` bzw. `fix/…`.
- **Gemergt wird nur von Gerry** (bzw. von einem Claude auf Gerrys Kommando) — nach Style- und
  Build-Check. Sebis Claude merged seine eigenen PRs nicht selbst.

Vor jedem PR lokal `./gradlew build` grün haben: der Task fährt Checkstyle
(`config/checkstyle/checkstyle.xml`) und SpotBugs mit, Unit-Tests gibt es in diesem Repo keine.

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

### Build-Voraussetzung: vier JARs in `libs/`
`libs/sable-neoforge-1.21.1-1.2.2.jar`, `libs/ToughAsNails-neoforge-1.21.1-10.1.0.13.jar`,
`libs/Quark-4.1-482.jar` und `libs/Zeta-1.1-40.jar` sind gitignored. Fehlen sie, bricht der Build
mit Compile-Fehlern ab (`dev.ryanhcode.sable.api`, `toughasnails.api.thirst`, bzw.
`org.violetmoon.quark.content.tools.item.PathfindersQuillItem` für `pathfinder_quills` — Zeta
wird zusätzlich gebraucht, weil Quarks `PathfindersQuillItem` von Zetas `ZetaItem` erbt und javac
die Supertyp-Hierarchie auflösen muss, auch wenn unser Code Zeta nie direkt referenziert). Die CI
lädt sie in `.github/workflows/build.yml` nach — lokal von dort die URLs nehmen oder aus einem
Mods-Ordner kopieren.

## Recipes & block loot: ALWAYS via code, never JSON
JSON-Datapack-Dateien laden in diesem Mod **nicht zuverlässig** (mehrfach bestätigt — auch im
korrekten 1.21-Singular-Ordner `recipe/`/`loot_table/`). Daher alles im Code, **vier Fälle**:
- **Eigene Rezepte (für unsere Items/Blöcke)** → **im jeweiligen Modul selbst** registrieren,
  per `RecipeManager`-Injection im `AddReloadListenerEvent`, gegated auf `isModuleEnabled()`.
  So ist das Item craftbar, solange das Modul aktiv ist. Vorlage: `MinecartChunkLoadingModule`
  (`onAddReloadListener` + `applyChunkLoaderRailRecipe`), analog `FlyingFishModule`.
- **Rezept-Erweiterungen für Vanilla / andere Mods** → als One-Liner in
  `CustomCraftingRecipesConfig.DEFAULT_RECIPES` / `DEFAULT_SHAPELESS_RECIPES`
  (z.B. die fairen Rail-Upgrades). Dieses Modul ist auch für user-konfigurierbare Rezepte da.
- **Block-Drops** → `getDrops(BlockState, LootParams.Builder)` am Block überschreiben
  (siehe `ChunkLoaderRailBlock`), nicht per Loot-Table-JSON.
- **Loot-Tables einer fremden Mod** (deren eigener Erweiterungspunkt, z.B. eine bewusst leer
  ausgelieferte Tabelle) → per Code über `LootTableLoadEvent` abfangen (Name-Vergleich per
  `ResourceLocation`) und `event.setTable(...)` ersetzen — kein Compile-Dependency auf die
  fremde Mod nötig, nur `ModList.isLoaded(...)` zur Laufzeit. Vorlage:
  `EnhancedAiLeaderLootModule` (`onLootTableLoad` + `buildLeaderLootTable`).
- **Keine** `data/.../recipe/`- oder `loot_table/`-JSONs mehr anlegen. Keine Migration zu JSON geplant.
Details: `docs/custom_crafting_recipes.md`, `docs/enhanced_ai_leader_loot.md`.

## Worktrees
Do NOT use worktrees for this project. Edit files directly in the repository working copy.
