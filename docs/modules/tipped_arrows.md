# Tipped Arrows from Potions

> **TL;DR** — Tipped arrows can be crafted with an ordinary potion in the middle of eight arrows,
> so you no longer need Dragon's Breath and a brewing detour just to tip a stack.

<!-- vpa:meta:start -->
|  |  |
|---|---|
| **Module ID** | `tipped_arrows` |
| **Side** | Client + Server |
| **Requires** | — |
| **Works with** | [JEI](https://modrinth.com/mod/jei) |
| **Download** | bundle only — no standalone jar |
| **Config section** | `[modules.tipped_arrows]` |
| **Since** | `v1.0.0-beta.73` |
<!-- vpa:meta:end -->

## What it does

Craft tipped arrows with a **normal potion** instead of a lingering one. Lingering potions still
work; only the recipe's centre ingredient is widened, and no new tipped arrow types are added.

```
 A A A      A = Arrow
 A P A      P = Potion  or  Lingering Potion
 A A A      → 8 Tipped Arrows carrying the centre potion's effect
```

This is vanilla's own recipe, widened in place — same id `minecraft:tipped_arrow`, same layout, same
eight-arrow output. There is no second recipe next to it, so nothing changes about how the craft
looks or what it costs beyond the bottle you put in the middle. Turn the module off and vanilla's
lingering-only recipe is back untouched: no config migration, no stranded items.

## Why it exists

Vanilla's tipped arrow recipe accepts `minecraft:lingering_potion` in the centre slot and nothing
else. A lingering potion is a splash potion plus Dragon's Breath, and Dragon's Breath means an ender
dragon — so the cheapest possible arrow of, say, Slowness sits behind a boss fight and two brewing
steps, for an effect the plain potion already carries.

The whole difference is one predicate:

```java
private static boolean isPotionSource(ItemStack stack) {
    return stack.is(Items.POTION) || stack.is(Items.LINGERING_POTION);
}
```

Everything else in `PotionTippedArrowRecipe` — the 3×3 layout, the eight arrows around the centre,
the eight-arrow result with `POTION_CONTENTS` copied across — mirrors what vanilla already did.

## In detail

### What counts as a valid grid

`matches()` walks all nine slots and rejects the craft on the first thing that is not exactly right:

| Slot | Requirement | Notes |
|---|---|---|
| The eight outer slots | `minecraft:arrow` | Spectral or already-tipped arrows do not match. |
| The centre slot | `minecraft:potion` or `minecraft:lingering_potion` | **Splash** potions are not accepted — same as vanilla. |
| Any slot | must not be empty | A partly filled grid never matches. |

The grid has to be exactly 3×3 (`input.width() != 3 || input.height() != 3` → no match), so the 2×2
inventory grid is out. `canCraftInDimensions` is the more generous `width >= 3 && height >= 3`,
which is the same asymmetry vanilla's recipe carries: a modded grid bigger than 3×3 advertises the
recipe but `matches()` still refuses it.

### What comes out

`assemble()` builds `new ItemStack(Items.TIPPED_ARROW, 8)` and copies
`DataComponents.POTION_CONTENTS` from the centre item verbatim. Whatever sits in that component
travels — the potion holder, custom effects, a custom colour, potions added by other mods. Nothing
else is copied: a custom item name on the bottle stays on the bottle.

`getRemainingItems()` is not overridden, so the glass bottle behaves exactly as it does for vanilla's
recipe — it is consumed with the potion.

### The centre item's contents are never inspected

`isPotionSource` tests the item type and only the item type. It never asks what is in the bottle, so
every brew qualifies, including the ones with no effect at all: Water Bottle, Mundane, Thick,
Awkward. Each yields eight tipped arrows carrying exactly those contents. That matches vanilla's
own behaviour for the lingering variant, which is equally uninterested in the contents — the
difference is that a plain, non-lingering water bottle now works too.

<!-- TODO: vanilla calls water-content tipped arrows "Arrows of Splashing" and normally wants a
     lingering water bottle for them. That naming and the exact contents of a fresh water bottle are
     vanilla behaviour and are not provable from this repository, so they are left unstated. -->

One edge case falls out of the same lines: a potion stack whose `potion_contents` component has been
removed still matches, and `result.set(POTION_CONTENTS, null)` simply leaves the component off the
result. You get eight plain, effectless tipped arrows rather than a failed craft.

### When the swap happens

On every datapack reload and every server start, not once at load. The reload listener waits on the
reload barrier before it writes, so it lands after the datapack's own recipes have been parsed — a
datapack that ships its own `minecraft:tipped_arrow` is therefore overwritten again while the module
is on.

<!-- vpa:config:start -->
## Configuration

Section `[modules.tipped_arrows]` in `config/vanillaplusadditions-common.toml`.

Every module also has the universal `enabled` and `debug_logging` keys — see the [Configuration Guide](../guides/configuration.md).

This module has no settings of its own.
<!-- vpa:config:end -->

## Compatibility and known limits

| Limit | Effect |
|---|---|
| Not in the vanilla recipe book | `PotionTippedArrowRecipe` extends `CustomRecipe` and overrides neither `getResultItem` nor `isSpecial`, so it stays a special recipe with an empty result item — the recipe book cannot surface it, exactly as it cannot surface vanilla's. |
| Without JEI | Nothing is lost functionally: the recipe works at any crafting table, it is simply invisible in every recipe browser. |
| With JEI | One display entry **per registered potion** — flavourless mundane/thick/awkward brews and every modded potion included — so the crafting category gets long. |
| `/vpa module disable tipped_arrows` | Not instant. The enabled check runs when the reload listener is added, so the already-replaced recipe stays live until the next datapack reload. |
| `/vpa module enable tipped_arrows` | Cannot work at runtime at all. `onInitialize()` only runs for modules that were enabled at startup, so for a module that started disabled the event-bus registration never happened and there is no handler to enable. Restart with the config set. |
| Client with the module off, server with it on | **Suspected hazard, not verified in game.** The serializer is registered inside `onInitialize()`, so a disabled module means `vanillaplusadditions:potion_tipped_arrow` is absent from the client's registry while the server syncs a `minecraft:tipped_arrow` recipe that needs it. Keep both sides configured the same. This is a mod-wide pattern, not specific to this module. |

With the [Stackables](stackables.md) module on, `minecraft:potion` and `minecraft:tipped_arrow` both
get a default max stack of `default_potion_stack_size` (64 by default), so eight-at-a-time crafting
fills a slot quickly. There is no code link between the two modules in either direction.

## Under the hood

Three Java files, no mixins, no access transformer, no assets, no lang keys, no data files — the
module is the recipe and its two pieces of plumbing.

**The replacement.** `AddReloadListenerEvent` (the module's only event handler, and it returns
early unless `isModuleEnabled()`) adds a `PreparableReloadListener` named
`vanillaplusadditions_tipped_arrow_recipe`. It waits on the barrier, then on the game thread copies
every recipe currently in the `RecipeManager` into a `LinkedHashMap`, overwrites the single key
`ResourceLocation.withDefaultNamespace("tipped_arrow")` — `LinkedHashMap.put` keeps the original
insertion position — and calls `recipeManager.replaceRecipes(...)`. This snapshot-and-replace dance
is house style rather than a one-off: twelve other modules do the same thing, and because each
listener snapshots when its own task runs on the game thread, they compose instead of clobbering one
another. JSON datapack recipes do not load reliably in this mod, which is why every recipe here is
built in code — see [Custom Crafting Recipes](custom_crafting_recipes.md).

**The serializer.** `SimpleCraftingRecipeSerializer<PotionTippedArrowRecipe>`, registered through a
`DeferredRegister` as `vanillaplusadditions:potion_tipped_arrow`. It is not a craftable thing; it
exists so the replaced `minecraft:tipped_arrow` recipe can be synced to clients at all.

**Why the side is "both".** The gameplay half is server-side — the event carries the server
resources, and `matches`/`assemble` run in the server's crafting menu. But the `DeferredRegister`
goes on the *mod* event bus, i.e. both physical sides, and the client instantiates
`PotionTippedArrowRecipe` itself when the server syncs the recipe. The JEI plugin is client-only on
top of that. Contrast `custom_crafting_recipes`, which injects plain vanilla `ShapedRecipe` objects
and therefore runs no code of its own on the client.

**JEI.** `TippedArrowsJeiPlugin` is display-only. It loops over all of `BuiltInRegistries.POTION`
and generates one plain `ShapedRecipe` per potion (`AAA`/`APA`/`AAA`, centre =
`Ingredient.of(normal potion, lingering potion)`, result = 8 tipped arrows with that potion's
contents) under ids `vanillaplusadditions:tipped_arrow_display/<potion path>`. These go straight to
`registration.addRecipes(RecipeTypes.CRAFTING, ...)` and never enter the `RecipeManager`. JEI is
`compileOnly` plus `localRuntime` and appears in no dependency block of `neoforge.mods.toml`; the
`@JeiPlugin` class is only ever loaded by JEI's own annotation scan, so no `ModList.isLoaded` gate is
needed. The plugin re-checks `isModuleEnabled("tipped_arrows")` itself, so a disabled module shows
nothing.

**Testing.** This repository has no unit tests, and nothing in it records an in-game test of this
module.

## See also

* [Configuration Guide](../guides/configuration.md) — where the config file lives
* [Custom Crafting Recipes](custom_crafting_recipes.md) — why recipes are built in code here
* [Stackables](stackables.md) — potion and tipped arrow stack sizes
* [All modules](../../README.md#-modules)
