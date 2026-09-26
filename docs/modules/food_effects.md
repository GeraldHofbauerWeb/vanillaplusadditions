# Food Effects

> **TL;DR** — Eating a configured item hands out the potion effects listed for it, and with Tough As
> Nails installed the configured drinks and stews also refill the thirst bar; every listed item
> additionally becomes edible on a full hunger bar.

<!-- vpa:meta:start -->
|  |  |
|---|---|
| **Module ID** | `food_effects` |
| **Side** | Client + Server |
| **Requires** | — |
| **Works with** | [Tough As Nails](https://modrinth.com/mod/tough-as-nails) <sub>tested 10.1.0.13</sub>, [Create](https://modrinth.com/mod/create) <sub>tested 6.0.10</sub>, `rottencreatures` |
| **Download** | [`vpa_food_effects.jar`](https://github.com/GeraldHofbauerWeb/vanillaplusadditions/releases/latest/download/vpa_food_effects.jar) · also needs `vpa_core` |
| **Config section** | `[modules.food_effects]` |
| **Since** | `v0.6.0` |
<!-- vpa:meta:end -->

## What it does

A config table maps items to potion effects. Finish eating an item that appears in it and you get the
effects listed against it — any effect, any duration, any level, with an optional probability per
line. Several lines may name the same item; each is rolled on its own.

A second table maps items to thirst. It is read only when **Tough As Nails** is installed, applies
only to players, and tops the bar up to its maximum of 20.

Every item named in *either* table is also made **always edible**, so you can drink a juice or eat a
cookie on a full hunger bar.

The shipped defaults cover vanilla, Create and Tough As Nails. Which item does what is config and
nothing else — delete every line and the module has nothing left to do.

### The default effects

`amplifier` 0 is level I, 1 is level II. A line with no chance is certain.

| Item | Effect | Duration | Level | Chance | Needs |
|---|---|---|---|---|---|
| `minecraft:cookie` | Speed | 160 t · 8 s | II | — | — |
| `minecraft:glow_berries` | Glowing | 60 t · 3 s | I | — | — |
| `minecraft:rabbit_stew` | Internal Warmth | 24 000 t · 20 min | I | — | TAN |
| `minecraft:mushroom_stew` | Internal Warmth | 12 000 t · 10 min | I | — | TAN |
| `vanillaplusadditions:glow_mushroom_stew` | Internal Warmth | 12 000 t · 10 min | I | — | TAN + [`glow_mushroom`](glow_mushroom.md) |
| `minecraft:beetroot_soup` | Internal Warmth | 12 000 t · 10 min | I | — | TAN |
| `toughasnails:sweet_berry_juice` | Internal Warmth | 3 600 t · 3 min | I | — | TAN |
| `rottencreatures:magma_rotten_flesh` | Internal Warmth | 6 000 t · 5 min | I | — | TAN + Rotten Creatures |
| `toughasnails:cactus_juice` | Internal Chill | 3 600 t · 3 min | I | — | TAN |
| `rottencreatures:frozen_rotten_flesh` | Internal Chill | 6 000 t · 5 min | I | — | TAN + Rotten Creatures |
| `minecraft:golden_apple` | **Thirst** | 600 t · 30 s | I | 25 % | TAN |
| `minecraft:enchanted_golden_apple` | **Thirst** | 600 t · 30 s | I | 25 % | TAN |
| `minecraft:golden_carrot` | **Thirst** | 600 t · 30 s | I | 25 % | TAN |
| `minecraft:enchanted_golden_apple` | Climate Clemency | 6 000 t · 5 min | I | 100 % | TAN |
| `toughasnails:melon_juice` | Regeneration | 60 t · 3 s | I | — | TAN |
| `toughasnails:glow_berry_juice` | Glowing | 120 t · 6 s | I | — | TAN |
| `toughasnails:chorus_fruit_juice` | Jump Boost | 240 t · 12 s | II | — | TAN |
| `create:sweet_roll` | Speed | 120 t · 6 s | I | — | Create |
| `create:bar_of_chocolate` | Speed · Jump Boost | 600 t · 30 s each | I | — | Create |
| `create:chocolate_glazed_berries` | Speed · Jump Boost | 60 t · 3 s each | I | — | Create |

**`toughasnails:thirst` is a penalty, not a refill.** TAN describes it as *"Lowers thirst at a faster
rate than normal"*. The three golden foods are the one place in the defaults where eating something
costs you — a quarter of the time. The `thirst_effects` table below does the opposite.

### The default thirst refills

The bar holds 20, drawn as 10 droplets.

| Item | Thirst | Droplets |
|---|---|---|
| `minecraft:beetroot_soup` | 6 | 3 |
| `minecraft:mushroom_stew` | 2 | 1 |
| `minecraft:rabbit_stew` | 2 | 1 |
| `minecraft:melon_slice` | 2 | 1 |

## In detail

### Three mechanisms that are easy to conflate

| | Fires on | Applies to | Needs TAN | Follows a config edit |
|---|---|---|---|---|
| Potion effects | `LivingEntityUseItemEvent.Finish`, server side | **any** living entity | no | on config load |
| Thirst refill | the same event | players only | yes | on config load |
| Always edible | `ModifyDefaultComponentsEvent`, once at mod load | the item itself | no | **only after a restart** |

The first two read a cache that is rebuilt whenever the config loads and again at load-complete. The
third rewrites the item's `FOOD` data component during mod loading and never runs again, so adding an
item to a list at runtime gives it effects but not the always-edible flag until the game restarts.

### The effect roll

The handler returns immediately on the client, so everything below is server-authoritative. For each
cached entry belonging to the eaten item:

```java
if (entity.getRandom().nextFloat() <= entry.chance()) {
    entity.addEffect(new MobEffectInstance(entry.effect()));
}
```

`nextFloat()` returns `[0, 1)`, so chance `1.0` is certain — and because the comparison is `<=`,
chance `0.0` can still fire on an exact zero roll. Each entry is rolled separately, so a Create bar
of chocolate rolls twice, once for Speed and once for Jump Boost. The cached `MobEffectInstance` is
copied before it is applied, so the same entry can serve any number of eaters.

The event carries a plain `LivingEntity`. Nothing narrows it to players, so any mob that finishes
using a configured item gets the effects too. Only the thirst half checks `instanceof Player`.

Short durations are worth reading twice. Vanilla's Regeneration heals once every `50 >> amplifier`
ticks, and it tests `duration % 50 == 0` *before* decrementing, so the default 60-tick Regeneration
from melon juice passes exactly one multiple of 50 on its way down: one heal, half a heart.

### Thirst

```java
IThirst thirst = ThirstHelper.getThirst(player);
thirst.setThirst(Math.min(thirst.getThirst() + amount, 20));
```

The cap of 20 is hardcoded here, not read from TAN. The thirst cache is a `HashMap` keyed by item, so
unlike the effects list a second line for the same item **overwrites** the first rather than adding to
it. Without TAN the whole list is skipped at load time (`loadThirstEffects` returns on the first
line) and the cache stays empty.

### Always edible, and what the rebuild keeps

The item's `FOOD` component is not patched in place — there is no partial-update API for it — so it is
rebuilt. The rebuild goes straight to the record, not through `FoodProperties.Builder`:

```java
builder.set(DataComponents.FOOD, new FoodProperties(
        existingFood.nutrition(),
        existingFood.saturation(),
        true,                          // the only thing this module actually changes
        existingFood.eatSeconds(),
        existingFood.usingConvertsTo(),
        existingFood.effects()));
```

Five of the six fields are carried over untouched and only `canAlwaysEat` is forced on. That is more
literal-minded than it looks, and it is deliberate — **the builder cannot express this record**, which
cost two real bugs before it was written this way (both fixed in `v1.0.0-beta.95`):

**`FoodProperties` stores absolute saturation, the builder takes a modifier.** `build()` runs the
value through

```java
public static float saturationByModifier(int foodLevel, float saturationModifier) {
    return (float)foodLevel * saturationModifier * 2.0F;
}
```

so feeding `existingFood.saturation()` — already the product — back in as the modifier squared it.
Worse, `ModifyDefaultComponentsEvent.modify` applies each patch *immediately*
(`Item.modifyDefaultComponentsFrom` reassigns `Item.components`) and `new ItemStack(item)` reads
exactly that map, so an item named on more than one line compounded once per line: rabbit stew, on
both the food and the thirst table, reached a saturation of 4 800. `FoodData.add` clamps saturation to
the hunger level of the bite, so the damage in play was bounded — but every configured food saturated
to the maximum instead of its vanilla value.

**The builder has no setter for `eatSeconds` and starts `usingConvertsTo` empty.** In 1.21.1 there is
no `BowlFoodItem` any more; `usingConvertsTo` is the whole mechanism, and `Player.eat` hands the empty
bowl back from that field. Vanilla builds all three soups through

```java
private static FoodProperties.Builder stew(int nutrition) {
    return new FoodProperties.Builder().nutrition(nutrition).saturationModifier(0.6F).usingConvertsTo(Items.BOWL);
}
```

Dropping the field meant that with this module on its defaults, **eating mushroom stew, rabbit stew or
beetroot soup returned no bowl** — and the glow mushroom stew would have joined them the moment it was
added to the list. `eatSeconds` fell back to 1.6 s the same way, which would have turned a configured
fast food (dried kelp, 0.8 s) into an ordinary meal.

The lesson generalises past this module: **when a vanilla record has a builder, check that the builder
can reach every field before using it to copy one.**

**A non-food item on either list still becomes food.** With no existing `FOOD` component there is
nothing to carry over, so the item gets `nutrition(0).saturationModifier(0).alwaysEdible()`. That is
what lets a drink from another mod carry an effect, and equally what turns a typo'd
`minecraft:diamond` into something you can chew on.

### Tooltips

Two separate tooltip paths, both client-facing.

**Heating and cooling lines** ride on `ItemTooltipEvent` and match the cached effects against two
hardcoded ids, `toughasnails:internal_warmth` and `toughasnails:internal_chill`. A match adds a gold
🔥 line or an aqua ❄ line using TAN's own translation keys, `desc.toughasnails.heating_consumed` and
`desc.toughasnails.cooling_consumed`. Any configured item carrying those effects gets the line, not
just the ones in the defaults; without TAN the effect never resolves, so the line never appears.

**The thirst droplets** are a graphical tooltip component. `RenderTooltipEvent.GatherComponents` adds a
`ThirstTooltipData`, and `ThirstClientTooltip` draws `amount / 2` full droplets plus a half droplet for
an odd amount, followed by a grey ` (NN%)` when the chance is below 1. The icons are blitted straight
out of TAN's own sheet:

```java
guiGraphics.blit(TAN_ICONS, currentX, y, 0, 41, 8, 8, 256, 256);   // full droplet
guiGraphics.blit(TAN_ICONS, currentX, y, 8, 41, 8, 8, 256, 256);   // half droplet
```

`toughasnails:textures/gui/icons.png` is 256 × 256 in the version this repository builds against
(10.1.0.13) and carries the droplets at exactly those coordinates. Nothing validates that at runtime,
so a TAN update that rearranges the sheet would draw whatever now sits there.

<!-- vpa:config:start -->
## Configuration

Section `[modules.food_effects]` in `config/vanillaplusadditions-common.toml` (or `config/vpa_food_effects-common.toml` if you run the standalone jar).

Every module also has the universal `enabled` and `debug_logging` keys — see the [Configuration Guide](../guides/configuration.md).

| Key | Type | Default | Range | Effect |
|---|---|---|---|---|
| `food_effects` | list | `["minecraft:cookie;minecraft:speed;160;1", "minecraft:rabbit_stew;toughasnails:internal_warmth;24000;0", "minecraft:mushroom_stew;toughasnails:internal_warmth;12000;0", "vanillaplusadditions:glow_mushroom_stew;toughasnails:internal_warmth;12000;0", "minecraft:beetroot_soup;toughasnails:internal_warmth;12000;0", "rottencreatures:magma_rotten_flesh;toughasnails:internal_warmth;6000;0", "toughasnails:sweet_berry_juice;toughasnails:internal_warmth;3600;0", "rottencreatures:frozen_rotten_flesh;toughasnails:internal_chill;6000;0", "toughasnails:cactus_juice;toughasnails:internal_chill;3600;0", "minecraft:golden_apple;toughasnails:thirst;600;0;0.25", "minecraft:enchanted_golden_apple;toughasnails:thirst;600;0;0.25", "minecraft:golden_carrot;toughasnails:thirst;600;0;0.25", "minecraft:glow_berries;minecraft:glowing;60;0", "toughasnails:melon_juice;minecraft:regeneration;60;0", "toughasnails:glow_berry_juice;minecraft:glowing;120;0", "toughasnails:chorus_fruit_juice;minecraft:jump_boost;240;1", "minecraft:enchanted_golden_apple;toughasnails:climate_clemency;6000;0;1.0", "create:sweet_roll;minecraft:speed;120;0", "create:bar_of_chocolate;minecraft:speed;600;0", "create:bar_of_chocolate;minecraft:jump_boost;600;0", "create:chocolate_glazed_berries;minecraft:speed;60;0", "create:chocolate_glazed_berries;minecraft:jump_boost;60;0"] - 21 entries covering 18 distinct items` | no spec range; defineList with a per-entry validator: 3 to 5 semicolon-separated parts, parts[0] and parts[1] must parse as ResourceLocations, duration_in_ticks >= 0, amplifier >= 0 (optional, default 0), chance between 0.0 and 1.0 (optional, default 1.0). The "new entry" template offered by the config UI is minecraft:apple;minecraft:speed;200;0;1.0. | Item-to-potion-effect table applied when a living entity finishes eating the item. Format: item_id;effect_id;duration_in_ticks;amplifier;chance. Several lines may target the same item (all of them are applied, each rolled separately). Entries whose item or effect is not registered are dropped silently at cache load. Every listed item is additionally made always-edible via the FOOD data component; the rest of its FOOD record - saturation, eat time and the bowl it converts to - is carried over unchanged. |
| `thirst_effects` | list | `["minecraft:beetroot_soup;6;", "minecraft:mushroom_stew;2;", "minecraft:rabbit_stew;2;", "minecraft:melon_slice;2;"] - note the trailing semicolons, which split drops, so chance defaults to 1.0` | no spec range; defineList with a per-entry validator: 2 or 3 semicolon-separated parts, parts[0] must parse as a ResourceLocation, thirst_amount >= 0, chance between 0.0 and 1.0 (optional, default 1.0). Template: minecraft:beetroot_soup;6;1.0. | Item-to-thirst table. Format: item_id;thirst_amount;chance. Only ever read when Tough As Nails is loaded (loadThirstEffects returns immediately otherwise, FoodEffectsModule.java:121-124) and only applied to Players; the restored value is capped at 20. One entry per item (a HashMap, so a second line for the same item overwrites the first). The items ARE made always-edible even without Tough As Nails. |
<!-- vpa:config:end -->

## Compatibility and known limits

| Limit | Effect |
|---|---|
| No Tough As Nails | The thirst table is never parsed and the droplet tooltip never fires. Every TAN effect id in the defaults fails its registry lookup and is dropped, which also removes the heating and cooling tooltip lines, and the TAN juices fail their item lookup the same way. In pure vanilla exactly two entries survive: cookie and glow berries. |
| No Tough As Nails, always-edible | Still applied. The always-edible pass reads only the item id from each line, from *both* tables, so the soups and melon slice are converted whether or not the thirst half does anything — and so is every line whose effect id is dead. |
| No Create / no Rotten Creatures | Those ids resolve to `Items.AIR` and are skipped. Rotten Creatures is not declared in `neoforge.mods.toml` and is on no classpath in this repository; both mods are default config **data**, not a code dependency. |
| Config edit, always edible | Needs a game restart. `ModifyDefaultComponentsEvent` fires once during mod loading; the effect and thirst caches reload with the config, the `FOOD` component does not. |
| Module disabled at runtime | Effects and thirst stop at once (the caches are cleared and the handlers check `isModuleEnabled()`), but the rewritten `FOOD` components stay as they are until the next start. |
| `debug_logging` is loud and public | With it on, **every** finished use-item — any eating or drinking, configured or not — broadcasts `[DEBUG] Consumed food item: …`, then the cache size, then one chat line per cached item, and `MessageBroadcaster` sends to the whole player list, not just the level. On the defaults with Create, TAN and Rotten Creatures installed that is about 20 lines per bite for everyone online; in pure vanilla, four. |
| Bad config lines | An unknown item or effect id is dropped **silently**; the warning only appears with `debug_logging` on. A line that fails to parse is logged at error level, except inside the always-edible pass, where the exception is swallowed entirely. |
| Standalone jar + TAN, thirst tooltip | The `ClientTooltipComponent` factory for `ThirstTooltipData` is registered only in `VanillaPlusAdditions.ClientModEvents`, and that class ships in neither `vpa_core` nor `vpa_food_effects`. Hovering a thirst-configured item there hands `ClientTooltipComponent.create` a component with no factory, which throws `IllegalArgumentException("Unknown TooltipComponent")`. Read off the build script and the decompiled sources; not reproduced in game. The bundle jar is unaffected. |
| Standalone jar metadata | The generated per-module `neoforge.mods.toml` declares only `vpa_core`, NeoForge and Minecraft, and does not carry the optional `toughasnails` dependency the bundle has. Its ordering there is `NONE`, so this is metadata only. |
| TAN icon coordinates | Hardcoded at (0, 41) and (8, 41) on a 256 × 256 sheet. Correct for 10.1.0.13; a TAN update that moves them breaks the droplets silently. |
| Dedicated servers | `onGatherTooltipComponents` subscribes `RenderTooltipEvent.GatherComponents` — a class in `net.neoforged.neoforge.client.event` — on the **common** bus, from the same class that carries the server logic, with no `Dist` guard. Every other module here keeps client handlers in a `modules/<id>/client/` class, and `end_oxygen` was moved out of exactly this pattern in commit `3a43685` ("Fix dedicated-server client class loading in End Oxygen", 2026-05-11). `food_effects` was last touched functionally three days earlier and never followed. This repository holds no stack trace and no test covering it, so what a dedicated server actually does with it is untested here. |

## Under the hood

No mixins, no items, no blocks, no commands, no keybinds, no network payloads. The module is one class
plus a config, a compat shim and two small client classes.

| Event | Bus | Purpose |
|---|---|---|
| `LivingEntityUseItemEvent.Finish` | common | The effect roll and the thirst refill; returns early on the client |
| `ItemTooltipEvent` | common | The gold heating and aqua cooling lines |
| `RenderTooltipEvent.GatherComponents` | common (client-only event class) | Adds the `ThirstTooltipData` component |
| `ModifyDefaultComponentsEvent` | mod | Rebuilds the `FOOD` component of every listed item |
| `RegisterClientTooltipComponentFactoriesEvent` | mod, client | Maps `ThirstTooltipData` to `ThirstClientTooltip` — **not** in the module; it lives in `VanillaPlusAdditions.ClientModEvents` |

The first three are registered together by `NeoForge.EVENT_BUS.register(this)` in `onInitialize`; the
fourth is added to the mod bus by hand. This module is the only user of `ItemTooltipEvent` and of
`RenderTooltipEvent` in the repository.

**Caches.** `Map<Item, List<EffectEntry>>` for the effects — a list, so several lines per item all
apply — and `Map<Item, ThirstEntry>` for thirst, where a second line for an item replaces the first.
Both are keyed by the resolved `Item`, so the cache size is the number of *resolvable distinct items*,
not the number of config lines: the 21 default effect lines cover 18 items, because enchanted golden
apple, bar of chocolate and chocolate-glazed berries each appear twice. Both are cleared and rebuilt
by `reloadEffectCache`, called from `FoodEffectsConfig.onConfigLoad` and from `onLoadComplete`, and a
disabled module leaves them empty.

**The Tough As Nails split** follows the rule the rest of this repository uses: the *question* lives in
the module (`ModList.get().isLoaded("toughasnails")`, cached in `onInitialize`), and every TAN type is
confined to `compat/TANIntegration`, whose only method is the two-statement `applyThirst`. TAN is
`implementation files("libs/ToughAsNails-…jar")` in `build.gradle` — compile and dev runtime only,
nothing is shipped — and `neoforge.mods.toml` declares it `type="optional"`,
`versionRange="[10.1.0,)"`, `ordering="NONE"`.

**Classes.**

| Class | Role |
|---|---|
| `modules/food_effects/FoodEffectsModule` | Caches, all four event handlers, the `FOOD` rebuild |
| `modules/food_effects/config/FoodEffectsConfig` | The two lists, their validators and the shipped defaults |
| `modules/food_effects/compat/TANIntegration` | The only file that names a TAN type |
| `modules/food_effects/client/ThirstTooltipData` | A `TooltipComponent` record of amount and chance |
| `modules/food_effects/client/ThirstClientTooltip` | Draws the droplets from TAN's icon sheet |
| `standalone/food_effects/FoodEffectsStandalone` | `@Mod("vpa_food_effects")` entry point |

**Small things.** The default thirst lines carry a trailing semicolon (`minecraft:beetroot_soup;6;`);
`String.split` drops trailing empty fields, so the chance still defaults to 1.0 and the validator is
happy. And `desc.vpa.toughasnails.thirst_restored` exists in `en_us.json` and in all five translations
but is referenced by no code — the droplet tooltip renders icons and an optional percentage, nothing
else. It is the module's only lang key.

**Version history.** The module arrived in `0.6.0`; the Tough As Nails half only in `v0.10.0`, and the
graphical thirst tooltip in `v0.10.2`. Builder's Tea was removed from the thirst defaults in August
2026, added back to both tables in September, and taken out of both again in `v1.0.0-beta.96` — see
below for why it kept coming back.

### Leave Builder's Tea alone

`create:builders_tea` belongs to the **Tough As Nails Create Addon** (`tanca`), which writes it into
three of TAN's own tags: `heating_consumed_items`, `thirst/7_thirst_drinks` and
`hydration/60_hydration_drinks`. TAN handles it from there. Every entry this module adds for it lands
*on top* of that — the warmth applied twice, thirst as 7 + 2 — which is why it was taken out again.

The test is cheap and worth doing before adding any drink from a TAN addon: unpack the addon's jar and
look in `data/toughasnails/tags/item/`. If the item is listed there, this module has nothing to add.
For comparison, TAN's own heating tag holds only `toughasnails:charc_os` and its cooling tag only
`toughasnails:ice_cream` — so the sweet berry juice and cactus juice entries in the defaults are
genuine and stay.

## See also

* [Stackables](stackables.md) — the other module whose defaults lean on Tough As Nails and Create
* [Configuration Guide](../guides/configuration.md) — where the config file lives
* [Debug Logging](../guides/debug-logging.md) — what the universal `debug_logging` key does; read the limits table above before turning it on for this module
* [All modules](../../README.md#-modules)
