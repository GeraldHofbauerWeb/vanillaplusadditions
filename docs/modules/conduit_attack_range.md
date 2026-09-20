# Conduit Attack Range

> **TL;DR** — Your conduit shoots hostile mobs as soon as it is active (16 frame blocks) instead of
> only at the full 42-block frame, and it reaches out to half its Conduit Power radius instead of a
> flat 8 blocks.

<!-- vpa:meta:start -->
|  |  |
|---|---|
| **Module ID** | `conduit_attack_range` |
| **Side** | Client + Server |
| **Requires** | — |
| **Works with** | — |
| **Download** | [`vpa_conduit_attack_range.jar`](https://github.com/GeraldHofbauerWeb/vanillaplusadditions/releases/latest/download/vpa_conduit_attack_range.jar) · also needs `vpa_core` |
| **Config section** | `[modules.conduit_attack_range]` |
| **Since** | `v1.0.0-beta.43` |
<!-- vpa:meta:end -->

## What it does

A conduit is sold as two things at once: a source of Conduit Power, and a turret that shoots
drowned. The second half almost never happens. Vanilla only lets a conduit attack once its frame is
**complete at 42 blocks**, and even then it reaches **8 blocks** — the water immediately around the
conduit. Anything smaller than a full frame is a lamp.

This module gives the attack the same scale the beneficial effect already has. A conduit attacks as
soon as it is **active** — 16 frame blocks, the smallest frame the game accepts — and it reaches
**half its Conduit Power radius**: 16 blocks at 16 frames, rising to 48 blocks at a full frame.

Damage and rate stay exactly vanilla: **4 magic damage every 2 seconds, to one mob at a time**. That
is a nuisance to a drowned, not a death ray. What changes is that the nuisance now exists at all,
and that it covers the water you actually swim in rather than the block you are standing on.

The attack animation works at the new range too — the stream of nautilus particles between conduit
and victim, which vanilla draws only for targets within 8 blocks.

## Why it exists

Both limits sit as literals in `ConduitBlockEntity.updateDestroyTarget`:

```java
int i = positions.size();
if (i < 42) {                                   // MIN_KILL_SIZE — a complete frame
    blockEntity.destroyTarget = null;
} else if (blockEntity.destroyTarget == null && blockEntity.destroyTargetUUID != null) {
    blockEntity.destroyTarget = findDestroyTarget(level, pos, blockEntity.destroyTargetUUID);
    blockEntity.destroyTargetUUID = null;
} else if (blockEntity.destroyTarget == null) {
    List<LivingEntity> list = level.getEntitiesOfClass(
            LivingEntity.class, getDestroyRangeAABB(pos),
            e -> e instanceof Enemy && e.isInWaterOrRain());
    if (!list.isEmpty()) {
        blockEntity.destroyTarget = list.get(level.random.nextInt(list.size()));
    }
} else if (!blockEntity.destroyTarget.isAlive()
        || !pos.closerThan(blockEntity.destroyTarget.blockPosition(), 8.0)) {
    blockEntity.destroyTarget = null;
}
```

and the search box is a constant of its own:

```java
private static AABB getDestroyRangeAABB(BlockPos pos) {
    int x = pos.getX(), y = pos.getY(), z = pos.getZ();
    return new AABB(x, y, z, x + 1, y + 1, z + 1).inflate(8.0);
}
```

The fixed `8.0` is the odd one out, because the *beneficial* half of the same block entity already
scales with the frame. `applyEffects` computes its radius from the frame size:

```java
int i = positions.size();
int j = i / 7 * 16;          // 32 blocks at 16 frames, 96 at a full 42
```

So a full conduit hands out Conduit Power for 96 blocks and shoots for 8. The two numbers describe
different buildings. Halving the effect radius gives a hostile range that grows with the frame the
player actually built, which is what the frame is for.

## In detail

### Who gets shot

Untouched — the module changes *where* and *when*, never *what*. Vanilla's own predicate still
decides: the target must be a `LivingEntity` that is an `Enemy` **and** `isInWaterOrRain()`. A
skeleton under a dry overhang two blocks from the conduit is safe; the same skeleton in the rain is
not. There is exactly one target at a time, drawn at random from everything in range, and it keeps
that target until the mob dies or leaves.

### How far

The hostile radius is the Conduit Power radius divided by `radius_divisor`, floored at 1 block. Both
sides of that use vanilla's integer division, so the radius rises in steps of seven frame blocks:

| Frame blocks | Conduit Power radius | Hostile radius, `radius_divisor = 2` (default) | `radius_divisor = 1` |
|---|---|---|---|
| 16–20 | 32 | **16** | 32 |
| 21–27 | 48 | **24** | 48 |
| 28–34 | 64 | **32** | 64 |
| 35–41 | 80 | **40** | 80 |
| 42 | 96 | **48** | 96 |
| *vanilla, any size* | *as above* | *8, and only at 42* | — |

`radius_divisor = 1` means the conduit shoots everything it protects. The module clamps the divisor
with `Math.max(1, …)` on top of the config's own 1–16 range, so a hand-edited `0` cannot divide by
zero.

**Acquisition is a cube, retention is a sphere.** That mismatch is vanilla's, inherited rather than
introduced: targets are collected from `new AABB(…).inflate(radius)`, a cube of edge `2·radius + 1`,
while the "is it still in range" check two seconds later is `closerThan(…, radius)`, which is
`distSqr < radius²`. A mob sitting in a corner of the cube — up to `radius·√3`, so about 83 blocks
at the default full-frame 48 — is therefore picked up, hit once, and dropped again on the next pass.
It is re-acquired from the cube on the pass after that, so a corner mob takes its 4 damage every
4 seconds instead of every 2. At vanilla's radius of 8 the same thing happens; it is only visible
once the numbers get large.

### When it starts

`min_frames` replaces the hardcoded 42. The configurable range is 1–96, but only **16–42** does
anything:

* **Below 16** changes nothing. `serverTick` only calls `updateDestroyTarget` when `updateShape`
  returned true, and that returns `positions.size() >= 16`. An inactive conduit never reaches the
  attack code at all.
* **Above 42** switches attacking off entirely, because a vanilla conduit frame cannot exceed 42
  blocks. It is a usable way to turn the attack off while keeping the module on, but nothing warns
  you that this is what you did.

The whole attack cycle runs on vanilla's clock: `serverTick` does its work when
`level.getGameTime() % 40 == 0`, so one target decision and one hit per 40 game ticks.

### The particle stream

The visible attack is a spray of `ParticleTypes.NAUTILUS` spawned in `animationTick` around the
target's eye position and drifting back toward the conduit. It is drawn only when the **client** has
resolved `destroyTarget` — and the client only ever receives the target's **UUID** in the block
entity update packet. It resolves that UUID through `findDestroyTarget`, which looks inside
`getDestroyRangeAABB(pos)`: the same fixed 8-block box.

So without a fourth edit a mob damaged at 30 blocks would take damage in silence, with no particles
anywhere. The mixin widens that one lookup to `maxHostileRadius()` — the hostile radius at 42
frames, 48 blocks by default. Because `hostileRadius` only grows with the frame count, that bound
covers every conduit in the world, whatever size it is.

The widened box is not a per-tick cost. `updateClientTarget` calls `findDestroyTarget` only when the
UUID it has is not the one already resolved, and clears the UUID when the lookup comes back empty —
at most one entity query per target change. On the server the same call happens once after a
chunk reload, to restore the target saved in NBT.

### The eye stays shut

Vanilla decides the angry open-eye texture somewhere else entirely:

```java
private static void updateHunting(ConduitBlockEntity blockEntity, List<BlockPos> positions) {
    blockEntity.setHunting(positions.size() >= 42);
}
```

`ConduitRenderer` picks `OPEN_EYE_TEXTURE` from that flag, and the mixin's `@ModifyConstant` is
scoped to `updateDestroyTarget` only. With the default `min_frames = 16` a half-sized conduit
therefore shoots mobs while looking perfectly calm. Cosmetic, but it is the one thing a player sees
that disagrees with what the block is doing.

<!-- vpa:config:start -->
## Configuration

Section `[modules.conduit_attack_range]` in `config/vanillaplusadditions-common.toml` (or `config/vpa_conduit_attack_range-common.toml` if you run the standalone jar).

Every module also has the universal `enabled` and `debug_logging` keys — see the [Configuration Guide](../guides/configuration.md).

| Key | Type | Default | Range | Effect |
|---|---|---|---|---|
| `min_frames` | int | `16` | 1 ~ 96 (effective 16 ~ 42) | Minimum frame-block count at which a conduit starts attacking hostiles, replacing vanilla's hardcoded 42. Values below 16 do nothing (vanilla only runs the attack tick on an ACTIVE conduit, which needs 16 frames) and values above 42 switch attacking off entirely (a vanilla frame caps at 42). Note the conduit's angry open-eye texture still needs 42 frames - the mixin does not touch updateHunting. |
| `radius_divisor` | int | `2` | 1 ~ 16 | Hostile-damage radius = Conduit Power radius (frames / 7 * 16) / this divisor, floored at 1 block. 2 (default) = half the effect radius: 16 blocks at 16 frames, 48 at 42. 1 = full effect radius (32 / 96). The module additionally clamps the value with Math.max(1, ...). |
<!-- vpa:config:end -->

## Compatibility and known limits

| Limit | Effect |
|---|---|
| **The standalone jar ships no behaviour** | `vpa_conduit_attack_range.jar` is built and published, but there is no `standalone/conduit_attack_range` package and therefore no `@Mod` entry point in it. Nothing ever constructs `ConduitAttackRangeModule`, so its static `instance` stays null, `isActive()` returns false and all four injections hand back the vanilla values. Use the bundle. |
| The angry eye still needs 42 frames | See above — `updateHunting` is untouched, so a sub-42 conduit attacks with a closed eye. |
| Corner targets are hit half as often | Cube acquisition versus spherical retention, inherited from vanilla. See *How far*. |
| `min_frames` outside 16–42 | Silently does nothing, or silently disables the attack. Nothing validates the value. |
| The **End Conduit** is not affected | `EndConduitBlockEntity extends BlockEntity`, not `ConduitBlockEntity`, and carries no attack code of its own. This module's mixin never sees it. |
| Another mod that rewrites `updateDestroyTarget` | Three of the four injections are `@Redirect`s on a single call instruction each. A second mod redirecting the same instruction conflicts with them, and one of the two will fail to apply. |
| Vanilla changes to `getDestroyRangeAABB` | With the module switched off, the two box redirects rebuild vanilla's box themselves (`new AABB(x, y, z, x+1, y+1, z+1).inflate(8)`) instead of calling the original. That is byte-for-byte what 1.21.1 does, but a change on Mojang's side would be silently reimplemented wrong. |
| Broad injection points | `@ModifyConstant(intValue = 42)` catches *every* literal 42 in `updateDestroyTarget`, and the `closerThan` redirect every such call in it. In 1.21.1 there is exactly one of each, so the edits are precise today; a future second occurrence would be captured unnoticed. |

## Under the hood

Three files, no registries, no events, no commands, no keybinds, no assets. `onInitialize()` writes a
single log line; every behaviour lives in the mixin.

| Class | Role |
|---|---|
| `modules/conduit_attack_range/ConduitAttackRangeModule` | The enabled gate and the radius arithmetic, all static, all called from the mixin |
| `modules/conduit_attack_range/config/ConduitAttackRangeConfig` | `radius_divisor`, `min_frames` |
| `mixin/conduit_attack_range/ConduitBlockEntityMixin` | The four injections |

**The injections**, all into `net.minecraft.world.level.block.entity.ConduitBlockEntity`:

| # | Method | Kind | Vanilla value | Replaced with |
|---|---|---|---|---|
| 1 | `updateDestroyTarget` | `@ModifyConstant` `intValue = 42` | the `MIN_KILL_SIZE` gate | `minFrames()` |
| 2 | `updateDestroyTarget` | `@Redirect` on `getDestroyRangeAABB` | `inflate(8)` | `inflate(hostileRadius(positions.size()))` |
| 3 | `updateDestroyTarget` | `@Redirect` on `BlockPos.closerThan(Vec3i, D)` | the `8.0` retain check | the same scaled radius, so a far-acquired target is not dropped on the next pass |
| 4 | `findDestroyTarget` | `@Redirect` on `getDestroyRangeAABB` | `inflate(8)` | `inflate(maxHostileRadius())` |

Injections 2 and 3 need the frame count, which is not in scope in a plain redirect — they take it
from `updateDestroyTarget`'s own `List<BlockPos> positions` parameter, which `@Redirect` passes
through after the redirected call's arguments. Injection 4 has no frame count available at all,
which is why it uses the upper bound instead.

**Sides.** The mixin is listed in the shared `mixins` block of `vanillaplusadditions.mixins.json`
(line 16), not in `client`. Injections 1–3 are reached from `serverTick` only; injection 4 is
reached from `clientTick` → `updateClientTarget` on the client and from `updateDestroyTarget` on the
server. There is no rendering code, no `Dist.CLIENT`, no `Minecraft.getInstance()`.

**Gating.** The mixin is applied unconditionally and gates itself at runtime: every injection asks
`ConduitAttackRangeModule.isActive()` first and returns the vanilla value when the answer is no.
`isActive()` is `instance != null && isModuleEnabled()`, and `isModuleEnabled()` goes through
`ModuleManager.resolveModuleEnabled`, which consults the runtime override map before the config
value. Both config values are re-read on every call. `enabled`, `radius_divisor` and `min_frames`
therefore take effect immediately — editing the toml or running
`/vpa module disable conduit_attack_range` needs no reload and no restart.

One framework quirk to expect in the log: `ModuleManager.initializeModules` runs before the config
spec is registered, so the *"Conduit Attack Range module initialized"* line is printed even when the
module is switched off in the toml. The real gate is the per-call check, not that line.

## See also

* [End Conduit](end_conduit.md) — a separate block entity, deliberately without an attack
* [End Oxygen](end_oxygen.md) — the other half of what an End conduit is for
* [Module System](../guides/module-system.md) — how a module is registered, gated and configured
* [Configuration Guide](../guides/configuration.md) — where the config file lives
* [All modules](../../README.md#-modules)
