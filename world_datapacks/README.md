# World datapacks

Datapacks that live in a **world folder**, not in the mod jar. They are not built by Gradle and
not shipped with any release — they are kept here so the live server is not the only copy.

Both are in use on games2, under `/AMP/Minecraft/survival_world/datapacks/`.

Install: copy the folder into `<world>/datapacks/`. New folders are picked up and enabled
automatically — `/reload` is enough for tags, worldgen needs a server restart and then only
affects chunks generated from that point on.

---

## `vpa_higher_mountains`

Raises terrain far from spawn, so mountains out in the unexplored land break through the cloud
layer while everything the players have already built stays untouched.

Wraps `minecraft:overworld_large_biomes/offset` through Lithostitched. **The `large_biomes`
variant is the one that matters** — games2 runs `level-type=minecraft:large_biomes`, and wrapping
plain `minecraft:overworld/offset` would do nothing at all.

- Amplification ramps in from 10,000 to 18,000 blocks from origin (`amplify_ramp.json`); the
  explored radius at the time was 8,397, so the inhabited area is outside the ramp entirely.
- The amplified value is a **locally blurred** copy of the offset (5-point cross at 384 blocks,
  `blur_offset.json`). Amplifying the raw offset multiplies the horizontal gradient by the same
  factor and produces vertical walls; blurring first keeps the peaks and loses the cliffs.
- `minecraft:clamp` rejects bounds beyond ±1,000,000. A larger value makes the whole pack fail to
  load and the world refuses to start — loudly, which is the one merciful part.

Measured against an identical seed without the pack: summit y 241 vs 156, maximum slope 77 vs 100,
chunk generation +10 %.

## `vpa_movable_docking_connector`

Lets a Create contraption carry a `simulated:docking_connector` (Create Aeronautics / Simulated).

Simulated 1.3.1 added the connector to the block tag `create:non_movable`, which Create checks in
`BlockMovementChecksImpl.isMovementAllowedFallback` **before** any config — so there is no switch,
only the tag. The pack overrides it with `"replace": true` and re-lists everything except the
connector itself.

`simulated:paired_docking_connector` deliberately stays non-movable: it only exists while the
connector is extended, and leaving it blocked turns "moving while extended" into a clear assembly
error instead of an orphaned block.

All entries carry `"required": false`, so the pack can never break world loading if one of those
mods is removed.

After a change, verify with `/reload` and then, standing next to a connector:

```
/execute if block <x> <y> <z> #create:non_movable run say STILL BLOCKED
```

Silence means the override took.
