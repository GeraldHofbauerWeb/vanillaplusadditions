# Pathfinder Quills

## Overview

Quark's Pathfinder's Quill (`quark:pathfinders_quill`) is normally trade-only (Cartographers,
Wandering Trader). This module adds a crafting recipe per target biome: 1 Feather + 1 Eye of
Ender + 1 block tied to that biome (shapeless — any position in the grid).

Inactive without Quark installed (`ModList.isLoaded("quark")`).

## Where the data comes from

Quark ships no public sources for this. The biome list and the exact colors below were read
straight out of the decompiled bytecode of `PathfinderMapsModule.register()` and
`PathfindersQuillItem` in `Quark-4.1-482.jar` (`javap -c`), so a crafted quill looks and behaves
identically to a traded one — same 15 biomes Quark's own trades support, same overlay color. The
quill turns out to be a single item with a `TARGET_BIOME` string data component; Quark exposes a
public `PathfindersQuillItem.forBiome(String biomeId, int color)` factory that builds the correct
`ItemStack`, which is the only Quark API this module calls directly.

## Biome → ingredient block → color

| Biome | Block | Color (Quark's own) |
|-------|-------|----------------------|
| Snowy Plains | Snow Block | `-8395521` |
| Windswept Hills | Gravel | `-7697782` |
| Dark Forest | Oak Leaves | `-16754422` |
| Desert | Sand | `-3360434` |
| Savanna | Acacia Leaves | `-6576798` |
| Swamp | Mud | `-14534897` |
| Mangrove Swamp | Mangrove Leaves | `-14534897` |
| Old Growth Pine Taiga | Podzol | `-10796513` |
| Flower Forest | Poppy | `-3258654` |
| Jungle | Jungle Leaves | `-14502400` |
| Bamboo Jungle | Bamboo | `-12721641` |
| Badlands | Terracotta | `-3768542` |
| Mushroom Fields | Brown Mushroom | `-11713933` |
| Ice Spikes | Packed Ice | `-14761783` |
| Cherry Grove | Cherry Leaves | `-1463832` |

Biomes with a signature tree (Dark Forest, Savanna, Jungle, Mangrove Swamp, Cherry Grove) use
that tree's own leaves. Note this is **not** obtainable without having reached the biome already
(leaves only drop from that biome's own trees, and need Shears/Silk Touch to keep as a block
rather than a sapling) — a deliberate tradeoff for thematic accuracy over accessibility.

## Implementation

See `modules/pathfinder_quills/`:
- `recipe/PathfinderQuillRecipes.java` — the table above, in code.
- `recipe/PathfinderQuillRecipe.java` — a plain (non-special) `CraftingRecipe`, so it shows up in
  the recipe book and JEI automatically, no `@JeiPlugin` needed.
- `recipe/PathfinderQuillRecipeSerializer.java` — hand-written serializer (vanilla's
  `SimpleCraftingRecipeSerializer` only carries a category; this recipe also needs `biomeId`).
- `PathfinderQuillsModule.java` — registers the serializer, injects the 15 recipes into the
  `RecipeManager` on every reload (see the "recipes always in code" convention below).

Recipes are pure additions (`vanillaplusadditions:pathfinder_quill_<biome>`), nothing is
overridden.

## Convention: recipes are always done in code

Same as `custom_crafting_recipes` — see `docs/custom_crafting_recipes.md` and CLAUDE.md. No
recipe JSON datapack files for this mod; recipes go through `RecipeManager` injection on
`AddReloadListenerEvent`.
