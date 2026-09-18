# Compass Overhaul

## Overview

Four repairs around the compass, each with its own config switch:

| Part | Config key | What it does |
|---|---|---|
| A | `guard_lodestone_binding` | A lodestone compass only loses its binding when the lodestone is provably gone |
| B | `fix_sublevel_needle` | The ordinary needle stops jittering aboard a Create Aeronautics airship |
| C | `world_compass_enabled` | Adds the **World Compass**, which always points north |
| D | `fix_quark_compass` | Repairs the needle where Quark's *Compasses Work Everywhere* takes it over |

---

## Part A — the disappearing lodestone binding

The symptom: you warp somewhere with a Waystone, and the compass that was bound to your base is
suddenly useless. It still glints, it is still called *Lodestone Compass*, and the needle spins at
random. The coordinates are gone from the item and cannot be recovered.

Waystones is not at fault. Neither `waystones`, `waystonessable` nor `sable` contains a single
reference to lodestones, `PoiManager`, `GlobalPos` or `LODESTONE_TRACKER`; the teleport is an
ordinary `ServerPlayer.teleportTo`, with no entity re-creation and no item re-serialisation.

The cause is vanilla `LodestoneTracker.tick(ServerLevel)`, called every tick for every slot from
`CompassItem.inventoryTick`:

```java
return level.isInWorldBounds(pos) && level.getPoiManager().existsAtPosition(PoiTypes.LODESTONE, pos)
     ? this : new LodestoneTracker(Optional.empty(), true);   // ← clears the binding
```

There is **no check that the chunk is even loaded**. `PoiManager.exists` turns "no data" into
`false` through `orElse(false)`, and `SectionStorage.readColumn` caches an empty result for a POI
column that is missing, unreadable or fails to parse — permanently, because 1.21.1 never evicts it.
Any of those wipes the coordinates.

Two details make the failure look like something else entirely:

* A target in **another dimension** is not checked at all (`tick` returns early). So breaking the
  lodestone while you are in the Nether does nothing — the binding dies the moment you set foot in
  the Overworld again, which is usually right after a warp.
* `isFoil` and `getDescriptionId` key on the *presence* of the component, not on the target, so the
  item keeps its glint and its name. Nothing tells you it happened.

**The fix** (`mixin/compass_overhaul/LodestoneTrackerMixin`) answers the same question from the
block instead of the POI index:

| Situation | Result |
|---|---|
| Target in another dimension | Untouched — same as vanilla |
| Chunk not loaded | Binding kept (no evidence either way) |
| Chunk loaded, lodestone still there | Binding kept |
| Chunk loaded, block is something else | Handed back to vanilla, which clears it |

Breaking a lodestone still unbinds the compass, immediately, as long as you are there to see it.

---

## Part B — the needle aboard an airship

A Sable sub-level is a plot of chunks parked far away in the parent level and drawn at the ship's
real position through a pose. An entity standing on a ship therefore has plot-local coordinates and
a plot-local yaw, and a needle computed from those would point at "ship north".

Sable already handles this: it overwrites `CompassItemPropertyFunction.getAngleFromEntityToPos` and
rotates the target into the ship's space. It reads `lastPose()` though — the pose from the
**previous tick**, not interpolated — so the needle jitters and lags whenever the ship turns. Our
mixin injects at HEAD of the same method and returns its own bearing, computed from
`renderPose(partialTick)`.

> **Only a viewer standing inside the plot counts**, and getting that wrong cost two rounds of
> testing. Rotating the target into the ship's space is correct only while the yaw that vanilla folds
> in afterwards lives in that same space — which holds exactly when the viewer is *in* the plot.
> Someone standing on a hull drawn out in the world keeps world coordinates and a world yaw, and
> mixing a ship-space bearing into a world-space yaw puts the needle off by the ship's entire
> rotation: half a turn on a ship that faces backwards. An earlier version asked
> `getTrackingOrVehicleSubLevel` as well, meaning to help a player riding a seat — that player is
> outside the plot, so it did precisely the wrong thing, and from part D onwards it broke the
> ordinary lodestone compass too.

> **Two mods writing to one method is fragile.** Sable uses `@Overwrite` there, and the order in
> which two mods' mixins are applied is not guaranteed. Ours therefore declares `priority = 1500`
> (so it is applied *after* Sable's overwrite rather than being replaced by it) and `require = 0`
> (a missed binding logs instead of crashing). If the two ever fall out properly, set
> `fix_sublevel_needle = false` and the game is back to Sable's behaviour.

---

## Part C — the World Compass

A second compass that points at **world north** — not at spawn, not at a lodestone. It works in the
Nether and the End, where an ordinary compass just spins, and it keeps pointing north while an
airship turns underneath it.

```
 . A .        A = Amethyst Shard
 E C E        E = Eye of Ender
 . E .        C = Compass
```

The recipe is registered in code (`AddReloadListenerEvent`), like every recipe in this mod.

### The needle

The needle is not computed here. Vanilla's own `CompassItemPropertyFunction` gets handed a target
four million blocks due north of the viewer:

```java
new CompassItemPropertyFunction((level, stack, entity) -> GlobalPos.of(
        level.dimension(),
        BlockPos.containing(entity.getX(), entity.getY(), entity.getZ() - 4_000_000)));
```

That distance is north from anywhere in the world to well within a tenth of a degree — a fraction of
one of the 32 frames — and stays comfortably inside the ±30 million world border.

Deriving the angle by hand looked tempting (for a target infinitely far north vanilla's
`0.5 - (yaw - 0.25 - bearing)` collapses to `0.5 - yaw`) and it was wrong in play: the needle came
out half a turn off. Handing vanilla a real target removes every sign convention from our side at
once, and it buys three things for free:

* the same wobble for the holding player and the same instant reading in an item frame, so the
  World Compass cannot disagree with the compass next to it in the hotbar;
* the Nether and the End, where an ordinary compass spins — vanilla only rejects a target from a
  *different* dimension, and ours always carries the viewer's own;
* part B, because the target now travels the ordinary vanilla path: aboard a turning airship the
  sub-level correction applies here as well.

### Textures

The 32 frames are the **vanilla compass frames, recoloured** — generated by
`scripts/gen_world_compass_textures.py` from a client jar and committed to the repo, so the build
needs no client jar. Every attempt at drawing them from scratch foundered on the lit metal casing
that carries the whole icon, so not a pixel of it is touched.

Only the dial is recoloured, bounded by a mask obtained from a flood fill at the centre: 46 pixels.
The counter-check is unambiguous — outside that mask **not one pixel differs across all 32 frames**,
so the casing is provably identical everywhere. Five colours are replaced:

| Source (inside the mask only) | Role | Target |
|---|---|---|
| `#ff1414`, `#cb1a1a`, `#be1515` | north needle | Amethyst `#a855f7` at the same brightness steps |
| `#2f2f2f` | dial face | Ender pearl `#105e51` |
| `#4f4d4d` | shading at the needle's foot | Ender eye green `#71ac49` |
| `#646464` | pivot | dial colour, lifted 30% toward white |
| `#353535`, **only the 10 pixels along the dial's rim** | inner edge | deeper green `#0b4d42` |
| everything else | casing | **unchanged** |

Two masks rather than one colour table, because two greys do double duty: `#4f4d4d` is the needle's
shadow inside and a dark spot on the casing outside, and `#353535` is both the inner edge and the
upper arc of the casing. A plain colour mapping would repaint the casing as well.

The model file reuses vanilla's override table verbatim (thresholds 0, 0.015625, then steps of 1/32,
in the order 16 → 17…31 → 00…15 → 16), so the frames line up exactly as they do on a normal compass.

Note that the PNGs are therefore derived Mojang assets.

---

## Part D — the needle Quark takes over

This one matters more than it looks, because it silently voids part B.

Quark's *Compasses Work Everywhere* replaces the needle wholesale during client setup:

```java
ItemProperties.register(Items.COMPASS, ResourceLocation.withDefaultNamespace("angle"),
                        new CompassAnglePropertyFunction());
```

The per-item property map is keyed by item, so from that moment on an ordinary compass never touches
`CompassItemPropertyFunction` again — **not vanilla's, not Sable's overwrite, and not part B**. Three
things are wrong in the replacement:

```java
private double getAngleToPosition(Entity entity, BlockPos blockpos) {
    Vec3 pos = entity.position();
    return Math.atan2(blockpos.getZ() - pos.z, blockpos.getX() - pos.x);   // ← raw block position
}
```

* **It aims at the block's corner.** Vanilla uses `Vec3.atCenterOf(pos)`; the raw position is the
  corner with the smallest X and Z — the **north-west** one. Half a block off in both axes, obvious
  from close up.
* **It knows nothing about sub-levels.** Aboard an airship the needle points at "ship north".
* **Its frame rotation drops the eight rotation steps** (`180 + direction.toYRot()`, without
  `getRotation() * 45`), so a turned item frame shows a needle turned by the same amount. It also
  uses `getYRot()` rather than `getVisualRotationYInDegrees()`.

`mixin/compass_overhaul/QuarkCompassAngleMixin` corrects all three at HEAD of the two private
methods: the block centre (or, aboard a ship, the bearing from `SableOrientation`, converted to
radians — Quark's method works in radians where vanilla's works in turns) and vanilla's
`ItemFrame.getVisualRotationYInDegrees()`.

Quark is not a compile dependency here, hence the string target; without Quark, Mixin disables the
mixin and nothing changes.

---

## Files

```
modules/compass_overhaul/CompassOverhaulModule.java          item, recipe, config gates
modules/compass_overhaul/client/WorldCompassAngle.java       the needle
modules/compass_overhaul/client/CompassOverhaulClientSetup.java
modules/compass_overhaul/compat/SableGate.java               "is Sable there?" — no Sable types
modules/compass_overhaul/compat/SableOrientation.java        every Sable reference lives here
mixin/compass_overhaul/LodestoneTrackerMixin.java            part A (server)
mixin/compass_overhaul/CompassAngleSubLevelMixin.java        part B (client)
mixin/compass_overhaul/QuarkCompassAngleMixin.java           part D (client)
scripts/gen_world_compass_textures.py                        the 32 frames and 32 models
```

Sable is optional. `SableOrientation` is only ever reached from inside an `if` guarded by
`SableGate.isLoaded()`, so a pack without Sable never resolves those classes.
