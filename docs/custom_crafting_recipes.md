# Custom Crafting Recipes

## Overview

Lets you define extra shaped or shapeless crafting recipes entirely from config — no datapack
or recipe JSON needed. Recipes are (re)loaded on every resource reload, so changes apply on
`/reload` without restarting the server.

---

## Shaped recipes (`recipes`)

Format (semicolon-separated):

```
recipe_id;result_item;result_count;pattern;keys
```

| Field          | Meaning                                                                  |
|-----------------|---------------------------------------------------------------------------|
| `recipe_id`     | `namespace:path` — must be unique; a duplicate ID overrides the earlier one |
| `result_item`   | Output item ID                                                            |
| `result_count`  | Output stack size, 1–64                                                  |
| `pattern`       | Grid rows separated by `\|`, e.g. `CDC\|ABA\|AAA`                        |
| `keys`          | Comma-separated `letter=ingredient` pairs, e.g. `A=minecraft:leather,B=#minecraft:planks` |

Ingredients can be an item ID (`minecraft:stone`) or a tag (`#minecraft:planks`). A space in
the pattern means an empty grid cell.

Example (default):
```
vanillaplusadditions:giant_backpack;overpacked:giant_backpack;1;CDC|ABA|AAA;A=minecraft:leather,B=create:item_vault,C=minecraft:string,D=create:andesite_alloy
```

---

## Shapeless recipes (`shapeless_recipes`)

Format:

```
ingredient1,ingredient2,...->result_item[;result_count[;recipe_id]]
```

- Ingredients are comma-separated item IDs or `#tag` references.
- `result_count` and `recipe_id` are optional (default count `1`; ID auto-generated if omitted).

Example (default):
```
minecraft:leather,minecraft:string->minecraft:bundle;1
```

---

## Behaviour notes

- Invalid entries are skipped (logged, not fatal) — a typo in one recipe won't break the rest.
- Custom recipes are merged into the recipe manager alongside vanilla/datapack recipes on
  every reload.
- Enable [debug logging](DEBUG_LOGGING_CONFIG.md) for this module to see every recipe that
  gets loaded.

---

## Convention: recipes (and block loot) are always done in code

**Do NOT add recipe or loot-table JSON datapack files to this mod** — they do not load
reliably here (confirmed repeatedly with both pre-1.21 plural folders *and* the correct 1.21
singular `recipe/`/`loot_table/` folders). Register everything in code, split by ownership:

- **Our own recipes (for our items/blocks)** → register **in the owning module itself**, via a
  `RecipeManager` injection on `AddReloadListenerEvent`, gated on `isModuleEnabled()`, so the
  item is craftable whenever the module is active. Template: `MinecartChunkLoadingModule`
  (`onAddReloadListener` + `applyChunkLoaderRailRecipe`); same pattern as `FlyingFishModule`.
- **Recipe extensions for vanilla / other mods** → add a one-line entry to `DEFAULT_RECIPES` /
  `DEFAULT_SHAPELESS_RECIPES` here (e.g. the fair rail upgrades). This module is also the home
  for user-configurable recipes.
- **Block drops** → override `getDrops(BlockState, LootParams.Builder)` on the block (see
  `ChunkLoaderRailBlock`) instead of shipping a loot-table JSON.

This is intentional and final — there is no plan to migrate to JSON datapacks.

---

## See also

- [Module Configuration Guide](MODULE_CONFIG_GUIDE.md)
