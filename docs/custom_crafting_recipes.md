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

## TODO / Roadmap

- [ ] **Migrate from code-injected recipes to proper JSON datapack recipes.**
  Today this module (and `flying_fish`) builds `ShapedRecipe`/`ShapelessRecipe` objects in
  Java and injects them into the `RecipeManager` via `replaceRecipes()` on
  `AddReloadListenerEvent`. That is a workaround — the *correct* way is to ship recipes as
  JSON datapack files, which the game loads natively.

  **Why it was done in code:** the mod's `data/vanillaplusadditions/recipes/` folder used the
  pre-1.21 **plural** name. Since MC 1.21 (snapshot 24w21a) the datapack folder is **singular**
  `recipe/` (likewise `loot_tables/` → `loot_table/`). Plural folders are silently ignored, so
  the JSON recipes never loaded — only the code-injected ones worked.

  **Correct 1.21.1 / NeoForge format** (`data/<namespace>/recipe/<name>.json`):
  ```json
  {
    "type": "minecraft:crafting_shaped",
    "category": "redstone",
    "pattern": ["R R", "RGR", "RDR"],
    "key": {
      "R": "minecraft:rail",
      "G": "minecraft:gold_ingot",
      "D": "minecraft:redstone"
    },
    "result": { "id": "minecraft:powered_rail", "count": 6 }
  }
  ```
  Note the 1.21 changes: folder is **`recipe`** (singular), result uses **`"id"`** (not
  `"item"`) plus **`"count"`**, and `key` values may be bare item-id strings.
  Ref: <https://docs.neoforged.net/docs/resources/server/recipes/>

  When migrating: move the config-driven recipes to generated JSON (ideally via NeoForge
  **data generation**), then drop the `replaceRecipes()` injection to avoid duplicate-ID
  collisions between the JSON recipe and the code-injected one.

---

## See also

- [Module Configuration Guide](MODULE_CONFIG_GUIDE.md)
