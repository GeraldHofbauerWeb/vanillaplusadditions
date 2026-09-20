# Pathfinder Quills

> **TL;DR** — Quark's Pathfinder's Quill becomes craftable: a Feather, an Eye of Ender and one
> block that stands for the biome you are looking for — Sand for the Desert, Podzol for an Old
> Growth Pine Taiga, Cherry Leaves for a Cherry Grove.

<!-- vpa:meta:start -->
|  |  |
|---|---|
| **Module ID** | `pathfinder_quills` |
| **Side** | Client + Server |
| **Requires** | [Quark](https://modrinth.com/mod/quark) <sub>tested 4.1-482</sub> |
| **Works with** | — |
| **Download** | [`vpa_pathfinder_quills.jar`](https://github.com/GeraldHofbauerWeb/vanillaplusadditions/releases/latest/download/vpa_pathfinder_quills.jar) · also needs `vpa_core` |
| **Config section** | `[modules.pathfinder_quills]` |
| **Since** | `v1.0.0-beta.73` |
<!-- vpa:meta:end -->

## What it does

Quark's Pathfinder's Quill (`quark:pathfinders_quill`) hunts down a biome for you: use it, it
searches in the background, and once it has a hit it becomes a map pointing at the place. Getting
hold of the right one is the problem. Quark hands the quill out through Cartographer and Wandering
Trader trades only, so which biome you can look for is whatever the trade rolls.

This module adds a crafting recipe per target biome — fifteen of them, exactly the fifteen biomes
Quark's own trades cover:

```
Feather  +  Eye of Ender  +  one block that stands for the biome
```

Position does not matter and no crafting table is needed; the 2×2 inventory grid takes it. What
does matter is that there is **exactly one of each** in the grid — a stack of three Sand plus a
Feather and an Eye produces nothing (see [In detail](#in-detail)).

The block is a leaf block for five of the fifteen entries and a defining ground, terrain or plant
block for the rest — Podzol for Old Growth Pine Taiga, Bamboo for Bamboo Jungle, Poppy for Flower
Forest. Among the five leaf entries Dark Forest is the odd one out: its entry is plain **Oak
Leaves**, not Dark Oak Leaves, even though dark oak is what the biome is built from — so that one
quill can be crafted from a tree in Plains or Forest, without ever seeing a Dark Forest.

Leaves keep their block form only under Shears or Silk Touch, but only two of the five leaf entries
are genuinely tied to the biome they point at: Mangrove Swamp and Cherry Grove, whose trees generate
in those biomes and nowhere else. Jungle Leaves come from Sparse Jungle and Bamboo Jungle as much as
from Jungle, and Acacia Leaves from Savanna Plateau and Windswept Savanna as much as from Savanna.
So the appeal of the quill — that the craft is the route to a *second* Cherry Grove rather than to
your first one — holds for those two entries, not for the table as a whole.

Everything past the crafting grid is Quark's. The search, the progress HUD, the retry behaviour and
the map it finally produces are untouched here — this module only puts the quill in your hands with
the target already stamped on it.

## In detail

### The table was read out of Quark's bytecode

Quark ships no published sources for this. The biome list and the overlay colours below were read
straight out of `PathfinderMapsModule.register()` and `PathfindersQuillItem` in `Quark-4.1-482.jar`
with `javap -c`, and re-checked against `PathfinderQuillRecipes.ENTRIES`: the same fifteen biomes,
in the same order, with the same colour constants. A crafted quill is therefore indistinguishable
from a traded one.

The duplicate `-14534897` on Swamp and Mangrove Swamp is Quark's own value, not a transcription
slip — both trades carry it.

### What counts as a valid grid

`PathfinderQuillRecipe` is a plain `CraftingRecipe` rather than a `ShapelessRecipe`, and its
`matches` is stricter than a shapeless recipe in one respect:

```java
if (entry == null || input.ingredientCount() != 3) {
    return false;
}
...
if (stack.getCount() != 1) {
    return false;
}
```

`CraftingInput.ingredientCount()` counts **non-empty slots**, not items, so the first check means
"three occupied slots, no more, no less". The second one is the surprise: a vanilla shapeless recipe
ignores stack sizes entirely, this one refuses any slot holding more than a single item. Drop a
stack of Sand into the grid next to the Feather and the Eye and there is no result and no
explanation.

Order is genuinely free. The three items are matched by an `else if` chain that takes the first
unclaimed role each stack fits, and anything that fits none of them fails the whole match — so a
second Feather, or a fourth item, ends the attempt immediately.

`canCraftInDimensions` returns `width * height >= 3`, which is what the recipe book uses to decide
whether a recipe fits the open grid. Matching itself never looks at the dimensions, so all fifteen
recipes work in the 2×2 inventory grid as well as on a table.

### What comes out

`assemble` and `getResultItem` both call the one Quark API this module touches:

```java
return entry == null ? ItemStack.EMPTY : PathfindersQuillItem.forBiome(biomeId, entry.color());
```

`forBiome` builds a stack of `PathfinderMapsModule.pathfinders_quill` and stamps three data
components on it: `TARGET_BIOME` (the biome id as a string), `BIOME_COLOR` (the int from the table)
and `IS_UNDERGROUND` — always `false`, because that is what `forBiome` hardcodes. It is the exact
call Quark makes itself when it fills the creative tab, which is why the crafted item and the
creative-tab item are byte-for-byte the same stack.

### How the recipes get into the game

There are no recipe JSONs. Per the house rule (see [Custom Crafting
Recipes](custom_crafting_recipes.md)), the fifteen recipes are built in code and injected into the
server's `RecipeManager` on every datapack reload, by a listener added in `AddReloadListenerEvent`:
the whole current recipe map is copied into a `LinkedHashMap`, the fifteen entries are added, and
`replaceRecipes` writes it back. Nothing vanilla or Quark owns is overridden — these are pure
additions under ids of the form `vanillaplusadditions:pathfinder_quill_<biome path>`.

Fourteen modules in this mod use that same merge-then-replace pattern — among them `mo_arrows`,
`tipped_arrows` and `custom_crafting_recipes` — each re-reading the manager when its own listener
runs, which is what lets them stack.

### Recipe book and JEI

JEI lists a recipe whether or not the player has unlocked it, so the fifteen appear there as soon as
the client has them. The recipe book is a different matter. It only ever shows collections the
player *knows*:

```java
list1.removeIf(p_100368_ -> !p_100368_.hasKnownRecipes());   // RecipeBookComponent
```

Vanilla recipes get that unlock from a generated recipe advancement. This repository ships no
advancements at all, and nothing here calls `awardRecipes`, so the only thing that unlocks a quill
recipe is crafting one: `ResultSlot.checkTakeAchievements` → `RecipeCraftingHolder.awardUsedRecipes`
→ `player.awardRecipes(...)`, which fires because the recipe is not `isSpecial()`. Craft one quill
and that recipe joins the book; until then it is absent from it. The consequence for
`doLimitedCrafting` worlds is in the limits table below.

## Items, blocks and recipes

The module registers no item and no block of its own. Its single registry entry is the recipe
serializer `vanillaplusadditions:pathfinder_quill`; the thing you craft is Quark's item.

| Target biome | Ingredient block | Overlay colour | Recipe id |
|---|---|---|---|
| Snowy Plains | Snow Block | `-8395521` · `#7FE4FF` | `pathfinder_quill_snowy_plains` |
| Windswept Hills | Gravel | `-7697782` · `#8A8A8A` | `pathfinder_quill_windswept_hills` |
| Dark Forest | Oak Leaves | `-16754422` · `#00590A` | `pathfinder_quill_dark_forest` |
| Desert | Sand | `-3360434` · `#CCB94E` | `pathfinder_quill_desert` |
| Savanna | Acacia Leaves | `-6576798` · `#9BA562` | `pathfinder_quill_savanna` |
| Swamp | Mud | `-14534897` · `#22370F` | `pathfinder_quill_swamp` |
| Mangrove Swamp | Mangrove Leaves | `-14534897` · `#22370F` | `pathfinder_quill_mangrove_swamp` |
| Old Growth Pine Taiga | Podzol | `-10796513` · `#5B421F` | `pathfinder_quill_old_growth_pine_taiga` |
| Flower Forest | Poppy | `-3258654` · `#CE46E2` | `pathfinder_quill_flower_forest` |
| Jungle | Jungle Leaves | `-14502400` · `#22B600` | `pathfinder_quill_jungle` |
| Bamboo Jungle | Bamboo | `-12721641` · `#3DE217` | `pathfinder_quill_bamboo_jungle` |
| Badlands | Terracotta | `-3768542` · `#C67F22` | `pathfinder_quill_badlands` |
| Mushroom Fields | Brown Mushroom | `-11713933` · `#4D4273` | `pathfinder_quill_mushroom_fields` |
| Ice Spikes | Packed Ice | `-14761783` · `#1EC0C9` | `pathfinder_quill_ice_spikes` |
| Cherry Grove | Cherry Leaves | `-1463832` · `#E9A9E8` | `pathfinder_quill_cherry_grove` |

Every recipe yields one quill, consumes all three inputs (none of them has a crafting remainder) and
sits in `CraftingBookCategory.MISC`. The hex values are the colour ints rendered out; all fifteen
are fully opaque.

<!-- vpa:config:start -->
## Configuration

Section `[modules.pathfinder_quills]` in `config/vanillaplusadditions-common.toml` (or `config/vpa_pathfinder_quills-common.toml` if you run the standalone jar).

Every module also has the universal `enabled` and `debug_logging` keys — see the [Configuration Guide](../guides/configuration.md).

This module has no settings of its own.
<!-- vpa:config:end -->

## Compatibility and known limits

| Limit | Effect |
|---|---|
| No Quark installed | The module is fully inert. `shouldInitialize()` asks `ModList.isLoaded("quark")`, and without it `onInitialize` never runs: no serializer, no reload listener, no recipes. |
| Quark installed, its *Pathfinder Maps* feature switched off | Our fifteen recipes still craft. The gate checks the mod, not Quark's own Zeta module toggle. What a disabled Quark feature does to the quill's search behaviour afterwards is Quark's business and was not tested here. |
| Quark's own trade config | `applyDefaultTrades`, the per-trade `enabled` flag and Quark's custom-trade list govern *trades*. Our table is a static copy of Quark's built-in list and follows none of them: turning a trade off leaves its recipe craftable. |
| Glimmering Weald and custom trades | Quark can offer a sixteenth quill (Glimmering Weald, colour `-13535930`) when its Stonelings and Glimmering Weald modules are on, and packs can add more through Quark's config. Neither has a recipe here. |
| A future Quark version | If Quark changes a biome or a colour, crafted quills silently stop matching traded ones. Nothing in the code detects that; the table would have to be re-read from the new jar. |
| A stack in the grid | `matches` rejects any slot with more than one item. Three Sand plus a Feather and an Eye gives no result, with nothing on screen to say why. |
| `doLimitedCrafting = true` | The recipes become uncraftable. `CraftingMenu.slotChangedCraftingGrid` only fills the result slot when `setRecipeUsed` succeeds, and under that gamerule it refuses any recipe the player has not unlocked — and nothing here ever grants the unlock. `/recipe give <player> vanillaplusadditions:pathfinder_quill_<biome>` is the way out. |
| Recipe book | Empty until you craft a quill; the craft itself is what unlocks the entry. JEI is unaffected. |
| Module switched off while the server runs | The listener is only attached while `isModuleEnabled()`, but the recipes already in the `RecipeManager` stay live until the next `/reload`. |
| Client with the module off, or without Quark | The fifteen recipes are synced to clients encoded with `vanillaplusadditions:pathfinder_quill`, and that registry entry exists only where Quark is present **and** the module is enabled — the config is `ModConfig.Type.COMMON`, so a client keeps its own copy of that switch and the server's setting does not travel. A client missing the entry cannot decode them. Read off the source; no such mismatch has been reproduced. |
| Standalone jar | `vpa_pathfinder_quills` is listed in `build.gradle`'s `standaloneModules` and needs `vpa_core`. Quark stays a runtime requirement there as in the bundle: without it the module registers nothing. |
| Modded target biomes | Recipe ids are built from the biome **path** only, so two targets with the same path in different namespaces would collide. Inert today — all fifteen entries are `minecraft:`. |

## Under the hood

Four source files, no mixins, no assets, no data files, no lang keys, no commands, no keybinds and
no client-only code.

| File | Role |
|---|---|
| `modules/pathfinder_quills/PathfinderQuillsModule.java` | Gate, serializer registration, the reload listener, the injection |
| `modules/pathfinder_quills/recipe/PathfinderQuillRecipes.java` | The fifteen-row table: biome id, ingredient item, colour |
| `modules/pathfinder_quills/recipe/PathfinderQuillRecipe.java` | `matches` / `assemble` / `getIngredients`; the only class that names a Quark type |
| `modules/pathfinder_quills/recipe/PathfinderQuillRecipeSerializer.java` | Hand-written `MapCodec` + `StreamCodec` |

**The serializer is hand-written** because vanilla's `SimpleCraftingRecipeSerializer` carries only a
`CraftingBookCategory`, and this recipe also has a `biomeId` field. The `StreamCodec` exists so the
recipe survives the trip to the client for the recipe book and the JEI preview; without it a client
would have the id and nothing else.

**The Quark gate is a class-loading gate, not just a behaviour gate.** `PathfinderQuillsModule`
itself names no Quark type — not in a field, not in a signature, not in `shouldInitialize()`. The
static `DeferredRegister` at the top of the class is initialised as soon as the module object is
constructed, but everything it touches eagerly (`DeferredRegister.create`, a constructor reference
to the serializer) is Quark-free; the serializer imports nothing but Mojang and Minecraft. The one
Quark-importing class, `PathfinderQuillRecipe`, is reached only after the gated
`register(getModEventBus())` or from inside the gated reload listener. Without Quark the residue is
one unused `DeferredRegister`. That is what the project's optional-mod rule demands, after the
beta.70 crash that came from an optional-mod type sitting in a gate class. It is reasoning about JVM
linkage, not a launch test.

Quark is deliberately **not** declared in `neoforge.mods.toml`, so there is no declared load order
against it; the runtime `ModList` check is the only link, and it holds because Quark's own item field
is filled during Quark's registration, long before our reload listener runs.

**Building it needs two jars.** `build.gradle` puts both `libs/Quark-4.1-482.jar` and
`libs/Zeta-1.1-40.jar` on the compile classpath — Zeta because `PathfindersQuillItem extends
ZetaItem` and `javac` has to resolve the supertype even though no line here names Zeta. Both are
`compileOnly` with no `localRuntime` on purpose, which means this module cannot be exercised in
`runClient` or `runServer` at all; it has to be verified from the deployed jar in a real 1.21.1
instance with Quark present. All three CI workflows download the pair, a lesson `code-quality.yml`
learned the hard way when the module first landed in `master`.

## See also

* [Custom Crafting Recipes](custom_crafting_recipes.md) — why every recipe in this mod is built in code
* [Configuration Guide](../guides/configuration.md) — where the config file lives
* [All modules](../../README.md#-modules)
