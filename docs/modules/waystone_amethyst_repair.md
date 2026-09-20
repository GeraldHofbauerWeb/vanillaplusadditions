# Waystone Amethyst Repair

> **TL;DR** — A worn Waystones **Warp Stone** goes back to full in an anvil with amethyst shards:
> 25 % of its maximum durability per shard, one XP level per shard — or nothing at all while the
> Free Anvil Repair module is enabled.

<!-- vpa:meta:start -->
|  |  |
|---|---|
| **Module ID** | `waystone_amethyst_repair` |
| **Side** | Server only |
| **Requires** | — |
| **Works with** | [Waystones](https://modrinth.com/mod/waystones) <sub>tested 21.1.41</sub> |
| **Download** | bundle only — no standalone jar |
| **Config section** | `[modules.waystone_amethyst_repair]` |
| **Since** | `v1.0.0-beta.33` |
<!-- vpa:meta:end -->

## What it does

The Warp Stone is the Waystones item that teleports you without walking to a waystone, and it wears
out doing it: 128 durability, one point per completed teleport. When it runs out it breaks, and
Waystones offers no anvil material to put it back together.

This module makes amethyst that material. Warp Stone in the left anvil slot, amethyst shards on the
right, and each shard restores a quarter of the stone's maximum durability — 32 points — until the
stone is whole or the shards run out. Only the shards actually needed are consumed; the rest of the
stack stays in the slot.

| Stone's condition | Shards taken | Levels |
|---|---|---|
| One teleport short of breaking (127 damage) | 4 | 4 |
| Half worn (64 damage) | 2 | 2 |
| A single teleport used (1 damage) | 1 | 1 |

Those levels disappear entirely while the [Free Anvil Repair](free_anvil_repair.md) module is on —
the repair then costs nothing at all, the same deal that module gives ordinary repairs.

Amethyst is a choice of flavour rather than a mechanic: nothing in the code cares which item it is,
and `repair_materials` takes any list of item ids. The same goes for the Warp Stone itself —
`target_item` is a plain registry id, so this is really a *make damageable item X repairable with
material Y* module that happens to ship pointed at Waystones.

Renaming is the one thing you cannot do in the same click. Repair first, rename after.

## Why it exists

An anvil only repairs an item with a material when the item says so, and by default no item says so:

```java
/**
 * Return whether this item is repairable in an anvil.
 */
public boolean isValidRepairItem(ItemStack stack, ItemStack repairCandidate) {
    return false;
}
```

`WarpStoneItem` does not override it — nor does any other class in the Waystones jar. The anvil
therefore falls through to its other branch, the one that merges two of the same item:

```java
if (!flag && (!itemstack1.is(itemstack2.getItem()) || !itemstack1.isDamageableItem())) {
    this.resultSlots.setItem(0, ItemStack.EMPTY);
    this.cost.set(0);
    return;
}
```

So vanilla leaves exactly two ways to keep a Warp Stone alive. Feed it a **second Warp Stone** — a
whole stone spent to top up a worn one — or enchant it with **Mending** and hand it your XP orbs;
Waystones ships a `#minecraft:enchantable/durability` tag file naming the Warp Stone, so the book
does go on. Both are heavy answers to "I have teleported 128 times", and neither is something you
can do on the spot with what a geode gives you by the stack.

<sub>Read off `waystones-neoforge-1.21.1-21.1.41.jar`, the version this pack is tested against:
`Item.Properties.durability(128)` in the `WarpStoneItem` constructor, one point of `hurtAndBreak`
on a completed teleport (guarded by Waystones' own `teleports.enableDurability` flag), no
`isValidRepairItem` anywhere in the jar, and `data/minecraft/tags/item/enchantable/durability.json`
containing `waystones:warp_stone`. The enchantment side of Mending is vanilla data and is not
checked into this repository.</sub>

## In detail

### What the module claims

`AnvilUpdateEvent` fires for every change to either anvil slot. The handler walks out again unless
all six of these hold:

| # | Condition | Source |
|---|---|---|
| 1 | The module is enabled | `isModuleEnabled()`, re-read on every event |
| 2 | Both anvil slots are occupied | `left.isEmpty() \|\| right.isEmpty()` |
| 3 | The left item is damageable **and damaged** | `left.isDamageableItem() && left.getDamageValue() > 0` |
| 4 | The left item is the configured target | `left.is(resolveTargetItem())` |
| 5 | The right item is in `repair_materials` | `repairMaterials.contains(right.getItem())` |
| 6 | No rename is in flight | `isRenaming(event.getName(), left)` |

Anything that fails a gate is left untouched, and the anvil behaves exactly as it always did.

### The arithmetic

```java
int perUnit = Math.max(1, left.getMaxDamage() * getConfig().getRepairPercentPerUnitValue() / 100);
```

Integer division, floored at one. For a 128-durability Warp Stone at the default 25 % that is 32
points a shard. The floor matters for small items: anything whose `maxDamage × percent / 100` rounds
down to zero still gets one point back per unit rather than none.

Units are then consumed one at a time, each restoring `min(remaining damage, perUnit)`, until the
stone is undamaged or the stack runs out. The last unit only repairs what is left, so a stone with a
single teleport on it costs a single shard — and a stack of 64 shards on the right does not mean 64
shards are spent.

The number of units consumed becomes two things at once: the material cost, and — unless
`free_anvil_repair` is enabled — the XP level cost. One shard, one level.

### What it costs compared with vanilla

Vanilla's price for an anvil operation is the sum of both items' stored prior-work penalty plus the
operation's own cost, clamped and then walled:

```java
int k2 = (int)Mth.clamp(j + (long)i, 0L, 2147483647L);
this.cost.set(k2);
...
if (this.cost.get() >= 40 && !this.player.getAbilities().instabuild) {
    itemstack1 = ItemStack.EMPTY;
}
```

None of that runs here. Handing the event a non-empty output makes NeoForge's hook write the result
and return `false`, which ends `createResult` on the spot — so the prior-work sum, the 40-level wall
and the penalty bump at the bottom of the method are all skipped for this combination.

The practical consequences:

* The price is the shard count and nothing else. It never climbs.
* The result is `left.copy()` with a lower damage value and no other change. Enchantments, a custom
  name, every data component and the existing `REPAIR_COST` survive untouched.
* Because `REPAIR_COST` is never incremented, a Warp Stone that vanilla has already priced out at
  *Too Expensive!* is still repairable — the wall is simply never consulted.

### Renaming falls back to vanilla, and that means no result at all

```java
if (isRenaming(event.getName(), left)) {
    return; // renaming keeps vanilla behavior (repair the Warp Stone first, rename after)
}
```

A name only counts as a rename when it would actually change something: a non-blank name that
differs from the current display name, or a blank one sent for an item that *has* a `CUSTOM_NAME`
to clear. A `null` name — nothing typed at all — never counts, and typing the stone's own name back
into the box does not cost you the repair.

When it does count, the module hands the combination back to vanilla, and vanilla has never heard of
a Warp Stone plus amethyst. The result slot stays **empty**: you get neither the repair nor the
rename. Repair in one anvil operation, rename in the next.

### Live configuration

All three keys are resolved lazily and cached against the config value they were parsed from, so a
changed id takes effect on the next anvil update without a restart:

* `target_item` is re-parsed only when the string stops equalling the cached one. An id that does not
  parse, or that is not installed, leaves the target `null` and the module inert — no error, no crash.
* `repair_materials` is re-parsed only when the list stops equalling the cached one. Entries are
  trimmed, resolved through `BuiltInRegistries.ITEM.containsKey`, and skipped individually when the
  item is not installed. Every accepted material repairs at the same rate; there is no per-material
  weighting.

Both failure paths log one line each, and only with `debug_logging` on.

One small asymmetry: material entries are trimmed before parsing, the target id is not. Neither can
be reached with stray whitespace in practice, because both config validators already reject anything
`ResourceLocation.tryParse` will not take.

<!-- vpa:config:start -->
## Configuration

Section `[modules.waystone_amethyst_repair]` in `config/vanillaplusadditions-common.toml`.

Every module also has the universal `enabled` and `debug_logging` keys — see the [Configuration Guide](../guides/configuration.md).

| Key | Type | Default | Range | Effect |
|---|---|---|---|---|
| `repair_materials` | list | `List.of("minecraft:amethyst_shard")` | no spec range; per-entry validator requires a String that ResourceLocation.tryParse accepts (WaystoneAmethystRepairConfig.java:50-52) | Item ids accepted as repair material in the anvil (amethyst shards by default). Entries are trimmed and resolved into a Set<Item>; entries whose item is not installed are skipped with a debug-only log line. Any listed material repairs at the same rate - there is no per-material weighting. |
| `repair_percent_per_unit` | int | `25` | 1 ~ 100 | How much of the item's maximum durability a single material unit restores, in percent (vanilla material repairs use 25). Applied as perUnit = max(1, maxDamage * percent / 100) with integer division, and units are consumed one at a time until the item is undamaged or the material stack runs out; the number of consumed units is both the material cost and (unless free_anvil_repair is enabled) the XP level cost. |
| `target_item` | string | `"waystones:warp_stone"` | no spec range; validator requires a String that ResourceLocation.tryParse accepts (WaystoneAmethystRepairConfig.java:44-45) | The damageable item made repairable by this module (Waystones Warp Stone by default). Resolved lazily by registry id and re-resolved whenever the string changes, so it takes effect without a restart; if the id is not installed (or unparsable) the module stays inert instead of erroring. |
<!-- vpa:config:end -->

## Compatibility and known limits

| Limit | Effect |
|---|---|
| No Waystones installed | The handler is registered anyway — there is no `ModList.isLoaded` check and no `shouldInitialize` gate. `waystones:warp_stone` fails `containsKey`, the target stays `null`, and the module is inert. Nothing breaks, and `target_item` can be pointed at any other installed damageable item instead. |
| Waystones' `teleports.enableDurability = false` | The Warp Stone never takes damage, so gate 3 never passes and there is never anything to repair. |
| Bundle only | This module has no standalone jar and no entry in `build.gradle`'s `standaloneModules`. It ships inside `vanillaplusadditions` alone. |
| Repair and rename in one click | No output at all, not merely a priced one — see above. |
| JEI / EMI | The module has no JEI plugin, and JEI's anvil list is a hardcoded vanilla one that cannot discover a code-only repair. Warp Stone plus amethyst appears in no recipe viewer; the only place it is written down is this page. |
| Module disabled at startup | `ModuleManager.initializeModules` only calls `initialize()` for modules that were enabled when the config was first read, and only `onInitialize` registers the handler. Enabling the module at runtime therefore needs a restart. Turning it **off** works immediately, because `isModuleEnabled()` is re-checked on every event. |
| `free_anvil_repair` switched off by editing the config file while the game runs | The zero-cost path and the pickup permission read different sources — see *Under the hood*. The window is an anvil result priced at 0 that the player cannot take out. A `/vpa` runtime override moves both together and is the safe way to toggle. Derived from the code paths; not reproduced in game. |
| `waystones:warp_stone=minecraft:amethyst_shard` added to `free_anvil_repair`'s `extra_repair_materials` | Both modules then claim the same combination and neither inspects the event's existing output, so whichever handler runs last wins. That order is not readable from the source (see below). With the default configs there is no overlap. |
| Tests | This repository has no unit tests. Everything here is read off the module source, the decompiled 1.21.1 `AnvilMenu` and NeoForge 21.0.167's `CommonHooks`, plus the Waystones jar named above. |

## Under the hood

Two files, 259 lines together. No registries, no items, no blocks, no commands, no keybinds, no
network payloads, no mixin, no lang keys, no datapack files.

| File | Role |
|---|---|
| `modules/waystone_amethyst_repair/WaystoneAmethystRepairModule.java` | the one event handler, the arithmetic and the id resolution |
| `modules/waystone_amethyst_repair/config/WaystoneAmethystRepairConfig.java` | the three keys and their validators |

`onInitialize` does nothing but `NeoForge.EVENT_BUS.register(this)`. `AnvilUpdateEvent` is the
module's only subscription, at default priority.

**Why a non-empty output wins.** NeoForge's hook is the first thing `createResult` does:

```java
if (e.getOutput().isEmpty())
    return true;

outputSlot.setItem(0, e.getOutput());
container.setMaximumCost(e.getCost());
container.repairItemCountCost = e.getMaterialCost();
return false;
```

and `createResult` answers that with `if (!CommonHooks.onAnvilChange(...)) return;`. The call sits
inside `if (!itemstack.isEmpty())` and **before** the `EnchantmentHelper.canStoreEnchantments`
branch that guards the rest of the method, so a non-empty left slot is the only precondition the
module inherits from vanilla.

**Material consumption.** `setMaterialCost(unitsUsed)` becomes `AnvilMenu.repairItemCountCost`. On
take, vanilla shrinks the right stack by that amount when `count > cost` and clears the whole slot
otherwise; since `unitsUsed <= right.getCount()` here, the two branches come out the same. `onTake`
is otherwise untouched, so the anvil keeps its usual chance to chip and `AnvilRepairEvent` still
fires.

**The free price is borrowed.** This module never frees anything by itself:

```java
boolean free = ModuleManager.getInstance().isModuleEnabled(FREE_REPAIR_MODULE_ID);
event.setOutput(result);
event.setMaterialCost(unitsUsed);
event.setCost(free ? 0 : unitsUsed);
```

A zero-cost anvil result is otherwise un-takeable — `AnvilMenu.mayPickup` ends in
`&& this.cost.get() > 0`, which fails in creative too. What rescues it is `free_anvil_repair`'s
`AnvilMenuFreeRepairMixin`, which permits the pickup of any non-empty cost-0 result while *that*
module is enabled. Setting the cost to 0 under the same condition is therefore deliberate, not a
convenience.

The gates are not quite the same check, though. `ModuleManager.isModuleEnabled(String)` consults the
runtime override map and otherwise `moduleEnabledState`, a snapshot taken at registration and
rewritten once in `initializeModules`; the mixin's `allowFreePickup()` goes through
`AbstractModule.isModuleEnabled()`, which resolves the **live** per-module config. A `/vpa` override
moves both, because both consult the override map first. Editing `free_anvil_repair`'s `enabled`
flag in the config file at runtime moves only the live value — the snapshot stays `true`, this
module keeps producing a cost-0 result, and the mixin has stopped allowing the pickup.

**Handler order is not source order.** Both this module and `free_anvil_repair` subscribe
`AnvilUpdateEvent` at default priority and register on the bus in their own `onInitialize`, so bus
order equals module initialisation order — and that comes from iterating a `ConcurrentHashMap`, not
from the order of the `registerModule` calls in `VanillaPlusAdditions.java`. Which handler runs
first cannot be read off the source. It only matters in the `extra_repair_materials` overlap above.

**A branch that cannot fire.** `applyRepair` guards against having consumed nothing:

```java
if (unitsUsed == 0) {
    return; // nothing to repair
}
```

It is unreachable. Gate 3 guarantees the damage value is above zero, gate 2 guarantees the right
stack holds at least one item, and `perUnit` is at least 1, so the loop always runs at least once.
`free_anvil_repair` carries an unreachable guard of the same shape.

**Sides.** Everything that counts is decided server-side and reaches the client through the ordinary
menu sync — the result slot and the `AnvilMenu` cost `DataSlot` — so a vanilla client on a modded
server sees the repair and can take it. The handler itself sits on the common event bus with no side
check, and `ItemCombinerMenu.slotsChanged` has none either, so the client's own menu copy runs the
same code when the input slots are synced; its locally computed values are then overwritten by the
server's.

## See also

* [Free Anvil Repair](free_anvil_repair.md) — where the zero price and the zero-cost pickup come from
* [Configuration Guide](../guides/configuration.md) — where the config file lives
* [All modules](../../README.md#-modules)
