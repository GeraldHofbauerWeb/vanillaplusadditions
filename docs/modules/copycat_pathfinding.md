# Copycat Pathfinding

> **TL;DR** — Mobs use a passage lined with Create Copycat Panels again, instead of treating a
> 3-pixel plate as a solid wall.

<!-- vpa:meta:start -->
|  |  |
|---|---|
| **Module ID** | `copycat_pathfinding` |
| **Side** | Server only |
| **Requires** | [Create](https://modrinth.com/mod/create) <sub>tested 6.0.10</sub> |
| **Works with** | — |
| **Download** | [`vpa_copycat_pathfinding.jar`](https://github.com/GeraldHofbauerWeb/vanillaplusadditions/releases/latest/download/vpa_copycat_pathfinding.jar) · also needs `vpa_core` |
| **Config section** | `[modules.copycat_pathfinding]` |
| **Since** | `v1.0.0-beta.77` |
<!-- vpa:meta:end -->

## What it does

Create's **Copycat Panel** is a 3-pixel plate stuck to one face of its block; the other 13 pixels of
that block are air. For mob pathfinding it counts as a solid wall. A cat refuses to use a crawl
passage whose floor or ceiling carries a panel, an axolotl will not swim past a waterlogged one, and
a corridor one block wide becomes impassable the moment you line a wall with panels — even though the
mob physically fits through the 13 pixels that are left.

This module gives mobs the finer answer. A panel blocks only the movements that actually cross its
plate: walking along a panel-lined wall, under a ceiling panel or over a supported floor panel works
again, while a panel standing squarely across the way still stops them. Free-standing panel bridges
and `copycat_step` keep their vanilla-slab behaviour.

| Block state | On land | In water |
|---|---|---|
| Panel, `FACING` horizontal (wall) | passable, except across the plate | waterlogged counts as water |
| Panel, `FACING = DOWN` (ceiling) | passable, except up or down through the plate | waterlogged counts as water |
| Panel, `FACING = UP` (floor) | walkable ground **if** it rests on something solid; unchanged when it floats | waterlogged counts as water |
| `copycat_step` | unchanged | waterlogged counts as water |

**Pathfinding is not collision.** The plate still stops mobs physically, whatever the path finder
believes. A panel standing squarely across a doorway stays impassable — which is precisely why the
direction check exists: without it the path finder would send mobs into the plate, where they would
bump against it and give up.

## Why it exists

`CopycatPanelBlock` and `CopycatStepBlock` both replace vanilla's default with a hardcoded answer.
In `create-1.21.1-6.0.9.jar` the method body of either is two instructions, `iconst_0; ireturn`:

```java
protected boolean isPathfindable(BlockState state, PathComputationType type) {
    return false;
}
```

Vanilla turns that into `PathType.BLOCKED` for the whole block, in the one place every land-based
node evaluator ends up:

```java
if (!blockstate.isPathfindable(PathComputationType.LAND)) {
    return PathType.BLOCKED;
}
```

`BLOCKED` carries a malus of `-1.0F`, and `findAcceptedNode` only creates a node where the malus is
`>= 0`. The cell is therefore not merely expensive — it does not exist for the A\* search at all, so
no malus or step-height tuning on the mob reaches it. `getPathTypeOfMob` widens the damage for
anything taller than one block: it scans the whole mob bounding box, and the first cell with a
negative malus is returned as the type of the entire node.

For two of the four cases Create's `false` is in fact right, and the module keeps it:

* A **floor panel** answering "not pathfindable" is what makes the cell *above* it walkable. That is
  how mobs cross panel floors and free-standing panel bridges today.
* **`copycat_step`** does on land exactly what `SlabBlock` does — vanilla's own slab returns `false`
  for `LAND` too. Nothing to repair.

Genuinely wrong are **wall panels**, **ceiling panels**, and **waterlogged** copycats of either kind:
`SlabBlock` hands swimming mobs `state.getFluidState().is(FluidTags.WATER)`, Create's copycats hand
them `false`.

## In detail

### What gets handed back

`BlockStateBaseCopycatMixin` injects at HEAD of `BlockBehaviour.BlockStateBase.isPathfindable` and,
where it answers at all, answers with vanilla's own expression from that same method — copied, not
approximated:

| Block | `LAND` | `WATER` | `AIR` |
|---|---|---|---|
| Panel, wall or ceiling | `!state.isCollisionShapeFullBlock(…)` → **true** | `state.getFluidState().is(FluidTags.WATER)` | untouched |
| Panel, floor (`FACING = UP`) | untouched → Create's `false` | `state.getFluidState().is(FluidTags.WATER)` | untouched |
| `copycat_step` | untouched → Create's `false` | `state.getFluidState().is(FluidTags.WATER)` | untouched |

`AIR` is left alone because no evaluator in 1.21.1 asks for it. A wall or ceiling panel thus becomes
`OPEN`, or `WATER` when it is waterlogged, because `getPathTypeFromState` falls through to
`fluidstate.is(FluidTags.WATER) ? PathType.WATER : PathType.OPEN` once `isPathfindable` says yes.

### Where the plate sits

The load-bearing detail of the whole module: **the plate sticks to `FACING.getOpposite()`, not to
`FACING`.** Create builds the shape from `AllShapes.CASING_3PX`, which is `shape(0, 0, 0, 16, 3, 16)`
— a *floor* plate anchored at `UP` — and rotates it by `FACING`; placement is

```java
state.setValue(FACING, context.getNearestLookingDirection().getOpposite())
```

so looking down gives `FACING = UP` and a plate in the lowest three pixels. Dropping that
`getOpposite()` would invert the module: it would free exactly the moves it means to block.

`CopycatBlocks.panelFacingIsInverted` therefore checks the convention once, at resolve time, by
asking the `FACING = UP` state for its shape through `EmptyBlockGetter` and demanding
`min(Y) < 1e-6 && max(Y) < 0.5`. If a future Create version flips it, the panel reference is set back
to `null`, a `WARN` goes to the log and every panel fix stays off.

### Which movements are rejected

`PanelGeometry` reduces the question to one rule per axis. A step along an axis is blocked when it
leaves the source through the source's own plate, or enters the target through the plate on the face
being crossed:

```java
return fromPlate == step || toPlate == step.getOpposite();
```

A straight move costs exactly two block lookups no matter how many axes change, because both plate
faces are fetched once and then tested per axis. The diagonal variant checks all three legs — root to
x-neighbour, root to z-neighbour, and root to the diagonal cell `(xNode.x, root.y, zNode.z)` — which
is the cell vanilla actually produces, since `findAcceptedNode` only ever shifts a node in y.

Both checks are injected at `RETURN` of `WalkNodeEvaluator.isNeighborValid` and
`isDiagonalValid(Node, Node, Node)` and act only on a `true` return, so they can only ever remove a
neighbour, never add one.

### The floor-panel upgrade

A floor panel keeps Create's `false`, which leaves its cell `BLOCKED` and the cell above walkable.
The cost of that is a passage exactly one block high — a crawl space whose floor is a panel has no
cell above to walk in, and the mob gives up.

`vpaFloorPanelWalkable` injects at `RETURN` of `WalkNodeEvaluator.getPathTypeStatic` and promotes
that one cell. It fires only when

1. vanilla already returned `PathType.BLOCKED`,
2. the block is `create:copycat_panel` with `FACING = UP`, and
3. the cell below is **not** one of `OPEN`, `WATER`, `LAVA`, `WALKABLE`.

Condition 3 is vanilla's own footing test, taken from the switch a few lines up in the same method,
and it is what leaves a free-standing panel bridge alone: a panel with air under it stays blocked, so
mobs keep walking across the top of it. When all three hold, the result is
`WalkNodeEvaluator.checkNeighbourBlocks(context, x, y, z, PathType.WALKABLE)`, so the promoted cell
still goes through vanilla's danger scan of its 24 horizontal and diagonal neighbours.

### Swimming mobs

`SwimNodeEvaluator.getPathTypeOfMob` ends on

```java
return blockstate1.isPathfindable(PathComputationType.WATER) ? PathType.WATER : PathType.BLOCKED;
```

which is the single line the `water_pathfinding` switch repairs, for panels and steps alike.
Amphibious mobs reach the same result down the land path instead: a waterlogged wall panel passes the
restored `LAND` test and then resolves to `PathType.WATER`, and because `AmphibiousNodeEvaluator`
extends `WalkNodeEvaluator` it also gets the direction check on its vertical neighbours. An axolotl
therefore cannot dive through a floor or ceiling plate; a cod can.

### What a config change reaches, and when

`PathTypeCache` is a 4096-slot direct-mapped cache per level, keyed by packed block position, and it
caches `getPathTypeFromState` — the very place `isPathfindable` is consulted. So:

| Key | Cached? | Takes effect |
|---|---|---|
| `enabled`, `water_pathfinding` | yes | once the entry is evicted by a block update or by another position hashing into the same slot |
| `direction_aware` | no | next path calculation |
| `floor_panels_walkable` | no | next path calculation |

<!-- vpa:config:start -->
## Configuration

Section `[modules.copycat_pathfinding]` in `config/vanillaplusadditions-common.toml` (or `config/vpa_copycat_pathfinding-common.toml` if you run the standalone jar).

Every module also has the universal `enabled` and `debug_logging` keys — see the [Configuration Guide](../guides/configuration.md).

| Key | Type | Default | Range | Effect |
|---|---|---|---|---|
| `direction_aware` | boolean | `true` | — | Blocks only those movements that actually cross a panel's plate; false makes wall and ceiling panels passable from every side, which lets mobs walk into a panel wall and get stuck against it. |
| `floor_panels_walkable` | boolean | `true` | — | Treats a floor panel (FACING=UP) resting on something solid as walkable ground instead of a wall, fixing passages only one block high, while a floor panel with air below stays as it is so mobs keep crossing free-standing panel bridges. |
| `water_pathfinding` | boolean | `true` | — | Lets waterlogged panels and steps count as water for swimming mobs, like vanilla slabs do, where Create blocks them outright. |
<!-- vpa:config:end -->

## Compatibility and known limits

| Limit | Effect |
|---|---|
| Pure water mobs | Fish, dolphins and guardians use `SwimNodeEvaluator`, whose `isNodeValid(Node)` sees only the neighbour and no source node. They get the waterlogged fix but no direction awareness, and swim straight through a plate. |
| Flying mobs | Parrots, bees and allays go through `FlyNodeEvaluator.isOpen(Node)` — same shape, same limitation. |
| Jump and fall diagonals | Checked between source and target cell only, never for the cells passed on the way: `crosses` looks at the sign of the y delta, not at its size. In practice the mob lands on the plate a little earlier. |
| Ceiling panels and tall mobs | The cell becomes walkable although a mob two blocks high does not quite fit under the plate. Irrelevant for cats and axolotls. |
| Floor panel on a damaging block | Vanilla gives an *open* cell above magma, fire or a lit campfire `DAMAGE_FIRE` from the switch over the cell below. The promotion here only excludes `OPEN`/`WATER`/`LAVA`/`WALKABLE` and then calls `checkNeighbourBlocks`, whose 3×3×3 scan deliberately skips the column directly below, so a floor panel laid on magma reads as ordinary walkable ground. Read off the source; nothing in this repository tests it. |
| `create:copycat_bars` | Not a copycat block at all — it is registered as a plain `WrenchableDirectionalBlock`, never overrode `isPathfindable`, and is already passable. Untouched. |
| Create's orientation convention changes | The self-check disables every panel fix and logs a `WARN`. Note the asymmetry: only the *panel* reference is nulled, so the `water_pathfinding` repair for `copycat_step` keeps running while the waterlogged-*panel* half goes silent with it. |
| Registry lookup fails | `ensureResolved` sets its `resolved` flag before the lookups, so any failure inside leaves both references `null` and the module inert for the rest of the session. Fail-safe, but entirely silent — only the orientation mismatch above logs anything. |
| No Create | Both references stay `null`, all three hooks fall through on the first comparison, and the jar loads and runs inert. The bundle declares Create as `type="optional"`; the generated standalone `vpa_copycat_pathfinding` declares no Create dependency at all. |

## Under the hood

No items, no blocks, no recipes, no commands, no keybinds, no lang keys, no assets, no data files, no
`DeferredRegister` and no event-bus listener of its own. The module's whole lifecycle is one log line
in `onInitialize` and an `ensureResolved` warm-up in `onLoadComplete`; everything else happens inside
three mixins.

| Mixin | Target | Purpose |
|---|---|---|
| `BlockStateBaseCopycatMixin` | `BlockBehaviour$BlockStateBase#isPathfindable`, HEAD | The single choke point both node evaluators ask — one injection covers land and water |
| `WalkNodeEvaluatorCopycatMixin` | `isNeighborValid`, `isDiagonalValid`, `getPathTypeStatic`, all at RETURN | The direction check for straight and diagonal moves, plus the floor-panel promotion |
| `NodeEvaluatorContextAccessor` | `NodeEvaluator#currentContext`, `@Accessor` | Read access to the region the path finder is working on |

All three sit in the both-sides `mixins` block of `vanillaplusadditions.mixins.json`, although every
caller — node evaluators, spawn placement — runs server-side. Both mixin configs, bundle and
generated, are `"required": false`, so a missing target disables the mixin rather than crashing the
game.

| Class | Role |
|---|---|
| `modules/copycat_pathfinding/CopycatPathfindingModule` | Lifecycle and the four static gates the mixins call |
| `modules/copycat_pathfinding/PanelGeometry` | Where the plate sits and which moves cross it |
| `modules/copycat_pathfinding/compat/CopycatBlocks` | Registry lookup and the orientation self-check |
| `modules/copycat_pathfinding/config/CopycatPathfindingConfig` | The three switches |
| `standalone/copycat_pathfinding/CopycatPathfindingStandalone` | `@Mod("vpa_copycat_pathfinding")` entry point |

**No Create class is ever named.** The two blocks are resolved from `BuiltInRegistries.BLOCK` by id
(with `getOptional`, not `get` — the block registry is defaulted and would hand back `AIR`), and
`BlockStateProperties.FACING` is the very same property instance Create's `CopycatPanelBlock`
registers. The module therefore compiles and links without Create on the classpath.

**Resolution is lazy on purpose.** `ModuleManager.loadComplete()` only calls back into modules that
were enabled at startup, so resolving there alone would leave a module switched on at runtime blind
to its blocks. `ensureResolved` is a single volatile read after the first call and is safe to call
from the hot hooks; `onLoadComplete` calls it once anyway as a warm-up.

**The hot path.** `isPathfindable` runs for every block of every path calculation and for spawn
placement, so the block-identity test comes first and `step()` is short-circuited away once the block
is known to be the panel. Only then is `isActive()` asked. The neighbour hooks do it the other way
round: `PanelGeometry.blocksMove` is evaluated before the config gate, so with Create installed and
`direction_aware = false` you still pay two block lookups per accepted neighbour.

**What `isActive()` really gates.** `AbstractModule.initialize` assigns `modEventBus` *before* the
`shouldInitialize()` check, and `isModuleEnabled()` keys off that field being non-null. Without Create
the module skips `onInitialize()`, but `isActive()` still returns `true` — it reflects the `enabled`
switch alone. The Create guard on all three hooks is the `null` block reference, not `isActive()`.
The three feature gates behave the other way round when the module instance is `null`: they return
`true` (`module == null || …`), which is harmless because every hook also checks `isActive()`.

**Two spellings of one test.** `BlockStateBaseCopycatMixin` singles out the floor panel with
`FACING.getOpposite() == DOWN`, `WalkNodeEvaluatorCopycatMixin` with `FACING != UP`. Equivalent, but
easy to misread as two different rules when editing. The latter also uses plain `==` comparisons
instead of an enum switch on purpose: an enum switch inside a mixin emits a synthetic switch-map
inner class that Mixin would then have to relocate into the target.

## See also

* [Cat Guardian](cat_guardian.md) — the cat that has to find its way to you
* [Axolotl Guardian](axolotl_guardian.md) — covered for free through `AmphibiousNodeEvaluator`
* [Configuration Guide](../guides/configuration.md) — where the config file lives
* [All modules](../../README.md#-modules)
