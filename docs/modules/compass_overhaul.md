# Compass Overhaul

> **TL;DR** — A lodestone compass stops forgetting where it was bound, the needle stays steady
> aboard a Create Aeronautics airship, and a craftable World Compass always points north — in the
> Nether and the End as well.

<!-- vpa:meta:start -->
|  |  |
|---|---|
| **Module ID** | `compass_overhaul` |
| **Side** | Client + Server |
| **Requires** | — |
| **Works with** | [Sable](https://modrinth.com/mod/sable) <sub>tested 2.0.5</sub>, [Quark](https://modrinth.com/mod/quark) <sub>tested 4.1-482</sub> |
| **Download** | [`vpa_compass_overhaul.jar`](https://github.com/GeraldHofbauerWeb/vanillaplusadditions/releases/latest/download/vpa_compass_overhaul.jar) · also needs `vpa_core` |
| **Config section** | `[modules.compass_overhaul]` |
| **Since** | `v1.0.0-beta.86` |
<!-- vpa:meta:end -->

## What it does

Four repairs around the compass, each with its own switch:

| Part | Config key | What it does | Runs on |
|---|---|---|---|
| A | `guard_lodestone_binding` | A lodestone compass only loses its binding when the lodestone is provably gone | server |
| B | `fix_sublevel_needle` | The needle stops jittering aboard a Create Aeronautics airship | client |
| C | `world_compass_enabled` | Adds the **World Compass**, which always points north | both |
| D | `fix_quark_compass` | Repairs the needle where Quark's *Compasses Work Everywhere* takes it over | client |

The symptom for part A is the one people notice: you warp somewhere with a Waystone, and the compass
that was bound to your base is suddenly useless. It still glints, it is still called *Lodestone
Compass*, and the needle spins at random. The coordinates are gone from the item and cannot be
recovered. (Waystones is not at fault — see below.)

Part C is the only thing this module adds rather than repairs:

<img src="../img/items/world_compass.png" width="64" alt="World Compass">

The **World Compass** points at world north instead of at a target. It works in the Nether and the
End, where an ordinary compass just spins, and it keeps pointing north while an airship turns
underneath it — which makes it a fixed direction sign in a (glass) item frame.

## Why it exists

### Vanilla throws the lodestone binding away

`LodestoneTracker.tick(ServerLevel)` runs every tick for every slot, driven by
`CompassItem.inventoryTick`:

```java
return level.isInWorldBounds(blockpos) && level.getPoiManager().existsAtPosition(PoiTypes.LODESTONE, blockpos)
    ? this
    : new LodestoneTracker(Optional.empty(), true);   // ← clears the binding
```

The question is never put to the world, only to the POI index — and the index turns every kind of
"no answer" into "no lodestone":

```java
public boolean exists(BlockPos pos, Predicate<Holder<PoiType>> typePredicate) {
    return this.getOrLoad(SectionPos.asLong(pos)).map(s -> s.exists(pos, typePredicate)).orElse(false);
}
```

There is no check that the chunk is even loaded. `SectionStorage.readColumn` stores `Optional.empty()`
for every section of a POI column that is missing, unreadable or fails to parse — `tryRead` swallows
an `IOException` into exactly that. `CompassItem.inventoryTick` then writes the empty tracker straight
back into the stack, and one such answer is all it takes: the binding is gone on the very next tick.

The cached empty is not necessarily permanent, but that does not help. Vanilla 1.21.1 never evicts
those entries at all; NeoForge does add an eviction — `CommonHooks.onChunkUnload` calls its patched
`SectionStorage.remove` for every section of an unloading chunk — and when a chunk does load,
`PoiManager.checkConsistencyWithBlocks` rebuilds the section from the real blocks. Both of those
need the chunk to be visited, so a column nobody goes near keeps its empty entry; and neither of
them gives back a binding that has already been erased from the stack.

Two details make the failure look like something else entirely:

* A target in **another dimension** is not checked at all (`tick` returns early). Breaking the
  lodestone while you are in the Nether therefore does nothing — the binding dies the moment you set
  foot in the Overworld again, which is usually right after a warp.
* `isFoil` and `getDescriptionId` key on the *presence* of the component, not on the target
  (`stack.has(DataComponents.LODESTONE_TRACKER) ? "item.minecraft.lodestone_compass" : …`), so the
  item keeps its glint and its name. Nothing tells you it happened.

The Waystones jars carry no reference to lodestones, `LodestoneTracker`, `GlobalPos` or the POI
manager at all — not one class file in `waystones` or `waystonessable`, and none in `sable` either.
The teleport is an ordinary one; the compass dies of its own accord, the warp only moves you into
range of the tick that kills it.

### Sable reads last tick's pose

A Sable sub-level is a plot of chunks parked far away in the parent level and drawn at the ship's
real position through a pose. Sable keeps most entities out of that plot — a player walking the deck
lives at the projected world position and merely *sticks* to the sub-level. Only what is in the
entity type tag `sable:retain_in_sub_level` — item frames, armour stands, minecarts, `create:seat` —
really sits inside it, with plot-local coordinates and a plot-local yaw, and a needle computed from
those would point at "ship north".

Sable already handles that: it `@Overwrite`s `CompassItemPropertyFunction.getAngleFromEntityToPos`
and rotates the target into the ship's space. It reads `lastPose()` though — the pose from the
**previous tick**, not interpolated — so the needle jitters and lags whenever the ship turns.

### Quark replaces the needle wholesale

Quark's *Compasses Work Everywhere* registers its own angle function during client setup:

```java
ItemProperties.register(Items.COMPASS, ResourceLocation.withDefaultNamespace("angle"),
                        new CompassAnglePropertyFunction());
```

The per-item property map is keyed by item, so from that moment on an ordinary compass never touches
`CompassItemPropertyFunction` again — **not vanilla's, not Sable's overwrite, and not part B**. Both
of its private helpers are wrong, in three ways:

```java
private double getAngleToPosition(Entity entity, BlockPos blockpos) {
    Vec3 pos = entity.position();
    return Math.atan2(blockpos.getZ() - pos.z, blockpos.getX() - pos.x);   // ← raw block position
}

private double getFrameRotation(ItemFrame frame) {
    return Mth.wrapDegrees(180.0F + frame.getDirection().toYRot());        // ← facing only
}
```

* **It aims at the block's corner.** Vanilla uses `Vec3.atCenterOf(pos)`; the raw position is the
  corner with the smallest X and Z — the **north-west** one. Half a block off in both axes, obvious
  from close up.
* **It knows nothing about sub-levels.** Aboard an airship the needle points at "ship north".
* **Its frame rotation drops the eight rotation steps.** Vanilla's
  `ItemFrame.getVisualRotationYInDegrees()` adds `getRotation() * 45` and an offset for floor and
  ceiling frames on top of the facing — which is precisely what the frame renderer turns the item by.
  Without it, a turned frame shows a needle turned by the same amount.

## In detail

### A — the lodestone binding

`LodestoneTrackerMixin` answers the same question from the block instead of the POI index:

| Situation | Result |
|---|---|
| Target in another dimension | Untouched — same as vanilla |
| Target outside the world bounds | Untouched — nothing can stand there, vanilla clears it |
| Chunk not loaded | Binding kept (no evidence either way) |
| Chunk loaded, lodestone still there | Binding kept |
| Chunk loaded, block is something else | Handed back to vanilla, which clears it |

Breaking a lodestone still unbinds the compass, immediately, as long as the chunk is loaded. The
guard only fires on a tracker that is `tracked()` and actually carries a target, so an unbound
compass behaves exactly as before.

### B — the needle aboard an airship

The mixin injects at HEAD of the method Sable overwrites and returns its own bearing, computed from
`renderPose(partialTick)` rather than `lastPose()`. The target is transformed into the plot's space
with `pose.transformPositionInverse(Vec3.atCenterOf(target))`, and the bearing comes out in **turns**,
the unit vanilla's angle function works in.

**Two viewers count, and no others.** Rotating the target into the ship's space is correct only
while the yaw that vanilla folds in afterwards lives in that same space. That holds for a viewer
that really sits in the plot — an item frame, or anything else in the entity type tag
`sable:retain_in_sub_level` — which has plot coordinates and a plot-local yaw, so only the target
has to be moved. It holds for a rider as well: Sable projects a player in a seat out to the parent
level but leaves its yaw plot-local, so the rider's *position* is moved into the plot too, through
the same pose, and the ship's translation cancels. Everyone else keeps world coordinates *and* a
world yaw — a player walking the deck is projected out of the plot in full — and mixing a ship-space
bearing into a world-space yaw would put the needle off by the ship's entire rotation: half a turn on
a ship that faces backwards. `SableOrientation.bearingInSubLevel` therefore asks
`SableCompanion.INSTANCE.getContainingClient` for the viewer, then for its vehicle, and returns empty
for everyone else — asking Sable for the sub-level an entity merely *tracks* would answer exactly the
cases that must not be answered.

Without Sable installed, `SableGate.isLoaded()` is false, the bearing is never asked for, and the
needle falls back to vanilla's (or Quark's) behaviour.

### C — the World Compass needle

The needle is not computed here. Vanilla's own `CompassItemPropertyFunction` gets handed a target
four million blocks due north of the viewer:

```java
new CompassItemPropertyFunction((level, stack, entity) -> {
    Vec3 origin = originOf(entity);
    return GlobalPos.of(level.dimension(),
            BlockPos.containing(origin.x, origin.y, origin.z - NORTH_DISTANCE));
});
```

`NORTH_DISTANCE` is `4_000_000`. That is north from anywhere in the world to well within a tenth of a
degree — a fraction of one of the 32 frames; the only offset is the floor `BlockPos.containing` puts
on X, at most half a block over four million. The target is measured from that origin and never
clamped, so for a viewer in the northernmost four million blocks it does land outside the world
border — which is harmless here: `isValidCompassTargetPos` checks the dimension, never the bounds,
and nothing on this path packs the position into a long.

**The four million are measured in the parent level, never inside a plot.** With Sable installed
`originOf` takes the origin from `SableOrientation.parentPosition(viewer, partialTick)` — the point
the viewer itself is drawn at out in the parent level — instead of the viewer's raw position. A
viewer that really sits in a plot, an item frame on a ship, has plot coordinates roughly twenty
million blocks away from where the ship appears to be, and "four million north of that" is a target
the sub-level correction of part B then maps back to a direction dominated by the plot offset rather
than by north. Starting in the parent level makes the ship's own translation cancel against that
correction, and what is left is north turned into the ship's space. Without Sable,
`SableGate.isLoaded()` is false and the origin is simply `viewer.position()`.

Deriving the angle by hand looked tempting (for a target infinitely far north vanilla's
`0.5 - (yaw - 0.25 - bearing)` collapses to `0.5 - yaw`) and it was wrong in play: the needle came
out half a turn off. Handing vanilla a real target removes every sign convention from our side at
once, and it buys three things for free:

* the same wobble for the holding player and the same instant reading in an item frame, so the
  World Compass cannot disagree with the compass next to it in the hotbar;
* the Nether and the End, where an ordinary compass spins — `isValidCompassTargetPos` only rejects a
  target from a *different* dimension, and ours always carries the viewer's own;
* part B, because the target travels the ordinary vanilla path: aboard a turning airship the
  sub-level correction applies here as well.

### D — the needle Quark takes over

`QuarkCompassAngleMixin` corrects all three faults at HEAD of the two private methods: the block
centre (or, aboard a ship, the bearing from `SableOrientation`) for `getAngleToPosition`, and
vanilla's `ItemFrame.getVisualRotationYInDegrees()` for `getFrameRotation`. Quark's Nether and End
compass is left intact — only these two helpers are touched.

Quark's method works in **radians** where vanilla's works in turns
(`Math.atan2(…) / (float)(Math.PI * 2)`), so the bearing is multiplied back by 2π here and passed
through unchanged in part B.

This part matters more than it looks, because **it silently voids part B**: in a pack with Quark, an
ordinary compass never reaches `CompassItemPropertyFunction`, so part B then only affects items that
still use the vanilla function — notably the World Compass. With Quark installed and
`fix_quark_compass = false`, an ordinary compass aboard a ship is back to pointing at "ship north".

## Items, blocks and recipes

| Item | ID | Notes |
|---|---|---|
| <img src="../img/items/world_compass.png" width="48" alt="World Compass"> **World Compass** | `vanillaplusadditions:world_compass` | Points at world north in every dimension. Carries no state; the needle lives entirely in the client-side property function. |

```
 . I .        I = Iron Ingot
 I A I        A = Amethyst Shard
 . I .
```

Vanilla's own compass with an amethyst shard where the redstone goes.
One World Compass per craft, category `MISC`. Like every recipe in this mod it is registered in code
(`AddReloadListenerEvent`), not as a datapack JSON — see the [Custom Crafting Recipes
module](custom_crafting_recipes.md) for the reasoning.

### Textures

The 32 frames are the **player's own compass frames, recoloured at load time**. The mod ships no
compass pixels at all: `WorldCompassSpriteSource` reads `minecraft:textures/item/compass_00…31.png`
out of the resource stack while the texture atlas is being stitched, recolours each frame in Java
(`WorldCompassRecolour`) and hands the results back as the 32 `world_compass_NN` sprites.

Two things follow from that. The jar contains **no derived Mojang assets** — only code and one
atlas definition. And the World Compass finally **follows the player's resource pack**: before
beta.92 it was frozen on vanilla, so anyone using a pack saw a restyled ordinary compass next to our
old-looking one.

Every attempt at drawing the frames from scratch foundered on the lit metal casing that carries the
whole icon, so not a pixel of it is touched. Only the dial is recoloured, bounded by a mask obtained
from a flood fill at the centre: 46 pixels on the vanilla 16×16 frame. Outside that mask **not one
pixel differs across all 32 frames**, so the casing is provably identical everywhere. Five roles are
recoloured:

| Source (inside the mask only) | Role | Target |
|---|---|---|
| `#ff1414`, `#cb1a1a`, `#be1515` | north needle | Amethyst `#a855f7` at the same brightness steps (1.0, 0.8, 0.74) |
| `#2f2f2f` | dial face | Ender pearl `#105e51` |
| `#4f4d4d` | south pointer | Ender eye green `#71ac49` |
| `#646464` | pivot | dial colour, lifted 30% toward white |
| `#353535`, **only the 10 pixels along the dial's rim** | inner edge | deeper green `#0b4d42` |
| everything else | casing | **unchanged** |

Two masks rather than one colour table, because two greys do double duty: `#4f4d4d` is the south
pointer inside and a dark spot on the casing outside, and `#353535` is both the inner edge and the
upper arc of the casing. A plain colour mapping would repaint the casing as well — which is also why
vanilla's own `minecraft:paletted_permutations` sprite source cannot do this job, and why a custom
one exists. The flood fill starts inside the dial and is walled in by the inner edge, so it never
reaches the outer occurrences of those two greys.

Because the masks are derived from colours rather than coordinates, the recolour is
**resolution-independent**: a resource pack drawing the compass at 32×32 or 64×64 is recoloured just
the same, as long as it keeps the five source colours. If it does not, the fallback is graceful in
three steps — a missing frame is skipped with a warning, a frame with no recognisable dial is passed
through **unchanged** (so the item looks like an ordinary compass instead of showing a missing
texture), and a frame that cannot be read at all is dropped by vanilla's own `loadSprite`.

The sprite source type is registered from `CompassOverhaulClientSetup` via NeoForge's
`RegisterSpriteSourceTypesEvent`, and referenced from
`assets/minecraft/atlases/blocks.json` — in the **`minecraft` namespace**, not ours, because
`SpriteSourceList.load` derives the file name from the atlas's own id (`minecraft:blocks`) and
reads that one path from every pack; a copy under our own namespace is never opened, and fails
silently. That atlas file deliberately ships in the
**`vpa_compass_overhaul` jar rather than in `vpa_core`**: it names a sprite source type that only
this module registers, so an installation without the module would log an unknown-type parse error
on every resource reload.

`scripts/gen_world_compass_textures.py` is retired as a build step but kept as the reference
implementation — the Java port was verified against its 32 committed outputs and reproduces them
pixel for pixel. It still generates the model files.

The model file reuses vanilla's override table verbatim, so the frames line up exactly as they do on
a normal compass. There are 32 texture frames but only 31 sub-models: `world_compass_16.json` is
deliberately absent, because the base model *is* frame 16, exactly as in vanilla.

<!-- vpa:config:start -->
## Configuration

Section `[modules.compass_overhaul]` in `config/vanillaplusadditions-common.toml` (or `config/vpa_compass_overhaul-common.toml` if you run the standalone jar).

Every module also has the universal `enabled` and `debug_logging` keys — see the [Configuration Guide](../guides/configuration.md).

| Key | Type | Default | Range | Effect |
|---|---|---|---|---|
| `fix_quark_compass` | boolean | `true` | — | Repairs the needle where Quark's 'Compasses Work Everywhere' takes it over (aim at block centre instead of the north-west corner, honour Sable sub-levels, keep an item frame's rotation steps); without Quark it does nothing. |
| `fix_sublevel_needle` | boolean | `true` | — | Corrects the compass needle inside Sable sub-levels (Create Aeronautics airships), using the interpolated render pose instead of Sable's previous-tick pose; turn it off if another mod fights over the same method. |
| `guard_lodestone_binding` | boolean | `true` | — | Keeps a lodestone compass bound unless the lodestone is provably gone, instead of dropping the binding whenever vanilla's POI lookup comes back empty (which also happens for an unloaded chunk or an unreadable POI file). |
| `world_compass_enabled` | boolean | `true` | — | Adds the World Compass recipe — a compass that always points north in world space, including the Nether, the End and aboard a turning airship. NOTE: only the recipe is actually gated; the item itself is registered regardless (see notes). |
<!-- vpa:config:end -->

## Compatibility and known limits

| Limit | Effect |
|---|---|
| No Sable installed | Part B is inert (`SableGate.isLoaded()` is false and no Sable class is ever resolved). Parts A, C and D work unchanged. |
| No Quark installed | Part D is inert — Mixin disables the mixin, because both mixin configs are `"required": false`. |
| Quark installed, `fix_quark_compass = false` | Part B is void for an ordinary compass: Quark's function owns the item, so the sub-level correction never runs there. The World Compass is unaffected. |
| `world_compass_enabled = false` | Removes the **recipe only**. The item stays registered, stays in the creative tab and still works; it is merely uncraftable. |
| Whole module disabled | Untested on a client, and suspect: `CompassOverhaulClientSetup` is an FML-scanned `@EventBusSubscriber` and is *not* gated on the module, yet it dereferences `WORLD_COMPASS`. A disabled module never reaches `onInitialize`, so the holder is never bound and client setup would run into an unbound `DeferredHolder` rather than quietly dropping the item. Deduced from the source, not reproduced. |
| Another mod writing to `getAngleFromEntityToPos` | Sable already `@Overwrite`s it. Part B declares `priority = 1500` and `require = 0`, so a lost race shows up as "nothing happens", never as a crash. The escape hatch is `fix_sublevel_needle = false`. |
| Lodestone broken in an unloaded chunk | The binding is kept until you come back and the chunk loads. That is the point: vanilla cannot tell that case apart from a failed POI read. |

## Under the hood

| File | Part |
|---|---|
| `modules/compass_overhaul/CompassOverhaulModule.java` | item, recipe, config gates |
| `modules/compass_overhaul/client/WorldCompassAngle.java` | the needle |
| `modules/compass_overhaul/client/CompassOverhaulClientSetup.java` | binds the needle to the item |
| `modules/compass_overhaul/compat/SableGate.java` | "is Sable there?" — names no Sable type |
| `modules/compass_overhaul/compat/SableOrientation.java` | every Sable reference lives here |
| `mixin/compass_overhaul/LodestoneTrackerMixin.java` | part A (server) |
| `mixin/compass_overhaul/CompassAngleSubLevelMixin.java` | part B (client) |
| `mixin/compass_overhaul/QuarkCompassAngleMixin.java` | part D (client) |
| `scripts/gen_world_compass_textures.py` | the 32 frames and 32 models |

**Mixins.** `LodestoneTrackerMixin` injects at HEAD of `LodestoneTracker.tick` with the default
`require = 1`; the method only ever runs server-side. `CompassAngleSubLevelMixin` targets
`CompassItemPropertyFunction` with `priority = 1500` and `require = 0` — a deliberate deviation from
the config-wide `"injectors": { "defaultRequire": 1 }`, because Sable overwrites the same method.
`QuarkCompassAngleMixin` names its target as a string (`remap = false`), so Quark is not a compile
dependency of this module.

**The Sable split is not cosmetic.** `SableOrientation` imports `dev.ryanhcode.sable.companion.*`
directly, so touching that class at all — even to ask whether Sable is installed — would resolve
those classes and throw `NoClassDefFoundError` in a pack without Sable. The question therefore lives
in `SableGate`, which names no Sable type and caches `ModList.get().isLoaded("sable")`, and every
call into `SableOrientation` sits inside an `if` guarded by it. Building the module still needs
`libs/sable-neoforge-*.jar` on the `compileOnly` classpath.

**Item and recipe.** The item is registered unconditionally in `onInitialize` and handed to
`VanillaPlusCreativeTabs.addToMainTab`; only the recipe is gated on
`isModuleEnabled() && world_compass_enabled`. The reload listener copies the whole recipe map into a
`LinkedHashMap`, adds `vanillaplusadditions:world_compass` and calls `RecipeManager.replaceRecipes`,
on every server resource reload.

**The needle property** is registered under vanilla's own key `minecraft:angle`. The property map is
keyed by item first, so reusing the key collides with nothing and lets the generated model file say
plain `"angle"`, exactly like the vanilla compass model it came from. `ItemProperties.PROPERTIES` is
a plain `HashMap` and client setup runs in parallel, hence the `enqueueWork`.

**Units.** Vanilla's angle function works in turns, Quark's in radians. `SableOrientation` returns
turns; `QuarkCompassAngleMixin` multiplies by 2π, `CompassAngleSubLevelMixin` passes the value
through. Easy to get wrong when editing either.

**Standalone jar.** `vpa_compass_overhaul` ships `LodestoneTrackerMixin` plus the two client mixins
and carries no data files of its own — the 32 models, the 32 textures and the lang entry live in
`vpa_core`, which every module jar requires.

**Testing.** This repo has no unit tests. Quark and Zeta are deliberately `compileOnly` with no
`localRuntime` (see the comment in `build.gradle`), so part D cannot be exercised in `runClient` at
all. Sable *is* on `localRuntime`, but the dev run is blocked for another reason: `neo_version` is
`21.0.167` while Sable requires NeoForge `[21.1.219,)`, so it refuses to load there too. Both parts
therefore need the deployed jar in a real 1.21.1 instance with those mods present.

## See also

* [Configuration Guide](../guides/configuration.md) — where the config file lives
* [Custom Crafting Recipes](custom_crafting_recipes.md) — why recipes are built in code here
* [All modules](../../README.md#-modules)
