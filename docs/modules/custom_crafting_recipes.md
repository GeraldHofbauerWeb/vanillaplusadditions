# Custom Crafting Recipes

> **TL;DR** — Extra crafting recipes written straight into the config file instead of a datapack,
> plus a few that ship enabled: six plain rails upgrade into six powered, detector or activator
> rails, leather and string make a Bundle, and with Create installed a Netherite Ingot costs one
> gold ingot instead of four.

<!-- vpa:meta:start -->
|  |  |
|---|---|
| **Module ID** | `custom_crafting_recipes` |
| **Side** | Server only |
| **Requires** | — |
| **Works with** | [Create](https://modrinth.com/mod/create) <sub>tested 6.0.10</sub>, [Overpacked](https://modrinth.com/mod/overpacked) <sub>tested 2.0.1</sub> |
| **Download** | [`vpa_custom_crafting_recipes.jar`](https://github.com/GeraldHofbauerWeb/vanillaplusadditions/releases/latest/download/vpa_custom_crafting_recipes.jar) · also needs `vpa_core` |
| **Config section** | `[modules.custom_crafting_recipes]` |
| **Since** | `v0.13.0` |
<!-- vpa:meta:end -->

## What it does

Two lists in the module config — one for shaped recipes, one for shapeless ones. Each entry is a
single line of text: an id, a result, a grid and what the letters in it mean. On every datapack
reload the module parses both lists and merges what comes out into the game's recipe set, so
`/reload` is enough to try a recipe out. No datapack, no restart, no JSON.

An entry whose recipe id is new is **added**. An entry that reuses an id that already exists
**replaces** that recipe — which is how the shipped Netherite entry swaps vanilla's scrap-and-gold
recipe for one of its own.

Six recipes ship in the defaults and are live as soon as the module is:

* **Rail upgrades** — six plain rails plus the vanilla trimmings become six powered, detector or
  activator rails. Purely additive; the vanilla recipes still work.
* **Netherite Ingot** — four Netherite Scrap, four Create Powdered Obsidian and one Gold Ingot.
  Replaces the vanilla recipe. Inert without Create, in which case vanilla's stands.
* **Bundle** — one leather plus one string, shapeless.
* **Giant Backpack** — needs Overpacked and Create; Overpacked ships no recipe for it of its own.

They are listed with their grids under [Items, blocks and recipes](#items-blocks-and-recipes).
Delete the lines you do not want — they are ordinary config entries, not code.

Nothing here runs on the client, and the module registers no items, blocks or text of its own.

## In detail

### A shaped entry

```
recipe_id;result_item;result_count;pattern;keys
```

| Field | Meaning |
|---|---|
| `recipe_id` | `namespace:path`. A new id adds a recipe, an existing one replaces it. |
| `result_item` | Item id of the output. |
| `result_count` | 1–64. Checked by the config validator and again while parsing. |
| `pattern` | The grid rows, `R R\|RGR\|RDR` or `"R R" "RGR" "RDR"`. |
| `keys` | Comma-separated `X=ingredient` pairs, e.g. `A=minecraft:leather,B=#minecraft:planks`. |

The line is split on `;` with a limit of five, so the keys part may itself contain semicolons — they
then end up inside an ingredient id and the entry fails to parse. Ingredients are item ids or tags
with a leading `#`; a space in the pattern is an empty cell.

Four things about the pattern are worth knowing before you write one:

* **Rows in quotes are taken verbatim.** The parser first collects everything matching `"([^"]+)"`,
  and if that yields at least one row it never looks at the rest of the field.
* **Unquoted rows are trimmed.** A row that begins or ends with an empty cell loses it, the grid
  comes out ragged, and vanilla's pattern check throws the whole entry away. That is what the quoted
  form is for.
* Rows are separated by `|`. If the field contains no `|` at all, commas are accepted instead —
  undocumented in the config comment, but it works.
* A trailing empty row disappears (`String.split` with the zero limit drops trailing blanks), so
  `AAA|` is one row, not two.

A key symbol has to be exactly one character and must not be a space — each of those has its own
error message.

**`minecraft:air` is rejected, not accepted as "empty".** Unknown ids are detected by looking the id
up in `BuiltInRegistries.ITEM` and comparing the result against `Items.AIR` — and air legitimately
resolves to `Items.AIR`, so writing it out lands on `Unknown result item: minecraft:air` and the
entry is dropped. Empty cells are spaces in the pattern.

### A shapeless entry

```
ingredient1,ingredient2,...->result_item[;result_count[;recipe_id]]
```

`result_count` defaults to 1. The recipe id, when omitted, is built as
`minecraft:shapeless_<result path>` — always in the `minecraft` namespace, never in
`vanillaplusadditions`. The shipped bundle entry therefore lands on `minecraft:shapeless_bundle` and
leaves vanilla's `minecraft:bundle` id alone, which is why it adds rather than replaces.

### Adding versus replacing

The merge is a `LinkedHashMap` keyed by recipe id and seeded from the recipe set the reload has just
built from the datapacks, so a few consequences follow directly:

* **Nothing is destroyed permanently.** Every reload starts again from the datapack set and the
  config entries are re-applied on top. Remove an entry — or the mod it needs — and the original
  recipe is back after the next `/reload`.
* **The map knows nothing about recipe types.** It is keyed by id alone, so reusing the id of a
  smelting, stonecutting or modded machine recipe removes that recipe and puts a crafting-table
  recipe in its place.
* **Whatever refers to a recipe by id keeps working.** Vanilla's
  `advancement/recipes/misc/netherite_ingot.json` triggers on `recipe_unlocked` for
  `minecraft:netherite_ingot` and rewards the same id; the replacement keeps that id, so picking up
  Netherite Scrap still unlocks it in the recipe book. The five *added* ids have no advancement
  anywhere in this repository, so nothing grants them — read off the shipped files, not tested in
  game.
* **Group and category are not configurable.** Every recipe is built with an empty group and
  `CraftingBookCategory.MISC`. Vanilla's netherite recipe carries `"group": "netherite_ingot"`,
  which the replacement loses, so it no longer shares a recipe-book slot with the
  block-to-ingot recipe.
* **Duplicate ids inside the config are not silent.** The shaped list is parsed first, then the
  shapeless one, against one shared set of seen ids. A repeat logs `WARN Duplicate custom recipe id
  in config. Last one wins: …` and the earlier holder is removed from the batch, so a shapeless
  entry can override a shaped one.

### When an entry is dropped

Everything is per entry. One bad line costs that line, never the list.

| Situation | Log | Result |
|---|---|---|
| Item id from a mod that is not loaded — namespace is neither `minecraft` nor `neoforge` and `ModList` does not know it | one `DEBUG` per entry plus a single `INFO` summary naming the namespaces | entry skipped, everything else loads |
| Item id wrong while its mod *is* installed | `ERROR` `Invalid custom crafting recipe definition` plus `Reason: Unknown result item: …` | entry skipped |
| Grid malformed — ragged rows, more than 3×3, a symbol used but not defined | `ERROR` — the same pair when the exception is an `IllegalArgumentException`, otherwise `Failed to parse custom crafting recipe` with a stack trace | entry skipped |

The split between the first two rows is the point of the design: a recipe extension for a mod the
pack does not have is expected, not a defect, so it stays quiet. A typo is loud.

**The mod gate only covers item ids.** A `#…` ingredient is turned straight into a tag ingredient
without ever touching the registry, so an entry whose ingredients are all tags is registered
whatever is installed — with an empty tag it is simply uncraftable rather than skipped.

## Items, blocks and recipes

The module registers no items and no blocks. It ships six recipes.

### The rail upgrades

```
 R . R        R = Rail                  6x Powered Rail
 R G R        G = Gold Ingot            vanillaplusadditions:powered_rail_from_rails
 R D R        D = Redstone Dust
```
```
 R . R        R = Rail                  6x Detector Rail
 R P R        P = Stone Pressure Plate  vanillaplusadditions:detector_rail_from_rails
 R D R        D = Redstone Dust
```
```
 R S R        R = Rail                  6x Activator Rail
 R T R        S = Stick                 vanillaplusadditions:activator_rail_from_rails
 R S R        T = Redstone Torch
```

`.` marks an empty cell. Each one is vanilla's own grid with the six metal ingots swapped for six
rails; the powered rail
additionally keeps one gold ingot where vanilla has the stick, so it does not become gold-free:

| Six of… | Vanilla | Here |
|---|---|---|
| Powered Rail | 6 Gold Ingot + 1 Stick + 1 Redstone | 6 Rail + 1 Gold Ingot + 1 Redstone |
| Detector Rail | 6 Iron Ingot + 1 Stone Pressure Plate + 1 Redstone | 6 Rail + 1 Stone Pressure Plate + 1 Redstone |
| Activator Rail | 6 Iron Ingot + 2 Stick + 1 Redstone Torch | 6 Rail + 2 Stick + 1 Redstone Torch |

Sixteen rails cost six iron ingots and a stick, so six rails are worth about 2¼ iron. That is the
whole idea: the metal frame is the expensive part of a special rail, and a mineshaft's worth of
plain rails is suddenly worth carrying home. The vanilla recipes keep their own ids and are
untouched.

### Netherite Ingot

```
 P S P        P = Powdered Obsidian (Create)     1x Netherite Ingot
 S G S        S = Netherite Scrap                minecraft:netherite_ingot  ← replaces vanilla
 P S P        G = Gold Ingot
```

Vanilla's recipe is shapeless, 4 Netherite Scrap + 4 Gold Ingot. Here four of those gold ingots
become four Powdered Obsidian and **one gold ingot stays in the middle** — the trade is most of the
gold, not all of it. Create makes Powdered Obsidian by crushing obsidian: one crushing recipe,
`processing_time` 500, with a 75 % chance of the obsidian coming back out alongside the powder. So
the cost moves from gold to obsidian and machine time.

Without Create the entry is skipped and vanilla's shapeless recipe stands, unchanged.

### Bundle

Shapeless, auto-generated id `minecraft:shapeless_bundle`: **1 Leather + 1 String → 1 Bundle**.

In 1.21.1 vanilla's own bundle recipe (6 Rabbit Hide + 2 String) lives inside the optional `bundle`
feature datapack — `data/minecraft/datapacks/bundle/data/minecraft/recipe/bundle.json` — so in an
ordinary world this entry is the only recipe for a Bundle there is, and a much cheaper one.

### Giant Backpack

```
 C D C        A = Leather                        1x overpacked:giant_backpack
 A B A        B = Item Vault (Create)            vanillaplusadditions:giant_backpack
 A A A        C = String
              D = Andesite Alloy (Create)
```

Five leather, two string, one Item Vault, one Andesite Alloy. Overpacked 2.0.1 ships exactly two
crafting recipes of its own — the Backpack Pocket and the dye recipe — and none for the Giant
Backpack itself. The entry needs both mods: Overpacked for the result, Create for two ingredients.
The result item is resolved first, so a pack without Overpacked reports `overpacked` in the summary
line even when Create is missing as well.

### Writing your own

```toml
[modules.custom_crafting_recipes]
    recipes = [
        "mypack:saddle;minecraft:saddle;1;LLL|L L;L=minecraft:leather",
        "mypack:sand_cross;minecraft:glass;4;\" S \"|\"SSS\"|\" S \";S=minecraft:sand"
    ]
    shapeless_recipes = [
        "minecraft:paper,minecraft:paper,minecraft:paper->minecraft:book;1;mypack:book_from_paper"
    ]
```

The first line needs no quoting: no row begins or ends with an empty cell. The second one does, and
because the config file is TOML those quotes have to be escaped as `\"` inside the entry. The
shapeless line spells out both optional fields; dropping `;1;mypack:book_from_paper` would do the
same thing under the auto-generated id `minecraft:shapeless_book`.

<!-- vpa:config:start -->
## Configuration

Section `[modules.custom_crafting_recipes]` in `config/vanillaplusadditions-common.toml` (or `config/vpa_custom_crafting_recipes-common.toml` if you run the standalone jar).

Every module also has the universal `enabled` and `debug_logging` keys — see the [Configuration Guide](../guides/configuration.md).

| Key | Type | Default | Range | Effect |
|---|---|---|---|---|
| `recipes` | list | `List.of(SAMPLE_RECIPE, POWERED_RAIL_FROM_RAILS, DETECTOR_RAIL_FROM_RAILS, ACTIVATOR_RAIL_FROM_RAILS, NETHERITE_INGOT_FROM_POWDERED_OBSIDIAN) = ["vanillaplusadditions:giant_backpack;overpacked:giant_backpack;1;CDC\|ABA\|AAA;A=minecraft:leather,B=create:item_vault,C=minecraft:string,D=create:andesite_alloy", "vanillaplusadditions:powered_rail_from_rails;minecraft:powered_rail;6;R R\|RGR\|RDR;R=minecraft:rail,G=minecraft:gold_ingot,D=minecraft:redstone", "vanillaplusadditions:detector_rail_from_rails;minecraft:detector_rail;6;R R\|RPR\|RDR;R=minecraft:rail,P=minecraft:stone_pressure_plate,D=minecraft:redstone", "vanillaplusadditions:activator_rail_from_rails;minecraft:activator_rail;6;RSR\|RTR\|RSR;R=minecraft:rail,S=minecraft:stick,T=minecraft:redstone_torch", "minecraft:netherite_ingot;minecraft:netherite_ingot;1;PSP\|SGS\|PSP;P=create:powdered_obsidian,S=minecraft:netherite_scrap,G=minecraft:gold_ingot"]` | no spec range; per-entry validator requires 5 semicolon-separated parts, two parsable ResourceLocations and result_count 1-64 | List of custom SHAPED recipes in the format recipe_id;result_item;result_count;pattern;keys (pattern as AAA\|BBB\|AAA or "AAA" "BBB" "AAA"; keys as A=minecraft:green_wool,B=minecraft:chest; ingredients may be tags with a leading #). A recipe_id that matches an existing recipe replaces it. |
| `shapeless_recipes` | list | `List.of(SAMPLE_SHAPELESS_RECIPE) = ["minecraft:leather,minecraft:string->minecraft:bundle;1"]` | no spec range; per-entry validator requires an -> arrow, a non-empty ingredient part, a parsable result ResourceLocation and, if given, result_count 1-64 and a parsable recipe_id | List of custom SHAPELESS recipes in the format ingredient1,ingredient2,...->result_item[;result_count[;recipe_id]], with item IDs or #tags as ingredients. Omitting recipe_id auto-generates minecraft:shapeless_<result path>. |
<!-- vpa:config:end -->

## Compatibility and known limits

| Limit | Effect |
|---|---|
| Create not installed | The Netherite and the Giant Backpack entries are skipped; vanilla's scrap-and-gold recipe stands. One `INFO` line names `create`. The module keeps running and the rail and bundle entries still load. |
| Overpacked not installed | The Giant Backpack entry is skipped, nothing else is lost. |
| Tag ingredients are never mod-gated | Only item ids are checked against `ModList`. A recipe built entirely from `#tags` is registered whatever is installed — an empty tag makes it uncraftable rather than absent. |
| Reusing the id of a non-crafting recipe | The merge is keyed by id alone, so the original recipe of whatever type disappears and a crafting-table recipe takes its id. |
| More than nine shapeless ingredients | Nothing checks the count. The recipe is built and can never match a 3×3 grid. Shaped grids are validated by vanilla's `ShapedRecipePattern`, so an oversized one is dropped with an error instead. |
| An existing config keeps its old defaults | The default list is only written when the file is created. A config from before an entry was added to the defaults keeps the shorter list — observed on an instance here whose `recipes` still holds only the four pre-Netherite entries. Add the line by hand. |
| Already-connected clients | Not established here. `replaceRecipes` runs server-side inside the reload listener, and this repository contains no recipe-sync code for the module; what a client that is already connected sees is whatever vanilla and NeoForge do on a reload. |
| The recipe book | Vanilla grants a recipe through an advancement reward, and no advancement of any kind ships in this repository, so nothing unlocks the five added ids on its own. The replaced `minecraft:netherite_ingot` keeps vanilla's. Deduced from the files, not tested in game. |
| Module turned off | `AddReloadListenerEvent` is the only hook and it is gated on the module, so a toggle takes effect on the next `/reload` or restart — the recipe set then comes straight from the datapacks again. |
| An entry the config validator rejects | Never reaches the parser. What `ModConfigSpec` then does with the list is NeoForge's business and is not established here. |

## Under the hood

`onInitialize` does one thing: `NeoForge.EVENT_BUS.register(this)`. The only handler is
`onAddReloadListener(AddReloadListenerEvent)`, gated on `isModuleEnabled()`, and all it does is add
one `PreparableReloadListener` named `vanillaplusadditions_custom_crafting_recipes` holding the
`RecipeManager` from `event.getServerResources()`:

```java
return preparationBarrier.wait(Unit.INSTANCE)
        .thenRunAsync(() -> applyConfiguredRecipes(recipeManager), gameExecutor);
```

There is nothing to prepare off-thread — the work is a config parse — so the listener waits at the
barrier and does everything in the apply stage on the game executor. `applyConfiguredRecipes` copies
`recipeManager.getRecipes()` into a `LinkedHashMap` keyed by `RecipeHolder.id()`, puts its own
holders in (counting adds and replaces by whether `Map.put` returned anything) and hands the whole
collection back through `RecipeManager.replaceRecipes`. One `INFO` line per reload:
`Applied {} custom recipes ({} added, {} replaced).`

Twelve other modules use the same injection — `flying_fish`, `cat_guardian`, `battle_dogs`,
`end_conduit`, `minecart_chunk_loading`, `pathfinder_quills`, `tipped_arrows` and the rest. Each one
copies the recipe map as it finds it and hands back a full replacement, so they accumulate rather
than overwrite one another.

**Logging.** The module's `debug_logging` key gates exactly one line, `No custom crafting recipes
configured.` The per-entry skip lines are plain `logger.debug` calls on the module's SLF4J logger
and follow the log level instead, so turning `debug_logging` on does not bring them out.

**No assets at all.** No lang keys — every log line is hardcoded English and there is no
player-facing text — no mixins, no data files, no registry objects, no commands, no keybinds. The
standalone entry in `build.gradle` is accordingly the bare two-field form: `vpa_custom_crafting_recipes`
plus `vpa_core`, with nothing to ship alongside.

**A dead TODO.** The comment above `onAddReloadListener` proposes migrating to
`data/<ns>/recipe/*.json` and points at a "TODO / Roadmap" section of this page. That section does
not exist, and the convention below is the opposite of it. Treat the comment as dead text.

### Convention: recipes and block loot are always done in code

**Do not add recipe or loot-table JSON datapack files to this mod** — they do not load reliably
here, confirmed repeatedly with both the pre-1.21 plural folders *and* the correct 1.21 singular
`recipe/` and `loot_table/` ones. Everything is registered in code, split by ownership:

* **Our own recipes, for our items and blocks** → in the owning module itself, via a `RecipeManager`
  injection on `AddReloadListenerEvent`, gated on `isModuleEnabled()`, so the item is craftable
  exactly while the module is active. Template: `MinecartChunkLoadingModule` (`onAddReloadListener`
  + `applyChunkLoaderRailRecipe`); same pattern in `FlyingFishModule`.
* **Recipe extensions for vanilla or another mod** → a one-line entry in `DEFAULT_RECIPES` /
  `DEFAULT_SHAPELESS_RECIPES` here, like the fair rail upgrades. This module is also the home for
  user-configurable recipes.
* **Block drops** → override `getDrops(BlockState, LootParams.Builder)` on the block, see
  `ChunkLoaderRailBlock`, instead of shipping a loot-table JSON.
* **A foreign mod's loot table** → intercept `LootTableLoadEvent` and `setTable(…)`, see
  `EnhancedAiLeaderLootModule`; that needs no compile dependency on the other mod.

This is intentional and final — there is no plan to migrate to JSON datapacks.

## See also

* [Configuration Guide](../guides/configuration.md) — where the config file lives
* [Config reference](../reference/config.md) — every module's keys in one place
* [Pathfinder Quills](pathfinder_quills.md) — a whole recipe family in code, with its own serializer
* [Minecart Chunk Loading](minecart_chunk_loading.md) — the template for a module's own recipe
* [All modules](../../README.md#-modules)
