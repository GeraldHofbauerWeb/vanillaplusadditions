# Mob Spawn Overlay

> **TL;DR** — Hold F3 and press M to light up every block around you where hostile mobs can spawn —
> red fields spawn them right now, yellow ones as soon as it gets dark, and a violet outline means a
> spider fits there too.

> **Status:** switching the overlay on used to crash the client, and the cause is worth knowing if
> you write another renderer in this repository — `SpawnOverlayRenderer` held all three vertex
> consumers at once and wrote to them in turn, which is exactly what a shared
> `MultiBufferSource.BufferSource` does not allow. It now draws one render type per pass and fetches
> each consumer at the start of its own pass. **Not yet confirmed in game**; everything below
> describes what the renderer is written to draw, read off the source.

<!-- vpa:meta:start -->
|  |  |
|---|---|
| **Module ID** | `mob_spawn_overlay` |
| **Side** | Client only |
| **Requires** | — |
| **Works with** | — |
| **Download** | [`vpa_mob_spawn_overlay.jar`](https://github.com/GeraldHofbauerWeb/vanillaplusadditions/releases/latest/download/vpa_mob_spawn_overlay.jar) · also needs `vpa_core` |
| **Config section** | `[modules.mob_spawn_overlay]` |
| **Since** | `v1.0.0-beta.64` |
<!-- vpa:meta:end -->

## What it does

Press **F3 + M** and every position around you where a hostile mob could stand lights up as a flat
striped field lying on the floor. Red fields spawn mobs right now, yellow ones once it
gets dark, and a violet outline marks a spot roomy enough for a spider. The same combo switches it off
again; an action-bar line — *Mob spawn overlay: ON* / *OFF* — confirms either way.

This is meant to be the spawn view OptiFine's `F7` used to give you and that Sodium/Iris do not bring
along. It is built for spawn-proofing: light a room, walk through it, and the fields that are still
there tell you where the torches are missing.

The check mirrors vanilla's own spawn code — ground placement, block and sky light, hitbox clearance
— and reads its light limits from the dimension instead of assuming a hardcoded 0–7, so the Nether
and the End come out right as well. Key, scan radii, colours and the animated stripes are
configurable.

Everything runs on your own client. No packet is sent, nothing is asked of the server, and the
overlay works on a server that has never heard of this mod.

## Why it exists

Vanilla 1.21.1 holds twenty-one debug renderers in `DebugRenderer`, and not one of them draws spawn
positions. The F3 dispatch has room for one, though. `KeyboardHandler.handleDebugKeys` is a plain
switch over the second key, each case doing its work and returning `true`:

```java
case 71:
    boolean flag1 = this.minecraft.debugRenderer.switchRenderChunkborder();
    this.debugFeedbackTranslated(flag1 ? "debug.chunk_boundaries.on" : "debug.chunk_boundaries.off");
    return true;
```

It answers to `1 2 3 A B C D G H I L N P Q S T` and `F4` — seventeen codes. **M**, GLFW code 77, is
free, and this module takes it.

The `true` matters as much as the key. Its caller feeds the result into the flag that decides what
releasing F3 does:

```java
flag5 |= flag && this.handleDebugKeys(key);
this.handledDebugKey |= flag5;
```

```java
if (flag3 && key == 292) {
    if (this.handledDebugKey) {
        this.handledDebugKey = false;
    } else {
        this.minecraft.getDebugOverlay().toggleOverlay();
    }
}
```

NeoForge's `InputEvent.Key` fires *after* that `|=` and cannot be cancelled, so a handler there would
toggle the overlay and then additionally open the debug screen when you let go of F3. Hooking
`handleDebugKeys` and returning `true` sets the flag, exactly as vanilla's own F3+G does. `flag5` is
then read a second time further down in `keyPress`: while no screen is open, a set `flag5` makes
vanilla call `KeyMapping.set(inputconstants$key, false)` instead of `set(…, true)` plus `click(…)`,
so whatever you have M bound to does not fire alongside it.

## In detail

### The check, per position

`SpawnScanner.scan` walks a box around the player and asks four questions, cheapest first:

```java
if (level.getBrightness(LightLayer.BLOCK, pos) > blockLightLimit) {
    continue;
}
int localBrightness = level.getMaxLocalRawBrightness(pos);
if (localBrightness > everDarkEnough) {
    continue;
}
if (!fits(level, pos, GROUND_MONSTER)) {
    continue;
}
```

| # | Question | Source |
|---|---|---|
| 1 | Is the block light already above what this dimension allows? | `DimensionType.monsterSpawnBlockLightLimit()` |
| 2 | Could this position *ever* be dark enough? | `monsterSpawnLightTest().getMaxValue()` |
| 3 | Is it a valid ground-spawn position? | `SpawnPlacementTypes.ON_GROUND.isSpawnPositionOk(level, pos, ZOMBIE)` |
| 4 | Does the mob's spawn hitbox fit without colliding? | `level.noCollision(type.getSpawnAABB(x + 0.5, y, z + 0.5))` |

Questions 3 and 4 together are `fits(…)`. Question 3 is vanilla's own: the block below must pass
`isValidSpawn`, and this block plus the one above must pass `NaturalSpawner.isValidEmptySpawnBlock` —
no full collision shape, not a signal source, no fluid, not in `#prevent_mob_spawning_inside`, and
not dangerous for the entity type. Question 4 is what keeps a wide mob out of a narrow gap.

`noCollision` is called with a `null` entity, which makes it collect entity collisions against
`EntitySelector.CAN_BE_COLLIDED_WITH`. In vanilla only `Boat` and `Shulker` override
`Entity.canBeCollidedWith()`, so a parked boat hides the marker under it while an ordinary mob or
another player does not. Vanilla's own spawn attempt tests the same set, so this is faithful rather
than a fault.

Everything that survives all four becomes a marker.

### Red or yellow

Vanilla's light test is two dice rolls, not a threshold:

```java
public static boolean isDarkEnoughToSpawn(ServerLevelAccessor level, BlockPos pos, RandomSource random) {
    if (level.getBrightness(LightLayer.SKY, pos) > random.nextInt(32)) {
        return false;
    } else {
        DimensionType dimensiontype = level.dimensionType();
        int i = dimensiontype.monsterSpawnBlockLightLimit();
        if (i < 15 && level.getBrightness(LightLayer.BLOCK, pos) > i) {
            return false;
        } else {
            int j = level.getLevel().isThundering() ? level.getMaxLocalRawBrightness(pos, 10) : level.getMaxLocalRawBrightness(pos);
            return j <= dimensiontype.monsterSpawnLightTest().sample(random);
        }
    }
}
```

The colours are the honest answer to "can both rolls still fail here?":

```java
boolean spawnsNow = localBrightness <= alwaysDarkEnough
        && level.getBrightness(LightLayer.SKY, pos) == 0;
```

* **Red** — sky light is exactly 0, so the first roll can never reject; and the local brightness is at
  or below `monsterSpawnLightTest().getMinValue()`, so the second roll cannot either. Mobs spawn here
  on every attempt.
* **Yellow** — the position passes everything deterministic, but at least one roll can still come up
  against it.

Sky light 1 is already yellow, although it only fails one attempt in 32. The overlay errs towards
"not guaranteed" on purpose.

### What the dimension decides

Both limits come from the `DimensionType`, never from a constant:

| Dimension type | `monster_spawn_block_light_limit` | `monster_spawn_light_level` | Sky light |
|---|---|---|---|
| `overworld`, `overworld_caves` | 0 | uniform 0–7 | yes |
| `the_nether` | 15 | constant 7 | none |
| `the_end` | 0 | uniform 0–7 | none |

Which has a consequence worth knowing before you go looking for it: **in vanilla's dimensions, yellow
only ever appears in the Overworld, and only on blocks that see the sky.**

* Overworld — the block-light limit is 0, so check 1 already throws out every position with any block
  light at all. What is left has block light 0, and `getMaxLocalRawBrightness` reduces to
  `max(0, skyLight − skyDarken)`. Sky light 0 makes that 0 and the marker red; anything above 0 makes it
  yellow.
* Nether — the block-light limit is 15, so check 1 never rejects, and min equals max equals 7, so
  everything surviving check 2 is at or below the minimum. There is no sky light. Every Nether marker
  is red.
* End — the block-light limit is 0 again and there is no sky light, so every surviving position sits
  at brightness 0. Every End marker is red.

`skyDarken` is the whole day/night cycle in one number — `Level.updateSkyBrightness()` ends in
`this.skyDarken = (int)((1.0 - d2 * d0 * d1) * 11.0)`, which is 0 at noon under a clear sky and 11 at
midnight. So the yellow fields on an open surface are not static — they appear as the sun goes down
and are gone again by midday, while an enclosed room's red fields never move.

### The spider outline

With `scan.mark_spider_spots` on, every position that already passed gets the same `fits(…)` test a
second time with `EntityType.SPIDER`. A zombie's spawn box is 0.6 × 1.95 and fits in one block; a
spider's is 1.4 × 0.9 and reaches 0.7 blocks past the block centre in every horizontal direction, so
the outline means "there is room around this block too", not just "there is room here".

`SpawnMarker`'s javadoc calls the outline "relevant below Y=0, where this pack turns every naturally
spawned spider into a cave spider". Nothing in this repository does that conversion, so treat the
outline as what the code actually computes — a spider-sized clearance test — and nothing more.

### Where the marker sits

The field is drawn on the floor the mob would stand on, not at the block boundary:

```java
VoxelShape shape = level.getBlockState(below).getCollisionShape(level, below);
double top = shape.isEmpty() ? 1.0D : shape.max(Direction.Axis.Y);
return (float) (below.getY() + Mth.clamp(top, 0.0D, 1.0D));
```

So a marker on a slab, on farmland or on a carpet lies flush on it instead of hovering at full block
height. It is then lifted by `LIFT` = 0.015 against z-fighting and inset by `INSET` = 0.03 per side,
which makes each field 0.94 × 0.94 blocks and leaves a visible seam between neighbours.

### What it deliberately does not check

The 24-block player distance and the mob cap are both momentary. Neither is evaluated: while you are
spawn-proofing you want the permanent state of a room, not whether a mob happens to be allowed this
second. The overlay also cannot say *which* mob would spawn — see the limits below.

### What it costs

The scan is a plain triple loop on the client thread, with the Y range clamped to the level's build
height, run every `scan.rescan_interval_ticks`:

| | Positions per scan | At the default 10-tick interval |
|---|---|---|
| Defaults (16 / 8) | 33 × 33 × 17 = 18,513 | twice a second |
| Maximum (48 / 32) | 97 × 97 × 65 = 611,585 | twice a second |

`scan.max_markers` (6000) caps the markers collected, not the positions visited. When the cap is hit
the loops break out with `y` outermost, so the scan box loses its top first and then its higher X —
the cut-off is a plane, not a fade.

Toggling the overlay on resets the tick counter and scans immediately, so the first markers appear
without waiting out the interval.

<!-- vpa:config:start -->
## Configuration

Section `[modules.mob_spawn_overlay]` in `config/vanillaplusadditions-common.toml` (or `config/vpa_mob_spawn_overlay-common.toml` if you run the standalone jar).

Every module also has the universal `enabled` and `debug_logging` keys — see the [Configuration Guide](../guides/configuration.md).

| Key | Type | Default | Range | Effect |
|---|---|---|---|---|
| `color_spawn_at_night.alpha` | double | `0.45` | 0.0 ~ 1.0 | Alpha component for positions that only spawn mobs in the dark. |
| `color_spawn_at_night.blue` | double | `0.2` | 0.0 ~ 1.0 | Blue component for positions that only spawn mobs in the dark. |
| `color_spawn_at_night.green` | double | `0.85` | 0.0 ~ 1.0 | Green component for positions that only spawn mobs in the dark. |
| `color_spawn_at_night.red` | double | `1.0` | 0.0 ~ 1.0 | Red component for positions that only spawn mobs in the dark. |
| `color_spawn_now.alpha` | double | `0.55` | 0.0 ~ 1.0 | Alpha component for positions where mobs spawn right now. |
| `color_spawn_now.blue` | double | `0.15` | 0.0 ~ 1.0 | Blue component for positions where mobs spawn right now. |
| `color_spawn_now.green` | double | `0.15` | 0.0 ~ 1.0 | Green component for positions where mobs spawn right now. |
| `color_spawn_now.red` | double | `1.0` | 0.0 ~ 1.0 | Red component for positions where mobs spawn right now. |
| `color_spider_outline.alpha` | double | `0.9` | 0.0 ~ 1.0 | Alpha component of the outline drawn around spider-sized spots. |
| `color_spider_outline.blue` | double | `1.0` | 0.0 ~ 1.0 | Blue component of the outline drawn around spider-sized spots. |
| `color_spider_outline.green` | double | `0.3` | 0.0 ~ 1.0 | Green component of the outline drawn around spider-sized spots. |
| `color_spider_outline.red` | double | `0.75` | 0.0 ~ 1.0 | Red component of the outline drawn around spider-sized spots. |
| `display.scroll_speed` | double | `0.35` | 0.0 ~ 5.0 | How fast the stripes travel (blocks per second). |
| `display.see_through_blocks` | boolean | `false` | — | Draw markers through terrain (x-ray) instead of hiding them behind blocks. |
| `display.shimmer_strength` | double | `0.35` | 0.0 ~ 1.0 | Strength of the additive enchantment-style shimmer; 0 skips the whole shimmer pass. |
| `display.stripe_scale` | double | `0.5` | 0.05 ~ 4.0 | Width of one diagonal stripe in blocks — smaller means denser stripes. |
| `scan.horizontal_radius` | int | `16` | 4 ~ 48 | How far around the player spawn positions are scanned (blocks). |
| `scan.mark_spider_spots` | boolean | `true` | — | Additionally outline positions with enough room (2x2) for a spider to spawn. |
| `scan.max_markers` | int | `6000` | 100 ~ 60000 | Safety cap on how many markers a single scan may collect. |
| `scan.rescan_interval_ticks` | int | `10` | 1 ~ 100 | Ticks between rescans (20 = one second); lower reacts faster but costs more. |
| `scan.vertical_radius` | int | `8` | 2 ~ 32 | How far above/below the player spawn positions are scanned (blocks). |
| `toggle_key` | int | `77` | 32 ~ 348 | GLFW key code pressed together with F3 to toggle the overlay (77 = M); change it if another mod claims the same F3 combo. |
<!-- vpa:config:end -->

## Compatibility and known limits

| Limit | Effect |
|---|---|
| One render type per pass, not per marker | `SpawnOverlayRenderer.render` used to fetch its three `VertexConsumer`s (stripes, the shimmer when `display.shimmer_strength > 0`, and the outline) before the marker loop and then write to them in turn. None of the render types is registered through `RegisterRenderBuffersEvent`, so they all land on the same `ByteBufferBuilder`, and `getBuffer` ends the previous shared batch before handing out the next one — `BufferBuilder.build()` leaves the earlier builder with `building = false`. The first write to the already-ended stripe consumer hit `ensureBuilding()` and threw `IllegalStateException: Not building!` inside `RenderLevelStageEvent`, which nothing catches — `ClientHooks.dispatchRenderStage` is a bare `NeoForge.EVENT_BUS.post`, so it unwound out of `LevelRenderer.renderLevel` into the `catch (Throwable)` in `Minecraft.run` and the client went to a crash report, as soon as the overlay was on with at least one marker. The renderer now walks the markers once per render type and takes each consumer at the start of its own pass, so only one batch is ever open. Not yet confirmed in game. |
| It cannot say *which* mob spawns | `Biome.NETWORK_CODEC` strips `MobSpawnSettings`, so the client never receives a biome's spawn lists. The answer is "is this a valid ground-spawn position for monsters", not "what spawns here" — a biome with no monsters in its list still lights up. |
| One stand-in hitbox | Every position is tested with `EntityType.ZOMBIE` as the 1-wide monster, plus `EntityType.SPIDER` for the outline. Mobs with other dimensions are only approximated. |
| Ground spawners only | Only `SpawnPlacementTypes.ON_GROUND` is evaluated. Drowned and other `IN_WATER` mobs, striders, phantoms and anything with `NO_RESTRICTIONS` are not represented at all. |
| Thunderstorms | While it thunders, vanilla substitutes a fixed darkening of 10 (`getMaxLocalRawBrightness(pos, 10)`); the scanner always uses the plain form. During a storm a sky-lit position can therefore spawn mobs that the overlay leaves unmarked. |
| Mob cap and the 24-block radius | Not checked, by design. A marked position need not be spawning anything at this moment. |
| Truncated scans are silent | `SpawnOverlayState.isTruncated()` is written by the scanner but read nowhere in the repository. Nothing tells you that `max_markers` cut the scan short. |
| Module disabled while the overlay is on | `onClientTick` checks `isActiveClientSide()`, `onRenderLevelStage` does not. Switch the overlay on and then disable the module (config key `enabled`, or the core module command): the last scan's markers keep rendering, no rescan happens, and the F3 combo is dead because the mixin bails out too. Re-enabling the module revives both — the timer rescans and F3 + M answers again, so switching the overlay off clears the list; short of that, only a restart. Read off the source, not reproduced. |
| The toggle is a session-wide static | `SpawnOverlayState.enabled` has no world-unload or disconnect hook, so it stays on across world changes. Switching it off does clear the marker list; leaving the world does not. |
| `toggle_key` set to a code vanilla uses | The mixin injects at HEAD, so our toggle wins and vanilla's own F3 shortcut for that key never runs. Codes below 32 cannot be configured at all (the range is 32–348). |
| Dedicated server | The module registers nothing server-side; `onInitialize` only logs. Its config is still a common config, so the keys exist in the server's file and do nothing. |
| Shader packs | The stripe animation goes through UVs rather than a texture matrix specifically to stay predictable under Iris/Sodium, per the render-type javadoc. This repository contains no test that covers it. |
| No tests | There are no unit tests in this repository, and none of the behaviour above is covered by an automated check. |

## Under the hood

| File | Role |
|---|---|
| `modules/mob_spawn_overlay/MobSpawnOverlayModule.java` | Module registration, plus the two static hooks the mixin calls |
| `modules/mob_spawn_overlay/config/MobSpawnOverlayConfig.java` | Every config value |
| `modules/mob_spawn_overlay/client/MobSpawnOverlayClientEvents.java` | Rescan timer, render hook, toggle |
| `modules/mob_spawn_overlay/client/SpawnScanner.java` | The four checks and the red/yellow decision |
| `modules/mob_spawn_overlay/client/SpawnMarker.java` | One scanned position (a record) |
| `modules/mob_spawn_overlay/client/SpawnOverlayState.java` | The toggle and the last scan result |
| `modules/mob_spawn_overlay/client/SpawnOverlayRenderer.java` | Quads, shimmer, outline |
| `modules/mob_spawn_overlay/client/SpawnOverlayRenderTypes.java` | Six render types |
| `mixin/mob_spawn_overlay/KeyboardHandlerDebugKeyMixin.java` | F3 + M |
| `standalone/mob_spawn_overlay/MobSpawnOverlayStandalone.java` | `@Mod("vpa_mob_spawn_overlay")` |

Those ten files are the module in full: no registries, no commands, no network payloads, no `data/`
files. There is no `KeyMapping` and no `RegisterKeyMappingsEvent` anywhere in it, so the combo does
**not** appear in the vanilla Controls screen — it is rebound through the `toggle_key` config value
and nowhere else.

**The mixin** is client-only. `KeyboardHandler` does not exist on a dedicated server, so it is listed
in the `"client"` block of `vanillaplusadditions.mixins.json` and as `clientMixins` in the standalone
jar definition in `build.gradle`; a dedicated server skips it instead of failing on a missing class.
It is eight lines of body:

```java
@Inject(method = "handleDebugKeys", at = @At("HEAD"), cancellable = true)
private void vpaToggleSpawnOverlay(int key, CallbackInfoReturnable<Boolean> cir) {
    if (!MobSpawnOverlayModule.isActiveClientSide() || key != MobSpawnOverlayModule.getToggleKey()) {
        return;
    }
    MobSpawnOverlayClientEvents.toggleFromDebugKey();
    cir.setReturnValue(true);
}
```

**Two events**, both on the game bus, both from an `@EventBusSubscriber(value = Dist.CLIENT)` class:
`ClientTickEvent.Post` counts down the rescan interval, and `RenderLevelStageEvent` draws, filtered to
`Stage.AFTER_TRANSLUCENT_BLOCKS`. The pose stack is translated by `-cameraPos` once and every marker
is then emitted in absolute world coordinates.

**Three render types**, each in a depth-tested and an x-ray variant — six constants in all:
`POSITION_TEX_COLOR` quads with translucent blending for the stripes, the same with additive blending
for the shimmer, and `POSITION_COLOR_NORMAL` lines for the spider outline. All of them use
`VIEW_OFFSET_Z_LAYERING`, the item-entity output target, a colour-only write mask (so markers never
occlude each other) and `NO_CULL` (so a field is visible from below as well). They are not part of
vanilla's sorted batch, which is why the render handler flushes them itself:

```java
for (var type : SpawnOverlayRenderTypes.all(config.isSeeThroughBlocks())) {
    buffers.endBatch(type);
}
```

**The stripes** come from one 194-byte texture,
`assets/vanillaplusadditions/textures/misc/spawn_overlay_stripes.png`, a seamless 45° pattern carried
in the alpha channel. Its UVs are derived from world coordinates rather than per block, so the
diagonal runs continuously across neighbouring markers, and each quad's UV origin is wrapped into
`[0,1)` — the texture repeats with period 1, so wrapping is free and keeps float precision usable far
from the world origin. The shimmer pass reuses the marker's own colour at alpha
`display.shimmer_strength`, with bands `1 / 0.35` ≈ 2.9× wider than the base stripes travelling 3.5×
faster; keeping the hue is deliberate, because additive blending multiplies rgb by alpha and a white
glint would wash the field out. `display.shimmer_strength = 0` skips the pass entirely.

The animation clock is `level.getGameTime() % 24000L + partialTick`, so the stripes stop while the
game is paused and the phase wraps once per Minecraft day.

**Gating.** `MobSpawnOverlayModule.isActiveClientSide()` resolves through
`AbstractModule.isModuleEnabled()`, which asks `ModuleManager.resolveModuleEnabled` on every call —
so disabling the module takes effect live for the key and the rescan timer. It is not consulted by the
render handler; see the limits above.

**Standalone jar.** `vpa_mob_spawn_overlay` carries no data of its own. The stripe texture and the two
lang keys (`message.vanillaplusadditions.mob_spawn_overlay.on` / `.off`, translated in `de_de`,
`de_at`, `cs_cz`, `fr_fr` and `es_es`) live in `vpa_core`, which every module jar requires anyway.

**History.** Exactly one commit ever touched this module, its mixin and its standalone entry point —
`cbb4dc8`, 2026-08-05 — and nothing has changed since. That commit also introduced the build's
`clientMixins` support, which the `KeyboardHandler` hook needed.

## See also

* [Configuration Guide](../guides/configuration.md) — where the config file lives
* [Module System](../guides/module-system.md) — enabling and disabling modules
* [All modules](../../README.md#-modules)
