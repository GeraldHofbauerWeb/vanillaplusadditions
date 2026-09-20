# Freecam Sub-Level Noclip

> **TL;DR** — The Freecam camera flies through an airship the way it flies through a mountain, instead of stopping dead at the hull while the terrain underneath stays passable.

<!-- vpa:meta:start -->
|  |  |
|---|---|
| **Module ID** | `freecam_sublevel_noclip` |
| **Side** | Client only |
| **Requires** | — |
| **Works with** | `freecam`, [Sable](https://modrinth.com/mod/sable) <sub>tested 2.0.5</sub> |
| **Download** | [`vpa_freecam_sublevel_noclip.jar`](https://github.com/GeraldHofbauerWeb/vanillaplusadditions/releases/latest/download/vpa_freecam_sublevel_noclip.jar) · also needs `vpa_core` |
| **Config section** | `[modules.freecam_sublevel_noclip]` |
| **Since** | `v1.0.0-beta.87` |
<!-- vpa:meta:end -->

## What it does

Freecam's camera passes through terrain but stops dead at an airship: you drift down through bedrock
quite happily, then bump into a hull as if it were a wall. This module gives the camera the same
freedom aboard a [Sable](https://modrinth.com/mod/sable) sub-level that it has everywhere else.

Nothing is configurable beyond the module switch, and nothing happens unless all three of these hold:
Freecam is installed, its camera is the active camera, and Freecam itself is set to ignore every
block.

## Why it exists

Freecam does not fly through blocks by turning off collision. It answers the collision question with
"there is nothing here", in `BlockStateBaseMixin`:

```java
if (context instanceof EntityCollisionContext ctx && ctx.getEntity() instanceof FreeCamera) {
    if (ModConfig.INSTANCE.collision.alwaysCheck && !Freecam.isEnabled()) {
        return;
    }
    if (CollisionBehavior.isIgnored(this.getBlock())) {
        cir.setReturnValue(Shapes.empty());
    }
}
```

That hook can only fire when somebody asks the three-argument
`getCollisionShape(BlockGetter, BlockPos, CollisionContext)` **and** passes a context that carries the
camera. Sable's sub-level collision does neither, except for one block:

```java
if (state.getBlock() instanceof ScaffoldingBlock) {
    VoxelShape originalShape = state.getCollisionShape(level, pos, new SubLevelEntityCollisionContext(entity));
    ...
} else {
    return state.getCollisionShape(level, pos);   // ← two arguments: the cached shape, no entity
}
```

The two-argument form returns `BlockStateBase.cache.collisionShape` and never asks who is colliding,
so Freecam's mixin is never consulted for the hull. The camera therefore obeys the ship and ignores
the world — exactly the asymmetry you see in game.

### Why it sets a flag instead of patching Sable

Sable takes over sub-level collision by redirecting the `Entity.collide(Vec3)` call inside
`Entity.move`. Vanilla only reaches that call in the second half of the method:

```java
public void move(MoverType type, Vec3 pos) {
    if (this.noPhysics) {
        this.setPos(this.getX() + pos.x, this.getY() + pos.y, this.getZ() + pos.z);
    } else {
        ...
        Vec3 vec3 = this.collide(pos);   // ← Sable's @Redirect sits here
```

So `noPhysics` cuts off the world collision and Sable's redirect in one step — and it does so without
naming a single Sable class.

**Where the flag is written matters, and the first attempt got it wrong.** Setting it from a
client-tick handler looks right and does nothing at all, because `Player.tick()` opens with

```java
this.noPhysics = this.isSpectator();
```

and Freecam's camera is a `LocalPlayer`, not a spectator — so the flag is wiped again before the
camera ever moves. It has to be set at HEAD of `Entity.move` itself, which is the last moment before
that `if` is read. The alternative, injecting into
`SubLevelEntityCollision.getSubLevelEntityCollisionShape` to restore the missing context, would be
more faithful (Freecam's own per-block rules would keep working), but it targets a private method
whose signature carries three Sable types (`Pose3dc`, `LevelAccelerator`, `LevelReusedVectors`), and
this repository compiles against Sable 1.2.2 while the pack runs 2.0.5. That signature happens to be
identical in both jars, so nothing is broken today — but a private method is free to move, and a
signature that has moved does not crash; it silently does nothing, which is the worst of the three
outcomes.

## Compatibility and known limits

| Limit | Effect |
|---|---|
| Freecam not installed | Inert. `ModList.get().isLoaded("freecam")` is false and no Freecam class is ever resolved. |
| Sable not installed | Inert in practice — without a sub-level the camera already passes through everything, and `noPhysics` changes nothing it did not already do. |
| Freecam set to ignore only *some* blocks | **Not fixed.** `noPhysics` is all or nothing and cannot reproduce a per-block rule, so the module declines to act at all: `ignoreAll` must be on, and the build variant must permit cheats — the same two conditions as the first branch of Freecam's `CollisionBehavior.isIgnored`. With a per-block rule the camera still sticks in a hull. |
| Freecam renames a field | The reflective lookup fails once, the module goes quiet for the session, and the game is unaffected. |
| Module disabled at runtime | Takes effect on the next freecam toggle at the latest: the flag lives on Freecam's throwaway camera entity, which is rebuilt every time freecam is switched on. |

## Under the hood

| File | Part |
|---|---|
| `modules/freecam_sublevel_noclip/FreecamSublevelNoclipModule.java` | the module, config gate |
| `mixin/freecam_sublevel_noclip/EntityMoveFreecamMixin.java` | the one flag, written at HEAD of `Entity.move` |
| `modules/freecam_sublevel_noclip/compat/FreecamAccess.java` | every question about Freecam, asked by reflection |

**One mixin, no compile dependency.** Freecam is not in `libs/` and does not appear in any signature.
`FreecamAccess` caches `ModList.get().isLoaded("freecam")`, recognises the camera by comparing
`entity.getClass().getName()` against `net.xolt.freecam.util.FreeCamera`, and reads
`ModConfig.INSTANCE.collision.ignoreAll` together with `BuildVariant.getInstance().cheatsPermitted()`
reflectively.

**Finding the camera costs nothing.** Freecam calls `MC.setCameraEntity(freeCamera)` when it switches
on, so `Minecraft.getInstance().getCameraEntity()` is the camera while freecam or a tripod is active.

**Cost.** `Entity.move` runs for every entity every tick, so the first test in the handler is a
reference comparison against `Minecraft.getInstance().getCameraEntity()`; every other entity leaves
immediately. The mixin is client-only and is never applied on a dedicated server.

**Testing.** This repository has no unit tests, and the dev run cannot load Sable: the jars are on
`compileOnly` and `localRuntime` in `build.gradle`, but `neo_version` is `21.0.167` while Sable
requires NeoForge `[21.1.219,)`, so it refuses to load there. This module therefore has to be checked
in a real 1.21.1 instance: switch freecam on aboard an airship and fly through the hull.

<!-- vpa:config:start -->
## Configuration

Section `[modules.freecam_sublevel_noclip]` in `config/vanillaplusadditions-common.toml` (or `config/vpa_freecam_sublevel_noclip-common.toml` if you run the standalone jar).

Every module also has the universal `enabled` and `debug_logging` keys — see the [Configuration Guide](../guides/configuration.md).

This module has no settings of its own.
<!-- vpa:config:end -->

## See also

* [Configuration Guide](../guides/configuration.md)
* [All modules](../../README.md#modules)
