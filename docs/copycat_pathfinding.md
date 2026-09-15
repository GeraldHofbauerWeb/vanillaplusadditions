# Copycat Pathfinding

## Overview

Create's **Copycat Panel** is a 3-pixel plate stuck to one face of its block — but for mob
pathfinding it counts as a solid wall. A cat refuses to walk through a passage whose ceiling
carries a panel, an axolotl won't swim past a waterlogged one, and no amount of pathfinding malus
or step-height tuning helps: the node simply does not exist for the A\* search.

This module gives mobs the finer answer. A panel blocks only the movements that actually cross its
plate; walking along a panel-lined wall, under a ceiling panel or over a floor panel works again.

---

## Why Create does it

`CopycatPanelBlock` and `CopycatStepBlock` override vanilla's default with a hardcoded

```java
protected boolean isPathfindable(BlockState state, PathComputationType type) {
   return false;
}
```

Vanilla turns that into `PathType.BLOCKED` for the whole block — on land
(`WalkNodeEvaluator`) and in water (`SwimNodeEvaluator`) alike, which is why it hits cats and
axolotls the same way.

For two of the four cases that answer is *correct*, and the module keeps it:

- A **floor panel** returning "not pathfindable" is what makes the cell above it walkable, and
  that is how mobs walk across panel floors and free-standing panel bridges.
- **`copycat_step`** does exactly what `SlabBlock` does. Nothing to repair.

Genuinely wrong are **wall panels**, **ceiling panels**, and **waterlogged** copycats (a vanilla
slab lets swimming mobs through, Create's copycat does not).

---

## Behaviour

| Block state | Land | Water |
|---|---|---|
| Panel, `FACING` horizontal (wall) | passable, except across the plate | vanilla (waterlogged = water) |
| Panel, `FACING = DOWN` (ceiling) | passable, except up/down through the plate | vanilla |
| Panel, `FACING = UP` (floor) | walkable ground **if** it rests on something solid; unchanged when it floats | vanilla |
| `copycat_step` | unchanged | vanilla |

**"Direction-aware" in detail.** The plate sticks to the face `FACING.getOpposite()` — placing a
panel while looking down gives `FACING = UP` and a plate on the floor. A move is rejected when it
would leave a cell through its own plate, or enter a cell through the plate on the face being
crossed. Everything else is allowed.

**Floor panels.** Their cell stays "blocked" so the cell above keeps being walkable — otherwise a
free-standing panel bridge would read as a hole and mobs would refuse to cross it. Instead the
panel cell itself is promoted to walkable ground whenever the block below offers footing, using
vanilla's own rule. That is what makes a passage only one block high work.

**Pathfinding is not collision.** The plate still stops mobs physically, whatever the path finder
believes. A panel standing squarely across a doorway stays impassable — that is precisely why the
direction check exists: without it the path finder would send mobs into the plate, where they would
bump and give up.

---

## Configuration

Section `[modules.copycat_pathfinding]`.

| Key | Default | Description |
|---|---|---|
| `enabled` | `true` | Module switch |
| `debug_logging` | `false` | Module debug logging |
| `direction_aware` | `true` | Block only movements that cross a plate. `false` makes wall and ceiling panels passable from every side — mobs then walk into panel walls and get stuck against them |
| `floor_panels_walkable` | `true` | Promote a supported floor panel to walkable ground (fixes passages one block high) |
| `water_pathfinding` | `true` | Let waterlogged panels and steps count as water, like vanilla slabs |

---

## Requirements

- **Create.** Without it the module reports itself as skipped and every hook stays inert — the code
  never references a Create class, the two blocks are looked up from the registry by name.
- Standalone jar: `vpa_copycat_pathfinding` (needs `vpa_core`).

---

## Known limits

1. **Pure water mobs** (fish, dolphins, guardians) use `SwimNodeEvaluator`, which has no source
   node in its neighbour check — they get the waterlogged fix but no direction awareness. Axolotls
   are fully covered, because `AmphibiousNodeEvaluator` extends `WalkNodeEvaluator`.
2. **Flying mobs** (parrots, bees, allays) go through `FlyNodeEvaluator`'s own `isOpen(Node)` path,
   which likewise has no source node — same limitation.
3. **Config changes need a moment.** Path types are cached per position (`PathTypeCache`,
   4096 entries per level); a toggle takes effect once the entry is evicted or a block update
   invalidates it.
4. **Jump and fall diagonals** are checked between start and target cell only, not for the cells
   passed on the way. In practice the mob just lands on the plate earlier.
5. **Ceiling panels and tall mobs.** The cell becomes walkable although a 2-block-high mob does not
   quite fit under the plate. Irrelevant for cats and axolotls.
6. **`create:copycat_bars`** never overrode `isPathfindable` and is already passable — untouched.

---

## Implementation

| Mixin | Target | Why |
|---|---|---|
| `BlockStateBaseCopycatMixin` | `BlockBehaviour$BlockStateBase#isPathfindable` | The single choke point both node evaluators ask — one injection covers land and water |
| `WalkNodeEvaluatorCopycatMixin` | `isNeighborValid`, `isDiagonalValid`, `getPathTypeStatic` | The direction check for straight and diagonal moves, plus the floor-panel promotion |
| `NodeEvaluatorContextAccessor` | `NodeEvaluator#currentContext` | Read access to the region the path finder is working on |

Geometry lives in `PanelGeometry`; the registry lookup and a one-off self-check of Create's
orientation convention live in `compat/CopycatBlocks`. If Create ever flips that convention, the
self-check disables the module rather than blocking the moves it means to allow.

---

## See also

- [Module Configuration Guide](MODULE_CONFIG_GUIDE.md)
- [Cat Guardian](cat_guardian.md)
