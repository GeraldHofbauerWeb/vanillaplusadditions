# VanillaPlusAdditions — Claude instructions

## Branching / Workflow
Gerry hat für dieses Projekt explizit festgelegt: **direkt auf `master` arbeiten, kein PR-Flow.**
Es gibt kein `staging`, kein PR-Flow — direkt auf `master`.
Lead-Dev darf Commits direkt auf `master` pushen. (Eigene Feature-PRs schreibe ich generell
nicht mehr; ich merge nur Mac-Claudes PRs.)

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
