# Glider Water Repair

> **TL;DR** — A paraglider from the Gliders mod that a lightning strike left broken glides again once
> you throw it in the water. Only the broken flag clears — the durability it lost stays lost.

<!-- vpa:meta:start -->
|  |  |
|---|---|
| **Module ID** | `glider_water_repair` |
| **Side** | Server only |
| **Requires** | [Gliders](https://modrinth.com/mod/gliders) <sub>tested 1.1.8</sub> |
| **Works with** | — |
| **Download** | [`vpa_glider_water_repair.jar`](https://github.com/GeraldHofbauerWeb/vanillaplusadditions/releases/latest/download/vpa_glider_water_repair.jar) · also needs `vpa_core` |
| **Config section** | `[modules.glider_water_repair]` |
| **Since** | `v1.0.0-beta.86` |
<!-- vpa:meta:end -->

## What it does

Glide through rain in the Gliders mod and it eventually drops a real lightning bolt on you — the bolt
is the mod's doing, no thunderstorm required — which without the copper upgrade **wrecks the
paraglider**, and that part stays exactly as the mod intends. What changes is the way back. The only
cure the mod offers is an anvil plus the Reinforced Paper of that glider's exact tier (for an iron
one: 5 leather + 4 paper + 8 iron ingots), while the broken glider wears a charred sprite and looks
for all the world like it is on fire. So here, dunking it works: a broken glider lying in water
becomes usable again, with a hiss and a puff of steam.

You have to actually throw it in. Only a **dropped item** is ever looked at — a broken glider in your
inventory, in the chest slot or in a Curios back slot is untouched however deep you wade.

Only the broken flag clears; the durability the strike cost stays gone, so:

* the strike still costs something;
* the Reinforced Paper repair keeps its purpose — it is still the only way to get the bar back;
* you are not locked out of flying until you have mined eight iron.

## Why it exists

Flying in the rain is supposed to be dangerous. Glide through rain for more than 200 ticks and
`GliderUtil.lightningLogic` spawns a real bolt straight onto the player:

```java
if (player.level().random.nextInt(24) == 0 && lightningTimer > 200 && !GliderItem.hasBeenStruck(glider)) {
    LightningBolt lightningBolt = new LightningBolt(EntityType.LIGHTNING_BOLT, level);
    lightningBolt.setPos(player.getX(), player.getY(), player.getZ());
    lightningBolt.setVisualOnly(false);
    level.addFreshEntity(lightningBolt);
}
```

`GliderEventsNeoForge.onEntityStruckByLightning` then decides what it costs. Without the copper
upgrade, `GliderItem.setBroken(chestItem, true)` — the glider is wrecked. With the upgrade it survives,
but the strike is not free: the mod marks it `struck` instead and deals two points of its own
`vc_gliders:zap_experiment` damage. Only then is the bolt's *vanilla* damage cancelled:
`onLivingHurt` zeroes `minecraft:lightning_bolt` for an upgraded player who is actually gliding,
so of the whole strike only the mod's own two points land. Without the upgrade nothing is zeroed —
the bolt hits in full *and* the glider breaks.

**That part is deliberately left alone.** The strike is the point of the mechanic.

What this module changes is the way back. The broken flag is the `vc_gliders:broken` data component,
and it does two things at once: the model switches to `damaged_glider`, and `isGlidingEnabled`
returns false. The mod's own cure is an `AnvilUpdateEvent` handler that takes the glider on the left
and the **Reinforced Paper of that glider's exact tier** on the right, clears the flag, sets the
damage to 0 and prices the whole visit at a flat 5 levels:

| Tier | Repair material | What that costs |
|---|---|---|
| Wood | `reinforced_paper` | 5 leather + 4 paper |
| Iron | `reinforced_paper_iron` | the above + 8 iron ingots |
| Gold | `reinforced_paper_gold` | the above + 8 gold ingots |
| Diamond | `reinforced_paper_diamond` | the above + 8 diamonds |
| Netherite | `reinforced_paper_netherite` | the above + 8 netherite scrap |

The price is not the five levels, it is the shopping list: not an iron ingot on its own, not a
diamond, but the tier's paper — which nobody carries on a flight. Meanwhile the charred sprite makes
the glider look as if it is on fire, so the first thing anyone tries is to dunk it. Here that does
something.

> Everything this page states about the Gliders mod itself was read off
> `gliders-1.21.1-neoforge-1.1.8.jar`, the version this pack ships (see the tested-versions table in
> the README). Nothing in this repository compiles against it.

## In detail

### When the check runs

One handler, and it is almost all guard clauses:

```java
if (!(event.getEntity() instanceof ItemEntity itemEntity)
        || itemEntity.tickCount % WATER_CHECK_INTERVAL != 0
        || !isModuleEnabled()
        || !(itemEntity.level() instanceof ServerLevel level)
        || !itemEntity.isInWater()) {
    return;
}
```

`EntityTickEvent.Post` fires on both sides for every ticking entity, so the order matters: the two
cheap tests come first, and the `ServerLevel` test means nothing at all runs on a client.
`WATER_CHECK_INTERVAL` is 20, so each dropped item is examined **once a second**, on its own
schedule — the counter is the item entity's, not the level's, so items dropped at different moments
check on different ticks. Up to a second of delay is intentional: the thing has to sink and bob
first anyway, and that is the beat the hiss wants.

`isInWater()` is water the item's hitbox touches. Rain does not count.

### Which items count

A glider is recognised by its registry id alone — namespace `vc_gliders`, path beginning with
`paraglider`:

```java
ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
return GLIDER_MOD_ID.equals(id.getNamespace()) && id.getPath().startsWith(GLIDER_ITEM_PREFIX);
```

In 1.1.8 that is exactly the five tiers — `paraglider_wood`, `_iron`, `_gold`, `_diamond`,
`_netherite` — and nothing else the mod ships (`copper_upgrade`, `nether_upgrade`,
`reinforced_paper*`). Applying an upgrade does not create a sixth item, though: `copper_upgrade` and
`nether_upgrade` are consumed at the anvil and set a data component on the paraglider itself. The
component changes how the glider behaves, but not what item it is: only the model swaps, through
the `upgrade_level` item property. An upgraded glider therefore keeps its `paraglider_*` id and is
covered by the same prefix. It is a prefix match rather than a list because a sixth tier should not
need a code change.

### What changes, and what does not

The entire effect is one line: `stack.set(requireBrokenComponent(), false)`.

| | After the dunk |
|---|---|
| `vc_gliders:broken` | `false` — the glider glides again and loses the charred sprite once it is back in your hands |
| Durability | **untouched.** `isGlidingEnabled` is `glide && !broken`; the mod's `isTooBroken` check exists but is never called, so a nearly worn-out glider flies |
| `vc_gliders:struck` | untouched, and it does not need to be: the mod clears it itself the moment flying is blocked — standing on the ground counts, so does being in water — which means the glider can be struck again |
| Copper / nether upgrade, name, anything else on the stack | untouched |

### The hiss

Both pieces of feedback are emitted server-side, so a client that does not run this module sees and
hears them anyway:

| | Value |
|---|---|
| Sound | `SoundEvents.FIRE_EXTINGUISH`, `SoundSource.NEUTRAL`, volume 0.7, pitch 1.2 |
| Particles | 8 × `ParticleTypes.CLOUD` at the item's position + 0.2 on Y, spread 0.2 / 0.1 / 0.2, speed 0 |

One `debug` line goes to the log per repair — see the [Debug Logging Guide](../guides/debug-logging.md).

### A glider with an empty bar

Gliding costs durability: the mod takes one point every 100 ticks, every 40 in the Nether, where
without the nether upgrade each step costs **half the bar** instead of a point. When the damage
reaches the maximum it plays the same extinguishing sound, sets the broken flag — and, outside the
Nether, sets the stack's count to 0. The glider is gone, not broken, so there is nothing left to
throw in the water.

A broken glider whose bar is genuinely empty therefore practically only comes out of the Nether, and
for that one the dunk buys a single flight. Stay in the Nether and the next durability step finds the
damage still at the maximum and sets the flag again. Carry the glider out and that same step also
sets the stack's count to 0, so it is destroyed rather than merely broken a second time. For every
ordinary strike victim — broken with durability left — the repair holds.

<!-- vpa:config:start -->
## Configuration

Section `[modules.glider_water_repair]` in `config/vanillaplusadditions-common.toml` (or `config/vpa_glider_water_repair-common.toml` if you run the standalone jar).

Every module also has the universal `enabled` and `debug_logging` keys — see the [Configuration Guide](../guides/configuration.md).

This module has no settings of its own.
<!-- vpa:config:end -->

## Compatibility and known limits

| Limit | Effect |
|---|---|
| Gliders not installed | `shouldInitialize()` is `ModList.get().isLoaded("vc_gliders")`, so the module logs *"is disabled and will not be initialized"* and registers no handler at all. Nothing declares the dependency statically — neither `neoforge.mods.toml` nor the generated standalone toml names `vc_gliders`. |
| Items in a slot | Never touched. Only an `ItemEntity` is examined, so the glider has to be on the ground (or in the water) as a dropped item. |
| Gliders renames its items | Detection is a prefix match on `vc_gliders:paraglider…`. A rename stops the module silently — no warning, no log line. |
| Gliders renames the `broken` component | Looked up once, lazily, the first time it is needed. If it is missing then, a warning is logged **once** and the module stays quiet for the rest of the session; there is no retry. |
| The runtime toggle is one-way | `isModuleEnabled()` is re-read inside the handler, so `/vpa module disable glider_water_repair` takes effect at once. Enabling does nothing until a restart: only a module that was enabled when the config was first read ever reaches `onInitialize`, and that is where the handler is registered. The same applies to a server that started without Gliders. |
| The item in the water | The stack is mutated in place and `ItemEntity.setItem` is never called, so the entity's synced stack is not marked dirty. The hiss and the steam are sent explicitly and always arrive; whether the sprite on the ground drops its charred look before you pick the glider up is not established. <!-- TODO: verify in game whether the dropped item's model refreshes before pickup, or only after the entity is re-tracked. --> |
| Testing | This repository has no unit tests, and Gliders is not on the development classpath — no jar in `libs/`, no compile dependency — so `runClient` and `runServer` cannot exercise the module at all. It needs a real 1.21.1 instance with the mod present. |

## Under the hood

Three files, no mixins, no assets, no lang keys, no commands, no keybinds, no data files.

| File | Lines | Role |
|---|---|---|
| `modules/glider_water_repair/GliderWaterRepairModule.java` | 132 | The gate, the handler, the component lookup |
| `modules/glider_water_repair/config/GliderWaterRepairConfig.java` | 18 | Empty subclass — the module adds no keys of its own |
| `standalone/glider_water_repair/GliderWaterRepairStandalone.java` | 20 | `@Mod("vpa_glider_water_repair")`, delegating to `StandaloneModuleBootstrap` |

**One event.** `onInitialize` registers the module instance on `NeoForge.EVENT_BUS`; the only
`@SubscribeEvent` is `EntityTickEvent.Post`. There is no mod-bus work, nothing is registered, and the
module is server-side in effect because of the `ServerLevel` guard rather than by declaration.

**Nothing is compiled against Gliders.** The mod is found through `ModList.get().isLoaded`, its items
through their registry ids, and its component through the registry:

```java
brokenComponent = (DataComponentType<Boolean>)
        BuiltInRegistries.DATA_COMPONENT_TYPE.get(BROKEN_COMPONENT_ID);
```

Resolving it lazily rather than at startup keeps the module independent of mod load order. The cast
is unchecked; against 1.1.8 it is correct, because the mod registers `broken` with `Codec.BOOL` and
reads it back as a `Boolean`.

**History.** This module replaced `glider_lightning_guard` in commit `e7469c4`. The predecessor took
a quarter of the paraglider's durability bar instead of letting the strike break it, and searched
Curios slots to find the worn glider; both went, the first because the danger of flying in the rain
is the point of the mechanic, the second because only dropped items are looked at now. It never
shipped in a tagged release — the tags jump straight from `v1.0.0-beta.81` to `v1.0.0-beta.86` — so
only someone running a build from that window carries the old state. For them, the config section
was renamed from `[modules.glider_lightning_guard]` to `[modules.glider_water_repair]` and the
`durability_cost` key is gone; both leftovers in an existing toml are dead entries.

## See also

* [Configuration Guide](../guides/configuration.md) — where the config file lives
* [Debug Logging Guide](../guides/debug-logging.md) — turning on the per-repair log line
* [All modules](../../README.md#-modules)
