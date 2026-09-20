# End Conduit

> **TL;DR** — A violet conduit for the End: ring it with 16 Glowstone, End Stone, End Stone Bricks
> or Sea Lanterns — no water anywhere — and it grants Conduit Power on dry End ground, which with
> End Oxygen running means you can breathe there.

<!-- vpa:meta:start -->
|  |  |
|---|---|
| **Module ID** | `end_conduit` |
| **Side** | Client + Server |
| **Requires** | — |
| **Works with** | — |
| **Download** | [`vpa_end_conduit.jar`](https://github.com/GeraldHofbauerWeb/vanillaplusadditions/releases/latest/download/vpa_end_conduit.jar) · also needs `vpa_core` |
| **Config section** | `[modules.end_conduit]` |
| **Since** | `v1.0.0-beta.46` |
<!-- vpa:meta:end -->

## What it does

The End has no water, so a vanilla conduit cannot be built there at all. This module adds a second
one that can: a separate craftable block, rendered as a conduit tinted violet and endstone-gold,
which activates only in `minecraft:the_end`, takes its frame from Glowstone, End Stone, End Stone
Bricks or Sea Lantern, and needs no water — neither around the block nor on the player.

You build it the way you build a conduit. Block in the middle, 16 frame blocks in the usual ring two
blocks out. It comes up with the conduit's own activation sound, the cage starts turning, nautilus
particles drift in from the frame, and every player within 32 blocks has Conduit Power.

Conduit Power on its own is underwater night vision and a mining-speed bonus, which is not much use
on dry End stone. The reason to build this block is the [End Oxygen](end_oxygen.md) module: with
`conduit_power_grants_air` left on, Conduit Power refills a player's air every tick in the End. One
End Conduit therefore turns a patch of End stone into a base you can stand in without a backtank,
indefinitely and without line of sight to anything.

It is crafted from four chorus fruit, four eyes of ender and an ordinary conduit, so a Heart of the
Sea and its nautilus shells are still the entry price and the End trip comes on top.

## In detail

### What it borrows from the vanilla conduit, and what it drops

`EndConduitBlockEntity` is vanilla's `ConduitBlockEntity` retyped, with four deliberate cuts.
Everything else is the same code: the 40-tick cadence, the ring geometry, the sound schedule, the
particle maths, the `-0.0375F` rotation speed, the 260-tick effect.

**The water gate is gone.** Vanilla refuses to form unless the 3×3×3 cube around the block is water,
and this loop has no counterpart here:

```java
for (int i = -1; i <= 1; i++) {
    for (int j = -1; j <= 1; j++) {
        for (int k = -1; k <= 1; k++) {
            BlockPos blockpos = pos.offset(i, j, k);
            if (!level.isWaterAt(blockpos)) {
                return false;
            }
        }
    }
}
```

**So is the second water check.** Vanilla grants the effect only to a player who is already wet:

```java
if (pos.closerThan(player.blockPosition(), (double)j) && player.isInWaterOrRain()) {
    player.addEffect(new MobEffectInstance(MobEffects.CONDUIT_POWER, 260, 0, true, true));
}
```

Ours keeps the distance test and drops `isInWaterOrRain()`. Standing on dry End stone counts.

**The frame set is a fixed array.** Vanilla asks the block itself through NeoForge's
`isConduitFrame`, whose default answer is prismarine, prismarine bricks, dark prismarine and sea
lantern. This module tests against four constants instead:

```java
private static final Block[] VALID_FRAME = {
        Blocks.GLOWSTONE, Blocks.END_STONE, Blocks.END_STONE_BRICKS, Blocks.SEA_LANTERN
};
```

Sea Lantern is the one block in both sets, which is what lets a sea lantern frame do double duty.

**And it never hunts.** Vanilla's `updateDestroyTarget` — the part that picks a hostile mob and deals
4 magic damage to it every two seconds — has no counterpart at all. Neither has the target UUID it
saves to NBT.

| | Vanilla conduit | End Conduit |
|---|---|---|
| Frame blocks | Prismarine, Prismarine Bricks, Dark Prismarine, Sea Lantern (extensible via `isConduitFrame`) | Glowstone, End Stone, End Stone Bricks, Sea Lantern (fixed array) |
| Water around the block | 3×3×3 must be water | not checked |
| Player must be wet | `isInWaterOrRain()` | not checked |
| Dimension | any | `minecraft:the_end` only |
| Activation threshold | 16, hardcoded | `min_frames`, default 16 |
| Effect radius | `frames / 7 * 16` | the same, divided by `effect_radius_divisor` |
| Attacks hostiles | from 42 frame blocks, 8 blocks away, 4 damage | never |
| Waterloggable | yes | no |
| Saved to NBT | the current target's UUID | nothing |

### Activation

The frame ring is vanilla's, position for position — the same `-2..2` triple loop with the same
"exactly one axis at zero, another at ±2" test. Counted out, that ring holds exactly **42**
positions, which is why 42 is both the upper bound of `min_frames` and the hardcoded number at which
the eye opens.

Three things must hold for the block to light up:

1. at least `min_frames` of the ring's 42 positions carry a frame block (default 16),
2. `level.dimension() == Level.END`,
3. the module is enabled — `EndConduitModule.isModuleActive()`, which resolves through the module
   manager on every call, so `/vpa module disable end_conduit` takes effect at the next check.

All three are re-tested **only when `gameTime % 40 == 0`**, so finishing the last frame block, or
walking back into a chunk, can take up to two seconds to register. Nothing is written to NBT — the
block entity overrides neither `saveAdditional` nor `loadAdditional` — so the state after a chunk
load is always rebuilt from the world, never restored.

Client and server each run their own ticker (`clientTick` / `serverTick`) and each recomputes the
shape for itself. That is vanilla's design and it means activation, rotation and the hunting flag
need no packet; see the compatibility table for what it costs.

### The radius

```java
public static int effectRadius(int frames) {
    int divisor = module != null ? Math.max(1, module.getConfig().getEffectRadiusDivisor()) : 1;
    return Math.max(1, frames / 7 * 16 / divisor);
}
```

Every step is integer division and it takes the **actual** frame count, not `min_frames`. At the
default divisor of 1 the table is vanilla's:

| Frame blocks | Radius |
|---|---|
| 16–20 | 32 |
| 21–27 | 48 |
| 28–34 | 64 |
| 35–41 | 80 |
| 42 | 96 |

The effect itself is `MobEffects.CONDUIT_POWER` for 260 ticks (13 seconds), amplifier 0, ambient and
visible, re-applied every 40 ticks — a comfortable margin over the two-second gap, so the icon never
flickers while you stay in range.

One quirk worth knowing: `min_frames` goes down to 1, but the radius does not follow. Build fewer
than seven frame blocks and `frames / 7` is 0, so the radius collapses to the `Math.max(1, …)` floor
of one block — the conduit activates, plays its sounds and reaches nobody standing next to it.

The candidate box is vanilla's odd one: an AABB inflated by the radius and then
`expandTowards(0, level.getHeight(), 0)`. The per-player test afterwards is
`pos.closerThan(player.blockPosition(), radius)`, so the granted area is a sphere; the vertical
expansion only widens the list of players considered.

### The eye that means nothing

`MIN_HUNT_SIZE` is 42 and hardcoded, and `isHunting` is set from nothing but `positions.size() >= 42`.
It feeds exactly one line, in the renderer:

```java
this.eye.render(poseStack,
        (blockEntity.isHunting() ? EndConduitTextures.OPEN_EYE : EndConduitTextures.CLOSED_EYE)
                .buffer(bufferSource, RenderType::entityCutoutNoCull),
        packedLight, packedOverlay);
```

The End Conduit has no attack code whatsoever. The open eye is decoration for a full 42-block frame,
nothing more. Because 42 is hardcoded while `min_frames` is configurable up to 42, setting
`min_frames = 42` makes the eye open the moment the conduit activates.

### Sounds and particles

| Cue | When |
|---|---|
| `CONDUIT_ACTIVATE` / `CONDUIT_DEACTIVATE` | server side, on every change of the active flag |
| `CONDUIT_AMBIENT` | while active, every 80 game ticks |
| `CONDUIT_AMBIENT_SHORT` | while active, at `60 + random(40)` ticks after the last one |
| `end_nautilus` particles | client side only, while active: per frame block, a 1-in-50 roll each tick |

The particles are spawned from a point that bobs above the block
(`sin((tickCount + 35) · 0.1) / 2 + 0.5`, squared and scaled) and fly towards the conduit from the
frame block that produced them — vanilla's nautilus behaviour, reused verbatim, with our tinted
sprite bound to it.

One difference from vanilla, easy to miss: vanilla calls `animationTick` on every client tick
regardless of the active flag, this module calls it only while active. An End Conduit that is short
of its frame count therefore stays completely still, where a vanilla conduit sitting in water with
too few frame blocks still draws particles.

### Breathing in the End

The End Conduit itself does nothing about air. It grants Conduit Power; the breathing is
[End Oxygen](end_oxygen.md)'s doing, in its `LivingBreatheEvent` handler:

```java
if (getConfig().conduitPowerGrantsAir() && player.hasEffect(MobEffects.CONDUIT_POWER)) {
    event.setCanBreathe(true);
    event.setRefillAirAmount(player.getMaxAirSupply());
    return;
}
```

`conduit_power_grants_air` lives in `[modules.end_oxygen]`, not here, and defaults to `true`. Turn
that key off, or disable End Oxygen entirely, and the End Conduit is back to being a source of
Conduit Power and nothing else. The effect is not exclusive to this block either — any Conduit Power,
from a vanilla conduit or a command, opens the same branch.

## Items, blocks and recipes

There is no rendered icon for this page: the End Conduit is drawn from the vanilla conduit's Java
geometry in both the world and the inventory, so there is no flat sprite to show.

| | Details |
|---|---|
| **End Conduit** (block) | `vanillaplusadditions:end_conduit`. Purple map colour, hardness and blast resistance 3.0, metal sounds, `noOcclusion`, light level **15 unconditionally** — it glows at full brightness even when inactive and outside the End. Hitbox is the vanilla conduit's 6×6×6 core, `Block.box(5, 5, 5, 11, 11, 11)`. `RenderShape.ENTITYBLOCK_ANIMATED`, so every visual comes from the block entity renderer. Not waterloggable, on purpose. |
| **End Conduit** (item) | A plain `BlockItem` with default properties: stacks to 64, no rarity, in the mod's main creative tab. Its inventory look is the 3D tinted shell, drawn by `EndConduitItemRenderer`. |

`block.vanillaplusadditions.end_conduit` is the only lang key the module has; the item inherits it.

**Drops.** `getDrops` returns `List.of(new ItemStack(this))` and ignores everything else it is handed
— tool, Silk Touch, explosion context. The End Conduit always drops itself, including when blown up.

**Recipe** — shaped, category `MISC`, id `vanillaplusadditions:end_conduit`:

```
 F E F        F = Chorus Fruit
 E C E        E = Eye of Ender
 F E F        C = Conduit
```

Per the project convention it is registered in code rather than as a datapack JSON: a reload listener
added in `AddReloadListenerEvent` copies the whole recipe map into a `LinkedHashMap`, adds this one
and calls `RecipeManager.replaceRecipes`, on every datapack reload. See
[Custom Crafting Recipes](custom_crafting_recipes.md) for why.

<!-- vpa:config:start -->
## Configuration

Section `[modules.end_conduit]` in `config/vanillaplusadditions-common.toml` (or `config/vpa_end_conduit-common.toml` if you run the standalone jar).

Every module also has the universal `enabled` and `debug_logging` keys — see the [Configuration Guide](../guides/configuration.md).

| Key | Type | Default | Range | Effect |
|---|---|---|---|---|
| `effect_radius_divisor` | int | `1` | 1 ~ 16 | Conduit Power radius = (actual frame count / 7 * 16) / this divisor, floored at 1 block (all integer division). 1 (default) = full vanilla Conduit Power radius: 32 blocks at a 16-block frame, 96 at a full 42-block frame. The module additionally clamps the value with Math.max(1, ...). |
| `min_frames` | int | `16` | 1 ~ 42 | Minimum frame-block count (Glowstone / End Stone / End Stone Bricks / Sea Lantern) required to activate the End Conduit. Vanilla conduit uses 16; 42 is the maximum the ring geometry can hold. Read live on both sides, so it also decides what the client renders as active. |
<!-- vpa:config:end -->

## Compatibility and known limits

| Limit | Effect |
|---|---|
| **The standalone jar does not work** | `vpa_end_conduit.jar` is built and published, but there is no `standalone/end_conduit/` package in the source tree, so the jar carries no `@Mod` entry point. Nothing constructs `EndConduitModule`, `onInitialize` never runs, no register is ever attached to a mod bus — the jar ships no block, no item and no recipe. Use the bundle. Read off `build.gradle` (the descriptor at line 422, the jar task's `standalone/${entrypointPkg}/**` include) and a `git log` that has never seen that package; not tried in a real instance. |
| Modded conduit frame blocks | Vanilla asks `isConduitFrame`, which any mod can answer; this module tests a fixed four-block array. A modded block that works in a vanilla conduit frame will not work here. |
| `enabled = false` is not a content switch | Registration happens before the config spec exists (`ModuleManager.initializeModules` runs at `VanillaPlusAdditions.java:114`, `registerConfig` at `:131`), so `isEnabled()` still falls back to the module default at that point and block, item, block entity type and particle type are **always** registered. Disabling the module stops activation — no Conduit Power, no particles, a dark shell — and stops the recipe injection on the next reload. Placed blocks stay placed, keep dropping themselves, and the item stays in the creative tab. |
| Toggling at runtime | `/vpa module enable\|disable end_conduit` resolves on every check, so activation follows immediately. The recipe follows only at the next datapack reload. |
| Config is not synced to the client | `min_frames` and the enabled flag live in a **common** config, which is per installation. Both sides evaluate activation for themselves, so a client whose local file disagrees with the server renders the conduit active when it is not, or the other way round. Derived from the code; not reproduced in game. |
| Break and step particles are white | `models/block/end_conduit.json` carries nothing but `"particle": "minecraft:block/sea_lantern"`. Mining it throws sea-lantern-white particles, not violet ones. The item model does point at our tinted `base` sprite. |
| It glows in the dark everywhere | `lightLevel` is 15 with no state test — inactive, in the Overworld, in a chest room, it is still a full light source. |
| Never attacks | Despite the open eye at 42 frame blocks. If you want a conduit that shoots things, that is a vanilla conduit plus [Conduit Attack Range](conduit_attack_range.md) — a separate module that mixes into vanilla's `ConduitBlockEntity` and does not touch this block at all. |
| No tests | This repository has no unit tests, and nothing in it records an in-game test of this module. Everything above is read off the source. |

## Under the hood

No mixins, no commands, no keybinds, no network payloads, no `ModList.isLoaded` checks, and no data
files — the module is four registrations and a block entity ticker.

| Class | Role |
|---|---|
| `modules/end_conduit/EndConduitModule` | The four registers, the creative-tab entry, `minFrames()` / `effectRadius()`, the recipe reload listener |
| `modules/end_conduit/block/EndConduitBlock` | `BaseEntityBlock`: shape, ticker, render shape, `getDrops` |
| `modules/end_conduit/blockentity/EndConduitBlockEntity` | Activation, effect, sounds, particles |
| `modules/end_conduit/config/EndConduitConfig` | `min_frames`, `effect_radius_divisor` |
| `modules/end_conduit/client/EndConduitBER` | The placed block, near-verbatim vanilla `ConduitRenderer` |
| `modules/end_conduit/client/EndConduitItemRenderer` | The inventory item, as the tinted 3D shell |
| `modules/end_conduit/client/EndConduitTextures` | The six sprite materials |
| `modules/end_conduit/client/EndConduitClientSetup` | Binds all three of the above |

| Registry | Id |
|---|---|
| Block | `vanillaplusadditions:end_conduit` |
| Item | `vanillaplusadditions:end_conduit` (plain `BlockItem`) |
| Block entity type | `vanillaplusadditions:end_conduit` |
| Particle type | `vanillaplusadditions:end_nautilus` (`SimpleParticleType`, limiter off) |

| Event | Bus | Purpose |
|---|---|---|
| `AddReloadListenerEvent` | game | Injects the crafting recipe; returns early unless the module is enabled |
| `EntityRenderersEvent.RegisterRenderers` | mod, client | `EndConduitBER` for the block entity type |
| `RegisterParticleProvidersEvent` | mod, client | `end_nautilus` reuses `FlyTowardsPositionParticle.NautilusProvider` |
| `RegisterClientExtensionsEvent` | mod, client | `EndConduitItemRenderer` as the item's custom renderer |

The three client handlers sit in `EndConduitClientSetup`, which is an FML-scanned
`@EventBusSubscriber(value = Dist.CLIENT, bus = MOD)` and is therefore **not** gated on the module.
In the bundle that is harmless — registration always ran, so `END_CONDUIT_BE`, `END_NAUTILUS` and
`END_CONDUIT_ITEM` are always bound by the time it dereferences them.

**Rendering.** `EndConduitBER` registers no model layer of its own. It bakes the vanilla-registered
`ModelLayers.CONDUIT_EYE`, `CONDUIT_WIND`, `CONDUIT_SHELL` and `CONDUIT_CAGE` and draws them with our
sprites, so the geometry and the animation are the vanilla conduit's and only the pixels differ. The
wind layer cycles through three orientations on `tickCount / 66 % 3`, the eye billboards to the
camera, and an inactive conduit draws the shell alone, turned to its last rotation.

**Textures.** Six sprites under `assets/vanillaplusadditions/textures/block/end_conduit/` — `base`,
`cage`, `wind`, `wind_vertical`, `open_eye`, `closed_eye` — violet and endstone-gold recolours of the
vanilla conduit's. They sit under `block/`, so the vanilla block atlas stitches them without any
registration; `EndConduitTextures` only names them. Both wind sprites carry a `.mcmeta` with
`frametime: 3` over a 32-pixel-high strip. The particle is a seventh tinted sprite,
`textures/particle/end_nautilus.png`, wired up through `particles/end_nautilus.json`.

**Version trail.** The module landed in `v1.0.0-beta.46` (`d73a4b0`) as a working but
vanilla-looking conduit. The tinted block and item textures and the 3D item renderer arrived one day
later in `v1.0.0-beta.47` (`1971b8a`), the tinted particle in `v1.0.0-beta.53` (`2308dd3`). Those
three commits are the module's complete history, so any screenshot older than beta.53 shows the wrong
colours. The class javadocs still describe the original "rendered identically to the vanilla conduit"
state and are out of date.

## See also

* [End Oxygen](end_oxygen.md) — the module that turns Conduit Power into breathable air
* [Conduit Attack Range](conduit_attack_range.md) — the *vanilla* conduit's attack, not this one
* [Custom Crafting Recipes](custom_crafting_recipes.md) — why recipes are built in code here
* [Configuration Guide](../guides/configuration.md) — where the config file lives
* [All modules](../../README.md#-modules)
