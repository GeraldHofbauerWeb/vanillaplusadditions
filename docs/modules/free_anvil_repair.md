# Free Anvil Repair

> **TL;DR** — Repairing a damaged tool, weapon or piece of armour in an anvil costs no XP levels,
> works on gear vanilla has already priced out at *Too Expensive!*, and accepts extra materials such
> as diamonds for netherite gear or a stick for a bow.

<!-- vpa:meta:start -->
|  |  |
|---|---|
| **Module ID** | `free_anvil_repair` |
| **Side** | Client + Server |
| **Requires** | — |
| **Works with** | [JEI](https://modrinth.com/mod/jei), [Create](https://modrinth.com/mod/create) <sub>tested 6.0.10</sub>, [Quark](https://modrinth.com/mod/quark) <sub>tested 4.1-482</sub> |
| **Download** | [`vpa_free_anvil_repair.jar`](https://github.com/GeraldHofbauerWeb/vanillaplusadditions/releases/latest/download/vpa_free_anvil_repair.jar) · also needs `vpa_core` |
| **Config section** | `[modules.free_anvil_repair]` |
| **Since** | `v1.0.0-beta.25` |
<!-- vpa:meta:end -->

## What it does

An anvil charges levels for everything it does, and the price climbs every time you use it. This
module takes the levels off the one operation that is pure maintenance — putting a damaged item back
together — and leaves every other anvil operation at its vanilla price.

| At the anvil | Costs |
|---|---|
| Damaged item + its repair material (diamond pickaxe + diamonds) | **nothing** |
| Damaged item + an extra material from the config (netherite axe + diamonds, bow + stick) | **nothing** |
| Damaged item + a second, unenchanted copy of itself | **nothing** |
| Any of the three **while also typing a new name** | vanilla price |
| Combining two enchanted items | vanilla price |
| Applying an enchanted book | vanilla price |
| Renaming on its own | vanilla price |

Free means free. No levels are required and none are deducted, the red *Too Expensive!* cannot
appear, and by default the repair does not raise the hidden prior-work penalty that makes the *next*
anvil visit dearer. Gear that vanilla has already priced out of the anvil altogether becomes
repairable again.

Each material unit also goes further than in vanilla: 37.5 % of maximum durability instead of 25 %,
so three diamonds take a pickaxe from nearly broken back to full where vanilla wants four. That is
`repair_boost_percent`, and setting it to `0` restores vanilla's amounts exactly.

The list of accepted materials is configurable, Quark-style, in the form `item=material`. Out of the
box netherite gear repairs with **diamonds**, Create's diving gear with diamonds or copper ingots, a
**trident** with a prismarine shard and a **bow** with a stick — the last two are combinations
vanilla does not have at all.

## Why it exists

### The price only ever goes up

Every anvil operation starts at the sum of both items' stored repair cost:

```java
j += (long)itemstack.getOrDefault(DataComponents.REPAIR_COST, Integer.valueOf(0)).intValue()
    + (long)itemstack2.getOrDefault(DataComponents.REPAIR_COST, Integer.valueOf(0)).intValue();
```

and every completed operation raises that stored value for next time:

```java
public static int calculateIncreasedRepairCost(int p_39026_) {
    return (int)Math.min((long)p_39026_ * 2L + 1L, 2147483647L);
}
```

0 → 1 → 3 → 7 → 15 → 31 → 63. An item that has been through an anvil six times carries a base cost of
63 levels before anything else is counted, and 63 is past the wall:

```java
if (this.cost.get() >= 40 && !this.player.getAbilities().instabuild) {
    itemstack1 = ItemStack.EMPTY;
}
```

Forty levels is where `AnvilMenu.createResult` stops producing a result at all — the slot is emptied
and the GUI writes *Too Expensive!* over it. The penalty has no way back down, so a well-used tool
eventually cannot be repaired at any price. Repairing is what pushes it there fastest: it is the
operation you do most often, and it is the one that buys you nothing but the durability you already
paid for once.

### A free result cannot be taken out

```java
@Override
protected boolean mayPickup(Player p_39023_, boolean p_39024_) {
    return (p_39023_.hasInfiniteMaterials() || p_39023_.experienceLevel >= this.cost.get()) && this.cost.get() > 0;
}
```

`&& this.cost.get() > 0`. Setting the cost to zero is not enough — vanilla then refuses the pickup,
creative mode included. That one clause is the whole reason this module ships a mixin.

### A trident and a bow have no repair material

```java
public boolean isValidRepairItem(ItemStack p_41402_, ItemStack p_41403_) {
    return false;
}
```

That is `Item.isValidRepairItem`, and neither `TridentItem` nor `BowItem` overrides it. In vanilla
the only way to put durability back into either is a second copy of the item, or Mending.

## In detail

The module never patches the anvil's cost calculation. It listens to NeoForge's `AnvilUpdateEvent`,
which fires at the top of `createResult`; when it recognises a pure repair it computes the result
itself and sets the cost to 0. Handing the event a non-empty output makes vanilla return
immediately, so the whole of `createResult` — the enchantment merge, the 40-level wall, the
prior-work bump at the end — is skipped for that combination. For everything else the module leaves
the event untouched and vanilla runs exactly as before.

### What counts as a pure repair

Three gates, in order:

```java
if (left.isEmpty() || right.isEmpty() || !left.isDamageableItem() || left.getDamageValue() <= 0) {
    return;
}
if (isRenaming(event.getName(), left)) {
    return; // renaming (even combined with a repair) keeps vanilla costs
}
```

The left item has to be damageable and actually damaged, the right slot has to hold something, and
no rename may be in flight. Then one of two paths:

| Path | Condition | Result |
|---|---|---|
| **Material** | `left.getItem().isValidRepairItem(left, right)` **or** the pair appears in `extra_repair_materials` | repaired by the material, gated on `free_material_repair` |
| **Sacrifice** | the right stack is the same item, damageable, and carries neither `ENCHANTMENTS` nor `STORED_ENCHANTMENTS` | durability merge, gated on `free_combine_repair` |

The kept item may be enchanted, named and as worn as it likes — only the *sacrifice* has to be plain.
An enchanted sword repaired with a second, unenchanted sword keeps every enchantment, because the
result is `left.copy()` with nothing but the damage value changed. Custom name, attribute modifiers
and every other data component come along untouched.

**Renaming is judged, not assumed.** A name only counts as a rename if it would actually change
something: a blank name counts when the item has a `CUSTOM_NAME` to clear, a non-blank one counts
when it differs from the current display name, and `null` — no name sent at all — never counts. So
typing an item's own name back into the box does not cost you the free repair, while renaming *and*
repairing in the same click keeps the full vanilla price for both.

### How much one material unit repairs

```java
private int repairPerUnit(int maxDamage) {
    return Math.max(1, boosted(maxDamage / 4));
}

private int boosted(int base) {
    return base * (100 + getConfig().getRepairBoostPercentValue()) / 100;
}
```

Vanilla's quarter, scaled by `repair_boost_percent` (default 50) and floored at 1. Both divisions
truncate, so the real figure can land a shade under 37.5 % — 585 of 1561 on a diamond pickaxe.

| Item | Max durability | Per unit, vanilla | Per unit, default boost | Units from broken to full |
|---|---|---|---|---|
| Bow | 384 | 96 | 144 | 3 sticks |
| Trident | 250 | 62 | 93 | 3 prismarine shards |
| Diamond pickaxe | 1561 | 390 | 585 | 3 diamonds |
| Netherite pickaxe | 2031 | 507 | 760 | 3 diamonds |

Units are consumed one at a time and the last one only repairs what is left, so a barely scratched
item still costs exactly one unit. The number consumed becomes the material cost, and the rest of
the stack stays in the slot.

The boost applies to **every** material repair the module handles, vanilla materials and
`extra_repair_materials` alike. It deliberately does not apply to combining two of the same item —
there is no material there to make go further.

### The extra materials

| Item | Material | What vanilla does |
|---|---|---|
| All nine netherite tools and armour pieces | Diamond | repairs with a netherite ingot only |
| `create:netherite_diving_helmet` / `_boots` | Diamond | repairs with its own base material |
| `create:copper_diving_helmet` / `_boots` | Copper ingot | repairs with its own base material |
| `minecraft:trident` | Prismarine shard | **no repair material at all** |
| `minecraft:bow` | Stick | **no repair material at all** |

Entries are `item=material`, one per line, and the same item may appear several times to accept
several materials. An entry whose item or material is not installed is skipped in silence (a debug
line only, and only with `debug_logging` on) — that is what happens to the four Create entries in a
pack without Create. A malformed entry is logged with a warning and ignored.

The Create entries are insurance rather than new behaviour: the diving gear already repairs with its
own base material through the ordinary path, and would already be free. The netherite entries are
the real saving — a diamond instead of a netherite ingot.

**`free_material_repair = false` switches the extra materials off entirely**, not just their price.
Both branches sit inside the same `if`:

```java
if (left.getItem().isValidRepairItem(left, right) || isExtraRepairMaterial(left, right)) {
    if (getConfig().isFreeMaterialRepairValue()) {
        applyMaterialRepair(event, left, right);
    }
}
```

With the flag off the module hands the combination back to vanilla, and vanilla has never heard of a
netherite sword plus a diamond: the result slot stays empty. Only the repairs vanilla already knows
fall back to being merely expensive.

### Combining two of the same item

```java
int merged = leftRemaining + rightRemaining + left.getMaxDamage() * 12 / 100;
int newDamage = Math.max(left.getMaxDamage() - merged, 0);
if (newDamage >= left.getDamageValue()) {
    return; // no improvement — leave it to vanilla (which shows no result)
}
```

That is vanilla's own formula, bonus included: both items' remaining durability plus 12 % of the
maximum. No boost, and no result when the merge would not actually help. The sacrifice is consumed
whole — the module sets no material cost, and a material cost of zero means the entire right stack.

### The prior-work penalty

Because the event short-circuits `createResult`, the bump at the end of that method never runs. By
default the module leaves it that way: a free repair does not make the next enchantment more
expensive, which is the part that used to kill the item. `increase_prior_work_penalty = true` puts
vanilla's step back, using vanilla's own arithmetic:

```java
int base = Math.max(
        result.getOrDefault(DataComponents.REPAIR_COST, 0),
        right.getOrDefault(DataComponents.REPAIR_COST, 0)
);
result.set(DataComponents.REPAIR_COST, AnvilMenu.calculateIncreasedRepairCost(base));
```

Note what this does **not** do: it never lowers a penalty an item has already accumulated. An item
sitting at 63 stays at 63 — it simply becomes repairable again, while enchanting it still runs into
the same wall.

<!-- vpa:config:start -->
## Configuration

Section `[modules.free_anvil_repair]` in `config/vanillaplusadditions-common.toml` (or `config/vpa_free_anvil_repair-common.toml` if you run the standalone jar).

Every module also has the universal `enabled` and `debug_logging` keys — see the [Configuration Guide](../guides/configuration.md).

| Key | Type | Default | Range | Effect |
|---|---|---|---|---|
| `extra_repair_materials` | list | `minecraft:netherite_sword=minecraft:diamond, minecraft:netherite_pickaxe=minecraft:diamond, minecraft:netherite_axe=minecraft:diamond, minecraft:netherite_shovel=minecraft:diamond, minecraft:netherite_hoe=minecraft:diamond, minecraft:netherite_helmet=minecraft:diamond, minecraft:netherite_chestplate=minecraft:diamond, minecraft:netherite_leggings=minecraft:diamond, minecraft:netherite_boots=minecraft:diamond, create:netherite_diving_helmet=minecraft:diamond, create:netherite_diving_boots=minecraft:diamond, create:copper_diving_helmet=minecraft:copper_ingot, create:copper_diving_boots=minecraft:copper_ingot, minecraft:trident=minecraft:prismarine_shard, minecraft:bow=minecraft:stick` | — | Additional anvil repair materials (Quark-style, format item=material, one item may appear in several entries). These repairs are computed by this module and are free like regular material repairs (and follow repair_boost_percent). Entries whose item or material is not installed are skipped silently; the config validator additionally rejects entries with no '=', with '=' at either end, or with a second '='. |
| `free_combine_repair` | boolean | `true` | — | Combining two items of the same type costs no XP levels, as long as the sacrifice item is damageable and carries neither enchantments nor stored enchantments (pure durability merge). The kept item may be enchanted. |
| `free_material_repair` | boolean | `true` | — | Repairing with the item's repair material (e.g. diamonds) costs no XP levels. Also the master switch for extra_repair_materials: with false, those non-vanilla combinations stop working altogether instead of just costing XP. |
| `increase_prior_work_penalty` | boolean | `false` | — | Whether free repairs still double the hidden prior-work penalty (REPAIR_COST component via AnvilMenu.calculateIncreasedRepairCost) like vanilla does; default false so repairing does not make later enchant operations more expensive. |
| `repair_boost_percent` | int | `50` | 0 ~ 500 | Extra durability restored per material unit on every module-handled MATERIAL repair, vanilla material and extra_repair_materials alike: perUnit = max(1, maxDamage/4 * (100+value)/100). 0 = vanilla 25%/unit; the default 50 = 37.5%/unit, so 3 units fully repair an item instead of 4. Does NOT apply to combining two of the same item, and the JEI display ignores it. |
<!-- vpa:config:end -->

## Compatibility and known limits

| Limit | Effect |
|---|---|
| `free_material_repair = false` | The extra combinations stop working altogether, not just for free. Vanilla does not recognise them, so the result slot stays empty. |
| No Create installed | The four `create:` entries are skipped in silence. Nothing else changes; there is no compile-time or runtime dependency on Create in this module. |
| No JEI installed | The extra combinations still work, they are just not listed anywhere. The plugin class is only ever loaded by JEI's own annotation scan. The pack this mod is tested against runs EMI with TooManyRecipeViewers rather than JEI, so the anvil entries reach the screen through that bridge and are untested here. |
| JEI's displayed repair amount | The plugin hardcodes vanilla's 25 % per unit and ignores `repair_boost_percent`, so with the default boost the shown recipe understates the real repair. |
| Quark's diamond repair | Not an integration and not a conflict. Quark patches the vanilla anvil code path; this module answers the event first and produces the result itself, so Quark's version never runs and cannot charge for the same combination. |
| Another mod's `AnvilUpdateEvent` handler | The mixin permits the pickup of **any** non-empty cost-0 anvil result, not only ones from this module — vanilla never produces such a result, so nothing vanilla breaks, but a third-party handler's free result becomes takeable too while this module is on. |
| Two handlers on the same combination | The module overwrites the event's output unconditionally when it recognises a pure repair; it does not check whether another handler already set one. |
| Anvil wear and XP | `onTake` is untouched: the anvil still has its usual chance to chip, and `AnvilRepairEvent` still fires. A cost of 0 simply means no levels change hands. |
| Module disabled | Both the event handler and the mixin's hook ask `isModuleEnabled()` on every call, so turning it off takes effect immediately — no restart, and the anvil is back to vanilla prices. |
| Tests | This repository has no unit tests. Everything on this page is read off the source and the decompiled 1.21.1 `AnvilMenu`. |

## Under the hood

| File | Role |
|---|---|
| `modules/free_anvil_repair/FreeAnvilRepairModule.java` | the event handler and all the arithmetic |
| `modules/free_anvil_repair/config/FreeAnvilRepairConfig.java` | the five keys and the list validator |
| `modules/free_anvil_repair/compat/jei/FreeAnvilRepairJeiPlugin.java` | the extra combinations in JEI's anvil tab |
| `mixin/free_anvil_repair/AnvilMenuFreeRepairMixin.java` | lets a cost-0 result be taken |
| `standalone/free_anvil_repair/FreeAnvilRepairStandalone.java` | `@Mod("vpa_free_anvil_repair")` |

No registries, no items, no blocks, no commands, no keybinds, no lang keys, no datapack files. The
module is one game-bus subscriber and one mixin.

**Why a non-empty output wins.** NeoForge's hook sits at the top of `createResult`, before anything
vanilla computes:

```java
if (e.getOutput().isEmpty())
    return true;

outputSlot.setItem(0, e.getOutput());
container.setMaximumCost(e.getCost());
container.repairItemCountCost = e.getMaterialCost();
return false;
```

`false` makes `createResult` return on the spot. That is the whole trick behind the *Too Expensive!*
bypass — the check never executes — and it is also why the module has to reimplement vanilla's repair
maths rather than adjust it.

**The mixin.**

```java
@Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
private void vpaAllowFreeRepairPickup(Player player, boolean hasStack,
                                      CallbackInfoReturnable<Boolean> cir) {
    AnvilMenu self = (AnvilMenu) (Object) this;
    if (hasStack && self.getCost() == 0 && FreeAnvilRepairModule.allowFreePickup()) {
        cir.setReturnValue(true);
    }
}
```

The boolean parameter is the result slot's `hasItem()` — `ItemCombinerMenu`'s result slot calls
`mayPickup(player, this.hasItem())`. The mixin is listed in the `"mixins"` block of
`vanillaplusadditions.mixins.json`, not in `"client"`, so it applies on both sides, and it is the
standalone jar's only mixin.

Note what it does *not* ask: who produced the result. Any non-empty cost-0 anvil output becomes
takeable while the module is enabled. The justification in the code is that vanilla never creates
one — which is true, but it does mean the mixin is a general-purpose zero-cost pickup, not a
module-private one.

**The handshake.** `allowFreePickup()` is static and reads a static `instance` field assigned in the
constructor, so the mixin reaches the module without a hard reference through `ModuleManager`. The
standalone jar constructs the module through `FreeAnvilRepairStandalone`, so `instance` is bound
there too.

**Deliberate cross-module coupling.** `waystone_amethyst_repair` asks `ModuleManager` whether
`free_anvil_repair` is enabled and sets cost 0 when it is, riding on exactly this mixin for the
pickup — so the Warp Stone repair is free only while this module is on, and costs one level per
shard otherwise. `CatArmorItem` and `AxolotlArmorItem` note the same thing for their scute repairs:
those repairs work on their own, only the zero price comes from here. The coupling is one-way; this
module imports nothing from any other module.

**Caching and live config.** `isModuleEnabled()` and every config getter are read per event, and
`extra_repair_materials` is re-parsed whenever the config list stops equalling the cached one. All
five keys plus `enabled` therefore take effect without a restart.

**Validator versus parser.** The config spec rejects an entry with no `=`, with `=` at either end, or
with a *second* `=`:

```java
int eq = s.indexOf('=');
return eq > 0 && eq < s.length() - 1 && s.indexOf('=', eq + 1) < 0;
```

The runtime parser is looser — it splits on the first `=` and would accept the rest as a material id
— so an entry with two `=` is corrected away by NeoForge before the parser ever sees it. The two
also differ on unresolved ids: the parser checks `BuiltInRegistries.ITEM.containsKey`, while the JEI
plugin compares the looked-up item against `Items.AIR`.

**JEI.** One anvil recipe per entry whose item and material both resolve and whose item is
damageable, registered under `vanillaplusadditions:anvil.extra_repair.<path>`. JEI's anvil list is a
hardcoded vanilla one and cannot discover a code-only repair, hence the explicit registration. The
plugin registers nothing when the module is disabled or `free_material_repair` is off. JEI itself is
`compileOnly` API plus `localRuntime`, with no entry in `neoforge.mods.toml`, so there is no
published dependency on it.

## See also

* [Waystone Amethyst Repair](waystone_amethyst_repair.md) — free only while this module is enabled
* [Cat Guardian](cat_guardian.md) and [Axolotl Guardian](axolotl_guardian.md) — their scute repairs cost nothing here
* [Configuration Guide](../guides/configuration.md) — where the config file lives
* [All modules](../../README.md#-modules)
