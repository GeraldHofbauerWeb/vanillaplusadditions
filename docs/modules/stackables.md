# Stackables

> **TL;DR** — Potions, stews, soups, ender pearls and eggs stack to 64 instead of 1 or 16, and any
> other item can be added to that list by id.

<!-- vpa:meta:start -->
|  |  |
|---|---|
| **Module ID** | `stackables` |
| **Side** | Client + Server |
| **Requires** | — |
| **Works with** | [Tough As Nails](https://modrinth.com/mod/tough-as-nails) <sub>tested 10.1.0.13</sub>, [Create](https://modrinth.com/mod/create) <sub>tested 6.0.10</sub> |
| **Download** | [`vpa_stackables.jar`](https://github.com/GeraldHofbauerWeb/vanillaplusadditions/releases/latest/download/vpa_stackables.jar) · also needs `vpa_core` |
| **Config section** | `[modules.stackables]` |
| **Since** | `v0.4.0` |
<!-- vpa:meta:end -->

## What it does

Vanilla keeps a handful of items deliberately small. A potion does not stack at all. Neither does a
stew, or a bowl of soup. Ender pearls and eggs stop at 16. On a long trip that means a hotbar of
single potions and an inventory row eaten by four kinds of stew.

This module raises those ceilings. It adds nothing — no items, no blocks, no recipes, no commands —
it only edits the maximum stack size of items that already exist:

* **Potions, splash potions, lingering potions and tipped arrows** follow one number,
  `default_potion_stack_size`, which is 64.
* **Everything else** comes from a list of `namespace:id:stack` entries. Twenty-seven of them ship by
  default: the four vanilla stews and soups plus our own glow mushroom stew, ender pearls, eggs,
  eleven Tough As Nails drinks, Create's Builder's Tea and Enderman Overhaul's eight pearls.

Any item from any mod can be added to that list by id. An id that no installed mod provides is
skipped, so the shipped Tough As Nails and Create entries cost nothing in a pack without those mods.

Items that carry durability are the one thing that can never be raised: in Minecraft an item has
either a damage bar or a stack, never both. A filled Tough As Nails canteen therefore stays at one
per slot whatever you put in the config.

## In detail

### One moment, once per game start

Everything the module does happens inside a single handler,
`StackablesModule.onModifyDefaultComponents`. NeoForge fires that event exactly once while the game
is loading mods, and closes the window again immediately afterwards:

```java
public static void modifyComponents() {
    canModifyComponents = true;
    ModLoader.postEvent(new ModifyDefaultComponentsEvent());
    canModifyComponents = false;
}
```

Two consequences follow from that, and they are the two things people trip over:

* **Config changes need a restart.** Editing `stackable_items` or `default_potion_stack_size` while
  the game runs changes nothing until the next start — the moment at which those values are read has
  already passed.
* **`/vpa module disable stackables` does nothing either.** The handler is gated on
  `isModuleEnabled()`, but that gate is only ever evaluated during mod loading. A runtime override
  set afterwards arrives too late, in both directions.

The handler subscribes at `EventPriority.LOWEST`. NeoForge's javadoc recommends that priority on
`modifyMatching`, for a listener that patches items based on their *current* default components;
this module does not — it calls `event.modify` with an absolute value. Running last still buys it
the last word: `event.modify` writes its patch straight into the item, so where two mods set the
same item's `MAX_STACK_SIZE`, this module's value is the one left standing.

### The four potion items

```java
int desired = Math.max(2, getConfig().getDefaultPotionStackSize());
```

`Items.POTION`, `SPLASH_POTION`, `LINGERING_POTION` and `TIPPED_ARROW` all get that one value, and
the whole block sits in a `try`/`catch Throwable` that degrades a failure to a `warn`.

The `Math.max(2, …)` is a floor, and the config range starts at 1, so the two do not line up: setting
`default_potion_stack_size = 1` still produces a stack of 2. Vanilla's unstackable potion cannot be
restored through this key — only by switching the module off.

Tipped arrows are the odd one in that group. Vanilla registers `tipped_arrow` with no `stacksTo` call
at all, so it keeps the 64 that `DataComponents.COMMON_ITEM_COMPONENTS` gives every item, and the
default setting leaves it exactly where it was. The key therefore cuts both ways: lowering it to 16
to make potions rarer also drops tipped arrows *below* vanilla, which is probably not what was
meant.

### The list

Each entry is split at its **last** colon — everything before it is parsed as a `ResourceLocation`,
everything after it as the target stack size. Three separate things can go wrong, and all three end
in a bare `continue`:

```java
int lastColon = entry.lastIndexOf(':');
if (lastColon <= 0 || lastColon == entry.length() - 1) {
    continue;                                   // no colon at all, or a trailing one
}
...
try {
    desiredStackSize = Integer.parseInt(stackPart);
} catch (NumberFormatException e) {
    continue;                                   // stack part is not a number
}
...
try {
    resourceLocation = ResourceLocation.parse(resourceStr);
} catch (Exception e) {
    continue;                                   // not a valid item id
}
```

A malformed entry is dropped **without a log line of any kind**, at any log level. An entry that
parses but names an item no mod registered is dropped too, this time with a `warn` — but only when
`debug_logging` is on, so by default that is silent as well. A typo that gets *past* the validator —
`a:b:c:64` is the realistic case — looks exactly like a working config.

### What actually changes

Ids and stack sizes below were read out of the jars this pack is built against — vanilla 1.21.1, and
`libs/ToughAsNails-neoforge-1.21.1-10.1.0.13.jar` and `libs/create-1.21.1-6.0.9.jar`. That Create jar
is the compile-time one; the played pack runs 6.0.10, which is the version the table at the top
names — `builders_tea` is `stacksTo(16)` in both. Enderman Overhaul was read from
`endermanoverhaul-neoforge-1.21.1-2.0.3.jar` in the played pack; it is not in `libs/`, so nothing in
this repository compiles against it.

| Entry | Default without this module | With it |
|---|---|---|
| `minecraft:mushroom_stew` | 1 | 64 |
| `minecraft:rabbit_stew` | 1 | 64 |
| `minecraft:beetroot_soup` | 1 | 64 |
| `minecraft:suspicious_stew` | 1 | 64 |
| `minecraft:ender_pearl` | 16 | 64 |
| `minecraft:egg` | 16 | 64 |
| `minecraft:potion` | 1 | 64 |
| `minecraft:splash_potion` | 1 | 64 |
| `minecraft:lingering_potion` | 1 | 64 |
| `minecraft:tipped_arrow` | 64 | 64 — unchanged at the default |
| `toughasnails:dirty_water_bottle` | 1 | 64 |
| `toughasnails:purified_water_bottle` | 1 | 64 |
| `toughasnails:apple_juice` and the six other juices | 16 | 64 |
| `toughasnails:ice_cream` | 16 | 64 |
| `toughasnails:charc_os` | 16 | 64 |
| `create:builders_tea` | 16 | 64 |
| `vanillaplusadditions:glow_mushroom_stew` | 1 | 64 |
| `endermanoverhaul:ancient_pearl` and the seven other pearls | 16 | 64 |

The seven juices are apple, cactus, chorus fruit, glow berry, melon, pumpkin and sweet berry. The
eight Enderman Overhaul pearls are ancient, bubble, corrupted, crimson, icy, soul, summoner and
warped.

**Two of those pearls will still refuse to stack, and that is not this module's doing.** `soul_pearl`
carries a `BOUND_ENTITY` component and `ancient_pearl` an `ENTITY_DATA` one, and Minecraft only merges
stacks whose components are equal. Unbound ones stack to 64; once two pearls are bound to different
things they stay apart no matter what the maximum says. Raising the limit is all a stack-size setting
can ever do.

Tough As Nails' **canteens** are in neither group. The filled ones carry durability, and
`Item.Properties.durability(int)` sets `MAX_STACK_SIZE` to 1 in the same breath, so they can never
stack. The six *empty* canteens are a different case: they carry no durability, only `stacksTo(1)`,
so nothing about how this module works excludes them — they are simply not in the default list, and
adding them is an ordinary config edit.

### The auto-detection that is not there

`StackablesConfig` still carries the note it was written with:

```java
// NOTE: The toughasnails item IDs below are assumed/guessed; please verify actual IDs in your mod if necessary.
```

The guesswork was real — the first version of the list (`v0.4.0`) contained `toughasnails:juice` and
`toughasnails:ice_cream_vanilla`, neither of which exists. A commit later a registry sweep was added
to catch whatever the guesses had missed: it walked every id in `BuiltInRegistries.ITEM` whose
namespace contained `tough`, took the ones whose path contained `juice`, `ice_cream` or `charc_os`,
or equalled one of the two water bottles, or started with `empty_`, and patched each to 64 —
skipping anything whose current stack was already 1, as a cheap durability test.

The sweep was commented out again in `v0.4.1` and has been dead ever since. The list was corrected by
hand instead, and all eleven ids in it resolve against Tough As Nails 10.1.0.13.

The sweep cannot be brought back by deleting the comment markers either, because the comment was
opened in the middle of a statement:

```java
        // TESTING: Disable auto-detection, only use config
        /*int p
        atchedCount = 0;
```

What survives is the log line that announces it. With `debug_logging` on, every start still prints

```
=== Starting auto-detection for Tough as Nails items ===
```

immediately before a block that does nothing, and the matching *"component patching complete"* line
sits inside the comment and is therefore never printed. A debug log that stops after the per-item
lines with no completion marker is the normal, healthy case.

Two practical consequences: **empty canteens are not covered** (that rule lived only inside the dead
block), and a Tough As Nails update that adds a new juice will **not** be picked up on its own — the
new id has to be added to `stackable_items` by hand.

### The validator and the parser disagree

The config spec and the runtime parser apply different rules to the same string.

| Entry | Config validator | Runtime parser |
|---|---|---|
| `minecraft:egg:64` | accepted | accepted |
| `mushroom_stew:64` (one colon) | **rejected** — the part before the last colon must itself contain one | would have worked |
| `a:b:c:64` | accepted | `ResourceLocation.parse("a:b:c")` throws, entry silently dropped |
| `minecraft:egg:0` / `:999` | **rejected** — the validator requires 1 ≤ n ≤ 64 | any `int` accepted |

The validator is the stricter of the two and runs first, so the loose cases in the right-hand column
cannot normally be reached. What NeoForge does with an element its spec rejects is drop that one
entry: `defineList` corrects the list with `list.removeIf(elementValidator.negate())`, and falls back
to the whole default list only if the value is not a list at all, or if the removal leaves it empty —
the overload this config uses passes `ListValueSpec.NON_EMPTY` as the allowed size range. One bad
line costs you that line; a list in which every line is bad costs you the list. The correction is not
silent: FML logs `Configuration file … is not correct. Correcting` at WARN, backs the file up and
writes it back without the rejected line.

<!-- vpa:config:start -->
## Configuration

Section `[modules.stackables]` in `config/vanillaplusadditions-common.toml` (or `config/vpa_stackables-common.toml` if you run the standalone jar).

Every module also has the universal `enabled` and `debug_logging` keys — see the [Configuration Guide](../guides/configuration.md).

| Key | Type | Default | Range | Effect |
|---|---|---|---|---|
| `default_potion_stack_size` | int | `64` | 1 ~ 64 | Default stack size for potions, splash potions, lingering potions and tipped arrows. Effective value is max(2, this) - setting 1 still yields 2, so vanilla's unstackable potions cannot be restored through this key (StackablesModule.java:73). The default was 16 until v0.9.3 (commit 73e2ffe). |
| `stackable_items` | list | `StackablesConfig.DEFAULT_STACKABLES (27 entries) = minecraft:mushroom_stew:64, vanillaplusadditions:glow_mushroom_stew:64, minecraft:rabbit_stew:64, minecraft:beetroot_soup:64, minecraft:suspicious_stew:64, minecraft:ender_pearl:64, minecraft:egg:64, toughasnails:dirty_water_bottle:64, toughasnails:purified_water_bottle:64, toughasnails:apple_juice:64, toughasnails:cactus_juice:64, toughasnails:chorus_fruit_juice:64, toughasnails:glow_berry_juice:64, toughasnails:melon_juice:64, toughasnails:pumpkin_juice:64, toughasnails:sweet_berry_juice:64, toughasnails:ice_cream:64, toughasnails:charc_os:64, create:builders_tea:64, endermanoverhaul:ancient_pearl:64, endermanoverhaul:bubble_pearl:64, endermanoverhaul:corrupted_pearl:64, endermanoverhaul:crimson_pearl:64, endermanoverhaul:icy_pearl:64, endermanoverhaul:soul_pearl:64, endermanoverhaul:summoner_pearl:64, endermanoverhaul:warped_pearl:64` | per entry: at least two colons (the part before the last colon must itself contain a colon) and a stack part parsing to 1 ~ 64 | List of item entries to make stackable, each in the format namespace:id:stack (e.g. minecraft:mushroom_stew:64); the last part is the desired max stack size. Ids that are not present in the item registry are skipped (WARN only with debug_logging on), malformed entries are skipped without any log. The runtime parser is looser than the config validator (see notes 10). |
<!-- vpa:config:end -->

## Compatibility and known limits

| Limit | Effect |
|---|---|
| Items with durability | Can never be stacked, config or no config. Filled Tough As Nails canteens are the case people meet. A failed patch is caught and logged as `✗ Failed to patch {}` at ERROR; nothing else breaks. |
| Config edited while the game runs | No effect until restart. The event that does the patching fires once during mod loading. |
| `/vpa module enable\|disable stackables` | Same — the enabled check happens during mod loading, so a runtime override never reaches this module. |
| Tough As Nails or Create absent | Their entries find no item in the registry and are skipped. A `warn` is logged, but only with `debug_logging` on. Nothing else changes. |
| A typo in `stackable_items` | Entries the config spec rejects are removed with a WARN and a config backup; the ones that slip past it are dropped by the parser without any log line at all. A well-formed id that no mod provides is only reported with debug logging on. |
| New Tough As Nails items | Not picked up automatically. The auto-detection is dead code — add the id to the list by hand. |
| `default_potion_stack_size = 1` | Yields 2, not 1. Use `enabled = false` to get vanilla's unstackable potions back. |
| Lowering `default_potion_stack_size` | Also lowers tipped arrows, which vanilla already stacks to 64. |
| Client and server with different configs | Each side patches its own item defaults during its own mod loading, so the two would disagree about stack sizes. Nothing in this module syncs the config or warns about a mismatch. Inferred from the mechanism — there is no test in this repository covering it. |
| Another mod patching the same item | This module subscribes at `EventPriority.LOWEST`, so it applies after the others in the same event. |

## Under the hood

No mixins, no access transformer, no registries, no items, no blocks, no commands, no keybinds, no
network payloads, no lang keys and no assets. The module is two handlers and a config class.

| Event | Bus | Purpose |
|---|---|---|
| `ModifyDefaultComponentsEvent` | mod, `EventPriority.LOWEST` | All of the work: the four potion items, then the config list |
| `RegisterEvent` | mod, `EventPriority.LOWEST` | Empty. Its own comment says *"This event is NOT used anymore"* |

| Class | Role |
|---|---|
| `modules/stackables/StackablesModule` | Both handlers, 239 lines of which ~85 are the commented-out auto-detection |
| `modules/stackables/config/StackablesConfig` | `DEFAULT_STACKABLES`, the two keys, the list validator |
| `modules/stackables/constant_items/` | Four classes — **dead code**, see below |
| `standalone/stackables/StackablesStandalone` | `@Mod("vpa_stackables")` entry point for the standalone jar |

**Registration.** `onInitialize` registers the module instance on the mod event bus, with a fallback:

```java
try {
    getModEventBus().register(this);
} catch (IllegalStateException e) {
    getLogger().warn("Mod event bus not available during module initialization, "
        + "falling back to global event bus: {}", e.getMessage());
    NeoForge.EVENT_BUS.register(this);
}
```

Both of the module's handlers are mod-bus events — `ModifyDefaultComponentsEvent implements
IModBusEvent` — so on that fallback path the module would register successfully on
`NeoForge.EVENT_BUS` and then never be called. It would fail quietly, with one warning line as the
only clue.

**The dead item classes.** `modules/stackables/constant_items/` holds `StackablePotionItem`,
`StackableSplashPotionItem`, `StackableLingeringPotionItem` and `StackableTippedArrowItem`, each a
subclass of the vanilla item with `getMaxStackSize` overridden to return the default. Nothing in the
repository references them: they are never registered and never instantiated. They are also the only
place where the number **16** still appears — three of the four construct
`new Item.Properties().stacksTo(16)` — which is where the old README's "default 16" came from. The
live default has been 64 since `v0.9.3`.

**Logging.** Almost every line in the module is gated on `shouldDebugLog()`. Four are not, and all
four log at INFO or above: the mod-event-bus fallback in `onInitialize` and `Failed to patch potion
default components`, both at WARN, `✗ Failed to patch {}` at ERROR, and, in
`StackablesConfig.onConfigLoad`, an unconditional INFO line —

```
StackablesConfig loaded - potion stack size: 64, stackable items count: 18
```

Both config getters also fall back to the hardcoded defaults (`DEFAULT_STACKABLES` and 64):
silently while the `ConfigValue` field is still null, and — when `get()` throws — with a debug-level
note that is itself ungated, so it prints whenever the log level allows it, `debug_logging` or not.
Either way the module patches something sensible even if it runs before the config file is read.

**Standalone jar.** `vpa_stackables` carries no mixins and no data files; its generated
`neoforge.mods.toml` declares only `vpa_core`, `neoforge` and `minecraft` as required, plus the
all-in-one bundle as incompatible. Tough As Nails and Create appear as optional dependencies in the
*bundle's* toml, not in this module's — the standalone jar has no declared relationship to either,
which is harmless, because missing ids are skipped rather than looked up at load time.

## See also

* [Tipped Arrows from Potions](tipped_arrows.md) — the other module that touches tipped arrows
* [Configuration Guide](../guides/configuration.md) — where the config file lives
* [All modules](../../README.md#-modules)
