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
